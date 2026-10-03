package com.meditriaje.dto.triage;

/**
 * Ítem de síntoma activo del catálogo para el formulario de triaje (ADR-009, HU-02).
 */
public record CatalogoSintomaResponse(
        String publicId,
        String codigo,
        String nombre,
        String categoria,
        boolean esAlarma
) {}
