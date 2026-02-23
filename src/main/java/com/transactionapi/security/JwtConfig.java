package com.transactionapi.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.transactionapi.service.DynamoJwtJwkSetReader;
import java.text.ParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Value("${app.security.jwt.issuer-uri:https://accounts.google.com}")
    private String issuerUri;

    @Value("${app.security.jwt.audience:}")
    private String audience;

    @Value("${app.security.jwt.jwk-source:issuer}")
    private String jwkSource;

    @Value("${app.security.jwt.jwk-set:}")
    private String jwkSetJson;

    @Value("${app.security.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    private final ObjectProvider<DynamoJwtJwkSetReader> dynamoJwtJwkSetReaderProvider;

    public JwtConfig(ObjectProvider<DynamoJwtJwkSetReader> dynamoJwtJwkSetReaderProvider) {
        this.dynamoJwtJwkSetReaderProvider = dynamoJwtJwkSetReaderProvider;
    }

    @Bean
    @ConditionalOnProperty(value = "app.security.jwt.enabled", havingValue = "true")
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder;
        if (StringUtils.hasText(jwkSetJson)) {
            log.info("Configuring JWT decoder from inline JWK set");
            decoder = buildDecoderFromJwkSet(jwkSetJson);
        } else if ("dynamo".equalsIgnoreCase(jwkSource)) {
            log.info("Configuring JWT decoder from DynamoDB JWK set (issuer={})", issuerUri);
            DynamoJwtJwkSetReader reader = dynamoJwtJwkSetReaderProvider.getIfAvailable(() -> {
                throw new IllegalStateException("JWT JWK source is 'dynamo' but no DynamoJwtJwkSetReader is configured");
            });
            String dynamoJwkSetJson = reader.getJwkSetJson();
            if (!StringUtils.hasText(dynamoJwkSetJson)) {
                throw new IllegalStateException("JWT JWK source is 'dynamo' but no usable JWK set JSON was found");
            }
            decoder = buildDecoderFromJwkSet(dynamoJwkSetJson);
        } else if ("jwk-set-uri".equalsIgnoreCase(jwkSource) && StringUtils.hasText(jwkSetUri)) {
            log.info("Configuring JWT decoder from JWK set URI {}", jwkSetUri);
            decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        } else {
            log.info("Configuring JWT decoder from issuer discovery {}", issuerUri);
            decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        }

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

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    private NimbusJwtDecoder buildDecoderFromJwkSet(String rawJwkSetJson) {
        try {
            JWKSet parsed = JWKSet.parse(rawJwkSetJson);
            ImmutableJWKSet<SecurityContext> jwkSource = new ImmutableJWKSet<>(parsed);
            JWSKeySelector<SecurityContext> keySelector = JWSAlgorithmFamilyJWSKeySelector.fromJWKSource(jwkSource);
            DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSKeySelector(keySelector);
            return new NimbusJwtDecoder(jwtProcessor);
        } catch (ParseException ex) {
            throw new IllegalStateException("Invalid JWT JWK set JSON", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize JWT decoder from JWK set", ex);
        }
    }
}
