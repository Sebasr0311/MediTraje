package com.meditriaje.service;

import com.meditriaje.dto.report.DistribucionItemDto;
import com.meditriaje.dto.report.MetricasCitasDto;
import com.meditriaje.dto.report.MetricasTriajeDto;
import com.meditriaje.dto.report.ResumenOperativoResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.ReporteRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-04T12:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    @Mock
    private ReporteRepository reporteRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AuditoriaService auditoriaService;

    private ReporteService reporteService;

    private final Usuario adminUser = new Usuario(
            1L, "usr-admin-uuid", "admin@meditriaje.com", "hash", "ACTIVO", 0, null, AHORA, false
    );


    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(AHORA, ZONE);
        reporteService = new ReporteService(reporteRepository, usuarioRepository, auditoriaService, fixedClock);
    }

    @Test
    @DisplayName("obtenerResumenOperativo: consolida métricas, calcula tasas y audita")
    void obtenerResumenOperativo_exito() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 4);

        when(usuarioRepository.buscarPorPublicId("usr-admin-uuid")).thenReturn(Optional.of(adminUser));

        // Citas: 10 atendidas, 2 canceladas, 1 no asistió, 7 programadas = total 20
        when(reporteRepository.contarCitasPorEstado(any(), any())).thenReturn(Map.of(
                "ATENDIDA", 10L,
                "CANCELADA", 2L,
                "NO_ASISTIO", 1L,
                "PROGRAMADA", 7L
        ));
        when(reporteRepository.obtenerCitasPorEspecialidad(any(), any())).thenReturn(List.of(
                new DistribucionItemDto("esp-1", "Medicina General", 15L, 75.0)
        ));
        when(reporteRepository.obtenerCitasPorSede(any(), any())).thenReturn(List.of(
                new DistribucionItemDto("sed-1", "Sede Norte", 20L, 100.0)
        ));

        // Triaje: 2 Nivel I, 3 Nivel II, 5 Nivel III = total 10; 2 emergencias
        when(reporteRepository.contarTriajesPorNivel(any(), any())).thenReturn(Map.of(
                "I", 2L,
                "II", 3L,
                "III", 5L
        ));
        when(reporteRepository.contarTriajesEmergencia(any(), any())).thenReturn(2L);
        when(reporteRepository.obtenerTriajesPorRuta(any(), any())).thenReturn(List.of(
                new DistribucionItemDto("URGENCIAS", "URGENCIAS", 2L, 20.0)
        ));

        // Farmacia
        when(reporteRepository.contarRecetasEmitidas(any(), any())).thenReturn(10L);
        when(reporteRepository.contarRecetasPorEstadoDispensacion(any(), any())).thenReturn(Map.of(
                "PENDIENTE", 3L,
                "DISPENSADA_PARCIAL", 2L,
                "DISPENSADA_TOTAL", 5L
        ));
        when(reporteRepository.contarUnidadesDispensadas(any(), any())).thenReturn(45L);

        // Break-Glass
        when(reporteRepository.contarActivacionesBreakGlass(any(), any())).thenReturn(4L);
        when(reporteRepository.contarBreakGlassActivos(any())).thenReturn(1L);
        when(reporteRepository.obtenerBreakGlassPorEspecialidad(any(), any())).thenReturn(List.of(
                new DistribucionItemDto("esp-1", "Urgencias", 4L, 100.0)
        ));

        ResumenOperativoResponse response = reporteService.obtenerResumenOperativo(
                desde, hasta, "usr-admin-uuid", "192.168.1.100"
        );

        assertThat(response).isNotNull();
        // Citas
        assertThat(response.citas().totalCitas()).isEqualTo(20L);
        assertThat(response.citas().atendidas()).isEqualTo(10L);
        assertThat(response.citas().tasaCumplimiento()).isEqualTo(50.0); // 10 / 20 * 100
        assertThat(response.citas().tasaCancelacion()).isEqualTo(15.0); // (2+1) / 20 * 100 = 15.0%

        // Triaje
        assertThat(response.triaje().totalTriajes()).isEqualTo(10L);
        assertThat(response.triaje().emergencias()).isEqualTo(2L);
        assertThat(response.triaje().tasaEmergencia()).isEqualTo(20.0); // 2 / 10 * 100

        // Farmacia
        assertThat(response.farmacia().totalRecetas()).isEqualTo(10L);
        assertThat(response.farmacia().dispensadasTotal()).isEqualTo(5L);
        assertThat(response.farmacia().totalUnidadesDispensadas()).isEqualTo(45L);

        // Break-Glass
        assertThat(response.breakGlass().totalActivaciones()).isEqualTo(4L);
        assertThat(response.breakGlass().activasVigentes()).isEqualTo(1L);

        // Auditoría
        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(captor.capture());
        EventoAuditoria evento = captor.getValue();
        assertThat(evento.accion()).isEqualTo(AccionAuditable.CONSULTA_REPORTE_ADMINISTRATIVO);
        assertThat(evento.usuarioId()).isEqualTo(1L);
        assertThat(evento.tipoRecurso()).isEqualTo("REPORTE_OPERATIVO");
        assertThat(evento.recursoPublicId()).isEqualTo("RESUMEN_OPERATIVO");
    }

    @Test
    @DisplayName("obtenerReporteCitas: calcula métricas específicas y maneja ceros sin error")
    void obtenerReporteCitas_sinDatos() {
        when(reporteRepository.contarCitasPorEstado(any(), any())).thenReturn(Map.of());
        when(reporteRepository.obtenerCitasPorEspecialidad(any(), any())).thenReturn(List.of());
        when(reporteRepository.obtenerCitasPorSede(any(), any())).thenReturn(List.of());

        MetricasCitasDto dto = reporteService.obtenerReporteCitas(null, null, null, "127.0.0.1");

        assertThat(dto.totalCitas()).isZero();
        assertThat(dto.tasaCumplimiento()).isZero();
        assertThat(dto.tasaCancelacion()).isZero();
    }

    @Test
    @DisplayName("obtenerReporteTriaje: calcula métricas específicas de triaje")
    void obtenerReporteTriaje_exito() {
        when(reporteRepository.contarTriajesPorNivel(any(), any())).thenReturn(Map.of(
                "IV", 5L,
                "V", 5L
        ));
        when(reporteRepository.contarTriajesEmergencia(any(), any())).thenReturn(0L);
        when(reporteRepository.obtenerTriajesPorRuta(any(), any())).thenReturn(List.of());

        MetricasTriajeDto dto = reporteService.obtenerReporteTriaje(null, null, null, "127.0.0.1");

        assertThat(dto.totalTriajes()).isEqualTo(10L);
        assertThat(dto.emergencias()).isZero();
        assertThat(dto.tasaEmergencia()).isZero();
    }

    @Test
    @DisplayName("validarRangoFechas: rechaza si fechaDesde es posterior a fechaHasta")
    void validarRangoFechas_invalido_lanzaExcepcion() {
        LocalDate desde = LocalDate.of(2026, 10, 10);
        LocalDate hasta = LocalDate.of(2026, 10, 5);

        assertThatThrownBy(() -> reporteService.obtenerResumenOperativo(desde, hasta, "admin", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("La fecha inicial no puede ser posterior a la fecha final.");
    }
}
