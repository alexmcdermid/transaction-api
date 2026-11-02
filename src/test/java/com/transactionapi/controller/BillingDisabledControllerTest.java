package com.transactionapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stripe.net.Webhook;
import com.transactionapi.constants.ApiPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Default test configuration: no Stripe webhook secret or Payment Link, so billing is off. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingDisabledControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void checkoutIsUnavailable() throws Exception {
        mockMvc.perform(
                        post(ApiPaths.BILLING_TAX_YEAR_PASSES + "/2025/checkout")
                                .header("X-User-Id", "billing-disabled-user")
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Tax year pass checkout is not configured"));
    }

    @Test
    void webhookIsUnavailableEvenWithASignedPayload() throws Exception {
        String payload = "{\"id\":\"evt_test_disabled\",\"object\":\"event\",\"type\":\"checkout.session.completed\"}";

        mockMvc.perform(
                        post(ApiPaths.BILLING_STRIPE_WEBHOOK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", Webhook.Signature.generateSignatureHeader(payload, "whsec_any"))
                                .content(payload)
                )
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void passHistoryStillLoads() throws Exception {
        mockMvc.perform(get(ApiPaths.BILLING_TAX_YEAR_PASSES).header("X-User-Id", "billing-disabled-history-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
