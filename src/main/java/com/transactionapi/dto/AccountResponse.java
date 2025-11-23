package com.transactionapi.dto;

import com.transactionapi.constants.AccountStatus;
import com.transactionapi.constants.AccountType;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        String institution,
        AccountType type,
        String currency,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
