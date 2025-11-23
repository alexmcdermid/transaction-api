package com.transactionapi.dto;

import com.transactionapi.constants.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
        @NotBlank String name,
        @NotNull AccountType type,
        @NotBlank String currency,
        String institution
) {
}
