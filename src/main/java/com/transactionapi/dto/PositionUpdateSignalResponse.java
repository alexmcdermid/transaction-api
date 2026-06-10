package com.transactionapi.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PositionUpdateSignalResponse(
        UUID tradeId,
        String symbol,
        UUID accountId,
        String accountName,
        LocalDate closedAt,
        int editCount,
        int initialQuantity,
        int latestQuantity,
        int quantityDelta,
        BigDecimal initialEntryPrice,
        BigDecimal latestEntryPrice,
        Instant createdAt,
        Instant latestEditAt
) {
}
