package com.transactionapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.stripe.Stripe;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class StripeBillingServiceTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";
    private static final String API_VERSION_FIELD = "\"api_version\": \"" + Stripe.API_VERSION + "\",";

    private UserService userService;
    private StripeBillingService stripeBillingService;

    @BeforeEach
    void setUp() {
        userService = org.mockito.Mockito.mock(UserService.class);
        stripeBillingService = new StripeBillingService(userService);
        ReflectionTestUtils.setField(stripeBillingService, "webhookSecret", WEBHOOK_SECRET);
    }

    @Test
    void checkoutCompletedWebhookLinksSubscriptionToAuthId() {
        String payload = """
                {
                  "id": "evt_checkout",
                  "object": "event",
                  %s
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test",
                      "object": "checkout.session",
                      "mode": "subscription",
                      "customer": "cus_test",
                      "subscription": "sub_test",
                      "client_reference_id": "auth-ref",
                      "metadata": {
                        "auth_id": "auth-meta"
                      }
                    }
                  }
                }
                """.formatted(API_VERSION_FIELD);

        stripeBillingService.handleWebhook(payload, signatureHeader(payload));

        verify(userService).updateStripeSubscriptionFromCheckout("auth-meta", "cus_test", "sub_test");
    }

    @Test
    void checkoutCompletedWebhookFallsBackToClientReferenceId() {
        String payload = """
                {
                  "id": "evt_checkout_reference",
                  "object": "event",
                  %s
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test",
                      "object": "checkout.session",
                      "mode": "subscription",
                      "customer": "cus_test",
                      "subscription": "sub_test",
                      "client_reference_id": "auth-ref",
                      "metadata": {}
                    }
                  }
                }
                """.formatted(API_VERSION_FIELD);

        stripeBillingService.handleWebhook(payload, signatureHeader(payload));

        verify(userService).updateStripeSubscriptionFromCheckout("auth-ref", "cus_test", "sub_test");
    }

    @Test
    void checkoutCompletedWebhookIgnoresNonSubscriptionSessions() {
        String payload = """
                {
                  "id": "evt_payment",
                  "object": "event",
                  %s
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test",
                      "object": "checkout.session",
                      "mode": "payment",
                      "customer": "cus_test",
                      "metadata": {
                        "auth_id": "auth-meta"
                      }
                    }
                  }
                }
                """.formatted(API_VERSION_FIELD);

        stripeBillingService.handleWebhook(payload, signatureHeader(payload));

        verifyNoInteractions(userService);
    }

    @Test
    void subscriptionWebhookUpdatesSubscriptionStatus() {
        String payload = """
                {
                  "id": "evt_subscription",
                  "object": "event",
                  %s
                  "type": "customer.subscription.updated",
                  "data": {
                    "object": {
                      "id": "sub_test",
                      "object": "subscription",
                      "customer": "cus_test",
                      "status": "active"
                    }
                  }
                }
                """.formatted(API_VERSION_FIELD);

        stripeBillingService.handleWebhook(payload, signatureHeader(payload));

        verify(userService).updateStripeSubscriptionStatus("cus_test", "sub_test", "active");
    }

    @Test
    void invalidWebhookSignatureReturnsBadRequest() {
        String payload = """
                {
                  "id": "evt_invalid",
                  "object": "event",
                  %s
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test",
                      "object": "checkout.session",
                      "mode": "subscription"
                    }
                  }
                }
                """.formatted(API_VERSION_FIELD);

        assertThatThrownBy(() -> stripeBillingService.handleWebhook(payload, "t=123,v1=bad-signature"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(userService);
    }

    @Test
    void missingWebhookSecretReturnsServiceUnavailable() {
        ReflectionTestUtils.setField(stripeBillingService, "webhookSecret", "");

        assertThatThrownBy(() -> stripeBillingService.handleWebhook("{}", ""))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        verifyNoInteractions(userService);
    }

    private String signatureHeader(String payload) {
        long timestamp = System.currentTimeMillis() / 1000L;
        return "t=" + timestamp + ",v1=" + hmacSha256(timestamp + "." + payload, WEBHOOK_SECRET);
    }

    private String hmacSha256(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign test Stripe payload", ex);
        }
    }
}
