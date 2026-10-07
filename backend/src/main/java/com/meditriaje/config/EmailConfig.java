package com.meditriaje.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.service.email.BrevoApiEmailTransport;
import com.meditriaje.service.email.EmailTransport;
import com.meditriaje.service.email.LogEmailTransport;
import com.meditriaje.service.email.SmtpEmailTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Configuración dinámica del bean de transporte de correo electrónico.
 * Soporta transporte oficial por API HTTP de Brevo (HTTPS 443 en prod),
 * transporte SMTP para relevos locales, y transporte log para pruebas/desarrollo (D1, T3).
 */
@Configuration
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    @Bean
    public EmailTransport emailTransport(
            @Value("${meditriaje.mail.transport:${mail.transport:brevo-api}}") String transport,
            @Value("${meditriaje.mail.brevo.api-url:https://api.brevo.com/v3/smtp/email}") String brevoApiUrl,
            @Value("${meditriaje.mail.brevo.api-key:${mail.brevo-api-key:${BREVO_API_KEY:}}}") String brevoApiKey,
            @Value("${meditriaje.mail.from:${mail.from:${SMTP_FROM:${BREVO_SENDER_EMAIL:no-reply@meditriaje.com}}}}") String mailFrom,
            @Value("${meditriaje.mail.from-name:${mail.from-name:${SMTP_FROM_NAME:MediTriaje 2.0}}}") String mailFromName,
            @Autowired(required = false) JavaMailSender javaMailSender,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        String cleanTransport = (transport != null && !transport.isBlank()) ? transport.trim().toLowerCase() : "brevo-api";
        log.info("Inicializando transporte de correo electrónico: [{}]", cleanTransport);

        return switch (cleanTransport) {
            case "smtp" -> new SmtpEmailTransport(javaMailSender, mailFrom, mailFromName);
            case "log" -> new LogEmailTransport();
            case "brevo-api", "brevo" -> new BrevoApiEmailTransport(
                    brevoApiUrl,
                    brevoApiKey,
                    mailFrom,
                    mailFromName,
                    null,
                    objectMapper
            );
            default -> {
                log.warn("Transporte de correo desconocido '{}'. Se utilizará transporte 'log' seguro por defecto.", cleanTransport);
                yield new LogEmailTransport();
            }
        };
    }
}
