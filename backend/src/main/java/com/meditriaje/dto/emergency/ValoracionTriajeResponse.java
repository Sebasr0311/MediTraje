package com.meditriaje.dto.emergency;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Respuesta con el detalle inmutable de una valoración clínica de triaje (Fase U, ADR-027).
 */
public record ValoracionTriajeResponse(
        String publicId,
        String episodioPublicId,
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
        String evaluadorNombre,
        boolean esReevaluacion,
        String motivoReevaluacion,
        Instant creadoAt
) {
}
