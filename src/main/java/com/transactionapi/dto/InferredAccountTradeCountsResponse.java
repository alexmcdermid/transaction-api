package com.transactionapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InferredAccountTradeCountsResponse(
        UUID accountId,
        String accountName,
        int recordedTradeCount,
        int inferredBuyCount,
        int inferredSellCount,
        int inferredTotalCount,
        int monthInferredTotalCount,
        int dayInferredTotalCount,
        int inferredAddCount,
        int monthInferredAddCount,
        int dayInferredAddCount,
        int inferredAddedQuantity,
        BigDecimal averageInferredAddPrice,
        Integer year,
        String month,
        LocalDate day
) {
}
