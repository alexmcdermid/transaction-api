package com.transactionapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

import java.util.List;

@Configuration
public class JwtConfig {

    @Value("${app.security.jwt.issuer-uri:}")
    private String issuerUri;

    @Value("${app.security.jwt.audience:}")
    private String audience;

    @Value("${app.security.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${app.security.jwt.jwk-set:}")
    private String jwkSetJson;

    @Bean
    @ConditionalOnProperty(value = "app.security.jwt.enabled", havingValue = "true")
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = buildDecoder();

        OAuth2TokenValidator<Jwt> withIssuer = StringUtils.hasText(issuerUri)
                ? JwtValidators.createDefaultWithIssuer(issuerUri)
                : JwtValidators.createDefault();
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

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    private NimbusJwtDecoder buildDecoder() {
        if (StringUtils.hasText(jwkSetJson)) {
            try {
                JWKSet jwkSet = JWKSet.parse(jwkSetJson);
                ImmutableJWKSet<SecurityContext> jwkSource = new ImmutableJWKSet<>(jwkSet);
                DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
                jwtProcessor.setJWSKeySelector(JWSAlgorithmFamilyJWSKeySelector.fromJWKSource(jwkSource));
                return new NimbusJwtDecoder(jwtProcessor);
            } catch (Exception ex) {
                throw new IllegalStateException("Invalid JWKS JSON", ex);
            }
        }
        if (StringUtils.hasText(jwkSetUri)) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }
        if (!StringUtils.hasText(issuerUri)) {
            throw new IllegalStateException("JWT enabled but no issuer or JWKS configured");
        }
        return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    }
}
