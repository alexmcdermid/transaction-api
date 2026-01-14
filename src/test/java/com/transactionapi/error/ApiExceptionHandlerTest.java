package com.transactionapi.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
