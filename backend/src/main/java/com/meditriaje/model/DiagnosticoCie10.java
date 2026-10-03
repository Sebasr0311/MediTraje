package com.meditriaje.model;

import java.util.Objects;

/**
 * Modelo de dominio inmutable para el catálogo de patologías {@code DIAGNOSTICO_CIE10} (ADR-013).
 */
public record DiagnosticoCie10(
        Long id,
        String codigo,
        String descripcion,
        String estado
) {
    public DiagnosticoCie10 {
        Objects.requireNonNull(codigo, "codigo no puede ser nulo");
        Objects.requireNonNull(descripcion, "descripcion no puede ser nulo");
        Objects.requireNonNull(estado, "estado no puede ser nulo");
    }

    public boolean estaActivo() {
        return "ACTIVO".equalsIgnoreCase(estado);
    }
}
