package com.transactionapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityConfigTest {

    @Test
    void csrfCookieUsesSessionCookieSecureSetting() {
        SecurityConfig config = new SecurityConfig(null, null, null, null, null);
        ReflectionTestUtils.setField(config, "sessionCookieSecure", true);

        CookieCsrfTokenRepository repository = ReflectionTestUtils.invokeMethod(
                config,
                "cookieCsrfTokenRepository"
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsrfToken token = repository.generateToken(request);
        repository.saveToken(token, request, response);

        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/");
    }
}
