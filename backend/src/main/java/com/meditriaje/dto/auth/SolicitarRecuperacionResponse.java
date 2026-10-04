package com.meditriaje.dto.auth;

/**
 * Respuesta genérica ante solicitud de recuperación de contraseña para evitar enumeración (F2.1, ADR-014).
 */
public record SolicitarRecuperacionResponse(
        String mensaje
) {
    public static final String MENSAJE_DEFAULT =
            "Si el correo se encuentra registrado en el sistema, recibiras un codigo de 6 digitos para restablecer tu contrasena.";

    public static SolicitarRecuperacionResponse defaultResponse() {
        return new SolicitarRecuperacionResponse(MENSAJE_DEFAULT);
    }
}
