package com.transactionapi.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.TradeRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsJsonMessageForResponseStatusException() throws Exception {
        TradeRequest invalid = new TradeRequest(
                "NVDA",
                AssetType.OPTION,
                Currency.USD,
                TradeDirection.LONG,
                1,
                new BigDecimal("1.00"),
                new BigDecimal("2.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.now(),
                LocalDate.now(),
                null
        );

        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "error-user")
                                .content(objectMapper.writeValueAsString(invalid))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Options require type, strike, and expiry"));
    }

    @Test
    void returnsJsonMessageForValidationErrors() throws Exception {
        TradeRequest invalid = new TradeRequest(
                "BTC",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.LONG,
                0,
                new BigDecimal("61000.00"),
                new BigDecimal("61200.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.now(),
                LocalDate.now(),
                null
        );

        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "validation-user")
                                .content(objectMapper.writeValueAsString(invalid))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Quantity must be greater than 0"))
                .andExpect(jsonPath("$.errors[0]").value("Quantity must be greater than 0"));
    }

    @Test
    void returnsJsonMessageForInvalidRequestBodyField() throws Exception {
        String invalid = """
                {
                  "symbol": "BTC",
                  "assetType": "CRYPTO",
                  "currency": "USD",
                  "direction": "LONG",
                  "quantity": 0.125,
                  "entryPrice": 61000.00,
                  "exitPrice": 61200.00,
                  "openedAt": "2026-06-24",
                  "closedAt": "2026-06-24"
                }
                """;

        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "invalid-body-user")
                                .content(invalid)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Asset Type has an invalid value"));
    }

    @Test
    void returnsJsonMessageForMalformedRequestBody() throws Exception {
        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "malformed-body-user")
                                .content("{")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request body is invalid or malformed"));
    }

    @Test
    void returnsJsonMessageForQueryParameterTypeMismatch() throws Exception {
        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .param("page", "not-a-number")
                                .header("X-User-Id", "type-mismatch-user")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Page must be a valid whole number"));
    }

    @Test
    void returnsJsonMessageForPathVariableTypeMismatch() throws Exception {
        mockMvc.perform(
                        get(ApiPaths.TRADES + "/not-a-uuid/history")
                                .header("X-User-Id", "path-mismatch-user")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Trade Id must be a valid UUID"));
    }
}
