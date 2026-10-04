package com.meditriaje.service;

import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.emergency.ConsultarResumenRequest;
import com.meditriaje.dto.emergency.summary.ResumenSaludResponse;
import com.meditriaje.dto.prescription.RecetaDetalleResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.exception.CredencialesInvalidasException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccesoTemporalQr;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Alergia;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AccesoTemporalQrRepository;
import com.meditriaje.repository.AlergiaRepository;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.RecetaRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.security.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

@ExtendWith(MockitoExtension.class)
class EmergencySummaryServiceTest {

    @Mock
    private AccesoTemporalQrRepository accesoTemporalQrRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AlergiaRepository alergiaRepository;

    @Mock
    private RecetaRepository recetaRepository;

    @Mock
    private AtencionRepository atencionRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private Clock fixedClock;
    private Instant fixedInstant;

    private EmergencySummaryService emergencySummaryService;

    private Usuario usuario;
    private Paciente paciente;
    private String tokenPlano;
    private String tokenHash;

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-10-04T12:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        emergencySummaryService = new EmergencySummaryService(
                accesoTemporalQrRepository,
                pacienteRepository,
                usuarioRepository,
                alergiaRepository,
                recetaRepository,
                atencionRepository,
                auditoriaService,
                passwordEncoder,
                fixedClock
        );

        tokenPlano = "token-emergencia-12345678901234567890";
        tokenHash = TokenHashUtil.hash(tokenPlano);

        usuario = new Usuario(
                5L,
                "user-uuid-5",
                "paciente@test.com",
                "pwdHash",
                "ACTIVO",
                0,
                null,
                fixedInstant,
                false,
                false,
                null,
                null
        );

