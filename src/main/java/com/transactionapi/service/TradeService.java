package com.transactionapi.service;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.TradeDirection;
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
        int boundedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), boundedSize);
        Page<Trade> result;
        if (month != null) {
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
        BigDecimal total = sumPnl(trades);

        Map<LocalDate, List<Trade>> byDay = trades.stream()
                .collect(Collectors.groupingBy(Trade::getClosedAt));
        List<PnlBucketResponse> daily = byDay.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<Trade>>comparingByKey().reversed())
                .map(entry -> new PnlBucketResponse(entry.getKey().toString(), sumPnl(entry.getValue()), entry.getValue().size()))
                .toList();

        Map<YearMonth, List<Trade>> byMonth = trades.stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getClosedAt())));
        List<PnlBucketResponse> monthly = byMonth.entrySet().stream()
                .sorted(Map.Entry.<YearMonth, List<Trade>>comparingByKey().reversed())
                .map(entry -> new PnlBucketResponse(entry.getKey().toString(), sumPnl(entry.getValue()), entry.getValue().size()))
                .toList();

        return new PnlSummaryResponse(
                total,
                trades.size(),
                daily,
                monthly,
                exchangeRateService.cadToUsd(),
                exchangeRateService.lastUpdatedOn()
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
        return gross.subtract(fees).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumPnl(List<Trade> trades) {
        return trades.stream()
                .map(this::toUsd)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toUsd(Trade trade) {
        Currency currency = trade.getCurrency() == null ? Currency.USD : trade.getCurrency();
        BigDecimal rate = currency == Currency.CAD ? exchangeRateService.cadToUsd() : BigDecimal.ONE;
        return trade.getRealizedPnl().multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private TradeResponse toResponse(Trade trade) {
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
                trade.getOptionType(),
                trade.getStrikePrice(),
                trade.getExpiryDate(),
                trade.getOpenedAt(),
                trade.getClosedAt(),
                trade.getRealizedPnl(),
                trade.getNotes(),
                trade.getCreatedAt(),
                trade.getUpdatedAt()
        );
    }
}
