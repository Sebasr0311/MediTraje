package com.meditriaje.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Representa una tarea o plan de seguimiento post-atención médica (ADR-015).
 * Mapea la tabla {@code SEGUIMIENTO_POST_ATENCION}.
 */
public record SeguimientoPostAtencion(
        Long id,
        String publicId,
        Long atencionId,
        Long pacienteId,
        Long profesionalId,
        String tipo,
        String indicaciones,
        LocalDate fechaSugeridaControl,
        String estado,
        Instant fechaRespuestaPaciente,
        String reportePaciente,
        Instant createdAt,
        Instant updatedAt
) {
}
