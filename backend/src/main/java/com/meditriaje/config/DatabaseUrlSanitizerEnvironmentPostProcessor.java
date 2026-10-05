package com.meditriaje.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Sanitiza automáticamente las variables de conexión a base de datos (DB_URL, spring.datasource.url)
 * eliminando saltos de línea (\r, \n), espacios y tabuladores accidentales introducidos
 * por interfaces web de despliegue cloud (como Render, Heroku o Railway) al copiar y pegar.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseUrlSanitizerEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUrlSanitizerEnvironmentPostProcessor.class);

    private static final String[] URL_KEYS = {
        "DB_URL",
        "spring.datasource.url",
        "spring.flyway.url",
        "SPRING_DATASOURCE_URL",
        "FLYWAY_URL"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> sanitizedProps = new HashMap<>();

        for (String key : URL_KEYS) {
            String value = environment.getProperty(key);
            if (value != null && !value.isBlank()) {
                String sanitized = value.replaceAll("\\s+", "");
                if (!sanitized.equals(value)) {
                    log.info("Sanitizando propiedad '{}': removidos saltos de línea o espacios accidentales.", key);
                    sanitizedProps.put(key, sanitized);
                }
            }
        }

        // Si DB_URL fue sanitizado o provisto, asegurar que spring.datasource.url y spring.flyway.url también queden limpios
        String activeDbUrl = (String) sanitizedProps.get("DB_URL");
        if (activeDbUrl == null) {
            String rawDbUrl = environment.getProperty("DB_URL");
            if (rawDbUrl != null && !rawDbUrl.isBlank()) {
                activeDbUrl = rawDbUrl.replaceAll("\\s+", "");
            }
        }
        if (activeDbUrl != null && !activeDbUrl.isBlank()) {
            sanitizedProps.putIfAbsent("spring.datasource.url", activeDbUrl);
            sanitizedProps.putIfAbsent("spring.flyway.url", activeDbUrl);
        }

        if (!sanitizedProps.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("sanitizedDatabaseProperties", sanitizedProps));
        }
    }
}
