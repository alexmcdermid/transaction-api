package com.transactionapi.security;

import com.transactionapi.constants.ApiPaths;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final HeaderUserAuthenticationFilter headerUserAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final LegalAgreementFilter legalAgreementFilter;
    private final Environment environment;

    @Value("${app.security.jwt.enabled:false}")
    private boolean jwtEnabled;

    @Value("${app.cors.allowed-origins:}")
    private String[] allowedOrigins;

    @Value("${app.security.allow-header-auth:false}")
    private boolean allowHeaderAuth;

    @Value("${app.security.header-name:X-User-Id}")
    private String headerName;

    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;

    public SecurityConfig(
            HeaderUserAuthenticationFilter headerUserAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter,
            LegalAgreementFilter legalAgreementFilter,
            ObjectProvider<JwtDecoder> jwtDecoderProvider,
            Environment environment
    ) {
        this.headerUserAuthenticationFilter = headerUserAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.legalAgreementFilter = legalAgreementFilter;
        this.jwtDecoderProvider = jwtDecoderProvider;
        this.environment = environment;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf
                .csrfTokenRepository(cookieCsrfTokenRepository())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(csrfProtectionMatcher())
        );
        http.cors(Customizer.withDefaults());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/", ApiPaths.HEALTH, ApiPaths.AUTH_CSRF, ApiPaths.AUTH_LOGIN, ApiPaths.AUTH_LOGOUT).permitAll()
                .requestMatchers(HttpMethod.GET, ApiPaths.SHARES + "/*").permitAll()
                .anyRequest().authenticated()
        );
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
        );
        if (jwtEnabled) {
            JwtDecoder decoder = jwtDecoderProvider.getIfAvailable(() -> {
                throw new IllegalStateException("JWT enabled but no JwtDecoder configured");
            });
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(decoder)));
        }
        http.addFilterBefore(headerUserAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(rateLimitingFilter, HeaderUserAuthenticationFilter.class);
        http.addFilterAfter(legalAgreementFilter, RateLimitingFilter.class);
        return http.build();
    }

    private CookieCsrfTokenRepository cookieCsrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        return repository;
    }

    private RequestMatcher csrfProtectionMatcher() {
        return request -> {
            String method = request.getMethod();
            if ("GET".equals(method) || "HEAD".equals(method) || "TRACE".equals(method) || "OPTIONS".equals(method)) {
                return false;
            }
            if (StringUtils.hasText(request.getHeader("Authorization"))) {
                return false;
            }
            if (allowHeaderAuth
                    && environment.acceptsProfiles(Profiles.of("local", "test"))
                    && StringUtils.hasText(request.getHeader(headerName))) {
                return false;
            }
            return true;
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-User-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
