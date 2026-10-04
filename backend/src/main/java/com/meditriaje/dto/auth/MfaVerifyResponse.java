package com.meditriaje.dto.auth;

import java.util.List;

/**
 * Respuesta tras verificar y activar exitosamente MFA TOTP, entregando códigos de respaldo (ADR-014, F2.1.4).
 */
public record MfaVerifyResponse(
        boolean mfaHabilitado,
        List<String> backupCodes,
        String mensaje
) {
}
