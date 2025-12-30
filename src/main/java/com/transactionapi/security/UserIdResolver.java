package com.transactionapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserIdResolver {

    @Value("${app.security.dev-user-id:}")
    private String devUserId;

    @Value("${app.security.allowed-emails:}")
    private String allowedEmails;

    private Set<String> allowedEmailSet = Collections.emptySet();

    @PostConstruct
    void init() {
        if (!StringUtils.hasText(allowedEmails)) {
            allowedEmailSet = Collections.emptySet();
            return;
        }
        allowedEmailSet = Arrays.stream(allowedEmails.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    public String requireUserId(Authentication authentication) {
        enforceAllowedEmails(authentication);
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

    private void enforceAllowedEmails(Authentication authentication) {
        if (allowedEmailSet.isEmpty()) {
            return;
        }
        String email = null;
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            email = jwtAuth.getToken().getClaimAsString("email");
        } else if (authentication != null && authentication.getPrincipal() instanceof String principal) {
            if (principal.contains("@")) {
                email = principal;
            }
        }
        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email not allowed");
        }
        if (!allowedEmailSet.contains(email.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email not allowed");
        }
    }
}
