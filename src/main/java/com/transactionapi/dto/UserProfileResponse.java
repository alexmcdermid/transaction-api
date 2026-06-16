package com.transactionapi.dto;

import com.transactionapi.constants.DashboardWidget;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.PnlDisplayMode;
import com.transactionapi.constants.ThemeMode;
import com.transactionapi.constants.TradeSortDirection;
import com.transactionapi.constants.TradeSortField;
import com.transactionapi.model.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String authId,
        String email,
        boolean admin,
        boolean premium,
        Instant createdAt,
        Instant updatedAt,
        ThemeMode themeMode,
        PnlDisplayMode pnlDisplayMode,
        TradeSortField defaultTradeSortBy,
        TradeSortDirection defaultTradeSortDirection,
        boolean showTradeHistory,
        boolean showDetailedTradeTimes,
        List<DashboardWidget> dashboardWidgets,
        Currency displayCurrency,
        BigDecimal taxCapitalGainsRate,
        BigDecimal taxPersonalRate,
        Instant termsAcceptedAt,
        Instant privacyPolicyAcceptedAt
) {
    public static UserProfileResponse from(User user) {
        return from(user, false);
    }

    public static UserProfileResponse from(User user, boolean admin) {
        ThemeMode themeMode = user.getThemeMode() != null ? user.getThemeMode() : ThemeMode.LIGHT;
        PnlDisplayMode pnlDisplayMode = user.getPnlDisplayMode() != null ? user.getPnlDisplayMode() : PnlDisplayMode.PNL;
        TradeSortField defaultTradeSortBy = user.getDefaultTradeSortBy() != null
                ? user.getDefaultTradeSortBy()
                : TradeSortField.defaultValue();
        TradeSortDirection defaultTradeSortDirection = user.getDefaultTradeSortDirection() != null
                ? user.getDefaultTradeSortDirection()
                : TradeSortDirection.defaultValue();
        return new UserProfileResponse(
                user.getId(),
                user.getAuthId(),
                user.getEmail(),
                admin,
                user.isPremium(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                themeMode,
                pnlDisplayMode,
                defaultTradeSortBy,
                defaultTradeSortDirection,
                user.isShowTradeHistory(),
                user.isShowDetailedTradeTimes(),
                DashboardWidget.fromStorage(user.getDashboardWidgets()),
                user.getDisplayCurrency() != null ? user.getDisplayCurrency() : Currency.USD,
                user.getTaxCapitalGainsRate(),
                user.getTaxPersonalRate(),
                user.getTermsAcceptedAt(),
                user.getPrivacyPolicyAcceptedAt()
        );
    }
}
