package com.transactionapi.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InferredAccountTradeCountsResponse(
        UUID accountId,
        String accountName,
        int recordedTradeCount,
        int inferredBuyCount,
        int inferredSellCount,
        int inferredTotalCount,
        int inferredAddCount,
        int inferredAddedQuantity,
        BigDecimal averageInferredAddPrice,
        Integer year
) {
}
