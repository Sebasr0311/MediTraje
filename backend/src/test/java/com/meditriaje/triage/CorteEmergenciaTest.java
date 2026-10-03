package com.meditriaje.triage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.3: el corte de emergencia es infalible. Los códigos de alarma se extraen de V007__triaje.sql,
 * de modo que la prueba falla si el catálogo semilla cambia.
 */
class CorteEmergenciaTest {

    /** Alarmas esperadas hoy en V007 (contrapeso explícito: si cambian, hay que revisar esta prueba). */
    private static final List<String> ALARMAS_ESPERADAS = List.of(
            "DOLOR_TORACICO_OPRESIVO",
            "DIFICULTAD_RESP_SEVERA",
            "PERDIDA_CONCIENCIA",
            "CONVULSIONES",
            "SANGRADO_INCONTROLABLE",
            "PARALISIS_FACIAL_SUBITA");

    private static final Pattern INSERT_ALARMA = Pattern.compile(
            "INSERT INTO SINTOMA \\(PUBLIC_ID, CODIGO, NOMBRE, CATEGORIA, ES_ALARMA\\)\\s+VALUES\\s*\\("
                    + "'[^']*',\\s*'([A-Z_]+)',\\s*'[^']*',\\s*'[^']*',\\s*1\\)");
    private static final Pattern INSERT_NO_ALARMA = Pattern.compile(
            "INSERT INTO SINTOMA \\(PUBLIC_ID, CODIGO, NOMBRE, CATEGORIA\\)\\s+VALUES\\s*\\("
                    + "'[^']*',\\s*'([A-Z_]+)',");

    private static String sql() throws IOException {
        for (String p : List.of("../database/migrations/V007__triaje.sql", "database/migrations/V007__triaje.sql")) {
            Path path = Path.of(p);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("No se encontró V007__triaje.sql");
    }

    private static List<String> extraer(Pattern pattern, String contenido) {
        List<String> codigos = new ArrayList<>();
        Matcher m = pattern.matcher(contenido);
        while (m.find()) {
            codigos.add(m.group(1));
        }
        return codigos;
    }

    private static List<String> alarmasDelSql() throws IOException {
        return extraer(INSERT_ALARMA, sql());
    }

    private static List<String> levesDelSql() throws IOException {
        return extraer(INSERT_NO_ALARMA, sql());
    }

    private static MotorTriajeBasadoEnReglas motorConCatalogoReal() throws IOException {
        List<SintomaTriaje> catalogo = new ArrayList<>();
        List<ReglaTriaje> reglas = new ArrayList<>();
        for (String a : alarmasDelSql()) {
            catalogo.add(new SintomaTriaje(a, true));
        }
        for (String l : levesDelSql()) {
            catalogo.add(new SintomaTriaje(l, false));
            reglas.add(new ReglaTriaje(l, BigDecimal.ZERO, null, 7, 10, NivelPrioridad.II));
            reglas.add(new ReglaTriaje(l, BigDecimal.ZERO, null, 4, 6, NivelPrioridad.III));
            reglas.add(new ReglaTriaje(l, BigDecimal.ZERO, null, 0, 3, NivelPrioridad.IV));
        }
        return new MotorTriajeBasadoEnReglas("v1-prototipo", catalogo, reglas);
    }

    @Test
    void sqlTieneLasAlarmasEsperadas_yLaPruebaCubreTodas() throws IOException {
        List<String> delSql = alarmasDelSql();

        assertThat(delSql).hasSize(ALARMAS_ESPERADAS.size());
        assertThat(delSql).containsExactlyInAnyOrderElementsOf(ALARMAS_ESPERADAS);
        assertThat(levesDelSql()).isNotEmpty().doesNotContainAnyElementsOf(delSql);
    }

    private static void verificarEmergencia(ResultadoTriaje r, String alarma) {
        assertThat(r.emergencia()).isTrue();
        assertThat(r.nivel()).isEqualTo(NivelPrioridad.I);
        assertThat(r.ruta()).isEqualTo(RutaSugerida.URGENCIAS);
        assertThat(r.mensaje()).contains("123").contains("urgencias");
        assertThat(r.aviso()).isEqualTo(MotorTriajeBasadoEnReglas.AVISO);
        assertThat(r.sintomasAlarma()).contains(alarma);
    }

    @Test
    void cadaAlarma_sola_conIntensidad0YDuracion0_esEmergencia() throws IOException {
        MotorTriajeBasadoEnReglas motor = motorConCatalogoReal();
        for (String alarma : alarmasDelSql()) {
            ResultadoTriaje r = motor.evaluar(
                    new EntradaTriaje(List.of(new SintomaReportado(alarma, BigDecimal.ZERO, 0))));
            verificarEmergencia(r, alarma);
        }
    }

    @Test
    void cadaAlarma_mezcladaConLeves_esEmergencia_encualquierIntensidad() throws IOException {
        MotorTriajeBasadoEnReglas motor = motorConCatalogoReal();
        List<String> leves = levesDelSql();
        for (String alarma : alarmasDelSql()) {
            for (int intensidad : new int[]{0, 5, 10}) {
                List<SintomaReportado> lista = new ArrayList<>();
                lista.add(new SintomaReportado(alarma, BigDecimal.ZERO, 0));
                for (String leve : leves.subList(0, 3)) {
                    lista.add(new SintomaReportado(leve, BigDecimal.ZERO, intensidad));
                }
                verificarEmergencia(motor.evaluar(new EntradaTriaje(lista)), alarma);
                // y con la alarma al final de la lista
                java.util.Collections.reverse(lista);
                verificarEmergencia(motor.evaluar(new EntradaTriaje(lista)), alarma);
            }
        }
    }

    @Test
    void todasLasAlarmasJuntas_seReportanOrdenadas() throws IOException {
        List<SintomaReportado> lista = new ArrayList<>();
        for (String a : alarmasDelSql()) {
            lista.add(new SintomaReportado(a, BigDecimal.ZERO, 0));
        }
        ResultadoTriaje r = motorConCatalogoReal().evaluar(new EntradaTriaje(lista));

        assertThat(r.emergencia()).isTrue();
        assertThat(r.sintomasAlarma()).containsExactlyInAnyOrderElementsOf(alarmasDelSql()).isSorted();
    }

    @Test
    void alarmaSinReglasNiCatalogoLeve_sigueSiendoEmergencia() {
        MotorTriajeBasadoEnReglas motor = new MotorTriajeBasadoEnReglas("v",
                List.of(new SintomaTriaje("CONVULSIONES", true)), List.of());

        ResultadoTriaje r = motor.evaluar(
                new EntradaTriaje(List.of(new SintomaReportado("CONVULSIONES", BigDecimal.ZERO, 0))));

        assertThat(r.emergencia()).isTrue();
        assertThat(r.ruta()).isEqualTo(RutaSugerida.URGENCIAS);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 4, 6, 7, 10})
    void sinAlarma_nivelIVMasLeve_noEsEmergencia(int intensidad) throws IOException {
        String leve = levesDelSql().get(0);
        ResultadoTriaje r = motorConCatalogoReal().evaluar(
                new EntradaTriaje(List.of(new SintomaReportado(leve, BigDecimal.ONE, intensidad))));

        assertThat(r.emergencia()).isFalse();
        assertThat(r.nivel()).isNotEqualTo(NivelPrioridad.I);
        assertThat(r.mensaje()).doesNotContain("123");
    }
}
