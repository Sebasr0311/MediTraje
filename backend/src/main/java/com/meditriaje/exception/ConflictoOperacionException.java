package com.meditriaje.exception;

/**
 * Se lanza ante conflictos de estado o incompatibilidad operacional en recursos (HTTP 409 Conflict).
 * Ejemplo: Intentar cancelar o marcar inasistencia en una cita con atención clínica ya vinculada.
 */
public class ConflictoOperacionException extends MediTriajeException {

    public ConflictoOperacionException(String mensaje) {
        super("CONFLICTO_OPERACION", mensaje);
    }
}
