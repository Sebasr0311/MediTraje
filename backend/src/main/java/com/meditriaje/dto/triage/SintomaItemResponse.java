package com.meditriaje.dto.triage;

import java.math.BigDecimal;

/**
 * Detalle consolidado de un síntoma reportado en un triaje (ADR-009, HU-02).
 */
public record SintomaItemResponse(
        String codigo,
        String nombre,
        BigDecimal duracionHoras,
        int intensidad,
        boolean esAlarma
) {}
