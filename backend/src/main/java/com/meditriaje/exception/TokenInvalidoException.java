package com.meditriaje.exception;

/**
 * Se lanza cuando un token de sesión (access o refresh) es nulo, inválido, expirado o revocado.
 * Mapeado a HTTP 401 Unauthorized.
 */
public class TokenInvalidoException extends MediTriajeException {

    public TokenInvalidoException() {
        super("TOKEN_INVALIDO", "Token de sesion invalido o expirado.");
    }

    public TokenInvalidoException(String mensaje) {
        super("TOKEN_INVALIDO", mensaje);
    }
}
