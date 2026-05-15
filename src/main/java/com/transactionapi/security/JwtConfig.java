package com.transactionapi.security;

import com.nimbusds.jose.JWSAlgorithm;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration
public class JwtConfig {

    private final ObjectProvider<DynamoJwkSetReader> dynamoJwkSetReaderProvider;

    @Value("${app.security.jwt.issuer-uri:https://accounts.google.com}")
    private String issuerUri;

    @Value("${app.security.jwt.audience:}")
    private String audience;

    @Value("${app.security.jwt.jwk-source:issuer}")
    private String jwkSource;

    @Value("${app.security.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${app.security.jwt.jwk-set:}")
    private String inlineJwkSet;

    @Value("${app.security.jwt.jws-algorithms:RS256}")
    private String jwsAlgorithms;

    @Value("${app.security.jwt.jwk-refresh-interval:PT15M}")
    private Duration jwkRefreshInterval;

    public JwtConfig(ObjectProvider<DynamoJwkSetReader> dynamoJwkSetReaderProvider) {
        this.dynamoJwkSetReaderProvider = dynamoJwkSetReaderProvider;
    }

    @Bean
    @ConditionalOnProperty(value = "app.security.jwt.enabled", havingValue = "true")
    public JwtDecoder jwtDecoder() {
        OAuth2TokenValidator<Jwt> validator = jwtValidator();
        String source = normalizeJwkSource(jwkSource);
        return switch (source) {
            case "issuer" -> {
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
                decoder.setJwtValidator(validator);
                yield decoder;
            }
            case "jwk-set-uri" -> {
                if (!StringUtils.hasText(jwkSetUri)) {
                    throw new IllegalStateException("JWT JWK source is jwk-set-uri but app.security.jwt.jwk-set-uri is empty");
                }
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
                decoder.setJwtValidator(validator);
                yield decoder;
            }
            case "inline" -> {
                if (!StringUtils.hasText(inlineJwkSet)) {
                    throw new IllegalStateException("JWT JWK source is inline but app.security.jwt.jwk-set is empty");
                }
                yield new RefreshingJwkSetJwtDecoder(
                        () -> inlineJwkSet,
                        parseJwsAlgorithms(jwsAlgorithms),
                        jwkRefreshInterval,
                        validator
                );
            }
            case "dynamo" -> {
                DynamoJwkSetReader reader = dynamoJwkSetReaderProvider.getIfAvailable(() -> {
                    throw new IllegalStateException("JWT JWK source is dynamo but no DynamoJwkSetReader is configured");
                });
                yield new RefreshingJwkSetJwtDecoder(
                        reader::getJwkSet,
                        parseJwsAlgorithms(jwsAlgorithms),
                        jwkRefreshInterval,
                        validator
                );
            }
            default -> throw new IllegalStateException("Unsupported JWT JWK source: " + jwkSource);
        };
    }

    private OAuth2TokenValidator<Jwt> jwtValidator() {
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = token -> {
            if (!StringUtils.hasText(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            List<String> audiences = token.getAudience();
            if (audiences != null && audiences.contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid audience", null)
            );
        };

        return new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience);
    }

    private String normalizeJwkSource(String source) {
        if (!StringUtils.hasText(source)) {
            return "issuer";
        }
        String normalized = source.trim().toLowerCase();
        if ("jwks-uri".equals(normalized)) {
            return "jwk-set-uri";
        }
        if ("jwk-set".equals(normalized)) {
            return "inline";
        }
        return normalized;
    }

    private Set<JWSAlgorithm> parseJwsAlgorithms(String algorithms) {
        if (!StringUtils.hasText(algorithms)) {
            return Set.of(JWSAlgorithm.RS256);
        }
        Set<JWSAlgorithm> parsed = Arrays.stream(algorithms.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(JWSAlgorithm::parse)
                .collect(Collectors.toUnmodifiableSet());
        return parsed.isEmpty() ? Set.of(JWSAlgorithm.RS256) : parsed;
    }
}
