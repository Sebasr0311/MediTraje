package com.meditriaje.exception;

/**
 * Excepción lanzada cuando una dirección IP o cliente supera el límite de solicitudes permitidas (HTTP 429).
 */
public class LimitePeticionesExcedidoException extends MediTriajeException {

    public LimitePeticionesExcedidoException(String mensaje) {
        super("LIMITE_PETICIONES_EXCEDIDO", mensaje);
    }
}
