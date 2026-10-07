package com.meditriaje.security;

import com.meditriaje.dto.clinical.AccesoBreakGlassResponse;
import com.meditriaje.dto.clinical.ActivarBreakGlassRequest;
import com.meditriaje.dto.emergency.ConsultarResumenRequest;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.CredencialesInvalidasException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.model.AccesoBreakGlass;
import com.meditriaje.model.AccesoTemporalQr;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AccesoTemporalQrRepository;
import com.meditriaje.repository.AlergiaRepository;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.BreakGlassRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.RecetaRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.service.AuditoriaService;
import com.meditriaje.service.BreakGlassService;
import com.meditriaje.service.EmergencyQrService;
import com.meditriaje.service.EmergencySummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Suite 9: Casos de borde en QR de emergencia y Break-Glass médico (ADR-012, ADR-014, T6).
 * Valida expiración a 15 min, bloqueo de lectura > 3, bloqueo ante PIN erróneo, expiración a 24h
 * de break-glass, justificación < 20 caracteres y rechazo absoluto a roles no clínicos.
 */
@ExtendWith(MockitoExtension.class)
class EmergencyQrAndBreakGlassSecurityTest {

    private static final Instant T0 = Instant.parse("2026-10-06T10:00:00Z");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final Clock clock = Clock.fixed(T0, BOGOTA);

    @Mock
    private AccesoTemporalQrRepository accesoTemporalQrRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ProfesionalRepository profesionalRepository;
    @Mock
    private AlergiaRepository alergiaRepository;
    @Mock
    private RecetaRepository recetaRepository;
    @Mock
    private AtencionRepository atencionRepository;
    @Mock
    private BreakGlassRepository breakGlassRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private EmergencyQrService emergencyQrService;
    private EmergencySummaryService emergencySummaryService;
    private BreakGlassService breakGlassService;

    private static final String TOKEN_PLANO = "test-token-emergencia-123456789";
    private static final String TOKEN_HASH = TokenHashUtil.hash(TOKEN_PLANO);

    @BeforeEach
    void setUp() {
        emergencyQrService = new EmergencyQrService(
                accesoTemporalQrRepository,
                pacienteRepository,
                usuarioRepository,
                auditoriaService,
                passwordEncoder,
                clock
        );

        emergencySummaryService = new EmergencySummaryService(
                accesoTemporalQrRepository,
                pacienteRepository,
                usuarioRepository,
                alergiaRepository,
                recetaRepository,
                atencionRepository,
                auditoriaService,
                passwordEncoder,
                clock
        );

        breakGlassService = new BreakGlassService(
                usuarioRepository,
                profesionalRepository,
                pacienteRepository,
                breakGlassRepository,
                auditoriaService,
                clock
        );
    }

    // =========================================================================
    // SECCIÓN 1: Casos de Borde en QR de Emergencia (F2.3, ADR-012)
    // =========================================================================

