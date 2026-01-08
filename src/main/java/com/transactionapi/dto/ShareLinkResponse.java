package com.transactionapi.dto;

import com.transactionapi.constants.ShareType;
import java.time.Instant;

public record ShareLinkResponse(
        String code,
        ShareType shareType,
        String data,
        boolean requiresAuth,
        Instant expiresAt,
        int accessCount,
        Instant createdAt
) {
}
