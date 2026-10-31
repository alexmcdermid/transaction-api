package com.transactionapi.service;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.AnnualSummaryResponse;
import com.transactionapi.dto.SummaryRow;
import com.transactionapi.model.Trade;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

/**
 * Writes the tax year CSV exports. Text cells are guarded against spreadsheet formula injection;
 * numbers are plain decimals, dates ISO {@code yyyy-MM-dd} and instants ISO-8601 UTC.
 */
@Service
public class TaxYearCsvWriter {

    private static final List<String> TRADE_LEDGER_HEADER = List.of(
            "trade_id", "account_id", "account_name", "account_tax_free", "symbol", "asset_type", "option_type",
            "strike_price", "expiry_date", "currency", "direction", "quantity", "entry_price", "exit_price", "fees",
            "margin_rate", "margin_interest", "opened_at", "closed_at", "gross_pnl", "recorded_realized_pnl", "notes",
            "created_at", "updated_at"
    );
    private static final List<String> DISPOSITIONS_HEADER = List.of(
            "trade_id", "description", "symbol", "asset_type", "position", "quantity", "opened_on", "closed_on",
            "currency", "proceeds", "cost", "outlays_and_expenses", "gain_loss", "account_name"
    );
    private static final List<String> ANNUAL_SUMMARY_HEADER = List.of(
            "section", "key", "label", "currency", "tax_free", "trade_count", "winning_trades", "losing_trades",
            "gains", "losses", "fees", "margin_interest", "net_realized_pnl"
    );
    private static final List<String> REPURCHASE_REVIEW_HEADER = List.of(
            "loss_trade_id", "symbol", "loss_closed_on", "loss_currency", "loss_amount", "loss_account_name",
            "match_trade_id", "match_asset_type", "match_direction", "match_opened_on", "match_account_name",
            "match_account_tax_free", "days_from_loss", "possible_same_position"
    );

    /** Every filtered closed trade as recorded. Notes stay blank unless {@code includeNotes}. */
    String tradeLedger(List<Trade> trades, ReportAccounts accounts, boolean includeNotes) {
        List<List<String>> records = trades.stream()
                .map(trade -> List.of(
                        trade.getId().toString(),
                        uuid(trade.getAccountId()),
                        text(accounts.name(trade.getAccountId())),
                        String.valueOf(accounts.isTaxFree(trade.getAccountId())),
                        text(trade.getSymbol()),
                        trade.getAssetType().name(),
                        trade.getOptionType() != null ? trade.getOptionType().name() : "",
                        plain(trade.getStrikePrice()),
                        date(trade.getExpiryDate()),
                        trade.getCurrency().name(),
                        trade.getDirection().name(),
                        plain(trade.getQuantity()),
                        plain(trade.getEntryPrice()),
                        plain(trade.getExitPrice()),
                        money(trade.getFees()),
                        plain(trade.getMarginRate()),
                        money(TradeCalculations.marginInterest(trade)),
                        date(trade.getOpenedAt()),
                        date(trade.getClosedAt()),
                        money(TradeCalculations.grossPnl(trade)),
                        money(trade.getRealizedPnl()),
                        includeNotes ? text(trade.getNotes()) : "",
                        instant(trade.getCreatedAt()),
                        instant(trade.getUpdatedAt())
                ))
                .toList();
        return write(TRADE_LEDGER_HEADER, records);
    }

    /** Proceeds and cost per closed trade. Trades in tax-free accounts are left out; unassigned stay in. */
    String dispositions(List<Trade> trades, ReportAccounts accounts) {
        List<List<String>> records = trades.stream()
                .filter(trade -> !accounts.isTaxFree(trade.getAccountId()))
                .map(trade -> {
                    Disposition disposition = disposition(trade);
                    return List.of(
                            trade.getId().toString(),
                            text(description(trade)),
                            text(trade.getSymbol()),
                            trade.getAssetType().name(),
                            trade.getDirection().name(),
                            plain(trade.getQuantity()),
                            date(trade.getOpenedAt()),
                            date(trade.getClosedAt()),
                            trade.getCurrency().name(),
                            disposition.proceeds().toPlainString(),
                            disposition.cost().toPlainString(),
                            disposition.outlaysAndExpenses().toPlainString(),
                            disposition.gainLoss().toPlainString(),
                            text(accounts.name(trade.getAccountId()))
                    );
                })
                .toList();
        return write(DISPOSITIONS_HEADER, records);
    }

    /** The JSON summary sections, in the same order, as one long-format table. */
    String annualSummary(AnnualSummaryResponse summary) {
        List<List<String>> records = Stream.of(
                        summaryRecords("TOTAL", summary.totals()),
                        summaryRecords("MONTH", summary.months()),
                        summaryRecords("ACCOUNT", summary.accounts()),
                        summaryRecords("SYMBOL", summary.symbols()),
                        summaryRecords("ASSET_TYPE", summary.assetTypes()),
                        summaryRecords("TAX_TREATMENT", summary.taxTreatment())
                )
                .flatMap(List::stream)
                .toList();
        return write(ANNUAL_SUMMARY_HEADER, records);
    }

