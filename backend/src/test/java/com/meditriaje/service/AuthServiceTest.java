package com.meditriaje.service;

import com.meditriaje.dto.AuthTokens;
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
    private ConsentimientoRepository consentimientoRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                usuarioRepository,
                pacienteRepository,
                consentimientoRepository,
                refreshTokenRepository,
                auditoriaService,
                passwordEncoder,
                jwtService,
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
}
