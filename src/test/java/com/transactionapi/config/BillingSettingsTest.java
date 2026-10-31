package com.transactionapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BillingSettingsTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";
    private static final String PAYMENT_LINK_URL = "https://buy.stripe.com/test_link";
    private static final String PAYMENT_LINK_ID = "plink_test_link";

    @Test
    void blankConfigurationDisablesCheckout() {
        BillingSettings settings = new BillingSettings("", "   ", null, null, "");

        assertThat(settings.isCheckoutEnabled()).isFalse();
        assertThat(settings.getWebhookSecret()).isNull();
        assertThat(settings.getPaymentLinkUrl()).isNull();
        assertThat(settings.getPaymentLinkId()).isNull();
        assertThat(settings.hasDisplayPrice()).isFalse();
        assertThat(settings.getPriceCents()).isNull();
        assertThat(settings.getPriceCurrency()).isNull();
    }

    @Test
    void secretPaymentLinkAndPaymentLinkIdEnableCheckoutWithoutADisplayPrice() {
        BillingSettings settings = new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, PAYMENT_LINK_ID, "", "");

        assertThat(settings.isCheckoutEnabled()).isTrue();
        assertThat(settings.getPaymentLinkId()).isEqualTo(PAYMENT_LINK_ID);
        assertThat(settings.hasDisplayPrice()).isFalse();
    }

    // An unquoted empty CSV value is null.
    @ParameterizedTest
    @CsvSource({
            WEBHOOK_SECRET + ",,",
            "," + PAYMENT_LINK_URL + ",",
            ",," + PAYMENT_LINK_ID,
            WEBHOOK_SECRET + "," + PAYMENT_LINK_URL + ",",
            WEBHOOK_SECRET + ",," + PAYMENT_LINK_ID,
            "," + PAYMENT_LINK_URL + "," + PAYMENT_LINK_ID
    })
    void rejectsAPartialCheckoutConfiguration(String webhookSecret, String paymentLinkUrl, String paymentLinkId) {
        assertThatThrownBy(() -> new BillingSettings(webhookSecret, paymentLinkUrl, paymentLinkId, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.billing.tax-year-pass.payment-link-id together, or none");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sk_test_123", "rk_live_123", "whsec"})
    void requiresAWebhookSigningSecret(String webhookSecret) {
        assertThatThrownBy(() -> new BillingSettings(webhookSecret, PAYMENT_LINK_URL, PAYMENT_LINK_ID, "", ""))
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
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, paymentLinkUrl, PAYMENT_LINK_ID, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absolute https URL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://buy.stripe.com/test_link", "test_link", "plink", "price_123", "prod_123"})
    void requiresAPaymentLinkId(String paymentLinkId) {
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, paymentLinkId, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stripe Payment Link id (plink_...)");
    }

    @Test
    void requiresDisplayPriceAmountAndCurrencyTogether() {
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, PAYMENT_LINK_ID, "3900", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Set both app.billing.tax-year-pass.price-cents");
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, PAYMENT_LINK_ID, "", "CAD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Set both app.billing.tax-year-pass.price-cents");
    }

    @Test
    void rejectsNegativeDisplayPrice() {
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, PAYMENT_LINK_ID, "-1", "CAD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void rejectsDisplayPriceThatIsNotWholeCents() {
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, PAYMENT_LINK_ID, "39.00", "CAD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("whole number of cents");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CA", "CADX", "C4D", "$$$"})
    void requiresAThreeLetterDisplayCurrency(String currency) {
        assertThatThrownBy(() -> new BillingSettings(WEBHOOK_SECRET, PAYMENT_LINK_URL, PAYMENT_LINK_ID, "3900", currency))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3-letter ISO code");
    }

    @Test
    void trimsValuesAndUpperCasesTheDisplayCurrency() {
        BillingSettings settings = new BillingSettings(
                "  " + WEBHOOK_SECRET + " ",
                " " + PAYMENT_LINK_URL + "  ",
                " " + PAYMENT_LINK_ID + " ",
                " 3900 ",
                " cad "
        );

        assertThat(settings.isCheckoutEnabled()).isTrue();
        assertThat(settings.getWebhookSecret()).isEqualTo(WEBHOOK_SECRET);
        assertThat(settings.getPaymentLinkUrl()).isEqualTo(PAYMENT_LINK_URL);
        assertThat(settings.getPaymentLinkId()).isEqualTo(PAYMENT_LINK_ID);
        assertThat(settings.hasDisplayPrice()).isTrue();
        assertThat(settings.getPriceCents()).isEqualTo(3900L);
        assertThat(settings.getPriceCurrency()).isEqualTo("CAD");
    }
}
