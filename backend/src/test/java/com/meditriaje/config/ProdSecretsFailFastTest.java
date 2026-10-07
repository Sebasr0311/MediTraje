package com.meditriaje.config;

import com.meditriaje.MediTriajeApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdSecretsFailFastTest {

    private Map<String, Object> baseProdProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("spring.profiles.active", "prod");
        props.put("DB_URL", "jdbc:oracle:thin:@sa-bogota-1.oraclecloud.com:1522/service");
        props.put("DB_USER", "MEDITRIAJE_APP");
        props.put("DB_PASSWORD", "SecretDbPassword123!");
        props.put("CORS_ORIGINS", "https://meditriaje.com");
        props.put("JWT_SECRET", "super-secret-jwt-key-that-is-at-least-256-bits-long-for-production");
        props.put("FRONTEND_URL", "https://meditriaje.com");
        props.put("FLYWAY_ENABLED", "false");
        props.put("BREVO_API_KEY", "xkeysib-test-fake-key-for-unit-tests-1234567890");
        props.put("MAIL_FROM", "notificaciones@meditriaje.com");
        props.put("MAIL_FROM_NAME", "MediTriaje 2.0");
        // Evita intentos de conexión de red reales de Hikari en el arranque del test
        props.put("spring.datasource.hikari.initialization-fail-timeout", "-1");
        return props;
    }

    @ParameterizedTest(name = "Prod debe fallar al arrancar si falta {0}")
    @ValueSource(strings = {
            "JWT_SECRET",
            "DB_URL",
            "DB_USER",
            "DB_PASSWORD",
            "CORS_ORIGINS",
            "BREVO_API_KEY",
            "MAIL_FROM",
            "MAIL_FROM_NAME"
    })
    @DisplayName("Debe fallar al arrancar en perfil prod si falta cualquier secreto o variable obligatoria")
    void debeFallarAlArrancarEnProdSiFaltaSecreto(String variableFaltante) {
        Map<String, Object> props = baseProdProperties();
        props.remove(variableFaltante);

        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(MediTriajeApplication.class)
                    .profiles("prod")
                    .properties(props)
                    .run()) {
                // No debe llegar aquí
            }
        }).satisfies(ex -> {
            boolean mentionsVariable = false;
            Throwable current = ex;
            StringBuilder causes = new StringBuilder();
            while (current != null) {
                if (current.getMessage() != null) {
                    causes.append(" -> ").append(current.getMessage());
                    if (current.getMessage().contains(variableFaltante)) {
                        mentionsVariable = true;
                        break;
                    }
                }
                current = current.getCause();
            }
            assertThat(mentionsVariable)
                    .as("La excepción debe indicar que la variable " + variableFaltante + " no pudo resolverse en perfil prod. Causas observadas: " + causes)
                    .isTrue();
        });
    }
}
