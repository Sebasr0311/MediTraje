package com.meditriaje.service;

import com.meditriaje.dto.appointment.CancelarCitaRequest;
import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.appointment.ReservarCitaRequest;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.CitaNoDisponibleException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Cita;
import com.meditriaje.model.DisponibilidadSlot;
import com.meditriaje.model.EstadoCita;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.CitaRepository;
import com.meditriaje.repository.DisponibilidadSlotRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private DisponibilidadSlotRepository disponibilidadSlotRepository;

    @Mock
    private ProfesionalRepository profesionalRepository;

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private AuditoriaService auditoriaService;

    private final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private AppointmentService appointmentService;

    private static final String USUARIO_PUBLIC_ID = "usr-uuid-1";
    private static final String SLOT_PUBLIC_ID = "slot-uuid-1";
    private static final String IP_CLIENTE = "192.168.1.100";

    private Usuario usuarioMock;
    private Paciente pacienteMock;
    private DisponibilidadSlot slotMock;
    private Profesional profesionalMock;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                usuarioRepository,
                pacienteRepository,
                disponibilidadSlotRepository,
                profesionalRepository,
                citaRepository,
                auditoriaService,
                fixedClock
        );

        usuarioMock = new Usuario(
                1L, USUARIO_PUBLIC_ID, "paciente@test.com", "hash", "ACTIVO", 0, null, NOW, false
        );

        pacienteMock = new Paciente(
                10L, 1L, "pac-uuid-1", "CC", "12345678", "Pepito", "Perez",
                LocalDate.of(1990, 5, 20), "3001234567", NOW, null
        );

        slotMock = new DisponibilidadSlot(
                50L,
                SLOT_PUBLIC_ID,
                100L, // profesionalId
                200L, // sedeId
                300L, // especialidadId
                NOW.plusSeconds(3600),
                NOW.plusSeconds(4800),
                "PRESENCIAL",
                "LIBRE"
        );

        profesionalMock = new Profesional(
                100L,
                2L,
                "prof-uuid-1",
                300L, // especialidadId coincidente
                "RM-12345",
                "Gregory",
                "House",
                NOW,
                null
        );
    }

    @Test
    void reservarCita_exitoso_reservaSlotCreaCitaYAudita() {
        ReservarCitaRequest request = new ReservarCitaRequest(SLOT_PUBLIC_ID);

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));
        when(disponibilidadSlotRepository.buscarEntidadPorPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotMock));
        when(profesionalRepository.buscarPorId(100L)).thenReturn(Optional.of(profesionalMock));
        when(disponibilidadSlotRepository.reservarSlot(50L)).thenReturn(1);
        when(citaRepository.crear(any(Cita.class))).thenReturn(500L);

        CitaResponse mockResponse = new CitaResponse(
                "cita-uuid-generada", SLOT_PUBLIC_ID, "pac-uuid-1", "Pepito Perez",
                "prof-uuid-1", "Gregory House", "esp-uuid-1", "Medicina Interna",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                slotMock.fechaHoraInicio(), slotMock.fechaHoraFin(),
                "PRESENCIAL", "PROGRAMADA", null, null, NOW
        );
        when(citaRepository.buscarPorPublicId(anyString())).thenReturn(Optional.of(mockResponse));

        CitaResponse resultado = appointmentService.reservarCita(request, USUARIO_PUBLIC_ID, IP_CLIENTE);

        assertThat(resultado).isNotNull();
        assertThat(resultado.estado()).isEqualTo("PROGRAMADA");
        assertThat(resultado.slotPublicId()).isEqualTo(SLOT_PUBLIC_ID);

        // Verifica cambio de estado atómico del slot a OCUPADO
        verify(disponibilidadSlotRepository).reservarSlot(50L);

        // Verifica inserción de la cita
        ArgumentCaptor<Cita> citaCaptor = ArgumentCaptor.forClass(Cita.class);
        verify(citaRepository).crear(citaCaptor.capture());
        Cita citaGuardada = citaCaptor.getValue();
        assertThat(citaGuardada.slotId()).isEqualTo(50L);
        assertThat(citaGuardada.pacienteId()).isEqualTo(10L);
        assertThat(citaGuardada.estado()).isEqualTo("PROGRAMADA");

        // Verifica auditoría obligatoria inmutable (ADR-011)
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(eventoCaptor.capture());
        EventoAuditoria evento = eventoCaptor.getValue();
        assertThat(evento.usuarioId()).isEqualTo(1L);
        assertThat(evento.accion()).isEqualTo(AccionAuditable.RESERVA_CITA);
        assertThat(evento.tipoRecurso()).isEqualTo("CITA");
        assertThat(evento.resultado()).isEqualTo(ResultadoAuditoria.EXITO);
        assertThat(evento.ipOrigen()).isEqualTo(IP_CLIENTE);
    }

    @Test
    void reservarCita_usuarioNoExiste_lanzaRecursoNoEncontradoException() {
        ReservarCitaRequest request = new ReservarCitaRequest(SLOT_PUBLIC_ID);
        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.reservarCita(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");

        verify(disponibilidadSlotRepository, never()).reservarSlot(anyLong());
        verify(citaRepository, never()).crear(any());
    }

    @Test
    void reservarCita_usuarioNoEsPaciente_lanzaAccesoNoAutorizadoException() {
        ReservarCitaRequest request = new ReservarCitaRequest(SLOT_PUBLIC_ID);
        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.reservarCita(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Solo pacientes registrados pueden agendar citas");

        verify(disponibilidadSlotRepository, never()).reservarSlot(anyLong());
        verify(citaRepository, never()).crear(any());
    }

    @Test
    void reservarCita_slotNoExiste_lanzaRecursoNoEncontradoException() {
        ReservarCitaRequest request = new ReservarCitaRequest("slot-fantasma");
        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));
        when(disponibilidadSlotRepository.buscarEntidadPorPublicId("slot-fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.reservarCita(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Slot de disponibilidad no encontrado");

        verify(disponibilidadSlotRepository, never()).reservarSlot(anyLong());
        verify(citaRepository, never()).crear(any());
    }

    @Test
    void reservarCita_slotEnElPasado_lanzaDatosInvalidosException() {
        ReservarCitaRequest request = new ReservarCitaRequest(SLOT_PUBLIC_ID);
        DisponibilidadSlot slotPasado = new DisponibilidadSlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.minusSeconds(3600), NOW.minusSeconds(2400), "PRESENCIAL", "LIBRE"
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));
        when(disponibilidadSlotRepository.buscarEntidadPorPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotPasado));

        assertThatThrownBy(() -> appointmentService.reservarCita(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("No es posible agendar una cita en un horario pasado");

        verify(disponibilidadSlotRepository, never()).reservarSlot(anyLong());
        verify(citaRepository, never()).crear(any());
    }

    @Test
    void reservarCita_especialidadNoCoincideConProfesional_lanzaDatosInvalidosException() {
        ReservarCitaRequest request = new ReservarCitaRequest(SLOT_PUBLIC_ID);
        Profesional profesionalOtraEspecialidad = new Profesional(
                100L, 2L, "prof-uuid-1", 999L, // diferente especialidad a 300L
                "RM-12345", "Gregory", "House", NOW, null
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));
        when(disponibilidadSlotRepository.buscarEntidadPorPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotMock));
        when(profesionalRepository.buscarPorId(100L)).thenReturn(Optional.of(profesionalOtraEspecialidad));

        assertThatThrownBy(() -> appointmentService.reservarCita(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("El profesional no corresponde a la especialidad del slot");

        verify(disponibilidadSlotRepository, never()).reservarSlot(anyLong());
        verify(citaRepository, never()).crear(any());
    }

    @Test
    void reservarCita_slotYaOcupado_lanzaCitaNoDisponibleException() {
        ReservarCitaRequest request = new ReservarCitaRequest(SLOT_PUBLIC_ID);

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));
        when(disponibilidadSlotRepository.buscarEntidadPorPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotMock));
        when(profesionalRepository.buscarPorId(100L)).thenReturn(Optional.of(profesionalMock));
        when(disponibilidadSlotRepository.reservarSlot(50L)).thenReturn(0); // 0 filas = no estaba libre

        assertThatThrownBy(() -> appointmentService.reservarCita(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(CitaNoDisponibleException.class)
                .hasMessageContaining("El slot de atencion ya ha sido reservado o no esta disponible");

        verify(citaRepository, never()).crear(any());
        verify(auditoriaService, never()).auditar(any());
    }

    @Test
    void reservarCita_violacionUnicidadIndiceOracle_lanzaCitaNoDisponibleException() {
        ReservarCitaRequest request = new ReservarCitaRequest(SLOT_PUBLIC_ID);

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));
        when(disponibilidadSlotRepository.buscarEntidadPorPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotMock));
        when(profesionalRepository.buscarPorId(100L)).thenReturn(Optional.of(profesionalMock));
        when(disponibilidadSlotRepository.reservarSlot(50L)).thenReturn(1);
        when(citaRepository.crear(any(Cita.class)))
                .thenThrow(new DataIntegrityViolationException("ORA-00001: unique constraint UQ_CITA_SLOT_ACTIVA violated"));

        assertThatThrownBy(() -> appointmentService.reservarCita(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(CitaNoDisponibleException.class)
                .hasMessageContaining("El slot de atencion ya cuenta con una cita activa");

        verify(auditoriaService, never()).auditar(any());
    }

    // =========================================================================
    // CANCELACIÓN DE CITAS Y MÁQUINA DE ESTADOS (ADR-006, HU-05)
    // =========================================================================

    @Test
    void cancelarCita_pacienteConMasDeDosHoras_exitoso_actualizaEstadoLiberaSlotYAudita() {
        // Slot a 3 horas de distancia (más de 2 horas)
        DisponibilidadSlot slotFuturo = new DisponibilidadSlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(10800), NOW.plusSeconds(12000), "PRESENCIAL", "OCUPADO"
        );

        Cita citaProgramada = new Cita(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        CitaResponse mockResponse = new CitaResponse(
                "cita-uuid-1", SLOT_PUBLIC_ID, "pac-uuid-1", "Pepito Perez",
                "prof-uuid-1", "Gregory House", "esp-uuid-1", "Medicina Interna",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                slotFuturo.fechaHoraInicio(), slotFuturo.fechaHoraFin(),
                "PRESENCIAL", "CANCELADA", null, null, NOW
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(disponibilidadSlotRepository.buscarPorId(50L)).thenReturn(Optional.of(slotFuturo));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));
        when(citaRepository.buscarPorPublicId("cita-uuid-1")).thenReturn(Optional.of(mockResponse));

        CancelarCitaRequest req = new CancelarCitaRequest("Imprevisto laboral");
        CitaResponse resultado = appointmentService.cancelarCita(
                "cita-uuid-1",
                req,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        );

        assertThat(resultado).isNotNull();
        assertThat(resultado.estado()).isEqualTo("CANCELADA");

        // 1. Verifica actualización en BD
        verify(citaRepository).actualizarEstado(500L, "CANCELADA", "Imprevisto laboral");

        // 2. Verifica liberación de slot a LIBRE
        verify(disponibilidadSlotRepository).liberarSlot(50L);

        // 3. Verifica auditoría inmutable
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(eventoCaptor.capture());
        EventoAuditoria evento = eventoCaptor.getValue();
        assertThat(evento.usuarioId()).isEqualTo(1L);
        assertThat(evento.accion()).isEqualTo(AccionAuditable.CANCELACION_CITA);
        assertThat(evento.tipoRecurso()).isEqualTo("CITA");
        assertThat(evento.recursoPublicId()).isEqualTo("cita-uuid-1");
        assertThat(evento.resultado()).isEqualTo(ResultadoAuditoria.EXITO);
        assertThat(evento.ipOrigen()).isEqualTo(IP_CLIENTE);
    }

    @Test
    void cancelarCita_pacienteConMenosDeDosHoras_lanzaDatosInvalidosException() {
        // Slot inicia en 1 hora (menos de 2 horas)
        DisponibilidadSlot slotCercano = new DisponibilidadSlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(3600), NOW.plusSeconds(4800), "PRESENCIAL", "OCUPADO"
        );

        Cita citaProgramada = new Cita(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(disponibilidadSlotRepository.buscarPorId(50L)).thenReturn(Optional.of(slotCercano));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));

        assertThatThrownBy(() -> appointmentService.cancelarCita(
                "cita-uuid-1",
                new CancelarCitaRequest("Motivo"),
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("La cancelacion por parte del paciente solo esta permitida hasta 2 horas antes de la cita.");

        verify(citaRepository, never()).actualizarEstado(anyLong(), anyString(), any());
        verify(disponibilidadSlotRepository, never()).liberarSlot(anyLong());
        verify(auditoriaService, never()).auditar(any());
    }

    @Test
    void cancelarCita_pacienteCitaEnElPasado_lanzaDatosInvalidosException() {
        DisponibilidadSlot slotPasado = new DisponibilidadSlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.minusSeconds(1800), NOW.minusSeconds(600), "PRESENCIAL", "OCUPADO"
        );

        Cita citaProgramada = new Cita(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(disponibilidadSlotRepository.buscarPorId(50L)).thenReturn(Optional.of(slotPasado));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));

        assertThatThrownBy(() -> appointmentService.cancelarCita(
                "cita-uuid-1",
                new CancelarCitaRequest("Motivo"),
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("La cancelacion por parte del paciente solo esta permitida hasta 2 horas antes de la cita.");

        verify(citaRepository, never()).actualizarEstado(anyLong(), anyString(), any());
        verify(disponibilidadSlotRepository, never()).liberarSlot(anyLong());
        verify(auditoriaService, never()).auditar(any());
    }

    @Test
    void cancelarCita_pacienteIntentaCancelarCitaAjena_lanzaAccesoNoAutorizadoException() {
        DisponibilidadSlot slotFuturo = new DisponibilidadSlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(10800), NOW.plusSeconds(12000), "PRESENCIAL", "OCUPADO"
        );

        // Cita con pacienteId = 999L (ajena al paciente autenticado con id = 10L)
        Cita citaAjena = new Cita(
                500L, "cita-uuid-1", 50L, 999L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaAjena));
        when(disponibilidadSlotRepository.buscarPorId(50L)).thenReturn(Optional.of(slotFuturo));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));

        assertThatThrownBy(() -> appointmentService.cancelarCita(
                "cita-uuid-1",
                new CancelarCitaRequest("Motivo"),
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessage("No tiene autorizacion para cancelar una cita ajena.");

        verify(citaRepository, never()).actualizarEstado(anyLong(), anyString(), any());
        verify(disponibilidadSlotRepository, never()).liberarSlot(anyLong());
        verify(auditoriaService, never()).auditar(any());
    }

    @Test
    void cancelarCita_transicionInvalidaDesdeAtendida_lanzaDatosInvalidosException() {
        Cita citaAtendida = new Cita(
                500L, "cita-uuid-1", 50L, 10L, null, null, "ATENDIDA", null, NOW, NOW
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaAtendida));

        assertThatThrownBy(() -> appointmentService.cancelarCita(
                "cita-uuid-1",
                null,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("Transicion de estado no permitida de ATENDIDA a CANCELADA.");

        verify(disponibilidadSlotRepository, never()).buscarPorId(anyLong());
        verify(citaRepository, never()).actualizarEstado(anyLong(), anyString(), any());
        verify(auditoriaService, never()).auditar(any());
    }

    @Test
    void cancelarCita_transicionInvalidaDesdeCancelada_lanzaDatosInvalidosException() {
        Cita citaCancelada = new Cita(
                500L, "cita-uuid-1", 50L, 10L, null, null, "CANCELADA", "Cancelada previamente", NOW, NOW
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaCancelada));

        assertThatThrownBy(() -> appointmentService.cancelarCita(
                "cita-uuid-1",
                null,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")),
                IP_CLIENTE
        ))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("Transicion de estado no permitida de CANCELADA a CANCELADA.");

        verify(disponibilidadSlotRepository, never()).buscarPorId(anyLong());
        verify(citaRepository, never()).actualizarEstado(anyLong(), anyString(), any());
    }

    @Test
    void cancelarCita_profesionalDeSuPropioSlotSinRestriccionDosHoras_exitoso() {
        // Slot inicia en solo 30 minutos (menos de 2 horas)
        DisponibilidadSlot slotInmediato = new DisponibilidadSlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(1800), NOW.plusSeconds(3000), "PRESENCIAL", "OCUPADO"
        );

        Cita citaConfirmada = new Cita(
                500L, "cita-uuid-1", 50L, 10L, null, null, "CONFIRMADA", null, NOW, NOW
        );

        Usuario usuarioProfesional = new Usuario(
                2L, "prof-usr-uuid", "drhouse@test.com", "hash", "ACTIVO", 0, null, NOW, false
        );

        CitaResponse mockResponse = new CitaResponse(
                "cita-uuid-1", SLOT_PUBLIC_ID, "pac-uuid-1", "Pepito Perez",
                "prof-uuid-1", "Gregory House", "esp-uuid-1", "Medicina Interna",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                slotInmediato.fechaHoraInicio(), slotInmediato.fechaHoraFin(),
                "PRESENCIAL", "CANCELADA", null, null, NOW
        );

        when(usuarioRepository.buscarPorPublicId("prof-usr-uuid")).thenReturn(Optional.of(usuarioProfesional));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaConfirmada));
        when(disponibilidadSlotRepository.buscarPorId(50L)).thenReturn(Optional.of(slotInmediato));
        when(profesionalRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(profesionalMock));
        when(citaRepository.buscarPorPublicId("cita-uuid-1")).thenReturn(Optional.of(mockResponse));

        CitaResponse res = appointmentService.cancelarCita(
                "cita-uuid-1",
                new CancelarCitaRequest("Emergencia medica del profesional"),
                "prof-usr-uuid",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        );

        assertThat(res).isNotNull();
        assertThat(res.estado()).isEqualTo("CANCELADA");

        verify(citaRepository).actualizarEstado(500L, "CANCELADA", "Emergencia medica del profesional");
        verify(disponibilidadSlotRepository).liberarSlot(50L);
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }

    @Test
    void cancelarCita_profesionalDeSlotAjeno_lanzaAccesoNoAutorizadoException() {
        // Slot asignado al profesional 999L
        DisponibilidadSlot slotDeOtroProfesional = new DisponibilidadSlot(
                50L, SLOT_PUBLIC_ID, 999L, 200L, 300L,
                NOW.plusSeconds(3600), NOW.plusSeconds(4800), "PRESENCIAL", "OCUPADO"
        );

        Cita citaProgramada = new Cita(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        Usuario usuarioProfesional = new Usuario(
                2L, "prof-usr-uuid", "drhouse@test.com", "hash", "ACTIVO", 0, null, NOW, false
        );

        when(usuarioRepository.buscarPorPublicId("prof-usr-uuid")).thenReturn(Optional.of(usuarioProfesional));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(disponibilidadSlotRepository.buscarPorId(50L)).thenReturn(Optional.of(slotDeOtroProfesional));
        when(profesionalRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(profesionalMock));

        assertThatThrownBy(() -> appointmentService.cancelarCita(
                "cita-uuid-1",
                new CancelarCitaRequest("Motivo"),
                "prof-usr-uuid",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        ))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessage("El profesional no tiene autorizacion para cancelar citas de otro colega.");

        verify(citaRepository, never()).actualizarEstado(anyLong(), anyString(), any());
        verify(disponibilidadSlotRepository, never()).liberarSlot(anyLong());
    }

    @Test
    void cancelarCita_administradorSinRestriccionDosHoras_exitoso() {
        // Slot inicia en 10 minutos
        DisponibilidadSlot slotInmediato = new DisponibilidadSlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(600), NOW.plusSeconds(1800), "PRESENCIAL", "OCUPADO"
        );

        Cita citaProgramada = new Cita(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        Usuario usuarioAdmin = new Usuario(
                3L, "admin-usr-uuid", "admin@test.com", "hash", "ACTIVO", 0, null, NOW, false
        );

        CitaResponse mockResponse = new CitaResponse(
                "cita-uuid-1", SLOT_PUBLIC_ID, "pac-uuid-1", "Pepito Perez",
                "prof-uuid-1", "Gregory House", "esp-uuid-1", "Medicina Interna",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                slotInmediato.fechaHoraInicio(), slotInmediato.fechaHoraFin(),
                "PRESENCIAL", "CANCELADA", null, null, NOW
        );

        when(usuarioRepository.buscarPorPublicId("admin-usr-uuid")).thenReturn(Optional.of(usuarioAdmin));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(disponibilidadSlotRepository.buscarPorId(50L)).thenReturn(Optional.of(slotInmediato));
        when(citaRepository.buscarPorPublicId("cita-uuid-1")).thenReturn(Optional.of(mockResponse));

        CitaResponse res = appointmentService.cancelarCita(
                "cita-uuid-1",
                new CancelarCitaRequest("Cancelacion administrativa por cierre de sede"),
                "admin-usr-uuid",
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")),
                IP_CLIENTE
        );

        assertThat(res).isNotNull();
        assertThat(res.estado()).isEqualTo("CANCELADA");

        verify(citaRepository).actualizarEstado(500L, "CANCELADA", "Cancelacion administrativa por cierre de sede");
        verify(disponibilidadSlotRepository).liberarSlot(50L);
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }

    @Test
    void cancelarCita_citaNoExiste_lanzaRecursoNoEncontradoException() {
        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(citaRepository.buscarEntidadPorPublicId("cita-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.cancelarCita(
                "cita-inexistente",
                null,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Cita no encontrada");

        verify(disponibilidadSlotRepository, never()).buscarPorId(anyLong());
        verify(citaRepository, never()).actualizarEstado(anyLong(), anyString(), any());
    }

    @Test
    void cancelarCita_usuarioNoExiste_lanzaRecursoNoEncontradoException() {
        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.cancelarCita(
                "cita-uuid-1",
                null,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");

        verify(citaRepository, never()).buscarEntidadPorPublicId(anyString());
    }

    @Test
    void cancelarCita_pacienteSinRegistroPaciente_lanzaAccesoNoAutorizadoException() {
        DisponibilidadSlot slotFuturo = new DisponibilidadSlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(10800), NOW.plusSeconds(12000), "PRESENCIAL", "OCUPADO"
        );

        Cita citaProgramada = new Cita(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(disponibilidadSlotRepository.buscarPorId(50L)).thenReturn(Optional.of(slotFuturo));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.cancelarCita(
                "cita-uuid-1",
                null,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessage("Solo pacientes registrados pueden cancelar citas.");
    }

    @Test
    void cancelarCita_profesionalSinRegistroProfesional_lanzaAccesoNoAutorizadoException() {
        DisponibilidadSlot slotFuturo = new DisponibilidadSlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(10800), NOW.plusSeconds(12000), "PRESENCIAL", "OCUPADO"
        );

        Cita citaProgramada = new Cita(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        Usuario usrProf = new Usuario(2L, "prof-usr-uuid", "prof@test.com", "hash", "ACTIVO", 0, null, NOW, false);

        when(usuarioRepository.buscarPorPublicId("prof-usr-uuid")).thenReturn(Optional.of(usrProf));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(disponibilidadSlotRepository.buscarPorId(50L)).thenReturn(Optional.of(slotFuturo));
        when(profesionalRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.cancelarCita(
                "cita-uuid-1",
                null,
                "prof-usr-uuid",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        ))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessage("El profesional no tiene autorizacion para cancelar citas de otro colega.");
    }
}
