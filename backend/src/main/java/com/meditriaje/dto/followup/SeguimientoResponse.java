package com.meditriaje.dto.followup;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Representación pública inmutable de un seguimiento post-atención (ADR-003, ADR-015).
 * Expone exclusivamente UUIDs públicos sin filtrar IDs autonuméricos internos de la base de datos.
 */
public record SeguimientoResponse(
        String publicId,
        String atencionPublicId,
        String pacientePublicId,
        String pacienteNombre,
        String profesionalPublicId,
        String profesionalNombre,
        String profesionalEspecialidad,
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
