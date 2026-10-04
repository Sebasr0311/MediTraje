package com.meditriaje.model;

import java.time.Instant;

/**
 * Entidad de dominio para la cuenta de usuario del sistema (F2.1, ADR-014).
 * Incluye soporte para autenticación de dos factores (TOTP RFC 6238).
 */
public record Usuario(
        Long id,
        String publicId,
        String email,
        String passwordHash,
        String estado,
        int intentosFallidos,
        Instant bloqueadoHasta,
        Instant createdAt,
        boolean debeCambiarPassword,
        boolean mfaHabilitado,
        String mfaSecret,
        Instant mfaConfiguradoAt
) {
    public Usuario(
            Long id,
            String publicId,
            String email,
            String passwordHash,
            String estado,
            int intentosFallidos,
            Instant bloqueadoHasta,
            Instant createdAt,
            boolean debeCambiarPassword
    ) {
        this(id, publicId, email, passwordHash, estado, intentosFallidos, bloqueadoHasta, createdAt, debeCambiarPassword, false, null, null);
    }

    public Usuario(
            Long id,
            String publicId,
            String email,
            String passwordHash,
            String estado,
            int intentosFallidos,
            Instant bloqueadoHasta,
            Instant createdAt
    ) {
        this(id, publicId, email, passwordHash, estado, intentosFallidos, bloqueadoHasta, createdAt, false, false, null, null);
    }

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
