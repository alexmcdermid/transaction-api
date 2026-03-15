package com.transactionapi.constants;

import java.util.Locale;

public enum TradeSortField {
    SYMBOL("symbol"),
    ASSET_TYPE("assetType"),
    CURRENCY("currency"),
    DIRECTION("direction"),
    QUANTITY("quantity"),
    ENTRY_PRICE("entryPrice"),
    EXIT_PRICE("exitPrice"),
    REALIZED_PNL("realizedPnl"),
    FEES("fees"),
    MARGIN_RATE("marginRate"),
    OPTION_TYPE("optionType"),
    STRIKE_PRICE("strikePrice"),
    EXPIRY_DATE("expiryDate"),
    OPENED_AT("openedAt"),
    CLOSED_AT("closedAt"),
    ACCOUNT_ID("accountId"),
    NOTES("notes"),
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt");

    private final String propertyName;

    TradeSortField(String propertyName) {
        this.propertyName = propertyName;
    }

    public String propertyName() {
        return propertyName;
    }

    public static TradeSortField defaultValue() {
        return CLOSED_AT;
    }

    public static TradeSortField fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        String normalizedUpper = normalized.toUpperCase(Locale.ROOT);
        for (TradeSortField field : values()) {
            if (
                    field.name().equals(normalizedUpper)
                            || field.propertyName.equalsIgnoreCase(normalized)
            ) {
                return field;
            }
        }
        throw new IllegalArgumentException("Invalid trade sort field: " + value);
    }
}
