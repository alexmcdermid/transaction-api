package com.transactionapi.security;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.model.User;
import com.transactionapi.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LegalAgreementFilter extends OncePerRequestFilter {

    private static final String AGREEMENT_REQUIRED_MESSAGE =
            "Terms of Service and Privacy Policy agreement required";

    private final Environment environment;
    private final UserIdResolver userIdResolver;
    private final UserService userService;

    @Value("${app.security.allow-header-auth:false}")
    private boolean allowHeaderAuth;

    @Value("${app.security.header-name:X-User-Id}")
    private String headerName;

    public LegalAgreementFilter(
            Environment environment,
            UserIdResolver userIdResolver,
            UserService userService
    ) {
        this.environment = environment;
        this.userIdResolver = userIdResolver;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (
                authentication == null
                        || !authentication.isAuthenticated()
                        || authentication instanceof AnonymousAuthenticationToken
        ) {
            filterChain.doFilter(request, response);
            return;
        }

        String authId = userIdResolver.requireUserId(authentication);
        String email = userIdResolver.resolveEmail(authentication);
        User user = userService.getOrCreateUser(authId, email);
        if (userService.hasAcceptedLegalAgreement(user)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + AGREEMENT_REQUIRED_MESSAGE + "\"}");
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (HttpMethod.OPTIONS.matches(method)) {
            return true;
        }
        if (!path.startsWith(ApiPaths.API_V1)) {
            return true;
        }
        if (path.equals(ApiPaths.HEALTH) || path.startsWith(ApiPaths.AUTH + "/")) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && path.equals(ApiPaths.BILLING_WEBHOOK)) {
            return true;
        }
        if (HttpMethod.GET.matches(method) && path.equals(ApiPaths.USER_ME)) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && path.equals(ApiPaths.USER_LEGAL_AGREEMENT)) {
            return true;
        }
        if (HttpMethod.GET.matches(method) && path.startsWith(ApiPaths.SHARES + "/")) {
            return true;
        }
        return isHeaderAuthProfile() && StringUtils.hasText(request.getHeader(headerName));
    }

    private boolean isHeaderAuthProfile() {
        return allowHeaderAuth && environment.acceptsProfiles(Profiles.of("local", "test"));
    }
}
