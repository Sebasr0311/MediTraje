package com.meditriaje.model;

import java.util.Objects;

/**
 * Modelo de dominio inmutable para el detalle de prescripción {@code RECETA_DETALLE} (M7.1, HU-08, ADR-008).
 * Contiene snapshot inmutable del medicamento en el instante de prescripción.
 */
public record RecetaDetalle(
        Long id,
        Long recetaId,
        Long medicamentoId,
        String snapshotNombre,
        String snapshotPrincipioActivo,
        String snapshotPresentacion,
        String snapshotConcentracion,
        String dosis,
        String frecuencia,
        int duracionDias,
        int cantidad,
        String indicaciones
) {
    public RecetaDetalle {
        Objects.requireNonNull(snapshotNombre, "snapshotNombre no puede ser nulo");
        Objects.requireNonNull(snapshotPrincipioActivo, "snapshotPrincipioActivo no puede ser nulo");
        Objects.requireNonNull(snapshotPresentacion, "snapshotPresentacion no puede ser nula");
        Objects.requireNonNull(snapshotConcentracion, "snapshotConcentracion no puede ser nula");
        Objects.requireNonNull(dosis, "dosis no puede ser nula");
        Objects.requireNonNull(frecuencia, "frecuencia no puede ser nula");
        if (duracionDias <= 0) {
            throw new IllegalArgumentException("La duracion en dias debe ser mayor a 0.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0.");
        }
    }
}
