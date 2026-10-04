package com.meditriaje.service.email;

import java.time.Instant;

/**
 * Representación inmutable de un correo electrónico despachado por el servicio.
 */
public record CorreoEnviado(
        String destinatario,
        String asunto,
        String cuerpoHtml,
        Instant fechaEnvio
) {
}
