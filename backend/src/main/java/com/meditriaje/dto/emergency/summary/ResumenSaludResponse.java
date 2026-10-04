package com.meditriaje.dto.emergency.summary;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta consolidada del resumen clínico de salud para emergencias (ADR-010, §5.17, §5.18).
 */
public record ResumenSaludResponse(
        PacienteEmergenciaDto paciente,
        List<AlergiaEmergenciaDto> alergias,
        List<MedicamentoActivoDto> medicamentosActivos,
        List<AtencionResumenDto> atencionesRecientes,
        Instant generadoAt,
        String advertenciaLegal
) {
}
