package com.meditriaje.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Estructura estándar de error de la API.
 * Nunca incluye stack traces ni datos clínicos/personales.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String codigo,
        String mensaje,
        Instant timestamp,
        String traceId
) {
    public static ApiError of(String codigo, String mensaje, String traceId) {
        return new ApiError(codigo, mensaje, Instant.now(), traceId);
    }
}
