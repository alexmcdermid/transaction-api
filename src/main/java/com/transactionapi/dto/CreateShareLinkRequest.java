package com.transactionapi.dto;

import com.transactionapi.constants.ShareType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateShareLinkRequest(
        @NotNull ShareType shareType,
        @NotBlank @Size(max = 65536) String data,
        boolean requiresAuth,
        @Max(90) Long expiryDays
) {
}
