package com.transactionapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    public static final String ISSUED_AT_ATTRIBUTE =
            AbsoluteSessionTimeoutFilter.class.getName() + ".issuedAt";

    private final Duration absoluteTimeout;

    public AbsoluteSessionTimeoutFilter(
            @Value("${app.security.session.absolute-timeout:P7D}") Duration absoluteTimeout
    ) {
        if (absoluteTimeout.isZero() || absoluteTimeout.isNegative()) {
            throw new IllegalArgumentException("app.security.session.absolute-timeout must be positive");
        }
        this.absoluteTimeout = absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && hasExpired(session)) {
            session.invalidate();
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasExpired(HttpSession session) {
        Object issuedAt = session.getAttribute(ISSUED_AT_ATTRIBUTE);
        return issuedAt instanceof Instant issuedAtInstant
                && !Instant.now().isBefore(issuedAtInstant.plus(absoluteTimeout));
    }
}
