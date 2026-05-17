package com.transactionapi.security;

import java.io.Serializable;

public record AuthenticatedUserPrincipal(
        String authId,
        String email,
        String name
) implements Serializable {
}
