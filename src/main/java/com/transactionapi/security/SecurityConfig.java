package com.transactionapi.security;

import com.transactionapi.constants.ApiPaths;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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

    @Value("${app.security.jwt.enabled:false}")
    private boolean jwtEnabled;

    @Value("${app.cors.allowed-origins:}")
    private String[] allowedOrigins;

    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;

    public SecurityConfig(
            HeaderUserAuthenticationFilter headerUserAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter,
            ObjectProvider<JwtDecoder> jwtDecoderProvider
    ) {
        this.headerUserAuthenticationFilter = headerUserAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.jwtDecoderProvider = jwtDecoderProvider;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.cors(Customizer.withDefaults());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(ApiPaths.HEALTH).permitAll()
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
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
