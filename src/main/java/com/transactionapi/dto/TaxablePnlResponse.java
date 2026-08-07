package com.transactionapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxablePnlResponse(
        BigDecimal totalPnl,
        BigDecimal cadToUsdRate,
        LocalDate fxDate,
        int year
) {
}
