package com.transactionapi.dto;

import com.transactionapi.constants.AccountStatus;
import com.transactionapi.constants.AccountType;
import com.transactionapi.constants.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        String institution,
        AccountType type,
        Currency currency,
        AccountStatus status,
        BigDecimal balance,
        Instant createdAt,
        Instant updatedAt
) {
}
