package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Representa la llegada y admisión presencial a urgencias (Fase U, ADR-022).
 */
public record IngresoUrgencia(
        Long id,
        String publicId,
        Long episodioId,
        String medioLlegada,
        String acompananteNombre,
        String acompananteContacto,
        String motivoResumido,
        String observaciones,
        Long registradoPorUsuarioId,
        Instant creadoAt
) {
    public IngresoUrgencia {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(episodioId, "episodioId no puede ser nulo");
        Objects.requireNonNull(medioLlegada, "medioLlegada no puede ser nulo");
        Objects.requireNonNull(motivoResumido, "motivoResumido no puede ser nulo");
        Objects.requireNonNull(registradoPorUsuarioId, "registradoPorUsuarioId no puede ser nulo");
    }
}
