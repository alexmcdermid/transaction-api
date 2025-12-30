package com.transactionapi.dto;

import com.transactionapi.model.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String authId,
        String email,
        boolean premium,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getAuthId(),
                user.getEmail(),
                user.isPremium(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
