package com.transactionapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class TradeControllerTest {

    private static final String USER_ID = "controller-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsListsAndSummarizesTrades() throws Exception {
        TradeRequest mayTrade = new TradeRequest(
                "TSLA",
                AssetType.STOCK,
                Currency.USD,
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
        TradeRequest juneTrade = new TradeRequest(
                "AAPL",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.SHORT,
                1,
                new BigDecimal("20.00"),
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.of(2024, 6, 10),
                LocalDate.of(2024, 6, 12),
                "summer fade"
        );

        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", USER_ID)
                                .content(objectMapper.writeValueAsString(mayTrade))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("TSLA"))
                .andExpect(jsonPath("$.realizedPnl").value(32.5));

        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", USER_ID)
                                .content(objectMapper.writeValueAsString(juneTrade))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"));

        mockMvc.perform(
                        get(ApiPaths.TRADES)
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/summary")
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(2))
                .andExpect(jsonPath("$.totalPnl").value(42.5));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/summary")
                                .param("month", "2024-05")
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(1))
                .andExpect(jsonPath("$.totalPnl").value(32.5));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .param("page", "0")
                                .param("size", "1")
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .param("page", "0")
                                .param("size", "5")
                                .param("month", "2024-05")
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].symbol").value("TSLA"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void aggregateStatsReturnsStatsWithCurrencyConversion() throws Exception {
        String testUserId = "stats-test-user";
        TradeRequest usdTrade = new TradeRequest(
                "AAPL",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.LONG,
                10,
                new BigDecimal("100.00"),
                new BigDecimal("110.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.of(2024, 1, 10),
                LocalDate.of(2024, 1, 10),
                null
        );

        TradeRequest cadTrade = new TradeRequest(
                "RY",
                AssetType.STOCK,
                Currency.CAD,
                TradeDirection.LONG,
                10,
                new BigDecimal("100.00"),
                new BigDecimal("110.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.of(2024, 2, 15),
                LocalDate.of(2024, 2, 15),
                null
        );

        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", testUserId)
                                .content(objectMapper.writeValueAsString(usdTrade))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", testUserId)
                                .content(objectMapper.writeValueAsString(cadTrade))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/stats")
                                .header("X-User-Id", testUserId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(2))
                .andExpect(jsonPath("$.totalPnl").exists())
                .andExpect(jsonPath("$.bestDay").exists())
                .andExpect(jsonPath("$.bestDay.period").exists())
                .andExpect(jsonPath("$.bestDay.pnl").exists())
                .andExpect(jsonPath("$.bestDay.trades").exists())
                .andExpect(jsonPath("$.bestMonth").exists())
                .andExpect(jsonPath("$.bestMonth.period").exists())
                .andExpect(jsonPath("$.bestMonth.pnl").exists())
                .andExpect(jsonPath("$.bestMonth.trades").exists())
                .andExpect(jsonPath("$.cadToUsdRate").exists())
                .andExpect(jsonPath("$.fxDate").exists());
    }

    @Test
    void aggregateStatsReturnsEmptyForNoTrades() throws Exception {
        mockMvc.perform(
                        get(ApiPaths.TRADES + "/stats")
                                .header("X-User-Id", "empty-user")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(0))
                .andExpect(jsonPath("$.totalPnl").value(0))
                .andExpect(jsonPath("$.bestDay").isEmpty())
                .andExpect(jsonPath("$.bestMonth").isEmpty());
    }
}
