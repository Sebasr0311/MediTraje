package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para la cabecera del evento de dispensación en farmacia {@code DISPENSACION} (F2.4, ADR-016).
 * Inmutable tras su inserción.
 */
public record Dispensacion(
        Long id,
        String publicId,
        Long recetaId,
        Long sedeId,
        Long usuarioId,
        String observaciones,
        Instant createdAt
) {
    public Dispensacion {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(recetaId, "recetaId no puede ser nulo");
        Objects.requireNonNull(sedeId, "sedeId no puede ser nula");
        Objects.requireNonNull(usuarioId, "usuarioId no puede ser nulo");
    }
}
