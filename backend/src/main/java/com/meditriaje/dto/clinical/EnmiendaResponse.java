package com.meditriaje.dto.clinical;

import java.time.Instant;

/**
 * DTO de respuesta para una enmienda médica append-only (ADR-008, HU-07, HU-09).
 */
public record EnmiendaResponse(
        String profesionalPublicId,
        String profesionalNombre,
        String motivo,
        String contenido,
        Instant fechaEnmienda
) {
}
