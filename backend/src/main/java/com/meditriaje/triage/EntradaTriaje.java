package com.meditriaje.triage;

import com.meditriaje.exception.DatosInvalidosException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Entrada del motor: lista no vacía, máximo {@value #MAX_SINTOMAS} síntomas, sin duplicados. */
public record EntradaTriaje(List<SintomaReportado> sintomas) {

    public static final int MAX_SINTOMAS = 20;

    public EntradaTriaje {
        if (sintomas == null || sintomas.isEmpty()) {
            throw new DatosInvalidosException("Debe reportar al menos un síntoma");
        }
        if (sintomas.size() > MAX_SINTOMAS) {
            throw new DatosInvalidosException("No se admiten más de " + MAX_SINTOMAS + " síntomas");
        }
        Set<String> vistos = new HashSet<>();
        for (SintomaReportado s : sintomas) {
            if (s == null) {
                throw new DatosInvalidosException("Síntoma reportado inválido");
            }
            if (!vistos.add(s.sintomaCodigo())) {
                throw new DatosInvalidosException("Síntoma duplicado: " + s.sintomaCodigo());
            }
        }
        sintomas = List.copyOf(sintomas);
    }
}
