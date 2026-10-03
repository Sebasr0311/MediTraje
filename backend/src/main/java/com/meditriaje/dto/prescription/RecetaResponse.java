package com.meditriaje.dto.prescription;

import java.time.Instant;
import java.util.List;

/**
 * DTO de respuesta para una receta médica.
 * Regla de seguridad (ADR-003): Cero exposición de identificadores autonuméricos de base de datos.
 */
public record RecetaResponse(
        String publicId,
        String atencionPublicId,
        String pacientePublicId,
        String pacienteNombre,
        String profesionalPublicId,
        String profesionalNombre,
        String especialidadNombre,
        int vigenciaDias,
        Instant createdAt,
        List<RecetaDetalleResponse> detalles
) {}
