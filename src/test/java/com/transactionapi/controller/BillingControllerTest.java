package com.transactionapi.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.service.StripeBillingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StripeBillingService stripeBillingService;

    @Test
    void checkoutSessionRequiresAuthentication() throws Exception {
        mockMvc.perform(post(ApiPaths.BILLING_CHECKOUT_SESSION).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkoutSessionReturnsStripeUrlForAuthenticatedUser() throws Exception {
        when(stripeBillingService.createCheckoutSession("billing-user", null))
                .thenReturn("https://checkout.stripe.com/c/test-session");

        mockMvc.perform(post(ApiPaths.BILLING_CHECKOUT_SESSION).header("X-User-Id", "billing-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/c/test-session"));

        verify(stripeBillingService).createCheckoutSession("billing-user", null);
    }

    @Test
    void webhookIsPublicAndCsrfExempt() throws Exception {
        doNothing().when(stripeBillingService).handleWebhook("{}", "test-signature");

        mockMvc.perform(
                        post(ApiPaths.BILLING_WEBHOOK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", "test-signature")
                                .content("{}")
                )
                .andExpect(status().isOk());

        verify(stripeBillingService).handleWebhook("{}", "test-signature");
    }
}
