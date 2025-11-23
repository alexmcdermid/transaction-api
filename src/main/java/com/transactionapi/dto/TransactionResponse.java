package com.transactionapi.dto;

import com.transactionapi.constants.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        BigDecimal amount,
        String symbol,
        Integer quantity,
        BigDecimal price,
        BigDecimal fee,
        UUID relatedTransactionId,
        Instant occurredAt,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
