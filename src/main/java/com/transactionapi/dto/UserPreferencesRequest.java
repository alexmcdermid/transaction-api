package com.transactionapi.dto;

import com.transactionapi.constants.PnlDisplayMode;
import com.transactionapi.constants.ThemeMode;

public record UserPreferencesRequest(
        ThemeMode themeMode,
        PnlDisplayMode pnlDisplayMode
) {
}
