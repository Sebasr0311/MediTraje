package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo inmutable para el profesional asistencial (ADR-003, ADR-006, HU-10).
 */
public record Profesional(
        Long id,
        Long usuarioId,
        String publicId,
        Long especialidadId,
        String registroMedico,
        String nombres,
        String apellidos,
        Instant createdAt,
        Instant updatedAt
) {
    public Profesional {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(registroMedico, "registroMedico no puede ser nulo");
        Objects.requireNonNull(nombres, "nombres no puede ser nulo");
        Objects.requireNonNull(apellidos, "apellidos no puede ser nulo");
    }

    public Profesional(
            Long usuarioId,
            String publicId,
            Long especialidadId,
            String registroMedico,
            String nombres,
            String apellidos
    ) {
        this(null, usuarioId, publicId, especialidadId, registroMedico, nombres, apellidos, null, null);
    }
}
