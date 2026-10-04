package com.meditriaje.service;

import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.BreakGlassRepository;
import com.meditriaje.repository.CitaRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link AccesoClinicoService}.
 * Verifica de forma determinista y exhaustiva las reglas de autorización asistencial (ADR-007):
 * - Paciente dueño: SÍ.
 * - Otro paciente: NO (403).
 * - Administrador: NUNCA (403).
 * - Profesional con relación asistencial (cita activa futura o atención previa propia en <= 12 meses): SÍ.
 * - Profesional sin relación asistencial: NO (403).
 */
@ExtendWith(MockitoExtension.class)
class AccesoClinicoServiceTest {

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    private static final Instant AHORA = Instant.parse("2026-10-03T18:00:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private ProfesionalRepository profesionalRepository;

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private AtencionRepository atencionRepository;

    @Mock
    private BreakGlassRepository breakGlassRepository;

    private Clock clock;
    private AccesoClinicoService accesoClinicoService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(AHORA, ZONE_BOGOTA);
        accesoClinicoService = new AccesoClinicoService(
                usuarioRepository,
                pacienteRepository,
                profesionalRepository,
                citaRepository,
                atencionRepository,
                breakGlassRepository,
                clock,
                12
        );
    }

    private Usuario crearUsuario(Long id, String publicId, String email) {
        return new Usuario(id, publicId, email, "hash", "ACTIVO", 0, null, AHORA, false);
    }

    private Paciente crearPaciente(Long id, Long usuarioId, String publicId) {
        return new Paciente(id, usuarioId, publicId, "CC", "12345678", "Juan", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", AHORA, AHORA);
    }

    @Test
    @DisplayName("Paciente dueño: puede acceder a su propio historial clínico")
    void pacienteDuenio_puedeAccederASuPropioHistorial() {
        String usuarioPublicId = "u-paciente-1";
        String pacientePublicId = "pac-001";
        Usuario usuario = crearUsuario(10L, usuarioPublicId, "paciente@test.com");
        Paciente paciente = crearPaciente(100L, 10L, pacientePublicId);

        when(usuarioRepository.buscarPorPublicId(usuarioPublicId)).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(paciente));

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"));

        assertThatCode(() -> accesoClinicoService.validarAccesoHistorialClinico(usuarioPublicId, pacientePublicId, authorities))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Otro paciente: no puede acceder al historial de otro paciente (403 Forbidden)")
    void otroPaciente_noPuedeAccederAHistorialClinicoAjeno() {
        String usuarioPublicId = "u-paciente-1";
        String pacientePropioPublicId = "pac-001";
        String pacienteAjenoPublicId = "pac-999";

        Usuario usuario = crearUsuario(10L, usuarioPublicId, "paciente1@test.com");
        Paciente paciente = crearPaciente(100L, 10L, pacientePropioPublicId);

        when(usuarioRepository.buscarPorPublicId(usuarioPublicId)).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(paciente));

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"));

        assertThatThrownBy(() -> accesoClinicoService.validarAccesoHistorialClinico(usuarioPublicId, pacienteAjenoPublicId, authorities))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("No tiene autorizacion para acceder a la informacion clinica de otro paciente");
    }

