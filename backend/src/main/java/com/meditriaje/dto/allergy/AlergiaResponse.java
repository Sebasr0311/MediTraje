package com.meditriaje.dto.allergy;

import java.time.Instant;

/**
 * Respuesta DTO para alergia clínica o autorreportada (D3, T4).
 * Cero exposición de identificadores numéricos de base de datos.
 */
public record AlergiaResponse(
        String publicId,
        String pacientePublicId,
        String sustancia,
        String reaccion,
        String severidad,
        String estado,
        String origen,
        boolean autorreportada,
        String atencionPublicId,
        Instant createdAt,
        Instant fechaInactivacion,
        String motivoInactivacion
) {
}
