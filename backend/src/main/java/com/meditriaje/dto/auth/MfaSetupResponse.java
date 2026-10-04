package com.meditriaje.dto.auth;

/**
 * Respuesta de configuración/enrolamiento MFA TOTP (ADR-014, F2.1.4).
 */
public record MfaSetupResponse(
        String secret,
        String qrUri,
        String issuer,
        String accountName
) {
}
