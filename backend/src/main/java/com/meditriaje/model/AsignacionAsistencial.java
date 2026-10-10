package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Representa la asignación de un profesional asistencial a un episodio de atención (Fase U, ADR-022).
 */
public record AsignacionAsistencial(
        Long id,
        String publicId,
        Long episodioId,
        Long profesionalId,
        String funcion,
        Long asignadoPorUsuarioId,
        Instant fechaInicio,
        Instant fechaFin,
        boolean activo,
        Instant creadoAt
) {
    public AsignacionAsistencial {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(episodioId, "episodioId no puede ser nulo");
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(funcion, "funcion no puede ser nula");
        Objects.requireNonNull(asignadoPorUsuarioId, "asignadoPorUsuarioId no puede ser nulo");
    }
}
