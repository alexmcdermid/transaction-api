package com.transactionapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank @Size(max = 120) String name,
        @DecimalMin("0.00") BigDecimal defaultStockFees,
        @DecimalMin("0.00") BigDecimal defaultOptionFees,
        @DecimalMin("0.00") BigDecimal defaultMarginRateUsd,
        @DecimalMin("0.00") BigDecimal defaultMarginRateCad
) {
}
