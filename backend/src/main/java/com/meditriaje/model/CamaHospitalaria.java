package com.meditriaje.model;

import java.time.Instant;

/**
 * Cama física hospitalaria con control de estado y ocupación (Fase H, ADR-024).
 * Estados: DISPONIBLE, OCUPADA, LIMPIEZA, MANTENIMIENTO, INACTIVA.
 */
public record CamaHospitalaria(
        Long id,
        String publicId,
        Long habitacionId,
        String codigo,
        String estado,
        Instant creadoAt,
        Instant updatedAt
) {
}
