package com.transactionapi.security;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transactionapi.constants.ApiPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class RateLimitingFilterTest {

    private final RateLimiterService rateLimiterService = org.mockito.Mockito.mock(RateLimiterService.class);
    private final UserIdResolver userIdResolver = org.mockito.Mockito.mock(UserIdResolver.class);

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedPublicShareUsesRemoteAddressByDefault() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(rateLimiterService, userIdResolver);
        MockHttpServletRequest request = publicShareRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.7");
        when(rateLimiterService.allowPublicShare("203.0.113.10")).thenReturn(true);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(rateLimiterService).allowPublicShare("203.0.113.10");
    }

    @Test
    void unauthenticatedPublicShareUsesForwardedHeaderOnlyWhenTrusted() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(rateLimiterService, userIdResolver);
        ReflectionTestUtils.setField(filter, "trustForwardedHeaders", true);
        MockHttpServletRequest request = publicShareRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.1");
        when(rateLimiterService.allowPublicShare("198.51.100.7")).thenReturn(true);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(rateLimiterService).allowPublicShare("198.51.100.7");
    }

    private MockHttpServletRequest publicShareRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ApiPaths.SHARES + "/abc12345");
        request.setServletPath(ApiPaths.SHARES + "/abc12345");
        return request;
    }
}
