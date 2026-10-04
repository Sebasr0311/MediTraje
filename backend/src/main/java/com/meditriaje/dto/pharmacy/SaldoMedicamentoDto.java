package com.meditriaje.dto.pharmacy;

/**
 * Representación del saldo y estado de entrega de un medicamento prescrito en una receta médica (F2.4, ADR-016).
 */
public record SaldoMedicamentoDto(
        String medicamentoPublicId,
        String medicamentoCodigo,
        String nombreComercial,
        String principioActivo,
        String presentacion,
        String concentracion,
        String dosis,
        String frecuencia,
        int duracionDias,
        int cantidadPrescrita,
        int cantidadDispensada,
        int saldoPendiente,
        String estado,
        String indicaciones
) {}
