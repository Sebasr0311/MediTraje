package com.meditriaje.dto.report;

import java.util.List;

/**
 * Métricas agregadas de agendamiento y estado de citas (F2.6, RF-30).
 */
public record MetricasCitasDto(
        long totalCitas,
        long programadas,
        long confirmadas,
        long atendidas,
        long canceladas,
        long noAsistio,
        long reprogramadas,
        double tasaCumplimiento,
        double tasaCancelacion,
        List<DistribucionItemDto> porEspecialidad,
        List<DistribucionItemDto> porSede
) {}
