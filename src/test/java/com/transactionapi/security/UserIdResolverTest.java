package com.transactionapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.transactionapi.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserIdResolverTest {

    @Mock
    private UserService userService;

    private UserIdResolver userIdResolver;

    @BeforeEach
    void setUp() {
        userIdResolver = new UserIdResolver(userService);
    }

    @Test
    void allowsUserWhenAllowlistEmpty() {
        configureAllowlist("");

        JwtAuthenticationToken auth = buildAuth("sub-1", "user@example.com");
        String userId = userIdResolver.requireUserId(auth);

        assertThat(userId).isEqualTo("sub-1");
        verify(userService, never()).ensureUserExists(anyString(), anyString());
    }

    @Test
    void blocksUserNotInAllowlistAndCreatesUser() {
        configureAllowlist("allowed@example.com");

        JwtAuthenticationToken auth = buildAuth("sub-2", "blocked@example.com");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userIdResolver.requireUserId(auth));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(userService).ensureUserExists("sub-2", "blocked@example.com");
    }

    @Test
    void allowsUserInAllowlist() {
        configureAllowlist("allowed@example.com");

        JwtAuthenticationToken auth = buildAuth("sub-3", "allowed@example.com");
        String userId = userIdResolver.requireUserId(auth);

        assertThat(userId).isEqualTo("sub-3");
        verify(userService, never()).ensureUserExists(anyString(), anyString());
    }

    @Test
    void resolveEmail_returnsNullWhenEmailVerifiedIsFalse() {
        configureAllowlist("");
        JwtAuthenticationToken auth = buildAuthWithEmailVerified("sub-unverified", "unverified@example.com", false);

        String email = userIdResolver.resolveEmail(auth);

        assertThat(email).isNull();
    }

    @Test
    void resolveEmail_returnsEmailWhenEmailVerifiedIsTrue() {
        configureAllowlist("");
        JwtAuthenticationToken auth = buildAuthWithEmailVerified("sub-verified", "verified@example.com", true);

        String email = userIdResolver.resolveEmail(auth);

        assertThat(email).isEqualTo("verified@example.com");
    }

    @Test
    void resolveEmail_returnsEmailWhenEmailVerifiedClaimAbsent() {
        // Some future providers may omit the claim entirely; we don't block them
        configureAllowlist("");
        JwtAuthenticationToken auth = buildAuth("sub-noverified", "noverified@example.com");

        String email = userIdResolver.resolveEmail(auth);

        assertThat(email).isEqualTo("noverified@example.com");
    }

    private void configureAllowlist(String allowed) {
        ReflectionTestUtils.setField(userIdResolver, "allowedEmails", allowed);
        ReflectionTestUtils.setField(userIdResolver, "adminEmails", "");
        userIdResolver.init();
    }

    private JwtAuthenticationToken buildAuth(String subject, String email) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("email", email)
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private JwtAuthenticationToken buildAuthWithEmailVerified(String subject, String email, boolean emailVerified) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
