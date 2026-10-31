package com.transactionapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.model.TaxYearPass;
import com.transactionapi.model.User;
import com.transactionapi.repository.TaxYearPassRepository;
import com.transactionapi.service.UserService;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.billing.stripe.webhook-secret=whsec_test_secret",
        "app.billing.tax-year-pass.payment-link-url=https://buy.stripe.com/test_link",
        "app.billing.tax-year-pass.payment-link-id=plink_test_link"
})
class BillingControllerTest {

    private static final String PAYMENT_LINK_URL = "https://buy.stripe.com/test_link";
    private static final int TAX_YEAR = 2025;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private TaxYearPassRepository taxYearPassRepository;

    @Test
    void checkoutCreatesAPendingPassAndPrefillsTheEncodedEmail() throws Exception {
        // A header-auth principal containing '@' doubles as the account email.
        String uniquePart = UUID.randomUUID().toString();
        String authId = "filer+" + uniquePart + "@example.com";

        String checkoutUrl = checkoutUrl(authId, TAX_YEAR);

        List<TaxYearPass> passes = passesOf(authId);
        assertThat(passes).hasSize(1);
        TaxYearPass pass = passes.getFirst();
        assertThat(pass.getStatus()).isEqualTo(TaxYearPassStatus.PENDING);
        assertThat(pass.getTaxYear()).isEqualTo(TAX_YEAR);
        assertThat(checkoutUrl).isEqualTo(
                PAYMENT_LINK_URL + "?client_reference_id=" + pass.getId()
                        + "&prefilled_email=filer%2B" + uniquePart + "%40example.com"
        );
    }

    @Test
    void checkoutOmitsPrefilledEmailWhenTheEmailIsUnknown() throws Exception {
        String authId = "checkout-no-email-" + UUID.randomUUID();

        String checkoutUrl = checkoutUrl(authId, TAX_YEAR);

        TaxYearPass pass = passesOf(authId).getFirst();
        assertThat(checkoutUrl).isEqualTo(PAYMENT_LINK_URL + "?client_reference_id=" + pass.getId());
    }

    @Test
    void checkoutReusesThePendingPass() throws Exception {
        String authId = "checkout-reuse-pending-" + UUID.randomUUID();

        String firstUrl = checkoutUrl(authId, TAX_YEAR);
        String secondUrl = checkoutUrl(authId, TAX_YEAR);

        assertThat(secondUrl).isEqualTo(firstUrl);
        assertThat(passesOf(authId)).hasSize(1);
    }

    @Test
    void checkoutReusesAFailedPass() throws Exception {
        String authId = "checkout-reuse-failed-" + UUID.randomUUID();
        TaxYearPass failedPass = savePass(authId, TAX_YEAR, TaxYearPassStatus.FAILED);

        String checkoutUrl = checkoutUrl(authId, TAX_YEAR);

        assertThat(checkoutUrl).isEqualTo(PAYMENT_LINK_URL + "?client_reference_id=" + failedPass.getId());
        assertThat(passesOf(authId))
                .singleElement()
                .extracting(TaxYearPass::getStatus)
                .isEqualTo(TaxYearPassStatus.FAILED);
    }

