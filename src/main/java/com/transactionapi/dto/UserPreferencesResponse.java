package com.transactionapi.dto;

import com.transactionapi.constants.DashboardWidget;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.PnlDisplayMode;
import com.transactionapi.constants.ThemeMode;
import com.transactionapi.constants.TradeSortDirection;
import com.transactionapi.constants.TradeSortField;
import com.transactionapi.model.User;
import java.math.BigDecimal;
import java.util.List;

public record UserPreferencesResponse(
        ThemeMode themeMode,
        PnlDisplayMode pnlDisplayMode,
        TradeSortField defaultTradeSortBy,
        TradeSortDirection defaultTradeSortDirection,
        boolean showTradeHistory,
        List<DashboardWidget> dashboardWidgets,
        Currency displayCurrency,
        BigDecimal taxCapitalGainsRate,
        BigDecimal taxPersonalRate
) {
    public static UserPreferencesResponse from(User user) {
        ThemeMode themeMode = user.getThemeMode() != null ? user.getThemeMode() : ThemeMode.LIGHT;
        PnlDisplayMode pnlDisplayMode =
                user.getPnlDisplayMode() != null ? user.getPnlDisplayMode() : PnlDisplayMode.PNL;
        TradeSortField defaultTradeSortBy = user.getDefaultTradeSortBy() != null
                ? user.getDefaultTradeSortBy()
                : TradeSortField.defaultValue();
        TradeSortDirection defaultTradeSortDirection = user.getDefaultTradeSortDirection() != null
                ? user.getDefaultTradeSortDirection()
                : TradeSortDirection.defaultValue();
        return new UserPreferencesResponse(
                themeMode,
                pnlDisplayMode,
                defaultTradeSortBy,
                defaultTradeSortDirection,
                user.isShowTradeHistory(),
                DashboardWidget.fromStorage(user.getDashboardWidgets()),
                user.getDisplayCurrency() != null ? user.getDisplayCurrency() : Currency.USD,
                user.getTaxCapitalGainsRate(),
                user.getTaxPersonalRate()
        );
    }
}
