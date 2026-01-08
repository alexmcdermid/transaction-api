package com.transactionapi.dto;

import com.transactionapi.constants.ShareType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateShareLinkRequest(
        @NotNull ShareType shareType,
        @NotBlank String data,
        boolean requiresAuth,
        @Max(90) Long expiryDays
) {
}
