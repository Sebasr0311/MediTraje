package com.meditriaje.dto.report;

import java.util.List;

/**
 * Métricas agregadas de accesos clínicos de emergencia Break-Glass (F2.6, RF-30, ADR-017).
 */
public record MetricasBreakGlassDto(
        long totalActivaciones,
        long activasVigentes,
        List<DistribucionItemDto> porEspecialidad
) {}
