package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo inmutable para el profesional asistencial (ADR-003, ADR-006, HU-10, Ley 1164/2007).
 */
public record Profesional(
        Long id,
        Long usuarioId,
        String publicId,
        Long especialidadId,
        String tipoDocumento,
        String numeroDocumento,
        String registroMedico,
        String nombres,
        String apellidos,
        String telefono,
        Instant createdAt,
        Instant updatedAt
) {
    public Profesional {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(registroMedico, "registroMedico no puede ser nulo");
        Objects.requireNonNull(nombres, "nombres no puede ser nulo");
        Objects.requireNonNull(apellidos, "apellidos no puede ser nulo");
        if (tipoDocumento == null || tipoDocumento.isBlank()) {
            tipoDocumento = "CC";
        }
    }

    public Profesional(
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
        this(id, usuarioId, publicId, especialidadId, "CC", null, registroMedico, nombres, apellidos, null, createdAt, updatedAt);
    }

    public Profesional(
            Long usuarioId,
            String publicId,
            Long especialidadId,
            String registroMedico,
            String nombres,
            String apellidos
    ) {
        this(null, usuarioId, publicId, especialidadId, "CC", null, registroMedico, nombres, apellidos, null, null, null);
    }

    public Profesional(
            Long usuarioId,
            String publicId,
            Long especialidadId,
            String tipoDocumento,
            String numeroDocumento,
            String registroMedico,
            String nombres,
            String apellidos,
            String telefono
    ) {
        this(null, usuarioId, publicId, especialidadId, tipoDocumento, numeroDocumento, registroMedico, nombres, apellidos, telefono, null, null);
    }
}
