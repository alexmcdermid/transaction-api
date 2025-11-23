package com.transactionapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

@Component
public class HeaderUserAuthenticationFilter extends OncePerRequestFilter {

    @Value("${app.security.allow-header-auth:true}")
    private boolean allowHeaderAuth;

    @Value("${app.security.header-name:X-User-Id}")
    private String headerName;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    )
            throws ServletException, IOException {
        if (allowHeaderAuth && SecurityContextHolder.getContext().getAuthentication() == null) {
            String userId = request.getHeader(headerName);
            if (StringUtils.hasText(userId)) {
                var authentication = new UsernamePasswordAuthenticationToken(userId.trim(), null, List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
