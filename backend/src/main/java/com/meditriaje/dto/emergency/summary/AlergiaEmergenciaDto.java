package com.meditriaje.dto.emergency.summary;

/**
 * Registro de alergia o hipersensibilidad en el resumen de emergencia (ADR-010, §5.17).
 */
public record AlergiaEmergenciaDto(
        String sustancia,
        String reaccion,
        String severidad
) {
}