    @Test
    @DisplayName("QR: Token expirado (> 15 min) es rechazado con DatosInvalidosException")
    void qr_tokenExpiradoTras15Minutos_esRechazado() {
        AccesoTemporalQr expirado = new AccesoTemporalQr(
                1L, "qr-1", 10L, TOKEN_HASH, null,
                true, true, true, true,
                3, 0, false,
                T0.minus(1, ChronoUnit.SECONDS), // Expirado justo antes de T0
                T0.minus(16, ChronoUnit.MINUTES),
                T0.minus(16, ChronoUnit.MINUTES)
        );

        when(accesoTemporalQrRepository.buscarPorTokenHash(TOKEN_HASH)).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> emergencySummaryService.consultarResumenPorToken(
                TOKEN_PLANO, new ConsultarResumenRequest(null), "10.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("El acceso QR ha expirado");

        verify(accesoTemporalQrRepository, never()).registrarAcceso(anyLong(), any());
    }

    @Test
    @DisplayName("QR: Acceso bloqueado al superar el límite estricto de 3 lecturas (intentos >= 3)")
    void qr_superacionLimite3Lecturas_esBloqueadoYRechazado() {
        AccesoTemporalQr agotado = new AccesoTemporalQr(
                2L, "qr-2", 10L, TOKEN_HASH, null,
                true, true, true, true,
                3, 3, false, // 3 de 3 lecturas consumidas
                T0.plus(10, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES)
        );

        when(accesoTemporalQrRepository.buscarPorTokenHash(TOKEN_HASH)).thenReturn(Optional.of(agotado));

        assertThatThrownBy(() -> emergencySummaryService.consultarResumenPorToken(
                TOKEN_PLANO, new ConsultarResumenRequest(null), "10.0.0.2"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("limite maximo de lecturas permitidas");

        verify(accesoTemporalQrRepository, never()).registrarAcceso(anyLong(), any());
    }

    @Test
    @DisplayName("QR: PIN erróneo lanza CredencialesInvalidasException y no registra lectura exitosa")
    void qr_pinErroneo_esRechazadoConCredencialesInvalidas() {
        AccesoTemporalQr conPin = new AccesoTemporalQr(
                3L, "qr-3", 10L, TOKEN_HASH, "argon2id$hashedpin",
                true, true, true, true,
                3, 1, false,
                T0.plus(10, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES)
        );

        when(accesoTemporalQrRepository.buscarPorTokenHash(TOKEN_HASH)).thenReturn(Optional.of(conPin));
        when(passwordEncoder.matches("0000", "argon2id$hashedpin")).thenReturn(false);

        assertThatThrownBy(() -> emergencySummaryService.consultarResumenPorToken(
                TOKEN_PLANO, new ConsultarResumenRequest("0000"), "10.0.0.3"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageContaining("PIN de seguridad proporcionado es incorrecto");

        verify(accesoTemporalQrRepository, never()).registrarAcceso(anyLong(), any());
    }

    @Test
    @DisplayName("QR: Token revocado por el paciente bloquea cualquier intento de consulta")
    void qr_revocadoPorPaciente_bloqueaAcceso() {
        AccesoTemporalQr revocado = new AccesoTemporalQr(
                4L, "qr-4", 10L, TOKEN_HASH, null,
                true, true, true, true,
                3, 0, true, // REVOCADO
                T0.plus(10, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES)
        );

        when(accesoTemporalQrRepository.buscarPorTokenHash(TOKEN_HASH)).thenReturn(Optional.of(revocado));

        assertThatThrownBy(() -> emergencySummaryService.consultarResumenPorToken(
                TOKEN_PLANO, new ConsultarResumenRequest(null), "10.0.0.4"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("ha sido revocado por el paciente");
    }

    @Test
    @DisplayName("QR: Paciente ajeno intentando revocar QR recibe 403 AccesoNoAutorizadoException")
    void qr_pacienteAjenoRevocar_lanza403() {
        Usuario usuarioAtacante = new Usuario(99L, "usr-atacante", "atacante@test.com", "hash", "ACTIVO", 0, null, T0, false, false, null, null);
        Paciente pacienteAtacante = new Paciente(99L, 99L, "pac-atacante", "CC", "999", "Atacante", "Hacker", LocalDate.of(1995, 1, 1), "300", T0, T0);
        AccesoTemporalQr qrVictima = new AccesoTemporalQr(
                5L, "qr-victima", 10L, "hash", null, true, true, true, true, 3, 0, false,
                T0.plus(10, ChronoUnit.MINUTES), T0, T0
        );

        when(usuarioRepository.buscarPorPublicId("usr-atacante")).thenReturn(Optional.of(usuarioAtacante));
        when(pacienteRepository.buscarPorUsuarioId(99L)).thenReturn(Optional.of(pacienteAtacante));
        when(accesoTemporalQrRepository.buscarPorPublicId("qr-victima")).thenReturn(Optional.of(qrVictima));

        assertThatThrownBy(() -> emergencyQrService.revocarAccesoQr("qr-victima", "usr-atacante", "10.0.0.9"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("No tiene autorizacion para revocar un acceso QR ajeno");

        verify(accesoTemporalQrRepository, never()).revocar(anyString(), anyLong());
    }

    // =========================================================================
    // SECCIÓN 2: Casos de Borde en Break-Glass Médico (F2.5, ADR-014)
    // =========================================================================

    @Test
    @DisplayName("Break-Glass: Justificación con menos de 20 caracteres es rechazada con DatosInvalidosException")
    void breakGlass_justificacionMenorA20Caracteres_esRechazada() {
        ActivarBreakGlassRequest request = new ActivarBreakGlassRequest("pac-100", "Muy urgente!"); // < 20 caracteres

        Usuario usuarioMed = new Usuario(1L, "usr-med-1", "med@test.com", "hash", "ACTIVO", 0, null, T0, false, false, null, null);
        Profesional prof = new Profesional(10L, 1L, "prof-1", 1L, "RM-1", "Dr. Juan", "Perez", T0, T0);
        Paciente pac = new Paciente(100L, 2L, "pac-100", "CC", "123", "Paciente", "Critico", LocalDate.of(1980, 1, 1), "300", T0, T0);

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMed));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(prof));
        when(pacienteRepository.buscarPorPublicId("pac-100")).thenReturn(Optional.of(pac));

        assertThatThrownBy(() -> breakGlassService.activarBreakGlass(request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("al menos 20 caracteres");

        verify(breakGlassRepository, never()).registrarAcceso(any());
    }

    @Test
    @DisplayName("Break-Glass: Solicitud por usuario no profesional (ej. admin o paciente) es rechazada con 403")
    void breakGlass_rolNoProfesional_esRechazado() {
        ActivarBreakGlassRequest request = new ActivarBreakGlassRequest("pac-100", "Paciente inconsciente en sala de cirugia mayor.");

        Usuario adminUser = new Usuario(5L, "usr-admin", "admin@test.com", "hash", "ACTIVO", 0, null, T0, false, false, null, null);
        when(usuarioRepository.buscarPorPublicId("usr-admin")).thenReturn(Optional.of(adminUser));
        when(profesionalRepository.buscarPorUsuarioId(5L)).thenReturn(Optional.empty()); // No es profesional asistencial

        assertThatThrownBy(() -> breakGlassService.activarBreakGlass(request, "usr-admin", "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Usuario no registrado como profesional asistencial");

        verify(breakGlassRepository, never()).registrarAcceso(any());
    }

    @Test
    @DisplayName("Break-Glass: Acceso vencido tras 24 horas no figura en consultas activas")
    void breakGlass_vencidoTras24Horas_noFiguraComoActivo() {
        Usuario usuarioMed = new Usuario(1L, "usr-med-1", "med@test.com", "hash", "ACTIVO", 0, null, T0, false, false, null, null);
        Profesional prof = new Profesional(10L, 1L, "prof-1", 1L, "RM-1", "Dr. Juan", "Perez", T0, T0);

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMed));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(prof));

        // El repositorio filtra now > FECHA_EXPIRACION -> lista vacía
        when(breakGlassRepository.listarActivosPorProfesional(10L, T0)).thenReturn(List.of());

        List<AccesoBreakGlassResponse> activos = breakGlassService.listarMisAccesosActivos("usr-med-1");
        assertThat(activos).isEmpty();
    }

    @Test
    @DisplayName("Break-Glass: Profesional intentando consultar detalle de autorización de otro colega recibe 403")
    void breakGlass_consultaColegaAjeno_retorna403() {
        Usuario usuarioMed2 = new Usuario(2L, "usr-med-2", "med2@test.com", "hash", "ACTIVO", 0, null, T0, false, false, null, null);
        Profesional prof2 = new Profesional(20L, 2L, "prof-2", 1L, "RM-2", "Dra. Gomez", "Lopez", T0, T0);

        when(usuarioRepository.buscarPorPublicId("usr-med-2")).thenReturn(Optional.of(usuarioMed2));
        when(profesionalRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(prof2));

        AccesoBreakGlassResponse accesoOtro = new AccesoBreakGlassResponse(
                "bg-otro-uuid",
                "prof-1", // Pertenece al prof 1, NO al prof 2
                "Dr. Juan Perez",
                "pac-100",
                "Paciente Critico",
                "Justificacion medica valida con mas de veinte caracteres",
                T0.plus(10, ChronoUnit.HOURS),
                T0,
                true
        );
        when(breakGlassRepository.buscarPorPublicId("bg-otro-uuid", T0)).thenReturn(Optional.of(accesoOtro));

        assertThatThrownBy(() -> breakGlassService.obtenerPorPublicId("bg-otro-uuid", "usr-med-2"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("No tiene autorizacion para consultar el acceso break-glass de otro profesional");
    }
}
