package com.transactionapi.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.TradeRequest;
import com.transactionapi.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TradeControllerTest {

    private static final String USER_ID = "controller-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

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

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .param("page", "0")
                                .param("size", "5")
                                .param("date", "2024-06-12")
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .param("page", "0")
                                .param("size", "5")
                                .param("sortBy", "symbol")
                                .param("sortDirection", "desc")
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].symbol").value("TSLA"));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .param("symbol", "ap")
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].symbol").value("AAPL"));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .param("accountId", UUID.randomUUID().toString())
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void createsFractionalCryptoLikeTrade() throws Exception {
        TradeRequest btcTrade = new TradeRequest(
                "BTC",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.LONG,
                new BigDecimal("0.1257"),
                new BigDecimal("60000.00"),
                new BigDecimal("62000.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.of(2026, 6, 24),
                LocalDate.of(2026, 6, 24),
                "fractional btc"
        );

        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "fractional-user")
                                .content(objectMapper.writeValueAsString(btcTrade))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("BTC"))
                .andExpect(jsonPath("$.quantity").value(0.1257))
                .andExpect(jsonPath("$.realizedPnl").value(251.4));
    }

    @Test
    void rejectsInvalidSortByValue() throws Exception {
        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .param("sortBy", "not-a-field")
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isBadRequest());
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
                .andExpect(jsonPath("$.tradedDays").value(2))
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
                .andExpect(jsonPath("$.tradedDays").value(0))
                .andExpect(jsonPath("$.totalPnl").value(0))
                .andExpect(jsonPath("$.bestDay").isEmpty())
                .andExpect(jsonPath("$.bestMonth").isEmpty());
    }

    @Test
    void scopedAggregateStatsReturnsYearScopedData() throws Exception {
        String scopedUserId = "scoped-stats-user";
        TradeRequest oldTrade = new TradeRequest(
                "OLD",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.LONG,
                10,
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.of(2023, 12, 15),
                LocalDate.of(2023, 12, 15),
                null
        );
        TradeRequest newTrade = new TradeRequest(
                "NEW",
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

        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", scopedUserId)
                                .content(objectMapper.writeValueAsString(oldTrade))
                )
                .andExpect(status().isCreated());
        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", scopedUserId)
                                .content(objectMapper.writeValueAsString(newTrade))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/stats/scoped")
                                .header("X-User-Id", scopedUserId)
                                .param("year", "2024")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.tradeCount").value(1))
                .andExpect(jsonPath("$.totalPnl").value(100))
                .andExpect(jsonPath("$.bestMonth.period").value("2024-01"))
                .andExpect(jsonPath("$.month").value("2024-01"))
                .andExpect(jsonPath("$.bestDay.period").value("2024-01-10"));
    }

    @Test
    void scopedAggregateStatsDefaultsToMostRecentTradeYear() throws Exception {
        String scopedUserId = "scoped-default-year-user";
        TradeRequest oldTrade = new TradeRequest(
                "OLD",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.LONG,
                10,
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.of(2022, 6, 10),
                LocalDate.of(2022, 6, 10),
                null
        );
        TradeRequest newTrade = new TradeRequest(
                "NEW",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.LONG,
                10,
                new BigDecimal("100.00"),
                new BigDecimal("105.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.of(2024, 3, 5),
                LocalDate.of(2024, 3, 5),
                null
        );

        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", scopedUserId)
                                .content(objectMapper.writeValueAsString(oldTrade))
                )
                .andExpect(status().isCreated());
        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", scopedUserId)
                                .content(objectMapper.writeValueAsString(newTrade))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/stats/scoped")
                                .header("X-User-Id", scopedUserId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.tradeCount").value(1))
                .andExpect(jsonPath("$.totalPnl").value(50))
                .andExpect(jsonPath("$.bestMonth.period").value("2024-03"))
                .andExpect(jsonPath("$.month").value("2024-03"))
                .andExpect(jsonPath("$.bestDay.period").value("2024-03-05"));
    }

    @Test
    void scopedAggregateStatsAcceptsDayParameter() throws Exception {
        String scopedUserId = "scoped-day-user";
        TradeRequest feb5 = new TradeRequest(
                "FEB-1",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.LONG,
                10,
                new BigDecimal("100.00"),
                new BigDecimal("106.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.of(2024, 2, 5),
                LocalDate.of(2024, 2, 5),
                null
        );
        TradeRequest feb20 = new TradeRequest(
                "FEB-2",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.LONG,
                5,
                new BigDecimal("20.00"),
                new BigDecimal("27.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.of(2024, 2, 20),
                LocalDate.of(2024, 2, 20),
                null
        );

        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", scopedUserId)
                                .content(objectMapper.writeValueAsString(feb5))
                )
                .andExpect(status().isCreated());
        mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", scopedUserId)
                                .content(objectMapper.writeValueAsString(feb20))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/stats/scoped")
                                .header("X-User-Id", scopedUserId)
                                .param("day", "2024-02-20")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.month").value("2024-02"))
                .andExpect(jsonPath("$.day").value("2024-02-20"))
                .andExpect(jsonPath("$.bestDay.period").value("2024-02-20"))
                .andExpect(jsonPath("$.bestDay.pnl").value(35));
    }

    @Test
    void taxablePnlStatsAcceptsYearParameter() throws Exception {
        mockMvc.perform(
                        get(ApiPaths.TRADES + "/stats/taxable-pnl")
                                .header("X-User-Id", "taxable-pnl-user")
                                .param("year", "2024")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.totalPnl").value(0));
    }

    @Test
    void userCannotAccessAnotherUsersTrades() throws Exception {
        String userA = "user-a";
        String userB = "user-b";

        TradeRequest trade = new TradeRequest(
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

        String tradeId = mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", userA)
                                .content(objectMapper.writeValueAsString(trade))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = objectMapper.readTree(tradeId).get("id").asText();

        mockMvc.perform(
                        get(ApiPaths.TRADES)
                                .header("X-User-Id", userB)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .header("X-User-Id", userB)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/summary")
                                .header("X-User-Id", userB)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(0))
                .andExpect(jsonPath("$.totalPnl").value(0));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/stats")
                                .header("X-User-Id", userB)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(0))
                .andExpect(jsonPath("$.totalPnl").value(0));

        mockMvc.perform(
                        put(ApiPaths.TRADES + "/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", userB)
                                .content(objectMapper.writeValueAsString(trade))
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        delete(ApiPaths.TRADES + "/" + id)
                                .header("X-User-Id", userB)
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        get(ApiPaths.TRADES)
                                .header("X-User-Id", userA)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void jwtUserCannotAccessAnotherJwtUsersTrades() throws Exception {
        String userA = "google-sub-trade-owner";
        String userB = "google-sub-trade-other";
        userService.acceptLegalAgreement(userA, "owner@example.com");
        userService.acceptLegalAgreement(userB, "other@example.com");

        TradeRequest trade = new TradeRequest(
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

        String id = objectMapper.readTree(
                        mockMvc.perform(
                                        post(ApiPaths.TRADES)
                                                .with(jwtUser(userA, "owner@example.com"))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(trade))
                                )
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString()
                )
                .get("id")
                .asText();

        mockMvc.perform(
                        get(ApiPaths.TRADES)
                                .with(jwtUser(userB, "other@example.com"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .with(jwtUser(userB, "other@example.com"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/summary")
                                .with(jwtUser(userB, "other@example.com"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(0))
                .andExpect(jsonPath("$.totalPnl").value(0));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/stats")
                                .with(jwtUser(userB, "other@example.com"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(0))
                .andExpect(jsonPath("$.totalPnl").value(0));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/stats/scoped")
                                .with(jwtUser(userB, "other@example.com"))
                                .param("year", "2024")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.tradeCount").value(0))
                .andExpect(jsonPath("$.totalPnl").value(0));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/" + id + "/history")
                                .with(jwtUser(userB, "other@example.com"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(
                        put(ApiPaths.TRADES + "/" + id)
                                .with(jwtUser(userB, "other@example.com"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(trade))
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        delete(ApiPaths.TRADES + "/" + id)
                                .with(jwtUser(userB, "other@example.com"))
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        get(ApiPaths.TRADES)
                                .with(jwtUser(userB, "other@example.com"))
                                .header("X-User-Id", userA)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(
                        get(ApiPaths.TRADES)
                                .with(jwtUser(userA, "owner@example.com"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void returnsHistoryForUsersOwnTradeOnly() throws Exception {
        String owner = "history-owner";
        String other = "history-other";
        TradeRequest create = new TradeRequest(
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
        TradeRequest edit = new TradeRequest(
                "MSFT",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.SHORT,
                5,
                new BigDecimal("50.00"),
                new BigDecimal("45.00"),
                new BigDecimal("1.00"),
                null,
                null,
                null,
                LocalDate.of(2024, 1, 11),
                LocalDate.of(2024, 1, 11),
                "edited"
        );

        String id = objectMapper.readTree(
                        mockMvc.perform(
                                        post(ApiPaths.TRADES)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .header("X-User-Id", owner)
                                                .content(objectMapper.writeValueAsString(create))
                                )
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString()
                )
                .get("id")
                .asText();

        mockMvc.perform(
                        put(ApiPaths.TRADES + "/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", owner)
                                .content(objectMapper.writeValueAsString(edit))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/" + id + "/history")
                                .header("X-User-Id", owner)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[1].action").value("EDIT"))
                .andExpect(jsonPath("$[1].symbol").value("MSFT"));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/" + id + "/history")
                                .header("X-User-Id", other)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void movesSeveralTradesToOneClosedAtInOneRequest() throws Exception {
        String owner = "bulk-move-owner";
        LocalDate openedAt = LocalDate.of(2024, 2, 1);
        LocalDate originalClosedAt = LocalDate.of(2024, 2, 5);
        LocalDate targetClosedAt = LocalDate.of(2024, 2, 11);
        // 36.5% margin on $1,000 costs $1 a day, so the move from 4 to 10 days held must cut P&L by $6.
        String marginStockId = createTrade(owner, new TradeRequest(
                "MRGN", AssetType.STOCK, Currency.USD, TradeDirection.LONG, 10,
                new BigDecimal("100.00"), new BigDecimal("110.00"), BigDecimal.ZERO, new BigDecimal("36.5"),
                null, null, null, null, openedAt, originalClosedAt, null
        ));
        String optionExpiringOnCloseId = createTrade(owner, new TradeRequest(
                "EXPC", AssetType.OPTION, Currency.USD, TradeDirection.LONG, 1,
                new BigDecimal("1.00"), new BigDecimal("2.00"), BigDecimal.ZERO, null,
                null, OptionType.CALL, new BigDecimal("50"), originalClosedAt, openedAt, originalClosedAt, null
        ));
        String optionWithLaterExpiryId = createTrade(owner, new TradeRequest(
                "LATE", AssetType.OPTION, Currency.USD, TradeDirection.LONG, 1,
                new BigDecimal("1.00"), new BigDecimal("2.00"), BigDecimal.ZERO, null,
                null, OptionType.PUT, new BigDecimal("50"), LocalDate.of(2024, 3, 15), openedAt, originalClosedAt, null
        ));

        mockMvc.perform(
                        put(ApiPaths.TRADES + "/closed-at")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", owner)
                                .content(moveTradesBody(targetClosedAt, marginStockId, optionExpiringOnCloseId, optionWithLaterExpiryId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .param("date", targetClosedAt.toString())
                                .header("X-User-Id", owner)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[?(@.symbol == 'MRGN')].realizedPnl").value(90.0))
                .andExpect(jsonPath("$.items[?(@.symbol == 'EXPC')].expiryDate").value("2024-02-11"))
                .andExpect(jsonPath("$.items[?(@.symbol == 'LATE')].expiryDate").value("2024-03-15"));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/" + marginStockId + "/history")
                                .header("X-User-Id", owner)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].action").value("EDIT"))
                .andExpect(jsonPath("$[1].closedAt").value("2024-02-11"));
    }

    @Test
    void moveTradesToClosedAtChangesNothingWhenAnyTradeIsRejected() throws Exception {
        String owner = "bulk-move-all-or-nothing";
        String otherUser = "bulk-move-intruder";
        LocalDate originalClosedAt = LocalDate.of(2024, 4, 10);
        String openedEarlyId = createTrade(owner, stockTrade("EARLY", LocalDate.of(2024, 4, 1), originalClosedAt));
        String openedLateId = createTrade(owner, stockTrade("LATER", LocalDate.of(2024, 4, 8), originalClosedAt));
        String otherUsersTradeId = createTrade(otherUser, stockTrade("OTHER", LocalDate.of(2024, 4, 1), originalClosedAt));

        mockMvc.perform(
                        put(ApiPaths.TRADES + "/closed-at")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", owner)
                                .content(moveTradesBody(LocalDate.of(2024, 4, 5), openedEarlyId, openedLateId))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Close date cannot be before open date: LATER"));

        mockMvc.perform(
                        put(ApiPaths.TRADES + "/closed-at")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", owner)
                                .content(moveTradesBody(LocalDate.of(2024, 4, 12), openedEarlyId, otherUsersTradeId))
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        put(ApiPaths.TRADES + "/closed-at")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", owner)
                                .content(moveTradesBody(LocalDate.of(2024, 4, 12)))
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .param("date", originalClosedAt.toString())
                                .header("X-User-Id", owner)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(
                        get(ApiPaths.TRADES + "/paged")
                                .param("date", originalClosedAt.toString())
                                .header("X-User-Id", otherUser)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private String createTrade(String userId, TradeRequest request) throws Exception {
        String body = mockMvc.perform(
                        post(ApiPaths.TRADES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", userId)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String moveTradesBody(LocalDate closedAt, String... tradeIds) throws Exception {
        return objectMapper.writeValueAsString(Map.of("tradeIds", List.of(tradeIds), "closedAt", closedAt.toString()));
    }

    private static TradeRequest stockTrade(String symbol, LocalDate openedAt, LocalDate closedAt) {
        return new TradeRequest(
                symbol, AssetType.STOCK, Currency.USD, TradeDirection.LONG, 1,
                new BigDecimal("10.00"), new BigDecimal("11.00"), BigDecimal.ZERO, null,
                null, null, null, null, openedAt, closedAt, null
        );
    }

    private static RequestPostProcessor jwtUser(String subject, String email) {
        return jwt().jwt(jwt -> jwt
                .subject(subject)
                .claim("email", email)
        );
    }
}
