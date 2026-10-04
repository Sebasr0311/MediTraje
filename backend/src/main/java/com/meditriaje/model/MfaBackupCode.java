package com.meditriaje.model;

import java.time.Instant;

/**
 * Entidad de dominio inmutable para códigos de respaldo de un solo uso en MFA (ADR-014, F2.1.4).
 * Almacena el hash SHA-256 del código para permitir acceso ante pérdida del dispositivo TOTP.
 */
public record MfaBackupCode(
        Long id,
        Long usuarioId,
        String codeHash,
        boolean usado,
        Instant usadoAt,
        Instant createdAt
) {
}
