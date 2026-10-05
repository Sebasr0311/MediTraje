package com.meditriaje.service.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Implementación principal de EmailService con soporte para Brevo SMTP y almacenamiento en buffer de auditoría.
 * Si JavaMailSender está configurado, despacha los mensajes por SMTP (STARTTLS 587); en caso de indisponibilidad
 * de red o falta de credenciales, registra el correo en buffer sin abortar las transacciones de negocio.
 */
@Service
public class DefaultEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmailService.class);
    private static final String PLANTILLA_RECUPERACION = "templates/email/recuperacion-password.html";
    private static final String PLANTILLA_BIENVENIDA = "templates/email/bienvenida-credenciales.html";

    private final EmailTemplateService templateService;
    private final JavaMailSender javaMailSender;
    private final String mailFrom;
    private final String mailFromName;
    private final String frontendUrl;

    private final ConcurrentLinkedDeque<CorreoEnviado> bufferCorreos = new ConcurrentLinkedDeque<>();

    @Autowired
    public DefaultEmailService(
            EmailTemplateService templateService,
            @Autowired(required = false) JavaMailSender javaMailSender,
            @Value("${mail.from:no-reply@meditriaje.com}") String mailFrom,
            @Value("${mail.from-name:MediTriaje 2.0}") String mailFromName,
            @Value("${app.frontend-url:http://localhost:5500}") String frontendUrl
    ) {
        this.templateService = Objects.requireNonNull(templateService, "templateService no puede ser nulo");
        this.javaMailSender = javaMailSender;
        this.mailFrom = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom : "no-reply@meditriaje.com";
        this.mailFromName = (mailFromName != null && !mailFromName.isBlank()) ? mailFromName : "MediTriaje 2.0";
        this.frontendUrl = (frontendUrl != null && !frontendUrl.isBlank()) ? frontendUrl : "http://localhost:5500";
    }

    public DefaultEmailService(EmailTemplateService templateService) {
        this(templateService, null, "no-reply@meditriaje.com", "MediTriaje 2.0", "http://localhost:5500");
    }

    @Override
    public void enviarCorreoHtml(String destinatarioEmail, String asunto, String cuerpoHtml) {
        Objects.requireNonNull(destinatarioEmail, "destinatarioEmail no puede ser nulo");
        Objects.requireNonNull(asunto, "asunto no puede ser nulo");
        Objects.requireNonNull(cuerpoHtml, "cuerpoHtml no puede ser nulo");

        CorreoEnviado correo = new CorreoEnviado(destinatarioEmail, asunto, cuerpoHtml, Instant.now());
        bufferCorreos.addFirst(correo);

        // Mantener tamaño máximo del buffer en memoria (50 últimos correos)
        while (bufferCorreos.size() > 50) {
            bufferCorreos.removeLast();
        }

        // Si JavaMailSender está disponible, despachar vía Brevo SMTP
        if (javaMailSender != null) {
            try {
                MimeMessage mimeMessage = javaMailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
                helper.setFrom(mailFrom, mailFromName);
                helper.setTo(destinatarioEmail);
                helper.setSubject(asunto);
                helper.setText(cuerpoHtml, true);

                javaMailSender.send(mimeMessage);
                log.info("Correo despachado exitosamente vía Brevo SMTP hacia [{}] con asunto [{}]", destinatarioEmail, asunto);
            } catch (Exception e) {
                // Resiliencia asistencial: capturar advertencia sin interrumpir flujo transaccional
                log.warn("Advertencia al enviar correo vía Brevo SMTP hacia [{}] (asunto: [{}]): {}",
                        destinatarioEmail, asunto, e.getMessage());
            }
        } else {
            log.info("JavaMailSender no activo. Correo simulado almacenado en buffer para destinatario [{}], asunto: [{}]",
                    destinatarioEmail, asunto);
        }
    }

    @Override
    public void enviarCodigoRecuperacion(String destinatarioEmail, String destinatarioNombre, String codigo, int minutosExpiracion) {
        Objects.requireNonNull(destinatarioEmail, "destinatarioEmail no puede ser nulo");
        Objects.requireNonNull(codigo, "codigo no puede ser nulo");

        String nombre = (destinatarioNombre != null && !destinatarioNombre.isBlank()) ? destinatarioNombre : "Usuario";

        Map<String, String> variables = Map.of(
                "nombre", nombre,
                "codigo", codigo,
                "minutosExpiracion", String.valueOf(minutosExpiracion)
        );

        String cuerpoHtml = templateService.renderizar(PLANTILLA_RECUPERACION, variables);
        String asunto = "MediTriaje 2.0 — Código de recuperación de contraseña";

        enviarCorreoHtml(destinatarioEmail, asunto, cuerpoHtml);
    }

    @Override
    public void enviarCredencialesIniciales(String destinatarioEmail, String destinatarioNombre, String rol, String passwordTemporal) {
        Objects.requireNonNull(destinatarioEmail, "destinatarioEmail no puede ser nulo");
        Objects.requireNonNull(passwordTemporal, "passwordTemporal no puede ser nula");

        String nombre = (destinatarioNombre != null && !destinatarioNombre.isBlank()) ? destinatarioNombre : "Profesional";
        String rolFormateado = (rol != null && !rol.isBlank()) ? rol : "Profesional Asistencial";
        String enlaceLogin = frontendUrl + "/#/login";

        Map<String, String> variables = Map.of(
                "nombre", nombre,
                "rol", rolFormateado,
                "email", destinatarioEmail,
                "passwordTemporal", passwordTemporal,
                "enlaceLogin", enlaceLogin
        );

        String cuerpoHtml = templateService.renderizar(PLANTILLA_BIENVENIDA, variables);
        String asunto = "MediTriaje 2.0 — Tus credenciales de acceso institucional";

        enviarCorreoHtml(destinatarioEmail, asunto, cuerpoHtml);
    }

    /**
     * Retorna el último correo despachado por el servicio (útil para pruebas).
     */
    public Optional<CorreoEnviado> obtenerUltimoCorreoEnviado() {
        return Optional.ofNullable(bufferCorreos.peekFirst());
    }

    /**
     * Limpia el buffer en memoria de correos despachados.
     */
    public void limpiarBuffer() {
        bufferCorreos.clear();
    }
}
