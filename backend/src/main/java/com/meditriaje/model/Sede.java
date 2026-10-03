package com.meditriaje.model;

import java.util.Objects;

/**
 * Modelo inmutable para la sede física o centro de atención médica (ADR-003, HU-10).
 */
public record Sede(
        Long id,
        Long institucionId,
        String publicId,
        String nombre,
        String direccion,
        String ciudad,
        String estado
) {
    public Sede {
        Objects.requireNonNull(institucionId, "institucionId no puede ser nulo");
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(nombre, "nombre no puede ser nulo");
        Objects.requireNonNull(direccion, "direccion no puede ser nulo");
        Objects.requireNonNull(ciudad, "ciudad no puede ser nulo");
        Objects.requireNonNull(estado, "estado no puede ser nulo");
    }
}
