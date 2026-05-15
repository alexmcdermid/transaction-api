package com.transactionapi.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public class RefreshingJwkSetJwtDecoder implements JwtDecoder {

    private final Supplier<String> jwkSetSupplier;
    private final Set<JWSAlgorithm> jwsAlgorithms;
    private final Duration refreshInterval;
    private final OAuth2TokenValidator<Jwt> jwtValidator;
    private final Clock clock;
    private volatile CachedDecoder cachedDecoder;

    public RefreshingJwkSetJwtDecoder(
            Supplier<String> jwkSetSupplier,
            Set<JWSAlgorithm> jwsAlgorithms,
            Duration refreshInterval,
            OAuth2TokenValidator<Jwt> jwtValidator
    ) {
        this(jwkSetSupplier, jwsAlgorithms, refreshInterval, jwtValidator, Clock.systemUTC());
    }

    RefreshingJwkSetJwtDecoder(
            Supplier<String> jwkSetSupplier,
            Set<JWSAlgorithm> jwsAlgorithms,
            Duration refreshInterval,
            OAuth2TokenValidator<Jwt> jwtValidator,
            Clock clock
    ) {
        this.jwkSetSupplier = jwkSetSupplier;
        this.jwsAlgorithms = jwsAlgorithms;
        this.refreshInterval = refreshInterval;
        this.jwtValidator = jwtValidator;
        this.clock = clock;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        NimbusJwtDecoder decoder = getDecoder(false);
        try {
            return decoder.decode(token);
        } catch (JwtException ex) {
            NimbusJwtDecoder refreshed = getDecoder(true);
            if (refreshed != decoder) {
                return refreshed.decode(token);
            }
            throw ex;
        }
    }

    private NimbusJwtDecoder getDecoder(boolean forceRefresh) {
        CachedDecoder current = cachedDecoder;
        Instant now = clock.instant();
        if (!forceRefresh && current != null && now.isBefore(current.refreshAfter())) {
            return current.decoder();
        }
        synchronized (this) {
            current = cachedDecoder;
            now = clock.instant();
            if (!forceRefresh && current != null && now.isBefore(current.refreshAfter())) {
                return current.decoder();
            }
            NimbusJwtDecoder decoder = buildDecoder(loadJwkSet());
            cachedDecoder = new CachedDecoder(decoder, now.plus(refreshInterval));
            return decoder;
        }
    }

    private String loadJwkSet() {
        try {
            return jwkSetSupplier.get();
        } catch (RuntimeException ex) {
            throw new JwtException("Unable to load JWKS", ex);
        }
    }

    private NimbusJwtDecoder buildDecoder(String jwks) {
        JWKSet jwkSet;
        try {
            jwkSet = JWKSet.parse(jwks);
        } catch (ParseException ex) {
            throw new JwtException("Invalid JWKS JSON", ex);
        }

        DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                jwsAlgorithms,
                new ImmutableJWKSet<>(jwkSet)
        ));

        NimbusJwtDecoder decoder = new NimbusJwtDecoder(jwtProcessor);
        decoder.setJwtValidator(jwtValidator);
        return decoder;
    }

    private record CachedDecoder(NimbusJwtDecoder decoder, Instant refreshAfter) {
    }
}
