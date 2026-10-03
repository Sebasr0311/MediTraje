package com.meditriaje.triage;

import com.meditriaje.exception.DatosInvalidosException;

/** Síntoma del catálogo. {@code esAlarma} = alarma incondicional (ADR-009). */
public record SintomaTriaje(String codigo, boolean esAlarma) {

    public SintomaTriaje {
        if (codigo == null || codigo.isBlank()) {
            throw new DatosInvalidosException("El código del síntoma es obligatorio");
        }
    }
}
