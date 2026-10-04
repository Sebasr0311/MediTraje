package com.meditriaje.dto.pharmacy;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Respuesta para un ítem dispensado en una entrega farmacéutica (F2.4, ADR-016).
 */
public record DispensacionDetalleResponse(
        String medicamentoPublicId,
        String medicamentoCodigo,
        String nombreComercial,
        int cantidadEntregada,
        String lote,
        LocalDate fechaVencimientoLote,
        Instant createdAt
) {}
