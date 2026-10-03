package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para la entidad Triaje (ADR-003, ADR-009, HU-02).
 */
public record Triaje(
        Long id,
        String publicId,
        Long pacienteId,
        String versionReglas,
        String nivelPrioridad,
        String rutaSugerida,
        boolean esEmergencia,
        String observaciones,
        Instant createdAt
) {
    public Triaje {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(versionReglas, "versionReglas no puede ser nula");
        Objects.requireNonNull(nivelPrioridad, "nivelPrioridad no puede ser nulo");
        Objects.requireNonNull(rutaSugerida, "rutaSugerida no puede ser nula");
    }

    public Triaje(
            String publicId,
            Long pacienteId,
            String versionReglas,
            String nivelPrioridad,
            String rutaSugerida,
            boolean esEmergencia,
            String observaciones
    ) {
        this(null, publicId, pacienteId, versionReglas, nivelPrioridad, rutaSugerida, esEmergencia, observaciones, null);
    }
}
