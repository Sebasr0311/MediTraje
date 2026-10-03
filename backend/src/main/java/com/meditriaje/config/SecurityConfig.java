package com.meditriaje.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.security.CsrfHeaderFilter;
import com.meditriaje.security.CustomAccessDeniedHandler;
import com.meditriaje.security.CustomAuthenticationEntryPoint;
import com.meditriaje.security.JwtAuthenticationFilter;
import com.meditriaje.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Objects;

/**
 * Configuración central de Spring Security (ADR-002, M2.5).
 * - Autenticación Stateless basada en JWT (cookie HttpOnly y header Bearer).
 * - Protección CSRF por cabecera personalizada (X-Requested-With) y SameSite=Strict.
 * - Autorización declarativa por roles con @PreAuthorize.
 * - Respuestas de error 401 y 403 formateadas como ApiError sin stack traces.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            CorsConfigurationSource corsConfigurationSource,
            JwtService jwtService,
            ObjectMapper objectMapper
    ) {
        this.corsConfigurationSource = Objects.requireNonNull(corsConfigurationSource, "CorsConfigurationSource no puede ser nulo");
        this.jwtService = Objects.requireNonNull(jwtService, "JwtService no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper no puede ser nulo");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CsrfHeaderFilter csrfHeaderFilter = new CsrfHeaderFilter(objectMapper);
        JwtAuthenticationFilter jwtAuthFilter = new JwtAuthenticationFilter(jwtService);
        CustomAuthenticationEntryPoint authenticationEntryPoint = new CustomAuthenticationEntryPoint(objectMapper);
        CustomAccessDeniedHandler accessDeniedHandler = new CustomAccessDeniedHandler(objectMapper);

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/ping", "/api/v1/auth/**", "/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(csrfHeaderFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
