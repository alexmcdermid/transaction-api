package com.transactionapi.dto;

import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        BigDecimal amount,
        String ticker,
        String name,
        String currency,
        String exchange,
        Integer quantity,
        BigDecimal price,
        OptionType optionType,
        BigDecimal strikePrice,
        LocalDate expiryDate,
        String underlyingTicker,
        BigDecimal fee,
        UUID relatedTransactionId,
        Instant occurredAt,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
