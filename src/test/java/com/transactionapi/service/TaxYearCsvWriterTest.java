package com.transactionapi.service;

import static com.transactionapi.service.ReportFixtures.account;
import static com.transactionapi.service.ReportFixtures.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.transactionapi.constants.Currency;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.AnnualSummaryResponse;
import com.transactionapi.model.Account;
import com.transactionapi.model.Trade;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

class TaxYearCsvWriterTest {

    private static final String TRADE_LEDGER_HEADER = "trade_id,account_id,account_name,account_tax_free,symbol,"
            + "asset_type,option_type,strike_price,expiry_date,currency,direction,quantity,entry_price,exit_price,fees,"
            + "margin_rate,margin_interest,opened_at,closed_at,gross_pnl,recorded_realized_pnl,notes,created_at,updated_at";
    private static final String DISPOSITIONS_HEADER = "trade_id,description,symbol,asset_type,position,quantity,"
            + "opened_on,closed_on,currency,proceeds,cost,outlays_and_expenses,gain_loss,account_name";
    private static final String ANNUAL_SUMMARY_HEADER = "section,key,label,currency,tax_free,trade_count,"
            + "winning_trades,losing_trades,gains,losses,fees,margin_interest,net_realized_pnl";
    private static final String REPURCHASE_REVIEW_HEADER = "loss_trade_id,symbol,loss_closed_on,loss_currency,"
            + "loss_amount,loss_account_name,match_trade_id,match_asset_type,match_direction,match_opened_on,"
            + "match_account_name,match_account_tax_free,days_from_loss,possible_same_position";

    private final TaxYearCsvWriter writer = new TaxYearCsvWriter();
    private final Account ibkr = account("IBKR", false);
    private final Account tfsa = account("Wealthsimple TFSA", true);
    private final ReportAccounts reportAccounts = ReportAccounts.of(List.of(ibkr, tfsa));

    @Test
    void emptyResultsAreTheExactHeaderRowOnly() {
        AnnualSummaryResponse emptySummary = TaxYearReportService.buildSummary(2025, List.of(), reportAccounts, 0);

        assertThat(writer.tradeLedger(List.of(), reportAccounts, true)).isEqualTo(TRADE_LEDGER_HEADER + "\r\n");
        assertThat(writer.dispositions(List.of(), reportAccounts)).isEqualTo(DISPOSITIONS_HEADER + "\r\n");
        assertThat(writer.annualSummary(emptySummary)).isEqualTo(ANNUAL_SUMMARY_HEADER + "\r\n");
        assertThat(writer.repurchaseReview(List.of(), reportAccounts)).isEqualTo(REPURCHASE_REVIEW_HEADER + "\r\n");
    }

    @Test
    void tradeLedgerWritesEachTradeAsRecorded() {
        Trade shortPut = trade("AAPL").account(ibkr)
                .option(OptionType.PUT, "100.0000", LocalDate.of(2025, 6, 20))
                .direction(TradeDirection.SHORT).quantity("2.0000000000").prices("3.1000", "1.1000")
                .fees("4.00").marginRate("5.0000").heldBetween(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31))
                .auditTimestamps(Instant.parse("2025-05-31T20:15:30Z"), Instant.parse("2025-06-01T08:00:00.123456Z"))
                .build();
        Trade unassignedStock = trade("SHOP").currency(Currency.CAD).quantity("10").prices("80.00", "81.00").build();

        List<CSVRecord> records = parse(writer.tradeLedger(List.of(shortPut, unassignedStock), reportAccounts, false));

