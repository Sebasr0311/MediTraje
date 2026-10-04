package com.meditriaje.dto.clinical;

/**
 * DTO de respuesta para catálogo de diagnósticos CIE-10 (ADR-013).
 */
public record DiagnosticoCie10Response(
        String codigo,
        String descripcion
) {
}
