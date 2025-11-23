package com.transactionapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserIdResolver {

    @Value("${app.security.dev-user-id:}")
    private String devUserId;

    public String requireUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            if (StringUtils.hasText(jwt.getSubject())) {
                return jwt.getSubject();
            }
            String email = jwt.getClaimAsString("email");
            if (StringUtils.hasText(email)) {
                return email;
            }
        }
        if (authentication != null && authentication.getPrincipal() instanceof String principal
                && StringUtils.hasText(principal)) {
            return principal;
        }
        if (StringUtils.hasText(devUserId)) {
            return devUserId;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User identity missing");
    }
}
