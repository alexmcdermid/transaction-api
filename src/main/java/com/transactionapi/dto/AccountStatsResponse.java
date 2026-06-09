package com.transactionapi.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountStatsResponse(
        UUID accountId,
        String accountName,
        BigDecimal totalPnl,
        BigDecimal monthlyAveragePnl,
        BigDecimal tradedDayAveragePnl,
        BigDecimal averageTradePnl,
        BigDecimal totalNotional,
        BigDecimal pnlPercent,
        int tradeCount,
        int tradedDays,
        int activeMonths,
        Integer year
) {
}
