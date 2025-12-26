package com.transactionapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.PagedResponse;
import com.transactionapi.dto.PnlSummaryResponse;
import com.transactionapi.dto.TradeRequest;
import com.transactionapi.dto.TradeResponse;
import com.transactionapi.repository.TradeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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

    @BeforeEach
    void cleanDb() {
        tradeRepository.deleteAll();
    }

    @Test
    void calculatesPnlForShortOption() {
        TradeRequest request = new TradeRequest(
                "AAPL",
                AssetType.OPTION,
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
    void rejectsOptionsWithoutRequiredFields() {
        TradeRequest incomplete = new TradeRequest(
                "QQQ",
                AssetType.OPTION,
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

        assertThat(summary.daily()).extracting(bucket -> bucket.period())
                .containsExactly("2024-06-01", "2024-05-02", "2024-05-01");
        assertThat(summary.daily()).extracting(bucket -> bucket.pnl().toPlainString())
                .containsExactly("99.00", "-30.00", "20.00");

        assertThat(summary.monthly()).extracting(bucket -> bucket.period())
                .containsExactly("2024-06", "2024-05");
        assertThat(summary.monthly()).extracting(bucket -> bucket.pnl().toPlainString())
                .containsExactly("99.00", "-10.00");
    }

    @Test
    void paginatesTradesDescendingByCloseDate() {
        tradeService.createTrade(
                new TradeRequest(
                        "AAA",
                        AssetType.STOCK,
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
    void summarizesSpecificMonthOnly() {
        tradeService.createTrade(
                new TradeRequest(
                        "JAN",
                        AssetType.STOCK,
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
}
