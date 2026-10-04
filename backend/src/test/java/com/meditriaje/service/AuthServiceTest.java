package com.meditriaje.service;

import com.meditriaje.dto.AuthTokens;
import com.meditriaje.dto.CambiarPasswordRequest;
import com.meditriaje.dto.LoginRequest;
import com.meditriaje.dto.RegistroPacienteRequest;
import com.meditriaje.dto.RegistroPacienteResponse;
import com.meditriaje.exception.CredencialesInvalidasException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.TokenInvalidoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.RefreshToken;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.ConsentimientoRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.RefreshTokenRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.security.JwtService;
import com.meditriaje.security.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.meditriaje.dto.auth.MfaAuthenticateRequest;
import com.meditriaje.dto.auth.MfaSetupResponse;
import com.meditriaje.dto.auth.MfaVerifyRequest;
import com.meditriaje.dto.auth.MfaVerifyResponse;
import com.meditriaje.dto.auth.RestablecerPasswordRequest;
import com.meditriaje.dto.auth.RestablecerPasswordResponse;
import com.meditriaje.dto.auth.SolicitarRecuperacionRequest;
import com.meditriaje.dto.auth.SolicitarRecuperacionResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.model.CodigoVerificacion;
import com.meditriaje.model.Paciente;
import com.meditriaje.repository.CodigoVerificacionRepository;
import com.meditriaje.repository.MfaBackupCodeRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.security.TotpService;
import com.meditriaje.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
    private JwtService jwtService;

    @Mock
    private TotpService totpService;

    @Mock
    private EmailService emailService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
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
                totpService,
                emailService,
                7
        );
    }

    private RegistroPacienteRequest requestValido() {
        return new RegistroPacienteRequest(
                "CC",
                "1020304050",
                "Carlos",
                "Perez",
                LocalDate.of(1995, 5, 20),
                "3001234567",
                "carlos.perez@example.com",
                "Segura12345*",
                "v1.0",
                true
        );
    }

    // -------------------------------------------------------------------------
    // REGISTRO
    // -------------------------------------------------------------------------

    @Test
    void registrarPaciente_exito_creaUsuarioPacienteConsentimientoYAudita() {
        RegistroPacienteRequest req = requestValido();

        when(usuarioRepository.existePorEmail("carlos.perez@example.com")).thenReturn(false);
        when(pacienteRepository.existePorDocumento("CC", "1020304050")).thenReturn(false);
        when(passwordEncoder.encode("Segura12345*")).thenReturn("$argon2id$encoded");
        when(usuarioRepository.crear(anyString(), eq("carlos.perez@example.com"), eq("$argon2id$encoded"))).thenReturn(100L);
        when(usuarioRepository.buscarRolIdPorNombre("ROLE_PACIENTE")).thenReturn(Optional.of(1L));
        when(pacienteRepository.crear(eq(100L), anyString(), eq("CC"), eq("1020304050"), eq("Carlos"), eq("Perez"), eq(LocalDate.of(1995, 5, 20)), eq("3001234567"))).thenReturn(200L);
        when(consentimientoRepository.registrar(eq(100L), eq("v1.0"), eq(true), eq("192.168.1.5"))).thenReturn(300L);

        RegistroPacienteResponse res = authService.registrarPaciente(req, "192.168.1.5");

        assertThat(res).isNotNull();
        assertThat(res.email()).isEqualTo("carlos.perez@example.com");
        assertThat(res.pacientePublicId()).isNotBlank();
        assertThat(res.nombres()).isEqualTo("Carlos");
        assertThat(res.apellidos()).isEqualTo("Perez");

        verify(usuarioRepository).asignarRol(100L, 1L);
        verify(auditoriaService).registrarEvento(
                eq(100L),
                eq(AccionAuditable.REGISTRO_PACIENTE),
                eq("PACIENTE"),
                anyString(),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.5")
        );
    }

    @Test
    void registrarPaciente_correoDuplicado_lanzaExcepcionYAuditaFallo() {
        RegistroPacienteRequest req = requestValido();
        when(usuarioRepository.existePorEmail("carlos.perez@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrarPaciente(req, "192.168.1.5"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("correo electronico ya se encuentra registrado");

        verify(auditoriaService).registrarEvento(
                eq(AccionAuditable.REGISTRO_PACIENTE),
                eq("USUARIO"),
                isNull(),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.5")
        );
        verify(usuarioRepository, never()).crear(anyString(), anyString(), anyString());
    }

    @Test
    void registrarPaciente_documentoDuplicado_lanzaExcepcionYAuditaFallo() {
        RegistroPacienteRequest req = requestValido();
        when(usuarioRepository.existePorEmail("carlos.perez@example.com")).thenReturn(false);
        when(pacienteRepository.existePorDocumento("CC", "1020304050")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrarPaciente(req, "192.168.1.5"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("documento de identidad ya se encuentra registrado");

        verify(auditoriaService).registrarEvento(
                eq(AccionAuditable.REGISTRO_PACIENTE),
                eq("PACIENTE"),
                isNull(),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.5")
        );
        verify(usuarioRepository, never()).crear(anyString(), anyString(), anyString());
    }

    @Test
    void registrarPaciente_sinConsentimiento_lanzaExcepcionYAuditaFallo() {
        RegistroPacienteRequest req = new RegistroPacienteRequest(
                "CC", "1020304050", "Carlos", "Perez",
                LocalDate.of(1995, 5, 20), null, "carlos.perez@example.com",
                "Segura12345*", "v1.0", false
        );

        assertThatThrownBy(() -> authService.registrarPaciente(req, "192.168.1.5"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("consentimiento informado");

        verify(auditoriaService).registrarEvento(
                eq(AccionAuditable.REGISTRO_PACIENTE),
                eq("USUARIO"),
                isNull(),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.5")
        );
    }

    // -------------------------------------------------------------------------
    // LOGIN
    // -------------------------------------------------------------------------

    @Test
    void login_exito_emiteTokensYAudita() {
        LoginRequest req = new LoginRequest("carlos.perez@example.com", "Segura12345*");
        Usuario usuario = new Usuario(
                10L,
                "user-pub-id-1",
                "carlos.perez@example.com",
                "$argon2id$hashed",
                "ACTIVO",
                0,
                null,
                Instant.now()
        );

        when(usuarioRepository.buscarPorEmail("carlos.perez@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Segura12345*", "$argon2id$hashed")).thenReturn(true);
        when(usuarioRepository.obtenerRoles(10L)).thenReturn(List.of("ROLE_PACIENTE"));
        when(jwtService.generarAccessToken(eq("user-pub-id-1"), eq("carlos.perez@example.com"), eq(List.of("ROLE_PACIENTE"))))
                .thenReturn("mock.access.jwt");

        AuthTokens resultado = authService.login(req, "192.168.1.10");

        assertThat(resultado).isNotNull();
        assertThat(resultado.accessToken()).isEqualTo("mock.access.jwt");
        assertThat(resultado.rawRefreshToken()).isNotBlank();
        assertThat(resultado.sessionResponse().email()).isEqualTo("carlos.perez@example.com");
        assertThat(resultado.sessionResponse().roles()).containsExactly("ROLE_PACIENTE");

        verify(refreshTokenRepository).crear(eq(10L), anyString(), any(Instant.class));
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.LOGIN_EXITOSO),
                eq("USUARIO"),
                eq("user-pub-id-1"),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.10")
        );
    }

    @Test
    void login_correoInexistente_lanzaCredencialesInvalidasYAuditaFallo() {
        LoginRequest req = new LoginRequest("desconocido@example.com", "CualquierClave1*");
        when(usuarioRepository.buscarPorEmail("desconocido@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req, "192.168.1.10"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Credenciales invalidas.");

        verify(auditoriaService).registrarEvento(
                eq(AccionAuditable.LOGIN_FALLIDO),
                eq("USUARIO"),
                isNull(),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.10")
        );
        verify(refreshTokenRepository, never()).crear(anyLong(), anyString(), any());
    }

    @Test
    void login_passwordErroneo_incrementaIntentosYAuditaFallo() {
        LoginRequest req = new LoginRequest("carlos.perez@example.com", "ClaveErronea1*");
        Usuario usuario = new Usuario(
                10L,
                "user-pub-id-1",
                "carlos.perez@example.com",
                "$argon2id$hashed",
                "ACTIVO",
                2, // 2 previos
                null,
                Instant.now()
        );

        when(usuarioRepository.buscarPorEmail("carlos.perez@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("ClaveErronea1*", "$argon2id$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req, "192.168.1.10"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Credenciales invalidas.");

        verify(usuarioRepository).actualizarIntentosFallidos(10L, 3, null, "ACTIVO");
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.LOGIN_FALLIDO),
                eq("USUARIO"),
                eq("user-pub-id-1"),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.10")
        );
    }

    @Test
    void login_quintoFalloConsecutivo_bloqueaCuentaTemporalmente() {
        LoginRequest req = new LoginRequest("carlos.perez@example.com", "ClaveErronea1*");
        Usuario usuario = new Usuario(
                10L,
                "user-pub-id-1",
                "carlos.perez@example.com",
                "$argon2id$hashed",
                "ACTIVO",
                4, // 4 previos -> este será el 5to
                null,
                Instant.now()
        );

        when(usuarioRepository.buscarPorEmail("carlos.perez@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("ClaveErronea1*", "$argon2id$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req, "192.168.1.10"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Credenciales invalidas.");

        verify(usuarioRepository).actualizarIntentosFallidos(eq(10L), eq(5), any(Instant.class), eq("BLOQUEADO"));
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.LOGIN_FALLIDO),
                eq("USUARIO"),
                eq("user-pub-id-1"),
                eq(ResultadoAuditoria.BLOQUEADO),
                eq("192.168.1.10")
        );
    }

    @Test
    void login_cuentaBloqueada_rechazaInmediatamenteSinVerificarPassword() {
        LoginRequest req = new LoginRequest("carlos.perez@example.com", "Segura12345*");
        Usuario usuario = new Usuario(
                10L,
                "user-pub-id-1",
                "carlos.perez@example.com",
                "$argon2id$hashed",
                "BLOQUEADO",
                5,
                Instant.now().plus(10, ChronoUnit.MINUTES), // Le faltan 10 minutos de bloqueo
                Instant.now()
        );

        when(usuarioRepository.buscarPorEmail("carlos.perez@example.com")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(req, "192.168.1.10"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Credenciales invalidas.");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.LOGIN_FALLIDO),
                eq("USUARIO"),
                eq("user-pub-id-1"),
                eq(ResultadoAuditoria.BLOQUEADO),
                eq("192.168.1.10")
        );
    }

    // -------------------------------------------------------------------------
    // REFRESH & ROTACIÓN
    // -------------------------------------------------------------------------

    @Test
    void refresh_exito_rotaTokenYRetornaNuevaSesion() {
        String rawToken = "raw-refresh-token-xyz";
        String tokenHash = TokenHashUtil.hash(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                55L,
                10L,
                tokenHash,
                Instant.now().plus(6, ChronoUnit.DAYS),
                false,
                Instant.now()
        );

        Usuario usuario = new Usuario(
                10L,
                "user-pub-id-1",
                "carlos.perez@example.com",
                "$argon2id$hashed",
                "ACTIVO",
                0,
                null,
                Instant.now()
        );

        when(refreshTokenRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));
        when(usuarioRepository.buscarPorId(10L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.obtenerRoles(10L)).thenReturn(List.of("ROLE_PACIENTE"));
        when(jwtService.generarAccessToken(eq("user-pub-id-1"), eq("carlos.perez@example.com"), eq(List.of("ROLE_PACIENTE"))))
                .thenReturn("new.access.jwt");

        AuthTokens resultado = authService.refresh(rawToken, "192.168.1.10");

        assertThat(resultado).isNotNull();
        assertThat(resultado.accessToken()).isEqualTo("new.access.jwt");
        assertThat(resultado.rawRefreshToken()).isNotBlank();
        assertThat(resultado.rawRefreshToken()).isNotEqualTo(rawToken);

        // Se revoca el token anterior y se persiste el nuevo rotativo
        verify(refreshTokenRepository).revocar(55L);
        verify(refreshTokenRepository).crear(eq(10L), anyString(), any(Instant.class));
    }

    @Test
    void refresh_tokenYaRevocado_activaMitigacionRoboYRevocaTodosLosTokens() {
        String rawToken = "stolen-refresh-token";
        String tokenHash = TokenHashUtil.hash(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                55L,
                10L,
                tokenHash,
                Instant.now().plus(6, ChronoUnit.DAYS),
                true, // Ya revocado
                Instant.now()
        );

        when(refreshTokenRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> authService.refresh(rawToken, "192.168.1.10"))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("deteccion de reuso");

        // Revocación masiva de todas las sesiones activas del usuario comprometido
        verify(refreshTokenRepository).revocarTodosPorUsuario(10L);
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.LOGIN_FALLIDO),
                eq("REFRESH_TOKEN"),
                eq("55"),
                eq(ResultadoAuditoria.BLOQUEADO),
                eq("192.168.1.10")
        );
    }

    @Test
    void refresh_tokenExpirado_revocaYRechaza() {
        String rawToken = "expired-refresh-token";
        String tokenHash = TokenHashUtil.hash(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                55L,
                10L,
                tokenHash,
                Instant.now().minus(1, ChronoUnit.DAYS), // Expirado ayer
                false,
                Instant.now()
        );

        when(refreshTokenRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> authService.refresh(rawToken, "192.168.1.10"))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("expirado");

        verify(refreshTokenRepository).revocar(55L);
    }

    // -------------------------------------------------------------------------
    // LOGOUT
    // -------------------------------------------------------------------------

    @Test
    void logout_conTokenValido_revocaYAudita() {
        String rawToken = "active-refresh-token";
        String tokenHash = TokenHashUtil.hash(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                55L,
                10L,
                tokenHash,
                Instant.now().plus(5, ChronoUnit.DAYS),
                false,
                Instant.now()
        );
        Usuario usuario = new Usuario(
                10L,
                "user-pub-id-1",
                "carlos.perez@example.com",
                "$argon2id$hashed",
                "ACTIVO",
                0,
                null,
                Instant.now()
        );

        when(refreshTokenRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));
        when(usuarioRepository.buscarPorId(10L)).thenReturn(Optional.of(usuario));

        authService.logout(rawToken, "192.168.1.10");

        verify(refreshTokenRepository).revocar(55L);
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.LOGOUT),
                eq("USUARIO"),
                eq("user-pub-id-1"),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.10")
        );
    }

    // -------------------------------------------------------------------------
    // CAMBIO DE CONTRASEÑA
    // -------------------------------------------------------------------------

    @Test
    void cambiarPassword_conDatosValidos_actualizaPasswordYAudita() {
        String userPubId = "user-pub-id-1";
        Usuario usuario = new Usuario(
                10L, userPubId, "carlos@hospital.com", "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), true
        );
        CambiarPasswordRequest req = new CambiarPasswordRequest("PasswordActual123*", "PasswordNuevo123*");

        when(usuarioRepository.buscarPorPublicId(userPubId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("PasswordActual123*", "$argon2id$oldhash")).thenReturn(true);
        when(passwordEncoder.matches("PasswordNuevo123*", "$argon2id$oldhash")).thenReturn(false);
        when(passwordEncoder.encode("PasswordNuevo123*")).thenReturn("$argon2id$newhash");

        authService.cambiarPassword(userPubId, req, "192.168.1.10");

        verify(usuarioRepository).actualizarPassword(10L, "$argon2id$newhash", false);
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.CAMBIO_PASSWORD),
                eq("USUARIO"),
                eq(userPubId),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.10")
        );
    }

    @Test
    void cambiarPassword_passwordActualIncorrecto_lanzaCredencialesInvalidasYAuditaFallo() {
        String userPubId = "user-pub-id-1";
        Usuario usuario = new Usuario(
                10L, userPubId, "carlos@hospital.com", "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), true
        );
        CambiarPasswordRequest req = new CambiarPasswordRequest("PasswordErroneo123*", "PasswordNuevo123*");

        when(usuarioRepository.buscarPorPublicId(userPubId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("PasswordErroneo123*", "$argon2id$oldhash")).thenReturn(false);

        assertThatThrownBy(() -> authService.cambiarPassword(userPubId, req, "192.168.1.10"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageContaining("Credenciales invalidas");

        verify(usuarioRepository, never()).actualizarPassword(anyLong(), anyString(), anyBoolean());
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.CAMBIO_PASSWORD),
                eq("USUARIO"),
                eq(userPubId),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.10")
        );
    }

    @Test
    void cambiarPassword_passwordNuevoIgualAlActual_lanzaDatosInvalidosExceptionYAuditaFallo() {
        String userPubId = "user-pub-id-1";
        Usuario usuario = new Usuario(
                10L, userPubId, "carlos@hospital.com", "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), true
        );
        CambiarPasswordRequest req = new CambiarPasswordRequest("PasswordActual123*", "PasswordActual123*");

        when(usuarioRepository.buscarPorPublicId(userPubId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("PasswordActual123*", "$argon2id$oldhash")).thenReturn(true);

        assertThatThrownBy(() -> authService.cambiarPassword(userPubId, req, "192.168.1.10"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("no puede ser igual a la anterior");

        verify(usuarioRepository, never()).actualizarPassword(anyLong(), anyString(), anyBoolean());
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.CAMBIO_PASSWORD),
                eq("USUARIO"),
                eq(userPubId),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.10")
        );
    }

    @Test
    void cambiarPassword_usuarioNoEncontrado_lanzaCredencialesInvalidasException() {
        String userPubId = "user-no-existe";
        CambiarPasswordRequest req = new CambiarPasswordRequest("PasswordActual123*", "PasswordNuevo123*");

        when(usuarioRepository.buscarPorPublicId(userPubId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.cambiarPassword(userPubId, req, "192.168.1.10"))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(usuarioRepository, never()).actualizarPassword(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void solicitarRecuperacionPassword_usuarioExiste_generaCodigoEnviaEmailYAuditaExito() {
        String email = "carlos@hospital.com";
        Usuario usuario = new Usuario(
                10L, "usr-pub-1", email, "$argon2id$hash", "ACTIVO", 0, null, Instant.now(), false
        );
        Paciente paciente = new Paciente(
                5L, 10L, "pac-pub-1", "CC", "12345678", "Carlos", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", Instant.now(), null
        );

        when(usuarioRepository.buscarPorEmail(email)).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(paciente));

        SolicitarRecuperacionRequest req = new SolicitarRecuperacionRequest(email);
        SolicitarRecuperacionResponse resp = authService.solicitarRecuperacionPassword(req, "192.168.1.50");

        assertThat(resp.mensaje()).isEqualTo(SolicitarRecuperacionResponse.MENSAJE_DEFAULT);

        verify(codigoVerificacionRepository).invalidarCodigosPrevios(10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD);
        verify(codigoVerificacionRepository).crear(any(CodigoVerificacion.class));
        verify(emailService).enviarCodigoRecuperacion(eq(email), eq("Carlos"), anyString(), eq(15));
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.SOLICITUD_RECUPERACION_PASSWORD),
                eq("USUARIO"),
                eq("usr-pub-1"),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.50")
        );
    }

    @Test
    void solicitarRecuperacionPassword_usuarioNoExiste_retornaMensajeGenericoSinEnviarEmail() {
        String email = "fantasma@hospital.com";
        when(usuarioRepository.buscarPorEmail(email)).thenReturn(Optional.empty());

        SolicitarRecuperacionRequest req = new SolicitarRecuperacionRequest(email);
        SolicitarRecuperacionResponse resp = authService.solicitarRecuperacionPassword(req, "192.168.1.50");

        assertThat(resp.mensaje()).isEqualTo(SolicitarRecuperacionResponse.MENSAJE_DEFAULT);

        verify(codigoVerificacionRepository, never()).crear(any());
        verify(emailService, never()).enviarCodigoRecuperacion(anyString(), anyString(), anyString(), anyInt());
        verify(auditoriaService).registrarEvento(
                eq(AccionAuditable.SOLICITUD_RECUPERACION_PASSWORD),
                eq("USUARIO"),
                isNull(),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.50")
        );
    }

    @Test
    void restablecerPassword_exito_actualizaHashRevocaSesionesYAuditaExito() {
        String email = "carlos@hospital.com";
        String rawCode = "654321";
        String codeHash = TokenHashUtil.hash(rawCode);
        Usuario usuario = new Usuario(
                10L, "usr-pub-1", email, "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), false
        );
        CodigoVerificacion codigo = new CodigoVerificacion(
                1L, "cod-pub-1", 10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD,
                codeHash, Instant.now().plus(10, ChronoUnit.MINUTES), 0, 3, false, Instant.now()
        );

        when(usuarioRepository.buscarPorEmail(email)).thenReturn(Optional.of(usuario));
        when(codigoVerificacionRepository.buscarUltimoPendientePorUsuarioYTipo(10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD))
                .thenReturn(Optional.of(codigo));
        when(passwordEncoder.matches("NuevoPasswordSeguro123*", "$argon2id$oldhash")).thenReturn(false);
        when(passwordEncoder.encode("NuevoPasswordSeguro123*")).thenReturn("$argon2id$newhash");

        RestablecerPasswordRequest req = new RestablecerPasswordRequest(email, rawCode, "NuevoPasswordSeguro123*");
        RestablecerPasswordResponse resp = authService.restablecerPassword(req, "192.168.1.50");

        assertThat(resp.mensaje()).isEqualTo(RestablecerPasswordResponse.MENSAJE_DEFAULT);

        verify(usuarioRepository).actualizarPassword(10L, "$argon2id$newhash", false);
        verify(usuarioRepository).restablecerIntentos(10L);
        verify(codigoVerificacionRepository).marcarComoUsado(1L);
        verify(refreshTokenRepository).revocarTodosPorUsuario(10L);
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.RECUPERACION_PASSWORD_EXITO),
                eq("USUARIO"),
                eq("usr-pub-1"),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.50")
        );
    }

    @Test
    void restablecerPassword_codigoIncorrecto_incrementaIntentosYAuditaFallo() {
        String email = "carlos@hospital.com";
        String rawCodeCorrecto = "654321";
        String rawCodeEnviado = "111111";
        String codeHash = TokenHashUtil.hash(rawCodeCorrecto);
        Usuario usuario = new Usuario(
                10L, "usr-pub-1", email, "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), false
        );
        CodigoVerificacion codigo = new CodigoVerificacion(
                1L, "cod-pub-1", 10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD,
                codeHash, Instant.now().plus(10, ChronoUnit.MINUTES), 0, 3, false, Instant.now()
        );

        when(usuarioRepository.buscarPorEmail(email)).thenReturn(Optional.of(usuario));
        when(codigoVerificacionRepository.buscarUltimoPendientePorUsuarioYTipo(10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD))
                .thenReturn(Optional.of(codigo));

        RestablecerPasswordRequest req = new RestablecerPasswordRequest(email, rawCodeEnviado, "NuevoPasswordSeguro123*");

        assertThatThrownBy(() -> authService.restablecerPassword(req, "192.168.1.50"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageContaining("Codigo de verificacion incorrecto");

        verify(codigoVerificacionRepository).incrementarIntentos(1L);
        verify(usuarioRepository, never()).actualizarPassword(anyLong(), anyString(), anyBoolean());
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.RECUPERACION_PASSWORD_FALLO),
                eq("USUARIO"),
                eq("usr-pub-1"),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.50")
        );
    }

    @Test
    void restablecerPassword_codigoExpirado_marcaComoUsadoYAuditaFallo() {
        String email = "carlos@hospital.com";
        Usuario usuario = new Usuario(
                10L, "usr-pub-1", email, "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), false
        );
        CodigoVerificacion codigo = new CodigoVerificacion(
                1L, "cod-pub-1", 10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD,
                "somehash", Instant.now().minus(5, ChronoUnit.MINUTES), 0, 3, false, Instant.now().minus(20, ChronoUnit.MINUTES)
        );

        when(usuarioRepository.buscarPorEmail(email)).thenReturn(Optional.of(usuario));
        when(codigoVerificacionRepository.buscarUltimoPendientePorUsuarioYTipo(10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD))
                .thenReturn(Optional.of(codigo));

        RestablecerPasswordRequest req = new RestablecerPasswordRequest(email, "123456", "NuevoPasswordSeguro123*");

        assertThatThrownBy(() -> authService.restablecerPassword(req, "192.168.1.50"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageContaining("expirado");

        verify(codigoVerificacionRepository).marcarComoUsado(1L);
        verify(usuarioRepository, never()).actualizarPassword(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void restablecerPassword_codigoSuperoMaxIntentos_marcaComoUsadoYAuditaBloqueado() {
        String email = "carlos@hospital.com";
        Usuario usuario = new Usuario(
                10L, "usr-pub-1", email, "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), false
        );
        CodigoVerificacion codigo = new CodigoVerificacion(
                1L, "cod-pub-1", 10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD,
                "somehash", Instant.now().plus(10, ChronoUnit.MINUTES), 3, 3, false, Instant.now()
        );

        when(usuarioRepository.buscarPorEmail(email)).thenReturn(Optional.of(usuario));
        when(codigoVerificacionRepository.buscarUltimoPendientePorUsuarioYTipo(10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD))
                .thenReturn(Optional.of(codigo));

        RestablecerPasswordRequest req = new RestablecerPasswordRequest(email, "123456", "NuevoPasswordSeguro123*");

        assertThatThrownBy(() -> authService.restablecerPassword(req, "192.168.1.50"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageContaining("superado el numero maximo de intentos");

        verify(codigoVerificacionRepository).marcarComoUsado(1L);
    }

    @Test
    void restablecerPassword_passwordNuevoIgualAlActual_lanzaDatosInvalidosException() {
        String email = "carlos@hospital.com";
        String rawCode = "654321";
        String codeHash = TokenHashUtil.hash(rawCode);
        Usuario usuario = new Usuario(
                10L, "usr-pub-1", email, "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), false
        );
        CodigoVerificacion codigo = new CodigoVerificacion(
                1L, "cod-pub-1", 10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD,
                codeHash, Instant.now().plus(10, ChronoUnit.MINUTES), 0, 3, false, Instant.now()
        );

        when(usuarioRepository.buscarPorEmail(email)).thenReturn(Optional.of(usuario));
        when(codigoVerificacionRepository.buscarUltimoPendientePorUsuarioYTipo(10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD))
                .thenReturn(Optional.of(codigo));
        when(passwordEncoder.matches("MismaPassword123*", "$argon2id$oldhash")).thenReturn(true);

        RestablecerPasswordRequest req = new RestablecerPasswordRequest(email, rawCode, "MismaPassword123*");

        assertThatThrownBy(() -> authService.restablecerPassword(req, "192.168.1.50"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("no puede ser igual a la anterior");

        verify(usuarioRepository, never()).actualizarPassword(anyLong(), anyString(), anyBoolean());
    }

    // -------------------------------------------------------------------------
    // AUTENTICACIÓN MULTIFACTOR (MFA TOTP) (ADR-014, F2.1.4)
    // -------------------------------------------------------------------------

    @Test
    void login_conMfaHabilitado_retornaDesafioSinTokensDefinitivos() {
        LoginRequest req = new LoginRequest("dr.garcia@hospital.com", "PasswordSeguro123*");
        Usuario usuario = new Usuario(
                20L,
                "usr-medico-1",
                "dr.garcia@hospital.com",
                "$argon2id$hashed",
                "ACTIVO",
                0,
                null,
                Instant.now(),
                false,
                true, // mfaHabilitado
                "JBSWY3DPEHPK3PXP",
                Instant.now()
        );

        when(usuarioRepository.buscarPorEmail("dr.garcia@hospital.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("PasswordSeguro123*", "$argon2id$hashed")).thenReturn(true);
        when(usuarioRepository.obtenerRoles(20L)).thenReturn(List.of("ROLE_PROFESIONAL"));
        when(jwtService.generarMfaChallengeToken("usr-medico-1", "dr.garcia@hospital.com", List.of("ROLE_PROFESIONAL")))
                .thenReturn("mock.challenge.jwt");

        AuthTokens resultado = authService.login(req, "192.168.1.100");

        assertThat(resultado).isNotNull();
        assertThat(resultado.accessToken()).isNull();
        assertThat(resultado.rawRefreshToken()).isNull();
        assertThat(resultado.sessionResponse().mfaRequerido()).isTrue();
        assertThat(resultado.sessionResponse().mfaChallengeToken()).isEqualTo("mock.challenge.jwt");

        verify(refreshTokenRepository, never()).crear(anyLong(), anyString(), any(Instant.class));
    }

    @Test
    void setupMfa_exito_guardaSecretYRetornaQrUri() {
        Usuario usuario = new Usuario(
                25L, "usr-medico-2", "medico@hospital.com", "$hash", "ACTIVO", 0, null, Instant.now(), false
        );

        when(usuarioRepository.buscarPorPublicId("usr-medico-2")).thenReturn(Optional.of(usuario));
        when(totpService.generarNuevoSecreto()).thenReturn("JBSWY3DPEHPK3PXP");
        when(totpService.generarOtpAuthUri("MediTriaje", "medico@hospital.com", "JBSWY3DPEHPK3PXP"))
                .thenReturn("otpauth://totp/MediTriaje:medico@hospital.com?secret=JBSWY3DPEHPK3PXP&issuer=MediTriaje");

        MfaSetupResponse response = authService.setupMfa("usr-medico-2", "192.168.1.100");

        assertThat(response).isNotNull();
        assertThat(response.secret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(response.qrUri()).startsWith("otpauth://totp/");

        verify(usuarioRepository).guardarMfaSecret(25L, "JBSWY3DPEHPK3PXP");
        verify(auditoriaService).registrarEvento(
                eq(25L),
                eq(AccionAuditable.MFA_SETUP),
                eq("USUARIO"),
                eq("usr-medico-2"),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.100")
        );
    }

    @Test
    void setupMfa_usuarioInactivo_lanzaAccesoNoAutorizado() {
        Usuario usuario = new Usuario(
                25L, "usr-inactivo", "inactivo@hospital.com", "$hash", "INACTIVO", 0, null, Instant.now(), false
        );

        when(usuarioRepository.buscarPorPublicId("usr-inactivo")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.setupMfa("usr-inactivo", "192.168.1.100"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("no se encuentra activa");
    }

    @Test
    void verifyMfa_codigoValido_activaMfaYGeneraCodigosRespaldo() {
        Usuario usuario = new Usuario(
                25L, "usr-medico-2", "medico@hospital.com", "$hash", "ACTIVO", 0, null, Instant.now(),
                false, false, "JBSWY3DPEHPK3PXP", null
        );

        when(usuarioRepository.buscarPorPublicId("usr-medico-2")).thenReturn(Optional.of(usuario));
        when(totpService.validarCodigo(eq("JBSWY3DPEHPK3PXP"), eq("123456"), any(Instant.class))).thenReturn(true);

        MfaVerifyRequest request = new MfaVerifyRequest("123456");
        MfaVerifyResponse response = authService.verifyMfa("usr-medico-2", request, "192.168.1.100");

        assertThat(response).isNotNull();
        assertThat(response.mfaHabilitado()).isTrue();
        assertThat(response.backupCodes()).hasSize(8);

        verify(usuarioRepository).activarMfa(25L, "JBSWY3DPEHPK3PXP");
        verify(mfaBackupCodeRepository).eliminarPorUsuario(25L);
        verify(mfaBackupCodeRepository).guardarLote(eq(25L), any());
        verify(auditoriaService).registrarEvento(
                eq(25L),
                eq(AccionAuditable.MFA_VERIFY),
                eq("USUARIO"),
                eq("usr-medico-2"),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.100")
        );
    }

    @Test
    void verifyMfa_codigoInvalido_lanzaCredencialesInvalidasException() {
        Usuario usuario = new Usuario(
                25L, "usr-medico-2", "medico@hospital.com", "$hash", "ACTIVO", 0, null, Instant.now(),
                false, false, "JBSWY3DPEHPK3PXP", null
        );

        when(usuarioRepository.buscarPorPublicId("usr-medico-2")).thenReturn(Optional.of(usuario));
        when(totpService.validarCodigo(eq("JBSWY3DPEHPK3PXP"), eq("000000"), any(Instant.class))).thenReturn(false);

        MfaVerifyRequest request = new MfaVerifyRequest("000000");

        assertThatThrownBy(() -> authService.verifyMfa("usr-medico-2", request, "192.168.1.100"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageContaining("incorrecto o expirado");

        verify(usuarioRepository, never()).activarMfa(anyLong(), anyString());
        verify(auditoriaService).registrarEvento(
                eq(25L),
                eq(AccionAuditable.MFA_VERIFY),
                eq("USUARIO"),
                eq("usr-medico-2"),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.100")
        );
    }

    @Test
    void autenticarMfa_desafioInvalido_lanzaTokenInvalidoException() {
        MfaAuthenticateRequest request = new MfaAuthenticateRequest("token-falso", "123456");
        when(jwtService.esMfaChallengeValido("token-falso")).thenReturn(false);

        assertThatThrownBy(() -> authService.autenticarMfa(request, "192.168.1.100"))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("desafio de autenticacion MFA ha expirado o es invalido");
    }

    @Test
    void autenticarMfa_conTotpValido_emiteTokensYAudita() {
        MfaAuthenticateRequest request = new MfaAuthenticateRequest("token-valido", "123456");
        Usuario usuario = new Usuario(
                30L, "usr-pub-30", "admin@hospital.com", "$hash", "ACTIVO", 0, null, Instant.now(),
                false, true, "JBSWY3DPEHPK3PXP", Instant.now()
        );

        when(jwtService.esMfaChallengeValido("token-valido")).thenReturn(true);
        when(jwtService.extraerPublicId("token-valido")).thenReturn("usr-pub-30");
        when(usuarioRepository.buscarPorPublicId("usr-pub-30")).thenReturn(Optional.of(usuario));
        when(totpService.validarCodigo(eq("JBSWY3DPEHPK3PXP"), eq("123456"), any(Instant.class))).thenReturn(true);
        when(usuarioRepository.obtenerRoles(30L)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
        when(jwtService.generarAccessToken(eq("usr-pub-30"), eq("admin@hospital.com"), eq(List.of("ROLE_ADMINISTRADOR"))))
                .thenReturn("mock.access.jwt");

        AuthTokens tokens = authService.autenticarMfa(request, "192.168.1.100");

        assertThat(tokens).isNotNull();
        assertThat(tokens.accessToken()).isEqualTo("mock.access.jwt");
        assertThat(tokens.rawRefreshToken()).isNotBlank();
        assertThat(tokens.sessionResponse().email()).isEqualTo("admin@hospital.com");

        verify(refreshTokenRepository).crear(eq(30L), anyString(), any(Instant.class));
        verify(auditoriaService).registrarEvento(
                eq(30L),
                eq(AccionAuditable.MFA_LOGIN_EXITOSO),
                eq("USUARIO"),
                eq("usr-pub-30"),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.100")
        );
        verify(auditoriaService).registrarEvento(
                eq(30L),
                eq(AccionAuditable.LOGIN_EXITOSO),
                eq("USUARIO"),
                eq("usr-pub-30"),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.100")
        );
    }

    @Test
    void autenticarMfa_conCodigoRespaldoValido_consumeCodigoYEmiteTokens() {
        MfaAuthenticateRequest request = new MfaAuthenticateRequest("token-valido", "1234-5678");
        Usuario usuario = new Usuario(
                30L, "usr-pub-30", "admin@hospital.com", "$hash", "ACTIVO", 0, null, Instant.now(),
                false, true, "JBSWY3DPEHPK3PXP", Instant.now()
        );

        when(jwtService.esMfaChallengeValido("token-valido")).thenReturn(true);
        when(jwtService.extraerPublicId("token-valido")).thenReturn("usr-pub-30");
        when(usuarioRepository.buscarPorPublicId("usr-pub-30")).thenReturn(Optional.of(usuario));
        when(mfaBackupCodeRepository.consumirCodigo(eq(30L), anyString())).thenReturn(true);
        when(usuarioRepository.obtenerRoles(30L)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
        when(jwtService.generarAccessToken(eq("usr-pub-30"), eq("admin@hospital.com"), eq(List.of("ROLE_ADMINISTRADOR"))))
                .thenReturn("mock.access.jwt");

        AuthTokens tokens = authService.autenticarMfa(request, "192.168.1.100");

        assertThat(tokens).isNotNull();
        assertThat(tokens.accessToken()).isEqualTo("mock.access.jwt");
        verify(mfaBackupCodeRepository).consumirCodigo(eq(30L), anyString());
    }

    @Test
    void autenticarMfa_conCodigoInvalido_lanzaCredencialesInvalidasException() {
        MfaAuthenticateRequest request = new MfaAuthenticateRequest("token-valido", "999999");
        Usuario usuario = new Usuario(
                30L, "usr-pub-30", "admin@hospital.com", "$hash", "ACTIVO", 0, null, Instant.now(),
                false, true, "JBSWY3DPEHPK3PXP", Instant.now()
        );

        when(jwtService.esMfaChallengeValido("token-valido")).thenReturn(true);
        when(jwtService.extraerPublicId("token-valido")).thenReturn("usr-pub-30");
        when(usuarioRepository.buscarPorPublicId("usr-pub-30")).thenReturn(Optional.of(usuario));
        when(totpService.validarCodigo(eq("JBSWY3DPEHPK3PXP"), eq("999999"), any(Instant.class))).thenReturn(false);
        when(mfaBackupCodeRepository.consumirCodigo(eq(30L), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.autenticarMfa(request, "192.168.1.100"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageContaining("invalido");

        verify(auditoriaService).registrarEvento(
                eq(30L),
                eq(AccionAuditable.MFA_LOGIN_FALLIDO),
                eq("USUARIO"),
                eq("usr-pub-30"),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.100")
        );
    }
}
