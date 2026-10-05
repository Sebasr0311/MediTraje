package com.meditriaje.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Implementación principal de EmailService con soporte dual para Brevo HTTP API (vía BREVO_API_KEY),
 * Brevo SMTP (vía JavaMailSender) y almacenamiento en buffer de auditoría para resiliencia.
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
    private final String brevoApiKey;
    private final ObjectMapper objectMapper;

    private final ConcurrentLinkedDeque<CorreoEnviado> bufferCorreos = new ConcurrentLinkedDeque<>();

    @Autowired
    public DefaultEmailService(
            EmailTemplateService templateService,
            @Autowired(required = false) JavaMailSender javaMailSender,
            @Value("${mail.from:${SMTP_FROM:${BREVO_SENDER_EMAIL:no-reply@meditriaje.com}}}") String mailFrom,
            @Value("${mail.from-name:${SMTP_FROM_NAME:MediTriaje 2.0}}") String mailFromName,
            @Value("${app.frontend-url:http://localhost:5500}") String frontendUrl,
            @Value("${mail.brevo-api-key:${BREVO_API_KEY:}}") String brevoApiKey,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.templateService = Objects.requireNonNull(templateService, "templateService no puede ser nulo");
        this.javaMailSender = javaMailSender;
        this.mailFrom = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom : "no-reply@meditriaje.com";
        this.mailFromName = (mailFromName != null && !mailFromName.isBlank()) ? mailFromName : "MediTriaje 2.0";
        this.frontendUrl = (frontendUrl != null && !frontendUrl.isBlank()) ? frontendUrl : "http://localhost:5500";
        this.brevoApiKey = (brevoApiKey != null && !brevoApiKey.isBlank()) ? brevoApiKey.trim() : null;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    public DefaultEmailService(EmailTemplateService templateService, JavaMailSender javaMailSender, String mailFrom, String mailFromName, String frontendUrl) {
        this(templateService, javaMailSender, mailFrom, mailFromName, frontendUrl, null, null);
    }

    public DefaultEmailService(EmailTemplateService templateService) {
        this(templateService, null, "no-reply@meditriaje.com", "MediTriaje 2.0", "http://localhost:5500", null, null);
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

        // 1. Intentar primero vía Brevo HTTP API si BREVO_API_KEY está provista
        if (despacharViaBrevoHttp(destinatarioEmail, asunto, cuerpoHtml)) {
            return;
        }

        // 2. Si no o si falla, despachar vía Brevo SMTP si JavaMailSender está configurado
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
            log.info("JavaMailSender y Brevo API Key inactivos. Correo simulado almacenado en buffer para destinatario [{}], asunto: [{}]",
                    destinatarioEmail, asunto);
        }
    }

    private boolean despacharViaBrevoHttp(String destinatarioEmail, String asunto, String cuerpoHtml) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            return false;
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            Map<String, Object> payloadMap = Map.of(
                    "sender", Map.of("name", mailFromName, "email", mailFrom),
                    "to", List.of(Map.of("email", destinatarioEmail)),
                    "subject", asunto,
                    "htmlContent", cuerpoHtml
            );

            String payloadJson = (objectMapper != null)
                    ? objectMapper.writeValueAsString(payloadMap)
                    : new ObjectMapper().writeValueAsString(payloadMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Correo despachado exitosamente vía Brevo HTTP API hacia [{}] con asunto [{}]", destinatarioEmail, asunto);
                return true;
            } else {
                log.warn("Brevo HTTP API devolvió código [{}] al enviar correo hacia [{}]: {}. Nota: Verifique que el remitente [{}] esté validado en su cuenta de Brevo.",
                        response.statusCode(), destinatarioEmail, response.body(), mailFrom);
                return false;
            }
        } catch (Exception e) {
            log.warn("Falla de conexión al despachar vía Brevo HTTP API hacia [{}]: {}", destinatarioEmail, e.getMessage());
            return false;
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
