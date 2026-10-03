package com.meditriaje.model;

import java.util.Objects;

/**
 * Modelo inmutable para la especialidad médica (ADR-003, ADR-006, HU-10).
 */
public record Especialidad(
        Long id,
        String publicId,
        String nombre,
        int duracionSlotMin,
        String estado
) {
    public Especialidad {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(nombre, "nombre no puede ser nulo");
        Objects.requireNonNull(estado, "estado no puede ser nulo");
    }
}
