package com.meditriaje.model;

/**
 * Estados del ciclo de vida de un seguimiento post-atención (ADR-015).
 */
public enum EstadoSeguimiento {
    PENDIENTE,
    COMPLETADO,
    CANCELADO;

    public static boolean esValido(String valor) {
        if (valor == null) {
            return false;
        }
        for (EstadoSeguimiento e : values()) {
            if (e.name().equalsIgnoreCase(valor.trim())) {
                return true;
            }
        }
        return false;
    }
}
