package com.transactionapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.AggregateStatsResponse;
import com.transactionapi.dto.PagedResponse;
import com.transactionapi.dto.PnlSummaryResponse;
import com.transactionapi.dto.TradeRequest;
import com.transactionapi.dto.TradeResponse;
import com.transactionapi.model.Account;
import com.transactionapi.repository.AccountRepository;
import com.transactionapi.repository.TradeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class TradeServiceTest {

    private static final String USER_ID = "test-user";

    @Autowired
    private TradeService tradeService;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void cleanDb() {
        tradeRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void calculatesPnlForShortOption() {
        TradeRequest request = new TradeRequest(
                "AAPL",
                AssetType.OPTION,
                Currency.USD,
                TradeDirection.SHORT,
                2,
                new BigDecimal("3.10"),
                new BigDecimal("1.10"),
                new BigDecimal("4.00"),
                OptionType.CALL,
                new BigDecimal("100"),
                LocalDate.of(2024, 12, 20),
                LocalDate.of(2024, 5, 1),
                LocalDate.of(2024, 5, 1),
                "covered call"
        );

        TradeResponse response = tradeService.createTrade(request, USER_ID);

        assertThat(response.realizedPnl()).isEqualByComparingTo("396.00");
    }

    @Test
    void createsTradeWithoutAccountWhenAccountIsNotProvided() {
        TradeRequest request = new TradeRequest(
                "NVDA",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.LONG,
                1,
                new BigDecimal("100.00"),
                new BigDecimal("110.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.of(2024, 5, 1),
                LocalDate.of(2024, 5, 1),
                null
        );

        TradeResponse response = tradeService.createTrade(request, USER_ID);

        assertThat(response.accountId()).isNull();
    }

    @Test
    void assignsAccountToTradeWhenOwnedByUser() {
        Account account = new Account();
        account.setUserId(USER_ID);
        account.setName("Wealthsimple");
        account.setDefaultStockFees(BigDecimal.ZERO);
        account.setDefaultOptionFees(new BigDecimal("1.25"));
        account.setDefaultMarginRateUsd(new BigDecimal("6.25"));
        account.setDefaultMarginRateCad(new BigDecimal("6.25"));
        Account savedAccount = accountRepository.save(account);

        TradeRequest request = new TradeRequest(
                "SHOP",
                AssetType.STOCK,
                Currency.CAD,
                TradeDirection.LONG,
                10,
                new BigDecimal("80.00"),
                new BigDecimal("81.00"),
                new BigDecimal("0.00"),
                new BigDecimal("6.25"),
                savedAccount.getId(),
                null,
                null,
                null,
                LocalDate.of(2024, 8, 20),
                LocalDate.of(2024, 8, 22),
                null
        );

        TradeResponse response = tradeService.createTrade(request, USER_ID);

        assertThat(response.accountId()).isEqualTo(savedAccount.getId());
    }

    @Test
    void rejectsTradeWhenAccountBelongsToDifferentUser() {
        Account account = new Account();
        account.setUserId("different-user");
        account.setName("Questrade");
        account.setDefaultStockFees(new BigDecimal("0.99"));
        account.setDefaultOptionFees(new BigDecimal("2.49"));
        account.setDefaultMarginRateUsd(new BigDecimal("8.00"));
        account.setDefaultMarginRateCad(new BigDecimal("8.00"));
        Account savedAccount = accountRepository.save(account);

        TradeRequest request = new TradeRequest(
                "QQQ",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.SHORT,
                3,
                new BigDecimal("300.00"),
                new BigDecimal("290.00"),
                new BigDecimal("0.99"),
                new BigDecimal("8.00"),
                savedAccount.getId(),
                null,
                null,
                null,
                LocalDate.of(2024, 9, 2),
                LocalDate.of(2024, 9, 6),
                null
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> tradeService.createTrade(request, USER_ID));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("Account not found");
    }

    @Test
    void rejectsTradeWhenAccountDoesNotExist() {
        TradeRequest request = new TradeRequest(
                "TSLA",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.LONG,
                2,
                new BigDecimal("200.00"),
                new BigDecimal("210.00"),
                BigDecimal.ZERO,
                new BigDecimal("5.00"),
                UUID.randomUUID(),
                null,
                null,
                null,
                LocalDate.of(2024, 7, 1),
                LocalDate.of(2024, 7, 2),
                null
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> tradeService.createTrade(request, USER_ID));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("Account not found");
    }

    @Test
    void rejectsOptionsWithoutRequiredFields() {
        TradeRequest incomplete = new TradeRequest(
                "QQQ",
                AssetType.OPTION,
                Currency.USD,
                TradeDirection.LONG,
                1,
                new BigDecimal("2.00"),
                new BigDecimal("2.50"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.now(),
                LocalDate.now(),
                null
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> tradeService.createTrade(incomplete, USER_ID));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void summarizesPnlByDayAndMonth() {
        // May 1: +20
        tradeService.createTrade(
                new TradeRequest(
                        "MSFT",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        10,
                        new BigDecimal("10.00"),
                        new BigDecimal("12.00"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 5, 1),
                        LocalDate.of(2024, 5, 1),
                        null
                ),
                USER_ID
        );

        // May 2: -30
        tradeService.createTrade(
                new TradeRequest(
                        "AMD",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.SHORT,
                        5,
                        new BigDecimal("50.00"),
                        new BigDecimal("55.00"),
                        new BigDecimal("5.00"),
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 5, 2),
                        LocalDate.of(2024, 5, 2),
                        "stop out"
                ),
                USER_ID
        );

        // June 1: +99
        tradeService.createTrade(
                new TradeRequest(
                        "SPY",
                        AssetType.OPTION,
                        Currency.USD,
                        TradeDirection.LONG,
                        1,
                        new BigDecimal("1.50"),
                        new BigDecimal("2.50"),
                        new BigDecimal("1.00"),
                        OptionType.CALL,
                        new BigDecimal("500"),
                        LocalDate.of(2024, 6, 30),
                        LocalDate.of(2024, 6, 1),
                        LocalDate.of(2024, 6, 1),
                        null
                ),
                USER_ID
        );

        PnlSummaryResponse summary = tradeService.summarize(USER_ID, null);

        assertThat(summary.totalPnl()).isEqualByComparingTo("89.00");
        assertThat(summary.tradeCount()).isEqualTo(3);
        assertThat(summary.pnlPercent()).isEqualByComparingTo("17.80");

        assertThat(summary.daily()).extracting(bucket -> bucket.period())
                .containsExactly("2024-06-01", "2024-05-02", "2024-05-01");
        assertThat(summary.daily()).extracting(bucket -> bucket.pnl().toPlainString())
                .containsExactly("99.00", "-30.00", "20.00");
        assertThat(summary.daily()).extracting(bucket -> bucket.pnlPercent().toPlainString())
                .containsExactly("66.00", "-12.00", "20.00");

        assertThat(summary.monthly()).extracting(bucket -> bucket.period())
                .containsExactly("2024-06", "2024-05");
        assertThat(summary.monthly()).extracting(bucket -> bucket.pnl().toPlainString())
                .containsExactly("99.00", "-10.00");
        assertThat(summary.monthly()).extracting(bucket -> bucket.pnlPercent().toPlainString())
                .containsExactly("66.00", "-2.86");
    }

    @Test
    void paginatesTradesDescendingByCloseDate() {
        tradeService.createTrade(
                new TradeRequest(
                        "AAA",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        1,
                        new BigDecimal("1"),
                        new BigDecimal("2"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 5, 1),
                        LocalDate.of(2024, 5, 10),
                        null
                ),
                USER_ID
        );
        tradeService.createTrade(
                new TradeRequest(
                        "BBB",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        1,
                        new BigDecimal("1"),
                        new BigDecimal("2"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 5, 1),
                        LocalDate.of(2024, 5, 12),
                        null
                ),
                USER_ID
        );
        tradeService.createTrade(
                new TradeRequest(
                        "CCC",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        1,
                        new BigDecimal("1"),
                        new BigDecimal("2"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 5, 1),
                        LocalDate.of(2024, 5, 11),
                        null
                ),
                USER_ID
        );

        PagedResponse<TradeResponse> pageOne = tradeService.listTrades(USER_ID, 0, 2);
        assertThat(pageOne.items()).hasSize(2);
        assertThat(pageOne.items()).extracting(TradeResponse::symbol)
                .containsExactly("BBB", "CCC");
        assertThat(pageOne.hasNext()).isTrue();

        PagedResponse<TradeResponse> pageTwo = tradeService.listTrades(USER_ID, 1, 2);
        assertThat(pageTwo.items()).hasSize(1);
        assertThat(pageTwo.items().getFirst().symbol()).isEqualTo("AAA");
        assertThat(pageTwo.hasNext()).isFalse();
        assertThat(pageTwo.hasPrevious()).isTrue();
    }

    @Test
    void paginatesWithinMonthWhenRequested() {
        tradeService.createTrade(
                new TradeRequest(
                        "JAN",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        1,
                        new BigDecimal("1"),
                        new BigDecimal("2"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 1, 15),
                        null
                ),
                USER_ID
        );
        tradeService.createTrade(
                new TradeRequest(
                        "MAY-1",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        1,
                        new BigDecimal("1"),
                        new BigDecimal("2"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 5, 1),
                        LocalDate.of(2024, 5, 10),
                        null
                ),
                USER_ID
        );
        tradeService.createTrade(
                new TradeRequest(
                        "MAY-2",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.SHORT,
                        1,
                        new BigDecimal("2"),
                        new BigDecimal("1"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 5, 2),
                        LocalDate.of(2024, 5, 11),
                        null
                ),
                USER_ID
        );

        PagedResponse<TradeResponse> mayOnly = tradeService.listTrades(USER_ID, 0, 5, YearMonth.of(2024, 5));

        assertThat(mayOnly.totalElements()).isEqualTo(2);
        assertThat(mayOnly.items()).extracting(TradeResponse::symbol)
                .containsExactly("MAY-2", "MAY-1");
    }

    @Test
    void paginatesWithinDayWhenRequested() {
        tradeService.createTrade(
                new TradeRequest(
                        "DAY-1",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        1,
                        new BigDecimal("1"),
                        new BigDecimal("2"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 5, 1),
                        LocalDate.of(2024, 5, 10),
                        null
                ),
                USER_ID
        );
        tradeService.createTrade(
                new TradeRequest(
                        "DAY-2",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.SHORT,
                        1,
                        new BigDecimal("2"),
                        new BigDecimal("1"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 5, 2),
                        LocalDate.of(2024, 5, 11),
                        null
                ),
                USER_ID
        );

        PagedResponse<TradeResponse> dayOnly = tradeService.listTrades(
                USER_ID,
                0,
                5,
                null,
                LocalDate.of(2024, 5, 10)
        );

        assertThat(dayOnly.totalElements()).isEqualTo(1);
        assertThat(dayOnly.items()).extracting(TradeResponse::symbol)
                .containsExactly("DAY-1");
    }

    @Test
    void summarizesSpecificMonthOnly() {
        tradeService.createTrade(
                new TradeRequest(
                        "JAN",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        1,
                        new BigDecimal("1"),
                        new BigDecimal("3"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 1, 15),
                        null
                ),
                USER_ID
        );
        tradeService.createTrade(
                new TradeRequest(
                        "FEB",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.SHORT,
                        1,
                        new BigDecimal("5"),
                        new BigDecimal("4"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 2, 1),
                        LocalDate.of(2024, 2, 10),
                        null
                ),
                USER_ID
        );

        PnlSummaryResponse january = tradeService.summarize(USER_ID, YearMonth.of(2024, 1));
        assertThat(january.totalPnl()).isEqualByComparingTo("2.00");
        assertThat(january.daily()).extracting(bucket -> bucket.period())
                .containsExactly("2024-01-15");

        PnlSummaryResponse february = tradeService.summarize(USER_ID, YearMonth.of(2024, 2));
        assertThat(february.totalPnl()).isEqualByComparingTo("1.00");
        assertThat(february.daily()).extracting(bucket -> bucket.period())
                .containsExactly("2024-02-10");
    }

    @Test
    void aggregateStatsReturnsEmptyForNoTrades() {
        AggregateStatsResponse stats = tradeService.getAggregateStats(USER_ID);

        assertThat(stats.totalPnl()).isEqualByComparingTo("0.00");
        assertThat(stats.tradeCount()).isEqualTo(0);
        assertThat(stats.bestDay()).isNull();
        assertThat(stats.bestMonth()).isNull();
        assertThat(stats.pnlPercent()).isNull();
    }

    @Test
    void aggregateStatsCalculatesTotalPnlAndCount() {
        tradeService.createTrade(
                new TradeRequest(
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
                ),
                USER_ID
        );

        tradeService.createTrade(
                new TradeRequest(
                        "MSFT",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        5,
                        new BigDecimal("50.00"),
                        new BigDecimal("55.00"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 2, 15),
                        LocalDate.of(2024, 2, 15),
                        null
                ),
                USER_ID
        );

        AggregateStatsResponse stats = tradeService.getAggregateStats(USER_ID);

        assertThat(stats.totalPnl()).isEqualByComparingTo("125.00");
        assertThat(stats.tradeCount()).isEqualTo(2);
        assertThat(stats.pnlPercent()).isEqualByComparingTo("10.00");
    }

    @Test
    void aggregateStatsConvertsCadToUsd() {
        tradeService.createTrade(
                new TradeRequest(
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
                ),
                USER_ID
        );

        tradeService.createTrade(
                new TradeRequest(
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
                ),
                USER_ID
        );

        AggregateStatsResponse stats = tradeService.getAggregateStats(USER_ID);

        assertThat(stats.totalPnl()).isGreaterThan(new BigDecimal("170.00"));
        assertThat(stats.totalPnl()).isLessThan(new BigDecimal("180.00"));
        assertThat(stats.tradeCount()).isEqualTo(2);
        assertThat(stats.cadToUsdRate()).isNotNull();
    }

    @Test
    void aggregateStatsFindsBestDayAcrossAllTime() {
        tradeService.createTrade(
                new TradeRequest(
                        "T1",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        5,
                        new BigDecimal("10.00"),
                        new BigDecimal("20.00"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 1, 10),
                        LocalDate.of(2024, 1, 10),
                        null
                ),
                USER_ID
        );

        tradeService.createTrade(
                new TradeRequest(
                        "T2",
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
                        LocalDate.of(2024, 2, 15),
                        LocalDate.of(2024, 2, 15),
                        null
                ),
                USER_ID
        );

        tradeService.createTrade(
                new TradeRequest(
                        "T3",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        5,
                        new BigDecimal("50.00"),
                        new BigDecimal("55.00"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 3, 20),
                        LocalDate.of(2024, 3, 20),
                        null
                ),
                USER_ID
        );

        AggregateStatsResponse stats = tradeService.getAggregateStats(USER_ID);

        assertThat(stats.bestDay()).isNotNull();
        assertThat(stats.bestDay().period()).isEqualTo("2024-02-15");
        assertThat(stats.bestDay().pnl()).isEqualByComparingTo("100.00");
        assertThat(stats.bestDay().trades()).isEqualTo(1);
    }

    @Test
    void aggregateStatsFindsBestMonthAcrossAllTime() {
        tradeService.createTrade(
                new TradeRequest(
                        "T1",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        5,
                        new BigDecimal("10.00"),
                        new BigDecimal("20.00"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2023, 1, 10),
                        LocalDate.of(2023, 1, 10),
                        null
                ),
                USER_ID
        );

        tradeService.createTrade(
                new TradeRequest(
                        "T2",
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
                        LocalDate.of(2023, 12, 10),
                        LocalDate.of(2023, 12, 10),
                        null
                ),
                USER_ID
        );

        tradeService.createTrade(
                new TradeRequest(
                        "T3",
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
                        LocalDate.of(2023, 12, 15),
                        LocalDate.of(2023, 12, 15),
                        null
                ),
                USER_ID
        );

        tradeService.createTrade(
                new TradeRequest(
                        "T4",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        15,
                        new BigDecimal("50.00"),
                        new BigDecimal("55.00"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 1, 20),
                        LocalDate.of(2024, 1, 20),
                        null
                ),
                USER_ID
        );

        AggregateStatsResponse stats = tradeService.getAggregateStats(USER_ID);

        assertThat(stats.bestMonth()).isNotNull();
        assertThat(stats.bestMonth().period()).isEqualTo("2023-12");
        assertThat(stats.bestMonth().pnl()).isEqualByComparingTo("200.00");
        assertThat(stats.bestMonth().trades()).isEqualTo(2);
    }

    @Test
    void aggregateStatsHandlesMultipleTradesOnSameDay() {
        tradeService.createTrade(
                new TradeRequest(
                        "T1",
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
                        LocalDate.of(2024, 5, 15),
                        LocalDate.of(2024, 5, 15),
                        null
                ),
                USER_ID
        );

        tradeService.createTrade(
                new TradeRequest(
                        "T2",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        5,
                        new BigDecimal("50.00"),
                        new BigDecimal("60.00"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 5, 15),
                        LocalDate.of(2024, 5, 15),
                        null
                ),
                USER_ID
        );

        tradeService.createTrade(
                new TradeRequest(
                        "T3",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        10,
                        new BigDecimal("20.00"),
                        new BigDecimal("25.00"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 5, 15),
                        LocalDate.of(2024, 5, 15),
                        null
                ),
                USER_ID
        );

        AggregateStatsResponse stats = tradeService.getAggregateStats(USER_ID);

        assertThat(stats.bestDay()).isNotNull();
        assertThat(stats.bestDay().period()).isEqualTo("2024-05-15");
        assertThat(stats.bestDay().pnl()).isEqualByComparingTo("200.00");
        assertThat(stats.bestDay().trades()).isEqualTo(3);
    }

    @Test
    void scopedAggregateStatsUsesOnlyRequestedYear() {
        tradeService.createTrade(
                new TradeRequest(
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
                ),
                USER_ID
        );
        tradeService.createTrade(
                new TradeRequest(
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
                ),
                USER_ID
        );

        AggregateStatsResponse scoped = tradeService.getScopedAggregateStats(USER_ID, 2024, null, null);

        assertThat(scoped.year()).isEqualTo(2024);
        assertThat(scoped.totalPnl()).isEqualByComparingTo("100.00");
        assertThat(scoped.tradeCount()).isEqualTo(1);
        assertThat(scoped.bestMonth()).isNotNull();
        assertThat(scoped.bestMonth().period()).isEqualTo("2024-01");
        assertThat(scoped.month()).isEqualTo("2024-01");
        assertThat(scoped.bestDay()).isNotNull();
        assertThat(scoped.bestDay().period()).isEqualTo("2024-01-10");
    }

    @Test
    void scopedAggregateStatsDefaultsToMostRecentTradeYear() {
        tradeService.createTrade(
                new TradeRequest(
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
                ),
                USER_ID
        );
        tradeService.createTrade(
                new TradeRequest(
                        "NEW-1",
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
                        LocalDate.of(2024, 1, 10),
                        LocalDate.of(2024, 1, 10),
                        null
                ),
                USER_ID
        );
        tradeService.createTrade(
                new TradeRequest(
                        "NEW-2",
                        AssetType.STOCK,
                        Currency.USD,
                        TradeDirection.LONG,
                        10,
                        new BigDecimal("20.00"),
                        new BigDecimal("27.00"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        LocalDate.of(2024, 3, 5),
                        LocalDate.of(2024, 3, 5),
                        null
                ),
                USER_ID
        );

        AggregateStatsResponse scoped = tradeService.getScopedAggregateStats(USER_ID, null, null, null);

        assertThat(scoped.year()).isEqualTo(2024);
        assertThat(scoped.totalPnl()).isEqualByComparingTo("120.00");
        assertThat(scoped.tradeCount()).isEqualTo(2);
        assertThat(scoped.bestMonth()).isNotNull();
        assertThat(scoped.bestMonth().period()).isEqualTo("2024-03");
        assertThat(scoped.month()).isEqualTo("2024-03");
        assertThat(scoped.bestDay()).isNotNull();
        assertThat(scoped.bestDay().period()).isEqualTo("2024-03-05");
    }

    @Test
    void scopedAggregateStatsUsesRequestedMonthForBestDay() {
        tradeService.createTrade(
                new TradeRequest(
                        "JAN",
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
                ),
                USER_ID
        );
        tradeService.createTrade(
                new TradeRequest(
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
                ),
                USER_ID
        );
        tradeService.createTrade(
                new TradeRequest(
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
                ),
                USER_ID
        );

        AggregateStatsResponse scoped = tradeService.getScopedAggregateStats(USER_ID, 2024, YearMonth.of(2024, 2), null);

        assertThat(scoped.year()).isEqualTo(2024);
        assertThat(scoped.month()).isEqualTo("2024-02");
        assertThat(scoped.bestDay()).isNotNull();
        assertThat(scoped.bestDay().period()).isEqualTo("2024-02-05");
        assertThat(scoped.bestDay().pnl()).isEqualByComparingTo("60.00");
    }

    @Test
    void scopedAggregateStatsUsesRequestedDayWhenProvided() {
        tradeService.createTrade(
                new TradeRequest(
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
                ),
                USER_ID
        );
        tradeService.createTrade(
                new TradeRequest(
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
                ),
                USER_ID
        );

        AggregateStatsResponse scoped = tradeService.getScopedAggregateStats(
                USER_ID,
                null,
                null,
                LocalDate.of(2024, 2, 20)
        );

        assertThat(scoped.year()).isEqualTo(2024);
        assertThat(scoped.month()).isEqualTo("2024-02");
        assertThat(scoped.day()).isEqualTo("2024-02-20");
        assertThat(scoped.bestDay()).isNotNull();
        assertThat(scoped.bestDay().period()).isEqualTo("2024-02-20");
        assertThat(scoped.bestDay().pnl()).isEqualByComparingTo("35.00");
    }
}
