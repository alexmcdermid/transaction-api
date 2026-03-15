package com.transactionapi.dto;

import com.transactionapi.constants.PnlDisplayMode;
import com.transactionapi.constants.ThemeMode;
import com.transactionapi.constants.TradeSortDirection;
import com.transactionapi.constants.TradeSortField;
import com.transactionapi.model.User;
import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String authId,
        String email,
        boolean premium,
        Instant createdAt,
        Instant updatedAt,
        ThemeMode themeMode,
        PnlDisplayMode pnlDisplayMode,
        TradeSortField defaultTradeSortBy,
        TradeSortDirection defaultTradeSortDirection
) {
    public static UserProfileResponse from(User user) {
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
                user.isPremium(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                themeMode,
                pnlDisplayMode,
                defaultTradeSortBy,
                defaultTradeSortDirection
        );
    }
}
