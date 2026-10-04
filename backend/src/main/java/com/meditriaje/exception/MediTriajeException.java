package com.meditriaje.exception;

/**
 * Excepción base de dominio. Todas las excepciones de negocio la extienden.
 */
public abstract class MediTriajeException extends RuntimeException {

    private final String codigo;

    protected MediTriajeException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
