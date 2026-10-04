package com.meditriaje.repository;

import com.meditriaje.dto.report.DistribucionItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ReporteRepository reporteRepository;

    @BeforeEach
    void setUp() {
        reporteRepository = new ReporteRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("contarCitasPorEstado - mapea estados correctamente")
    void contarCitasPorEstado_exito() throws Exception {
        Instant desde = Instant.now().minusSeconds(86400);
        Instant hasta = Instant.now();

        doAnswer(invocation -> {
            RowCallbackHandler rch = invocation.getArgument(1);
            ResultSet rs1 = mock(ResultSet.class);
            when(rs1.getString("ESTADO")).thenReturn("ATENDIDA");
            when(rs1.getLong("TOTAL")).thenReturn(15L);
            rch.processRow(rs1);

            ResultSet rs2 = mock(ResultSet.class);
            when(rs2.getString("ESTADO")).thenReturn("CANCELADA");
            when(rs2.getLong("TOTAL")).thenReturn(3L);
            rch.processRow(rs2);
            return null;
        }).when(jdbcTemplate).query(contains("FROM CITA"), any(RowCallbackHandler.class), any(Object[].class));

        Map<String, Long> estados = reporteRepository.contarCitasPorEstado(desde, hasta);

        assertThat(estados).containsEntry("ATENDIDA", 15L);
        assertThat(estados).containsEntry("CANCELADA", 3L);
    }

    @Test
    @DisplayName("obtenerCitasPorEspecialidad - calcula porcentajes y orden")
    void obtenerCitasPorEspecialidad_exito() {
        when(jdbcTemplate.queryForList(contains("JOIN ESPECIALIDAD"), any(Object[].class)))
                .thenReturn(List.of(
                        Map.of("PUBLIC_ID", "esp-1", "NOMBRE", "Medicina General", "TOTAL", 75L),
                        Map.of("PUBLIC_ID", "esp-2", "NOMBRE", "Pediatría", "TOTAL", 25L)
                ));

        List<DistribucionItemDto> items = reporteRepository.obtenerCitasPorEspecialidad(null, null);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).etiqueta()).isEqualTo("Medicina General");
        assertThat(items.get(0).cantidad()).isEqualTo(75L);
        assertThat(items.get(0).porcentaje()).isEqualTo(75.0);

        assertThat(items.get(1).etiqueta()).isEqualTo("Pediatría");
        assertThat(items.get(1).cantidad()).isEqualTo(25L);
        assertThat(items.get(1).porcentaje()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("obtenerCitasPorSede - agrupa por sede hospitalaria")
    void obtenerCitasPorSede_exito() {
        when(jdbcTemplate.queryForList(contains("JOIN SEDE"), any(Object[].class)))
                .thenReturn(List.of(
                        Map.of("PUBLIC_ID", "sed-1", "NOMBRE", "Sede Norte", "TOTAL", 60L),
                        Map.of("PUBLIC_ID", "sed-2", "NOMBRE", "Sede Centro", "TOTAL", 40L)
                ));

        List<DistribucionItemDto> items = reporteRepository.obtenerCitasPorSede(null, null);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).etiqueta()).isEqualTo("Sede Norte");
        assertThat(items.get(0).porcentaje()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("contarTriajesPorNivel y emergencias - consulta correcta")
    void contarTriajes_exito() throws Exception {
        doAnswer(invocation -> {
            RowCallbackHandler rch = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("NIVEL_PRIORIDAD")).thenReturn("II");
            when(rs.getLong("TOTAL")).thenReturn(8L);
            rch.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(contains("FROM TRIAJE WHERE 1=1"), any(RowCallbackHandler.class), any(Object[].class));

        when(jdbcTemplate.queryForObject(contains("WHERE ES_EMERGENCIA = 1"), eq(Long.class), any(Object[].class)))
                .thenReturn(4L);

        Map<String, Long> niveles = reporteRepository.contarTriajesPorNivel(null, null);
        long emergencias = reporteRepository.contarTriajesEmergencia(null, null);

        assertThat(niveles).containsEntry("II", 8L);
        assertThat(emergencias).isEqualTo(4L);
    }

    @Test
    @DisplayName("contarRecetasPorEstadoDispensacion - mapea pendientes, parciales y totales")
    void contarRecetasPorEstadoDispensacion_exito() throws Exception {
        doAnswer(invocation -> {
            RowCallbackHandler rch = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("PENDIENTES")).thenReturn(10L);
            when(rs.getLong("PARCIALES")).thenReturn(5L);
            when(rs.getLong("TOTALES")).thenReturn(20L);
            rch.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(contains("FROM RECETA r"), any(RowCallbackHandler.class), any(Object[].class));

        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM RECETA"), eq(Long.class), any(Object[].class)))
                .thenReturn(35L);
        when(jdbcTemplate.queryForObject(contains("COALESCE(SUM(dd.CANTIDAD_ENTREGADA)"), eq(Long.class), any(Object[].class)))
                .thenReturn(150L);

        long emitidas = reporteRepository.contarRecetasEmitidas(null, null);
        long unidades = reporteRepository.contarUnidadesDispensadas(null, null);
        Map<String, Long> estados = reporteRepository.contarRecetasPorEstadoDispensacion(null, null);

        assertThat(emitidas).isEqualTo(35L);
        assertThat(unidades).isEqualTo(150L);
        assertThat(estados.get("PENDIENTE")).isEqualTo(10L);
        assertThat(estados.get("DISPENSADA_PARCIAL")).isEqualTo(5L);
        assertThat(estados.get("DISPENSADA_TOTAL")).isEqualTo(20L);
    }

    @Test
    @DisplayName("contarActivacionesBreakGlass y activos - métricas de emergencia")
    void metricasBreakGlass_exito() {
        when(jdbcTemplate.queryForObject(contains("FROM ACCESO_BREAK_GLASS WHERE 1=1"), eq(Long.class), any(Object[].class)))
                .thenReturn(6L);
        when(jdbcTemplate.queryForObject(contains("WHERE FECHA_EXPIRACION > ?"), eq(Long.class), any()))
                .thenReturn(2L);

        when(jdbcTemplate.queryForList(contains("FROM ACCESO_BREAK_GLASS bg"), any(Object[].class)))
                .thenReturn(List.of(
                        Map.of("PUBLIC_ID", "esp-1", "NOMBRE", "Urgencias", "TOTAL", 6L)
                ));

        long total = reporteRepository.contarActivacionesBreakGlass(null, null);
        long activas = reporteRepository.contarBreakGlassActivos(Instant.now());
        List<DistribucionItemDto> porEsp = reporteRepository.obtenerBreakGlassPorEspecialidad(null, null);

        assertThat(total).isEqualTo(6L);
        assertThat(activas).isEqualTo(2L);
        assertThat(porEsp).hasSize(1);
        assertThat(porEsp.get(0).cantidad()).isEqualTo(6L);
        assertThat(porEsp.get(0).porcentaje()).isEqualTo(100.0);
    }
}
