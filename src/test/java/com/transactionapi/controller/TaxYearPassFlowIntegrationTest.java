package com.transactionapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.stripe.net.Webhook;
import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.TaxYearReportType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Year;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * One buyer's tax year pass, end to end over HTTP: locked reports, checkout, a signed Stripe webhook,
 * unlocked summary and exports, then a full refund that locks the year again.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.billing.stripe.webhook-secret=" + TaxYearPassFlowIntegrationTest.WEBHOOK_SECRET,
        "app.billing.tax-year-pass.payment-link-url=https://buy.stripe.com/test_link",
        "app.billing.tax-year-pass.payment-link-id=" + TaxYearPassFlowIntegrationTest.PAYMENT_LINK_ID,
        "app.billing.tax-year-pass.price-cents=3900",
        "app.billing.tax-year-pass.price-currency=CAD"
})
class TaxYearPassFlowIntegrationTest {

    static final String WEBHOOK_SECRET = "whsec_test_secret";
    static final String PAYMENT_LINK_ID = "plink_test_link";
    private static final String USER_ID_HEADER = "X-User-Id";
    /** stripe-java 34.1.0's pinned API version. */
    private static final String STRIPE_API_VERSION = "2026-10-28.endive";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void buyingATaxYearPassUnlocksReportsUntilItIsRefunded() throws Exception {
        int taxYear = Year.now().getValue() - 1;
        String buyer = "flow-buyer-" + UUID.randomUUID();
        String otherUser = "flow-other-user-" + UUID.randomUUID();
        String passRequired = "Tax year pass required for " + taxYear;

        String usdStockWinId = createTrade(buyer, """
                {"symbol": "AAPL", "assetType": "STOCK", "currency": "USD", "direction": "LONG",
                 "quantity": 10, "entryPrice": 150.00, "exitPrice": 165.50, "fees": 1.00,
                 "openedAt": "%1$d-03-03", "closedAt": "%1$d-03-10"}
                """.formatted(taxYear));
        String cadOptionLossId = createTrade(buyer, """
                {"symbol": "SHOP", "assetType": "OPTION", "currency": "CAD", "direction": "LONG",
                 "quantity": 2, "entryPrice": 3.25, "exitPrice": 1.10, "fees": 2.50,
                 "optionType": "CALL", "strikePrice": 120.00, "expiryDate": "%1$d-06-20",
                 "openedAt": "%1$d-05-01", "closedAt": "%1$d-06-02"}
                """.formatted(taxYear));
        String otherUsersTradeId = createTrade(otherUser, """
                {"symbol": "TSLA", "assetType": "STOCK", "currency": "USD", "direction": "LONG",
                 "quantity": 1, "entryPrice": 200.00, "exitPrice": 210.00,
                 "openedAt": "%1$d-04-15", "closedAt": "%1$d-04-15"}
                """.formatted(taxYear));

        // The overview is free and offers the year for sale.
        getOverview(buyer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutEnabled").value(true))
                .andExpect(jsonPath("$.price.amountCents").value(3900))
                .andExpect(jsonPath("$.price.currency").value("CAD"))
                .andExpect(jsonPath("$.years[*].taxYear").value(contains(taxYear + 1, taxYear)))
                .andExpect(jsonPath("$.years[1].closedTradeCount").value(2))
                .andExpect(jsonPath("$.years[1].passStatus").value(nullValue()))
                .andExpect(jsonPath("$.years[1].unlocked").value(false))
                .andExpect(jsonPath("$.years[1].purchasable").value(true));
        postSummary(buyer, taxYear)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(passRequired));
        postExport(buyer, taxYear, TaxYearReportType.TRADE_LEDGER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(passRequired));

        String passId = startCheckout(buyer, taxYear);
        String paymentIntentId = uniqueId("pi_test_");

        // Stripe calls the webhook with no session, CSRF token or X-User-Id; the signature is the credential.
        postSignedWebhook(checkoutSessionCompletedEvent(passId, paymentIntentId)).andExpect(status().isOk());

        getOverview(buyer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.years[1].passStatus").value("PAID"))
                .andExpect(jsonPath("$.years[1].unlocked").value(true))
                .andExpect(jsonPath("$.years[1].purchasable").value(false));
        mockMvc.perform(get(ApiPaths.BILLING_TAX_YEAR_PASSES).header(USER_ID_HEADER, buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(passId))
                .andExpect(jsonPath("$[0].taxYear").value(taxYear))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].amountTotal").value(3900))
                .andExpect(jsonPath("$[0].currency").value("CAD"))
                .andExpect(jsonPath("$[0].paidAt").isNotEmpty());

        // Each currency is totalled on its own row; nothing is converted.
        postSummary(buyer, taxYear)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxYear").value(taxYear))
                .andExpect(jsonPath("$.tradeCount").value(2))
                .andExpect(jsonPath("$.firstClosedOn").value(taxYear + "-03-10"))
                .andExpect(jsonPath("$.lastClosedOn").value(taxYear + "-06-02"))
                .andExpect(jsonPath("$.repurchaseFlagCount").value(0))
                .andExpect(jsonPath("$.totals[*].currency").value(contains("CAD", "USD")))
                .andExpect(jsonPath("$.totals[*].tradeCount").value(contains(1, 1)))
                .andExpect(jsonPath("$.totals[0].losses").value(-432.5))
                .andExpect(jsonPath("$.totals[0].fees").value(2.5))
                .andExpect(jsonPath("$.totals[0].netRealizedPnl").value(-432.5))
                .andExpect(jsonPath("$.totals[1].gains").value(154.0))
                .andExpect(jsonPath("$.totals[1].fees").value(1.0))
                .andExpect(jsonPath("$.totals[1].netRealizedPnl").value(154.0))
                .andExpect(jsonPath("$.assetTypes[*].key").value(contains("STOCK", "OPTION_CALL")))
                .andExpect(jsonPath("$.assetTypes[*].currency").value(contains("USD", "CAD")));

        Map<TaxYearReportType, List<CSVRecord>> exports = new EnumMap<>(TaxYearReportType.class);
        for (TaxYearReportType reportType : TaxYearReportType.values()) {
            String csv = postExport(buyer, taxYear, reportType)
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("text/csv;charset=UTF-8"))
                    .andExpect(header().string(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"tradelog-" + taxYear + "-" + reportType.slug() + ".csv\""
                    ))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            assertThat(csv).doesNotContain(otherUsersTradeId);
            exports.put(reportType, parseCsv(csv));
        }
        List<CSVRecord> ledger = exports.get(TaxYearReportType.TRADE_LEDGER);
        assertThat(ledger).extracting(record -> record.get("trade_id")).containsExactly(usdStockWinId, cadOptionLossId);
        CSVRecord cadOptionLoss = ledger.get(1);
        assertThat(cadOptionLoss.get("currency")).isEqualTo("CAD");
        assertThat(cadOptionLoss.get("option_type")).isEqualTo("CALL");
        assertThat(cadOptionLoss.get("strike_price")).isEqualTo("120");
        assertThat(cadOptionLoss.get("quantity")).isEqualTo("2");
        assertThat(cadOptionLoss.get("exit_price")).isEqualTo("1.1");
        assertThat(cadOptionLoss.get("gross_pnl")).isEqualTo("-430.00");
        assertThat(cadOptionLoss.get("recorded_realized_pnl")).isEqualTo("-432.50");
        assertThat(exports.get(TaxYearReportType.DISPOSITIONS))
                .extracting(
                        record -> record.get("description"),
                        record -> record.get("currency"),
                        record -> record.get("proceeds"),
                        record -> record.get("cost"),
                        record -> record.get("outlays_and_expenses"),
                        record -> record.get("gain_loss")
                )
                .containsExactly(
                        tuple("10 AAPL", "USD", "1655.00", "1500.00", "1.00", "154.00"),
                        tuple("2 SHOP " + taxYear + "-06-20 120 CALL", "CAD", "220.00", "650.00", "2.50", "-432.50")
                );
        assertThat(exports.get(TaxYearReportType.ANNUAL_SUMMARY))
                .filteredOn(record -> record.get("section").equals("TOTAL"))
                .extracting(record -> record.get("currency"), record -> record.get("net_realized_pnl"))
                .containsExactly(tuple("CAD", "-432.50"), tuple("USD", "154.00"));
        assertThat(exports.get(TaxYearReportType.REPURCHASE_REVIEW)).isEmpty();

        postSignedWebhook(fullRefundEvent(paymentIntentId)).andExpect(status().isOk());

        postSummary(buyer, taxYear)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(passRequired));
        postExport(buyer, taxYear, TaxYearReportType.TRADE_LEDGER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(passRequired));
        getOverview(buyer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.years[1].passStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.years[1].unlocked").value(false))
                .andExpect(jsonPath("$.years[1].purchasable").value(true));

        // A refunded pass is terminal, so buying the year again starts a new pass.
        String secondPassId = startCheckout(buyer, taxYear);

        assertThat(secondPassId).isNotEqualTo(passId);
        mockMvc.perform(get(ApiPaths.BILLING_TAX_YEAR_PASSES).header(USER_ID_HEADER, buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(secondPassId, passId)))
                .andExpect(jsonPath("$[*].status").value(contains("PENDING", "REFUNDED")));
    }

