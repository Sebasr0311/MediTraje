package com.meditriaje.model;

import java.util.Objects;

/**
 * Modelo de dominio inmutable para el catálogo de síntomas (ADR-009, HU-02).
 */
public record Sintoma(
        Long id,
        String publicId,
        String codigo,
        String nombre,
        String categoria,
        boolean esAlarma,
        String estado
) {
    public Sintoma {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(codigo, "codigo no puede ser nulo");
        Objects.requireNonNull(nombre, "nombre no puede ser nulo");
        Objects.requireNonNull(categoria, "categoria no puede ser nula");
        Objects.requireNonNull(estado, "estado no puede ser nulo");
    }
}
