package com.transactionapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.model.TaxYearPass;
import com.transactionapi.model.User;
import com.transactionapi.repository.TaxYearPassRepository;
import com.transactionapi.service.UserService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.billing.stripe.webhook-secret=whsec_test_secret",
        "app.billing.tax-year-pass.payment-link-url=https://buy.stripe.com/test_link"
})
@ExtendWith(OutputCaptureExtension.class)
class StripeWebhookControllerTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";
    /** stripe-java 34.1.0's pinned API version. Events from another release need the deserializeUnsafe fallback. */
    private static final String SDK_API_VERSION = "2026-10-28.endive";
    private static final String BUYER_EMAIL = "webhook.buyer@example.com";
    private static final String STRIPE_CONTENT_TYPE = "application/json; charset=utf-8";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private TaxYearPassRepository taxYearPassRepository;

    @Test
    void paidCheckoutUnlocksThePassAndRecordsWhatStripeCharged(CapturedOutput output) throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        CheckoutSession session = CheckoutSession.paid(pass);

        postWebhook(checkoutSessionEvent("checkout.session.completed", session)).andExpect(status().isOk());

        TaxYearPass updated = reload(pass);
        assertThat(updated.getStatus()).isEqualTo(TaxYearPassStatus.PAID);
        assertThat(updated.getStripeCheckoutSessionId()).isEqualTo(session.id());
        assertThat(updated.getStripePaymentIntentId()).isEqualTo(session.paymentIntentId());
        assertThat(updated.getStripeCustomerId()).isEqualTo("cus_test_buyer");
        assertThat(updated.getAmountTotal()).isEqualTo(3900L);
        assertThat(updated.getCurrency()).isEqualTo("CAD");
        assertThat(updated.getPaidAt()).isNotNull();
        assertThat(updated.getRefundedAt()).isNull();
        assertThat(output).contains("for tax year pass " + pass.getId() + ": UPDATED, status PAID");
        assertThat(output).doesNotContain(BUYER_EMAIL);
    }

    @Test
    void zeroAmountCheckoutUnlocksThePass() throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        CheckoutSession session = new CheckoutSession(pass.getId().toString(), uniqueId("cs_test_"),
                "no_payment_required", 0, null);

        postWebhook(checkoutSessionEvent("checkout.session.completed", session)).andExpect(status().isOk());

        TaxYearPass updated = reload(pass);
        assertThat(updated.getStatus()).isEqualTo(TaxYearPassStatus.PAID);
        assertThat(updated.getAmountTotal()).isZero();
        assertThat(updated.getStripePaymentIntentId()).isNull();
        assertThat(updated.getPaidAt()).isNotNull();
    }

    @Test
    void delayedPaymentStaysProcessingUntilStripeConfirmsIt() throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        CheckoutSession session = CheckoutSession.unpaid(pass);

        postWebhook(checkoutSessionEvent("checkout.session.completed", session)).andExpect(status().isOk());

        TaxYearPass processing = reload(pass);
        assertThat(processing.getStatus()).isEqualTo(TaxYearPassStatus.PROCESSING);
        assertThat(processing.getStripeCheckoutSessionId()).isEqualTo(session.id());
        assertThat(processing.getStripePaymentIntentId()).isEqualTo(session.paymentIntentId());
        assertThat(processing.getAmountTotal()).isEqualTo(3900L);
        assertThat(processing.getCurrency()).isEqualTo("CAD");
        assertThat(processing.getPaidAt()).isNull();

        postWebhook(checkoutSessionEvent("checkout.session.async_payment_succeeded", session.withPaymentStatus("paid")))
                .andExpect(status().isOk());

        TaxYearPass paid = reload(pass);
        assertThat(paid.getStatus()).isEqualTo(TaxYearPassStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
    }

    @Test
    void failedDelayedPaymentMarksThePassFailed() throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        CheckoutSession session = CheckoutSession.unpaid(pass);

        postWebhook(checkoutSessionEvent("checkout.session.completed", session)).andExpect(status().isOk());
        postWebhook(checkoutSessionEvent("checkout.session.async_payment_failed", session)).andExpect(status().isOk());

        TaxYearPass failed = reload(pass);
        assertThat(failed.getStatus()).isEqualTo(TaxYearPassStatus.FAILED);
        assertThat(failed.getPaidAt()).isNull();
    }

    @Test
    void fullRefundRevokesThePass() throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        CheckoutSession session = CheckoutSession.paid(pass);
        postWebhook(checkoutSessionEvent("checkout.session.completed", session)).andExpect(status().isOk());

        postWebhook(chargeRefundedEvent(session.paymentIntentId(), true)).andExpect(status().isOk());

        TaxYearPass refunded = reload(pass);
        assertThat(refunded.getStatus()).isEqualTo(TaxYearPassStatus.REFUNDED);
        assertThat(refunded.getRefundedAt()).isNotNull();
        assertThat(refunded.getPaidAt()).isNotNull();
    }

    @Test
    void partialRefundKeepsThePassUnlocked() throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        CheckoutSession session = CheckoutSession.paid(pass);
        postWebhook(checkoutSessionEvent("checkout.session.completed", session)).andExpect(status().isOk());

        postWebhook(chargeRefundedEvent(session.paymentIntentId(), false)).andExpect(status().isOk());

        TaxYearPass stillPaid = reload(pass);
        assertThat(stillPaid.getStatus()).isEqualTo(TaxYearPassStatus.PAID);
        assertThat(stillPaid.getRefundedAt()).isNull();
    }

    @Test
    void replayedCompletedEventIsIdempotent() throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        String payload = checkoutSessionEvent("checkout.session.completed", CheckoutSession.paid(pass));

        postWebhook(payload).andExpect(status().isOk());
        TaxYearPass afterFirstDelivery = reload(pass);
        postWebhook(payload).andExpect(status().isOk());

        TaxYearPass afterReplay = reload(pass);
        assertThat(afterReplay.getStatus()).isEqualTo(TaxYearPassStatus.PAID);
        assertThat(afterReplay.getPaidAt()).isEqualTo(afterFirstDelivery.getPaidAt());
        assertThat(afterReplay.getUpdatedAt()).isEqualTo(afterFirstDelivery.getUpdatedAt());
    }

    @Test
    void asyncSuccessDeliveredBeforeCompletedStillEndsPaid(CapturedOutput output) throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        CheckoutSession session = CheckoutSession.unpaid(pass);

        postWebhook(checkoutSessionEvent("checkout.session.async_payment_succeeded", session.withPaymentStatus("paid")))
                .andExpect(status().isOk());
        postWebhook(checkoutSessionEvent("checkout.session.completed", session)).andExpect(status().isOk());

        TaxYearPass updated = reload(pass);
        assertThat(updated.getStatus()).isEqualTo(TaxYearPassStatus.PAID);
        assertThat(updated.getStripeCheckoutSessionId()).isEqualTo(session.id());
        assertThat(output).doesNotContain("checkout session " + session.id() + " with payment intent");
    }

    @Test
    void lateUnpaidDeliveryDoesNotReopenASessionThatAlreadyFailed() throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        CheckoutSession session = CheckoutSession.unpaid(pass);

        postWebhook(checkoutSessionEvent("checkout.session.async_payment_failed", session)).andExpect(status().isOk());
        postWebhook(checkoutSessionEvent("checkout.session.completed", session)).andExpect(status().isOk());

        assertThat(reload(pass).getStatus()).isEqualTo(TaxYearPassStatus.FAILED);
    }

    @Test
    void secondPaymentForAnUnlockedPassIsLoggedForRefundNotApplied(CapturedOutput output) throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        CheckoutSession firstSession = CheckoutSession.paid(pass);
        CheckoutSession duplicateSession = new CheckoutSession(pass.getId().toString(), uniqueId("cs_test_"),
                "paid", 2900, uniqueId("pi_test_"));
        postWebhook(checkoutSessionEvent("checkout.session.completed", firstSession)).andExpect(status().isOk());
        TaxYearPass afterFirstPayment = reload(pass);

        postWebhook(checkoutSessionEvent("checkout.session.completed", duplicateSession)).andExpect(status().isOk());

        TaxYearPass updated = reload(pass);
        assertThat(updated.getStatus()).isEqualTo(TaxYearPassStatus.PAID);
        assertThat(updated.getStripeCheckoutSessionId()).isEqualTo(firstSession.id());
        assertThat(updated.getStripePaymentIntentId()).isEqualTo(firstSession.paymentIntentId());
        assertThat(updated.getAmountTotal()).isEqualTo(3900L);
        assertThat(updated.getPaidAt()).isEqualTo(afterFirstPayment.getPaidAt());
        assertThat(output).containsSubsequence(
                "WARN",
                "tax year pass " + pass.getId() + " is already PAID",
                "checkout session " + duplicateSession.id() + " with payment intent " + duplicateSession.paymentIntentId(),
                "Refund that payment in Stripe"
        );
    }

    @Test
    void paymentThroughAnOldLinkForARefundedPassIsLoggedForRefundNotApplied(CapturedOutput output) throws Exception {
        TaxYearPass refundedPass = savePass(TaxYearPassStatus.REFUNDED);

        postWebhook(checkoutSessionEvent("checkout.session.completed", CheckoutSession.paid(refundedPass)))
                .andExpect(status().isOk());

        assertThat(reload(refundedPass).getStatus()).isEqualTo(TaxYearPassStatus.REFUNDED);
        assertThat(output).contains("tax year pass " + refundedPass.getId() + " is already REFUNDED");
    }

    @Test
    void sessionsWithoutAUsablePassReferenceAreAcknowledgedWithoutChanges(CapturedOutput output) throws Exception {
        TaxYearPass untouchedPass = savePass(TaxYearPassStatus.PENDING);
        UUID unknownPassId = UUID.randomUUID();

        for (String clientReferenceId : new String[]{null, "not-a-pass-id", unknownPassId.toString()}) {
            CheckoutSession session = new CheckoutSession(clientReferenceId, uniqueId("cs_test_"), "paid", 3900,
                    uniqueId("pi_test_"));
            postWebhook(checkoutSessionEvent("checkout.session.completed", session)).andExpect(status().isOk());
        }

        assertThat(taxYearPassRepository.findById(unknownPassId)).isEmpty();
        TaxYearPass unchanged = reload(untouchedPass);
        assertThat(unchanged.getStatus()).isEqualTo(TaxYearPassStatus.PENDING);
        assertThat(unchanged.getStripeCheckoutSessionId()).isNull();
        assertThat(output).contains("no tax year pass " + unknownPassId + "; acknowledged without changes");
        assertThat(output).contains("missing or malformed client_reference_id; acknowledged without changes");
    }

    @Test
    void rejectsPayloadsThatAreNotSignedWithTheWebhookSecret() throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        String payload = checkoutSessionEvent("checkout.session.completed", CheckoutSession.paid(pass));
        String signedByAnotherSecret = Webhook.Signature.generateSignatureHeader(payload, "whsec_another_secret");
        String signedThenTampered = Webhook.Signature.generateSignatureHeader(payload, WEBHOOK_SECRET);
        String signedTooLongAgo = Webhook.Signature.generateSignatureHeader(
                payload,
                WEBHOOK_SECRET,
                Instant.now().minusSeconds(Webhook.DEFAULT_TOLERANCE + 60).getEpochSecond()
        );

        postWebhook(payload, signedByAnotherSecret)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid Stripe-Signature header"));
        postWebhook(payload.replace("\"amount_total\": 3900", "\"amount_total\": 1"), signedThenTampered)
                .andExpect(status().isBadRequest());
        postWebhook(payload, signedTooLongAgo).andExpect(status().isBadRequest());

        assertThat(reload(pass).getStatus()).isEqualTo(TaxYearPassStatus.PENDING);
    }

    @Test
    void rejectsAMissingSignatureHeader() throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        String payload = checkoutSessionEvent("checkout.session.completed", CheckoutSession.paid(pass));

        mockMvc.perform(post(ApiPaths.BILLING_STRIPE_WEBHOOK).contentType(STRIPE_CONTENT_TYPE).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing Stripe-Signature header"));

        assertThat(reload(pass).getStatus()).isEqualTo(TaxYearPassStatus.PENDING);
    }

    @Test
    void webhookNeedsNoSessionOrCsrfTokenAndAcknowledgesOtherEventTypes() throws Exception {
        String payload = """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "%s",
                  "created": %d,
                  "livemode": false,
                  "pending_webhooks": 1,
                  "request": {"id": null, "idempotency_key": null},
                  "type": "customer.created",
                  "data": {"object": {"id": "cus_test_buyer", "object": "customer", "email": "%s"}}
                }
                """.formatted(uniqueId("evt_test_"), SDK_API_VERSION, Instant.now().getEpochSecond(), BUYER_EMAIL);

        // No X-User-Id, no session and no CSRF token: the Stripe signature is the only credential.
        MvcResult result = postWebhook(payload).andExpect(status().isOk()).andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void appliesEventsSentAtAnotherStripeApiVersion() throws Exception {
        TaxYearPass pass = savePass(TaxYearPassStatus.PENDING);
        String payload = checkoutSessionEvent("checkout.session.completed", "2025-03-31.basil", CheckoutSession.paid(pass));
        Event event = Webhook.constructEvent(payload, sign(payload), WEBHOOK_SECRET);
        assertThat(event.getDataObjectDeserializer().getObject())
                .as("stripe-java only binds its pinned API version, so this event needs deserializeUnsafe")
                .isEmpty();

        postWebhook(payload).andExpect(status().isOk());

        assertThat(reload(pass).getStatus()).isEqualTo(TaxYearPassStatus.PAID);
    }

    private ResultActions postWebhook(String payload) throws Exception {
        return postWebhook(payload, sign(payload));
    }

    private ResultActions postWebhook(String payload, String signatureHeader) throws Exception {
        return mockMvc.perform(
                post(ApiPaths.BILLING_STRIPE_WEBHOOK)
                        .contentType(STRIPE_CONTENT_TYPE)
                        .header("Stripe-Signature", signatureHeader)
                        .content(payload)
        );
    }

    private static String sign(String payload) throws Exception {
        return Webhook.Signature.generateSignatureHeader(payload, WEBHOOK_SECRET);
    }

    private TaxYearPass savePass(TaxYearPassStatus status) {
        User user = userService.getOrCreateUser("webhook-user-" + UUID.randomUUID(), null);
        TaxYearPass pass = new TaxYearPass();
        pass.setUserId(user.getId());
        pass.setTaxYear(2025);
        pass.setStatus(status);
        return taxYearPassRepository.save(pass);
    }

    private TaxYearPass reload(TaxYearPass pass) {
        return taxYearPassRepository.findById(pass.getId()).orElseThrow();
    }

    private static String checkoutSessionEvent(String eventType, CheckoutSession session) {
        return checkoutSessionEvent(eventType, SDK_API_VERSION, session);
    }

    /** A trimmed but realistic Stripe event; the non-ASCII name checks the raw body is verified as UTF-8. */
    private static String checkoutSessionEvent(String eventType, String apiVersion, CheckoutSession session) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "%s",
                  "created": %d,
                  "livemode": false,
                  "pending_webhooks": 1,
                  "request": {"id": null, "idempotency_key": null},
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session",
                      "amount_subtotal": %d,
                      "amount_total": %d,
                      "client_reference_id": %s,
                      "currency": "cad",
                      "customer": "cus_test_buyer",
                      "customer_details": {"email": "%s", "name": "Zoë Buyer"},
                      "livemode": false,
                      "mode": "payment",
                      "payment_intent": %s,
                      "payment_link": "plink_test_tax_year_pass",
                      "payment_status": "%s",
                      "status": "complete"
                    }
                  }
                }
                """.formatted(
                uniqueId("evt_test_"),
                apiVersion,
                Instant.now().getEpochSecond(),
                eventType,
                session.id(),
                session.amountTotal(),
                session.amountTotal(),
                jsonString(session.clientReferenceId()),
                BUYER_EMAIL,
                jsonString(session.paymentIntentId()),
                session.paymentStatus()
        );
    }

    private static String chargeRefundedEvent(String paymentIntentId, boolean fullyRefunded) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "%s",
                  "created": %d,
                  "livemode": false,
                  "pending_webhooks": 1,
                  "request": {"id": null, "idempotency_key": null},
                  "type": "charge.refunded",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "charge",
                      "amount": 3900,
                      "amount_captured": 3900,
                      "amount_refunded": %d,
                      "billing_details": {"email": "%s", "name": "Zoë Buyer"},
                      "captured": true,
                      "currency": "cad",
                      "customer": "cus_test_buyer",
                      "livemode": false,
                      "paid": true,
                      "payment_intent": "%s",
                      "refunded": %b,
                      "status": "succeeded"
                    }
                  }
                }
                """.formatted(
                uniqueId("evt_test_"),
                SDK_API_VERSION,
                Instant.now().getEpochSecond(),
                uniqueId("ch_test_"),
                fullyRefunded ? 3900 : 1000,
                BUYER_EMAIL,
                paymentIntentId,
                fullyRefunded
        );
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String uniqueId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private record CheckoutSession(
            String clientReferenceId,
            String id,
            String paymentStatus,
            long amountTotal,
            String paymentIntentId
    ) {
        static CheckoutSession paid(TaxYearPass pass) {
            return new CheckoutSession(pass.getId().toString(), uniqueId("cs_test_"), "paid", 3900, uniqueId("pi_test_"));
        }

        static CheckoutSession unpaid(TaxYearPass pass) {
            return paid(pass).withPaymentStatus("unpaid");
        }

        CheckoutSession withPaymentStatus(String newPaymentStatus) {
            return new CheckoutSession(clientReferenceId, id, newPaymentStatus, amountTotal, paymentIntentId);
        }
    }
}
