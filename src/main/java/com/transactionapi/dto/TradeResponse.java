package com.transactionapi.dto;

import com.transactionapi.constants.AssetType;
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
        TradeDirection direction,
        int quantity,
        BigDecimal entryPrice,
        BigDecimal exitPrice,
        BigDecimal fees,
        OptionType optionType,
        BigDecimal strikePrice,
        LocalDate expiryDate,
        LocalDate openedAt,
        LocalDate closedAt,
        BigDecimal realizedPnl,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
