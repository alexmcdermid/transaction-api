package com.transactionapi.dto;

import com.transactionapi.constants.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull TransactionType type,
        @NotNull BigDecimal amount,
        String symbol,
        @Positive Integer quantity,
        BigDecimal price,
        BigDecimal fee,
        UUID relatedTransactionId,
        Instant occurredAt,
        String notes
) {
}
