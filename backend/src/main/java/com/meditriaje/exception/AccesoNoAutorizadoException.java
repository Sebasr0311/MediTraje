package com.meditriaje.exception;

/**
 * Se lanza cuando un usuario autenticado intenta acceder a un recurso
 * al que no tiene autorización. Mapeado a HTTP 403.
 *
 * <p>Distinción clave: autenticado != autorizado.
 * Un paciente puede estar autenticado pero no autorizado a ver datos de otro paciente.</p>
 */
public class AccesoNoAutorizadoException extends MediTriajeException {

    public AccesoNoAutorizadoException() {
        super("ACCESO_NO_AUTORIZADO", "No tiene permisos para realizar esta operacion.");
    }
}
