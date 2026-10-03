package com.meditriaje.triage;

import com.meditriaje.exception.DatosInvalidosException;

import java.math.BigDecimal;

/**
 * Regla de triaje de prototipo. Duración: {@code [min, max)} (max nulo = sin tope).
 * Intensidad: rango cerrado {@code [intensidadMin, intensidadMax]}.
 */
public record ReglaTriaje(
        String sintomaCodigo,
        BigDecimal duracionMinHoras,
        BigDecimal duracionMaxHoras,
        int intensidadMin,
        int intensidadMax,
        NivelPrioridad nivel) {

    public ReglaTriaje {
        if (sintomaCodigo == null || sintomaCodigo.isBlank()) {
            throw new DatosInvalidosException("La regla requiere código de síntoma");
        }
        if (duracionMinHoras == null || duracionMinHoras.signum() < 0) {
            throw new DatosInvalidosException("La duración mínima de la regla debe ser >= 0");
        }
        if (duracionMaxHoras != null && duracionMaxHoras.compareTo(duracionMinHoras) <= 0) {
            throw new DatosInvalidosException("La duración máxima de la regla debe ser mayor que la mínima");
        }
        if (intensidadMin < 0 || intensidadMax > SintomaReportado.INTENSIDAD_MAX || intensidadMin > intensidadMax) {
            throw new DatosInvalidosException("Rango de intensidad de la regla inválido");
        }
        if (nivel == null) {
            throw new DatosInvalidosException("La regla requiere nivel de prioridad");
        }
    }

    /** Indica si la regla aplica a la duración e intensidad dadas. */
    public boolean aplica(BigDecimal duracionHoras, int intensidad) {
        boolean durOk = duracionHoras.compareTo(duracionMinHoras) >= 0
                && (duracionMaxHoras == null || duracionHoras.compareTo(duracionMaxHoras) < 0);
        return durOk && intensidad >= intensidadMin && intensidad <= intensidadMax;
    }
}
