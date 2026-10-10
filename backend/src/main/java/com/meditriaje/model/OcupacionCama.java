package com.meditriaje.model;

import java.time.Instant;

/**
 * Registro de asignación longitudinal de una cama a un episodio de atención (Fase H, ADR-024).
 * Cumple la regla de ocupación única por intervalo.
 */
public record OcupacionCama(
        Long id,
        String publicId,
        Long episodioId,
        Long camaId,
        Instant inicioAt,
        Instant finAt,
        String estado,
        Long asignadoPorUsuarioId,
        String motivoAsignacion,
        Instant creadoAt
) {
}
