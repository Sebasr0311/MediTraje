package com.meditriaje.service;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.followup.CrearSeguimientoRequest;
import com.meditriaje.dto.followup.ReportarEvolucionRequest;
import com.meditriaje.dto.followup.SeguimientoResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Atencion;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.SeguimientoPostAtencion;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.SeguimientoRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUpServiceTest {

    @Mock
    private SeguimientoRepository seguimientoRepository;

    @Mock
    private AtencionRepository atencionRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private ProfesionalRepository profesionalRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AccesoClinicoService accesoClinicoService;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private AppointmentNotificationService notificationService;

    @InjectMocks
    private FollowUpService followUpService;

    private Usuario usuarioProf;
    private Profesional profesional;
    private Usuario usuarioPac;
    private Paciente paciente;
    private Atencion atencionCerrada;

    @BeforeEach
    void setUp() {
        usuarioProf = new Usuario(1L, "usr-prof-1", "medico@example.com", "hash", "ACTIVO", 0, null, Instant.now());
        profesional = new Profesional(10L, 1L, "prof-1", 100L, "RM-123", "Laura", "Perez", Instant.now(), Instant.now());

        usuarioPac = new Usuario(2L, "usr-pac-1", "paciente@example.com", "hash", "ACTIVO", 0, null, Instant.now());
        paciente = new Paciente(20L, 2L, "pac-1", "CC", "12345678", "Carlos", "Gomez", LocalDate.of(1990, 5, 10), "3001234567", Instant.now(), Instant.now());

        atencionCerrada = new Atencion(
                50L, "at-1", 1000L, 20L, 10L, 5L,
                "Faringitis", "Evolución favorable", "Reposo",
                "CERRADA", Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("prescribirSeguimiento - Éxito: médico autor prescribe seguimiento, audita y notifica")
    void prescribirSeguimiento_exito() {
        when(usuarioRepository.buscarPorPublicId("usr-prof-1")).thenReturn(Optional.of(usuarioProf));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        when(atencionRepository.buscarEntidadPorPublicId("at-1")).thenReturn(Optional.of(atencionCerrada));

        when(seguimientoRepository.guardar(any(SeguimientoPostAtencion.class))).thenReturn(1L);

        SeguimientoResponse resp = new SeguimientoResponse(
                "seg-new-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Laura Perez", "Medicina General",
                "EVOLUCION_SINTOMAS", "Monitorear fiebre",
                LocalDate.now().plusDays(2), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );
        when(seguimientoRepository.buscarPorPublicId(anyString())).thenReturn(Optional.of(resp));

        CrearSeguimientoRequest request = new CrearSeguimientoRequest(
                "EVOLUCION_SINTOMAS",
                "Monitorear fiebre",
                LocalDate.now().plusDays(2)
        );

        SeguimientoResponse resultado = followUpService.prescribirSeguimiento("at-1", request, "usr-prof-1", "127.0.0.1");

        assertThat(resultado).isNotNull();
        assertThat(resultado.tipo()).isEqualTo("EVOLUCION_SINTOMAS");

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(captor.capture());
        assertThat(captor.getValue().accion()).isEqualTo(AccionAuditable.CREACION_SEGUIMIENTO);
    }

    @Test
    @DisplayName("prescribirSeguimiento - Tipo inválido arroja DatosInvalidosException")
    void prescribirSeguimiento_tipoInvalido() {
        when(usuarioRepository.buscarPorPublicId("usr-prof-1")).thenReturn(Optional.of(usuarioProf));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));

        CrearSeguimientoRequest request = new CrearSeguimientoRequest(
                "TIPO_DESCONOCIDO",
                "Indicaciones",
                null
        );

        assertThatThrownBy(() -> followUpService.prescribirSeguimiento("at-1", request, "usr-prof-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Tipo de seguimiento invalido");
    }

    @Test
    @DisplayName("prescribirSeguimiento - Fecha sugerida en el pasado arroja DatosInvalidosException")
    void prescribirSeguimiento_fechaPasada() {
        when(usuarioRepository.buscarPorPublicId("usr-prof-1")).thenReturn(Optional.of(usuarioProf));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));

        CrearSeguimientoRequest request = new CrearSeguimientoRequest(
                "CONTROL_MEDICO",
                "Indicaciones",
                LocalDate.now().minusDays(1)
        );

        assertThatThrownBy(() -> followUpService.prescribirSeguimiento("at-1", request, "usr-prof-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("no puede ser anterior a la fecha actual");
    }

    @Test
    @DisplayName("prescribirSeguimiento - Atención no cerrada arroja DatosInvalidosException")
    void prescribirSeguimiento_atencionNoCerrada() {
        Atencion atencionAbierta = new Atencion(
                50L, "at-1", 1000L, 20L, 10L, null,
                "Motivo", null, null,
                "ABIERTA", null, Instant.now()
        );

        when(usuarioRepository.buscarPorPublicId("usr-prof-1")).thenReturn(Optional.of(usuarioProf));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        when(atencionRepository.buscarEntidadPorPublicId("at-1")).thenReturn(Optional.of(atencionAbierta));

        CrearSeguimientoRequest request = new CrearSeguimientoRequest(
                "CONTROL_MEDICO",
                "Indicaciones",
                LocalDate.now().plusDays(5)
        );

        assertThatThrownBy(() -> followUpService.prescribirSeguimiento("at-1", request, "usr-prof-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Solo se pueden prescribir tareas de seguimiento sobre atenciones medicas cerradas");
    }

    @Test
    @DisplayName("reportarEvolucion - Éxito: paciente reporta evolución sobre seguimiento propio")
    void reportarEvolucion_exito() {
        SeguimientoPostAtencion seg = new SeguimientoPostAtencion(
                100L, "seg-1", 50L, 20L, 10L,
                "EVOLUCION_SINTOMAS", "Monitorear dolor",
                LocalDate.now().plusDays(2), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );

        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioPac));
        when(pacienteRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(seguimientoRepository.buscarEntidadPorPublicId("seg-1")).thenReturn(Optional.of(seg));

        SeguimientoResponse respActualizada = new SeguimientoResponse(
                "seg-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Laura Perez", "Medicina General",
                "EVOLUCION_SINTOMAS", "Monitorear dolor",
                LocalDate.now().plusDays(2), "COMPLETADO",
                Instant.now(), "Me encuentro sin síntomas.", Instant.now(), Instant.now()
        );
        when(seguimientoRepository.buscarPorPublicId("seg-1")).thenReturn(Optional.of(respActualizada));

        ReportarEvolucionRequest req = new ReportarEvolucionRequest("Me encuentro sin síntomas.");

        SeguimientoResponse res = followUpService.reportarEvolucion("seg-1", req, "usr-pac-1", "127.0.0.1");

        assertThat(res.estado()).isEqualTo("COMPLETADO");
        assertThat(res.reportePaciente()).isEqualTo("Me encuentro sin síntomas.");

        verify(seguimientoRepository).registrarReportePaciente(eq(100L), eq("Me encuentro sin síntomas."), any());
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("reportarEvolucion - Paciente ajeno arroja AccesoNoAutorizadoException")
    void reportarEvolucion_pacienteAjeno() {
        SeguimientoPostAtencion segDeOtro = new SeguimientoPostAtencion(
                100L, "seg-1", 50L, 999L, 10L, // pacienteId distinto a 20L
                "EVOLUCION_SINTOMAS", "Monitorear dolor",
                LocalDate.now().plusDays(2), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );

        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioPac));
        when(pacienteRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(seguimientoRepository.buscarEntidadPorPublicId("seg-1")).thenReturn(Optional.of(segDeOtro));

        ReportarEvolucionRequest req = new ReportarEvolucionRequest("Reporte");

        assertThatThrownBy(() -> followUpService.reportarEvolucion("seg-1", req, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("No tiene autorizacion para reportar evolucion en un seguimiento ajeno");
    }

    @Test
    @DisplayName("reportarEvolucion - Seguimiento ya COMPLETADO arroja DatosInvalidosException")
    void reportarEvolucion_yaCompletado() {
        SeguimientoPostAtencion segCompletado = new SeguimientoPostAtencion(
                100L, "seg-1", 50L, 20L, 10L,
                "EVOLUCION_SINTOMAS", "Monitorear dolor",
                LocalDate.now().plusDays(2), "COMPLETADO",
                Instant.now(), "Reporte previo", Instant.now(), Instant.now()
        );

        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioPac));
        when(pacienteRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(seguimientoRepository.buscarEntidadPorPublicId("seg-1")).thenReturn(Optional.of(segCompletado));

        ReportarEvolucionRequest req = new ReportarEvolucionRequest("Nuevo reporte");

        assertThatThrownBy(() -> followUpService.reportarEvolucion("seg-1", req, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("ya se encuentra completado");
    }

    @Test
    @DisplayName("obtenerPorPublicId - Administrador recibe 403 Forbidden")
    void obtenerPorPublicId_admin_retorna403() {
        assertThatThrownBy(() -> followUpService.obtenerPorPublicId(
                "seg-1", "usr-admin-1", List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))
        )).isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("El personal administrativo no tiene acceso a contenido clinico");
    }

    @Test
    @DisplayName("listarMisSeguimientos - Paciente autenticado obtiene respuesta paginada")
    void listarMisSeguimientos_exito() {
        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioPac));
        when(pacienteRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(paciente));

        SeguimientoResponse item = new SeguimientoResponse(
                "seg-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Laura Perez", "Medicina General",
                "CONTROL_MEDICO", "Control de TA",
                LocalDate.now().plusDays(5), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );
        when(seguimientoRepository.listarPorPacienteId(eq(20L), any(), eq(0), eq(10)))
                .thenReturn(List.of(item));
        when(seguimientoRepository.contarPorPacienteId(eq(20L), any()))
                .thenReturn(1);

        PaginatedResponse<SeguimientoResponse> res = followUpService.listarMisSeguimientos(
                "usr-pac-1", null, 0, 10
        );

        assertThat(res.content()).hasSize(1);
        assertThat(res.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancelarSeguimiento - Éxito: médico cancela seguimiento pendiente")
    void cancelarSeguimiento_exito() {
        SeguimientoPostAtencion seg = new SeguimientoPostAtencion(
                100L, "seg-1", 50L, 20L, 10L,
                "EXAMEN_PENDIENTE", "Cuadro hemático",
                LocalDate.now().plusDays(3), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );

        when(usuarioRepository.buscarPorPublicId("usr-prof-1")).thenReturn(Optional.of(usuarioProf));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        when(seguimientoRepository.buscarEntidadPorPublicId("seg-1")).thenReturn(Optional.of(seg));

        SeguimientoResponse cancelado = new SeguimientoResponse(
                "seg-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Laura Perez", "Medicina General",
                "EXAMEN_PENDIENTE", "Cuadro hemático",
                LocalDate.now().plusDays(3), "CANCELADO",
                null, null, Instant.now(), Instant.now()
        );
        when(seguimientoRepository.buscarPorPublicId("seg-1")).thenReturn(Optional.of(cancelado));

        SeguimientoResponse res = followUpService.cancelarSeguimiento(
                "seg-1", "usr-prof-1", List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"))
        );

        assertThat(res.estado()).isEqualTo("CANCELADO");
        verify(seguimientoRepository).actualizarEstado(100L, "CANCELADO");
    }
}