        paciente = new Paciente(
                10L,
                5L,
                "pac-uuid-10",
                "CC",
                "1020304050",
                "Maria",
                "Gomez",
                LocalDate.of(1996, 10, 4), // 30 años exactos el 2026-10-04
                "3101234567",
                fixedInstant,
                fixedInstant
        );
    }

    @Test
    @DisplayName("consultarResumenPorToken: consulta exitosa con todos los módulos y auditoría")
    void consultarResumenPorToken_exitoCompleto() {
        AccesoTemporalQr acceso = new AccesoTemporalQr(
                1L, "qr-pub-1", 10L, tokenHash, null,
                true, true, true, true,
                3, 0, false,
                fixedInstant.plus(15, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(accesoTemporalQrRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(acceso));
        when(accesoTemporalQrRepository.registrarAcceso(1L, fixedInstant)).thenReturn(1);
        when(pacienteRepository.buscarPorId(10L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.buscarPorId(5L)).thenReturn(Optional.of(usuario));

        // Alergias
        Alergia alergia = new Alergia(100L, 10L, "Penicilina", "Shock anafilactico", "GRAVE", fixedInstant);
        when(alergiaRepository.listarPorPacienteId(10L)).thenReturn(List.of(alergia));

        // Recetas vigentes
        RecetaDetalleResponse det1 = new RecetaDetalleResponse(
                "med-1", "MED-001", "Amoxicilina", "Amoxicilina", "Capsulas", "500mg",
                "1 cada 8 horas", "Oral", 7, 21, "Tomar con alimentos"
        );
        RecetaResponse recetaVigente = new RecetaResponse(
                "rec-1", "at-1", "pac-uuid-10", "Maria Gomez", "prof-1", "Dr. Perez",
                "Medicina General", 30, fixedInstant.minus(5, ChronoUnit.DAYS), List.of(det1)
        );
        when(recetaRepository.listarPorPacienteId(10L, 0, 50)).thenReturn(List.of(recetaVigente));

        // Atenciones recientes
        AtencionResponse atencion = new AtencionResponse(
                "at-1", "cita-1", "pac-uuid-10", "Maria Gomez", "prof-1", "Dr. Perez",
                "Medicina General", "CERRADA", fixedInstant.minus(5, ChronoUnit.DAYS), fixedInstant.minus(5, ChronoUnit.DAYS),
                "J00", "Rinofaringitis aguda", "Congestion y dolor de garganta", "Evolucion adecuada",
                "Reposo e hidratacion", null, List.of()
        );
        when(atencionRepository.listarHistoriaPaciente(10L, 0, 5)).thenReturn(List.of(atencion));

        ResumenSaludResponse response = emergencySummaryService.consultarResumenPorToken(
                tokenPlano,
                new ConsultarResumenRequest(null),
                "200.1.2.3"
        );

        assertThat(response).isNotNull();
        assertThat(response.paciente().nombreCompleto()).isEqualTo("Maria Gomez");
        assertThat(response.paciente().tipoDocumento()).isEqualTo("CC");
        assertThat(response.paciente().numeroDocumento()).isEqualTo("1020304050");
        assertThat(response.paciente().edad()).isEqualTo(30);
        assertThat(response.paciente().telefono()).isEqualTo("3101234567");
        assertThat(response.paciente().email()).isEqualTo("paciente@test.com");

        assertThat(response.alergias()).hasSize(1);
        assertThat(response.alergias().get(0).sustancia()).isEqualTo("Penicilina");
        assertThat(response.alergias().get(0).severidad()).isEqualTo("GRAVE");

        assertThat(response.medicamentosActivos()).hasSize(1);
        assertThat(response.medicamentosActivos().get(0).nombre()).isEqualTo("Amoxicilina");

        assertThat(response.atencionesRecientes()).hasSize(1);
        assertThat(response.atencionesRecientes().get(0).diagnosticoCodigo()).isEqualTo("J00");
        assertThat(response.atencionesRecientes().get(0).diagnosticoDescripcion()).isEqualTo("Rinofaringitis aguda");

        verify(accesoTemporalQrRepository).registrarAcceso(1L, fixedInstant);

        ArgumentCaptor<EventoAuditoria> auditCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(auditCaptor.capture());
        EventoAuditoria audit = auditCaptor.getValue();
        assertThat(audit.accion()).isEqualTo(AccionAuditable.ACCESO_EMERGENCIA_QR);
        assertThat(audit.recursoPublicId()).isEqualTo("qr-pub-1");
        assertThat(audit.ipOrigen()).isEqualTo("200.1.2.3");
    }

    @Test
    @DisplayName("consultarResumenPorToken: con PIN numérico válido permite la consulta")
    void consultarResumenPorToken_conPinValido() {
        AccesoTemporalQr accesoConPin = new AccesoTemporalQr(
                2L, "qr-pub-2", 10L, tokenHash, "argon2id$hashed",
                true, true, true, true,
                3, 0, false,
                fixedInstant.plus(15, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(accesoTemporalQrRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(accesoConPin));
        when(passwordEncoder.matches("4321", "argon2id$hashed")).thenReturn(true);
        when(accesoTemporalQrRepository.registrarAcceso(2L, fixedInstant)).thenReturn(1);
        when(pacienteRepository.buscarPorId(10L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.buscarPorId(5L)).thenReturn(Optional.of(usuario));

        ResumenSaludResponse response = emergencySummaryService.consultarResumenPorToken(
                tokenPlano,
                new ConsultarResumenRequest("4321"),
                "127.0.0.1"
        );

        assertThat(response).isNotNull();
        verify(accesoTemporalQrRepository).registrarAcceso(2L, fixedInstant);
    }

    @Test
    @DisplayName("consultarResumenPorToken: PIN incorrecto o ausente lanza CredencialesInvalidasException")
    void consultarResumenPorToken_pinIncorrecto_lanzaCredencialesInvalidasException() {
        AccesoTemporalQr accesoConPin = new AccesoTemporalQr(
                2L, "qr-pub-2", 10L, tokenHash, "argon2id$hashed",
                true, true, true, true,
                3, 0, false,
                fixedInstant.plus(15, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(accesoTemporalQrRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(accesoConPin));
        when(passwordEncoder.matches("0000", "argon2id$hashed")).thenReturn(false);

        assertThatThrownBy(() -> emergencySummaryService.consultarResumenPorToken(
                tokenPlano,
                new ConsultarResumenRequest("0000"),
                "127.0.0.1"
        )).isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageContaining("El PIN de seguridad proporcionado es incorrecto.");

        verify(accesoTemporalQrRepository, never()).registrarAcceso(anyLong(), any());
    }

    @Test
    @DisplayName("consultarResumenPorToken: flags de alcance respetan las exclusiones del paciente")
    void consultarResumenPorToken_flagsExclusiones() {
        AccesoTemporalQr accesoRestringido = new AccesoTemporalQr(
                3L, "qr-pub-3", 10L, tokenHash, null,
                false, false, false, false,
                3, 0, false,
                fixedInstant.plus(15, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(accesoTemporalQrRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(accesoRestringido));
        when(accesoTemporalQrRepository.registrarAcceso(3L, fixedInstant)).thenReturn(1);
        when(pacienteRepository.buscarPorId(10L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.buscarPorId(5L)).thenReturn(Optional.of(usuario));

        ResumenSaludResponse response = emergencySummaryService.consultarResumenPorToken(
                tokenPlano,
                new ConsultarResumenRequest(null),
                "127.0.0.1"
        );

        assertThat(response.alergias()).isEmpty();
        assertThat(response.medicamentosActivos()).isEmpty();
        assertThat(response.atencionesRecientes()).isEmpty();
        assertThat(response.paciente().telefono()).isNull();
        assertThat(response.paciente().email()).isNull();

        verify(alergiaRepository, never()).listarPorPacienteId(anyLong());
        verify(recetaRepository, never()).listarPorPacienteId(anyLong(), any(Integer.class), any(Integer.class));
        verify(atencionRepository, never()).listarHistoriaPaciente(anyLong(), any(Integer.class), any(Integer.class));
    }

    @Test
    @DisplayName("consultarResumenPorToken: recetas vencidas no se incluyen en medicamentos activos")
    void consultarResumenPorToken_recetasVencidasNoIncluidas() {
        AccesoTemporalQr acceso = new AccesoTemporalQr(
                4L, "qr-pub-4", 10L, tokenHash, null,
                false, true, false, false,
                3, 0, false,
                fixedInstant.plus(15, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(accesoTemporalQrRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(acceso));
        when(accesoTemporalQrRepository.registrarAcceso(4L, fixedInstant)).thenReturn(1);
        when(pacienteRepository.buscarPorId(10L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.buscarPorId(5L)).thenReturn(Optional.of(usuario));

        // Receta emitida hace 40 días con vigencia de 30 días -> vencida
        RecetaDetalleResponse detVencido = new RecetaDetalleResponse(
                "med-old", "MED-OLD", "Ibuprofeno", "Ibuprofeno", "Tabletas", "400mg",
                "1 cada 8h", "Oral", 3, 9, "Tomar con agua"
        );
        RecetaResponse recetaVencida = new RecetaResponse(
                "rec-old", "at-old", "pac-uuid-10", "Maria Gomez", "prof-1", "Dr. Perez",
                "Medicina General", 30, fixedInstant.minus(40, ChronoUnit.DAYS), List.of(detVencido)
        );
        when(recetaRepository.listarPorPacienteId(10L, 0, 50)).thenReturn(List.of(recetaVencida));

        ResumenSaludResponse response = emergencySummaryService.consultarResumenPorToken(
                tokenPlano,
                new ConsultarResumenRequest(null),
                "127.0.0.1"
        );

        assertThat(response.medicamentosActivos()).isEmpty();
    }

    @Test
    @DisplayName("consultarResumenPorToken: token expirado lanza DatosInvalidosException")
    void consultarResumenPorToken_expirado_lanzaDatosInvalidos() {
        AccesoTemporalQr expirado = new AccesoTemporalQr(
                5L, "qr-pub-5", 10L, tokenHash, null,
                true, true, true, true,
                3, 0, false,
                fixedInstant.minus(1, ChronoUnit.SECONDS), fixedInstant.minus(16, ChronoUnit.MINUTES), fixedInstant
        );

        when(accesoTemporalQrRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> emergencySummaryService.consultarResumenPorToken(
                tokenPlano,
                new ConsultarResumenRequest(null),
                "127.0.0.1"
        )).isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("El acceso QR ha expirado.");
    }

    @Test
    @DisplayName("consultarResumenPorToken: token revocado lanza DatosInvalidosException")
    void consultarResumenPorToken_revocado_lanzaDatosInvalidos() {
        AccesoTemporalQr revocado = new AccesoTemporalQr(
                6L, "qr-pub-6", 10L, tokenHash, null,
                true, true, true, true,
                3, 0, true,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(accesoTemporalQrRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(revocado));

        assertThatThrownBy(() -> emergencySummaryService.consultarResumenPorToken(
                tokenPlano,
                new ConsultarResumenRequest(null),
                "127.0.0.1"
        )).isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("El acceso QR ha sido revocado por el paciente.");
    }

    @Test
    @DisplayName("consultarResumenPorToken: token con lecturas agotadas lanza DatosInvalidosException")
    void consultarResumenPorToken_agotado_lanzaDatosInvalidos() {
        AccesoTemporalQr agotado = new AccesoTemporalQr(
                7L, "qr-pub-7", 10L, tokenHash, null,
                true, true, true, true,
                3, 3, false,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(accesoTemporalQrRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(agotado));

        assertThatThrownBy(() -> emergencySummaryService.consultarResumenPorToken(
                tokenPlano,
                new ConsultarResumenRequest(null),
                "127.0.0.1"
        )).isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("superado el limite maximo de lecturas permitidas");
    }

    @Test
    @DisplayName("consultarResumenPorToken: token inexistente lanza RecursoNoEncontradoException")
    void consultarResumenPorToken_inexistente_lanzaRecursoNoEncontrado() {
        when(accesoTemporalQrRepository.buscarPorTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emergencySummaryService.consultarResumenPorToken(
                "token-fantasma",
                new ConsultarResumenRequest(null),
                "127.0.0.1"
        )).isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Acceso de emergencia no valido o inexistente.");
    }
}
