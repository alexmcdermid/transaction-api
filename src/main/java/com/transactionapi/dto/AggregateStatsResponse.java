package com.transactionapi.dto;

import java.math.BigDecimal;

/**
 * Aggregate statistics computed efficiently via database queries
 */
public record AggregateStatsResponse(
        BigDecimal totalPnl,
        int tradeCount,
        BigDecimal pnlPercent,
        PnlBucketResponse bestDay,
        PnlBucketResponse bestMonth,
        BigDecimal cadToUsdRate,
        java.time.LocalDate fxDate
) {
}
