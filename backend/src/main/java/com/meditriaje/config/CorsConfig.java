package com.meditriaje.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración de CORS robusta y flexible para despliegues locales, staging y producción.
 * Los orígenes adicionales se inyectan desde la variable de entorno CORS_ORIGINS
 * (lista separada por comas, ej: https://meditriaje.com,http://localhost:5500).
 */
@Configuration
public class CorsConfig {

    @Value("${cors.origins:http://localhost:5500,http://127.0.0.1:5500}")
    private String corsOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> patterns = new ArrayList<>();
        if (corsOrigins != null && !corsOrigins.isBlank()) {
            for (String origin : corsOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    patterns.add(trimmed);
                }
            }
        }

        // Orígenes permitidos estándar (desarrollo local en cualquier puerto, preview y dominios institucionales)
        List<String> defaultPatterns = List.of(
                "http://localhost:[*]",
                "http://localhost:*",
                "http://localhost",
                "http://127.0.0.1:[*]",
                "http://127.0.0.1:*",
                "http://127.0.0.1",
                "https://*.github.io",
                "https://*.vercel.app",
                "https://*.onrender.com",
                "https://*.netlify.app",
                "https://meditriaje.com",
                "https://*.meditriaje.com"
        );
        for (String def : defaultPatterns) {
            if (!patterns.contains(def)) {
                patterns.add(def);
            }
        }

        config.setAllowedOriginPatterns(patterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Set-Cookie", "Authorization", "X-CSRF-Protection", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
