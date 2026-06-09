package com.transactionapi.constants;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum DashboardWidget {
    TOTAL_REALIZED,
    BEST_MONTH,
    BEST_DAY,
    DAILY_AVG_YTD,
    TAX_OWED,
    ACCOUNT_STATS,
    TRADE_COUNTS,
    POSITION_UPDATE_SIGNALS,
    INFERRED_ACCOUNT_TRADE_COUNTS;

    public static List<DashboardWidget> defaults() {
        return List.of(TOTAL_REALIZED, BEST_MONTH, BEST_DAY);
    }

    public static String defaultStorageValue() {
        return toStorage(defaults());
    }

    public static List<DashboardWidget> fromStorage(String value) {
        if (value == null) {
            return defaults();
        }
        if (value.isBlank()) {
            return List.of();
        }
        Set<DashboardWidget> widgets = new LinkedHashSet<>();
        for (String raw : value.split(",")) {
            String normalized = raw.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                widgets.add(DashboardWidget.valueOf(normalized));
            } catch (IllegalArgumentException ignored) {
                // Ignore removed or unknown widget ids stored by older app versions.
            }
        }
        return new ArrayList<>(widgets);
    }

    public static String toStorage(List<DashboardWidget> widgets) {
        if (widgets == null) {
            return defaultStorageValue();
        }
        return widgets.stream()
                .distinct()
                .map(DashboardWidget::name)
                .collect(Collectors.joining(","));
    }
}
