package com.meditriaje.service;

import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.clinical.CerrarAtencionRequest;
import com.meditriaje.dto.clinical.CrearEnmiendaRequest;
import com.meditriaje.dto.clinical.EnmiendaResponse;
import com.meditriaje.dto.clinical.IniciarAtencionRequest;
import com.meditriaje.dto.clinical.SignosVitalesDto;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Atencion;
import com.meditriaje.model.Cita;
import com.meditriaje.model.DiagnosticoCie10;
import com.meditriaje.model.DisponibilidadSlot;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.SignoVital;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.CitaRepository;
import com.meditriaje.repository.DiagnosticoCie10Repository;
import com.meditriaje.repository.DisponibilidadSlotRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link ClinicalAttentionService}.
 * Verifica el ciclo de vida de atención médica, inmutabilidad, reglas asistenciales y auditoría (HU-07, HU-09).
 */
@ExtendWith(MockitoExtension.class)
class ClinicalAttentionServiceTest {

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    private static final Instant AHORA = Instant.parse("2026-10-03T18:30:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private ProfesionalRepository profesionalRepository;

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private DisponibilidadSlotRepository disponibilidadSlotRepository;

    @Mock
    private AtencionRepository atencionRepository;

    @Mock
    private DiagnosticoCie10Repository diagnosticoCie10Repository;

    @Mock
    private AccesoClinicoService accesoClinicoService;

    @Mock
    private AuditoriaService auditoriaService;

    private ClinicalAttentionService clinicalAttentionService;

    private final Clock clock = Clock.fixed(AHORA, ZONE_BOGOTA);

    private final Usuario usuarioMedico = new Usuario(10L, "usr-med-1", "medico@test.com", "hash", "ACTIVO", 0, null, AHORA, false);
    private final Profesional profesionalMedico = new Profesional(50L, 10L, "prof-1", 1L, "RM-12345", "Carlos", "Gomez", AHORA, null);
    private final Cita citaValida = new Cita(100L, "cita-uuid-1", 200L, 300L, null, null, "PROGRAMADA", null, AHORA, null);
    private final DisponibilidadSlot slotValido = new DisponibilidadSlot(200L, "slot-uuid-1", 50L, 1L, 1L, AHORA, AHORA.plusSeconds(1800), "PRESENCIAL", "OCUPADO");

    @BeforeEach
    void setUp() {
        clinicalAttentionService = new ClinicalAttentionService(
                usuarioRepository,
                pacienteRepository,
                profesionalRepository,
                citaRepository,
                disponibilidadSlotRepository,
                atencionRepository,
                diagnosticoCie10Repository,
                accesoClinicoService,
                auditoriaService,
                clock
        );
    }

    @Test
    @DisplayName("iniciarAtencion - éxito: crea atención en estado ABIERTA, pasa cita a CONFIRMADA y audita")
    void iniciarAtencion_exito() {
        IniciarAtencionRequest request = new IniciarAtencionRequest("cita-uuid-1");

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaValida));
        when(disponibilidadSlotRepository.buscarPorId(200L)).thenReturn(Optional.of(slotValido));
        when(atencionRepository.existePorCitaId(100L)).thenReturn(false);
        when(atencionRepository.crear(any(Atencion.class))).thenReturn(1L);

        AtencionResponse mockResponse = new AtencionResponse(
                "atencion-uuid-1", "cita-uuid-1", "pac-uuid-1", "Juan Perez",
                "prof-1", "Carlos Gomez", "Medicina General",
                "ABIERTA", AHORA, null, null, null, null, null, null, null
        );
        when(atencionRepository.buscarDetallePorPublicId(any())).thenReturn(Optional.of(mockResponse));

        AtencionResponse response = clinicalAttentionService.iniciarAtencion(request, "usr-med-1", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.estado()).isEqualTo("ABIERTA");

        // Verifica que la cita pasó a CONFIRMADA
        verify(citaRepository).actualizarEstado(100L, "CONFIRMADA", null);

        // Verifica guardado de atención
        ArgumentCaptor<Atencion> atencionCaptor = ArgumentCaptor.forClass(Atencion.class);
        verify(atencionRepository).crear(atencionCaptor.capture());
        Atencion atencionGuardada = atencionCaptor.getValue();
        assertThat(atencionGuardada.citaId()).isEqualTo(100L);
        assertThat(atencionGuardada.pacienteId()).isEqualTo(300L);
        assertThat(atencionGuardada.profesionalId()).isEqualTo(50L);
        assertThat(atencionGuardada.estado()).isEqualTo("ABIERTA");

