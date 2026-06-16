package com.transactionapi.service;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.constants.TradeHistoryAction;
import com.transactionapi.constants.TradeSortDirection;
import com.transactionapi.constants.TradeSortField;
import com.transactionapi.dto.AccountStatsResponse;
import com.transactionapi.dto.AggregateStatsResponse;
import com.transactionapi.dto.InferredAccountTradeCountsResponse;
import com.transactionapi.dto.PnlBucketResponse;
import com.transactionapi.dto.PagedResponse;
import com.transactionapi.dto.PnlSummaryResponse;
import com.transactionapi.dto.TradeCountStatsResponse;
import com.transactionapi.dto.TradeHistoryResponse;
import com.transactionapi.dto.TradeRequest;
import com.transactionapi.dto.TradeResponse;
import com.transactionapi.model.Account;
import com.transactionapi.model.Trade;
import com.transactionapi.model.TradeHistory;
import com.transactionapi.repository.AccountRepository;
import com.transactionapi.repository.TradeHistoryRepository;
import com.transactionapi.repository.TradeRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TradeService {

    private static final BigDecimal OPTION_MULTIPLIER = BigDecimal.valueOf(100);
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final TradeRepository tradeRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final AccountRepository accountRepository;
    private final ExchangeRateService exchangeRateService;

    public TradeService(
            TradeRepository tradeRepository,
            TradeHistoryRepository tradeHistoryRepository,
            AccountRepository accountRepository,
            ExchangeRateService exchangeRateService
    ) {
        this.tradeRepository = tradeRepository;
        this.tradeHistoryRepository = tradeHistoryRepository;
        this.accountRepository = accountRepository;
        this.exchangeRateService = exchangeRateService;
    }

    public TradeResponse createTrade(TradeRequest request, String userId) {
        Trade trade = new Trade();
        trade.setUserId(userId);
        applyRequest(trade, request, userId);
        Trade saved = tradeRepository.saveAndFlush(trade);
        recordHistory(saved, TradeHistoryAction.CREATE);
        return toResponse(saved);
    }

    public TradeResponse updateTrade(@NonNull UUID tradeId, TradeRequest request, String userId) {
        Trade trade = tradeRepository.findByIdAndUserId(Objects.requireNonNull(tradeId), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade not found"));
        applyRequest(trade, request, userId);
        Trade saved = tradeRepository.saveAndFlush(trade);
        recordHistory(saved, TradeHistoryAction.EDIT);
        return toResponse(saved);
    }

    public PagedResponse<TradeResponse> listTrades(String userId, int page, int size) {
        return listTrades(userId, page, size, null, null, null, null);
    }

    public PagedResponse<TradeResponse> listTrades(String userId, int page, int size, YearMonth month) {
        return listTrades(userId, page, size, month, null, null, null);
    }

    public PagedResponse<TradeResponse> listTrades(
            String userId,
            int page,
            int size,
            YearMonth month,
            LocalDate day
    ) {
        return listTrades(userId, page, size, month, day, null, null);
    }

    public PagedResponse<TradeResponse> listTrades(
            String userId,
            int page,
            int size,
            YearMonth month,
            LocalDate day,
            TradeSortField sortBy,
            TradeSortDirection sortDirection
    ) {
        return listTrades(userId, page, size, month, day, sortBy, sortDirection, List.of(), false, null);
    }

    public PagedResponse<TradeResponse> listTrades(
            String userId,
            int page,
            int size,
            YearMonth month,
            LocalDate day,
            TradeSortField sortBy,
            TradeSortDirection sortDirection,
            List<UUID> accountIds,
            boolean includeUnassignedAccounts,
            String symbolQuery
    ) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        Sort sort = buildSort(sortBy, sortDirection);
        Pageable pageable = PageRequest.of(Math.max(page, 0), boundedSize, sort);
        Page<Trade> result = tradeRepository.findAll(
                buildTradeFilterSpecification(userId, month, day, accountIds, includeUnassignedAccounts, symbolQuery),
                pageable
        );
        List<TradeResponse> items = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PagedResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.hasPrevious()
        );
    }

    private Specification<Trade> buildTradeFilterSpecification(
            String userId,
            YearMonth month,
            LocalDate day,
            List<UUID> accountIds,
            boolean includeUnassignedAccounts,
            String symbolQuery
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));

            if (day != null) {
                predicates.add(criteriaBuilder.equal(root.get("closedAt"), day));
            } else if (month != null) {
                predicates.add(criteriaBuilder.between(
                        root.get("closedAt"),
                        month.atDay(1),
                        month.atEndOfMonth()
                ));
            }

            List<UUID> selectedAccountIds = accountIds == null ? List.of() : accountIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!selectedAccountIds.isEmpty() || includeUnassignedAccounts) {
                List<Predicate> accountPredicates = new ArrayList<>();
                if (!selectedAccountIds.isEmpty()) {
                    accountPredicates.add(root.get("accountId").in(selectedAccountIds));
                }
                if (includeUnassignedAccounts) {
                    accountPredicates.add(criteriaBuilder.isNull(root.get("accountId")));
                }
                predicates.add(criteriaBuilder.or(accountPredicates.toArray(Predicate[]::new)));
            }

            if (StringUtils.hasText(symbolQuery)) {
                String normalizedSymbol = escapeLike(symbolQuery.trim().toUpperCase(Locale.ROOT));
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.upper(root.get("symbol")),
                        "%" + normalizedSymbol + "%",
                        '\\'
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private Sort buildSort(TradeSortField requestedField, TradeSortDirection requestedDirection) {
        TradeSortField sortField = requestedField != null ? requestedField : TradeSortField.defaultValue();
        TradeSortDirection sortDirection = requestedDirection != null
                ? requestedDirection
                : TradeSortDirection.defaultValue();
        Sort.Direction primaryDirection = sortDirection.toSpringDirection();
        Sort sort = Sort.by(primaryDirection, sortField.propertyName());
        if (sortField == TradeSortField.CLOSED_AT) {
            return sort.and(Sort.by(primaryDirection, TradeSortField.CREATED_AT.propertyName()));
        }
        if (sortField == TradeSortField.CREATED_AT) {
            return sort.and(Sort.by(Sort.Direction.DESC, TradeSortField.CLOSED_AT.propertyName()));
        }
        return sort
                .and(Sort.by(Sort.Direction.DESC, TradeSortField.CLOSED_AT.propertyName()))
                .and(Sort.by(Sort.Direction.DESC, TradeSortField.CREATED_AT.propertyName()));
    }

    public void deleteTrade(@NonNull UUID tradeId, String userId) {
        Trade trade = tradeRepository.findByIdAndUserId(Objects.requireNonNull(tradeId), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade not found"));
        recordHistory(trade, TradeHistoryAction.DELETE);
        tradeRepository.delete(trade);
    }

    public List<TradeHistoryResponse> listTradeHistory(@NonNull UUID tradeId, String userId) {
        return tradeHistoryRepository.findByTradeIdAndUserIdOrderByActionAtAsc(
                        Objects.requireNonNull(tradeId),
                        userId
                ).stream()
                .map(TradeHistoryResponse::from)
                .toList();
    }

    public List<TradeHistoryResponse> listTradeHistoryForUser(String userId) {
        return tradeHistoryRepository.findByUserIdOrderByActionAtDesc(userId).stream()
                .map(TradeHistoryResponse::from)
                .toList();
    }

    public PnlSummaryResponse summarize(String userId, YearMonth month) {
        List<Trade> trades;
        if (month != null) {
            LocalDate start = month.atDay(1);
            LocalDate end = month.atEndOfMonth();
            trades = tradeRepository.findByUserIdAndClosedAtBetweenOrderByClosedAtDesc(userId, start, end);
        } else {
            trades = tradeRepository.findAllForUser(userId);
        }
        BigDecimal cadToUsdRate = exchangeRateService.cadToUsd();
        BigDecimal total = sumPnl(trades, cadToUsdRate);
        BigDecimal totalNotional = sumNotional(trades, cadToUsdRate);
        BigDecimal pnlPercent = computePnlPercent(total, totalNotional);

        Map<LocalDate, List<Trade>> byDay = trades.stream()
                .collect(Collectors.groupingBy(Trade::getClosedAt));
        List<PnlBucketResponse> daily = byDay.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<Trade>>comparingByKey().reversed())
                .map(entry -> {
                    BigDecimal dayPnl = sumPnl(entry.getValue(), cadToUsdRate);
                    BigDecimal dayNotional = sumNotional(entry.getValue(), cadToUsdRate);
                    BigDecimal dayPercent = computePnlPercent(dayPnl, dayNotional);
                    BigDecimal dayMarginFee = sumMarginFees(entry.getValue(), cadToUsdRate);
                    return new PnlBucketResponse(
                            entry.getKey().toString(),
                            dayPnl,
                            entry.getValue().size(),
                            dayPercent,
                            dayMarginFee
                    );
                })
                .toList();

        Map<YearMonth, List<Trade>> byMonth = trades.stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getClosedAt())));
        List<PnlBucketResponse> monthly = byMonth.entrySet().stream()
                .sorted(Map.Entry.<YearMonth, List<Trade>>comparingByKey().reversed())
                .map(entry -> {
                    BigDecimal monthPnl = sumPnl(entry.getValue(), cadToUsdRate);
                    BigDecimal monthNotional = sumNotional(entry.getValue(), cadToUsdRate);
                    BigDecimal monthPercent = computePnlPercent(monthPnl, monthNotional);
                    BigDecimal monthMarginFee = sumMarginFees(entry.getValue(), cadToUsdRate);
                    return new PnlBucketResponse(
                            entry.getKey().toString(),
                            monthPnl,
                            entry.getValue().size(),
                            monthPercent,
                            monthMarginFee
                    );
                })
                .toList();

        return new PnlSummaryResponse(
                total,
                trades.size(),
                pnlPercent,
                daily,
                monthly,
                cadToUsdRate,
                exchangeRateService.lastUpdatedOn()
        );
    }

    /**
     * Get aggregated statistics efficiently using database queries.
     * This is optimized for performance and doesn't load all trades into memory.
     * All values are converted to USD using the current exchange rate.
     */
    public AggregateStatsResponse getAggregateStats(String userId) {
        BigDecimal cadToUsdRate = exchangeRateService.cadToUsd();
        
        // Use database aggregation instead of loading all trades
        int tradeCount = tradeRepository.countByUserId(userId);
        int tradedDays = tradeRepository.countTradedDaysByUserId(userId);
        BigDecimal totalPnl = tradeRepository.sumPnlByUserId(userId, cadToUsdRate);
        if (totalPnl == null) {
            totalPnl = BigDecimal.ZERO;
        }
        totalPnl = totalPnl.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalNotional = tradeRepository.sumNotionalByUserId(userId, cadToUsdRate);
        BigDecimal pnlPercent = computePnlPercent(totalPnl, totalNotional);

        // Get best day using database query (returns only top result)
        TradeRepository.DailyAggregateProjection bestDayProj = tradeRepository.findBestDayByUserId(userId, cadToUsdRate);
        PnlBucketResponse bestDay = null;
        if (bestDayProj != null && bestDayProj.getPeriod() != null) {
            BigDecimal pnl = bestDayProj.getPnl();
            if (pnl != null) {
                bestDay = new PnlBucketResponse(
                        bestDayProj.getPeriod().toString(),
                        pnl.setScale(2, RoundingMode.HALF_UP),
                        bestDayProj.getTrades(),
                        null,
                        null
                );
            }
        }

        // Get best month using database query (returns only top result)
        TradeRepository.MonthlyAggregateProjection monthProj = tradeRepository.findBestMonthByUserId(userId, cadToUsdRate);
        PnlBucketResponse bestMonth = null;
        if (monthProj != null && monthProj.getPeriod() != null) {
            BigDecimal pnl = monthProj.getPnl();
            if (pnl != null) {
                bestMonth = new PnlBucketResponse(
                        monthProj.getPeriod(),
                        pnl.setScale(2, RoundingMode.HALF_UP),
                        monthProj.getTrades(),
                        null,
                        null
                );
            }
        }

        return new AggregateStatsResponse(
                totalPnl,
                tradeCount,
                tradedDays,
                pnlPercent,
                bestDay,
                bestMonth,
                cadToUsdRate,
                exchangeRateService.lastUpdatedOn(),
                null,
                null,
                null
        );
    }

    /**
     * Scoped aggregate stats for a year.
     * When month is provided, best day is computed for that month.
     * When day is provided, best day is that exact day.
     */
    public AggregateStatsResponse getScopedAggregateStats(String userId, Integer year, YearMonth month, LocalDate day) {
        int scopedYear = resolveScopedYear(userId, year, month, day);
        YearMonth yearStart = YearMonth.of(scopedYear, 1);
        LocalDate startDate = yearStart.atDay(1);
        LocalDate endDate = startDate.plusYears(1);

        BigDecimal cadToUsdRate = exchangeRateService.cadToUsd();
        int tradeCount = tradeRepository.countByUserIdAndDateRange(userId, startDate, endDate);
        int tradedDays = tradeRepository.countTradedDaysByUserIdAndDateRange(userId, startDate, endDate);

        BigDecimal totalPnl = tradeRepository.sumPnlByUserIdAndDateRange(userId, cadToUsdRate, startDate, endDate);
        if (totalPnl == null) {
            totalPnl = BigDecimal.ZERO;
        }
        totalPnl = totalPnl.setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalNotional = tradeRepository.sumNotionalByUserIdAndDateRange(
                userId,
                cadToUsdRate,
                startDate,
                endDate
        );
        BigDecimal pnlPercent = computePnlPercent(totalPnl, totalNotional);

        TradeRepository.MonthlyAggregateProjection monthProjection = tradeRepository.findBestMonthByUserIdAndDateRange(
                userId,
                cadToUsdRate,
                startDate,
                endDate
        );
        PnlBucketResponse bestMonth = toBestMonthBucket(monthProjection);

        YearMonth scopedMonth = month != null
                ? month
                : day != null
                        ? YearMonth.from(day)
                        : bestMonth != null
                                ? YearMonth.parse(bestMonth.period())
                                : null;

        PnlBucketResponse bestDay = null;
        if (day != null) {
            TradeRepository.DailyAggregateProjection dayProjection = tradeRepository.findDayByUserIdAndDate(
                    userId,
                    cadToUsdRate,
                    day
            );
            bestDay = toBestDayBucket(dayProjection);
        } else if (scopedMonth != null) {
            LocalDate monthStart = scopedMonth.atDay(1);
            LocalDate monthEnd = monthStart.plusMonths(1);
            TradeRepository.DailyAggregateProjection dayProjection = tradeRepository.findBestDayByUserIdAndDateRange(
                    userId,
                    cadToUsdRate,
                    monthStart,
                    monthEnd
            );
            bestDay = toBestDayBucket(dayProjection);
        }

        return new AggregateStatsResponse(
                totalPnl,
                tradeCount,
                tradedDays,
                pnlPercent,
                bestDay,
                bestMonth,
                cadToUsdRate,
                exchangeRateService.lastUpdatedOn(),
                scopedYear,
                scopedMonth != null ? scopedMonth.toString() : null,
                day != null ? day.toString() : null
        );
    }

    public List<AccountStatsResponse> getAccountStats(String userId, Integer year) {
        int scopedYear = year != null ? year : resolveScopedYear(userId, null, null, null);
        LocalDate startDate = LocalDate.of(scopedYear, 1, 1);
        LocalDate endDate = startDate.plusYears(1).minusDays(1);
        BigDecimal cadToUsdRate = exchangeRateService.cadToUsd();
        Map<UUID, String> accountNames = accountRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
        List<Trade> trades = tradeRepository.findByUserIdAndClosedAtBetweenOrderByClosedAtDesc(
                userId,
                startDate,
                endDate
        );

        Map<UUID, List<Trade>> tradesByAccount = new HashMap<>();
        trades.forEach(trade -> tradesByAccount.computeIfAbsent(trade.getAccountId(), ignored -> new ArrayList<>()).add(trade));
        return tradesByAccount.entrySet()
                .stream()
                .map(entry -> buildAccountStats(entry.getKey(), accountNames, entry.getValue(), cadToUsdRate, scopedYear))
                .sorted(Comparator.comparing(AccountStatsResponse::totalPnl).reversed())
                .toList();
    }

    private AccountStatsResponse buildAccountStats(
            UUID accountId,
            Map<UUID, String> accountNames,
            List<Trade> trades,
            BigDecimal cadToUsdRate,
            int year
    ) {
        BigDecimal totalPnl = sumPnl(trades, cadToUsdRate);
        BigDecimal totalNotional = sumNotional(trades, cadToUsdRate);
        BigDecimal pnlPercent = computePnlPercent(totalPnl, totalNotional);
        int activeMonths = (int) trades.stream()
                .map(trade -> YearMonth.from(trade.getClosedAt()))
                .distinct()
                .count();
        int tradedDays = (int) trades.stream()
                .map(Trade::getClosedAt)
                .distinct()
                .count();
        int tradeCount = trades.size();
        return new AccountStatsResponse(
                accountId,
                accountId != null ? accountNames.getOrDefault(accountId, "Deleted account") : "Unassigned",
                totalPnl,
                average(totalPnl, activeMonths),
                average(totalPnl, tradedDays),
                average(totalPnl, tradeCount),
                totalNotional,
                pnlPercent,
                tradeCount,
                tradedDays,
                activeMonths,
                year
        );
    }

    public TradeCountStatsResponse getTradeCountStats(
            String userId,
            Integer year,
            YearMonth month,
            LocalDate day,
            UUID accountId,
            boolean unassigned
    ) {
        int scopedYear = resolveScopedYear(userId, year, month, day);
        YearMonth scopedMonth = month != null
                ? month
                : day != null
                        ? YearMonth.from(day)
                        : YearMonth.of(scopedYear, LocalDate.now().getMonth());
        LocalDate scopedDay = day != null ? day : LocalDate.now();
        LocalDate yearStart = LocalDate.of(scopedYear, 1, 1);
        LocalDate yearEnd = yearStart.plusYears(1);
        List<Trade> scopedTrades = tradeRepository.findByUserIdAndClosedAtBetweenOrderByClosedAtDesc(
                userId,
                yearStart,
                yearEnd.minusDays(1)
        ).stream()
                .filter(trade -> accountMatches(trade.getAccountId(), accountId, unassigned))
                .toList();

        int yearTradeCount = scopedTrades.size();
        int monthTradeCount = (int) scopedTrades.stream()
                .filter(trade -> YearMonth.from(trade.getClosedAt()).equals(scopedMonth))
                .count();
        int dayTradeCount = (int) scopedTrades.stream()
                .filter(trade -> trade.getClosedAt().equals(scopedDay))
                .count();
        int yearTradedDays = (int) scopedTrades.stream()
                .map(Trade::getClosedAt)
                .distinct()
                .count();
        int tradingDays = countWeekdays(yearStart, LocalDate.now().getYear() == scopedYear ? LocalDate.now() : yearEnd.minusDays(1));

        return new TradeCountStatsResponse(
                unassigned ? null : accountId,
                resolveAccountName(userId, accountId, unassigned),
                yearTradeCount,
                monthTradeCount,
                dayTradeCount,
                yearTradedDays,
                average(BigDecimal.valueOf(yearTradeCount), yearTradedDays),
                average(BigDecimal.valueOf(yearTradeCount), tradingDays),
                scopedYear,
                scopedMonth.toString(),
                scopedDay
        );
    }

    private boolean accountMatches(UUID tradeAccountId, UUID accountId, boolean unassigned) {
        if (unassigned) {
            return tradeAccountId == null;
        }
        return accountId == null || Objects.equals(tradeAccountId, accountId);
    }

    private String resolveAccountName(String userId, UUID accountId, boolean unassigned) {
        if (unassigned) {
            return "Unassigned";
        }
        if (accountId == null) {
            return "All accounts";
        }
        return accountRepository.findByIdAndUserId(accountId, userId)
                .map(Account::getName)
                .orElse("Deleted account");
    }

    public List<InferredAccountTradeCountsResponse> getInferredAccountTradeCounts(
            String userId,
            Integer year,
            YearMonth month,
            LocalDate day
    ) {
        int scopedYear = year != null ? year : resolveScopedYear(userId, null, month, day);
        YearMonth scopedMonth = month != null
                ? month
                : YearMonth.of(scopedYear, LocalDate.now().getMonth());
        LocalDate scopedDay = day != null ? day : LocalDate.now();
        Map<UUID, String> accountNames = accountRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
        Map<UUID, List<TradeHistory>> byTrade = tradeHistoryRepository.findByUserIdOrderByActionAtDesc(userId).stream()
                .collect(Collectors.groupingBy(TradeHistory::getTradeId));

        Map<UUID, InferredAccountTradeCountsAccumulator> byAccount = new HashMap<>();
        for (List<TradeHistory> rawHistory : byTrade.values()) {
            List<TradeHistory> history = rawHistory.stream()
                    .sorted(Comparator.comparing(TradeHistory::getActionAt))
                    .toList();
            applyInferredTradeCounts(history, scopedYear, scopedMonth, scopedDay, accountNames, byAccount);
        }

        return byAccount.values().stream()
                .map(accumulator -> accumulator.toResponse(scopedYear, scopedMonth, scopedDay))
                .sorted(Comparator.comparing(InferredAccountTradeCountsResponse::inferredTotalCount).reversed())
                .toList();
    }

    private void applyInferredTradeCounts(
            List<TradeHistory> history,
            int year,
            YearMonth month,
            LocalDate day,
            Map<UUID, String> accountNames,
            Map<UUID, InferredAccountTradeCountsAccumulator> byAccount
    ) {
        TradeHistory created = history.stream()
                .filter(entry -> entry.getAction() == TradeHistoryAction.CREATE)
                .findFirst()
                .orElse(null);
        if (created == null || history.stream().anyMatch(entry -> entry.getAction() == TradeHistoryAction.DELETE)) {
            return;
        }

        TradeHistory latest = history.get(history.size() - 1);
        if (latest.getClosedAt() == null || latest.getClosedAt().getYear() != year) {
            return;
        }

        UUID accountId = latest.getAccountId();
        InferredAccountTradeCountsAccumulator accumulator = byAccount.computeIfAbsent(
                accountId,
                id -> new InferredAccountTradeCountsAccumulator(
                        id,
                        id != null ? accountNames.getOrDefault(id, "Deleted account") : "Unassigned"
                )
        );
        boolean inMonth = YearMonth.from(latest.getClosedAt()).equals(month);
        boolean onDay = latest.getClosedAt().equals(day);
        accumulator.recordClosedTrade(latest.getDirection(), inMonth, onDay);

        TradeHistory previous = created;
        for (TradeHistory current : history.stream().filter(entry -> entry.getAction() == TradeHistoryAction.EDIT).toList()) {
            if (!sameTradeIdentity(previous, current)) {
                previous = current;
                continue;
            }
            int previousQuantity = previous.getQuantity() != null ? previous.getQuantity() : 0;
            int currentQuantity = current.getQuantity() != null ? current.getQuantity() : 0;
            int quantityDelta = currentQuantity - previousQuantity;
            if (quantityDelta > 0) {
                accumulator.recordAdd(
                        current.getDirection(),
                        quantityDelta,
                        inferAddedEntryPrice(previous, current, quantityDelta),
                        inMonth,
                        onDay
                );
            }
            previous = current;
        }
    }

    private BigDecimal inferAddedEntryPrice(TradeHistory previous, TradeHistory current, int quantityDelta) {
        if (quantityDelta <= 0 || previous.getEntryPrice() == null || current.getEntryPrice() == null) {
            return null;
        }
        int previousQuantity = previous.getQuantity() != null ? previous.getQuantity() : 0;
        int currentQuantity = current.getQuantity() != null ? current.getQuantity() : 0;
        if (previousQuantity <= 0 || currentQuantity <= previousQuantity) {
            return null;
        }
        BigDecimal currentNotional = current.getEntryPrice().multiply(BigDecimal.valueOf(currentQuantity));
        BigDecimal previousNotional = previous.getEntryPrice().multiply(BigDecimal.valueOf(previousQuantity));
        return currentNotional.subtract(previousNotional)
                .divide(BigDecimal.valueOf(quantityDelta), 4, RoundingMode.HALF_UP);
    }

    private boolean sameTradeIdentity(TradeHistory initial, TradeHistory current) {
        return Objects.equals(initial.getSymbol(), current.getSymbol())
                && initial.getAssetType() == current.getAssetType()
                && initial.getCurrency() == current.getCurrency()
                && initial.getDirection() == current.getDirection()
                && Objects.equals(initial.getAccountId(), current.getAccountId())
                && Objects.equals(initial.getOpenedAt(), current.getOpenedAt())
                && Objects.equals(initial.getClosedAt(), current.getClosedAt())
                && Objects.equals(initial.getOptionType(), current.getOptionType())
                && equalBigDecimal(initial.getStrikePrice(), current.getStrikePrice())
                && Objects.equals(initial.getExpiryDate(), current.getExpiryDate());
    }

    private boolean equalBigDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private BigDecimal average(BigDecimal total, int divisor) {
        if (total == null || divisor <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return total.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private int countWeekdays(LocalDate start, LocalDate inclusiveEnd) {
        if (inclusiveEnd.isBefore(start)) {
            return 0;
        }
        int days = 0;
        for (LocalDate date = start; !date.isAfter(inclusiveEnd); date = date.plusDays(1)) {
            int dayOfWeek = date.getDayOfWeek().getValue();
            if (dayOfWeek <= 5) {
                days++;
            }
        }
        return days;
    }

    private static class InferredAccountTradeCountsAccumulator {
        private final UUID accountId;
        private final String accountName;
        private int recordedTradeCount;
        private int inferredBuyCount;
        private int inferredSellCount;
        private int monthInferredTotalCount;
        private int dayInferredTotalCount;
        private int inferredAddCount;
        private int monthInferredAddCount;
        private int dayInferredAddCount;
        private int inferredAddedQuantity;
        private int inferredPricedAddedQuantity;
        private BigDecimal inferredAddedNotional = BigDecimal.ZERO;

        InferredAccountTradeCountsAccumulator(UUID accountId, String accountName) {
            this.accountId = accountId;
            this.accountName = accountName;
        }

        void recordClosedTrade(TradeDirection direction, boolean inMonth, boolean onDay) {
            recordedTradeCount++;
            if (direction == TradeDirection.SHORT) {
                inferredSellCount++;
                inferredBuyCount++;
                recordScopedInferredCount(2, inMonth, onDay);
                return;
            }
            inferredBuyCount++;
            inferredSellCount++;
            recordScopedInferredCount(2, inMonth, onDay);
        }

        void recordAdd(TradeDirection direction, int quantityDelta, BigDecimal inferredPrice, boolean inMonth, boolean onDay) {
            inferredAddCount++;
            inferredAddedQuantity += quantityDelta;
            if (direction == TradeDirection.SHORT) {
                inferredSellCount++;
            } else {
                inferredBuyCount++;
            }
            recordScopedInferredCount(1, inMonth, onDay);
            if (inMonth) {
                monthInferredAddCount++;
            }
            if (onDay) {
                dayInferredAddCount++;
            }
            if (inferredPrice != null) {
                inferredPricedAddedQuantity += quantityDelta;
                inferredAddedNotional = inferredAddedNotional.add(inferredPrice.multiply(BigDecimal.valueOf(quantityDelta)));
            }
        }

        private void recordScopedInferredCount(int count, boolean inMonth, boolean onDay) {
            if (inMonth) {
                monthInferredTotalCount += count;
            }
            if (onDay) {
                dayInferredTotalCount += count;
            }
        }

        InferredAccountTradeCountsResponse toResponse(int year, YearMonth month, LocalDate day) {
            BigDecimal averageInferredAddPrice = inferredPricedAddedQuantity > 0
                    ? inferredAddedNotional.divide(BigDecimal.valueOf(inferredPricedAddedQuantity), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            return new InferredAccountTradeCountsResponse(
                    accountId,
                    accountName,
                    recordedTradeCount,
                    inferredBuyCount,
                    inferredSellCount,
                    inferredBuyCount + inferredSellCount,
                    monthInferredTotalCount,
                    dayInferredTotalCount,
                    inferredAddCount,
                    monthInferredAddCount,
                    dayInferredAddCount,
                    inferredAddedQuantity,
                    averageInferredAddPrice,
                    year,
                    month.toString(),
                    day
            );
        }
    }

    private int resolveScopedYear(String userId, Integer year, YearMonth month, LocalDate day) {
        if (day != null) {
            return day.getYear();
        }
        if (month != null) {
            return month.getYear();
        }
        if (year != null) {
            return year;
        }
        LocalDate latestClosedAt = tradeRepository.findLatestClosedAtByUserId(userId);
        if (latestClosedAt != null) {
            return latestClosedAt.getYear();
        }
        return LocalDate.now().getYear();
    }

    private PnlBucketResponse toBestDayBucket(TradeRepository.DailyAggregateProjection projection) {
        if (projection == null || projection.getPeriod() == null || projection.getPnl() == null) {
            return null;
        }
        return new PnlBucketResponse(
                projection.getPeriod().toString(),
                projection.getPnl().setScale(2, RoundingMode.HALF_UP),
                projection.getTrades(),
                null,
                null
        );
    }

    private PnlBucketResponse toBestMonthBucket(TradeRepository.MonthlyAggregateProjection projection) {
        if (projection == null || projection.getPeriod() == null || projection.getPnl() == null) {
            return null;
        }
        return new PnlBucketResponse(
                projection.getPeriod(),
                projection.getPnl().setScale(2, RoundingMode.HALF_UP),
                projection.getTrades(),
                null,
                null
        );
    }

    private void applyRequest(Trade trade, TradeRequest request, String userId) {
        if (request.assetType() == AssetType.OPTION) {
            if (request.optionType() == null || request.strikePrice() == null || request.expiryDate() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Options require type, strike, and expiry");
            }
        }
        if (request.openedAt().isAfter(request.closedAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Close date cannot be before open date");
        }

        trade.setSymbol(request.symbol().trim().toUpperCase());
        trade.setAssetType(request.assetType());
        trade.setCurrency(request.currency() != null ? request.currency() : Currency.USD);
        trade.setDirection(request.direction());
        trade.setQuantity(request.quantity());
        trade.setEntryPrice(request.entryPrice());
        trade.setExitPrice(request.exitPrice());
        trade.setFees(request.fees() != null ? request.fees() : BigDecimal.ZERO);
        trade.setMarginRate(request.marginRate() != null ? request.marginRate() : BigDecimal.ZERO);
        if (request.accountId() != null) {
            accountRepository.findByIdAndUserId(request.accountId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account not found"));
            trade.setAccountId(request.accountId());
        } else {
            trade.setAccountId(null);
        }
        if (request.assetType() == AssetType.OPTION) {
            trade.setOptionType(request.optionType());
            trade.setStrikePrice(request.strikePrice());
            trade.setExpiryDate(request.expiryDate());
        } else {
            trade.setOptionType(null);
            trade.setStrikePrice(null);
            trade.setExpiryDate(null);
        }
        trade.setOpenedAt(request.openedAt());
        trade.setClosedAt(request.closedAt());
        trade.setNotes(request.notes());
        trade.setRealizedPnl(calculatePnl(trade));
    }

    private void recordHistory(Trade trade, TradeHistoryAction action) {
        tradeHistoryRepository.save(TradeHistory.fromTrade(trade, action));
    }

    private BigDecimal calculatePnl(Trade trade) {
        BigDecimal movement = trade.getExitPrice().subtract(trade.getEntryPrice());
        if (trade.getDirection() == TradeDirection.SHORT) {
            movement = movement.negate();
        }
        BigDecimal multiplier = trade.getAssetType() == AssetType.OPTION ? OPTION_MULTIPLIER : BigDecimal.ONE;
        BigDecimal gross = movement.multiply(BigDecimal.valueOf(trade.getQuantity())).multiply(multiplier);
        BigDecimal fees = trade.getFees() != null ? trade.getFees() : BigDecimal.ZERO;
        BigDecimal marginFee = calculateMarginFee(trade);
        return gross.subtract(fees).subtract(marginFee).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMarginFee(Trade trade) {
        BigDecimal marginRate = trade.getMarginRate();
        if (marginRate == null || marginRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (trade.getOpenedAt() == null || trade.getClosedAt() == null) {
            return BigDecimal.ZERO;
        }
        long daysHeld = ChronoUnit.DAYS.between(trade.getOpenedAt(), trade.getClosedAt());
        if (daysHeld <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal notional = toTradeNotional(trade);
        if (notional == null || notional.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal yearFraction = BigDecimal.valueOf(daysHeld)
                .divide(DAYS_IN_YEAR, 10, RoundingMode.HALF_UP);
        BigDecimal rate = marginRate.divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP);
        return notional.multiply(rate).multiply(yearFraction).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumPnl(List<Trade> trades, BigDecimal cadToUsdRate) {
        return trades.stream()
                .map(trade -> toUsd(trade, cadToUsdRate))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumNotional(List<Trade> trades, BigDecimal cadToUsdRate) {
        return trades.stream()
                .map(trade -> toUsdNotional(trade, cadToUsdRate))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumMarginFees(List<Trade> trades, BigDecimal cadToUsdRate) {
        return trades.stream()
                .map(trade -> toUsdMarginFee(trade, cadToUsdRate))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toUsd(Trade trade, BigDecimal cadToUsdRate) {
        Currency currency = trade.getCurrency() == null ? Currency.USD : trade.getCurrency();
        BigDecimal rate = currency == Currency.CAD ? cadToUsdRate : BigDecimal.ONE;
        return trade.getRealizedPnl().multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toUsdMarginFee(Trade trade, BigDecimal cadToUsdRate) {
        BigDecimal marginFee = calculateMarginFee(trade);
        Currency currency = trade.getCurrency() == null ? Currency.USD : trade.getCurrency();
        BigDecimal rate = currency == Currency.CAD ? cadToUsdRate : BigDecimal.ONE;
        return marginFee.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toUsdNotional(Trade trade, BigDecimal cadToUsdRate) {
        if (trade.getEntryPrice() == null || trade.getQuantity() == null) {
            return null;
        }
        BigDecimal multiplier = trade.getAssetType() == AssetType.OPTION ? OPTION_MULTIPLIER : BigDecimal.ONE;
        BigDecimal notional = trade.getEntryPrice()
                .multiply(BigDecimal.valueOf(trade.getQuantity()))
                .multiply(multiplier)
                .abs();
        if (trade.getCurrency() == Currency.CAD) {
            return notional.multiply(cadToUsdRate);
        }
        return notional;
    }

    private BigDecimal toTradeNotional(Trade trade) {
        if (trade.getEntryPrice() == null || trade.getQuantity() == null) {
            return null;
        }
        BigDecimal multiplier = trade.getAssetType() == AssetType.OPTION ? OPTION_MULTIPLIER : BigDecimal.ONE;
        return trade.getEntryPrice()
                .multiply(BigDecimal.valueOf(trade.getQuantity()))
                .multiply(multiplier)
                .abs();
    }

    private BigDecimal computePnlPercent(BigDecimal totalPnl, BigDecimal totalNotional) {
        if (totalPnl == null || totalNotional == null) {
            return null;
        }
        if (totalNotional.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return totalPnl
                .multiply(BigDecimal.valueOf(100))
                .divide(totalNotional, 2, RoundingMode.HALF_UP);
    }

    private TradeResponse toResponse(Trade trade) {
        BigDecimal pnlPercent = computePnlPercent(trade.getRealizedPnl(), toTradeNotional(trade));
        return new TradeResponse(
                trade.getId(),
                trade.getSymbol(),
                trade.getAssetType(),
                trade.getCurrency(),
                trade.getDirection(),
                trade.getQuantity(),
                trade.getEntryPrice(),
                trade.getExitPrice(),
                trade.getFees(),
                trade.getMarginRate(),
                trade.getAccountId(),
                trade.getOptionType(),
                trade.getStrikePrice(),
                trade.getExpiryDate(),
                trade.getOpenedAt(),
                trade.getClosedAt(),
                trade.getRealizedPnl(),
                pnlPercent,
                trade.getNotes(),
                trade.getCreatedAt(),
                trade.getUpdatedAt(),
                trade.getCreatedAt(),
                trade.getUpdatedAt()
        );
    }
}
