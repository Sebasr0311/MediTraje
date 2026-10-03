package com.meditriaje.dto.prescription;

/**
 * DTO de respuesta para un ítem prescrito en una receta médica.
 * Preserva los datos del snapshot inmutable de medicamento.
 */
public record RecetaDetalleResponse(
        String medicamentoPublicId,
        String medicamentoCodigo,
        String nombreComercial,
        String principioActivo,
        String presentacion,
        String concentracion,
        String dosis,
        String frecuencia,
        int duracionDias,
        int cantidad,
        String indicaciones
) {}