    private String createTrade(String authId, String tradeJson) throws Exception {
        String body = mockMvc.perform(post(ApiPaths.TRADES)
                        .header(USER_ID_HEADER, authId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeJson))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions getOverview(String authId) throws Exception {
        return mockMvc.perform(get(ApiPaths.REPORTS_TAX_YEARS).header(USER_ID_HEADER, authId));
    }

    private ResultActions postSummary(String authId, int taxYear) throws Exception {
        return mockMvc.perform(post(ApiPaths.REPORTS_TAX_YEARS + "/" + taxYear + "/summary")
                .header(USER_ID_HEADER, authId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    private ResultActions postExport(String authId, int taxYear, TaxYearReportType reportType) throws Exception {
        return mockMvc.perform(post(ApiPaths.REPORTS_TAX_YEARS + "/" + taxYear + "/exports")
                .header(USER_ID_HEADER, authId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportType\":\"" + reportType.name() + "\"}"));
    }

    /** Starts checkout and returns the pass id that the Payment Link carries as client_reference_id. */
    private String startCheckout(String authId, int taxYear) throws Exception {
        String body = mockMvc.perform(post(ApiPaths.BILLING_TAX_YEAR_PASSES + "/" + taxYear + "/checkout")
                        .header(USER_ID_HEADER, authId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String checkoutUrl = JsonPath.read(body, "$.checkoutUrl");
        assertThat(checkoutUrl).startsWith("https://buy.stripe.com/test_link?client_reference_id=");
        String passId = UriComponentsBuilder.fromUriString(checkoutUrl).build().getQueryParams()
                .getFirst("client_reference_id");
        assertThat(passId).isNotNull();
        return UUID.fromString(passId).toString();
    }

    private ResultActions postSignedWebhook(String payload) throws Exception {
        return mockMvc.perform(post(ApiPaths.BILLING_STRIPE_WEBHOOK)
                .contentType("application/json; charset=utf-8")
                .header("Stripe-Signature", Webhook.Signature.generateSignatureHeader(payload, WEBHOOK_SECRET))
                .content(payload));
    }

    private static String checkoutSessionCompletedEvent(String passId, String paymentIntentId) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "%s",
                  "created": %d,
                  "livemode": false,
                  "pending_webhooks": 1,
                  "request": {"id": null, "idempotency_key": null},
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session",
                      "amount_subtotal": 3900,
                      "amount_total": 3900,
                      "client_reference_id": "%s",
                      "currency": "cad",
                      "customer": "cus_test_flow_buyer",
                      "livemode": false,
                      "mode": "payment",
                      "payment_intent": "%s",
                      "payment_link": "%s",
                      "payment_status": "paid",
                      "status": "complete"
                    }
                  }
                }
                """.formatted(
                uniqueId("evt_test_"),
                STRIPE_API_VERSION,
                Instant.now().getEpochSecond(),
                uniqueId("cs_test_"),
                passId,
                paymentIntentId,
                PAYMENT_LINK_ID
        );
    }

    private static String fullRefundEvent(String paymentIntentId) {
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
                      "amount_refunded": 3900,
                      "captured": true,
                      "currency": "cad",
                      "customer": "cus_test_flow_buyer",
                      "livemode": false,
                      "paid": true,
                      "payment_intent": "%s",
                      "refunded": true,
                      "status": "succeeded"
                    }
                  }
                }
                """.formatted(
                uniqueId("evt_test_"),
                STRIPE_API_VERSION,
                Instant.now().getEpochSecond(),
                uniqueId("ch_test_"),
                paymentIntentId
        );
    }

    private static List<CSVRecord> parseCsv(String csv) throws IOException {
        try (CSVParser parser = CSVParser.parse(csv, CSVFormat.DEFAULT.builder().setHeader().get())) {
            return parser.getRecords();
        }
    }

    private static String uniqueId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
