package com.meditriaje.triage;

import com.meditriaje.exception.DatosInvalidosException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Motor de triaje puro y determinista (sin Spring/JDBC/HTTP). Reglas de PROTOTIPO, no validadas
 * clínicamente (ADR-009 y adenda M5.2).
 *
 * <ul>
 *   <li>Síntoma de alarma: corte de emergencia incondicional (no consulta reglas ni intensidad/duración).</li>
 *   <li>Por síntoma: gana la regla aplicable más urgente; sin regla aplicable aporta nivel III (nunca V).</li>
 *   <li>Nivel final: el más urgente de todos los síntomas. Nivel I también es emergencia.</li>
 * </ul>
 */
public final class MotorTriajeBasadoEnReglas implements MotorTriaje {

    public static final String MENSAJE_EMERGENCIA = "Llama al 123 o acude a urgencias de inmediato.";
    public static final String AVISO =
            "Esta orientación es un prototipo, no sustituye la valoración de un profesional de la salud.";
    public static final NivelPrioridad NIVEL_POR_DEFECTO = NivelPrioridad.III;

    private final String versionReglas;
    private final Map<String, SintomaTriaje> catalogo;
    private final Map<String, List<ReglaTriaje>> reglasPorSintoma;

    public MotorTriajeBasadoEnReglas(String versionReglas, Collection<SintomaTriaje> catalogo,
                                     Collection<ReglaTriaje> reglas) {
        if (versionReglas == null || versionReglas.isBlank()) {
            throw new IllegalArgumentException("versionReglas es obligatoria");
        }
        this.versionReglas = versionReglas;
        Map<String, SintomaTriaje> cat = new HashMap<>();
        for (SintomaTriaje s : catalogo) {
            cat.put(s.codigo(), s);
        }
        this.catalogo = Map.copyOf(cat);
        Map<String, List<ReglaTriaje>> porSintoma = new HashMap<>();
        for (ReglaTriaje r : reglas) {
            porSintoma.computeIfAbsent(r.sintomaCodigo(), k -> new ArrayList<>()).add(r);
        }
        porSintoma.replaceAll((k, v) -> List.copyOf(v));
        this.reglasPorSintoma = Map.copyOf(porSintoma);
    }

    @Override
    public ResultadoTriaje evaluar(EntradaTriaje entrada) {
        NivelPrioridad nivelFinal = null;
        List<String> alarmas = new ArrayList<>();

        for (SintomaReportado rep : entrada.sintomas()) {
            SintomaTriaje sintoma = catalogo.get(rep.sintomaCodigo());
            if (sintoma == null) {
                throw new DatosInvalidosException("Síntoma desconocido: " + rep.sintomaCodigo());
            }
            if (sintoma.esAlarma()) {
                alarmas.add(sintoma.codigo());
                continue;
            }
            NivelPrioridad nivel = nivelDe(rep);
            nivelFinal = nivelFinal == null ? nivel : NivelPrioridad.masUrgente(nivelFinal, nivel);
        }
        alarmas.sort(String::compareTo);

        boolean emergencia = !alarmas.isEmpty() || nivelFinal == NivelPrioridad.I;
        if (emergencia) {
            return new ResultadoTriaje(NivelPrioridad.I, RutaSugerida.URGENCIAS, true, versionReglas,
                    MENSAJE_EMERGENCIA, AVISO, alarmas);
        }
        return new ResultadoTriaje(nivelFinal, nivelFinal.ruta(), false, versionReglas,
                mensajePara(nivelFinal.ruta()), AVISO, List.of());
    }

    private NivelPrioridad nivelDe(SintomaReportado rep) {
        NivelPrioridad mejor = null;
        for (ReglaTriaje r : reglasPorSintoma.getOrDefault(rep.sintomaCodigo(), List.of())) {
            if (r.aplica(rep.duracionHoras(), rep.intensidad())) {
                mejor = mejor == null ? r.nivel() : NivelPrioridad.masUrgente(mejor, r.nivel());
            }
        }
        return mejor != null ? mejor : NIVEL_POR_DEFECTO;
    }

    public static String mensajePara(RutaSugerida ruta) {
        return switch (ruta) {
            case URGENCIAS -> MENSAJE_EMERGENCIA;
            case ATENCION_PRIORITARIA -> "Se sugiere atención prioritaria.";
            case CITA_PRESENCIAL -> "Se sugiere una cita presencial.";
            case CITA_TELEMEDICINA -> "Se sugiere una cita de telemedicina.";
            case CONSULTA_PROGRAMADA -> "Se sugiere una consulta programada.";
        };
    }
}
