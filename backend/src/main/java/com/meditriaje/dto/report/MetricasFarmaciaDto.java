package com.meditriaje.dto.report;

/**
 * Métricas agregadas de recetas y dispensaciones farmacéuticas (F2.6, RF-30).
 */
public record MetricasFarmaciaDto(
        long totalRecetas,
        long pendientes,
        long dispensadasParcial,
        long dispensadasTotal,
        long totalUnidadesDispensadas
) {}
