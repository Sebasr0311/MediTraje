package com.meditriaje.model;

/**
 * Tipos permitidos para las tareas de seguimiento post-atención (ADR-015).
 */
public enum TipoSeguimiento {
    CONTROL_MEDICO,
    EVOLUCION_SINTOMAS,
    EXAMEN_PENDIENTE,
    ADHERENCIA_TRATAMIENTO;

    public static boolean esValido(String valor) {
        if (valor == null) {
            return false;
        }
        for (TipoSeguimiento t : values()) {
            if (t.name().equalsIgnoreCase(valor.trim())) {
                return true;
            }
        }
        return false;
    }
}
