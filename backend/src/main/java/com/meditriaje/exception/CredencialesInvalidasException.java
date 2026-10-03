package com.meditriaje.exception;

/**
 * Se lanza cuando la autenticación falla (credenciales incorrectas o cuenta bloqueada).
 * Mapeado a HTTP 401 Unauthorized.
 * Nunca revela si el correo existe o no en el sistema (HU-01).
 */
public class CredencialesInvalidasException extends MediTriajeException {

    public CredencialesInvalidasException() {
        super("CREDENCIALES_INVALIDAS", "Credenciales invalidas.");
    }

    public CredencialesInvalidasException(String mensaje) {
        super("CREDENCIALES_INVALIDAS", mensaje);
    }
}
