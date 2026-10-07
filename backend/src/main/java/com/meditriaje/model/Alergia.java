package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable que representa una hipersensibilidad o alergia de un paciente (ADR-008, V008, V016).
 */
public record Alergia(
        Long id,
        String publicId,
        Long pacienteId,
        String sustancia,
        String reaccion,
        String severidad,
        String estado,
        String origen,
        Long registradaPorUsuarioId,
        Long atencionId,
        Instant fechaInactivacion,
        Long inactivadaPorUsuarioId,
        String motivoInactivacion,
        Instant createdAt,
        Instant updatedAt
) {
    public Alergia {
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(sustancia, "sustancia no puede ser nula");
        Objects.requireNonNull(severidad, "severidad no puede ser nula");
    }

    /**
     * Constructor de compatibilidad hacia atrás para código y pruebas existentes (V008).
     */
    public Alergia(Long id, Long pacienteId, String sustancia, String reaccion, String severidad, Instant createdAt) {
        this(
                id,
                null,
                pacienteId,
                sustancia,
                reaccion,
                severidad,
                "ACTIVA",
                "PROFESIONAL",
                null,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt
        );
    }
}
