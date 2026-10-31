package com.transactionapi.service;

import com.transactionapi.config.BillingSettings;
import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.AnnualSummaryResponse;
import com.transactionapi.dto.ReportFilters;
import com.transactionapi.dto.SummaryRow;
import com.transactionapi.dto.TaxYearExportRequest;
import com.transactionapi.dto.TaxYearOverviewResponse;
import com.transactionapi.model.Trade;
import com.transactionapi.model.User;
import com.transactionapi.repository.AccountRepository;
import com.transactionapi.repository.TradeRepository;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tax year reports and exports. Every paid report goes through loadReportData, which checks the tax
 * year, the PAID pass and account ownership before any trade is read. Amounts stay in each trade's
 * recorded currency and use its recorded realized P/L.
 */
@Service
@Transactional(readOnly = true)
public class TaxYearReportService {

    private static final int REPURCHASE_WINDOW_DAYS = 30;
    private static final String TOTAL_KEY = "ALL";
    private static final String TOTAL_LABEL = "All trades";
    private static final String UNASSIGNED_ACCOUNT_KEY = "UNASSIGNED";
    private static final ReportFilters NO_FILTERS = new ReportFilters(null, null, null, null, null, null);
    private static final Sort LEDGER_ORDER = Sort.by(Sort.Order.asc("closedAt"), Sort.Order.asc("id"));

