package com.transactionapi.service;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.AggregateStatsResponse;
import com.transactionapi.dto.PnlBucketResponse;
import com.transactionapi.dto.PagedResponse;
import com.transactionapi.dto.PnlSummaryResponse;
import com.transactionapi.dto.TradeRequest;
import com.transactionapi.dto.TradeResponse;
import com.transactionapi.model.Trade;
import com.transactionapi.repository.TradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TradeService {

    private static final BigDecimal OPTION_MULTIPLIER = BigDecimal.valueOf(100);
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final TradeRepository tradeRepository;
    private final ExchangeRateService exchangeRateService;

    public TradeService(TradeRepository tradeRepository, ExchangeRateService exchangeRateService) {
        this.tradeRepository = tradeRepository;
        this.exchangeRateService = exchangeRateService;
    }

    public TradeResponse createTrade(TradeRequest request, String userId) {
        Trade trade = new Trade();
        trade.setUserId(userId);
        applyRequest(trade, request);
        return toResponse(tradeRepository.save(trade));
    }

    public TradeResponse updateTrade(@NonNull UUID tradeId, TradeRequest request, String userId) {
        Trade trade = tradeRepository.findByIdAndUserId(Objects.requireNonNull(tradeId), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade not found"));
        applyRequest(trade, request);
        return toResponse(tradeRepository.save(trade));
    }

    public PagedResponse<TradeResponse> listTrades(String userId, int page, int size) {
        return listTrades(userId, page, size, null);
    }

    public PagedResponse<TradeResponse> listTrades(String userId, int page, int size, YearMonth month) {
        return listTrades(userId, page, size, month, null);
    }

    public PagedResponse<TradeResponse> listTrades(
            String userId,
            int page,
            int size,
            YearMonth month,
            LocalDate day
    ) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), boundedSize);
        Page<Trade> result;
        if (day != null) {
            result = tradeRepository.findByUserIdAndClosedAtOrderByClosedAtDesc(userId, day, pageable);
        } else if (month != null) {
            LocalDate start = month.atDay(1);
            LocalDate end = month.atEndOfMonth();
            result = tradeRepository.findByUserIdAndClosedAtBetweenOrderByClosedAtDesc(userId, start, end, pageable);
        } else {
            result = tradeRepository.findByUserIdOrderByClosedAtDesc(userId, pageable);
        }
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

    public void deleteTrade(@NonNull UUID tradeId, String userId) {
        Trade trade = tradeRepository.findByIdAndUserId(Objects.requireNonNull(tradeId), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade not found"));
        tradeRepository.delete(trade);
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
                    return new PnlBucketResponse(entry.getKey().toString(), dayPnl, entry.getValue().size(), dayPercent);
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
                    return new PnlBucketResponse(entry.getKey().toString(), monthPnl, entry.getValue().size(), monthPercent);
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
                        null
                );
            }
        }

        return new AggregateStatsResponse(
                totalPnl,
                tradeCount,
                pnlPercent,
                bestDay,
                bestMonth,
                cadToUsdRate,
                exchangeRateService.lastUpdatedOn(),
                null,
                null
        );
    }

    /**
     * Scoped aggregate stats for a year, with best day scoped to a month.
     * If month is omitted, best day is computed from the best month in the year.
     */
    public AggregateStatsResponse getScopedAggregateStats(String userId, Integer year, YearMonth month) {
        int scopedYear = resolveScopedYear(userId, year, month);
        YearMonth yearStart = YearMonth.of(scopedYear, 1);
        LocalDate startDate = yearStart.atDay(1);
        LocalDate endDate = startDate.plusYears(1);

        BigDecimal cadToUsdRate = exchangeRateService.cadToUsd();
        int tradeCount = tradeRepository.countByUserIdAndDateRange(userId, startDate, endDate);

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
                : bestMonth != null
                        ? YearMonth.parse(bestMonth.period())
                        : null;

        PnlBucketResponse bestDay = null;
        if (scopedMonth != null) {
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
                pnlPercent,
                bestDay,
                bestMonth,
                cadToUsdRate,
                exchangeRateService.lastUpdatedOn(),
                scopedYear,
                scopedMonth != null ? scopedMonth.toString() : null
        );
    }

    private int resolveScopedYear(String userId, Integer year, YearMonth month) {
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
                null
        );
    }

    private void applyRequest(Trade trade, TradeRequest request) {
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

    private BigDecimal toUsd(Trade trade, BigDecimal cadToUsdRate) {
        Currency currency = trade.getCurrency() == null ? Currency.USD : trade.getCurrency();
        BigDecimal rate = currency == Currency.CAD ? cadToUsdRate : BigDecimal.ONE;
        return trade.getRealizedPnl().multiply(rate).setScale(2, RoundingMode.HALF_UP);
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
                trade.getOptionType(),
                trade.getStrikePrice(),
                trade.getExpiryDate(),
                trade.getOpenedAt(),
                trade.getClosedAt(),
                trade.getRealizedPnl(),
                pnlPercent,
                trade.getNotes(),
                trade.getCreatedAt(),
                trade.getUpdatedAt()
        );
    }
}
