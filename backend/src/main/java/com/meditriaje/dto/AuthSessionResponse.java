package com.meditriaje.dto;

import java.util.List;

/**
 * Respuesta de sesión autenticada (ADR-002, ADR-003, ADR-014).
 * Los tokens de acceso y refresco viajan exclusivamente en cookies HttpOnly y NO en este body.
 * Cuando mfaRequerido es true, se entrega mfaChallengeToken para completar el segundo factor.
 */
public record AuthSessionResponse(
        String publicId,
        String email,
        List<String> roles,
        String mensaje,
        boolean debeCambiarPassword,
        boolean mfaRequerido,
        String mfaChallengeToken
) {
    public AuthSessionResponse(String publicId, String email, List<String> roles, String mensaje) {
        this(publicId, email, roles, mensaje, false, false, null);
    }

    public AuthSessionResponse(String publicId, String email, List<String> roles, String mensaje, boolean debeCambiarPassword) {
        this(publicId, email, roles, mensaje, debeCambiarPassword, false, null);
    }

    public static AuthSessionResponse mfaRequerido(String publicId, String email, List<String> roles, String challengeToken) {
        return new AuthSessionResponse(
                publicId,
                email,
                roles,
                "Autenticacion de segundo factor requerida.",
                false,
                true,
                challengeToken
        );
    }
}
