package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para la enmienda o aclaración médica append-only (ADR-008, HU-07).
 */
public record AtencionEnmienda(
        Long id,
        Long atencionId,
        Long profesionalId,
        String motivo,
        String contenido,
        Instant fechaEnmienda
) {
    public AtencionEnmienda {
        Objects.requireNonNull(atencionId, "atencionId no puede ser nulo");
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(motivo, "motivo no puede ser nulo");
        Objects.requireNonNull(contenido, "contenido no puede ser nulo");
    }
}
