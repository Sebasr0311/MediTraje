package com.meditriaje.dto.operational;

import java.util.List;
import java.util.Map;

public record DashboardHospitalarioResponse(
        String sedePublicId,
        String sedeNombre,
        int totalCamas,
        int camasDisponibles,
        int camasOcupadas,
        int camasLimpieza,
        int camasMantenimiento,
        double tasaOcupacionPorcentaje,
        int episodiosUrgenciasActivos,
        Map<String, Long> episodiosPorNivelTriaje,
        Map<String, Double> tiemposPromedioEsperaMinutos,
        List<AlertaOperativaResponse> alertasActivas
) {
}
