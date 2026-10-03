package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para la cabecera del acto médico asistencial {@code ATENCION}
 * (ADR-007, ADR-008, HU-07).
 */
public record Atencion(
        Long id,
        String publicId,
        Long citaId,
        Long pacienteId,
        Long profesionalId,
        Long diagnosticoPrincipalId,
        String motivoConsulta,
        String evolucion,
        String indicaciones,
        String estado,
        Instant fechaCierre,
        Instant createdAt
) {
    public Atencion {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(citaId, "citaId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(estado, "estado no puede ser nulo");
    }

    public Atencion(
            Long id,
            String publicId,
            Long citaId,
            Long pacienteId,
            Long profesionalId,
            String estado
    ) {
        this(id, publicId, citaId, pacienteId, profesionalId, null, null, null, null, estado, null, Instant.now());
    }

    public boolean estaCerrada() {
        return "CERRADA".equalsIgnoreCase(estado);
    }

    public boolean estaAbierta() {
        return "ABIERTA".equalsIgnoreCase(estado);
    }
}
