package com.meditriaje.dto.emergency.summary;

import java.time.Instant;

/**
 * Medicamento activo prescrito en receta vigente para el resumen de emergencia (ADR-010, §5.17).
 */
public record MedicamentoActivoDto(
        String nombre,
        String principioActivo,
        String presentacion,
        String concentracion,
        String dosis,
        String frecuencia,
        int duracionDias,
        int cantidad,
        String indicaciones,
        Instant fechaPrescripcion,
        int vigenciaDias
) {
}
