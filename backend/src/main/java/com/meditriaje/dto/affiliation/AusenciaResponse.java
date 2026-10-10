package com.meditriaje.dto.affiliation;

import java.time.Instant;

public record AusenciaResponse(
        String publicId,
        String profesionalPublicId,
        String profesionalNombre,
        Instant fechaInicio,
        Instant fechaFin,
        String motivo,
        String estado
) {
}
