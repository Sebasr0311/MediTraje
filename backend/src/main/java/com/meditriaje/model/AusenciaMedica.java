package com.meditriaje.model;

import java.time.Instant;

/**
 * Ausencia programada, permiso, incapacidad o bloqueo de agenda médica (Fase C, ADR-026).
 */
public record AusenciaMedica(
        Long id,
        String publicId,
        Long profesionalId,
        Instant fechaInicio,
        Instant fechaFin,
        String motivo,
        String estado,
        Long registradoPorUsuarioId,
        Instant creadoAt
) {
}
