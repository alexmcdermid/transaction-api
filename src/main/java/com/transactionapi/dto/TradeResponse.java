package com.transactionapi.dto;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TradeDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TradeResponse(
        UUID id,
        String symbol,
        AssetType assetType,
        Currency currency,
        TradeDirection direction,
        int quantity,
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
        BigDecimal pnlPercent,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
