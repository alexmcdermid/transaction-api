package com.transactionapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BillingSettingsTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";
    private static final String PAYMENT_LINK_URL = "https://buy.stripe.com/test_link";

    @Test
    void blankConfigurationDisablesCheckout() {
        BillingSettings settings = new BillingSettings("", "   ", null, "");

        assertThat(settings.isCheckoutEnabled()).isFalse();
        assertThat(settings.getWebhookSecret()).isNull();
        assertThat(settings.getPaymentLinkUrl()).isNull();
        assertThat(settings.hasDisplayPrice()).isFalse();
        assertThat(settings.getPriceCents()).isNull();
        assertThat(settings.getPriceCurrency()).isNull();
    }

    @Test
    void secretAndPaymentLinkEnableCheckoutWithoutADisplayPrice() {
        BillingSettings settings = new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, "", "");

        assertThat(settings.isCheckoutEnabled()).isTrue();
        assertThat(settings.hasDisplayPrice()).isFalse();
    }

    @Test
    void rejectsWebhookSecretWithoutPaymentLink() {
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, "", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Set both app.billing.stripe.webhook-secret");
    }

    @Test
    void rejectsPaymentLinkWithoutWebhookSecret() {
        assertThatThrownBy(() -> new BillingSettings("", PAYMENT_LINK_URL, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Set both app.billing.stripe.webhook-secret");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sk_test_123", "rk_live_123", "whsec"})
    void requiresAWebhookSigningSecret(String webhookSecret) {
        assertThatThrownBy(() -> new BillingSettings(webhookSecret, PAYMENT_LINK_URL, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("whsec_");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://buy.stripe.com/test_link",
            "buy.stripe.com/test_link",
            "https:///test_link",
            "not a url"
    })
    void requiresAnAbsoluteHttpsPaymentLink(String paymentLinkUrl) {
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, paymentLinkUrl, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absolute https URL");
    }

    @Test
    void requiresDisplayPriceAmountAndCurrencyTogether() {
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, "3900", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Set both app.billing.tax-year-pass.price-cents");
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, "", "CAD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Set both app.billing.tax-year-pass.price-cents");
    }

    @Test
    void rejectsNegativeDisplayPrice() {
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, "-1", "CAD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void rejectsDisplayPriceThatIsNotWholeCents() {
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, "39.00", "CAD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("whole number of cents");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CA", "CADX", "C4D", "$$$"})
    void requiresAThreeLetterDisplayCurrency(String currency) {
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, "3900", currency))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3-letter ISO code");
    }

    @Test
    void trimsValuesAndUpperCasesTheDisplayCurrency() {
        BillingSettings settings = new BillingSettings(
                "  " + WEBHOOK_SECRET + " ",
                " " + PAYMENT_LINK_URL + "  ",
                " 3900 ",
                " cad "
        );

        assertThat(settings.isCheckoutEnabled()).isTrue();
        assertThat(settings.getWebhookSecret()).isEqualTo(WEBHOOK_SECRET);
        assertThat(settings.getPaymentLinkUrl()).isEqualTo(PAYMENT_LINK_URL);
        assertThat(settings.hasDisplayPrice()).isTrue();
        assertThat(settings.getPriceCents()).isEqualTo(3900L);
        assertThat(settings.getPriceCurrency()).isEqualTo("CAD");
    }
}
