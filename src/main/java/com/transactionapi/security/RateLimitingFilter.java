package com.transactionapi.security;

import com.transactionapi.constants.ApiPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final UserIdResolver userIdResolver;

    public RateLimitingFilter(RateLimiterService rateLimiterService, UserIdResolver userIdResolver) {
        this.rateLimiterService = rateLimiterService;
        this.userIdResolver = userIdResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Public share endpoint: IP-based rate limiting for unauthenticated, user-based for authenticated
        if ("GET".equals(method) && requestPath != null && requestPath.matches(ApiPaths.SHARES + "/[^/]+")) {
            if (authentication == null || !authentication.isAuthenticated()) {
                String ipAddress = getClientIp(request);
                if (!rateLimiterService.allowPublicShare(ipAddress)) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.getWriter().write("Too many requests. Please try again later.");
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            }
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId;
        try {
            userId = userIdResolver.requireUserId(authentication);
        } catch (Exception ex) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!rateLimiterService.allow(userId)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
