package com.meditriaje.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para el detalle de entrega farmacéutica {@code DISPENSACION_DETALLE} (F2.4, ADR-016).
 * Inmutable tras su inserción.
 */
public record DispensacionDetalle(
        Long id,
        Long dispensacionId,
        Long recetaDetalleId,
        int cantidadEntregada,
        String lote,
        LocalDate fechaVencimientoLote,
        Instant createdAt
) {
    public DispensacionDetalle {
        Objects.requireNonNull(recetaDetalleId, "recetaDetalleId no puede ser nulo");
        if (cantidadEntregada <= 0) {
            throw new IllegalArgumentException("La cantidad entregada debe ser mayor a 0.");
        }
    }
}
