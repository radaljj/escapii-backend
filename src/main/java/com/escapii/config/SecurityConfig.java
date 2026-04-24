package com.escapii.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security konfiguracija.
 *
 *  - CORS whitelist       → samo tvoj WordPress sajt sme da poziva API
 *  - CSRF                 → iskljuceno (REST API zastiten CORS-om)
 *  - HTTP security headers → CSP, X-Frame-Options, nosniff, Referrer-Policy
 *  - Autentikacija        → nema (MVP, bez login sistema)
 *  - Rate limiting        → videti RateLimitingFilter (5 req/IP/sat na POST /api/booking)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.cors-allowed-origin:https://escapii.com}")
    private String corsAllowedOrigin;

    /** Opciono: extra CORS origins odvojeni zarezom (samo za lokalni razvoj).
     *  U produkciji ovu env var NE postavljati. */
    @Value("${app.cors-extra-origins:}")
    private String corsExtraOrigins;

    private final RateLimitingFilter rateLimitingFilter;
    private final AdminKeyFilter     adminKeyFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .cors(cors ->
                cors.configurationSource(corsConfigurationSource()))

            .csrf(AbstractHttpConfigurer::disable)

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/**").permitAll()
                .requestMatchers("/images/**").permitAll()
                .anyRequest().denyAll()
            )

            .headers(headers -> headers
                // Inline JS i CSS su dozvoljeni za statičke stranice, fetch ka istom originu
                .contentSecurityPolicy(csp ->
                    csp.policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' https: data:; " +
                        "connect-src 'self'; " +
                        "frame-ancestors 'none'"
                    ))
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(contentType -> {})
                .referrerPolicy(referrer ->
                    referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN))
            )

            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(adminKeyFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Produkcija: samo CORS_ALLOWED_ORIGIN env var (WordPress sajt)
        // Lokalni razvoj: dodati CORS_EXTRA_ORIGINS=http://localhost:3000,http://escapii.local
        List<String> origins = new ArrayList<>();
        origins.add(corsAllowedOrigin);
        if (corsExtraOrigins != null && !corsExtraOrigins.isBlank()) {
            for (String o : corsExtraOrigins.split(",")) {
                String trimmed = o.trim();
                if (!trimmed.isEmpty()) origins.add(trimmed);
            }
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "X-Admin-Key", "X-Frontend-Url"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
