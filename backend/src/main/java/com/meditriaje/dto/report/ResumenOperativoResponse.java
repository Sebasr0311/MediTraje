package com.meditriaje.dto.report;

import java.time.Instant;

/**
 * Resumen consolidado de indicadores y métricas operativas hospitalarias (F2.6, RF-30, ADR-018).
 * Cero datos clínicos personales ni identificables de pacientes (ADR-007).
 */
public record ResumenOperativoResponse(
        Instant fechaDesde,
        Instant fechaHasta,
        MetricasCitasDto citas,
        MetricasTriajeDto triaje,
        MetricasFarmaciaDto farmacia,
        MetricasBreakGlassDto breakGlass,
        Instant generadoAt
) {}
