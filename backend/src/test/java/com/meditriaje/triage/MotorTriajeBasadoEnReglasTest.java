package com.meditriaje.triage;

import com.meditriaje.exception.DatosInvalidosException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MotorTriajeBasadoEnReglasTest {

    private static final BigDecimal CERO = BigDecimal.ZERO;

    private static List<ReglaTriaje> reglasV1(String codigo) {
        return List.of(
                new ReglaTriaje(codigo, CERO, null, 7, 10, NivelPrioridad.II),
                new ReglaTriaje(codigo, CERO, null, 4, 6, NivelPrioridad.III),
                new ReglaTriaje(codigo, CERO, null, 0, 3, NivelPrioridad.IV));
    }

    private static MotorTriajeBasadoEnReglas motor() {
        List<SintomaTriaje> catalogo = List.of(
                new SintomaTriaje("FIEBRE", false),
                new SintomaTriaje("TOS", false),
                new SintomaTriaje("MAREO", false),
                new SintomaTriaje("SIN_REGLA", false),
                new SintomaTriaje("POR_DURACION", false),
                new SintomaTriaje("SOLAPE", false),
                new SintomaTriaje("CRITICO_REGLA", false),
                new SintomaTriaje("CONVULSIONES", true));
        List<ReglaTriaje> reglas = new ArrayList<>();
        reglas.addAll(reglasV1("FIEBRE"));
        reglas.addAll(reglasV1("TOS"));
        reglas.addAll(reglasV1("MAREO"));
        // Duración: [0,24) -> V ; [24,48) -> IV ; [48, sin tope) -> II
        reglas.add(new ReglaTriaje("POR_DURACION", CERO, new BigDecimal("24"), 0, 10, NivelPrioridad.V));
        reglas.add(new ReglaTriaje("POR_DURACION", new BigDecimal("24"), new BigDecimal("48"), 0, 10, NivelPrioridad.IV));
        reglas.add(new ReglaTriaje("POR_DURACION", new BigDecimal("48"), null, 0, 10, NivelPrioridad.II));
        // Solape: ambas aplican con intensidad 5; gana la más urgente (II), sin importar el orden
        reglas.add(new ReglaTriaje("SOLAPE", CERO, null, 0, 10, NivelPrioridad.IV));
        reglas.add(new ReglaTriaje("SOLAPE", CERO, null, 5, 5, NivelPrioridad.II));
        // Regla que da nivel I por sí sola
        reglas.add(new ReglaTriaje("CRITICO_REGLA", CERO, null, 9, 10, NivelPrioridad.I));
        return new MotorTriajeBasadoEnReglas("v-test", catalogo, reglas);
    }

    private static SintomaReportado rep(String codigo, String horas, int intensidad) {
        return new SintomaReportado(codigo, new BigDecimal(horas), intensidad);
    }

    private static ResultadoTriaje evaluar(SintomaReportado... s) {
        return motor().evaluar(new EntradaTriaje(List.of(s)));
    }

    @ParameterizedTest(name = "{0} dur={1} int={2} -> {3}")
    @CsvSource({
            // límites de intensidad
            "FIEBRE, 1, 0, IV",
            "FIEBRE, 1, 3, IV",
            "FIEBRE, 1, 4, III",
            "FIEBRE, 1, 6, III",
            "FIEBRE, 1, 7, II",
            "FIEBRE, 1, 10, II",
            // límites de duración: min inclusivo, max exclusivo, fraccionaria
            "POR_DURACION, 0, 5, V",
            "POR_DURACION, 23.99, 5, V",
            "POR_DURACION, 24, 5, IV",
            "POR_DURACION, 47.999, 5, IV",
            "POR_DURACION, 48, 5, II",
            "POR_DURACION, 10000, 5, II",
            // solape: la más urgente
            "SOLAPE, 1, 5, II",
            "SOLAPE, 1, 4, IV",
            // default conservador
            "SIN_REGLA, 1, 5, III",
            "SIN_REGLA, 0, 0, III",
            "SIN_REGLA, 1, 10, III"
    })
    void tablaDeCasos(String codigo, String horas, int intensidad, NivelPrioridad esperado) {
        ResultadoTriaje r = evaluar(rep(codigo, horas, intensidad));

        assertThat(r.nivel()).isEqualTo(esperado);
        assertThat(r.ruta()).isEqualTo(esperado.ruta());
        assertThat(r.emergencia()).isFalse();
        assertThat(r.versionReglas()).isEqualTo("v-test");
        assertThat(r.aviso()).isEqualTo(MotorTriajeBasadoEnReglas.AVISO);
    }

    @Test
    void mapeoNivelRuta() {
        assertThat(NivelPrioridad.I.ruta()).isEqualTo(RutaSugerida.URGENCIAS);
        assertThat(NivelPrioridad.II.ruta()).isEqualTo(RutaSugerida.ATENCION_PRIORITARIA);
        assertThat(NivelPrioridad.III.ruta()).isEqualTo(RutaSugerida.CITA_PRESENCIAL);
        assertThat(NivelPrioridad.IV.ruta()).isEqualTo(RutaSugerida.CITA_TELEMEDICINA);
        assertThat(NivelPrioridad.V.ruta()).isEqualTo(RutaSugerida.CONSULTA_PROGRAMADA);
    }

    @Test
    void variosSintomas_ganaElMasUrgente() {
        ResultadoTriaje r = evaluar(rep("FIEBRE", "1", 2), rep("TOS", "1", 5), rep("MAREO", "1", 8));

        assertThat(r.nivel()).isEqualTo(NivelPrioridad.II);
        assertThat(r.ruta()).isEqualTo(RutaSugerida.ATENCION_PRIORITARIA);
    }

    @Test
    void sintomaSinReglaMezcladoConLeve_aportaIII() {
        ResultadoTriaje r = evaluar(rep("FIEBRE", "1", 1), rep("SIN_REGLA", "1", 1));

        assertThat(r.nivel()).isEqualTo(NivelPrioridad.III);
    }

    @Test
    void reglaNivelI_esEmergencia() {
        ResultadoTriaje r = evaluar(rep("CRITICO_REGLA", "1", 9));

        assertThat(r.emergencia()).isTrue();
        assertThat(r.nivel()).isEqualTo(NivelPrioridad.I);
        assertThat(r.ruta()).isEqualTo(RutaSugerida.URGENCIAS);
        assertThat(r.mensaje()).isEqualTo(MotorTriajeBasadoEnReglas.MENSAJE_EMERGENCIA);
        assertThat(r.sintomasAlarma()).isEmpty();
    }

    @Test
    void defaultNuncaEsV_paraTodaIntensidadYDuracion() {
        for (int intensidad = 0; intensidad <= 10; intensidad++) {
            for (String horas : List.of("0", "0.5", "1", "24", "1000")) {
                ResultadoTriaje r = evaluar(rep("SIN_REGLA", horas, intensidad));
                assertThat(r.nivel()).isNotEqualTo(NivelPrioridad.V).isEqualTo(NivelPrioridad.III);
            }
        }
    }

    @Test
    void determinista_yInvariantePorOrdenDeEntrada() {
        List<SintomaReportado> base = List.of(
                rep("FIEBRE", "3", 2), rep("TOS", "5", 5), rep("MAREO", "1", 1), rep("SIN_REGLA", "2", 9));
        ResultadoTriaje referencia = motor().evaluar(new EntradaTriaje(base));

        permutaciones(base, new ArrayList<>(), new boolean[base.size()], perm ->
                assertThat(motor().evaluar(new EntradaTriaje(perm))).isEqualTo(referencia));
        assertThat(motor().evaluar(new EntradaTriaje(base))).isEqualTo(referencia);
    }

    @Test
    void invarianteAlOrdenDeReglas() {
        List<ReglaTriaje> reglas = new ArrayList<>(reglasV1("FIEBRE"));
        reglas.add(new ReglaTriaje("FIEBRE", CERO, null, 0, 10, NivelPrioridad.V));
        List<ReglaTriaje> invertidas = new ArrayList<>(reglas);
        java.util.Collections.reverse(invertidas);
        List<SintomaTriaje> cat = List.of(new SintomaTriaje("FIEBRE", false));
        EntradaTriaje entrada = new EntradaTriaje(List.of(rep("FIEBRE", "1", 8)));

        assertThat(new MotorTriajeBasadoEnReglas("v", cat, reglas).evaluar(entrada))
                .isEqualTo(new MotorTriajeBasadoEnReglas("v", cat, invertidas).evaluar(entrada));
    }

    private static void permutaciones(List<SintomaReportado> src, List<SintomaReportado> actual,
                                      boolean[] usado, java.util.function.Consumer<List<SintomaReportado>> check) {
        if (actual.size() == src.size()) {
            check.accept(List.copyOf(actual));
            return;
        }
        for (int i = 0; i < src.size(); i++) {
            if (!usado[i]) {
                usado[i] = true;
                actual.add(src.get(i));
                permutaciones(src, actual, usado, check);
                actual.remove(actual.size() - 1);
                usado[i] = false;
            }
        }
    }

    // ---------- validaciones ----------

    @Test
    void sintomaDesconocido_lanzaDatosInvalidos() {
        assertThatThrownBy(() -> evaluar(rep("NO_EXISTE", "1", 5)))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void entradaVaciaONula_lanzaDatosInvalidos() {
        assertThatThrownBy(() -> new EntradaTriaje(List.of())).isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> new EntradaTriaje(null)).isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void masDeVeinteSintomas_lanzaDatosInvalidos() {
        List<SintomaReportado> lista = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            lista.add(rep("S" + i, "1", 1));
        }
        assertThatThrownBy(() -> new EntradaTriaje(lista)).isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void sintomasDuplicados_lanzaDatosInvalidos() {
        assertThatThrownBy(() -> new EntradaTriaje(List.of(rep("FIEBRE", "1", 1), rep("FIEBRE", "2", 3))))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @ParameterizedTest
    @CsvSource({"-1", "11", "100"})
    void intensidadFueraDeRango_lanzaDatosInvalidos(int intensidad) {
        assertThatThrownBy(() -> rep("FIEBRE", "1", intensidad)).isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void duracionNegativaONula_lanzaDatosInvalidos() {
        assertThatThrownBy(() -> rep("FIEBRE", "-0.1", 5)).isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> new SintomaReportado("FIEBRE", null, 5)).isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void reglaInvalida_lanzaDatosInvalidos() {
        assertThatThrownBy(() -> new ReglaTriaje("X", CERO, CERO, 0, 10, NivelPrioridad.III))
                .isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> new ReglaTriaje("X", CERO, null, 5, 4, NivelPrioridad.III))
                .isInstanceOf(DatosInvalidosException.class);
    }
}
