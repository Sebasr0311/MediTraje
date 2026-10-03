package com.meditriaje.dto;

/**
 * Contenedor de tokens y datos de sesión retornado por la capa de servicio
 * para que el controlador configure las cookies de seguridad (ADR-002).
 */
public record AuthTokens(
        String accessToken,
        String rawRefreshToken,
        AuthSessionResponse sessionResponse
) {}
