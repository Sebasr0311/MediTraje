package com.meditriaje.exception;

/**
 * Se lanza cuando un horario de cita ya no está disponible (doble reserva).
 * Mapeado a HTTP 409 Conflict.
 */
public class CitaNoDisponibleException extends MediTriajeException {

    public CitaNoDisponibleException() {
        super("CITA_NO_DISPONIBLE", "El horario solicitado ya no esta disponible.");
    }
}