        CSVRecord option = records.get(0);
        assertThat(option.get("trade_id")).isEqualTo(shortPut.getId().toString());
        assertThat(option.get("account_id")).isEqualTo(ibkr.getId().toString());
        assertThat(option.get("account_name")).isEqualTo("IBKR");
        assertThat(option.get("account_tax_free")).isEqualTo("false");
        assertThat(option.get("symbol")).isEqualTo("AAPL");
        assertThat(option.get("asset_type")).isEqualTo("OPTION");
        assertThat(option.get("option_type")).isEqualTo("PUT");
        assertThat(option.get("strike_price")).isEqualTo("100");
        assertThat(option.get("expiry_date")).isEqualTo("2025-06-20");
        assertThat(option.get("currency")).isEqualTo("USD");
        assertThat(option.get("direction")).isEqualTo("SHORT");
        assertThat(option.get("quantity")).isEqualTo("2");
        assertThat(option.get("entry_price")).isEqualTo("3.1");
        assertThat(option.get("exit_price")).isEqualTo("1.1");
        assertThat(option.get("fees")).isEqualTo("4.00");
        assertThat(option.get("margin_rate")).isEqualTo("5");
        assertThat(option.get("margin_interest")).isEqualTo("2.55");
        assertThat(option.get("opened_at")).isEqualTo("2025-05-01");
        assertThat(option.get("closed_at")).isEqualTo("2025-05-31");
        assertThat(option.get("gross_pnl")).isEqualTo("400.00");
        assertThat(option.get("recorded_realized_pnl")).isEqualTo("393.45");
        assertThat(option.get("notes")).isEmpty();
        assertThat(option.get("created_at")).isEqualTo("2025-05-31T20:15:30Z");
        assertThat(option.get("updated_at")).isEqualTo("2025-06-01T08:00:00.123456Z");

