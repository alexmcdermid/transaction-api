package com.transactionapi.dto;

import com.transactionapi.constants.TransactionType;
import com.transactionapi.constants.OptionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull TransactionType type,
        @NotNull BigDecimal amount,
        @Size(max = 4) String ticker,
        @Size(max = 255) String name,
        @Size(min = 3, max = 3) String currency,
        @Size(max = 20) String exchange,
        @Positive Integer quantity,
        BigDecimal price,
        OptionType optionType,
        BigDecimal strikePrice,
        LocalDate expiryDate,
        @Size(max = 4) String underlyingTicker,
        BigDecimal fee,
        UUID relatedTransactionId,
        Instant occurredAt,
        String notes
) {

    @AssertTrue(message = "strikePrice, expiryDate, and underlyingTicker are required when optionType is set")
    public boolean isOptionFieldsValid() {
        if (optionType == null) {
            return true;
        }
        return strikePrice != null && expiryDate != null && underlyingTicker != null;
    }
}
