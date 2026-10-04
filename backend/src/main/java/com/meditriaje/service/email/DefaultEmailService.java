package com.meditriaje.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Implementación principal de EmailService.
 * En entornos de desarrollo y pruebas, almacena los correos renderizados en memoria y registra el evento en log.
 */
@Service
public class DefaultEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmailService.class);
    private static final String PLANTILLA_RECUPERACION = "templates/email/recuperacion-password.html";

    private final EmailTemplateService templateService;
    private final ConcurrentLinkedDeque<CorreoEnviado> bufferCorreos = new ConcurrentLinkedDeque<>();

    public DefaultEmailService(EmailTemplateService templateService) {
        this.templateService = Objects.requireNonNull(templateService, "templateService no puede ser nulo");
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

        CorreoEnviado correo = new CorreoEnviado(destinatarioEmail, asunto, cuerpoHtml, Instant.now());
        bufferCorreos.addFirst(correo);

        // Mantener tamaño máximo del buffer en memoria (50 últimos correos)
        while (bufferCorreos.size() > 50) {
            bufferCorreos.removeLast();
        }

        log.info("Correo de recuperacion despachado exitosamente hacia destinatario [{}], expiracion {} min", destinatarioEmail, minutosExpiracion);
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
