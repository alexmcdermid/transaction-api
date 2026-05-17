package com.transactionapi.dto;

public record CsrfTokenResponse(
        String headerName,
        String parameterName,
        String token
) {
}