    private static final Comparator<SummaryRow> BY_CURRENCY =
            Comparator.comparing((SummaryRow row) -> row.currency().name());
    private static final Comparator<SummaryRow> MONTH_ORDER =
            Comparator.comparing(SummaryRow::key).thenComparing(BY_CURRENCY);
    private static final Comparator<SummaryRow> ACCOUNT_ORDER =
            Comparator.comparing(SummaryRow::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(BY_CURRENCY)
                    .thenComparing(SummaryRow::key);
    private static final Comparator<SummaryRow> SYMBOL_ORDER =
            Comparator.comparing(SummaryRow::netRealizedPnl, Comparator.reverseOrder())
                    .thenComparing(SummaryRow::key)
                    .thenComparing(BY_CURRENCY);
    private static final Comparator<SummaryRow> ASSET_CLASS_ORDER =
            Comparator.comparing((SummaryRow row) -> SummaryAssetClass.valueOf(row.key())).thenComparing(BY_CURRENCY);
    private static final Comparator<SummaryRow> TAX_TREATMENT_ORDER =
            Comparator.comparing((SummaryRow row) -> TaxTreatment.valueOf(row.key())).thenComparing(BY_CURRENCY);

    // UUID.compareTo is signed; comparing the canonical strings matches PostgreSQL's uuid ordering.
    private static final Comparator<RepurchaseMatch> REPURCHASE_REVIEW_ORDER =
            Comparator.comparing((RepurchaseMatch match) -> match.lossTrade().getClosedAt())
                    .thenComparing(match -> match.lossTrade().getId().toString())
                    .thenComparing(match -> match.matchTrade().getOpenedAt())
                    .thenComparing(match -> match.matchTrade().getId().toString());

    private final TradeRepository tradeRepository;
    private final AccountRepository accountRepository;
    private final TaxYearPassService taxYearPassService;
    private final BillingSettings billingSettings;
    private final TaxYearCsvWriter taxYearCsvWriter;

    public TaxYearReportService(
            TradeRepository tradeRepository,
            AccountRepository accountRepository,
            TaxYearPassService taxYearPassService,
            BillingSettings billingSettings,
            TaxYearCsvWriter taxYearCsvWriter
    ) {
        this.tradeRepository = tradeRepository;
        this.accountRepository = accountRepository;
        this.taxYearPassService = taxYearPassService;
        this.billingSettings = billingSettings;
        this.taxYearCsvWriter = taxYearCsvWriter;
    }

    /** Free: every year with a closed trade plus the current year, newest first. */
    public TaxYearOverviewResponse overview(User user) {
        int currentTaxYear = taxYearPassService.currentTaxYear();
        boolean checkoutEnabled = billingSettings.isCheckoutEnabled();
        Map<Integer, Long> closedTradeCountByYear = new HashMap<>();
        for (TradeRepository.TaxYearTradeCountProjection row : tradeRepository.countClosedTradesByTaxYear(user.getAuthId())) {
            closedTradeCountByYear.put(row.getTaxYear(), row.getClosedTradeCount());
        }
        Map<Integer, TaxYearPassStatus> passStatusByYear = taxYearPassService.effectiveStatusByYear(user);

        Set<Integer> taxYears = new TreeSet<>(Comparator.reverseOrder());
        taxYears.addAll(closedTradeCountByYear.keySet());
        taxYears.add(currentTaxYear);

        List<TaxYearOverviewResponse.TaxYear> years = taxYears.stream()
                .map(taxYear -> {
                    TaxYearPassStatus passStatus = passStatusByYear.get(taxYear);
                    boolean unlocked = passStatus == TaxYearPassStatus.PAID;
                    boolean purchasable = checkoutEnabled
                            && !unlocked
                            && passStatus != TaxYearPassStatus.PROCESSING
                            && taxYear >= TaxYearPassService.EARLIEST_TAX_YEAR
                            && taxYear <= currentTaxYear;
                    return new TaxYearOverviewResponse.TaxYear(
                            taxYear,
                            closedTradeCountByYear.getOrDefault(taxYear, 0L),
                            passStatus,
                            unlocked,
                            purchasable
                    );
                })
                .toList();
        TaxYearOverviewResponse.DisplayPrice price = billingSettings.hasDisplayPrice()
                ? new TaxYearOverviewResponse.DisplayPrice(billingSettings.getPriceCents(), billingSettings.getPriceCurrency())
                : null;
        return new TaxYearOverviewResponse(currentTaxYear, checkoutEnabled, price, years);
    }

    public AnnualSummaryResponse summary(User user, int taxYear, ReportFilters filters) {
        return summarize(loadReportData(user, taxYear, filters));
    }

    /** UTF-8 CSV for the requested export; a valid empty result is the header row only. */
    public byte[] export(User user, int taxYear, TaxYearExportRequest request) {
        TaxYearReportData data = loadReportData(user, taxYear, request.filters());
        String csv = switch (request.reportType()) {
            case TRADE_LEDGER -> taxYearCsvWriter.tradeLedger(
                    data.trades(),
                    data.accounts(),
                    Boolean.TRUE.equals(request.includeNotes())
            );
            case DISPOSITIONS -> taxYearCsvWriter.dispositions(data.trades(), data.accounts());
            case ANNUAL_SUMMARY -> taxYearCsvWriter.annualSummary(summarize(data));
            case REPURCHASE_REVIEW -> taxYearCsvWriter.repurchaseReview(loadRepurchaseReview(data), data.accounts());
        };
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    private TaxYearReportData loadReportData(User user, int taxYear, ReportFilters requestedFilters) {
        taxYearPassService.requireValidTaxYear(taxYear);
        taxYearPassService.requirePaidPass(user, taxYear);

        ReportFilters filters = requestedFilters != null ? requestedFilters : NO_FILTERS;
        ReportAccounts accounts = ReportAccounts.of(accountRepository.findByUserIdOrderByCreatedAtDesc(user.getAuthId()));
        Set<UUID> accountIds = distinctNonNull(filters.accountIds());
        if (!accountIds.stream().allMatch(accounts::owns)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account access denied");
        }

        List<Trade> trades = tradeRepository.findAll(
                filteredTaxYearTrades(user.getAuthId(), taxYear, filters, accountIds),
                LEDGER_ORDER
        );
        return new TaxYearReportData(user.getAuthId(), taxYear, trades, accounts);
    }

    private AnnualSummaryResponse summarize(TaxYearReportData data) {
        int repurchaseFlagCount = countRepurchaseFlags(loadRepurchaseReview(data));
        return buildSummary(data.taxYear(), data.trades(), data.accounts(), repurchaseFlagCount);
    }

    private List<RepurchaseMatch> loadRepurchaseReview(TaxYearReportData data) {
        Set<String> lossSymbols = repurchaseLossTrades(data.trades(), data.accounts()).stream()
                .map(Trade::getSymbol)
                .collect(Collectors.toCollection(TreeSet::new));
        if (lossSymbols.isEmpty()) {
            return List.of();
        }
        LocalDate yearStart = LocalDate.of(data.taxYear(), 1, 1);
        LocalDate yearEnd = LocalDate.of(data.taxYear(), 12, 31);
        List<Trade> candidateTrades = tradeRepository.findByUserIdAndSymbolInAndOpenedAtBetween(
                data.authUserId(),
                lossSymbols,
                yearStart.minusDays(REPURCHASE_WINDOW_DAYS),
                yearEnd.plusDays(REPURCHASE_WINDOW_DAYS)
        );
        return reviewRepurchases(data.trades(), candidateTrades, data.accounts());
    }

    private static Specification<Trade> filteredTaxYearTrades(
            String authUserId,
            int taxYear,
            ReportFilters filters,
            Set<UUID> accountIds
    ) {
        LocalDate yearStart = LocalDate.of(taxYear, 1, 1);
        LocalDate nextYearStart = yearStart.plusYears(1);
        boolean includeUnassigned = !Boolean.FALSE.equals(filters.includeUnassigned());
        Set<String> symbols = normalizeSymbols(filters.symbols());
        Set<AssetType> assetTypes = distinctNonNull(filters.assetTypes());
        Set<Currency> currencies = distinctNonNull(filters.currencies());
        Set<TradeDirection> directions = distinctNonNull(filters.directions());

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("userId"), authUserId));
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("closedAt"), yearStart));
            predicates.add(criteriaBuilder.lessThan(root.get("closedAt"), nextYearStart));

            Path<UUID> accountId = root.get("accountId");
            if (!accountIds.isEmpty() && includeUnassigned) {
                predicates.add(criteriaBuilder.or(accountId.in(accountIds), criteriaBuilder.isNull(accountId)));
            } else if (!accountIds.isEmpty()) {
                predicates.add(accountId.in(accountIds));
            } else if (!includeUnassigned) {
                predicates.add(criteriaBuilder.isNotNull(accountId));
            }

            if (!symbols.isEmpty()) {
                predicates.add(root.get("symbol").in(symbols));
            }
            if (!assetTypes.isEmpty()) {
                predicates.add(root.get("assetType").in(assetTypes));
            }
            if (!currencies.isEmpty()) {
                predicates.add(root.get("currency").in(currencies));
            }
            if (!directions.isEmpty()) {
                predicates.add(root.get("direction").in(directions));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Set<String> normalizeSymbols(List<String> symbols) {
        if (symbols == null) {
            return Set.of();
        }
        return symbols.stream()
                .filter(Objects::nonNull)
                .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
                .filter(symbol -> !symbol.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static <T> Set<T> distinctNonNull(List<T> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static AnnualSummaryResponse buildSummary(
            int taxYear,
            List<Trade> trades,
            ReportAccounts accounts,
            int repurchaseFlagCount
    ) {
        return new AnnualSummaryResponse(
                taxYear,
                trades.size(),
                trades.stream().map(Trade::getClosedAt).min(Comparator.naturalOrder()).orElse(null),
                trades.stream().map(Trade::getClosedAt).max(Comparator.naturalOrder()).orElse(null),
                repurchaseFlagCount,
                summarizeBy(trades, trade -> new SummaryGroup(TOTAL_KEY, TOTAL_LABEL, null), BY_CURRENCY),
                summarizeBy(trades, TaxYearReportService::monthGroup, MONTH_ORDER),
                summarizeBy(trades, trade -> accountGroup(trade, accounts), ACCOUNT_ORDER),
                summarizeBy(trades, trade -> new SummaryGroup(trade.getSymbol(), trade.getSymbol(), null), SYMBOL_ORDER),
                summarizeBy(trades, trade -> SummaryAssetClass.of(trade).group(), ASSET_CLASS_ORDER),
                summarizeBy(trades, trade -> TaxTreatment.of(trade, accounts).group(), TAX_TREATMENT_ORDER)
        );
    }

    /** Losses eligible for the repurchase review: recorded realized P/L below zero in a taxable account. */
    static List<Trade> repurchaseLossTrades(List<Trade> filteredTrades, ReportAccounts accounts) {
        return filteredTrades.stream()
                .filter(trade -> trade.getRealizedPnl().signum() < 0)
                .filter(trade -> !accounts.isTaxFree(trade.getAccountId()))
                .toList();
    }

    /**
     * Pairs each taxable loss in {@code filteredTrades} with every other candidate trade of the same
     * symbol opened within 30 days of the loss closing, inclusive. Candidates are not filtered by
     * account or report filters.
     */
    static List<RepurchaseMatch> reviewRepurchases(
            List<Trade> filteredTrades,
            List<Trade> candidateTrades,
            ReportAccounts accounts
    ) {
        Map<String, List<Trade>> candidatesBySymbol = candidateTrades.stream()
                .collect(Collectors.groupingBy(Trade::getSymbol));
        List<RepurchaseMatch> matches = new ArrayList<>();
        for (Trade lossTrade : repurchaseLossTrades(filteredTrades, accounts)) {
            for (Trade candidate : candidatesBySymbol.getOrDefault(lossTrade.getSymbol(), List.of())) {
                if (candidate.getId().equals(lossTrade.getId())) {
                    continue;
                }
                long daysFromLoss = ChronoUnit.DAYS.between(lossTrade.getClosedAt(), candidate.getOpenedAt());
                if (Math.abs(daysFromLoss) > REPURCHASE_WINDOW_DAYS) {
                    continue;
                }
                boolean possibleSamePosition = candidate.getOpenedAt().equals(lossTrade.getOpenedAt())
                        && Objects.equals(candidate.getAccountId(), lossTrade.getAccountId())
                        && candidate.getDirection() == lossTrade.getDirection();
                matches.add(new RepurchaseMatch(lossTrade, candidate, daysFromLoss, possibleSamePosition));
            }
        }
        matches.sort(REPURCHASE_REVIEW_ORDER);
        return matches;
    }

    /** Distinct losses with at least one match that is not just another part of the same position. */
    static int countRepurchaseFlags(List<RepurchaseMatch> matches) {
        return (int) matches.stream()
                .filter(match -> !match.possibleSamePosition())
                .map(match -> match.lossTrade().getId())
                .distinct()
                .count();
    }

    private static SummaryGroup monthGroup(Trade trade) {
        String month = YearMonth.from(trade.getClosedAt()).toString();
        return new SummaryGroup(month, month, null);
    }

    private static SummaryGroup accountGroup(Trade trade, ReportAccounts accounts) {
        UUID accountId = trade.getAccountId();
        return new SummaryGroup(
                accountId != null ? accountId.toString() : UNASSIGNED_ACCOUNT_KEY,
                accounts.name(accountId),
                accounts.isTaxFree(accountId)
        );
    }

    private static List<SummaryRow> summarizeBy(
            List<Trade> trades,
            Function<Trade, SummaryGroup> groupOf,
            Comparator<SummaryRow> rowOrder
    ) {
        Map<GroupInCurrency, SummaryAccumulator> accumulators = new HashMap<>();
        for (Trade trade : trades) {
            accumulators.computeIfAbsent(
                    new GroupInCurrency(groupOf.apply(trade), trade.getCurrency()),
                    ignored -> new SummaryAccumulator()
            ).add(trade);
        }
        return accumulators.entrySet().stream()
                .map(entry -> entry.getValue().toRow(entry.getKey().group(), entry.getKey().currency()))
                .sorted(rowOrder)
                .toList();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record TaxYearReportData(String authUserId, int taxYear, List<Trade> trades, ReportAccounts accounts) {
    }

    private record SummaryGroup(String key, String label, Boolean taxFree) {
    }

    private record GroupInCurrency(SummaryGroup group, Currency currency) {
    }

    /** Asset type keys in summary order. */
    private enum SummaryAssetClass {
        STOCK("Stocks"),
        OPTION_CALL("Calls"),
        OPTION_PUT("Puts");

        private final String label;

        SummaryAssetClass(String label) {
            this.label = label;
        }

        static SummaryAssetClass of(Trade trade) {
            if (trade.getAssetType() == AssetType.STOCK) {
                return STOCK;
            }
            return switch (trade.getOptionType()) {
                case CALL -> OPTION_CALL;
                case PUT -> OPTION_PUT;
            };
        }

        SummaryGroup group() {
            return new SummaryGroup(name(), label, null);
        }
    }

    /** Tax treatment keys in summary order. Unassigned trades count as taxable. */
    private enum TaxTreatment {
        TAXABLE("Taxable accounts"),
        TAX_FREE("Tax-free accounts");

        private final String label;

        TaxTreatment(String label) {
            this.label = label;
        }

        static TaxTreatment of(Trade trade, ReportAccounts accounts) {
            return accounts.isTaxFree(trade.getAccountId()) ? TAX_FREE : TAXABLE;
        }

        SummaryGroup group() {
            return new SummaryGroup(name(), label, this == TAX_FREE);
        }
    }

    private static final class SummaryAccumulator {
        private int tradeCount;
        private int winningTrades;
        private int losingTrades;
        private BigDecimal gains = BigDecimal.ZERO;
        private BigDecimal losses = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;
        private BigDecimal marginInterest = BigDecimal.ZERO;
        private BigDecimal netRealizedPnl = BigDecimal.ZERO;

        void add(Trade trade) {
            BigDecimal realizedPnl = trade.getRealizedPnl();
            tradeCount++;
            if (realizedPnl.signum() > 0) {
                winningTrades++;
                gains = gains.add(realizedPnl);
            } else if (realizedPnl.signum() < 0) {
                losingTrades++;
                losses = losses.add(realizedPnl);
            }
            fees = fees.add(trade.getFees());
            marginInterest = marginInterest.add(TradeCalculations.marginInterest(trade));
            netRealizedPnl = netRealizedPnl.add(realizedPnl);
        }

        SummaryRow toRow(SummaryGroup group, Currency currency) {
            return new SummaryRow(
                    group.key(),
                    group.label(),
                    currency,
                    group.taxFree(),
                    tradeCount,
                    winningTrades,
                    losingTrades,
                    money(gains),
                    money(losses),
                    money(fees),
                    money(marginInterest),
                    money(netRealizedPnl)
            );
        }
    }
}