    @Test
    void checkoutStartsANewPassAfterARefund() throws Exception {
        String authId = "checkout-after-refund-" + UUID.randomUUID();
        TaxYearPass refundedPass = savePass(authId, TAX_YEAR, TaxYearPassStatus.REFUNDED);

        String checkoutUrl = checkoutUrl(authId, TAX_YEAR);

        List<TaxYearPass> passes = passesOf(authId);
        assertThat(passes).hasSize(2);
        TaxYearPass newPass = passes.stream()
                .filter(pass -> !pass.getId().equals(refundedPass.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(newPass.getStatus()).isEqualTo(TaxYearPassStatus.PENDING);
        assertThat(checkoutUrl).isEqualTo(PAYMENT_LINK_URL + "?client_reference_id=" + newPass.getId());
    }

    @Test
    void checkoutRejectsAYearThatIsAlreadyUnlocked() throws Exception {
        String authId = "checkout-paid-" + UUID.randomUUID();
        savePass(authId, TAX_YEAR, TaxYearPassStatus.PAID);

        checkout(authId, TAX_YEAR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tax year 2025 is already unlocked"));
        assertThat(passesOf(authId)).hasSize(1);
    }

    @Test
    void checkoutRejectsAYearWithAPaymentStillProcessing() throws Exception {
        String authId = "checkout-processing-" + UUID.randomUUID();
        savePass(authId, TAX_YEAR, TaxYearPassStatus.PROCESSING);

        checkout(authId, TAX_YEAR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A payment for tax year 2025 is still processing"));
        assertThat(passesOf(authId)).hasSize(1);
    }

    @Test
    void checkoutRejectsFutureAndTooOldTaxYears() throws Exception {
        String authId = "checkout-invalid-year-" + UUID.randomUUID();

        checkout(authId, Year.now().getValue() + 1).andExpect(status().isBadRequest());
        checkout(authId, 1999).andExpect(status().isBadRequest());
        assertThat(passesOf(authId)).isEmpty();
    }

    @Test
    void listsPassesNewestTaxYearFirst() throws Exception {
        String authId = "list-passes-" + UUID.randomUUID();
        TaxYearPass paidPass = savePass(authId, 2024, TaxYearPassStatus.PAID);
        paidPass.setAmountTotal(3900L);
        paidPass.setCurrency("CAD");
        paidPass.setPaidAt(Instant.parse("2025-02-03T18:22:11Z"));
        taxYearPassRepository.save(paidPass);
        // Compare with the stored value: H2 under the PostgreSQL dialect shifts instants on the round trip.
        Instant storedPaidAt = taxYearPassRepository.findById(paidPass.getId()).orElseThrow().getPaidAt();
        TaxYearPass pendingPass = savePass(authId, TAX_YEAR, TaxYearPassStatus.PENDING);

        mockMvc.perform(get(ApiPaths.BILLING_TAX_YEAR_PASSES).header("X-User-Id", authId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(pendingPass.getId().toString()))
                .andExpect(jsonPath("$[0].taxYear").value(2025))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].paidAt").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[1].id").value(paidPass.getId().toString()))
                .andExpect(jsonPath("$[1].taxYear").value(2024))
                .andExpect(jsonPath("$[1].status").value("PAID"))
                .andExpect(jsonPath("$[1].amountTotal").value(3900))
                .andExpect(jsonPath("$[1].currency").value("CAD"))
                .andExpect(jsonPath("$[1].paidAt").value(storedPaidAt.toString()))
                .andExpect(jsonPath("$[1].refundedAt").doesNotExist());
    }

    @Test
    void billingRoutesStillRequireASessionAndCsrf() throws Exception {
        mockMvc.perform(get(ApiPaths.BILLING_TAX_YEAR_PASSES))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(ApiPaths.BILLING_TAX_YEAR_PASSES + "/2025/checkout").with(csrf()))
                .andExpect(status().isUnauthorized());
        // Only the Stripe webhook is exempt from CSRF.
        mockMvc.perform(post(ApiPaths.BILLING_TAX_YEAR_PASSES + "/2025/checkout"))
                .andExpect(status().isForbidden());
    }

    private ResultActions checkout(String authId, int taxYear) throws Exception {
        return mockMvc.perform(
                post(ApiPaths.BILLING_TAX_YEAR_PASSES + "/" + taxYear + "/checkout").header("X-User-Id", authId)
        );
    }

    private String checkoutUrl(String authId, int taxYear) throws Exception {
        String body = checkout(authId, taxYear)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.checkoutUrl");
    }

    private List<TaxYearPass> passesOf(String authId) {
        User user = userService.getOrCreateUser(authId, null);
        return taxYearPassRepository.findByUserIdOrderByTaxYearDescCreatedAtDesc(user.getId());
    }

    private TaxYearPass savePass(String authId, int taxYear, TaxYearPassStatus status) {
        User user = userService.getOrCreateUser(authId, null);
        TaxYearPass pass = new TaxYearPass();
        pass.setUserId(user.getId());
        pass.setTaxYear(taxYear);
        pass.setStatus(status);
        return taxYearPassRepository.save(pass);
    }
}
