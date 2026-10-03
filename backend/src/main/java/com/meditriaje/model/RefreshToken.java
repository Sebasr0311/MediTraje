package com.meditriaje.model;

import java.time.Instant;

/**
 * Entidad de dominio para el token de refresco rotativo (ADR-002).
 */
public record RefreshToken(
        Long id,
        Long usuarioId,
        String tokenHash,
        Instant expiracion,
        boolean revocado,
        Instant createdAt
) {
    /**
     * Retorna verdadero si el token ha superado su fecha de expiración.
     */
    public boolean estaExpirado() {
        return Instant.now().isAfter(expiracion);
    }
}
