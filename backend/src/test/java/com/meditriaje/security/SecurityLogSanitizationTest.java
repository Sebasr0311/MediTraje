package com.meditriaje.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.meditriaje.dto.AuthTokens;
import com.meditriaje.dto.LoginRequest;
import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.clinical.CerrarAtencionRequest;
import com.meditriaje.dto.clinical.IniciarAtencionRequest;
import com.meditriaje.dto.emergency.GenerarQrRequest;
import com.meditriaje.dto.prescription.CrearRecetaDetalleRequest;
import com.meditriaje.dto.prescription.CrearRecetaRequest;
import com.meditriaje.model.Atencion;
import com.meditriaje.model.Cita;
import com.meditriaje.model.DiagnosticoCie10;
import com.meditriaje.model.DisponibilidadSlot;
import com.meditriaje.model.Medicamento;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AccesoTemporalQrRepository;
import com.meditriaje.repository.AlergiaRepository;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.CitaRepository;
import com.meditriaje.repository.CodigoVerificacionRepository;
import com.meditriaje.repository.ConsentimientoRepository;
import com.meditriaje.repository.DiagnosticoCie10Repository;
import com.meditriaje.repository.DisponibilidadSlotRepository;
import com.meditriaje.repository.MedicamentoRepository;
import com.meditriaje.repository.MfaBackupCodeRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.RecetaRepository;
import com.meditriaje.repository.RefreshTokenRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.service.AccesoClinicoService;
import com.meditriaje.service.AuditoriaService;
import com.meditriaje.service.AuthService;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.service.EmergencyQrService;
import com.meditriaje.service.PrescriptionService;
import com.meditriaje.service.email.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Suite 8: Pruebas de sanitización de logs (cero PHI, datos clínicos, contraseñas ni tokens en logs) (ADR-011, T6).
 * Valida mediante captura estricta con ListAppender que los flujos de autenticación, atención, receta y QR no filtren datos sensibles.
 */
@ExtendWith(MockitoExtension.class)
class SecurityLogSanitizationTest {

    private ListAppender<ILoggingEvent> listAppender;
    private Logger meditriajeLogger;

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private ProfesionalRepository profesionalRepository;
    @Mock
    private ConsentimientoRepository consentimientoRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private CodigoVerificacionRepository codigoVerificacionRepository;
    @Mock
    private MfaBackupCodeRepository mfaBackupCodeRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private AtencionRepository atencionRepository;
    @Mock
    private CitaRepository citaRepository;
    @Mock
    private DiagnosticoCie10Repository cie10Repository;
    @Mock
    private DisponibilidadSlotRepository slotRepository;
    @Mock
    private AccesoClinicoService accesoClinicoService;
    @Mock
    private MedicamentoRepository medicamentoRepository;
    @Mock
    private RecetaRepository recetaRepository;
    @Mock
    private AccesoTemporalQrRepository accesoTemporalQrRepository;
    @Mock
    private AlergiaRepository alergiaRepository;

    private JwtService jwtService;
    private AuthService authService;
    private ClinicalAttentionService attentionService;
    private PrescriptionService prescriptionService;
    private EmergencyQrService emergencyQrService;

    private static final String SENSITIVE_PASSWORD = "VerySecretPassword!2026";
    private static final String SENSITIVE_CLINICAL_NOTE = "Paciente con sospecha de infarto agudo de miocardio y dolor retroesternal";
    private static final String SENSITIVE_MEDICATION = "Morfina Solucion Inyectable 10mg/ml";
    private static final String SENSITIVE_INDICATION = "Administrar via intravenosa lenta cada 4 horas";
    private static final String SENSITIVE_PIN = "8492";
    private static final String SENSITIVE_DOC = "CC-9988776655";

    @BeforeEach
    void setUp() {
        meditriajeLogger = (Logger) LoggerFactory.getLogger("com.meditriaje");
        listAppender = new ListAppender<>();
        listAppender.start();
        meditriajeLogger.addAppender(listAppender);

        jwtService = new JwtService("c2VjdXJpdHktand0LXNlY3JldC1rZXktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n", 15);
        authService = new AuthService(
                usuarioRepository,
                pacienteRepository,
                profesionalRepository,
                consentimientoRepository,
                refreshTokenRepository,
                codigoVerificacionRepository,
                mfaBackupCodeRepository,
                auditoriaService,
                passwordEncoder,
                jwtService,
                new TotpService(),
                emailService,
                7
        );

        Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneId.of("America/Bogota"));
        attentionService = new ClinicalAttentionService(
                usuarioRepository,
                pacienteRepository,
                profesionalRepository,
                citaRepository,
                slotRepository,
                atencionRepository,
                cie10Repository,
                accesoClinicoService,
                auditoriaService,
                clock
        );

