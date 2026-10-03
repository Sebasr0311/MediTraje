package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para la prescripción médica {@code RECETA} (M7.1, HU-08, ADR-008).
 * Inmutable desde su inserción.
 */
public record Receta(
        Long id,
        String publicId,
        Long atencionId,
        Long pacienteId,
        Long profesionalId,
        int vigenciaDias,
        Instant createdAt
) {
    public Receta {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(atencionId, "atencionId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        if (vigenciaDias <= 0) {
            throw new IllegalArgumentException("La vigencia de la receta debe ser mayor a 0 dias.");
        }
    }
}
