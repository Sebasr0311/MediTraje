package com.meditriaje.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Solicitud de segundo factor de autenticación MFA (ADR-014, F2.1.4).
 * Admite tanto un código numérico TOTP de 6 dígitos como un código de respaldo alfanumérico.
 */
public record MfaAuthenticateRequest(
        @NotBlank(message = "El token de desafio MFA es obligatorio.")
        String challengeToken,

        @NotBlank(message = "El codigo TOTP o de respaldo es obligatorio.")
        String codigo
) {
}