        CSVRecord stock = records.get(1);
        assertThat(stock.get("account_id")).isEmpty();
        assertThat(stock.get("account_name")).isEqualTo("Unassigned");
        assertThat(stock.get("account_tax_free")).isEqualTo("false");
        assertThat(stock.get("option_type")).isEmpty();
        assertThat(stock.get("strike_price")).isEmpty();
        assertThat(stock.get("expiry_date")).isEmpty();
        assertThat(stock.get("currency")).isEqualTo("CAD");
        assertThat(stock.get("gross_pnl")).isEqualTo("10.00");
        assertThat(stock.get("margin_interest")).isEqualTo("0.00");
    }

    @Test
    void tradeLedgerGrossPnlIsRoundedToCents() {
        Trade fractional = trade("XYZ").quantity("3").prices("10.0001", "10.0004").build();

        CSVRecord record = parse(writer.tradeLedger(List.of(fractional), reportAccounts, false)).getFirst();

        assertThat(record.get("gross_pnl")).isEqualTo("0.00");
    }

    @Test
    void notesStayBlankUnlessIncludeNotes() {
        Trade withNotes = trade("AAPL").notes("covered call").build();

        CSVRecord withoutNotes = parse(writer.tradeLedger(List.of(withNotes), reportAccounts, false)).getFirst();
        CSVRecord includingNotes = parse(writer.tradeLedger(List.of(withNotes), reportAccounts, true)).getFirst();

        assertThat(withoutNotes.isMapped("notes")).isTrue();
        assertThat(withoutNotes.get("notes")).isEmpty();
        assertThat(includingNotes.get("notes")).isEqualTo("covered call");
    }

    @Test
    void textCellsAreNeutralizedAgainstFormulaInjection() {
        Account formulaAccount = account("@SUM(A1:A9)", false);
        ReportAccounts accountsWithFormula = ReportAccounts.of(List.of(formulaAccount));
        Trade injected = trade("=CMD").account(formulaAccount).quantity("5").prices("50.00", "45.00").fees("0.50")
                .notes("+HYPERLINK(\"http://x\")").build();

        CSVRecord ledger = parse(writer.tradeLedger(List.of(injected), accountsWithFormula, true)).getFirst();
        CSVRecord disposition = parse(writer.dispositions(List.of(injected), accountsWithFormula)).getFirst();

        assertThat(ledger.get("symbol")).isEqualTo("'=CMD");
        assertThat(ledger.get("account_name")).isEqualTo("'@SUM(A1:A9)");
        assertThat(ledger.get("notes")).isEqualTo("'+HYPERLINK(\"http://x\")");
        assertThat(ledger.get("gross_pnl")).isEqualTo("-25.00");
        assertThat(ledger.get("recorded_realized_pnl")).isEqualTo("-25.50");
        assertThat(disposition.get("symbol")).isEqualTo("'=CMD");
        assertThat(disposition.get("description")).isEqualTo("5 =CMD");
        assertThat(disposition.get("account_name")).isEqualTo("'@SUM(A1:A9)");
        assertThat(disposition.get("gain_loss")).isEqualTo("-25.50");
    }

    @Test
    void summaryAndReviewTextCellsAreNeutralizedButNumbersAreNot() {
        Account formulaAccount = account("-Margin", false);
        ReportAccounts accountsWithFormula = ReportAccounts.of(List.of(formulaAccount));
        Trade loss = trade("-X").account(formulaAccount).heldBetween(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 15))
                .realizedPnl("-25.50").build();
        Trade rebuy = trade("-X").heldBetween(LocalDate.of(2025, 6, 10), LocalDate.of(2025, 6, 20)).build();
        AnnualSummaryResponse summary = TaxYearReportService.buildSummary(2025, List.of(loss), accountsWithFormula, 0);
        List<RepurchaseMatch> matches = TaxYearReportService.reviewRepurchases(
                List.of(loss), List.of(loss, rebuy), accountsWithFormula);

        List<CSVRecord> summaryRecords = parse(writer.annualSummary(summary));
        CSVRecord review = parse(writer.repurchaseReview(matches, accountsWithFormula)).getFirst();

        assertThat(summaryRecords)
                .filteredOn(record -> record.get("section").equals("SYMBOL"))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.get("key")).isEqualTo("'-X");
                    assertThat(record.get("label")).isEqualTo("'-X");
                    assertThat(record.get("losses")).isEqualTo("-25.50");
                });
        assertThat(summaryRecords)
                .filteredOn(record -> record.get("section").equals("ACCOUNT"))
                .singleElement()
                .satisfies(record -> assertThat(record.get("label")).isEqualTo("'-Margin"));
        assertThat(review.get("symbol")).isEqualTo("'-X");
        assertThat(review.get("loss_account_name")).isEqualTo("'-Margin");
        assertThat(review.get("loss_amount")).isEqualTo("-25.50");
        assertThat(review.get("days_from_loss")).isEqualTo("-5");
    }

    @Test
    void textGuardPrefixesEveryFormulaTrigger() {
        assertThat(List.of("=1+2", "+1", "-1", "@A1", "\tx", "\rx", "\nx"))
                .allSatisfy(value -> assertThat(TaxYearCsvWriter.text(value)).isEqualTo("'" + value));
        assertThat(TaxYearCsvWriter.text("AAPL")).isEqualTo("AAPL");
        assertThat(TaxYearCsvWriter.text("1-800")).isEqualTo("1-800");
        assertThat(TaxYearCsvWriter.text("")).isEmpty();
        assertThat(TaxYearCsvWriter.text(null)).isEmpty();
    }

    @Test
    void commasQuotesAndNewlinesAreQuotedByCommonsCsv() {
        String notes = "He said \"sell\", then\nleft";
        Trade trade = trade("AAPL").notes(notes).build();

        String csv = writer.tradeLedger(List.of(trade), reportAccounts, true);

        assertThat(csv).contains(",\"He said \"\"sell\"\", then\nleft\",");
        assertThat(csv).endsWith("\r\n");
        assertThat(parse(csv).getFirst().get("notes")).isEqualTo(notes);
    }

    @Test
    void decimalsNeverUseExponentNotation() {
        Trade trade = trade("AAPL").option(OptionType.CALL, "1.5E+2", LocalDate.of(2025, 3, 21)).build();
        trade.setQuantity(new BigDecimal("1E+3"));
        trade.setEntryPrice(new BigDecimal("1E+1"));
        trade.setExitPrice(new BigDecimal("2E+1"));
        trade.setFees(new BigDecimal("1E+1"));
        trade.setRealizedPnl(new BigDecimal("1E+6"));

        CSVRecord ledger = parse(writer.tradeLedger(List.of(trade), reportAccounts, false)).getFirst();
        CSVRecord disposition = parse(writer.dispositions(List.of(trade), reportAccounts)).getFirst();

        assertThat(ledger.get("quantity")).isEqualTo("1000");
        assertThat(ledger.get("strike_price")).isEqualTo("150");
        assertThat(ledger.get("entry_price")).isEqualTo("10");
        assertThat(ledger.get("fees")).isEqualTo("10.00");
        assertThat(ledger.get("gross_pnl")).isEqualTo("1000000.00");
        assertThat(ledger.get("recorded_realized_pnl")).isEqualTo("1000000.00");
        assertThat(disposition.get("description")).isEqualTo("1000 AAPL 2025-03-21 150 CALL");
        assertThat(disposition.get("proceeds")).isEqualTo("2000000.00");
        assertThat(disposition.get("cost")).isEqualTo("1000000.00");
        assertThat(String.join(",", ledger.values()) + String.join(",", disposition.values())).doesNotContain("E+");
    }

    @Test
    void longStockProceedsAreExitValueAndCostIsEntryValue() {
        Trade longStock = trade("AAPL").quantity("100").prices("150.25", "155.75").fees("1.00").build();

        CSVRecord record = parse(writer.dispositions(List.of(longStock), reportAccounts)).getFirst();

        assertThat(record.get("description")).isEqualTo("100 AAPL");
        assertThat(record.get("position")).isEqualTo("LONG");
        assertThat(record.get("proceeds")).isEqualTo("15575.00");
        assertThat(record.get("cost")).isEqualTo("15025.00");
        assertThat(record.get("outlays_and_expenses")).isEqualTo("1.00");
        assertThat(record.get("gain_loss")).isEqualTo("549.00");
    }

    @Test
    void shortStockProceedsAreEntryValueAndCostIsExitValue() {
        Trade shortStock = trade("TSLA").direction(TradeDirection.SHORT).quantity("50").prices("200.00", "210.00")
                .fees("0.50").build();

        TaxYearCsvWriter.Disposition disposition = TaxYearCsvWriter.disposition(shortStock);

        assertThat(disposition.proceeds()).isEqualTo("10000.00");
        assertThat(disposition.cost()).isEqualTo("10500.00");
        assertThat(disposition.outlaysAndExpenses()).isEqualTo("0.50");
        assertThat(disposition.gainLoss()).isEqualTo("-500.50");
    }

    @Test
    void longOptionAppliesTheContractMultiplier() {
        Trade longCall = trade("AAPL").option(OptionType.CALL, "150.0000", LocalDate.of(2025, 3, 21))
                .quantity("2").prices("1.25", "2.10").fees("1.30").build();

        CSVRecord record = parse(writer.dispositions(List.of(longCall), reportAccounts)).getFirst();

        assertThat(record.get("description")).isEqualTo("2 AAPL 2025-03-21 150 CALL");
        assertThat(record.get("asset_type")).isEqualTo("OPTION");
        assertThat(record.get("proceeds")).isEqualTo("420.00");
        assertThat(record.get("cost")).isEqualTo("250.00");
        assertThat(record.get("outlays_and_expenses")).isEqualTo("1.30");
        assertThat(record.get("gain_loss")).isEqualTo("168.70");
    }

    @Test
    void shortOptionProceedsAreThePremiumReceived() {
        Trade shortPut = trade("SPY").option(OptionType.PUT, "512.5000", LocalDate.of(2025, 6, 20))
                .direction(TradeDirection.SHORT).quantity("3").prices("4.20", "1.05").fees("1.95").build();

        CSVRecord record = parse(writer.dispositions(List.of(shortPut), reportAccounts)).getFirst();

        assertThat(record.get("description")).isEqualTo("3 SPY 2025-06-20 512.5 PUT");
        assertThat(record.get("position")).isEqualTo("SHORT");
        assertThat(record.get("proceeds")).isEqualTo("1260.00");
        assertThat(record.get("cost")).isEqualTo("315.00");
        assertThat(record.get("outlays_and_expenses")).isEqualTo("1.95");
        assertThat(record.get("gain_loss")).isEqualTo("943.05");
    }

    @Test
    void outlaysAreTheRecordedFeesWithoutEstimatedMarginInterest() {
        Trade marginLong = trade("AAPL").quantity("100").prices("150.00", "160.00").fees("2.00").marginRate("7.30")
                .heldBetween(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 20)).build();

        CSVRecord ledger = parse(writer.tradeLedger(List.of(marginLong), reportAccounts, false)).getFirst();
        CSVRecord disposition = parse(writer.dispositions(List.of(marginLong), reportAccounts)).getFirst();

        // margin interest: 15000 notional * 7.3% * 50/365 = 150.00, already taken out of recorded realized P/L
        assertThat(ledger.get("margin_interest")).isEqualTo("150.00");
        assertThat(ledger.get("recorded_realized_pnl")).isEqualTo("848.00");
        assertThat(disposition.get("proceeds")).isEqualTo("16000.00");
        assertThat(disposition.get("cost")).isEqualTo("15000.00");
        assertThat(disposition.get("outlays_and_expenses")).isEqualTo("2.00");
        assertThat(disposition.get("gain_loss")).isEqualTo("998.00");
    }

    @Test
    void fractionalQuantitiesRoundHalfUpAndStillFoot() {
        Trade halfShare = trade("BTC").quantity("0.5000000000").prices("10.00", "10.01").build();
        Trade thirdShare = trade("XYZ").quantity("0.3").prices("1.115", "1.125").build();

        TaxYearCsvWriter.Disposition half = TaxYearCsvWriter.disposition(halfShare);
        TaxYearCsvWriter.Disposition third = TaxYearCsvWriter.disposition(thirdShare);

        assertThat(TaxYearCsvWriter.description(halfShare)).isEqualTo("0.5 BTC");
        assertThat(half.proceeds()).isEqualTo("5.01");
        assertThat(half.cost()).isEqualTo("5.00");
        assertThat(half.gainLoss()).isEqualTo("0.01");
        assertThat(half.outlaysAndExpenses()).isEqualTo("0.00");
        assertThat(third.proceeds()).isEqualTo("0.34");
        assertThat(third.cost()).isEqualTo("0.33");
        // Rounding proceeds and cost separately puts gain/loss 0.01 off the recorded 0.00; outlays stay the fees.
        assertThat(thirdShare.getRealizedPnl()).isEqualTo("0.00");
        assertThat(third.outlaysAndExpenses()).isEqualTo("0.00");
        assertThat(third.gainLoss()).isEqualTo("0.01");
    }

    @Test
    void everyDispositionRowFootsExactly() {
        List<Trade> trades = new ArrayList<>();
        for (TradeDirection direction : TradeDirection.values()) {
            for (String quantity : List.of("1", "0.3333", "7", "12.5")) {
                for (String fees : List.of("0", "0.99", "4.95")) {
                    trades.add(trade("AAPL").direction(direction).quantity(quantity).prices("10.0137", "9.4471")
                            .fees(fees).marginRate("7.25").heldBetween(LocalDate.of(2025, 1, 3), LocalDate.of(2025, 2, 17))
                            .build());
                    trades.add(trade("AAPL").option(OptionType.CALL, "12.5", LocalDate.of(2025, 4, 17))
                            .direction(direction).quantity(quantity).prices("0.37", "1.0450").fees(fees)
                            .marginRate("6.10").heldBetween(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 4, 1)).build());
                }
            }
        }

        List<CSVRecord> records = parse(writer.dispositions(trades, reportAccounts));

        assertThat(records).hasSize(trades.size());
        assertThat(records).allSatisfy(record -> assertThat(
                new BigDecimal(record.get("proceeds"))
                        .subtract(new BigDecimal(record.get("cost")))
                        .subtract(new BigDecimal(record.get("outlays_and_expenses"))))
                .isEqualTo(new BigDecimal(record.get("gain_loss"))));
    }

    @Test
    void dispositionsLeaveOutTaxFreeAccountsAndKeepUnassigned() {
        Trade taxable = trade("AAPL").account(ibkr).build();
        Trade taxFree = trade("SHOP").account(tfsa).build();
        Trade unassigned = trade("NVDA").build();

        List<CSVRecord> records = parse(writer.dispositions(List.of(taxable, taxFree, unassigned), reportAccounts));

        assertThat(records)
                .extracting(record -> record.get("trade_id"), record -> record.get("account_name"))
                .containsExactly(
                        tuple(taxable.getId().toString(), "IBKR"),
                        tuple(unassigned.getId().toString(), "Unassigned")
                );
    }

    @Test
    void annualSummaryEmitsEverySectionInJsonOrder() {
        LocalDate expiry = LocalDate.of(2025, 12, 19);
        AnnualSummaryResponse summary = TaxYearReportService.buildSummary(2025, List.of(
                trade("AAPL").account(ibkr).closedOn(LocalDate.of(2025, 2, 3)).realizedPnl("100.00").build(),
                trade("SHOP").account(tfsa).currency(Currency.CAD).closedOn(LocalDate.of(2025, 1, 6)).realizedPnl("-40.00")
                        .build(),
                trade("SPY").option(OptionType.PUT, "500", expiry).closedOn(LocalDate.of(2025, 2, 10)).fees("1.30")
                        .realizedPnl("12.34").build()
        ), reportAccounts, 3);

        List<CSVRecord> records = parse(writer.annualSummary(summary));

        assertThat(records)
                .extracting(record -> record.get("section"), record -> record.get("key"), record -> record.get("currency"),
                        record -> record.get("tax_free"))
                .containsExactly(
                        tuple("TOTAL", "ALL", "CAD", ""),
                        tuple("TOTAL", "ALL", "USD", ""),
                        tuple("MONTH", "2025-01", "CAD", ""),
                        tuple("MONTH", "2025-02", "USD", ""),
                        tuple("ACCOUNT", ibkr.getId().toString(), "USD", "false"),
                        tuple("ACCOUNT", "UNASSIGNED", "USD", "false"),
                        tuple("ACCOUNT", tfsa.getId().toString(), "CAD", "true"),
                        tuple("SYMBOL", "AAPL", "USD", ""),
                        tuple("SYMBOL", "SPY", "USD", ""),
                        tuple("SYMBOL", "SHOP", "CAD", ""),
                        tuple("ASSET_TYPE", "STOCK", "CAD", ""),
                        tuple("ASSET_TYPE", "STOCK", "USD", ""),
                        tuple("ASSET_TYPE", "OPTION_PUT", "USD", ""),
                        tuple("TAX_TREATMENT", "TAXABLE", "USD", "false"),
                        tuple("TAX_TREATMENT", "TAX_FREE", "CAD", "true")
                );
        assertThat(records.get(1).toMap())
                .containsEntry("label", "All trades")
                .containsEntry("trade_count", "2")
                .containsEntry("winning_trades", "2")
                .containsEntry("losing_trades", "0")
                .containsEntry("gains", "112.34")
                .containsEntry("losses", "0.00")
                .containsEntry("fees", "1.30")
                .containsEntry("margin_interest", "0.00")
                .containsEntry("net_realized_pnl", "112.34");
    }

    @Test
    void repurchaseReviewWritesOneRowPerLossAndMatchPair() {
        Trade loss = trade("AAPL").account(ibkr).heldBetween(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 15))
                .realizedPnl("-25.00").build();
        Trade remainder = trade("AAPL").account(ibkr).heldBetween(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30))
                .build();
        Trade taxFreeRebuy = trade("AAPL").account(tfsa).direction(TradeDirection.SHORT)
                .heldBetween(LocalDate.of(2025, 6, 20), LocalDate.of(2025, 7, 2)).build();
        List<RepurchaseMatch> matches = TaxYearReportService.reviewRepurchases(
                List.of(loss), List.of(loss, remainder, taxFreeRebuy), reportAccounts);

        List<CSVRecord> records = parse(writer.repurchaseReview(matches, reportAccounts));

        assertThat(records).extracting(CSVRecord::toMap).containsExactly(
                Map.ofEntries(
                        Map.entry("loss_trade_id", loss.getId().toString()),
                        Map.entry("symbol", "AAPL"),
                        Map.entry("loss_closed_on", "2025-06-15"),
                        Map.entry("loss_currency", "USD"),
                        Map.entry("loss_amount", "-25.00"),
                        Map.entry("loss_account_name", "IBKR"),
                        Map.entry("match_trade_id", remainder.getId().toString()),
                        Map.entry("match_asset_type", "STOCK"),
                        Map.entry("match_direction", "LONG"),
                        Map.entry("match_opened_on", "2025-06-01"),
                        Map.entry("match_account_name", "IBKR"),
                        Map.entry("match_account_tax_free", "false"),
                        Map.entry("days_from_loss", "-14"),
                        Map.entry("possible_same_position", "true")
                ),
                Map.ofEntries(
                        Map.entry("loss_trade_id", loss.getId().toString()),
                        Map.entry("symbol", "AAPL"),
                        Map.entry("loss_closed_on", "2025-06-15"),
                        Map.entry("loss_currency", "USD"),
                        Map.entry("loss_amount", "-25.00"),
                        Map.entry("loss_account_name", "IBKR"),
                        Map.entry("match_trade_id", taxFreeRebuy.getId().toString()),
                        Map.entry("match_asset_type", "STOCK"),
                        Map.entry("match_direction", "SHORT"),
                        Map.entry("match_opened_on", "2025-06-20"),
                        Map.entry("match_account_name", "Wealthsimple TFSA"),
                        Map.entry("match_account_tax_free", "true"),
                        Map.entry("days_from_loss", "5"),
                        Map.entry("possible_same_position", "false")
                )
        );
    }

    private static List<CSVRecord> parse(String csv) {
        CSVFormat headerFirst = CSVFormat.DEFAULT.builder().setHeader().get();
        try (CSVParser parser = CSVParser.parse(csv, headerFirst)) {
            return parser.getRecords();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
