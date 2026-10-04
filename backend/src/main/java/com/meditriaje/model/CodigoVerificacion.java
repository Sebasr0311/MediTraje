package com.meditriaje.model;

import java.time.Instant;

/**
 * Entidad de dominio inmutable que representa un código numérico OTP de verificación (F2.1, ADR-014).
 * Almacena el hash SHA-256 del código para recuperación de contraseña y enrolamiento MFA.
 */
public record CodigoVerificacion(
        Long id,
        String publicId,
        Long usuarioId,
        String tipo,
        String codigoHash,
        Instant fechaExpiracion,
        int intentosFallidos,
        int maxIntentos,
        boolean usado,
        Instant createdAt
) {
    public static final String TIPO_RECUPERACION_PASSWORD = "RECUPERACION_PASSWORD";

    public boolean estaExpirado(Instant ahora) {
        return fechaExpiracion != null && ahora.isAfter(fechaExpiracion);
    }

    public boolean alcanzoMaxIntentos() {
        return intentosFallidos >= maxIntentos;
    }

    public boolean esValido(Instant ahora) {
        return !usado && !estaExpirado(ahora) && !alcanzoMaxIntentos();
    }
}
