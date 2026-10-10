package com.meditriaje.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Validador de arranque fail-fast para perfil de producción.
 * Garantiza que la aplicación aborte de forma inmediata si falta cualquier secreto
 * o variable de infraestructura obligatoria, impidiendo arranques inseguros con defaults.
 */
@Configuration
@Profile("prod")
public class ProdEnvironmentValidator {

    private static final Logger log = LoggerFactory.getLogger(ProdEnvironmentValidator.class);

    private final Environment environment;

    public ProdEnvironmentValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateRequiredProdProperties() {
        List<String> requiredVars = new ArrayList<>(List.of(
                "JWT_SECRET",
                "DB_URL",
                "DB_USER",
                "DB_PASSWORD",
                "CORS_ORIGINS"
        ));

        for (String var : requiredVars) {
            String val = environment.getProperty(var);
            if (val == null || val.isBlank() || val.equals("${" + var + "}")) {
                String errorMsg = "Configuracion de produccion invalida: La variable obligatoria '" + var + "' no esta definida o esta vacia.";
                log.error("FAIL-FAST EN PRODUCCION: {}", errorMsg);
                throw new IllegalStateException(errorMsg);
            }
        }

        String mailTransport = environment.getProperty("meditriaje.mail.transport",
                environment.getProperty("MAIL_TRANSPORT", "brevo-api")).trim().toLowerCase();

        if ("brevo-api".equals(mailTransport) || "brevo".equals(mailTransport)) {
            String brevoApiKey = environment.getProperty("BREVO_API_KEY",
                    environment.getProperty("meditriaje.mail.brevo.api-key"));
            if (brevoApiKey == null || brevoApiKey.isBlank() || brevoApiKey.startsWith("${")) {
                String errorMsg = "Configuracion de produccion invalida: La variable obligatoria 'BREVO_API_KEY' no esta definida o esta vacia.";
                log.error("FAIL-FAST EN PRODUCCION: {}", errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            String mailFrom = environment.getProperty("MAIL_FROM",
                    environment.getProperty("SMTP_FROM",
                    environment.getProperty("meditriaje.mail.from", "meditraje.admin@gmail.com")));
            if (mailFrom == null || mailFrom.isBlank() || mailFrom.startsWith("${")) {
                String errorMsg = "Configuracion de produccion invalida: La variable obligatoria 'MAIL_FROM' no esta definida o esta vacia.";
                log.error("FAIL-FAST EN PRODUCCION: {}", errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            String mailFromName = environment.getProperty("MAIL_FROM_NAME",
                    environment.getProperty("SMTP_FROM_NAME",
                    environment.getProperty("meditriaje.mail.from-name", "MediTriaje 2.0")));
            if (mailFromName == null || mailFromName.isBlank() || mailFromName.startsWith("${")) {
                String errorMsg = "Configuracion de produccion invalida: La variable obligatoria 'MAIL_FROM_NAME' no esta definida o esta vacia.";
                log.error("FAIL-FAST EN PRODUCCION: {}", errorMsg);
                throw new IllegalStateException(errorMsg);
            }
        }
    }
}
