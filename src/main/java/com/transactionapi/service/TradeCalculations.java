package com.transactionapi.service;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.model.Trade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;

/**
 * Per-trade money math shared by recorded realized P/L and tax year reports, so both always agree.
 */
public final class TradeCalculations {

    private static final BigDecimal OPTION_MULTIPLIER = BigDecimal.valueOf(100);
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private TradeCalculations() {
    }

    /** Contract size: 100 shares per option contract, 1 per share of stock. */
    public static BigDecimal multiplier(AssetType assetType) {
        return assetType == AssetType.OPTION ? OPTION_MULTIPLIER : BigDecimal.ONE;
    }

    /** {@code (exit - entry) * quantity * multiplier}, negated for SHORT. Unrounded. */
    public static BigDecimal grossPnl(Trade trade) {
        BigDecimal movement = trade.getExitPrice().subtract(trade.getEntryPrice());
        if (trade.getDirection() == TradeDirection.SHORT) {
            movement = movement.negate();
        }
        return movement.multiply(trade.getQuantity()).multiply(multiplier(trade.getAssetType()));
    }

    /**
     * Estimated margin interest: entry notional * annual rate * days held / 365, rounded to 2 decimals
     * HALF_UP. Zero without a positive rate, for same-day trades, or without a positive notional.
     */
    public static BigDecimal marginInterest(Trade trade) {
        BigDecimal marginRate = trade.getMarginRate();
        if (marginRate == null || marginRate.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO_MONEY;
        }
        if (trade.getOpenedAt() == null || trade.getClosedAt() == null) {
            return ZERO_MONEY;
        }
        long daysHeld = ChronoUnit.DAYS.between(trade.getOpenedAt(), trade.getClosedAt());
        if (daysHeld <= 0) {
            return ZERO_MONEY;
        }
        BigDecimal notional = notional(trade);
        if (notional == null || notional.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO_MONEY;
        }
        BigDecimal yearFraction = BigDecimal.valueOf(daysHeld)
                .divide(DAYS_IN_YEAR, 10, RoundingMode.HALF_UP);
        BigDecimal rate = marginRate.divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP);
        return notional.multiply(rate).multiply(yearFraction).setScale(2, RoundingMode.HALF_UP);
    }

    /** Absolute entry notional, {@code |entry * quantity * multiplier|}; null when entry or quantity is missing. */
    public static BigDecimal notional(Trade trade) {
        if (trade.getEntryPrice() == null || trade.getQuantity() == null) {
            return null;
        }
        return trade.getEntryPrice()
                .multiply(trade.getQuantity())
                .multiply(multiplier(trade.getAssetType()))
                .abs();
    }
}
