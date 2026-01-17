package com.transactionapi.dto;

import com.transactionapi.constants.PnlDisplayMode;
import com.transactionapi.constants.ThemeMode;
import com.transactionapi.model.User;

public record UserPreferencesResponse(
        ThemeMode themeMode,
        PnlDisplayMode pnlDisplayMode
) {
    public static UserPreferencesResponse from(User user) {
        ThemeMode themeMode = user.getThemeMode() != null ? user.getThemeMode() : ThemeMode.LIGHT;
        PnlDisplayMode pnlDisplayMode =
                user.getPnlDisplayMode() != null ? user.getPnlDisplayMode() : PnlDisplayMode.PNL;
        return new UserPreferencesResponse(themeMode, pnlDisplayMode);
    }
}
