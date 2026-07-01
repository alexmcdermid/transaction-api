package com.transactionapi.dto;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.constants.TradeHistoryAction;
import com.transactionapi.model.TradeHistory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TradeHistoryResponse(
        UUID id,
        UUID tradeId,
        TradeHistoryAction action,
        String userId,
        String symbol,
        AssetType assetType,
        Currency currency,
        TradeDirection direction,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal exitPrice,
        BigDecimal fees,
        BigDecimal marginRate,
        UUID accountId,
        OptionType optionType,
        BigDecimal strikePrice,
        LocalDate expiryDate,
        LocalDate openedAt,
        LocalDate closedAt,
        BigDecimal realizedPnl,
        String notes,
        Instant tradeCreatedAt,
        Instant tradeUpdatedAt,
        Instant actionAt
) {
    public static TradeHistoryResponse from(TradeHistory history) {
        return new TradeHistoryResponse(
                history.getId(),
                history.getTradeId(),
                history.getAction(),
                history.getUserId(),
                history.getSymbol(),
                history.getAssetType(),
                history.getCurrency(),
                history.getDirection(),
                history.getQuantity(),
                history.getEntryPrice(),
                history.getExitPrice(),
                history.getFees(),
                history.getMarginRate(),
                history.getAccountId(),
                history.getOptionType(),
                history.getStrikePrice(),
                history.getExpiryDate(),
                history.getOpenedAt(),
                history.getClosedAt(),
                history.getRealizedPnl(),
                history.getNotes(),
                history.getTradeCreatedAt(),
                history.getTradeUpdatedAt(),
                history.getActionAt()
        );
    }
}
