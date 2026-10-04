package com.meditriaje.service;

import com.meditriaje.dto.clinical.AccesoBreakGlassResponse;
import com.meditriaje.dto.clinical.ActivarBreakGlassRequest;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccesoBreakGlass;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.BreakGlassRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BreakGlassServiceTest {

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    private static final Instant AHORA = Instant.parse("2026-10-04T12:00:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProfesionalRepository profesionalRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private BreakGlassRepository breakGlassRepository;

    @Mock
    private AuditoriaService auditoriaService;

    private Clock clock;
    private BreakGlassService breakGlassService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(AHORA, ZONE_BOGOTA);
        breakGlassService = new BreakGlassService(
                usuarioRepository,
                profesionalRepository,
                pacienteRepository,
                breakGlassRepository,
                auditoriaService,
                clock
        );
    }

    private Usuario crearUsuario(Long id, String publicId, String email) {
        return new Usuario(id, publicId, email, "hash", "ACTIVO", 0, null, AHORA, false);
    }

    private Profesional crearProfesional(Long id, Long usuarioId, String publicId) {
        return new Profesional(id, usuarioId, publicId, 1L, "RM-998877", "Roberto", "Gomez", AHORA, AHORA);
    }

    private Paciente crearPaciente(Long id, Long usuarioId, String publicId) {
        return new Paciente(id, usuarioId, publicId, "CC", "10203040", "Carlos", "Sanchez",
                LocalDate.of(1985, 5, 20), "3001234567", AHORA, AHORA);
    }

    @Test
    @DisplayName("activarBreakGlass: registro exitoso con vigencia de 24 horas y auditoría ACCESO_BREAK_GLASS")
    void activarBreakGlass_exitoso() {
        String usuarioMedPubId = "usr-med-1";
        String pacPubId = "pac-100";
        String motivo = "Paciente en shock hipovolemico por hemorragia aguda; requiere acceso urgente al historial.";
        ActivarBreakGlassRequest request = new ActivarBreakGlassRequest(pacPubId, motivo);

        Usuario usuario = crearUsuario(10L, usuarioMedPubId, "medico@test.com");
        Profesional profesional = crearProfesional(100L, 10L, "prof-100");
        Paciente paciente = crearPaciente(200L, 20L, pacPubId);

        when(usuarioRepository.buscarPorPublicId(usuarioMedPubId)).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId(pacPubId)).thenReturn(Optional.of(paciente));
        when(breakGlassRepository.registrarAcceso(any(AccesoBreakGlass.class))).thenReturn(1L);

        Instant expiracionEsperada = AHORA.plus(24, ChronoUnit.HOURS);
        AccesoBreakGlassResponse respuestaMock = new AccesoBreakGlassResponse(
                "bg-created-uuid",
                "prof-100",
                "Roberto Gomez",
                pacPubId,
                "Carlos Sanchez",
                motivo,
                expiracionEsperada,
                AHORA,
                true
        );
        when(breakGlassRepository.buscarPorPublicId(any(String.class), eq(AHORA))).thenReturn(Optional.of(respuestaMock));

        AccesoBreakGlassResponse response = breakGlassService.activarBreakGlass(request, usuarioMedPubId, "192.168.1.100");

        assertThat(response).isNotNull();
        assertThat(response.publicId()).isEqualTo("bg-created-uuid");
        assertThat(response.activo()).isTrue();
        assertThat(response.fechaExpiracion()).isEqualTo(expiracionEsperada);

        // Verificar que el acceso guardado tenga los IDs y expiración exacta
        ArgumentCaptor<AccesoBreakGlass> accesoCaptor = ArgumentCaptor.forClass(AccesoBreakGlass.class);
        verify(breakGlassRepository).registrarAcceso(accesoCaptor.capture());
        AccesoBreakGlass guardado = accesoCaptor.getValue();
        assertThat(guardado.profesionalId()).isEqualTo(100L);
        assertThat(guardado.pacienteId()).isEqualTo(200L);
        assertThat(guardado.fechaExpiracion()).isEqualTo(expiracionEsperada);
        assertThat(guardado.motivo()).isEqualTo(motivo);

        // Verificar auditoría reforzada inmutable
        ArgumentCaptor<EventoAuditoria> audCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(audCaptor.capture());
        EventoAuditoria aud = audCaptor.getValue();
        assertThat(aud.usuarioId()).isEqualTo(10L);
        assertThat(aud.accion()).isEqualTo(AccionAuditable.ACCESO_BREAK_GLASS);
        assertThat(aud.tipoRecurso()).isEqualTo("PACIENTE");
        assertThat(aud.recursoPublicId()).isEqualTo(pacPubId);
        assertThat(aud.ipOrigen()).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("activarBreakGlass: motivo con menos de 20 caracteres lanza DatosInvalidosException")
    void activarBreakGlass_motivoCorto_lanzaDatosInvalidos() {
        String usuarioMedPubId = "usr-med-1";
        String pacPubId = "pac-100";
        ActivarBreakGlassRequest request = new ActivarBreakGlassRequest(pacPubId, "Urgente");

        Usuario usuario = crearUsuario(10L, usuarioMedPubId, "medico@test.com");
        Profesional profesional = crearProfesional(100L, 10L, "prof-100");
        Paciente paciente = crearPaciente(200L, 20L, pacPubId);

        when(usuarioRepository.buscarPorPublicId(usuarioMedPubId)).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId(pacPubId)).thenReturn(Optional.of(paciente));

        assertThatThrownBy(() -> breakGlassService.activarBreakGlass(request, usuarioMedPubId, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("al menos 20 caracteres");
    }

    @Test
    @DisplayName("activarBreakGlass: usuario no registrado como profesional lanza AccesoNoAutorizadoException")
    void activarBreakGlass_usuarioNoProfesional_lanzaAccesoNoAutorizado() {
        String usuarioAdminPubId = "usr-admin-1";
        ActivarBreakGlassRequest request = new ActivarBreakGlassRequest("pac-100", "Justificación de emergencia con más de veinte caracteres.");

        Usuario usuario = crearUsuario(5L, usuarioAdminPubId, "admin@test.com");
        when(usuarioRepository.buscarPorPublicId(usuarioAdminPubId)).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> breakGlassService.activarBreakGlass(request, usuarioAdminPubId, "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Usuario no registrado como profesional");
    }

    @Test
    @DisplayName("activarBreakGlass: paciente inexistente lanza RecursoNoEncontradoException")
    void activarBreakGlass_pacienteInexistente_lanzaRecursoNoEncontrado() {
        String usuarioMedPubId = "usr-med-1";
        ActivarBreakGlassRequest request = new ActivarBreakGlassRequest("pac-fantasma", "Justificación de emergencia con más de veinte caracteres.");

        Usuario usuario = crearUsuario(10L, usuarioMedPubId, "medico@test.com");
        Profesional profesional = crearProfesional(100L, 10L, "prof-100");

        when(usuarioRepository.buscarPorPublicId(usuarioMedPubId)).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId("pac-fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> breakGlassService.activarBreakGlass(request, usuarioMedPubId, "127.0.0.1"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Paciente no encontrado");
    }

    @Test
    @DisplayName("listarMisAccesosActivos: retorna autorizaciones vigentes del profesional")
    void listarMisAccesosActivos_exitoso() {
        String usuarioMedPubId = "usr-med-1";
        Usuario usuario = crearUsuario(10L, usuarioMedPubId, "medico@test.com");
        Profesional profesional = crearProfesional(100L, 10L, "prof-100");

        when(usuarioRepository.buscarPorPublicId(usuarioMedPubId)).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesional));

        AccesoBreakGlassResponse acceso = new AccesoBreakGlassResponse(
                "bg-1",
                "prof-100",
                "Roberto Gomez",
                "pac-1",
                "Carlos Sanchez",
                "Motivo de urgencia médica con suficientes caracteres",
                AHORA.plus(10, ChronoUnit.HOURS),
                AHORA,
                true
        );
        when(breakGlassRepository.listarActivosPorProfesional(100L, AHORA)).thenReturn(List.of(acceso));

        List<AccesoBreakGlassResponse> lista = breakGlassService.listarMisAccesosActivos(usuarioMedPubId);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).publicId()).isEqualTo("bg-1");
    }

    @Test
    @DisplayName("obtenerPorPublicId: si pertenece a otro profesional lanza AccesoNoAutorizadoException")
    void obtenerPorPublicId_otroProfesional_lanzaAccesoNoAutorizado() {
        String usuarioMedPubId = "usr-med-1";
        Usuario usuario = crearUsuario(10L, usuarioMedPubId, "medico@test.com");
        Profesional profesional = crearProfesional(100L, 10L, "prof-100");

        when(usuarioRepository.buscarPorPublicId(usuarioMedPubId)).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(profesional));

        AccesoBreakGlassResponse accesoDeOtro = new AccesoBreakGlassResponse(
                "bg-99",
                "prof-OTRO",
                "Dra. Maria Perez",
                "pac-1",
                "Carlos Sanchez",
                "Motivo de urgencia médica con suficientes caracteres",
                AHORA.plus(10, ChronoUnit.HOURS),
                AHORA,
                true
        );
        when(breakGlassRepository.buscarPorPublicId("bg-99", AHORA)).thenReturn(Optional.of(accesoDeOtro));

        assertThatThrownBy(() -> breakGlassService.obtenerPorPublicId("bg-99", usuarioMedPubId))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("No tiene autorizacion para consultar el acceso break-glass de otro profesional");
    }
}
