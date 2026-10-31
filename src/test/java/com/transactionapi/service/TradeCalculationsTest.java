package com.transactionapi.service;

import static com.transactionapi.service.ReportFixtures.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.TradeRequest;
import com.transactionapi.dto.TradeResponse;
import com.transactionapi.model.Trade;
import com.transactionapi.repository.AccountRepository;
import com.transactionapi.repository.TradeHistoryRepository;
import com.transactionapi.repository.TradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class TradeCalculationsTest {

    @Test
    void multiplierIsOneHundredForOptionsAndOneForStock() {
        assertThat(TradeCalculations.multiplier(AssetType.OPTION)).isEqualByComparingTo("100");
        assertThat(TradeCalculations.multiplier(AssetType.STOCK)).isEqualByComparingTo("1");
    }

    @Test
    void grossPnlForLongStockIsExitMinusEntryTimesQuantity() {
        Trade longStock = trade("MSFT").quantity("10").prices("100.00", "110.00").build();

        assertThat(TradeCalculations.grossPnl(longStock)).isEqualByComparingTo("100.00");
    }

    @Test
    void grossPnlForShortStockNegatesThePriceMove() {
        Trade winningShort = trade("QQQ").direction(TradeDirection.SHORT).quantity("3").prices("300.00", "290.00").build();
        Trade losingShort = trade("QQQ").direction(TradeDirection.SHORT).quantity("2").prices("50.00", "55.00").build();

        assertThat(TradeCalculations.grossPnl(winningShort)).isEqualByComparingTo("30.00");
        assertThat(TradeCalculations.grossPnl(losingShort)).isEqualByComparingTo("-10.00");
    }

    @Test
    void grossPnlForOptionsAppliesTheContractMultiplier() {
        Trade longCall = trade("AAPL").option(OptionType.CALL, "150", LocalDate.of(2025, 3, 21))
                .quantity("1").prices("2.00", "2.50").build();
        Trade shortCall = trade("AAPL").option(OptionType.CALL, "100", LocalDate.of(2024, 12, 20))
                .direction(TradeDirection.SHORT).quantity("2").prices("3.10", "1.10").build();

        assertThat(TradeCalculations.grossPnl(longCall)).isEqualByComparingTo("50.00");
        assertThat(TradeCalculations.grossPnl(shortCall)).isEqualByComparingTo("400.00");
    }

    @Test
    void grossPnlIsNotRounded() {
        Trade fractional = trade("XYZ").quantity("3").prices("10.0001", "10.0004").build();

        assertThat(TradeCalculations.grossPnl(fractional)).isEqualByComparingTo("0.0009");
    }

    @Test
    void marginInterestAccruesOnEntryNotionalOverDaysHeldOnA365DayYear() {
        Trade tenDays = trade("MSFT").quantity("10").prices("100.00", "110.00").marginRate("36.50")
                .heldBetween(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 11)).build();

        assertThat(TradeCalculations.marginInterest(tenDays)).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void marginInterestUsesOptionContractNotional() {
        Trade shortPut = trade("AAPL").option(OptionType.PUT, "100", LocalDate.of(2025, 6, 20))
                .direction(TradeDirection.SHORT).quantity("2").prices("3.10", "1.10").marginRate("5.00")
                .heldBetween(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31)).build();

        // 620 notional * 5% * 30/365
        assertThat(TradeCalculations.marginInterest(shortPut)).isEqualTo(new BigDecimal("2.55"));
    }

    @Test
    void marginInterestOnShortStockUsesAbsoluteEntryNotional() {
        Trade shortStock = trade("QQQ").direction(TradeDirection.SHORT).quantity("3").prices("300.00", "290.00")
                .marginRate("8.00").heldBetween(LocalDate.of(2024, 9, 2), LocalDate.of(2024, 9, 6)).build();

        // 900 notional * 8% * 4/365
        assertThat(TradeCalculations.marginInterest(shortStock)).isEqualTo(new BigDecimal("0.79"));
    }

    @Test
    void marginInterestRoundsHalfUpToCents() {
        Trade halfCent = trade("XYZ").quantity("1").prices("1.00", "1.00").marginRate("0.50")
                .heldBetween(LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1)).build();

        assertThat(TradeCalculations.marginInterest(halfCent)).isEqualTo(new BigDecimal("0.01"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "0.00", "-5.00"})
    void marginInterestIsZeroWithoutAPositiveRate(String marginRate) {
        Trade trade = trade("MSFT").quantity("10").prices("100.00", "110.00")
                .heldBetween(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1)).build();
        trade.setMarginRate(marginRate != null ? new BigDecimal(marginRate) : null);

        assertThat(TradeCalculations.marginInterest(trade)).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void marginInterestIsZeroForSameDayAndInvertedDates() {
        Trade sameDay = trade("MSFT").quantity("10").prices("100.00", "110.00").marginRate("36.50")
                .heldBetween(LocalDate.of(2024, 1, 11), LocalDate.of(2024, 1, 11)).build();
        Trade inverted = trade("MSFT").quantity("10").prices("100.00", "110.00").marginRate("36.50")
                .heldBetween(LocalDate.of(2024, 1, 11), LocalDate.of(2024, 1, 1)).build();

        assertThat(TradeCalculations.marginInterest(sameDay)).isEqualTo(new BigDecimal("0.00"));
        assertThat(TradeCalculations.marginInterest(inverted)).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void notionalIsAbsoluteEntryValueTimesMultiplier() {
        Trade option = trade("AAPL").option(OptionType.CALL, "150", LocalDate.of(2025, 3, 21))
                .quantity("2").prices("3.10", "1.10").build();
        Trade missingEntry = trade("AAPL").quantity("2").build();
        missingEntry.setEntryPrice(null);

        assertThat(TradeCalculations.notional(option)).isEqualByComparingTo("620.00");
        assertThat(TradeCalculations.notional(missingEntry)).isNull();
    }

    static Stream<Arguments> recordedPnlScenarios() {
        return Stream.of(
                Arguments.of("long stock held 10 days on margin", new TradeRequest(
                        "MSFT", AssetType.STOCK, Currency.USD, TradeDirection.LONG, new BigDecimal("10"),
                        new BigDecimal("100.00"), new BigDecimal("110.00"), BigDecimal.ZERO, new BigDecimal("36.50"),
                        null, null, null, null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 11), null
                ), "90.00"),
                Arguments.of("short stock on margin with fees", new TradeRequest(
                        "QQQ", AssetType.STOCK, Currency.USD, TradeDirection.SHORT, new BigDecimal("3"),
                        new BigDecimal("300.00"), new BigDecimal("290.00"), new BigDecimal("0.99"), new BigDecimal("8.00"),
                        null, null, null, null, LocalDate.of(2024, 9, 2), LocalDate.of(2024, 9, 6), null
                ), "28.22"),
                Arguments.of("same-day long option accrues no margin", new TradeRequest(
                        "AAPL", AssetType.OPTION, Currency.USD, TradeDirection.LONG, new BigDecimal("1"),
                        new BigDecimal("2.00"), new BigDecimal("2.50"), new BigDecimal("0.65"), new BigDecimal("5.00"),
                        null, OptionType.CALL, new BigDecimal("150"), LocalDate.of(2025, 3, 21),
                        LocalDate.of(2025, 3, 3), LocalDate.of(2025, 3, 3), null
                ), "49.35"),
                Arguments.of("short option held 30 days on margin", new TradeRequest(
                        "AAPL", AssetType.OPTION, Currency.USD, TradeDirection.SHORT, new BigDecimal("2"),
                        new BigDecimal("3.10"), new BigDecimal("1.10"), new BigDecimal("4.00"), new BigDecimal("5.00"),
                        null, OptionType.PUT, new BigDecimal("100"), LocalDate.of(2025, 6, 20),
                        LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31), null
                ), "393.45"),
                Arguments.of("zero margin rate", new TradeRequest(
                        "TSLA", AssetType.STOCK, Currency.CAD, TradeDirection.LONG, new BigDecimal("0.5"),
                        new BigDecimal("200.00"), new BigDecimal("190.00"), new BigDecimal("1.00"), BigDecimal.ZERO,
                        null, null, null, null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1), null
                ), "-6.00"),
                Arguments.of("negative margin rate", new TradeRequest(
                        "TSLA", AssetType.STOCK, Currency.USD, TradeDirection.LONG, new BigDecimal("1"),
                        new BigDecimal("200.00"), new BigDecimal("210.00"), BigDecimal.ZERO, new BigDecimal("-5.00"),
                        null, null, null, null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1), null
                ), "10.00")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordedPnlScenarios")
    void tradeServiceRecordsGrossPnlMinusFeesMinusMarginInterest(
            String scenario,
            TradeRequest request,
            String expectedRealizedPnl
    ) {
        AtomicReference<Trade> savedTrade = new AtomicReference<>();
        TradeRepository tradeRepository = mock(TradeRepository.class);
        when(tradeRepository.saveAndFlush(any(Trade.class))).thenAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            ReflectionTestUtils.setField(trade, "id", UUID.randomUUID());
            savedTrade.set(trade);
            return trade;
        });
        TradeService tradeService = new TradeService(
                tradeRepository,
                mock(TradeHistoryRepository.class),
                mock(AccountRepository.class),
                mock(ExchangeRateService.class)
        );

        TradeResponse response = tradeService.createTrade(request, "calculations-user");

        Trade trade = savedTrade.get();
        BigDecimal fromCalculations = TradeCalculations.grossPnl(trade)
                .subtract(trade.getFees())
                .subtract(TradeCalculations.marginInterest(trade))
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(response.realizedPnl()).isEqualTo(new BigDecimal(expectedRealizedPnl));
        assertThat(response.realizedPnl()).isEqualTo(fromCalculations);
    }
}