    String repurchaseReview(List<RepurchaseMatch> matches, ReportAccounts accounts) {
        List<List<String>> records = matches.stream()
                .map(match -> {
                    Trade lossTrade = match.lossTrade();
                    Trade matchTrade = match.matchTrade();
                    return List.of(
                            lossTrade.getId().toString(),
                            text(lossTrade.getSymbol()),
                            date(lossTrade.getClosedAt()),
                            lossTrade.getCurrency().name(),
                            money(lossTrade.getRealizedPnl()),
                            text(accounts.name(lossTrade.getAccountId())),
                            matchTrade.getId().toString(),
                            matchTrade.getAssetType().name(),
                            matchTrade.getDirection().name(),
                            date(matchTrade.getOpenedAt()),
                            text(accounts.name(matchTrade.getAccountId())),
                            String.valueOf(accounts.isTaxFree(matchTrade.getAccountId())),
                            String.valueOf(match.daysFromLoss()),
                            String.valueOf(match.possibleSamePosition())
                    );
                })
                .toList();
        return write(REPURCHASE_REVIEW_HEADER, records);
    }

    /**
     * LONG sells at exit and bought at entry; SHORT sold at entry and bought back at exit. Outlays are the
     * recorded fees, and {@code gainLoss = proceeds - cost - outlays}, so every row foots exactly. Estimated
     * margin interest is a cost of borrowing, not of the sale, so it stays out: gain/loss exceeds recorded
     * realized P/L by that interest.
     */
    static Disposition disposition(Trade trade) {
        BigDecimal units = trade.getQuantity().multiply(TradeCalculations.multiplier(trade.getAssetType()));
        BigDecimal entryValue = trade.getEntryPrice().multiply(units).setScale(2, RoundingMode.HALF_UP);
        BigDecimal exitValue = trade.getExitPrice().multiply(units).setScale(2, RoundingMode.HALF_UP);
        boolean isShort = trade.getDirection() == TradeDirection.SHORT;
        BigDecimal proceeds = isShort ? entryValue : exitValue;
        BigDecimal cost = isShort ? exitValue : entryValue;
        BigDecimal outlaysAndExpenses = trade.getFees().setScale(2, RoundingMode.HALF_UP);
        return new Disposition(proceeds, cost, outlaysAndExpenses, proceeds.subtract(cost).subtract(outlaysAndExpenses));
    }

    /** {@code 100 AAPL} for stock, {@code 2 AAPL 2025-03-21 150 CALL} for options. */
    static String description(Trade trade) {
        String quantityAndSymbol = plain(trade.getQuantity()) + " " + trade.getSymbol();
        if (trade.getAssetType() != AssetType.OPTION) {
            return quantityAndSymbol;
        }
        return quantityAndSymbol + " " + date(trade.getExpiryDate()) + " " + plain(trade.getStrikePrice())
                + " " + trade.getOptionType().name();
    }

    /** Prefixes {@code '} to text a spreadsheet would otherwise evaluate as a formula. */
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return switch (value.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r', '\n' -> "'" + value;
            default -> value;
        };
    }

    private static List<List<String>> summaryRecords(String section, List<SummaryRow> rows) {
        return rows.stream()
                .map(row -> List.of(
                        section,
                        text(row.key()),
                        text(row.label()),
                        row.currency().name(),
                        row.taxFree() != null ? row.taxFree().toString() : "",
                        String.valueOf(row.tradeCount()),
                        String.valueOf(row.winningTrades()),
                        String.valueOf(row.losingTrades()),
                        money(row.gains()),
                        money(row.losses()),
                        money(row.fees()),
                        money(row.marginInterest()),
                        money(row.netRealizedPnl())
                ))
                .toList();
    }

    private static String write(List<String> header, List<List<String>> records) {
        StringBuilder csv = new StringBuilder();
        try (CSVPrinter printer = CSVFormat.DEFAULT.print(csv)) {
            printer.printRecord(header);
            printer.printRecords(records);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write CSV export", ex);
        }
        return csv.toString();
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Non-money decimals keep their value but drop storage padding, e.g. quantity 100.0000000000 -> 100. */
    private static String plain(BigDecimal value) {
        return value != null ? value.stripTrailingZeros().toPlainString() : "";
    }

    private static String date(LocalDate value) {
        return value != null ? value.toString() : "";
    }

    private static String instant(Instant value) {
        return value != null ? value.toString() : "";
    }

    private static String uuid(UUID value) {
        return value != null ? value.toString() : "";
    }

    record Disposition(BigDecimal proceeds, BigDecimal cost, BigDecimal outlaysAndExpenses, BigDecimal gainLoss) {
    }
}
