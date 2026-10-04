package com.meditriaje.dto.prescription;

/**
 * DTO de respuesta para un ítem del catálogo maestro de medicamentos.
 */
public record MedicamentoResponse(
        String publicId,
        String codigo,
        String nombreComercial,
        String principioActivo,
        String presentacion,
        String concentracion,
        String estado
) {}
