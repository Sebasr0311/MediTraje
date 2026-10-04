package com.meditriaje.dto.pharmacy;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta para un evento de dispensación en farmacia (F2.4, ADR-016).
 */
public record DispensacionResponse(
        String publicId,
        String recetaPublicId,
        String sedePublicId,
        String sedeNombre,
        String dispensadorPublicId,
        String dispensadorNombre,
        String observaciones,
        Instant createdAt,
        List<DispensacionDetalleResponse> detalles
) {}
