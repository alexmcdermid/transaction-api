package com.transactionapi.dto;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TradeDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeRequest(
        @NotBlank @Size(max = 12) String symbol,
        @NotNull AssetType assetType,
        @NotNull TradeDirection direction,
        @NotNull @Positive Integer quantity,
        @NotNull @DecimalMin("0.00") BigDecimal entryPrice,
        @NotNull @DecimalMin("0.00") BigDecimal exitPrice,
        @DecimalMin("0.00") BigDecimal fees,
        OptionType optionType,
        BigDecimal strikePrice,
        LocalDate expiryDate,
        @NotNull LocalDate openedAt,
        @NotNull LocalDate closedAt,
        @Size(max = 500) String notes
) {
}
