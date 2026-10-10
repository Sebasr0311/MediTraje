package com.meditriaje.service.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Transporte de correo electrónico mediante protocolo SMTP estándar (JavaMailSender).
 * Reservado para entornos de desarrollo local o relevos SMTP donde el puerto no esté bloqueado (D1, T3).
 */
public class SmtpEmailTransport implements EmailTransport {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailTransport.class);

    private final JavaMailSender javaMailSender;
    private final String mailFrom;
    private final String mailFromName;

    public SmtpEmailTransport(JavaMailSender javaMailSender, String mailFrom, String mailFromName) {
        this.javaMailSender = javaMailSender;
        this.mailFrom = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom.trim() : "no-reply@meditriaje.com";
        this.mailFromName = (mailFromName != null && !mailFromName.isBlank()) ? mailFromName.trim() : "MediTriaje 2.0";
    }

    @Override
    public String getNombreTransporte() {
        return "smtp";
    }

    @Override
    public ResultadoEnvio enviar(String destinatarioEmail, String asunto, String cuerpoHtml) {
        Objects.requireNonNull(destinatarioEmail, "destinatarioEmail no puede ser nulo");
        Objects.requireNonNull(asunto, "asunto no puede ser nulo");
        Objects.requireNonNull(cuerpoHtml, "cuerpoHtml no puede ser nulo");

        String emailEnmascarado = EmailUtil.enmascararEmail(destinatarioEmail);

        if (javaMailSender == null) {
            log.error("Fallo al enviar correo vía SMTP: JavaMailSender no configurado. Destinatario=[{}]", emailEnmascarado);
            return ResultadoEnvio.fallido("SMTP_CONFIG_ERROR", "Servidor SMTP no configurado en la plataforma.");
        }

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom, mailFromName);
            helper.setTo(destinatarioEmail);
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml, true);

            javaMailSender.send(mimeMessage);
            log.info("Correo despachado exitosamente vía SMTP hacia [{}] con asunto [{}]", emailEnmascarado, asunto);
            return ResultadoEnvio.exitoso();
        } catch (Exception ex) {
            String sanitizedMsg = EmailUtil.sanitizarMensajeError(ex.getMessage(), null);
            log.warn("Error al despachar correo vía SMTP hacia [{}]: {}", emailEnmascarado, sanitizedMsg);
            return ResultadoEnvio.fallido("SMTP_ERROR", sanitizedMsg);
        }
    }
}
