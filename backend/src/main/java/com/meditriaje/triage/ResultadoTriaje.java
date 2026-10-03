package com.meditriaje.triage;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de orientación de triaje (prototipo). No contiene diagnósticos, medicamentos ni tiempos de espera.
 * Si {@code emergencia} es true, la ruta es {@link RutaSugerida#URGENCIAS} y no se ofrece cita.
 */
public record ResultadoTriaje(
        NivelPrioridad nivel,
        RutaSugerida ruta,
        boolean emergencia,
        String versionReglas,
        String mensaje,
        String aviso,
        List<String> sintomasAlarma) {

    public ResultadoTriaje {
        Objects.requireNonNull(nivel, "nivel");
        Objects.requireNonNull(ruta, "ruta");
        Objects.requireNonNull(versionReglas, "versionReglas");
        Objects.requireNonNull(mensaje, "mensaje");
        Objects.requireNonNull(aviso, "aviso");
        sintomasAlarma = List.copyOf(sintomasAlarma);
    }
}
