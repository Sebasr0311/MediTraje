package com.meditriaje.dto.clinical;

import java.time.Instant;

/**
 * Representación inmutable de la autorización de acceso Break-Glass para la API (ADR-017).
 * Cero exposición de identificadores numéricos internos de base de datos.
 */
public record AccesoBreakGlassResponse(
        String publicId,
        String profesionalPublicId,
        String profesionalNombre,
        String pacientePublicId,
        String pacienteNombre,
        String motivo,
        Instant fechaExpiracion,
        Instant createdAt,
        boolean activo
) {}
