package com.transactionapi.dto;

import com.transactionapi.constants.Currency;
import com.transactionapi.constants.Exchange;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull TransactionType type,
        @NotNull @PositiveOrZero BigDecimal amount,
        @Size(max = 4) String ticker,
        @Size(max = 255) String name,
        Currency currency,
        Exchange exchange,
        @Positive Integer quantity,
        @PositiveOrZero BigDecimal price,
        OptionType optionType,
        @PositiveOrZero BigDecimal strikePrice,
        LocalDate expiryDate,
        @PositiveOrZero BigDecimal fee,
        UUID relatedTransactionId,
        UUID targetAccountId,
        Instant occurredAt,
        String notes
) {

    @AssertTrue(message = "strikePrice and expiryDate are required when optionType is set")
    public boolean isOptionFieldsValid() {
        if (optionType == null) {
            return true;
        }
        return strikePrice != null && expiryDate != null;
    }

    @AssertTrue(message = "targetAccountId is required for TRANSFER")
    public boolean isTransferFieldsValid() {
        if (type != TransactionType.TRANSFER) {
            return true;
        }
        return targetAccountId != null;
    }
}
