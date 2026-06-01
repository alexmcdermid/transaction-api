package com.transactionapi.dto;

import com.transactionapi.constants.DashboardWidget;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.PnlDisplayMode;
import com.transactionapi.constants.ThemeMode;
import com.transactionapi.constants.TradeSortDirection;
import com.transactionapi.constants.TradeSortField;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record UserPreferencesRequest(
        ThemeMode themeMode,
        PnlDisplayMode pnlDisplayMode,
        TradeSortField defaultTradeSortBy,
        TradeSortDirection defaultTradeSortDirection,
        Boolean showTradeHistory,
        List<@NotNull DashboardWidget> dashboardWidgets,
        Currency displayCurrency,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal taxCapitalGainsRate,
        @DecimalMin("1.00") @DecimalMax("100.00") BigDecimal taxPersonalRate
) {
}
