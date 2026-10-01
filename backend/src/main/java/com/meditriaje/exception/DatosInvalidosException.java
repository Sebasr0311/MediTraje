package com.meditriaje.exception;

/**
 * Se lanza cuando los datos de entrada no cumplen las reglas de negocio.
 * Mapeado a HTTP 400.
 */
public class DatosInvalidosException extends MediTriajeException {

    public DatosInvalidosException(String detalle) {
        super("DATOS_INVALIDOS", detalle);
    }
}
