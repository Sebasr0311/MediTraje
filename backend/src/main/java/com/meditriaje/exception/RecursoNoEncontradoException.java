package com.meditriaje.exception;

/**
 * Se lanza cuando un recurso solicitado no existe en el sistema.
 * Mapeado a HTTP 404.
 */
public class RecursoNoEncontradoException extends MediTriajeException {

    public RecursoNoEncontradoException(String recurso) {
        super("RECURSO_NO_ENCONTRADO", "El recurso solicitado no fue encontrado: " + recurso);
    }
}
