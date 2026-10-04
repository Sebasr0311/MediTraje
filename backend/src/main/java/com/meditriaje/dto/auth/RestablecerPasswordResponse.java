package com.meditriaje.dto.auth;

/**
 * Respuesta tras restablecer exitosamente la contraseña (F2.1, ADR-014).
 */
public record RestablecerPasswordResponse(
        String mensaje
) {
    public static final String MENSAJE_DEFAULT =
            "Contrasena restablecida exitosamente. Ahora puedes iniciar sesion con tu nueva clave.";

    public static RestablecerPasswordResponse defaultResponse() {
        return new RestablecerPasswordResponse(MENSAJE_DEFAULT);
    }
}
