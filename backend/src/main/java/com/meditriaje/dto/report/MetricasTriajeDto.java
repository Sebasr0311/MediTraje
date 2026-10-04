package com.meditriaje.dto.report;

import java.util.List;

/**
 * Métricas agregadas de triaje clínico y cortes de emergencia (F2.6, RF-30).
 */
public record MetricasTriajeDto(
        long totalTriajes,
        long nivel1,
        long nivel2,
        long nivel3,
        long nivel4,
        long nivel5,
        long emergencias,
        double tasaEmergencia,
        List<DistribucionItemDto> porRutaSugerida
) {
    public MetricasTriajeDto(
            long totalTriajes,
            long nivel1,
            long nivel2,
            long nivel3,
            long nivel4,
            long nivel5,
            long emergencias,
            double tasaEmergencia
    ) {
        this(totalTriajes, nivel1, nivel2, nivel3, nivel4, nivel5, emergencias, tasaEmergencia, List.of());
    }
}

