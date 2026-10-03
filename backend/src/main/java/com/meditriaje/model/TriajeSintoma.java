package com.meditriaje.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para la entidad TriajeSintoma (ADR-009, HU-02).
 */
public record TriajeSintoma(
        Long id,
        Long triajeId,
        Long sintomaId,
        BigDecimal duracionHoras,
        int intensidad
) {
    public TriajeSintoma {
        Objects.requireNonNull(sintomaId, "sintomaId no puede ser nulo");
        Objects.requireNonNull(duracionHoras, "duracionHoras no puede ser nula");
    }

    public TriajeSintoma(Long triajeId, Long sintomaId, BigDecimal duracionHoras, int intensidad) {
        this(null, triajeId, sintomaId, duracionHoras, intensidad);
    }
}
