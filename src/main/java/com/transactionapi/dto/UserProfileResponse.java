package com.transactionapi.dto;

import com.transactionapi.constants.PnlDisplayMode;
import com.transactionapi.constants.ThemeMode;
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
        PnlDisplayMode pnlDisplayMode
) {
    public static UserProfileResponse from(User user) {
        ThemeMode themeMode = user.getThemeMode() != null ? user.getThemeMode() : ThemeMode.LIGHT;
        PnlDisplayMode pnlDisplayMode = user.getPnlDisplayMode() != null ? user.getPnlDisplayMode() : PnlDisplayMode.PNL;
        return new UserProfileResponse(
                user.getId(),
                user.getAuthId(),
                user.getEmail(),
                user.isPremium(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                themeMode,
                pnlDisplayMode
        );
    }
}
