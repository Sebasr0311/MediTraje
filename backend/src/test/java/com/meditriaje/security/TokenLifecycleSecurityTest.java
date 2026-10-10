package com.meditriaje.security;

import com.meditriaje.dto.AuthSessionResponse;
import com.meditriaje.dto.AuthTokens;
import com.meditriaje.exception.TokenInvalidoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.RefreshToken;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.CodigoVerificacionRepository;
import com.meditriaje.repository.ConsentimientoRepository;
import com.meditriaje.repository.MfaBackupCodeRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.RefreshTokenRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.service.AuditoriaService;
import com.meditriaje.service.AuthService;
import com.meditriaje.service.email.EmailService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
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
 * Suite 7: Pruebas de seguridad del ciclo de vida y resistencia a ataques sobre tokens (ADR-002, T6).
 * Valida expiración, manipulación criptográfica, rotación estricta y mitigación de reuso de refresh tokens.
 */
@ExtendWith(MockitoExtension.class)
class TokenLifecycleSecurityTest {

    private static final String SECRET_KEY_256 = "c2VjdXJpdHktand0LXNlY3JldC1rZXktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";
    private static final String ANOTHER_SECRET_KEY = "YW5vdGhlci1zZWNyZXQta2V5LW11c3QtYmUtYXQtbGVhc3QtMjU2LWJpdHMtbG9uZy1leHRyYQ==";

