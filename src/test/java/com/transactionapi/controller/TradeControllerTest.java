package com.transactionapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.AssetType;
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
class TradeControllerTest {

    private static final String USER_ID = "controller-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsListsAndSummarizesTrades() throws Exception {
        TradeRequest request = new TradeRequest(
                "TSLA",
                AssetType.STOCK,
                TradeDirection.LONG,
                3,
                new BigDecimal("100.00"),
                new BigDecimal("112.50"),
                new BigDecimal("5.00"),
                null,
                null,
                null,
                LocalDate.of(2024, 5, 10),
                LocalDate.of(2024, 5, 10),
                "test trade"
        );

        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", USER_ID)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("TSLA"))
                .andExpect(jsonPath("$.realizedPnl").value(32.5));

        mockMvc.perform(
                        get(ApiPaths.TRADES)
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("TSLA"));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/summary")
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(1))
                .andExpect(jsonPath("$.totalPnl").value(32.5));
    }
}