        // Verifica auditoría sin datos clínicos
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditable.CREACION_ATENCION);
        assertThat(eventoCaptor.getValue().tipoRecurso()).isEqualTo("ATENCION");
    }

    @Test
    @DisplayName("iniciarAtencion - rechaza si el usuario no es profesional asistencial (403)")
    void iniciarAtencion_rechazaSiUsuarioNoEsProfesional() {
        IniciarAtencionRequest request = new IniciarAtencionRequest("cita-uuid-1");
        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clinicalAttentionService.iniciarAtencion(request, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Usuario no registrado como profesional asistencial.");
    }

    @Test
    @DisplayName("iniciarAtencion - rechaza si el profesional no está asignado a la cita (403)")
    void iniciarAtencion_rechazaSiProfesionalNoCorrespondeACita() {
        IniciarAtencionRequest request = new IniciarAtencionRequest("cita-uuid-1");
        DisponibilidadSlot slotOtroMedico = new DisponibilidadSlot(200L, "slot-uuid-1", 999L, 1L, 1L, AHORA, AHORA.plusSeconds(1800), "PRESENCIAL", "OCUPADO");

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaValida));
        when(disponibilidadSlotRepository.buscarPorId(200L)).thenReturn(Optional.of(slotOtroMedico));

        assertThatThrownBy(() -> clinicalAttentionService.iniciarAtencion(request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("El profesional no esta asignado a esta cita medica.");
    }

    @Test
    @DisplayName("iniciarAtencion - rechaza si la cita no está en estado válida (400)")
    void iniciarAtencion_rechazaSiCitaNoEstaEnEstadoValido() {
        IniciarAtencionRequest request = new IniciarAtencionRequest("cita-uuid-1");
        Cita citaCancelada = new Cita(100L, "cita-uuid-1", 200L, 300L, null, null, "CANCELADA", null, AHORA, null);

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaCancelada));
        when(disponibilidadSlotRepository.buscarPorId(200L)).thenReturn(Optional.of(slotValido));

        assertThatThrownBy(() -> clinicalAttentionService.iniciarAtencion(request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("La cita no se encuentra en un estado valido");
    }

    @Test
    @DisplayName("iniciarAtencion - rechaza si ya existe una atención para la cita (400)")
    void iniciarAtencion_rechazaSiYaExisteAtencionParaCita() {
        IniciarAtencionRequest request = new IniciarAtencionRequest("cita-uuid-1");

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaValida));
        when(disponibilidadSlotRepository.buscarPorId(200L)).thenReturn(Optional.of(slotValido));
        when(atencionRepository.existePorCitaId(100L)).thenReturn(true);

        assertThatThrownBy(() -> clinicalAttentionService.iniciarAtencion(request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Ya existe una atencion clinica registrada");
    }

    @Test
    @DisplayName("cerrarAtencion - éxito con diagnóstico CIE-10 y signos vitales: cierra atención, cita pasa a ATENDIDA y audita")
    void cerrarAtencion_exito_conSignosVitales() {
        String atencionPublicId = "atencion-uuid-1";
        SignosVitalesDto signos = new SignosVitalesDto(120, 80, 75, 16, new BigDecimal("36.5"), 98, new BigDecimal("70.5"), new BigDecimal("172.0"));
        CerrarAtencionRequest request = new CerrarAtencionRequest("J00", "Cefalea y congestion nasal", "Cuadro viral agudo", "Reposo e hidratacion oral", signos);

        Atencion atencionAbierta = new Atencion(1L, atencionPublicId, 100L, 300L, 50L, "ABIERTA");
        DiagnosticoCie10 cie10 = new DiagnosticoCie10(15L, "J00", "Rinofaringitis aguda (resfriado comun)", "ACTIVO");
        Cita citaConfirmada = new Cita(100L, "cita-uuid-1", 200L, 300L, null, null, "CONFIRMADA", null, AHORA, null);

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(atencionRepository.buscarEntidadPorPublicId(atencionPublicId)).thenReturn(Optional.of(atencionAbierta));
        when(diagnosticoCie10Repository.buscarPorCodigo("J00")).thenReturn(Optional.of(cie10));
        when(citaRepository.buscarEntidadPorId(100L)).thenReturn(Optional.of(citaConfirmada));

        AtencionResponse mockResponse = new AtencionResponse(
                atencionPublicId, "cita-uuid-1", "pac-uuid-1", "Juan Perez",
                "prof-1", "Carlos Gomez", "Medicina General",
                "CERRADA", AHORA.minusSeconds(1200), AHORA, "J00", "Rinofaringitis aguda (resfriado comun)",
                "Cefalea y congestion nasal", "Cuadro viral agudo", "Reposo e hidratacion oral", signos
        );
        when(atencionRepository.buscarDetallePorPublicId(atencionPublicId)).thenReturn(Optional.of(mockResponse));

        AtencionResponse response = clinicalAttentionService.cerrarAtencion(atencionPublicId, request, "usr-med-1", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.estado()).isEqualTo("CERRADA");

        // Verifica inserción de signos vitales
        ArgumentCaptor<SignoVital> svCaptor = ArgumentCaptor.forClass(SignoVital.class);
        verify(atencionRepository).guardarSignosVitales(svCaptor.capture());
        SignoVital sv = svCaptor.getValue();
        assertThat(sv.atencionId()).isEqualTo(1L);
        assertThat(sv.presionSistolica()).isEqualTo(120);
        assertThat(sv.presionDiastolica()).isEqualTo(80);

        // Verifica cierre de atención en BD
        verify(atencionRepository).cerrarAtencion(1L, 15L, "Cefalea y congestion nasal", "Cuadro viral agudo", "Reposo e hidratacion oral", AHORA);

        // Verifica transición de cita a ATENDIDA
        verify(citaRepository).actualizarEstado(100L, "ATENDIDA", null);

        // Verifica auditoría sin datos clínicos
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditable.CIERRE_ATENCION);
        assertThat(eventoCaptor.getValue().tipoRecurso()).isEqualTo("ATENCION");
        assertThat(eventoCaptor.getValue().recursoPublicId()).isEqualTo(atencionPublicId);
    }

    @Test
    @DisplayName("cerrarAtencion - rechaza si la atención ya está CERRADA (inmutabilidad ADR-008)")
    void cerrarAtencion_rechazaSiAtencionYaEstaCerrada() {
        String atencionPublicId = "atencion-uuid-cerrada";
        CerrarAtencionRequest request = new CerrarAtencionRequest("J00", "Motivo", "Evolucion", "Indicaciones", null);
        Atencion atencionCerrada = new Atencion(1L, atencionPublicId, 100L, 300L, 50L, "CERRADA");

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(atencionRepository.buscarEntidadPorPublicId(atencionPublicId)).thenReturn(Optional.of(atencionCerrada));

        assertThatThrownBy(() -> clinicalAttentionService.cerrarAtencion(atencionPublicId, request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("ya se encuentra CERRADA y es inmutable");
    }

    @Test
    @DisplayName("cerrarAtencion - rechaza si el profesional autenticado no es el asignado a la atención (403)")
    void cerrarAtencion_rechazaSiProfesionalNoEsElAsignado() {
        String atencionPublicId = "atencion-uuid-1";
        CerrarAtencionRequest request = new CerrarAtencionRequest("J00", "Motivo", "Evolucion", "Indicaciones", null);
        Atencion atencionOtroMedico = new Atencion(1L, atencionPublicId, 100L, 300L, 999L, "ABIERTA");

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(atencionRepository.buscarEntidadPorPublicId(atencionPublicId)).thenReturn(Optional.of(atencionOtroMedico));

        assertThatThrownBy(() -> clinicalAttentionService.cerrarAtencion(atencionPublicId, request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("No tiene autorizacion para cerrar la atencion de otro profesional.");
    }

    @Test
    @DisplayName("cerrarAtencion - rechaza si el código CIE-10 no existe o está inactivo (400)")
    void cerrarAtencion_rechazaSiDiagnosticoInvalidoOInactivo() {
        String atencionPublicId = "atencion-uuid-1";
        CerrarAtencionRequest request = new CerrarAtencionRequest("COD_INEXISTENTE", "Motivo", "Evolucion", "Indicaciones", null);
        Atencion atencionAbierta = new Atencion(1L, atencionPublicId, 100L, 300L, 50L, "ABIERTA");

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(atencionRepository.buscarEntidadPorPublicId(atencionPublicId)).thenReturn(Optional.of(atencionAbierta));
        when(diagnosticoCie10Repository.buscarPorCodigo("COD_INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clinicalAttentionService.cerrarAtencion(atencionPublicId, request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Codigo de diagnostico CIE-10 no encontrado");

        // Caso inactivo
        CerrarAtencionRequest reqInactivo = new CerrarAtencionRequest("INACTIVO", "Motivo", "Evolucion", "Indicaciones", null);
        DiagnosticoCie10 diagInactivo = new DiagnosticoCie10(99L, "INACTIVO", "Inactivo", "INACTIVO");
        when(diagnosticoCie10Repository.buscarPorCodigo("INACTIVO")).thenReturn(Optional.of(diagInactivo));

        assertThatThrownBy(() -> clinicalAttentionService.cerrarAtencion(atencionPublicId, reqInactivo, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("se encuentra inactivo");
    }

    @Test
    @DisplayName("cerrarAtencion - rechaza si presión arterial sistólica es menor o igual a diastólica (400)")
    void cerrarAtencion_rechazaSiPresionSistolicaMenorOIgualADiastolica() {
        String atencionPublicId = "atencion-uuid-1";
        SignosVitalesDto signosInvalidos = new SignosVitalesDto(80, 120, null, null, null, null, null, null);
        CerrarAtencionRequest request = new CerrarAtencionRequest("J00", "Motivo", "Evolucion", "Indicaciones", signosInvalidos);
        Atencion atencionAbierta = new Atencion(1L, atencionPublicId, 100L, 300L, 50L, "ABIERTA");
        DiagnosticoCie10 cie10 = new DiagnosticoCie10(15L, "J00", "Rinofaringitis", "ACTIVO");

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(atencionRepository.buscarEntidadPorPublicId(atencionPublicId)).thenReturn(Optional.of(atencionAbierta));
        when(diagnosticoCie10Repository.buscarPorCodigo("J00")).thenReturn(Optional.of(cie10));

        assertThatThrownBy(() -> clinicalAttentionService.cerrarAtencion(atencionPublicId, request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("La presion arterial sistolica debe ser estrictamente mayor");

        verify(atencionRepository, never()).guardarSignosVitales(any());
        verify(atencionRepository, never()).cerrarAtencion(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("obtenerPorPublicId - valida relación asistencial, audita consulta y retorna detalle")
    void obtenerPorPublicId_exito() {
        String atencionPublicId = "atencion-uuid-1";
        Atencion atencion = new Atencion(1L, atencionPublicId, 100L, 300L, 50L, "CERRADA");
        AtencionResponse mockResponse = new AtencionResponse(
                atencionPublicId, "cita-uuid-1", "pac-uuid-1", "Juan Perez",
                "prof-1", "Carlos Gomez", "Medicina General",
                "CERRADA", AHORA.minusSeconds(1000), AHORA, "J00", "Rinofaringitis aguda",
                "Motivo", "Evolucion", "Indicaciones", null
        );

        when(atencionRepository.buscarEntidadPorPublicId(atencionPublicId)).thenReturn(Optional.of(atencion));
        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioMedico));
        when(atencionRepository.buscarDetallePorPublicId(atencionPublicId)).thenReturn(Optional.of(mockResponse));

        AtencionResponse response = clinicalAttentionService.obtenerPorPublicId(
                atencionPublicId,
                "usr-pac-1",
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                "127.0.0.1"
        );

        assertThat(response).isNotNull();
        assertThat(response.publicId()).isEqualTo(atencionPublicId);

        // Valida que invocó el servicio centralizado de acceso clínico (ADR-007)
        verify(accesoClinicoService).validarAccesoHistorialClinico(
                eq("usr-pac-1"),
                eq(300L),
                eq(List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")))
        );

        // Valida auditoría inmutable de consulta de historia clínica
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditable.CONSULTA_HISTORIA);
        assertThat(eventoCaptor.getValue().tipoRecurso()).isEqualTo("ATENCION");
        assertThat(eventoCaptor.getValue().recursoPublicId()).isEqualTo(atencionPublicId);
    }

    @Test
    @DisplayName("crearEnmienda - éxito con el mismo profesional autor: registra enmienda inmutable y audita")
    void crearEnmienda_exito_mismoProfesionalAutor() {
        String atencionPublicId = "atencion-uuid-1";
        CrearEnmiendaRequest request = new CrearEnmiendaRequest("Aclaracion de dosis", "Se precisa que la dosis es cada 12 horas.");
        Atencion atencionCerrada = new Atencion(1L, atencionPublicId, 100L, 300L, 50L, "CERRADA");

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(atencionRepository.buscarEntidadPorPublicId(atencionPublicId)).thenReturn(Optional.of(atencionCerrada));
        when(atencionRepository.crearEnmienda(eq(1L), eq(50L), eq("Aclaracion de dosis"), anyString(), eq(AHORA))).thenReturn(10L);

        EnmiendaResponse response = clinicalAttentionService.crearEnmienda(atencionPublicId, request, "usr-med-1", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.profesionalPublicId()).isEqualTo("prof-1");
        assertThat(response.motivo()).isEqualTo("Aclaracion de dosis");
        assertThat(response.contenido()).isEqualTo("Se precisa que la dosis es cada 12 horas.");
        assertThat(response.fechaEnmienda()).isEqualTo(AHORA);

        // Verifica guardado
        verify(atencionRepository).crearEnmienda(1L, 50L, "Aclaracion de dosis", "Se precisa que la dosis es cada 12 horas.", AHORA);

        // Verifica auditoría inmutable
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditable.ENMIENDA_ATENCION);
        assertThat(eventoCaptor.getValue().tipoRecurso()).isEqualTo("ATENCION");
        assertThat(eventoCaptor.getValue().recursoPublicId()).isEqualTo(atencionPublicId);
    }

    @Test
    @DisplayName("crearEnmienda - éxito con otro profesional que tiene relación asistencial activa")
    void crearEnmienda_exito_otroProfesionalConRelacionAsistencial() {
        String atencionPublicId = "atencion-uuid-1";
        CrearEnmiendaRequest request = new CrearEnmiendaRequest("Seguimiento", "Paciente evaluado en control posterior.");
        Atencion atencionCerrada = new Atencion(1L, atencionPublicId, 100L, 300L, 999L, "CERRADA"); // Otro médico fue el autor

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(atencionRepository.buscarEntidadPorPublicId(atencionPublicId)).thenReturn(Optional.of(atencionCerrada));
        when(atencionRepository.crearEnmienda(eq(1L), eq(50L), anyString(), anyString(), eq(AHORA))).thenReturn(11L);

        EnmiendaResponse response = clinicalAttentionService.crearEnmienda(atencionPublicId, request, "usr-med-1", "127.0.0.1");

        assertThat(response).isNotNull();
        // Verifica que se validó la relación asistencial del nuevo médico con el paciente
        verify(accesoClinicoService).validarRelacionAsistencial(50L, 300L);
        verify(atencionRepository).crearEnmienda(1L, 50L, "Seguimiento", "Paciente evaluado en control posterior.", AHORA);
    }

    @Test
    @DisplayName("crearEnmienda - rechaza si la atención está ABIERTA (inmutabilidad ADR-008)")
    void crearEnmienda_rechazaSiAtencionEstaAbierta() {
        String atencionPublicId = "atencion-uuid-1";
        CrearEnmiendaRequest request = new CrearEnmiendaRequest("Aclaracion", "Contenido");
        Atencion atencionAbierta = new Atencion(1L, atencionPublicId, 100L, 300L, 50L, "ABIERTA");

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(atencionRepository.buscarEntidadPorPublicId(atencionPublicId)).thenReturn(Optional.of(atencionAbierta));

        assertThatThrownBy(() -> clinicalAttentionService.crearEnmienda(atencionPublicId, request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Solo es posible registrar enmiendas sobre atenciones clinicas CERRADAS.");

        verify(atencionRepository, never()).crearEnmienda(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("crearEnmienda - rechaza si el usuario autenticado no es profesional asistencial (403)")
    void crearEnmienda_rechazaSiUsuarioNoEsProfesional() {
        String atencionPublicId = "atencion-uuid-1";
        CrearEnmiendaRequest request = new CrearEnmiendaRequest("Aclaracion", "Contenido");

        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clinicalAttentionService.crearEnmienda(atencionPublicId, request, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Usuario no registrado como profesional asistencial.");
    }

    @Test
    @DisplayName("crearEnmienda - rechaza si el profesional ajeno no tiene relación asistencial activa (403)")
    void crearEnmienda_rechazaSiOtroProfesionalNoTieneRelacionAsistencial() {
        String atencionPublicId = "atencion-uuid-1";
        CrearEnmiendaRequest request = new CrearEnmiendaRequest("Aclaracion", "Contenido");
        Atencion atencionCerrada = new Atencion(1L, atencionPublicId, 100L, 300L, 999L, "CERRADA");

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(atencionRepository.buscarEntidadPorPublicId(atencionPublicId)).thenReturn(Optional.of(atencionCerrada));

        org.mockito.Mockito.doThrow(new AccesoNoAutorizadoException("El profesional no cuenta con una relacion asistencial activa con el paciente."))
                .when(accesoClinicoService).validarRelacionAsistencial(50L, 300L);

        assertThatThrownBy(() -> clinicalAttentionService.crearEnmienda(atencionPublicId, request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("El profesional no cuenta con una relacion asistencial activa");

        verify(atencionRepository, never()).crearEnmienda(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("obtenerMiHistoriaClinica - éxito: paciente consulta su propio historial, retorna paginado y audita")
    void obtenerMiHistoriaClinica_exito() {
        String usuarioPublicId = "usr-pac-1";
        Long usuarioId = 20L;
        Long pacienteId = 300L;
        String pacientePublicId = "pac-uuid-1";

        Usuario usuarioPaciente = new Usuario(usuarioId, usuarioPublicId, "paciente@test.com", "hash", "ACTIVO", 0, null, AHORA, false);
        Paciente paciente = new Paciente(pacienteId, usuarioId, pacientePublicId, "CC", "10101010", "Maria", "Lopez", java.time.LocalDate.of(1995, 5, 20), "3001234567");

        AtencionResponse atencion = new AtencionResponse(
                "atencion-uuid-1",
                "cita-uuid-1",
                pacientePublicId,
                "Maria Lopez",
                "prof-1",
                "Carlos Gomez",
                "Medicina General",
                "CERRADA",
                AHORA.minusSeconds(3600),
                AHORA,
                "J00",
                "Rinofaringitis aguda",
                "Congestion nasal",
                "Paciente estable",
                "Reposo e hidratacion",
                null
        );

        when(usuarioRepository.buscarPorPublicId(usuarioPublicId)).thenReturn(Optional.of(usuarioPaciente));
        when(pacienteRepository.buscarPorUsuarioId(usuarioId)).thenReturn(Optional.of(paciente));
        when(atencionRepository.listarHistoriaPaciente(pacienteId, 0, 10)).thenReturn(List.of(atencion));
        when(atencionRepository.contarHistoriaPaciente(pacienteId)).thenReturn(1);

        PaginatedResponse<AtencionResponse> respuesta = clinicalAttentionService.obtenerMiHistoriaClinica(usuarioPublicId, 0, 10, "192.168.1.50");

        assertThat(respuesta).isNotNull();
        assertThat(respuesta.content()).hasSize(1);
        assertThat(respuesta.content().get(0).publicId()).isEqualTo("atencion-uuid-1");
        assertThat(respuesta.totalElements()).isEqualTo(1L);
        assertThat(respuesta.page()).isEqualTo(0);
        assertThat(respuesta.size()).isEqualTo(10);

        ArgumentCaptor<EventoAuditoria> captorAuditoria = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(captorAuditoria.capture());
        EventoAuditoria evento = captorAuditoria.getValue();
        assertThat(evento.usuarioId()).isEqualTo(usuarioId);
        assertThat(evento.accion()).isEqualTo(AccionAuditable.CONSULTA_HISTORIA);
        assertThat(evento.tipoRecurso()).isEqualTo("HISTORIA_CLINICA");
        assertThat(evento.recursoPublicId()).isEqualTo(pacientePublicId);
        assertThat(evento.ipOrigen()).isEqualTo("192.168.1.50");
    }

    @Test
    @DisplayName("obtenerMiHistoriaClinica - rechaza si usuario no está registrado como paciente (403)")
    void obtenerMiHistoriaClinica_rechazaSiUsuarioNoEsPaciente() {
        String usuarioPublicId = "usr-med-1";
        when(usuarioRepository.buscarPorPublicId(usuarioPublicId)).thenReturn(Optional.of(usuarioMedico));
        when(pacienteRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clinicalAttentionService.obtenerMiHistoriaClinica(usuarioPublicId, 0, 10, "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Usuario no registrado como paciente.");

        verify(atencionRepository, never()).listarHistoriaPaciente(any(), eq(0), eq(10));
    }

    @Test
    @DisplayName("obtenerMiHistoriaClinica - rechaza si usuario autenticado no existe en BD (404)")
    void obtenerMiHistoriaClinica_rechazaSiUsuarioNoExiste() {
        String usuarioPublicId = "usr-inexistente";
        when(usuarioRepository.buscarPorPublicId(usuarioPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clinicalAttentionService.obtenerMiHistoriaClinica(usuarioPublicId, 0, 10, "127.0.0.1"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario autenticado no encontrado.");
    }

    @Test
    @DisplayName("obtenerHistoriaClinicaPaciente - médico autorizado por relación o break-glass consulta historia y audita")
    void obtenerHistoriaClinicaPaciente_exito() {
        String pacientePublicId = "pac-100";
        String usuarioMedPublicId = "usr-med-1";
        Long pacienteId = 300L;
        Paciente paciente = new Paciente(
                pacienteId, 30L, pacientePublicId, "CC", "12345678", "Carlos", "Sanchez",
                LocalDate.of(1985, 5, 20), "3001234567", AHORA, AHORA
        );

        when(usuarioRepository.buscarPorPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMedico));
        when(pacienteRepository.buscarPorPublicId(pacientePublicId)).thenReturn(Optional.of(paciente));

        AtencionResponse atencion = new AtencionResponse(
                "atn-uuid-1", "cita-uuid-1", pacientePublicId, "Carlos Sanchez",
                "prof-1", "Carlos Gomez", "Medicina General",
                "CERRADA", AHORA.minusSeconds(1000), AHORA, "J00", "Rinofaringitis aguda",
                "Motivo", "Evolucion", "Indicaciones", null
        );

        when(atencionRepository.listarHistoriaPaciente(pacienteId, 0, 10)).thenReturn(List.of(atencion));
        when(atencionRepository.contarHistoriaPaciente(pacienteId)).thenReturn(1);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        PaginatedResponse<AtencionResponse> response = clinicalAttentionService.obtenerHistoriaClinicaPaciente(
                pacientePublicId,
                usuarioMedPublicId,
                authorities,
                0,
                10,
                "192.168.1.50"
        );

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().get(0).publicId()).isEqualTo("atn-uuid-1");

        // Verifica autorización centralizada (ADR-007, ADR-017)
        verify(accesoClinicoService).validarAccesoHistorialClinico(usuarioMedPublicId, pacientePublicId, authorities);

        // Verifica auditoría
        ArgumentCaptor<EventoAuditoria> captorAuditoria = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(captorAuditoria.capture());
        EventoAuditoria evento = captorAuditoria.getValue();
        assertThat(evento.accion()).isEqualTo(AccionAuditable.CONSULTA_HISTORIA);
        assertThat(evento.tipoRecurso()).isEqualTo("HISTORIA_CLINICA");
        assertThat(evento.recursoPublicId()).isEqualTo(pacientePublicId);
    }

    @Test
    @DisplayName("obtenerHistoriaClinicaPaciente - rechaza si acceso clínico no autoriza al médico")
    void obtenerHistoriaClinicaPaciente_sinAutorizacion_lanzaExcepcion() {
        String pacientePublicId = "pac-100";
        String usuarioMedPublicId = "usr-med-1";
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        org.mockito.Mockito.doThrow(new AccesoNoAutorizadoException("No existe una relacion asistencial activa con el paciente."))
                .when(accesoClinicoService).validarAccesoHistorialClinico(usuarioMedPublicId, pacientePublicId, authorities);

        assertThatThrownBy(() -> clinicalAttentionService.obtenerHistoriaClinicaPaciente(
                pacientePublicId, usuarioMedPublicId, authorities, 0, 10, "127.0.0.1"
        )).isInstanceOf(AccesoNoAutorizadoException.class)
          .hasMessageContaining("No existe una relacion asistencial activa");

        verify(atencionRepository, never()).listarHistoriaPaciente(any(), anyInt(), anyInt());
    }
}
