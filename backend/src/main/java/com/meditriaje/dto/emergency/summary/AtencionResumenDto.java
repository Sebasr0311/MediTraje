package com.meditriaje.dto.emergency.summary;

import java.time.Instant;

/**
 * Antecedente o atención médica reciente relevante para el resumen de emergencia (ADR-010, §5.17).
 */
public record AtencionResumenDto(
        Instant fechaAtencion,
        String especialidad,
        String diagnosticoCodigo,
        String diagnosticoDescripcion,
        String motivoConsulta,
        String indicaciones
) {
}
