package com.transactionapi.constants;

import java.util.Locale;
import org.springframework.data.domain.Sort;

public enum TradeSortDirection {
    ASC,
    DESC;

    public static TradeSortDirection defaultValue() {
        return DESC;
    }

    public Sort.Direction toSpringDirection() {
        return this == ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    public static TradeSortDirection fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (TradeSortDirection direction : values()) {
            if (direction.name().equals(normalized)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Invalid trade sort direction: " + value);
    }
}
