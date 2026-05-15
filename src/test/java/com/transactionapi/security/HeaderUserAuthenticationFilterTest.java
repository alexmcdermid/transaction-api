package com.transactionapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class HeaderUserAuthenticationFilterTest {

    private final HeaderUserAuthenticationFilter filter = new HeaderUserAuthenticationFilter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoresUserHeaderWhenHeaderAuthIsDisabled() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "allowHeaderAuth", false);
        ReflectionTestUtils.setField(filter, "headerName", "X-User-Id");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "spoofed-user");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void authenticatesUserHeaderWhenHeaderAuthIsEnabled() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "allowHeaderAuth", true);
        ReflectionTestUtils.setField(filter, "headerName", "X-User-Id");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "local-user");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .extracting(authentication -> authentication.getPrincipal())
                .isEqualTo("local-user");
    }

    @Test
    void doesNotOverrideExistingAuthenticationWithUserHeader() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "allowHeaderAuth", true);
        ReflectionTestUtils.setField(filter, "headerName", "X-User-Id");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("jwt-user", null, List.of())
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "spoofed-user");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .extracting(authentication -> authentication.getPrincipal())
                .isEqualTo("jwt-user");
    }
}
