package com.transactionapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transactionapi.constants.ApiPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsListsAndDeletesAccounts() throws Exception {
        String userId = "account-user";

        MvcResult createResult = mockMvc.perform(
                        post(ApiPaths.USER_ACCOUNTS)
                                .header("X-User-Id", userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name":"Wealthsimple",
                                          "defaultStockFees":0,
                                          "defaultOptionFees":1.25,
                                          "defaultMarginRateUsd":6.25,
                                          "defaultMarginRateCad":5.75
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Wealthsimple"))
                .andExpect(jsonPath("$.defaultStockFees").value(0))
                .andExpect(jsonPath("$.defaultOptionFees").value(1.25))
                .andExpect(jsonPath("$.defaultMarginRateUsd").value(6.25))
                .andExpect(jsonPath("$.defaultMarginRateCad").value(5.75))
                .andReturn();

        String accountId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(),
                "$.id"
        );

        mockMvc.perform(
                        get(ApiPaths.USER_ACCOUNTS)
                                .header("X-User-Id", userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(accountId))
                .andExpect(jsonPath("$[0].name").value("Wealthsimple"));

        mockMvc.perform(
                        delete(ApiPaths.USER_ACCOUNTS + "/" + accountId)
                                .header("X-User-Id", userId)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get(ApiPaths.USER_ACCOUNTS)
                                .header("X-User-Id", userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
