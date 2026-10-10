package com.meditriaje.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Representa una valoración clínica presencial de triaje estructurada y firmada por un profesional (Fase U, ADR-027).
 * Inmutable y versionada append-only.
 */
public record ValoracionTriaje(
        Long id,
        String publicId,
        Long episodioId,
        Integer version,
        String nivel,
        String motivoConsulta,
        String hallazgosClinicos,
        String presionArterial,
        Integer frecuenciaCardiaca,
        Integer frecuenciaRespiratoria,
        Integer saturacionOxigeno,
        BigDecimal temperatura,
        Integer escalaGlasgow,
        Long evaluadorUsuarioId,
        boolean esReevaluacion,
        String motivoReevaluacion,
        Instant creadoAt
) {
    public ValoracionTriaje {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(episodioId, "episodioId no puede ser nulo");
        Objects.requireNonNull(version, "version no puede ser nula");
        Objects.requireNonNull(nivel, "nivel no puede ser nulo");
        Objects.requireNonNull(motivoConsulta, "motivoConsulta no puede ser nulo");
        Objects.requireNonNull(evaluadorUsuarioId, "evaluadorUsuarioId no puede ser nulo");
    }
}
