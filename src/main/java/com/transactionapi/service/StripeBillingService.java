package com.transactionapi.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.billingportal.SessionCreateParams;
import com.transactionapi.model.User;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StripeBillingService {

    private final UserService userService;

    @Value("${app.stripe.secret-key:}")
    private String secretKey;

    @Value("${app.stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.stripe.pro-price-id:}")
    private String proPriceId;

    @Value("${app.stripe.success-url:}")
    private String successUrl;

    @Value("${app.stripe.cancel-url:}")
    private String cancelUrl;

    @Value("${app.stripe.billing-portal-return-url:}")
    private String billingPortalReturnUrl;

    public StripeBillingService(UserService userService) {
        this.userService = userService;
    }

    public String createCheckoutSession(String authId, String email) {
        requireCheckoutConfigured();
        User user = userService.getOrCreateUser(authId, email);
        String customerId = ensureStripeCustomer(user, authId, email);

        com.stripe.param.checkout.SessionCreateParams params =
                com.stripe.param.checkout.SessionCreateParams.builder()
                        .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                        .setCustomer(customerId)
                        .setClientReferenceId(authId)
                        .putMetadata("auth_id", authId)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .setAllowPromotionCodes(true)
                        .addLineItem(
                                com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                                        .setPrice(proPriceId)
                                        .setQuantity(1L)
                                        .build()
                        )
                        .setSubscriptionData(
                                com.stripe.param.checkout.SessionCreateParams.SubscriptionData.builder()
                                        .putMetadata("auth_id", authId)
                                        .build()
                        )
                        .build();

        try {
            Session session = Session.create(params, requestOptions());
            return session.getUrl();
        } catch (StripeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not create Stripe checkout session", ex);
        }
    }

    public String createBillingPortalSession(String authId, String email) {
        requireStripeSecret();
        User user = userService.getOrCreateUser(authId, email);
        if (!StringUtils.hasText(user.getStripeCustomerId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No Stripe customer is linked to this account");
        }
        if (!StringUtils.hasText(billingPortalReturnUrl)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe billing portal is not configured");
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(user.getStripeCustomerId())
                .setReturnUrl(billingPortalReturnUrl)
                .build();

        try {
            com.stripe.model.billingportal.Session session =
                    com.stripe.model.billingportal.Session.create(params, requestOptions());
            return session.getUrl();
        } catch (StripeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not create Stripe billing portal session", ex);
        }
    }

    public void handleWebhook(String payload, String signatureHeader) {
        if (!StringUtils.hasText(webhookSecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe webhook is not configured");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook signature", ex);
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted" ->
                    handleSubscriptionEvent(event);
            default -> {
                // Ignore unrelated Stripe events.
            }
        }
    }

    private String ensureStripeCustomer(User user, String authId, String email) {
        if (StringUtils.hasText(user.getStripeCustomerId())) {
            return user.getStripeCustomerId();
        }

        CustomerCreateParams.Builder params = CustomerCreateParams.builder()
                .putMetadata("auth_id", authId);
        if (StringUtils.hasText(email)) {
            params.setEmail(email);
        }

        try {
            Customer customer = Customer.create(params.build(), requestOptions());
            userService.updateStripeCustomerId(authId, email, customer.getId());
            return customer.getId();
        } catch (StripeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not create Stripe customer", ex);
        }
    }

    private void handleCheckoutCompleted(Event event) {
        Session session = deserialize(event, Session.class);
        if (!"subscription".equals(session.getMode())) {
            return;
        }

        String authId = firstNonBlank(
                metadataValue(session.getMetadata(), "auth_id"),
                session.getClientReferenceId()
        );
        userService.updateStripeSubscriptionFromCheckout(
                authId,
                session.getCustomer(),
                session.getSubscription()
        );
    }

    private void handleSubscriptionEvent(Event event) {
        Subscription subscription = deserialize(event, Subscription.class);
        userService.updateStripeSubscriptionStatus(
                subscription.getCustomer(),
                subscription.getId(),
                subscription.getStatus()
        );
    }

    private <T> T deserialize(Event event, Class<T> type) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject object = deserializer.getObject()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Could not deserialize Stripe webhook payload"
                ));
        if (!type.isInstance(object)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unexpected Stripe webhook payload type");
        }
        return type.cast(object);
    }

    private String metadataValue(Map<String, String> metadata, String key) {
        return metadata == null ? null : metadata.get(key);
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private void requireCheckoutConfigured() {
        requireStripeSecret();
        if (!StringUtils.hasText(proPriceId)
                || !StringUtils.hasText(successUrl)
                || !StringUtils.hasText(cancelUrl)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe checkout is not configured");
        }
    }

    private void requireStripeSecret() {
        if (!StringUtils.hasText(secretKey)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe billing is not configured");
        }
    }

    private RequestOptions requestOptions() {
        return RequestOptions.builder()
                .setApiKey(secretKey)
                .build();
    }
}
