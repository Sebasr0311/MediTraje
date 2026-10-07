package com.meditriaje.dto.emergency.summary;

/**
 * Registro de alergia o hipersensibilidad en el resumen de emergencia (ADR-010, §5.17, T4).
 */
public record AlergiaEmergenciaDto(
        String sustancia,
        String reaccion,
        String severidad,
        String origen
) {
    /**
     * Constructor de conveniencia compatible hacia atrás (V008).
     */
    public AlergiaEmergenciaDto(String sustancia, String reaccion, String severidad) {
        this(sustancia, reaccion, severidad, "PROFESIONAL");
    }
}
