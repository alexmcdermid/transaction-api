package com.transactionapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TradeCountStatsResponse(
        UUID accountId,
        String accountName,
        int yearTradeCount,
        int monthTradeCount,
        int dayTradeCount,
        int yearTradedDays,
        BigDecimal averageTradesPerTradedDay,
        BigDecimal averageTradesPerTradingDay,
        Integer year,
        String month,
        LocalDate day
) {
}