    @Test
    @DisplayName("Administrador: NUNCA puede acceder a historial clínico (ADR-007, 403 Forbidden)")
    void administrador_nuncaPuedeAccederAHistorialClinico() {
        String adminPublicId = "u-admin-1";
        String pacientePublicId = "pac-001";
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));

        assertThatThrownBy(() -> accesoClinicoService.validarAccesoHistorialClinico(adminPublicId, pacientePublicId, authorities))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("El personal administrativo no tiene autorizacion para acceder a contenido clinico");

        // El admin es bloqueado sin consultar repositorios clínicos
        verify(citaRepository, never()).existeCitaActivaFutura(any(), any(), any());
        verify(atencionRepository, never()).existeAtencionPreviaEnVentana(any(), any(), any());
    }

    @Test
    @DisplayName("Profesional con cita activa futura: SÍ puede acceder al historial clínico")
    void profesionalConCitaFuturaActiva_puedeAcceder() {
        String usuarioMedPublicId = "u-med-1";
        String profesionalPublicId = "pro-001";
        String pacientePublicId = "pac-001";

        Usuario usuarioMed = crearUsuario(20L, usuarioMedPublicId, "med@test.com");
        Profesional profesional = new Profesional(200L, 20L, profesionalPublicId, 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);
        Paciente paciente = crearPaciente(100L, 10L, pacientePublicId);

        when(usuarioRepository.buscarPorPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMed));
        when(profesionalRepository.buscarPorUsuarioId(20L)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId(pacientePublicId)).thenReturn(Optional.of(paciente));

        // Cita activa futura confirmada
        when(citaRepository.existeCitaActivaFutura(200L, 100L, AHORA)).thenReturn(true);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        assertThatCode(() -> accesoClinicoService.validarAccesoHistorialClinico(usuarioMedPublicId, pacientePublicId, authorities))
                .doesNotThrowAnyException();

        // Al haber cita futura, no es necesario consultar atención previa
        verify(atencionRepository, never()).existeAtencionPreviaEnVentana(any(), any(), any());
    }

    @Test
    @DisplayName("Profesional con atención previa propia dentro de 12 meses: SÍ puede acceder")
    void profesionalConAtencionPreviaEnVentana_puedeAcceder() {
        String usuarioMedPublicId = "u-med-1";
        String profesionalPublicId = "pro-001";
        String pacientePublicId = "pac-001";

        Usuario usuarioMed = crearUsuario(20L, usuarioMedPublicId, "med@test.com");
        Profesional profesional = new Profesional(200L, 20L, profesionalPublicId, 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);
        Paciente paciente = crearPaciente(100L, 10L, pacientePublicId);

        when(usuarioRepository.buscarPorPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMed));
        when(profesionalRepository.buscarPorUsuarioId(20L)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId(pacientePublicId)).thenReturn(Optional.of(paciente));

        // Sin cita futura
        when(citaRepository.existeCitaActivaFutura(200L, 100L, AHORA)).thenReturn(false);

        // Pero con atención médica previa en los últimos 12 meses
        Instant fechaLimite12Meses = ZonedDateTime.ofInstant(AHORA, ZONE_BOGOTA).minusMonths(12).toInstant();
        when(atencionRepository.existeAtencionPreviaEnVentana(200L, 100L, fechaLimite12Meses)).thenReturn(true);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        assertThatCode(() -> accesoClinicoService.validarAccesoHistorialClinico(usuarioMedPublicId, pacientePublicId, authorities))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Profesional sin relación asistencial: NO puede acceder (403 Forbidden)")
    void profesionalSinRelacionAsistencial_noPuedeAcceder() {
        String usuarioMedPublicId = "u-med-1";
        String profesionalPublicId = "pro-001";
        String pacientePublicId = "pac-001";

        Usuario usuarioMed = crearUsuario(20L, usuarioMedPublicId, "med@test.com");
        Profesional profesional = new Profesional(200L, 20L, profesionalPublicId, 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);
        Paciente paciente = crearPaciente(100L, 10L, pacientePublicId);

        when(usuarioRepository.buscarPorPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMed));
        when(profesionalRepository.buscarPorUsuarioId(20L)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId(pacientePublicId)).thenReturn(Optional.of(paciente));

        // Sin cita futura y sin atención previa en ventana
        when(citaRepository.existeCitaActivaFutura(200L, 100L, AHORA)).thenReturn(false);
        Instant fechaLimite = ZonedDateTime.ofInstant(AHORA, ZONE_BOGOTA).minusMonths(12).toInstant();
        when(atencionRepository.existeAtencionPreviaEnVentana(200L, 100L, fechaLimite)).thenReturn(false);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        assertThatThrownBy(() -> accesoClinicoService.validarAccesoHistorialClinico(usuarioMedPublicId, pacientePublicId, authorities))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("No existe una relacion asistencial activa con el paciente");
    }

    @Test
    @DisplayName("Validación por ID numérico de paciente resuelve entidad y delega")
    void validarAccesoPorIdNumerico_buscaPacienteYValida() {
        String usuarioPublicId = "u-paciente-1";
        Long pacienteId = 100L;
        String pacientePublicId = "pac-001";

        Paciente paciente = crearPaciente(pacienteId, 10L, pacientePublicId);
        Usuario usuario = crearUsuario(10L, usuarioPublicId, "paciente@test.com");

        when(pacienteRepository.buscarPorId(pacienteId)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.buscarPorPublicId(usuarioPublicId)).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(paciente));

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"));

        assertThatCode(() -> accesoClinicoService.validarAccesoHistorialClinico(usuarioPublicId, pacienteId, authorities))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Usuario sin roles o no autenticado: es rechazado categóricamente")
    void usuarioSinRoles_esRechazado() {
        assertThatThrownBy(() -> accesoClinicoService.validarAccesoHistorialClinico("u-1", "pac-1", List.of()))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Usuario no autenticado o sin roles");

        assertThatThrownBy(() -> accesoClinicoService.validarAccesoHistorialClinico(null, "pac-1", List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))))
                .isInstanceOf(AccesoNoAutorizadoException.class);
    }

    @Test
    @DisplayName("Paciente objetivo inexistente: lanza RecursoNoEncontradoException")
    void pacienteObjetivoNoExiste_lanzaRecursoNoEncontrado() {
        String usuarioMedPublicId = "u-med-1";
        String pacienteInexistentePublicId = "pac-no-existe";

        Usuario usuarioMed = crearUsuario(20L, usuarioMedPublicId, "med@test.com");
        Profesional profesional = new Profesional(200L, 20L, "pro-001", 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);

        when(usuarioRepository.buscarPorPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMed));
        when(profesionalRepository.buscarPorUsuarioId(20L)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId(pacienteInexistentePublicId)).thenReturn(Optional.empty());

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        assertThatThrownBy(() -> accesoClinicoService.validarAccesoHistorialClinico(usuarioMedPublicId, pacienteInexistentePublicId, authorities))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Paciente no encontrado");
    }

    @Test
    @DisplayName("Sobrecarga tieneRelacionAsistencial con UUIDs busca entidades y retorna resultado")
    void tieneRelacionAsistencial_conUUIDs() {
        String proPublicId = "pro-001";
        String pacPublicId = "pac-001";

        Profesional profesional = new Profesional(200L, 20L, proPublicId, 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);
        Paciente paciente = crearPaciente(100L, 10L, pacPublicId);

        when(profesionalRepository.buscarPorPublicId(proPublicId)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId(pacPublicId)).thenReturn(Optional.of(paciente));
        when(citaRepository.existeCitaActivaFutura(200L, 100L, AHORA)).thenReturn(true);

        boolean tieneRelacion = accesoClinicoService.tieneRelacionAsistencial(proPublicId, pacPublicId);

        assertThat(tieneRelacion).isTrue();
    }

    @Test
    @DisplayName("Profesional sin cita ni atención previa pero con Break-Glass activo: acceso permitido (ADR-017)")
    void profesional_sinRelacionOrdinaria_conBreakGlassActivo_accesoPermitido() {
        String usuarioMedPublicId = "u-med-1";
        String pacientePublicId = "pac-001";

        Usuario usuarioMed = crearUsuario(20L, usuarioMedPublicId, "med@test.com");
        Profesional profesional = new Profesional(200L, 20L, "pro-001", 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);
        Paciente paciente = crearPaciente(100L, 10L, pacientePublicId);

        when(usuarioRepository.buscarPorPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMed));
        when(profesionalRepository.buscarPorUsuarioId(20L)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId(pacientePublicId)).thenReturn(Optional.of(paciente));

        // No hay cita activa futura
        when(citaRepository.existeCitaActivaFutura(eq(200L), eq(100L), any(Instant.class))).thenReturn(false);
        // No hay atención previa en ventana de 12 meses
        when(atencionRepository.existeAtencionPreviaEnVentana(eq(200L), eq(100L), any(Instant.class))).thenReturn(false);
        // SÍ hay acceso Break-Glass activo
        when(breakGlassRepository.existeAccesoActivo(eq(200L), eq(100L), any(Instant.class))).thenReturn(true);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        assertThatCode(() -> accesoClinicoService.validarAccesoHistorialClinico(usuarioMedPublicId, pacientePublicId, authorities))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Profesional sin relación ordinaria y sin Break-Glass activo: acceso rechazado 403 (ADR-007, ADR-017)")
    void profesional_sinRelacionOrdinaria_sinBreakGlass_accesoRechazado() {
        String usuarioMedPublicId = "u-med-1";
        String pacientePublicId = "pac-001";

        Usuario usuarioMed = crearUsuario(20L, usuarioMedPublicId, "med@test.com");
        Profesional profesional = new Profesional(200L, 20L, "pro-001", 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);
        Paciente paciente = crearPaciente(100L, 10L, pacientePublicId);

        when(usuarioRepository.buscarPorPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMed));
        when(profesionalRepository.buscarPorUsuarioId(20L)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId(pacientePublicId)).thenReturn(Optional.of(paciente));

        when(citaRepository.existeCitaActivaFutura(eq(200L), eq(100L), any(Instant.class))).thenReturn(false);
        when(atencionRepository.existeAtencionPreviaEnVentana(eq(200L), eq(100L), any(Instant.class))).thenReturn(false);
        when(breakGlassRepository.existeAccesoActivo(eq(200L), eq(100L), any(Instant.class))).thenReturn(false);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        assertThatThrownBy(() -> accesoClinicoService.validarAccesoHistorialClinico(usuarioMedPublicId, pacientePublicId, authorities))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("No existe una relacion asistencial activa con el paciente");
    }
}