    private JwtService jwtService;

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
    private TotpService totpService;
    @Mock
    private EmailService emailService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET_KEY_256, 15);
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

    // =========================================================================
    // PARTE 1: Access Tokens (JWT) - Ataques y Manipulaciones
    // =========================================================================

    @Test
    @DisplayName("JWT: Token expirado es rechazado categóricamente por esValido()")
    void accessToken_expirado_esRechazado() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_KEY_256));
        Instant pasado = Instant.now().minus(10, ChronoUnit.MINUTES);

        String expiredToken = Jwts.builder()
                .subject("usr-uuid-1")
                .claim("email", "paciente@test.com")
                .claim("roles", List.of("ROLE_PACIENTE"))
                .issuedAt(Date.from(pasado.minus(15, ChronoUnit.MINUTES)))
                .expiration(Date.from(pasado))
                .signWith(key)
                .compact();

        assertThat(jwtService.esValido(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("JWT: Token con firma manipulada/alterada es rechazado por esValido()")
    void accessToken_firmaManipulada_esRechazado() {
        String tokenValido = jwtService.generarAccessToken("usr-uuid-1", "paciente@test.com", List.of("ROLE_PACIENTE"));
        String[] partes = tokenValido.split("\\.");
        assertThat(partes).hasSize(3);

        // Modificar el último byte de la firma
        String firmaAlterada = partes[2].substring(0, partes[2].length() - 2) + "ZZ";
        String tokenManipulado = partes[0] + "." + partes[1] + "." + firmaAlterada;

        assertThat(jwtService.esValido(tokenManipulado)).isFalse();
    }

    @Test
    @DisplayName("JWT: Token con payload manipulado (escalada de privilegios) es rechazado")
    void accessToken_payloadManipulado_esRechazado() {
        String tokenValido = jwtService.generarAccessToken("usr-uuid-1", "paciente@test.com", List.of("ROLE_PACIENTE"));
        String[] partes = tokenValido.split("\\.");

        // Decodificar el payload original
        String payloadOriginal = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);

        // Inyectar rol administrativo en el payload manteniendo la firma anterior
        String payloadManipulado = payloadOriginal.replace("ROLE_PACIENTE", "ROLE_ADMINISTRADOR");
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadManipulado.getBytes(StandardCharsets.UTF_8));

        String tokenManipulado = partes[0] + "." + payloadBase64 + "." + partes[2];

        assertThat(jwtService.esValido(tokenManipulado)).isFalse();
    }

    @Test
    @DisplayName("JWT: Token firmado con una clave secreta distinta es rechazado")
    void accessToken_claveSecretaDistinta_esRechazado() {
        JwtService foreignJwtService = new JwtService(ANOTHER_SECRET_KEY, 15);
        String foreignToken = foreignJwtService.generarAccessToken("usr-uuid-1", "admin@test.com", List.of("ROLE_ADMINISTRADOR"));

        assertThat(jwtService.esValido(foreignToken)).isFalse();
    }

    @Test
    @DisplayName("JWT: Tokens nulos, vacíos o no-JWT retornan false de forma segura")
    void accessToken_malformadoONulo_retornaFalse() {
        assertThat(jwtService.esValido(null)).isFalse();
        assertThat(jwtService.esValido("")).isFalse();
        assertThat(jwtService.esValido("Bearer null")).isFalse();
        assertThat(jwtService.esValido("token.con.dos.puntos.demas.invalido")).isFalse();
    }

    // =========================================================================
    // PARTE 2: Refresh Tokens - Ciclo de Vida, Rotación y Detección de Reuso
    // =========================================================================

    @Test
    @DisplayName("Refresh Token: Rotación exitosa invalida el token actual y emite uno nuevo")
    void refreshToken_rotacionExitosa_emiteNuevoTokenYRevocaAnterior() {
        String rawToken = "raw-refresh-token-12345";
        String tokenHash = TokenHashUtil.hash(rawToken);

        RefreshToken tokenVigente = new RefreshToken(
                1L, 10L, tokenHash,
                Instant.now().plus(7, ChronoUnit.DAYS),
                false, Instant.now()
        );

        Usuario usuario = new Usuario(
                10L, "usr-uuid-10", "usuario@test.com", "hash",
                "ACTIVO", 0, null, Instant.now(), false, false, null, null
        );

        when(refreshTokenRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(tokenVigente));
        when(usuarioRepository.buscarPorId(10L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.obtenerRoles(10L)).thenReturn(List.of("ROLE_PACIENTE"));

        AuthTokens resultado = authService.refresh(rawToken, "192.168.1.1");

        assertThat(resultado).isNotNull();
        assertThat(resultado.accessToken()).isNotBlank();
        assertThat(resultado.rawRefreshToken()).isNotBlank().isNotEqualTo(rawToken);

        // Verifica que el token viejo haya sido revocado en BD
        verify(refreshTokenRepository).revocar(1L);
        // Verifica que el nuevo refresh token haya sido creado
        verify(refreshTokenRepository).crear(eq(10L), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("Refresh Token: Detección de reuso de token revocado revoca TODAS las sesiones del usuario (RFC 6749)")
    void refreshToken_reusoDeTokenRevocado_revocaTodasLasSesionesDelUsuario() {
        String rawTokenReusado = "stolen-or-reused-refresh-token";
        String tokenHash = TokenHashUtil.hash(rawTokenReusado);

        // Token que YA fue marcado como revocado previamente
        RefreshToken tokenYaRevocado = new RefreshToken(
                55L, 20L, tokenHash,
                Instant.now().plus(5, ChronoUnit.DAYS),
                true, Instant.now().minus(1, ChronoUnit.HOURS)
        );

        when(refreshTokenRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(tokenYaRevocado));

        assertThatThrownBy(() -> authService.refresh(rawTokenReusado, "203.0.113.195"))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("Sesion revocada por seguridad debido a deteccion de reuso");

        // CRÍTICO: Debe invalidar inmediatamente TODAS las sesiones activas del usuario víctima
        verify(refreshTokenRepository).revocarTodosPorUsuario(20L);

        // Debe auditar como intento bloqueado
        verify(auditoriaService).registrarEvento(
                eq(20L),
                eq(AccionAuditable.LOGIN_FALLIDO),
                eq("REFRESH_TOKEN"),
                eq("55"),
                eq(ResultadoAuditoria.BLOQUEADO),
                eq("203.0.113.195")
        );

        // No debe emitirse ningún nuevo token
        verify(refreshTokenRepository, never()).crear(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("Refresh Token: Token expirado es revocado y lanza TokenInvalidoException")
    void refreshToken_expirado_esRevocadoYLanzaExcepcion() {
        String rawToken = "expired-refresh-token";
        String tokenHash = TokenHashUtil.hash(rawToken);

        RefreshToken tokenExpirado = new RefreshToken(
                99L, 10L, tokenHash,
                Instant.now().minus(2, ChronoUnit.HOURS), // Expiró en el pasado
                false, Instant.now().minus(8, ChronoUnit.DAYS)
        );

        when(refreshTokenRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(tokenExpirado));

        assertThatThrownBy(() -> authService.refresh(rawToken, "127.0.0.1"))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("El token de sesion ha expirado");

        // El token expirado debe ser revocado en BD para higiene de datos
        verify(refreshTokenRepository).revocar(99L);
        verify(refreshTokenRepository, never()).crear(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("Refresh Token: Token inexistente o no proporcionado lanza TokenInvalidoException")
    void refreshToken_inexistenteONulo_lanzaExcepcion() {
        assertThatThrownBy(() -> authService.refresh(null, "127.0.0.1"))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("no proporcionado");

        assertThatThrownBy(() -> authService.refresh("   ", "127.0.0.1"))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("no proporcionado");

        when(refreshTokenRepository.buscarPorTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("random-token-no-existe", "127.0.0.1"))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("Token de sesion invalido");
    }
}
