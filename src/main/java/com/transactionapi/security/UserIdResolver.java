package com.transactionapi.security;

import com.transactionapi.service.UserService;
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

    private final UserService userService;

    @Value("${app.security.dev-user-id:}")
    private String devUserId;

    @Value("${app.security.allowed-emails:}")
    private String allowedEmails;

    @Value("${app.security.admin-emails:}")
    private String adminEmails;

    private Set<String> allowedEmailSet = Collections.emptySet();
    private Set<String> adminEmailSet = Collections.emptySet();

    public UserIdResolver(UserService userService) {
        this.userService = userService;
    }

    @PostConstruct
    void init() {
        allowedEmailSet = parseEmails(allowedEmails);
        adminEmailSet = parseEmails(adminEmails);
        if (adminEmailSet.isEmpty()) {
            adminEmailSet = allowedEmailSet;
        }
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

    public void requireAdmin(Authentication authentication) {
        if (adminEmailSet.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        String email = resolveEmail(authentication);
        if (!StringUtils.hasText(email) || !adminEmailSet.contains(email.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private void enforceAllowedEmails(Authentication authentication) {
        if (allowedEmailSet.isEmpty()) {
            return;
        }
        String email = resolveEmail(authentication);
        if (!StringUtils.hasText(email)) {
            registerBlockedUser(authentication, null);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email not allowed");
        }
        if (!allowedEmailSet.contains(email.toLowerCase())) {
            registerBlockedUser(authentication, email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email not allowed");
        }
    }

    public String resolveEmail(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getClaimAsString("email");
        }
        if (authentication != null && authentication.getPrincipal() instanceof String principal) {
            if (principal.contains("@")) {
                return principal;
            }
        }
        return null;
    }

    private String resolveAuthId(Authentication authentication) {
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
        return null;
    }

    private void registerBlockedUser(Authentication authentication, String email) {
        String authId = resolveAuthId(authentication);
        if (StringUtils.hasText(authId)) {
            userService.ensureUserExists(authId, email);
        }
    }

    private Set<String> parseEmails(String emails) {
        if (!StringUtils.hasText(emails)) {
            return Collections.emptySet();
        }
        return Arrays.stream(emails.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}
