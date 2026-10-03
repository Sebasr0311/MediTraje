package com.meditriaje.model;

import java.util.Objects;

/**
 * Modelo de dominio inmutable para el catálogo maestro de fármacos {@code MEDICAMENTO} (M7.1, HU-08).
 */
public record Medicamento(
        Long id,
        String publicId,
        String codigo,
        String nombreComercial,
        String principioActivo,
        String presentacion,
        String concentracion,
        String estado
) {
    public Medicamento {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(codigo, "codigo no puede ser nulo");
        Objects.requireNonNull(nombreComercial, "nombreComercial no puede ser nulo");
        Objects.requireNonNull(principioActivo, "principioActivo no puede ser nulo");
        Objects.requireNonNull(presentacion, "presentacion no puede ser nula");
        Objects.requireNonNull(concentracion, "concentracion no puede ser nula");
        Objects.requireNonNull(estado, "estado no puede ser nulo");
    }

    public boolean estaActivo() {
        return "ACTIVO".equalsIgnoreCase(estado);
    }
}
