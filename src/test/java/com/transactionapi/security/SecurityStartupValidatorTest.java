package com.transactionapi.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityStartupValidatorTest {

    @Test
    void allowsUnsafeConfigInLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        SecurityStartupValidator validator = validator(environment, false, "", true, "", false, "");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void allowsUnsafeConfigInTestProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        SecurityStartupValidator validator = validator(environment, false, "", true, "", false, "");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsafeConfigOutsideLocalAndTestProfiles() {
        SecurityStartupValidator validator = validator(new MockEnvironment(), false, "", true, "", false, "");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.security.jwt.enabled must be true")
                .hasMessageContaining("app.security.jwt.audience must be set")
                .hasMessageContaining("app.security.allow-header-auth must be false")
                .hasMessageContaining("app.security.admin-emails must be set")
                .hasMessageContaining("server.servlet.session.cookie.secure must be true")
                .hasMessageContaining("server.servlet.session.cookie.same-site must be lax, strict, or none");
    }

    @Test
    void allowsHardenedConfigOutsideLocalAndTestProfiles() {
        SecurityStartupValidator validator = validator(
                new MockEnvironment(),
                true,
                "google-client-id",
                false,
                "admin@example.com",
                true,
                "lax"
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private SecurityStartupValidator validator(
            MockEnvironment environment,
            boolean jwtEnabled,
            String jwtAudience,
            boolean allowHeaderAuth,
            String adminEmails,
            boolean sessionCookieSecure,
            String sessionCookieSameSite
    ) {
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        ReflectionTestUtils.setField(validator, "jwtEnabled", jwtEnabled);
        ReflectionTestUtils.setField(validator, "jwtAudience", jwtAudience);
        ReflectionTestUtils.setField(validator, "allowHeaderAuth", allowHeaderAuth);
        ReflectionTestUtils.setField(validator, "adminEmails", adminEmails);
        ReflectionTestUtils.setField(validator, "sessionCookieSecure", sessionCookieSecure);
        ReflectionTestUtils.setField(validator, "sessionCookieSameSite", sessionCookieSameSite);
        return validator;
    }
}
