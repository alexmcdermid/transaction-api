package com.transactionapi.dto;

import com.transactionapi.constants.AccountType;
import com.transactionapi.constants.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
        @NotBlank String name,
        @NotNull AccountType type,
        @NotNull Currency currency,
        String institution
) {
}
