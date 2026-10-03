package com.meditriaje.dto;

import java.util.List;

/**
 * Respuesta de sesión autenticada (ADR-002, ADR-003).
 * Los tokens de acceso y refresco viajan exclusivamente en cookies HttpOnly y NO en este body.
 */
public record AuthSessionResponse(
        String publicId,
        String email,
        List<String> roles,
        String mensaje
) {}
