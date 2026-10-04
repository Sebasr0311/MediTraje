package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable que representa una hipersensibilidad o alergia de un paciente (ADR-008, V008).
 */
public record Alergia(
        Long id,
        Long pacienteId,
        String sustancia,
        String reaccion,
        String severidad,
        Instant createdAt
) {
    public Alergia {
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(sustancia, "sustancia no puede ser nula");
        Objects.requireNonNull(severidad, "severidad no puede ser nula");
    }
}
