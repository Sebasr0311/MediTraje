package com.meditriaje.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para los parámetros fisiológicos {@code SIGNO_VITAL}
 * (ADR-008, HU-07).
 */
public record SignoVital(
        Long id,
        Long atencionId,
        Integer presionSistolica,
        Integer presionDiastolica,
        Integer frecuenciaCardiaca,
        Integer frecuenciaRespiratoria,
        BigDecimal temperatura,
        Integer saturacionOxigeno,
        BigDecimal pesoKg,
        BigDecimal tallaCm
) {
    public SignoVital {
        Objects.requireNonNull(atencionId, "atencionId no puede ser nulo");
    }

    public SignoVital(
            Long atencionId,
            Integer presionSistolica,
            Integer presionDiastolica,
            Integer frecuenciaCardiaca,
            Integer frecuenciaRespiratoria,
            BigDecimal temperatura,
            Integer saturacionOxigeno,
            BigDecimal pesoKg,
            BigDecimal tallaCm
    ) {
        this(null, atencionId, presionSistolica, presionDiastolica, frecuenciaCardiaca, frecuenciaRespiratoria,
                temperatura, saturacionOxigeno, pesoKg, tallaCm);
    }
}
