package com.transactionapi.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.TradeRequest;
import com.transactionapi.model.TaxYearPass;
import com.transactionapi.model.User;
import com.transactionapi.repository.TaxYearPassRepository;
import com.transactionapi.service.TradeService;
import com.transactionapi.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.billing.stripe.webhook-secret=whsec_test_secret",
        "app.billing.tax-year-pass.payment-link-url=https://buy.stripe.com/test_link",
        "app.billing.tax-year-pass.payment-link-id=plink_test_link",
        "app.billing.tax-year-pass.price-cents=3900",
        "app.billing.tax-year-pass.price-currency=CAD"
})
class ReportsControllerBillingEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private TaxYearPassRepository taxYearPassRepository;

    @Autowired
    private UserService userService;

    @Test
    void overviewShowsTheDisplayPriceAndWhichYearsCanBePurchased() throws Exception {
        String authId = "reports-billing-overview-user";
        int currentTaxYear = Year.now().getValue();
        for (int taxYear : new int[] {currentTaxYear + 1, 2025, 2024, 2023, 2022, 2021, 1999}) {
            recordTradeClosedIn(authId, taxYear);
        }
        grantPass(authId, 2025, TaxYearPassStatus.PAID);
        grantPass(authId, 2024, TaxYearPassStatus.PROCESSING);
        grantPass(authId, 2023, TaxYearPassStatus.FAILED);
        grantPass(authId, 2022, TaxYearPassStatus.REFUNDED);
        grantPass(authId, 2021, TaxYearPassStatus.PENDING);

        mockMvc.perform(get(ApiPaths.REPORTS_TAX_YEARS).header("X-User-Id", authId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTaxYear").value(currentTaxYear))
                .andExpect(jsonPath("$.checkoutEnabled").value(true))
                .andExpect(jsonPath("$.price.amountCents").value(3900))
                .andExpect(jsonPath("$.price.currency").value("CAD"))
                .andExpect(jsonPath("$.years[*].taxYear")
                        .value(contains(currentTaxYear + 1, currentTaxYear, 2025, 2024, 2023, 2022, 2021, 1999)))
                .andExpect(jsonPath("$.years[*].closedTradeCount").value(contains(1, 0, 1, 1, 1, 1, 1, 1)))
                .andExpect(jsonPath("$.years[*].passStatus")
                        .value(contains(null, null, "PAID", "PROCESSING", "FAILED", "REFUNDED", "PENDING", null)))
                .andExpect(jsonPath("$.years[*].unlocked")
                        .value(contains(false, false, true, false, false, false, false, false)))
                .andExpect(jsonPath("$.years[*].purchasable")
                        .value(contains(false, true, false, false, true, true, true, false)));
    }

    private void recordTradeClosedIn(String authId, int taxYear) {
        LocalDate closedOn = LocalDate.of(taxYear, 6, 15);
        tradeService.createTrade(new TradeRequest(
                "AAPL", AssetType.STOCK, Currency.USD, TradeDirection.LONG, new BigDecimal("1"),
                new BigDecimal("100.00"), new BigDecimal("101.00"), BigDecimal.ZERO, BigDecimal.ZERO, null,
                null, null, null, closedOn, closedOn, null
        ), authId);
    }

    private void grantPass(String authId, int taxYear, TaxYearPassStatus status) {
        User user = userService.getOrCreateUser(authId, null);
        TaxYearPass pass = new TaxYearPass();
        pass.setUserId(user.getId());
        pass.setTaxYear(taxYear);
        pass.setStatus(status);
        taxYearPassRepository.save(pass);
    }
}
