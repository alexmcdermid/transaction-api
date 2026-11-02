package com.transactionapi.service;

import static com.transactionapi.service.ReportFixtures.account;
import static com.transactionapi.service.ReportFixtures.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.transactionapi.constants.Currency;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.AnnualSummaryResponse;
import com.transactionapi.dto.SummaryRow;
import com.transactionapi.model.Account;
import com.transactionapi.model.Trade;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaxYearReportServiceTest {

    private static final int TAX_YEAR = 2025;

    private final Account ibkr = account("IBKR", false);
    private final Account tfsa = account("Wealthsimple TFSA", true);
    private final Account apex = account("apex", false);
    private final ReportAccounts reportAccounts = ReportAccounts.of(List.of(ibkr, tfsa, apex));

    @Test
    void totalsArePerCurrencyAndNeverMixUsdWithCad() {
        List<Trade> trades = List.of(
                trade("AAPL").realizedPnl("100.00").fees("1.00").build(),
                trade("MSFT").realizedPnl("-40.00").fees("1.00").build(),
                trade("SHOP").currency(Currency.CAD).realizedPnl("50.00").fees("2.00").build()
        );

        AnnualSummaryResponse summary = summarize(trades);

        assertThat(summary.totals())
                .extracting(SummaryRow::key, SummaryRow::label, SummaryRow::currency, SummaryRow::taxFree,
                        SummaryRow::tradeCount, SummaryRow::winningTrades, SummaryRow::losingTrades)
                .containsExactly(
                        tuple("ALL", "All trades", Currency.CAD, null, 1, 1, 0),
                        tuple("ALL", "All trades", Currency.USD, null, 2, 1, 1)
                );
        assertThat(summary.totals())
                .extracting(row -> row.gains().toPlainString(), row -> row.losses().toPlainString(),
                        row -> row.fees().toPlainString(), row -> row.netRealizedPnl().toPlainString())
                .containsExactly(
                        tuple("50.00", "0.00", "2.00", "50.00"),
                        tuple("100.00", "-40.00", "2.00", "60.00")
                );
    }

    @Test
    void breakevenTradesCountButAreNeitherWinningNorLosing() {
        AnnualSummaryResponse summary = summarize(List.of(
                trade("AAPL").realizedPnl("0.00").build(),
                trade("AAPL").realizedPnl("12.50").build()
        ));

        SummaryRow total = summary.totals().getFirst();
        assertThat(total.tradeCount()).isEqualTo(2);
        assertThat(total.winningTrades()).isEqualTo(1);
        assertThat(total.losingTrades()).isZero();
        assertThat(total.gains()).isEqualByComparingTo("12.50");
        assertThat(total.losses()).isEqualByComparingTo("0.00");
        assertThat(total.netRealizedPnl()).isEqualByComparingTo("12.50");
    }

    @Test
    void sumsGainsLossesFeesMarginInterestAndNetAtTwoDecimals() {
        Trade marginWinner = trade("MSFT").quantity("10").prices("100.00", "110.00").fees("1.00").marginRate("36.50")
                .heldBetween(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 11)).build();
        Trade loser = trade("TSLA").quantity("5").prices("50.00", "45.00").fees("0.50").build();

        SummaryRow total = summarize(List.of(marginWinner, loser)).totals().getFirst();

        assertThat(marginWinner.getRealizedPnl()).isEqualByComparingTo("89.00");
        assertThat(total.gains()).isEqualTo("89.00");
        assertThat(total.losses()).isEqualTo("-25.50");
        assertThat(total.fees()).isEqualTo("1.50");
        assertThat(total.marginInterest()).isEqualTo("10.00");
        assertThat(total.netRealizedPnl()).isEqualTo("63.50");
        assertThat(List.of(total.gains(), total.losses(), total.fees(), total.marginInterest(), total.netRealizedPnl()))
                .allSatisfy(amount -> assertThat(amount.scale()).isEqualTo(2));
    }

    @Test
    void monthsAreChronologicalThenByCurrency() {
        AnnualSummaryResponse summary = summarize(List.of(
                trade("AAPL").closedOn(LocalDate.of(2025, 12, 30)).realizedPnl("1.00").build(),
                trade("AAPL").closedOn(LocalDate.of(2025, 1, 15)).realizedPnl("2.00").build(),
                trade("SHOP").currency(Currency.CAD).closedOn(LocalDate.of(2025, 3, 3)).realizedPnl("3.00").build(),
                trade("SHOP").currency(Currency.CAD).closedOn(LocalDate.of(2025, 1, 2)).realizedPnl("4.00").build()
        ));

        assertThat(summary.months())
                .extracting(SummaryRow::key, SummaryRow::label, SummaryRow::currency, SummaryRow::taxFree)
                .containsExactly(
                        tuple("2025-01", "2025-01", Currency.CAD, null),
                        tuple("2025-01", "2025-01", Currency.USD, null),
                        tuple("2025-03", "2025-03", Currency.CAD, null),
                        tuple("2025-12", "2025-12", Currency.USD, null)
                );
    }

    @Test
    void accountsCarryTaxFreeIncludeUnassignedAndSortByLabelThenCurrency() {
        AnnualSummaryResponse summary = summarize(List.of(
                trade("AAPL").account(ibkr).realizedPnl("10.00").build(),
                trade("SHOP").account(tfsa).currency(Currency.CAD).realizedPnl("20.00").build(),
                trade("NVDA").realizedPnl("30.00").build(),
                trade("SHOP").account(ibkr).currency(Currency.CAD).realizedPnl("40.00").build(),
                trade("AMD").account(apex).realizedPnl("50.00").build()
        ));

        assertThat(summary.accounts())
                .extracting(SummaryRow::key, SummaryRow::label, SummaryRow::currency, SummaryRow::taxFree)
                .containsExactly(
                        tuple(apex.getId().toString(), "apex", Currency.USD, false),
                        tuple(ibkr.getId().toString(), "IBKR", Currency.CAD, false),
                        tuple(ibkr.getId().toString(), "IBKR", Currency.USD, false),
                        tuple("UNASSIGNED", "Unassigned", Currency.USD, false),
                        tuple(tfsa.getId().toString(), "Wealthsimple TFSA", Currency.CAD, true)
                );
    }

    @Test
    void symbolsSortByNetRealizedPnlDescendingThenSymbolThenCurrency() {
        AnnualSummaryResponse summary = summarize(List.of(
                trade("TSLA").realizedPnl("-50.00").build(),
                trade("MSFT").realizedPnl("100.00").build(),
                trade("AAPL").realizedPnl("100.00").build(),
                trade("AAPL").currency(Currency.CAD).realizedPnl("60.00").build(),
                trade("AAPL").currency(Currency.CAD).realizedPnl("40.00").build()
        ));

        assertThat(summary.symbols())
                .extracting(SummaryRow::key, SummaryRow::label, SummaryRow::currency, SummaryRow::tradeCount)
                .containsExactly(
                        tuple("AAPL", "AAPL", Currency.CAD, 2),
                        tuple("AAPL", "AAPL", Currency.USD, 1),
                        tuple("MSFT", "MSFT", Currency.USD, 1),
                        tuple("TSLA", "TSLA", Currency.USD, 1)
                );
        assertThat(summary.symbols()).extracting(SummaryRow::taxFree).containsOnlyNulls();
    }

    @Test
    void assetTypesUseStocksCallsPutsKeysLabelsAndOrder() {
        LocalDate expiry = LocalDate.of(2025, 12, 19);
        AnnualSummaryResponse summary = summarize(List.of(
                trade("SPY").option(OptionType.PUT, "500", expiry).realizedPnl("1.00").build(),
                trade("SHOP").currency(Currency.CAD).realizedPnl("2.00").build(),
                trade("SPY").option(OptionType.CALL, "600", expiry).realizedPnl("3.00").build(),
                trade("AAPL").realizedPnl("4.00").build()
        ));

        assertThat(summary.assetTypes())
                .extracting(SummaryRow::key, SummaryRow::label, SummaryRow::currency, SummaryRow::taxFree)
                .containsExactly(
                        tuple("STOCK", "Stocks", Currency.CAD, null),
                        tuple("STOCK", "Stocks", Currency.USD, null),
                        tuple("OPTION_CALL", "Calls", Currency.USD, null),
                        tuple("OPTION_PUT", "Puts", Currency.USD, null)
                );
    }

    @Test
    void taxTreatmentCountsUnassignedTradesAsTaxable() {
        AnnualSummaryResponse summary = summarize(List.of(
                trade("SHOP").account(tfsa).realizedPnl("20.00").build(),
                trade("NVDA").realizedPnl("30.00").build(),
                trade("AAPL").account(ibkr).realizedPnl("-10.00").build()
        ));

        assertThat(summary.taxTreatment())
                .extracting(SummaryRow::key, SummaryRow::label, SummaryRow::currency, SummaryRow::taxFree,
                        SummaryRow::tradeCount, row -> row.netRealizedPnl().toPlainString())
                .containsExactly(
                        tuple("TAXABLE", "Taxable accounts", Currency.USD, false, 2, "20.00"),
                        tuple("TAX_FREE", "Tax-free accounts", Currency.USD, true, 1, "20.00")
                );
    }

    @Test
    void reportsTradeCountAndFirstAndLastCloseDates() {
        AnnualSummaryResponse summary = TaxYearReportService.buildSummary(TAX_YEAR, List.of(
                trade("AAPL").closedOn(LocalDate.of(2025, 3, 5)).build(),
                trade("AAPL").closedOn(LocalDate.of(2025, 1, 3)).build(),
                trade("AAPL").closedOn(LocalDate.of(2025, 12, 30)).build()
        ), reportAccounts, 7);

        assertThat(summary.taxYear()).isEqualTo(TAX_YEAR);
        assertThat(summary.tradeCount()).isEqualTo(3);
        assertThat(summary.firstClosedOn()).isEqualTo(LocalDate.of(2025, 1, 3));
        assertThat(summary.lastClosedOn()).isEqualTo(LocalDate.of(2025, 12, 30));
        assertThat(summary.repurchaseFlagCount()).isEqualTo(7);
    }

    @Test
    void emptyYearHasNoRowsAndNoCloseDates() {
        AnnualSummaryResponse summary = summarize(List.of());

        assertThat(summary.tradeCount()).isZero();
        assertThat(summary.firstClosedOn()).isNull();
        assertThat(summary.lastClosedOn()).isNull();
        assertThat(summary.repurchaseFlagCount()).isZero();
        assertThat(List.of(summary.totals(), summary.months(), summary.accounts(), summary.symbols(),
                summary.assetTypes(), summary.taxTreatment()))
                .allSatisfy(rows -> assertThat(rows).isEmpty());
    }

    @Test
    void repurchaseWindowIsInclusiveAtThirtyDaysAndExcludesThirtyOne() {
        Trade loss = loss("AAPL").heldBetween(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 15)).build();
        Trade thirtyOneBefore = trade("AAPL").openedOn(LocalDate.of(2025, 5, 15)).closedOn(LocalDate.of(2025, 5, 20)).build();
        Trade thirtyBefore = trade("AAPL").openedOn(LocalDate.of(2025, 5, 16)).closedOn(LocalDate.of(2025, 5, 20)).build();
        Trade thirtyAfter = trade("AAPL").openedOn(LocalDate.of(2025, 7, 15)).closedOn(LocalDate.of(2025, 7, 20)).build();
        Trade thirtyOneAfter = trade("AAPL").openedOn(LocalDate.of(2025, 7, 16)).closedOn(LocalDate.of(2025, 7, 20)).build();

        List<RepurchaseMatch> matches = TaxYearReportService.reviewRepurchases(
                List.of(loss),
                List.of(loss, thirtyOneBefore, thirtyBefore, thirtyAfter, thirtyOneAfter),
                reportAccounts
        );

        assertThat(matches)
                .extracting(RepurchaseMatch::lossTrade, RepurchaseMatch::matchTrade, RepurchaseMatch::daysFromLoss)
                .containsExactly(
                        tuple(loss, thirtyBefore, -30L),
                        tuple(loss, thirtyAfter, 30L)
                );
    }

    @Test
    void repurchaseWindowCrossesTheYearBoundary() {
        Trade decemberLoss = loss("AAPL").heldBetween(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 20)).build();
        Trade januaryBuy = trade("AAPL").heldBetween(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 1)).build();

        List<RepurchaseMatch> matches = TaxYearReportService.reviewRepurchases(
                List.of(decemberLoss), List.of(decemberLoss, januaryBuy), reportAccounts);

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.matchTrade()).isSameAs(januaryBuy);
            assertThat(match.daysFromLoss()).isEqualTo(21);
            assertThat(match.possibleSamePosition()).isFalse();
        });
    }

    @Test
    void repurchaseMatchesIgnoreReportFiltersAndIncludeTaxFreeAccounts() {
        Trade taxableLoss = loss("SHOP").account(ibkr).build();
        Trade taxFreeBuyOutsideFilters = trade("SHOP").account(tfsa).currency(Currency.CAD)
                .direction(TradeDirection.SHORT).openedOn(LocalDate.of(2025, 6, 10)).closedOn(LocalDate.of(2025, 6, 11))
                .build();

        List<RepurchaseMatch> matches = TaxYearReportService.reviewRepurchases(
                List.of(taxableLoss), List.of(taxableLoss, taxFreeBuyOutsideFilters), reportAccounts);

        assertThat(matches).singleElement()
                .satisfies(match -> assertThat(match.matchTrade()).isSameAs(taxFreeBuyOutsideFilters));
    }

    @Test
    void repurchaseLossesComeOnlyFromTaxableAccountsInTheFilteredSet() {
        Trade taxableLoss = loss("AAPL").account(ibkr).build();
        Trade unassignedLoss = loss("AAPL").build();
        Trade taxFreeLoss = loss("AAPL").account(tfsa).build();
        Trade taxableWin = trade("AAPL").account(ibkr).realizedPnl("15.00").build();
        Trade lossOutsideFilters = loss("AAPL").account(apex).build();
        List<Trade> filteredTrades = List.of(taxableLoss, unassignedLoss, taxFreeLoss, taxableWin);

        List<RepurchaseMatch> matches = TaxYearReportService.reviewRepurchases(
                filteredTrades,
                List.of(taxableLoss, unassignedLoss, taxFreeLoss, taxableWin, lossOutsideFilters),
                reportAccounts
        );

        assertThat(TaxYearReportService.repurchaseLossTrades(filteredTrades, reportAccounts))
                .containsExactly(taxableLoss, unassignedLoss);
        assertThat(matches).extracting(RepurchaseMatch::lossTrade).containsOnly(taxableLoss, unassignedLoss);
        assertThat(matches).extracting(RepurchaseMatch::matchTrade).contains(taxFreeLoss, lossOutsideFilters);
    }

    @Test
    void possibleSamePositionRequiresSameOpenDateAccountAndDirection() {
        LocalDate opened = LocalDate.of(2025, 6, 1);
        Trade loss = loss("AAPL").account(ibkr).heldBetween(opened, LocalDate.of(2025, 6, 15)).build();
        Trade samePosition = trade("AAPL").account(ibkr).heldBetween(opened, LocalDate.of(2025, 6, 20)).build();
        Trade otherAccount = trade("AAPL").account(apex).heldBetween(opened, LocalDate.of(2025, 6, 20)).build();
        Trade unassigned = trade("AAPL").heldBetween(opened, LocalDate.of(2025, 6, 20)).build();
        Trade otherDirection = trade("AAPL").account(ibkr).direction(TradeDirection.SHORT)
                .heldBetween(opened, LocalDate.of(2025, 6, 20)).build();
        Trade nextDay = trade("AAPL").account(ibkr).heldBetween(opened.plusDays(1), LocalDate.of(2025, 6, 20)).build();

        List<RepurchaseMatch> matches = TaxYearReportService.reviewRepurchases(
                List.of(loss),
                List.of(loss, samePosition, otherAccount, unassigned, otherDirection, nextDay),
                reportAccounts
        );

        assertThat(matches)
                .filteredOn(RepurchaseMatch::possibleSamePosition)
                .extracting(RepurchaseMatch::matchTrade)
                .containsExactly(samePosition);
        assertThat(matches).hasSize(5);
    }

    @Test
    void possibleSamePositionWhenBothTradesAreUnassigned() {
        LocalDate opened = LocalDate.of(2025, 6, 1);
        Trade unassignedLoss = loss("AAPL").heldBetween(opened, LocalDate.of(2025, 6, 15)).build();
        Trade unassignedRemainder = trade("AAPL").heldBetween(opened, LocalDate.of(2025, 6, 30)).build();
        Trade assignedSameDay = trade("AAPL").account(ibkr).heldBetween(opened, LocalDate.of(2025, 6, 30)).build();

        List<RepurchaseMatch> matches = TaxYearReportService.reviewRepurchases(
                List.of(unassignedLoss), List.of(unassignedLoss, unassignedRemainder, assignedSameDay), reportAccounts);

        assertThat(matches)
                .extracting(RepurchaseMatch::matchTrade, RepurchaseMatch::possibleSamePosition)
                .containsExactlyInAnyOrder(tuple(unassignedRemainder, true), tuple(assignedSameDay, false));
    }

    @Test
    void repurchaseReviewSortsByLossCloseLossIdMatchOpenThenMatchId() {
        // Tie-breaks compare id strings (PostgreSQL uuid order); UUID.compareTo would put 8000... and ffff... first.
        Trade lateLoss = loss("AAPL").id("00000000-0000-0000-0000-000000000001")
                .heldBetween(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 10)).build();
        Trade earlyLossHighId = loss("AAPL").id("ffffffff-0000-0000-0000-000000000002")
                .heldBetween(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 3, 1)).build();
        Trade earlyLossLowId = loss("AAPL").id("10000000-0000-0000-0000-000000000003")
                .heldBetween(LocalDate.of(2025, 2, 2), LocalDate.of(2025, 3, 1)).build();
        Trade laterBuy = trade("AAPL").id("20000000-0000-0000-0000-000000000004")
                .heldBetween(LocalDate.of(2025, 3, 5), LocalDate.of(2025, 3, 20)).build();
        Trade earlierBuyHighId = trade("AAPL").id("80000000-0000-0000-0000-000000000005")
                .heldBetween(LocalDate.of(2025, 2, 20), LocalDate.of(2025, 3, 20)).build();
        Trade earlierBuyLowId = trade("AAPL").id("30000000-0000-0000-0000-000000000006")
                .heldBetween(LocalDate.of(2025, 2, 20), LocalDate.of(2025, 3, 20)).build();

        List<RepurchaseMatch> matches = TaxYearReportService.reviewRepurchases(
                List.of(lateLoss, earlyLossHighId, earlyLossLowId),
                List.of(laterBuy, earlierBuyHighId, earlierBuyLowId),
                reportAccounts
        );

        assertThat(matches)
                .extracting(RepurchaseMatch::lossTrade, RepurchaseMatch::matchTrade)
                .containsExactly(
                        tuple(earlyLossLowId, earlierBuyLowId),
                        tuple(earlyLossLowId, earlierBuyHighId),
                        tuple(earlyLossLowId, laterBuy),
                        tuple(earlyLossHighId, earlierBuyLowId),
                        tuple(earlyLossHighId, earlierBuyHighId),
                        tuple(earlyLossHighId, laterBuy),
                        tuple(lateLoss, earlierBuyLowId),
                        tuple(lateLoss, earlierBuyHighId),
                        tuple(lateLoss, laterBuy)
                );
    }

    @Test
    void repurchaseFlagCountSkipsLossesWhoseOnlyMatchesArePossibleSamePosition() {
        LocalDate opened = LocalDate.of(2025, 6, 1);
        Trade partialCloseLoss = loss("AAPL").account(ibkr).heldBetween(opened, LocalDate.of(2025, 6, 15)).build();
        Trade partialCloseRemainder = trade("AAPL").account(ibkr).heldBetween(opened, LocalDate.of(2025, 6, 20)).build();
        Trade rebuyLoss = loss("MSFT").account(ibkr).heldBetween(opened, LocalDate.of(2025, 6, 15)).build();
        Trade rebuy = trade("MSFT").account(ibkr).heldBetween(LocalDate.of(2025, 6, 16), LocalDate.of(2025, 6, 25)).build();
        Trade mixedLoss = loss("NVDA").account(ibkr).heldBetween(opened, LocalDate.of(2025, 6, 15)).build();
        Trade mixedRemainder = trade("NVDA").account(ibkr).heldBetween(opened, LocalDate.of(2025, 6, 20)).build();
        Trade mixedRebuy = trade("NVDA").account(apex).heldBetween(LocalDate.of(2025, 6, 18), LocalDate.of(2025, 6, 25)).build();

        List<RepurchaseMatch> matches = TaxYearReportService.reviewRepurchases(
                List.of(partialCloseLoss, rebuyLoss, mixedLoss),
                List.of(partialCloseLoss, partialCloseRemainder, rebuyLoss, rebuy, mixedLoss, mixedRemainder, mixedRebuy),
                reportAccounts
        );

        assertThat(matches).hasSize(4);
        assertThat(TaxYearReportService.countRepurchaseFlags(matches)).isEqualTo(2);
    }

    private AnnualSummaryResponse summarize(List<Trade> trades) {
        return TaxYearReportService.buildSummary(TAX_YEAR, trades, reportAccounts, 0);
    }

    private static ReportFixtures.TradeBuilder loss(String symbol) {
        return trade(symbol).heldBetween(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 15)).realizedPnl("-25.00");
    }
}
