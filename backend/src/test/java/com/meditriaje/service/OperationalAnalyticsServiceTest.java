package com.meditriaje.service;

import com.meditriaje.dto.emergency.ItemColaUrgenciaResponse;
import com.meditriaje.dto.hospital.CensoCamasResponse;
import com.meditriaje.dto.operational.*;
import com.meditriaje.model.*;
import com.meditriaje.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationalAnalyticsServiceTest {

    @Mock
    private AlertaOperativaRepository alertaRepository;

    @Mock
    private SeguimientoQrRepository seguimientoQrRepository;

    @Mock
    private HospitalService hospitalService;

    @Mock
    private SedeRepository sedeRepository;

    @Mock
    private EpisodioAtencionRepository episodioRepository;

    @Mock
    private ValoracionTriajeRepository triajeRepository;

    @Mock
    private IdentidadProvisionalRepository identidadRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AuditoriaService auditoriaService;

    private Clock clock;
    private OperationalAnalyticsService service;

    private final Instant now = Instant.parse("2026-03-30T10:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        service = new OperationalAnalyticsService(
                alertaRepository,
                seguimientoQrRepository,
                hospitalService,
                sedeRepository,
                episodioRepository,
                triajeRepository,
                identidadRepository,
                usuarioRepository,
                auditoriaService,
                clock
        );
    }

    @Test
    @DisplayName("O01/O02: obtenerDashboardHospitalario calcula ocupación, esperas y genera alerta si saturación >= 85%")
    void obtenerDashboardHospitalario_calculaYGeneraAlerta() {
        Sede sede = new Sede(1L, 10L, "sede-1", "Sede Central", "Dir", "Valledupar", "ACTIVO");
        when(sedeRepository.buscarPorPublicId("sede-1")).thenReturn(Optional.of(sede));

        CensoCamasResponse censo = new CensoCamasResponse(
                "sede-1", "Sede Central", 100, 88, 10, 2, 0, 88.0, List.of()
        );
        when(hospitalService.obtenerCensoCamas("sede-1")).thenReturn(censo);

        ItemColaUrgenciaResponse itemCola = new ItemColaUrgenciaResponse(
                "ep-1", null, "Paciente NN", "NN-001", "sede-1", "Sede Central",
                "URGENCIA", "EN_VALORACION", "II", "Dolor torácico", 40L, now.minusSeconds(2400), "Dr. Médico"
        );
        when(episodioRepository.listarColaUrgenciaPorSede(1L, null)).thenReturn(List.of(itemCola));
        when(alertaRepository.listarPorSede(1L, "ACTIVA")).thenReturn(List.of());

        DashboardHospitalarioResponse resp = service.obtenerDashboardHospitalario("sede-1", "usr-admin", "127.0.0.1");

        assertNotNull(resp);
        assertEquals(88.0, resp.tasaOcupacionPorcentaje());
        assertEquals(88, resp.camasOcupadas());
        assertEquals(1, resp.episodiosUrgenciasActivos());
        assertEquals(1L, resp.episodiosPorNivelTriaje().get("II"));

        // Alerta generada por ocupación >= 85% y demora Triaje II > 30 min (40 min)
        verify(alertaRepository, atLeastOnce()).guardar(any(AlertaOperativa.class));
    }

    @Test
    @DisplayName("O02: reconocerAlerta actualiza estado a RECONOCIDA y audita evento")
    void reconocerAlerta_exitoso() {
        AlertaOperativa alerta = new AlertaOperativa(
                5L, "alerta-1", 1L, "SATURACION_CAMAS", "CRITICA", "Ocupación al 90%", "ACTIVA", null, null, null, now
        );
        when(alertaRepository.buscarPorPublicId("alerta-1")).thenReturn(Optional.of(alerta));

        Usuario usuario = new Usuario(10L, "usr-admin", "admin@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(usuarioRepository.buscarPorPublicId("usr-admin")).thenReturn(Optional.of(usuario));

        ReconocerAlertaRequest req = new ReconocerAlertaRequest("Se activó pabellón de expansión");
        service.reconocerAlerta("alerta-1", req, "usr-admin", "127.0.0.1");

        verify(alertaRepository).reconocerAlerta(eq(5L), eq(10L), eq(now), eq("Se activó pabellón de expansión"));
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("O03: generarQrSeguimiento genera token QROP seguro sin exponer PHI")
    void generarQrSeguimiento_creaTokenExitosamente() {
        EpisodioAtencion ep = new EpisodioAtencion(
                50L, "ep-1", null, "URGENCIAS", 1L, "EN_VALORACION", now, null, "Urgencia", now, null
        );
        when(episodioRepository.buscarPorPublicId("ep-1")).thenReturn(Optional.of(ep));

        Usuario usuario = new Usuario(20L, "usr-enf", "enf@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(usuarioRepository.buscarPorPublicId("usr-enf")).thenReturn(Optional.of(usuario));

        when(seguimientoQrRepository.buscarActivoPorEpisodioId(50L)).thenReturn(Optional.empty());

        SeguimientoIntrahospitalarioQr qrGuardado = new SeguimientoIntrahospitalarioQr(
                1L, "qr-pub", 50L, "QROP-1234567890", "URGENCIAS / SALA DE OBSERVACION", "ACTIVO", now, now.plusSeconds(86400)
        );
        when(seguimientoQrRepository.guardar(any(SeguimientoIntrahospitalarioQr.class))).thenReturn(qrGuardado);
        when(identidadRepository.buscarPorEpisodioId(50L)).thenReturn(Optional.empty());
        when(triajeRepository.buscarUltimaPorEpisodioId(50L)).thenReturn(Optional.empty());

        QrSeguimientoResponse resp = service.generarQrSeguimiento("ep-1", "usr-enf", "127.0.0.1");

        assertNotNull(resp);
        assertEquals("QROP-1234567890", resp.tokenQr());
        assertEquals("URGENCIAS / SALA DE OBSERVACION", resp.ubicacionActual());
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("O03: consultarPorTokenQr recupera ubicación sin exponer historial clínico")
    void consultarPorTokenQr_retornaUbicacionSegura() {
        SeguimientoIntrahospitalarioQr qr = new SeguimientoIntrahospitalarioQr(
                1L, "qr-pub", 50L, "QROP-TEST", "PABELLON SAN ROQUE / CAMA 102", "ACTIVO", now, now.plusSeconds(86400)
        );
        when(seguimientoQrRepository.buscarPorToken("QROP-TEST")).thenReturn(Optional.of(qr));

        EpisodioAtencion ep = new EpisodioAtencion(
                50L, "ep-1", null, "HOSPITALIZACION", 1L, "EN_PABELLON", now, null, "Hospitalizado", now, null
        );
        when(episodioRepository.buscarPorId(50L)).thenReturn(Optional.of(ep));

        IdentidadProvisional idProv = new IdentidadProvisional(
                1L, "id-prov-uuid", 50L, "NN-20260330-0001", "Físico", 30, "M", "ALERTA", "PENDIENTE", null, null, null, now
        );
        when(identidadRepository.buscarPorEpisodioId(50L)).thenReturn(Optional.of(idProv));
        when(triajeRepository.buscarUltimaPorEpisodioId(50L)).thenReturn(Optional.empty());

        QrSeguimientoResponse resp = service.consultarPorTokenQr("QROP-TEST", "127.0.0.1");

        assertNotNull(resp);
        assertEquals("NN-20260330-0001", resp.codigoIdentidadProvisional());
        assertEquals("PABELLON SAN ROQUE / CAMA 102", resp.ubicacionActual());
        assertEquals("EN_PABELLON", resp.estadoEpisodio());
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }
}
