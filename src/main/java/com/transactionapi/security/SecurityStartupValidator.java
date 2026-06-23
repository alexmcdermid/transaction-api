package com.transactionapi.security;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecurityStartupValidator {

    private final Environment environment;

    @Value("${app.security.jwt.enabled:false}")
    private boolean jwtEnabled;

    @Value("${app.security.jwt.audience:}")
    private String jwtAudience;

    @Value("${app.security.allow-header-auth:false}")
    private boolean allowHeaderAuth;

    @Value("${app.security.admin-emails:}")
    private String adminEmails;

    @Value("${server.servlet.session.cookie.secure:true}")
    private boolean sessionCookieSecure;

    @Value("${server.servlet.session.cookie.same-site:lax}")
    private String sessionCookieSameSite;

    public SecurityStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
            return;
        }

        List<String> problems = new ArrayList<>();
        if (!jwtEnabled) {
            problems.add("app.security.jwt.enabled must be true");
        }
        if (!StringUtils.hasText(jwtAudience)) {
            problems.add("app.security.jwt.audience must be set");
        }
        if (allowHeaderAuth) {
            problems.add("app.security.allow-header-auth must be false");
        }
        if (!StringUtils.hasText(adminEmails)) {
            problems.add("app.security.admin-emails must be set");
        }
        if (!sessionCookieSecure) {
            problems.add("server.servlet.session.cookie.secure must be true");
        }
        if (!isValidSameSite(sessionCookieSameSite)) {
            problems.add("server.servlet.session.cookie.same-site must be lax, strict, or none");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start with unsafe security configuration outside local/test profiles: "
                            + String.join("; ", problems)
            );
        }
    }

    private boolean isValidSameSite(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase();
        return "lax".equals(normalized) || "strict".equals(normalized) || "none".equals(normalized);
    }
}
