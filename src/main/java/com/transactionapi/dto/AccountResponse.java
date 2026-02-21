package com.transactionapi.dto;

import com.transactionapi.model.Account;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        BigDecimal defaultStockFees,
        BigDecimal defaultOptionFees,
        BigDecimal defaultMarginRateUsd,
        BigDecimal defaultMarginRateCad,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountResponse from(Account account) {
        BigDecimal defaultStockFees = account.getDefaultStockFees() != null
                ? account.getDefaultStockFees()
                : BigDecimal.ZERO;
        BigDecimal defaultOptionFees = account.getDefaultOptionFees() != null
                ? account.getDefaultOptionFees()
                : BigDecimal.ZERO;
        BigDecimal defaultMarginRateUsd =
                account.getDefaultMarginRateUsd() != null ? account.getDefaultMarginRateUsd() : BigDecimal.ZERO;
        BigDecimal defaultMarginRateCad =
                account.getDefaultMarginRateCad() != null ? account.getDefaultMarginRateCad() : BigDecimal.ZERO;
        return new AccountResponse(
                account.getId(),
                account.getName(),
                defaultStockFees,
                defaultOptionFees,
                defaultMarginRateUsd,
                defaultMarginRateCad,
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
