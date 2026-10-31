package com.transactionapi.config;

import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Stripe settings for tax year pass purchases.
 *
 * <p>Checkout uses a Stripe Payment Link and an inbound webhook only. The backend never calls the
 * Stripe API, because the prod backend has VPC egress without NAT and cannot reach api.stripe.com.
 * Billing is disabled when both the webhook secret and payment link URL are blank; any other partial
 * configuration fails startup.
 */
@Component
public class BillingSettings {

    private static final String WEBHOOK_SECRET_PREFIX = "whsec_";

    private final String webhookSecret;
    private final String paymentLinkUrl;
    private final Long priceCents;
    private final String priceCurrency;

    public BillingSettings(
            @Value("${app.billing.stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.billing.tax-year-pass.payment-link-url:}") String paymentLinkUrl,
            @Value("${app.billing.tax-year-pass.price-cents:}") String priceCents,
            @Value("${app.billing.tax-year-pass.price-currency:}") String priceCurrency
    ) {
        this.webhookSecret = trimToNull(webhookSecret);
        this.paymentLinkUrl = trimToNull(paymentLinkUrl);
        this.priceCents = parsePriceCents(trimToNull(priceCents));
        String currency = trimToNull(priceCurrency);
        this.priceCurrency = currency == null ? null : currency.toUpperCase(Locale.ROOT);
        validate();
    }

    public boolean isCheckoutEnabled() {
        return webhookSecret != null && paymentLinkUrl != null;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public String getPaymentLinkUrl() {
        return paymentLinkUrl;
    }

    /** Display-only list price in minor units. Stripe remains the source of truth for the charge. */
    public Long getPriceCents() {
        return priceCents;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public boolean hasDisplayPrice() {
        return priceCents != null && priceCurrency != null;
    }

    private void validate() {
        if ((webhookSecret == null) != (paymentLinkUrl == null)) {
            throw new IllegalStateException(
                    "Set both app.billing.stripe.webhook-secret and app.billing.tax-year-pass.payment-link-url, "
                            + "or neither to disable tax year pass checkout"
            );
        }
        if (webhookSecret != null && !webhookSecret.startsWith(WEBHOOK_SECRET_PREFIX)) {
            throw new IllegalStateException(
                    "app.billing.stripe.webhook-secret must be a Stripe webhook signing secret (whsec_...)"
            );
        }
        if (paymentLinkUrl != null && !isHttpsUrl(paymentLinkUrl)) {
            throw new IllegalStateException("app.billing.tax-year-pass.payment-link-url must be an absolute https URL");
        }
        if ((priceCents == null) != (priceCurrency == null)) {
            throw new IllegalStateException(
                    "Set both app.billing.tax-year-pass.price-cents and app.billing.tax-year-pass.price-currency, or neither"
            );
        }
        if (priceCurrency != null && !priceCurrency.matches("[A-Z]{3}")) {
            throw new IllegalStateException("app.billing.tax-year-pass.price-currency must be a 3-letter ISO code");
        }
    }

    private static boolean isHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && StringUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static Long parsePriceCents(String value) {
        if (value == null) {
            return null;
        }
        try {
            long cents = Long.parseLong(value);
            if (cents < 0) {
                throw new IllegalStateException("app.billing.tax-year-pass.price-cents must not be negative");
            }
            return cents;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("app.billing.tax-year-pass.price-cents must be a whole number of cents", ex);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
