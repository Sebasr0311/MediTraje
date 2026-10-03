package com.meditriaje.dto.admin;

import java.time.Instant;

/**
 * DTO para la representación pública de un slot de disponibilidad médica (HU-10, ADR-003, ADR-005, ADR-006).
 */
public record SlotResponse(
        String publicId,
        String profesionalPublicId,
        String profesionalNombre,
        String sedePublicId,
        String sedeNombre,
        String especialidadPublicId,
        String especialidadNombre,
        Instant fechaHoraInicio,
        Instant fechaHoraFin,
        String modalidad,
        String estado
) {
}