        prescriptionService = new PrescriptionService(
                usuarioRepository,
                profesionalRepository,
                pacienteRepository,
                atencionRepository,
                medicamentoRepository,
                recetaRepository,
                accesoClinicoService,
                auditoriaService,
                clock
        );

        emergencyQrService = new EmergencyQrService(
                accesoTemporalQrRepository,
                pacienteRepository,
                usuarioRepository,
                auditoriaService,
                passwordEncoder,
                clock
        );
    }

    @AfterEach
    void tearDown() {
        if (meditriajeLogger != null && listAppender != null) {
            meditriajeLogger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    private void assertLogsDoNotContain(String... forbiddenStrings) {
        for (ILoggingEvent event : listAppender.list) {
            String logMsg = event.getFormattedMessage();
            for (String forbidden : forbiddenStrings) {
                assertThat(logMsg)
                        .as("El log no debe filtrar información sensible: '%s'. Log encontrado: %s", forbidden, logMsg)
                        .doesNotContain(forbidden);
            }
        }
    }

    @Test
    @DisplayName("Sanitización: Flujo de login no registra contraseña en claro ni tokens en los logs")
    void login_noFiltraPasswordNiTokens() {
        Usuario usuario = new Usuario(
                1L, "usr-uuid-1", "usuario@test.com", "argon2id$hashed",
                "ACTIVO", 0, null, Instant.now(), false, false, null, null
        );

        when(usuarioRepository.buscarPorEmail("usuario@test.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(SENSITIVE_PASSWORD, "argon2id$hashed")).thenReturn(true);
        when(usuarioRepository.obtenerRoles(1L)).thenReturn(List.of("ROLE_PACIENTE"));

        LoginRequest request = new LoginRequest("usuario@test.com", SENSITIVE_PASSWORD);
        AuthTokens response = authService.login(request, "192.168.1.1");

        assertThat(response).isNotNull();
        assertLogsDoNotContain(SENSITIVE_PASSWORD, response.accessToken());
    }

    @Test
    @DisplayName("Sanitización: Apertura y cierre de atención clínica no filtra diagnósticos ni evoluciones en los logs")
    void atencionClinica_noFiltraEvolucionNiDiagnostico() {
        Usuario usuarioProf = new Usuario(10L, "prof-user-uuid", "doc@test.com", "hash", "ACTIVO", 0, null, Instant.now(), false, false, null, null);
        Profesional prof = new Profesional(100L, 10L, "prof-uuid", 1L, "RM-1010", "Dra. Ana", "Ruiz", Instant.now(), Instant.now());
        DisponibilidadSlot slot = new DisponibilidadSlot(300L, "slot-uuid", 100L, 1L, 1L, Instant.now(), Instant.now().plus(20, ChronoUnit.MINUTES), "PRESENCIAL", "OCUPADO");
        Cita cita = new Cita(400L, "cita-uuid", 300L, 200L, null, null, "PROGRAMADA", null, Instant.now(), Instant.now());

        when(usuarioRepository.buscarPorPublicId("prof-user-uuid")).thenReturn(Optional.of(usuarioProf));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(prof));
        when(citaRepository.buscarEntidadPorPublicId("cita-uuid")).thenReturn(Optional.of(cita));
        when(citaRepository.buscarEntidadPorId(400L)).thenReturn(Optional.of(cita));
        when(slotRepository.buscarPorId(300L)).thenReturn(Optional.of(slot));
        when(atencionRepository.existePorCitaId(400L)).thenReturn(false);
        when(atencionRepository.crear(any())).thenReturn(500L);

        AtencionResponse mockResponse = new AtencionResponse(
                "atn-uuid-500", "cita-uuid", "pac-uuid", "Juan Perez", "prof-uuid", "Dra. Ana Ruiz",
                "Medicina General", "ABIERTA", Instant.now(), null, null, null, null, null, null, null, List.of()
        );
        when(atencionRepository.buscarDetallePorPublicId(anyString())).thenReturn(Optional.of(mockResponse));

        // 1. Iniciar atención
        IniciarAtencionRequest iniciarReq = new IniciarAtencionRequest("cita-uuid");
        attentionService.iniciarAtencion(iniciarReq, "prof-user-uuid", "127.0.0.1");

        // 2. Cerrar atención con CIE-10 y evolución clínica sensible
        Atencion atencionIniciada = new Atencion(500L, "atn-uuid-500", 400L, 200L, 100L, "ABIERTA");
        when(atencionRepository.buscarEntidadPorPublicId("atn-uuid-500")).thenReturn(Optional.of(atencionIniciada));

        DiagnosticoCie10 cie10 = new DiagnosticoCie10(1L, "I20.0", "Angina inestable aguda", "ACTIVO");
        when(cie10Repository.buscarPorCodigo("I20.0")).thenReturn(Optional.of(cie10));

        CerrarAtencionRequest cerrarReq = new CerrarAtencionRequest(
                "I20.0",
                "Motivo de consulta general",
                "Evolución clínica crítica: " + SENSITIVE_CLINICAL_NOTE,
                SENSITIVE_INDICATION,
                null
        );
        attentionService.cerrarAtencion("atn-uuid-500", cerrarReq, "prof-user-uuid", "127.0.0.1");

        // Verificar que ningún log contenga notas clínicas, diagnósticos o documentos del paciente
        assertLogsDoNotContain(
                SENSITIVE_CLINICAL_NOTE,
                "Angina inestable",
                "I20.0"
        );
    }

    @Test
    @DisplayName("Sanitización: Prescripción médica no filtra nombres de fármacos, dosis ni indicaciones en los logs")
    void prescripcion_noFiltraMedicamentosNiIndicaciones() {
        Usuario usuarioProf = new Usuario(10L, "prof-user-uuid", "doc@test.com", "hash", "ACTIVO", 0, null, Instant.now(), false, false, null, null);
        Profesional prof = new Profesional(100L, 10L, "prof-uuid", 1L, "RM-1010", "Dra. Ana", "Ruiz", Instant.now(), Instant.now());
        Atencion atencion = new Atencion(500L, "atn-uuid", 400L, 200L, 100L, "CERRADA");
        Medicamento med = new Medicamento(1L, "med-uuid", "MED-MORF", SENSITIVE_MEDICATION, "Morfina", "Ampolla", "10mg/ml", "ACTIVO");

        when(usuarioRepository.buscarPorPublicId("prof-user-uuid")).thenReturn(Optional.of(usuarioProf));
        when(profesionalRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(prof));
        when(atencionRepository.buscarEntidadPorPublicId("atn-uuid")).thenReturn(Optional.of(atencion));
        when(medicamentoRepository.buscarPorPublicId("med-uuid")).thenReturn(Optional.of(med));
        when(recetaRepository.crearReceta(any())).thenReturn(77L);
        when(recetaRepository.buscarPorPublicId(anyString())).thenReturn(Optional.of(
                new com.meditriaje.dto.prescription.RecetaResponse(
                        "rx-pub-1", "atn-uuid", "pac-uuid", "Juan Perez", "prof-uuid", "Dra. Ana Ruiz",
                        "Medicina General", 30, Instant.now(), List.of()
                )
        ));

        CrearRecetaDetalleRequest detalle = new CrearRecetaDetalleRequest(
                "med-uuid", "10mg", "Cada 4h", 3, 6, SENSITIVE_INDICATION
        );
        CrearRecetaRequest request = new CrearRecetaRequest("atn-uuid", 30, List.of(detalle));

        prescriptionService.emitirReceta(request, "prof-user-uuid", "127.0.0.1");

        assertLogsDoNotContain(
                SENSITIVE_MEDICATION,
                "Morfina",
                SENSITIVE_INDICATION
        );
    }

    @Test
    @DisplayName("Sanitización: Generación de QR de emergencia no filtra PIN ni documentos en logs")
    void qrEmergencia_noFiltraPinNiDocumentosEnLogs() {
        Usuario usuarioPac = new Usuario(20L, "pac-user-uuid", "pac@test.com", "hash", "ACTIVO", 0, null, Instant.now(), false, false, null, null);
        Paciente pac = new Paciente(200L, 20L, "pac-uuid", "CC", SENSITIVE_DOC, "Juan", "Perez", LocalDate.of(1990, 1, 1), "3001234567", Instant.now(), Instant.now());

        when(usuarioRepository.buscarPorPublicId("pac-user-uuid")).thenReturn(Optional.of(usuarioPac));
        when(pacienteRepository.buscarPorUsuarioId(20L)).thenReturn(Optional.of(pac));
        when(passwordEncoder.encode(SENSITIVE_PIN)).thenReturn("argon2id$hashedpin");
        when(accesoTemporalQrRepository.crear(any())).thenReturn(99L);

        GenerarQrRequest request = new GenerarQrRequest(SENSITIVE_PIN, true, true, true, true);
        emergencyQrService.generarAccesoQr(request, "pac-user-uuid", "192.168.1.10");

        assertLogsDoNotContain(
                SENSITIVE_PIN,
                SENSITIVE_DOC
        );
    }
}
