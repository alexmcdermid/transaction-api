package com.transactionapi.service;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.transactionapi.config.BillingSettings;
import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.service.TaxYearPassService.CheckoutSessionDetails;
import com.transactionapi.service.TaxYearPassService.WebhookUpdate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifies and applies Stripe webhook events for tax year passes. Signature verification is a local
 * HMAC check; this service never calls the Stripe API.
 *
 * <p>Logs carry the event id, event type, pass id and outcome only, never emails or payloads.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private static final String CHECKOUT_SESSION_COMPLETED = "checkout.session.completed";
    private static final String CHECKOUT_SESSION_ASYNC_PAYMENT_SUCCEEDED = "checkout.session.async_payment_succeeded";
    private static final String CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED = "checkout.session.async_payment_failed";
    private static final String CHARGE_REFUNDED = "charge.refunded";

    private final BillingSettings billingSettings;
    private final TaxYearPassService taxYearPassService;

    public StripeWebhookService(BillingSettings billingSettings, TaxYearPassService taxYearPassService) {
        this.billingSettings = billingSettings;
        this.taxYearPassService = taxYearPassService;
    }

    public void handleWebhook(String payload, String signatureHeader) {
        Event event = verifiedEvent(payload, signatureHeader);
        switch (event.getType()) {
            case CHECKOUT_SESSION_COMPLETED,
                    CHECKOUT_SESSION_ASYNC_PAYMENT_SUCCEEDED,
                    CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED -> handleCheckoutSessionEvent(event);
            case CHARGE_REFUNDED -> handleChargeRefunded(event);
            default -> log.info("Stripe event {} ({}) ignored", event.getId(), event.getType());
        }
    }

    private Event verifiedEvent(String payload, String signatureHeader) {
        if (!billingSettings.isCheckoutEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe webhook is not configured");
        }
        if (!StringUtils.hasText(signatureHeader)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Stripe-Signature header");
        }
        try {
            return Webhook.constructEvent(payload, signatureHeader, billingSettings.getWebhookSecret());
        } catch (SignatureVerificationException ex) {
            log.warn("Rejected Stripe webhook: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe-Signature header");
        } catch (NumberFormatException | IndexOutOfBoundsException ex) {
            // stripe-java parses the header without guards, so a malformed t= or v1= element ("t=abc", a bare
            // "v1") escapes as one of these instead of a SignatureVerificationException.
            log.warn("Rejected Stripe webhook: malformed Stripe-Signature header");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe-Signature header");
        }
    }

    private void handleCheckoutSessionEvent(Event event) {
        Session session = dataObject(event, Session.class);
        Optional<UUID> passId = parsePassId(session.getClientReferenceId());
        if (passId.isEmpty()) {
            log.warn(
                    "Stripe event {} ({}) has a missing or malformed client_reference_id; acknowledged without changes",
                    event.getId(),
                    event.getType()
            );
            return;
        }
        CheckoutSessionDetails details = new CheckoutSessionDetails(
                session.getId(),
                session.getPaymentIntent(),
                session.getCustomer(),
                session.getAmountTotal(),
                session.getCurrency()
        );
        WebhookUpdate update = taxYearPassService.applyCheckoutSession(
                passId.get(),
                targetStatus(event, session),
                details
        );
        switch (update.outcome()) {
            case DUPLICATE_PAYMENT -> log.warn(
                    "Stripe event {} ({}): tax year pass {} is already {}, so checkout session {} with payment intent {} "
                            + "was not applied. Refund that payment in Stripe.",
                    event.getId(),
                    event.getType(),
                    update.passId(),
                    update.status(),
                    details.checkoutSessionId(),
                    details.paymentIntentId()
            );
            case PASS_NOT_FOUND -> log.warn(
                    "Stripe event {} ({}): no tax year pass {}; acknowledged without changes",
                    event.getId(),
                    event.getType(),
                    update.passId()
            );
            default -> logUpdate(event, update);
        }
    }

    private void handleChargeRefunded(Event event) {
        Charge charge = dataObject(event, Charge.class);
        if (!Boolean.TRUE.equals(charge.getRefunded())) {
            log.info("Stripe event {} ({}) is a partial refund; tax year pass access kept", event.getId(), event.getType());
            return;
        }
        if (!StringUtils.hasText(charge.getPaymentIntent())) {
            log.info("Stripe event {} ({}) refunds a charge without a payment intent; ignored", event.getId(), event.getType());
            return;
        }
        List<WebhookUpdate> updates = taxYearPassService.markRefunded(charge.getPaymentIntent());
        if (updates.isEmpty()) {
            // Usually another product's charge. But a pass learns its payment intent from its checkout event,
            // so a refund that overtakes a delayed checkout delivery lands here and that pass would still unlock.
            log.warn(
                    "Stripe event {} ({}): full refund of payment intent {} matches no tax year pass; acknowledged "
                            + "without changes. If a tax year pass records this payment intent later, revoke it by hand.",
                    event.getId(),
                    event.getType(),
                    charge.getPaymentIntent()
            );
        }
        updates.forEach(update -> logUpdate(event, update));
    }

    private static TaxYearPassStatus targetStatus(Event event, Session session) {
        if (CHECKOUT_SESSION_ASYNC_PAYMENT_SUCCEEDED.equals(event.getType())) {
            return TaxYearPassStatus.PAID;
        }
        if (CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED.equals(event.getType())) {
            return TaxYearPassStatus.FAILED;
        }
        return switch (session.getPaymentStatus()) {
            case "paid", "no_payment_required" -> TaxYearPassStatus.PAID;
            // Delayed payment methods such as ACSS debit complete checkout before the money clears.
            case "unpaid" -> TaxYearPassStatus.PROCESSING;
            case null, default -> throw processingFailure(event, "unexpected payment_status " + session.getPaymentStatus());
        };
    }

    /**
     * stripe-java only binds data objects from the API release it pins, so {@code getObject()} is empty
     * when the webhook endpoint uses another release. Fall back to a best-effort bind.
     */
    private static <T extends StripeObject> T dataObject(Event event, Class<T> expectedType) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject dataObject = deserializer.getObject().orElseGet(() -> {
            try {
                return deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException ex) {
                throw processingFailure(event, "data object unreadable at API version " + event.getApiVersion());
            }
        });
        if (!expectedType.isInstance(dataObject)) {
            throw processingFailure(event, "data object is not a " + expectedType.getSimpleName());
        }
        return expectedType.cast(dataObject);
    }

    /** A 5xx makes Stripe retry, so the event stays visible in Stripe until the code can handle it. */
    private static ResponseStatusException processingFailure(Event event, String problem) {
        log.error("Stripe event {} ({}) could not be processed: {}", event.getId(), event.getType(), problem);
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stripe event could not be processed");
    }

    private static Optional<UUID> parsePassId(String clientReferenceId) {
        if (!StringUtils.hasText(clientReferenceId)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(clientReferenceId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static void logUpdate(Event event, WebhookUpdate update) {
        log.info(
                "Stripe event {} ({}) for tax year pass {}: {}, status {}",
                event.getId(),
                event.getType(),
                update.passId(),
                update.outcome(),
                update.status()
        );
    }
}
