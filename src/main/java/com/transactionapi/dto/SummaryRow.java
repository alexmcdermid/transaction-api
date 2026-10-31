package com.transactionapi.dto;

import com.transactionapi.constants.Currency;
import java.math.BigDecimal;

/**
 * One annual summary group in one recorded currency. Money is never FX-converted.
 * {@code taxFree} is only set on account and tax treatment rows.
 */
public record SummaryRow(
        String key,
        String label,
        Currency currency,
        Boolean taxFree,
        int tradeCount,
        int winningTrades,
        int losingTrades,
        BigDecimal gains,
        BigDecimal losses,
        BigDecimal fees,
        BigDecimal marginInterest,
        BigDecimal netRealizedPnl
) {
}
