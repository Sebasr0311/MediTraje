package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo inmutable para la institución prestadora de salud (IPS/EPS) (ADR-003, HU-10).
 */
public record Institucion(
        Long id,
        String publicId,
        String nit,
        String razonSocial,
        String estado,
        Instant createdAt
) {
    public Institucion {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(nit, "nit no puede ser nulo");
        Objects.requireNonNull(razonSocial, "razonSocial no puede ser nulo");
        Objects.requireNonNull(estado, "estado no puede ser nulo");
    }
}
