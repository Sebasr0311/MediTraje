package com.meditriaje.model;

import java.time.Instant;

/**
 * Habitación o sala individual/compartida dentro de un área hospitalaria (Fase H, ADR-024).
 */
public record HabitacionSala(
        Long id,
        String publicId,
        Long areaId,
        String codigo,
        String tipo,
        String estado,
        Instant creadoAt
) {
}
