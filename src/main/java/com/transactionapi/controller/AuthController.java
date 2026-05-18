package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.AuthLoginRequest;
import com.transactionapi.dto.CsrfTokenResponse;
import com.transactionapi.dto.UserProfileResponse;
import com.transactionapi.model.User;
import com.transactionapi.security.AuthenticatedUserPrincipal;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ApiPaths.AUTH)
public class AuthController {

    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;
    private final UserIdResolver userIdResolver;
    private final UserService userService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextLogoutHandler securityContextLogoutHandler = new SecurityContextLogoutHandler();
    private final CookieClearingLogoutHandler cookieClearingLogoutHandler =
            new CookieClearingLogoutHandler("JSESSIONID", "XSRF-TOKEN");

    public AuthController(
            ObjectProvider<JwtDecoder> jwtDecoderProvider,
            UserIdResolver userIdResolver,
            UserService userService
    ) {
        this.jwtDecoderProvider = jwtDecoderProvider;
        this.userIdResolver = userIdResolver;
        this.userService = userService;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(
                csrfToken.getHeaderName(),
                csrfToken.getParameterName(),
                csrfToken.getToken()
        );
    }

    @PostMapping("/login")
    public UserProfileResponse login(
            @Valid @RequestBody AuthLoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable(() -> {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "JWT login is not configured");
        });
        Jwt jwt;
        try {
            jwt = decoder.decode(loginRequest.credential());
        } catch (JwtException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credential", ex);
        }
        JwtAuthenticationToken jwtAuthentication = new JwtAuthenticationToken(jwt);
        String authId = userIdResolver.requireUserId(jwtAuthentication);
        String email = userIdResolver.resolveEmail(jwtAuthentication);
        User user = userService.getOrCreateUser(authId, email);

        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                authId,
                email,
                jwt.getClaimAsString("name")
        );
        UsernamePasswordAuthenticationToken sessionAuthentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());

        HttpSession session = request.getSession(true);
        if (!session.isNew()) {
            request.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(sessionAuthentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return UserProfileResponse.from(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        cookieClearingLogoutHandler.logout(request, response, authentication);
        securityContextLogoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
