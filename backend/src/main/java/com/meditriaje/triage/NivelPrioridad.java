package com.meditriaje.triage;

/**
 * Nivel de prioridad de triaje (ADR-009). El orden de declaración es el orden de urgencia:
 * {@code I} es el más urgente y {@code V} el menos urgente.
 */
public enum NivelPrioridad {
    I(RutaSugerida.URGENCIAS),
    II(RutaSugerida.ATENCION_PRIORITARIA),
    III(RutaSugerida.CITA_PRESENCIAL),
    IV(RutaSugerida.CITA_TELEMEDICINA),
    V(RutaSugerida.CONSULTA_PROGRAMADA);

    private final RutaSugerida ruta;

    NivelPrioridad(RutaSugerida ruta) {
        this.ruta = ruta;
    }

    /** Mapeo nivel → ruta (prototipo, no validado clínicamente). */
    public RutaSugerida ruta() {
        return ruta;
    }

    /** Devuelve el más urgente de los dos niveles. */
    public static NivelPrioridad masUrgente(NivelPrioridad a, NivelPrioridad b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
