package com.meditriaje.model;

import java.time.Instant;

/**
 * Entidad de dominio para la cuenta de usuario del sistema.
 */
public record Usuario(
        Long id,
        String publicId,
        String email,
        String passwordHash,
        String estado,
        int intentosFallidos,
        Instant bloqueadoHasta,
        Instant createdAt
) {
    /**
     * Retorna verdadero si la cuenta está actualmente bloqueada por intentos fallidos.
     */
    public boolean estaBloqueado() {
        if ("BLOQUEADO".equalsIgnoreCase(estado)) {
            if (bloqueadoHasta == null) {
                return true;
            }
            return Instant.now().isBefore(bloqueadoHasta);
        }
        return false;
    }
}
