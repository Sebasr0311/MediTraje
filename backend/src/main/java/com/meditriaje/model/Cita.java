package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo inmutable para la entidad Cita asistencial (ADR-002, ADR-003, ADR-006, HU-04).
 */
public record Cita(
        Long id,
        String publicId,
        Long slotId,
        Long pacienteId,
        Long triajeId,
        Long citaOrigenId,
        String estado,
        String motivoCancelacion,
        Instant createdAt,
        Instant updatedAt
) {
    public Cita {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(slotId, "slotId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(estado, "estado no puede ser nulo");
    }

    public Cita(
            String publicId,
            Long slotId,
            Long pacienteId,
            Long triajeId,
            Long citaOrigenId,
            String estado,
            String motivoCancelacion
    ) {
        this(null, publicId, slotId, pacienteId, triajeId, citaOrigenId, estado, motivoCancelacion, null, null);
    }
}
