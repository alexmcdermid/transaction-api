package com.transactionapi.dto;

import java.math.BigDecimal;

public record PnlBucketResponse(
        String period,
        BigDecimal pnl,
        int trades,
        BigDecimal pnlPercent
) {
}
