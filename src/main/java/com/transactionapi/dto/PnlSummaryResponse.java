package com.transactionapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PnlSummaryResponse(
        BigDecimal totalPnl,
        int tradeCount,
        BigDecimal pnlPercent,
        List<PnlBucketResponse> daily,
        List<PnlBucketResponse> monthly,
        BigDecimal cadToUsdRate,
        LocalDate fxDate
) {
}
