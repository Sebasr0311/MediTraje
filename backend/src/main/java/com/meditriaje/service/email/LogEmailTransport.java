package com.meditriaje.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Transporte para desarrollo/pruebas locales que no envía correos a servicios externos.
 * Logea el envío con PII enmascarada y devuelve un resultado exitoso simulado (D1, T3).
 */
public class LogEmailTransport implements EmailTransport {

    private static final Logger log = LoggerFactory.getLogger(LogEmailTransport.class);

    @Override
    public String getNombreTransporte() {
        return "log";
    }

    @Override
    public ResultadoEnvio enviar(String destinatarioEmail, String asunto, String cuerpoHtml) {
        Objects.requireNonNull(destinatarioEmail, "destinatarioEmail no puede ser nulo");
        Objects.requireNonNull(asunto, "asunto no puede ser nulo");
        Objects.requireNonNull(cuerpoHtml, "cuerpoHtml no puede ser nulo");

        String emailEnmascarado = EmailUtil.enmascararEmail(destinatarioEmail);
        log.info("[LOG_EMAIL_TRANSPORT] Correo simulado hacia [{}] con asunto [{}] (longitud cuerpo: {} chars)",
                emailEnmascarado, asunto, cuerpoHtml.length());
        return ResultadoEnvio.exitosoSimulado("log");
    }
}
