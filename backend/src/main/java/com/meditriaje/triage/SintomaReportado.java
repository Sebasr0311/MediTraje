package com.meditriaje.triage;

import com.meditriaje.exception.DatosInvalidosException;

import java.math.BigDecimal;

/** Síntoma reportado por el paciente: duración en horas (>= 0) e intensidad 0-10. */
public record SintomaReportado(String sintomaCodigo, BigDecimal duracionHoras, int intensidad) {

    public static final int INTENSIDAD_MAX = 10;

    public SintomaReportado {
        if (sintomaCodigo == null || sintomaCodigo.isBlank()) {
            throw new DatosInvalidosException("El código del síntoma es obligatorio");
        }
        if (duracionHoras == null || duracionHoras.signum() < 0) {
            throw new DatosInvalidosException("La duración debe ser mayor o igual a 0");
        }
        if (intensidad < 0 || intensidad > INTENSIDAD_MAX) {
            throw new DatosInvalidosException("La intensidad debe estar entre 0 y 10");
        }
    }
}
