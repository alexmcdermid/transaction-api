package com.transactionapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeCountStatsResponse(
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
