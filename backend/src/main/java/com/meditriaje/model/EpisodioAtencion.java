package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Representa un episodio de atención hospitalario independiente del agendamiento de citas (Fase U, ADR-022).
 */
public record EpisodioAtencion(
        Long id,
        String publicId,
        Long pacienteId,
        String tipo,
        Long sedeId,
        String estado,
        Instant ingresoAt,
        Instant egresoAt,
        String motivoIngreso,
        Instant creadoAt,
        Instant updatedAt
) {
    public EpisodioAtencion {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(tipo, "tipo no puede ser nulo");
        Objects.requireNonNull(sedeId, "sedeId no puede ser nulo");
        Objects.requireNonNull(estado, "estado no puede ser nulo");
    }
}
