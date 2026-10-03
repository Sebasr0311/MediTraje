package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo inmutable para el slot o turno de disponibilidad asistencial (ADR-003, ADR-005, ADR-006, HU-10).
 */
public record DisponibilidadSlot(
        Long id,
        String publicId,
        Long profesionalId,
        Long sedeId,
        Long especialidadId,
        Instant fechaHoraInicio,
        Instant fechaHoraFin,
        String modalidad,
        String estado
) {
    public DisponibilidadSlot {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(sedeId, "sedeId no puede ser nulo");
        Objects.requireNonNull(especialidadId, "especialidadId no puede ser nulo");
        Objects.requireNonNull(fechaHoraInicio, "fechaHoraInicio no puede ser nulo");
        Objects.requireNonNull(fechaHoraFin, "fechaHoraFin no puede ser nulo");
        Objects.requireNonNull(modalidad, "modalidad no puede ser nulo");
        Objects.requireNonNull(estado, "estado no puede ser nulo");
    }

    public DisponibilidadSlot(
            String publicId,
            Long profesionalId,
            Long sedeId,
            Long especialidadId,
            Instant fechaHoraInicio,
            Instant fechaHoraFin,
            String modalidad,
            String estado
    ) {
        this(null, publicId, profesionalId, sedeId, especialidadId, fechaHoraInicio, fechaHoraFin, modalidad, estado);
    }
}
