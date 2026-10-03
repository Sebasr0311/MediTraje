package com.meditriaje.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Modelo inmutable para el paciente (ADR-002, ADR-003, HU-09).
 */
public record Paciente(
        Long id,
        Long usuarioId,
        String publicId,
        String tipoDocumento,
        String numeroDocumento,
        String nombres,
        String apellidos,
        LocalDate fechaNacimiento,
        String telefono,
        Instant createdAt,
        Instant updatedAt
) {
    public Paciente {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(tipoDocumento, "tipoDocumento no puede ser nulo");
        Objects.requireNonNull(numeroDocumento, "numeroDocumento no puede ser nulo");
        Objects.requireNonNull(nombres, "nombres no puede ser nulo");
        Objects.requireNonNull(apellidos, "apellidos no puede ser nulo");
    }

    public Paciente(
            Long id,
            Long usuarioId,
            String publicId,
            String tipoDocumento,
            String numeroDocumento,
            String nombres,
            String apellidos,
            LocalDate fechaNacimiento,
            String telefono
    ) {
        this(id, usuarioId, publicId, tipoDocumento, numeroDocumento, nombres, apellidos, fechaNacimiento, telefono, null, null);
    }
}
