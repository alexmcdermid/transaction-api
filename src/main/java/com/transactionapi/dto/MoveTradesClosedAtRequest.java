package com.transactionapi.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Moves several trades to the same close date in one call. The cap matches the largest trades page,
 * since the UI only selects trades from the page it is showing.
 */
public record MoveTradesClosedAtRequest(
        @NotEmpty @Size(max = 100) List<@NotNull UUID> tradeIds,
        @NotNull LocalDate closedAt
) {
}
