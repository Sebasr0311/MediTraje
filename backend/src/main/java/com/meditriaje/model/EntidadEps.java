package com.meditriaje.model;

import java.time.Instant;

/**
 * Entidad Promotora de Salud (EPS) aseguradora colombiana (Fase A, ADR-025).
 */
public record EntidadEps(
        Long id,
        String publicId,
        String codigoMinSalud,
        String nombre,
        String nit,
        String regimenHabitual,
        String estado,
        Instant creadoAt
) {
}
