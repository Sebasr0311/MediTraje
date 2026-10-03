package com.meditriaje.service;

import com.meditriaje.dto.AuthSessionResponse;
import com.meditriaje.dto.AuthTokens;
import com.meditriaje.dto.LoginRequest;
import com.meditriaje.dto.RegistroPacienteRequest;
import com.meditriaje.dto.RegistroPacienteResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de autenticación, registro de usuarios y ciclo de vida de sesiones (HU-01, ADR-002, ADR-013).
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ConsentimientoRepository consentimientoRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditoriaService auditoriaService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshExpirationDays;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ConsentimientoRepository consentimientoRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuditoriaService auditoriaService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${security.jwt.refresh-expiration-days:7}") long refreshExpirationDays
    ) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "UsuarioRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "PacienteRepository no puede ser nulo");
        this.consentimientoRepository = Objects.requireNonNull(consentimientoRepository, "ConsentimientoRepository no puede ser nulo");
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "RefreshTokenRepository no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "AuditoriaService no puede ser nulo");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder no puede ser nulo");
        this.jwtService = Objects.requireNonNull(jwtService, "JwtService no puede ser nulo");
        this.refreshExpirationDays = refreshExpirationDays;
    }

    /**
     * Registra un nuevo paciente en una única transacción atómica:
     * crea USUARIO, asigna ROLE_PACIENTE, crea PACIENTE y registra CONSENTIMIENTO.
     */
    @Transactional
    public RegistroPacienteResponse registrarPaciente(RegistroPacienteRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de registro no puede ser nula");

        // 1. Validar consentimiento
        if (!Boolean.TRUE.equals(request.aceptaConsentimiento())) {
            auditoriaService.registrarEvento(
                    AccionAuditable.REGISTRO_PACIENTE,
                    "USUARIO",
                    null,
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new DatosInvalidosException("Debe aceptar el consentimiento informado para registrarse.");
        }

        // 2. Normalizar correo y verificar duplicados
        String emailNormalizado = request.email().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existePorEmail(emailNormalizado)) {
            auditoriaService.registrarEvento(
                    AccionAuditable.REGISTRO_PACIENTE,
                    "USUARIO",
                    null,
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new DatosInvalidosException("El correo electronico ya se encuentra registrado.");
        }

        // 3. Verificar documento duplicado
        if (pacienteRepository.existePorDocumento(request.tipoDocumento(), request.numeroDocumento().trim())) {
            auditoriaService.registrarEvento(
                    AccionAuditable.REGISTRO_PACIENTE,
                    "PACIENTE",
                    null,
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new DatosInvalidosException("El documento de identidad ya se encuentra registrado.");
        }

        // 4. Hashear contraseña con Argon2id
        String passwordHash = passwordEncoder.encode(request.password());

        // 5. Crear USUARIO
        String usuarioPublicId = UUID.randomUUID().toString();
        Long usuarioId = usuarioRepository.crear(usuarioPublicId, emailNormalizado, passwordHash);

        // 6. Asignar rol ROLE_PACIENTE
        Long rolId = usuarioRepository.buscarRolIdPorNombre("ROLE_PACIENTE")
                .orElseThrow(() -> new IllegalStateException("El rol ROLE_PACIENTE no existe en el sistema."));
        usuarioRepository.asignarRol(usuarioId, rolId);

        // 7. Crear PACIENTE
        String pacientePublicId = UUID.randomUUID().toString();
        pacienteRepository.crear(
                usuarioId,
                pacientePublicId,
                request.tipoDocumento(),
                request.numeroDocumento().trim(),
                request.nombres().trim(),
                request.apellidos().trim(),
                request.fechaNacimiento(),
                request.telefono() != null ? request.telefono().trim() : null
        );

        // 8. Registrar CONSENTIMIENTO
        consentimientoRepository.registrar(
                usuarioId,
                request.consentimientoTextoVersion().trim(),
                true,
                ipOrigen
        );

        // 9. Registrar evento exitoso en auditoría
        auditoriaService.registrarEvento(
                usuarioId,
                AccionAuditable.REGISTRO_PACIENTE,
                "PACIENTE",
                pacientePublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new RegistroPacienteResponse(
                pacientePublicId,
                emailNormalizado,
                request.nombres().trim(),
                request.apellidos().trim(),
                "Registro completado exitosamente."
        );
    }

    /**
     * Inicia sesión validando credenciales y bloqueo temporal (5 fallos -> 15 min).
     * Devuelve tokens y sesión si es exitoso (ADR-002, HU-01).
     */
    @Transactional
    public AuthTokens login(LoginRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de login no puede ser nula");

        String emailNormalizado = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<Usuario> usuarioOpt = usuarioRepository.buscarPorEmail(emailNormalizado);

        if (usuarioOpt.isEmpty()) {
            auditoriaService.registrarEvento(
                    AccionAuditable.LOGIN_FALLIDO,
                    "USUARIO",
                    null,
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new CredencialesInvalidasException();
        }

        Usuario usuario = usuarioOpt.get();

        // Validar bloqueo temporal de cuenta
        if (usuario.estaBloqueado()) {
            auditoriaService.registrarEvento(
                    usuario.id(),
                    AccionAuditable.LOGIN_FALLIDO,
                    "USUARIO",
                    usuario.publicId(),
                    ResultadoAuditoria.BLOQUEADO,
                    ipOrigen
            );
            throw new CredencialesInvalidasException();
        }

        // Si expiró el bloqueo temporal previo, restablecer intentos
        if (usuario.bloqueadoHasta() != null && Instant.now().isAfter(usuario.bloqueadoHasta())) {
            usuarioRepository.restablecerIntentos(usuario.id());
            usuario = new Usuario(
                    usuario.id(),
                    usuario.publicId(),
                    usuario.email(),
                    usuario.passwordHash(),
                    "ACTIVO",
                    0,
                    null,
                    usuario.createdAt()
            );
        }

        // Validar contraseña
        if (!passwordEncoder.matches(request.password(), usuario.passwordHash())) {
            int nuevosIntentos = usuario.intentosFallidos() + 1;
            if (nuevosIntentos >= 5) {
                Instant bloqueadoHasta = Instant.now().plus(15, ChronoUnit.MINUTES);
                usuarioRepository.actualizarIntentosFallidos(usuario.id(), nuevosIntentos, bloqueadoHasta, "BLOQUEADO");
                auditoriaService.registrarEvento(
                        usuario.id(),
                        AccionAuditable.LOGIN_FALLIDO,
                        "USUARIO",
                        usuario.publicId(),
                        ResultadoAuditoria.BLOQUEADO,
                        ipOrigen
                );
            } else {
                usuarioRepository.actualizarIntentosFallidos(usuario.id(), nuevosIntentos, null, "ACTIVO");
                auditoriaService.registrarEvento(
                        usuario.id(),
                        AccionAuditable.LOGIN_FALLIDO,
                        "USUARIO",
                        usuario.publicId(),
                        ResultadoAuditoria.FALLO,
                        ipOrigen
                );
            }
            throw new CredencialesInvalidasException();
        }

        // Contraseña correcta: restablecer intentos si hubo fallos previos
        if (usuario.intentosFallidos() > 0 || usuario.bloqueadoHasta() != null) {
            usuarioRepository.restablecerIntentos(usuario.id());
        }

        List<String> roles = usuarioRepository.obtenerRoles(usuario.id());
        String accessToken = jwtService.generarAccessToken(usuario.publicId(), usuario.email(), roles);

        // Refresh token rotativo opaco con alta entropía
        String rawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String tokenHash = TokenHashUtil.hash(rawRefreshToken);
        Instant expiracionRefresh = Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS);
        refreshTokenRepository.crear(usuario.id(), tokenHash, expiracionRefresh);

        auditoriaService.registrarEvento(
                usuario.id(),
                AccionAuditable.LOGIN_EXITOSO,
                "USUARIO",
                usuario.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        AuthSessionResponse sessionResponse = new AuthSessionResponse(
                usuario.publicId(),
                usuario.email(),
                roles,
                "Inicio de sesion exitoso."
        );

        return new AuthTokens(accessToken, rawRefreshToken, sessionResponse);
    }

    /**
     * Rota el refresh token y emite un nuevo access JWT (ADR-002).
     * Si el token presentado ya fue revocado, activa la revocación masiva del usuario (mitigación de robo).
     */
    @Transactional
    public AuthTokens refresh(String rawRefreshToken, String ipOrigen) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new TokenInvalidoException("Token de sesion no proporcionado.");
        }

        String tokenHash = TokenHashUtil.hash(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.buscarPorTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            throw new TokenInvalidoException("Token de sesion invalido.");
        }

        RefreshToken refreshToken = tokenOpt.get();

        // Detección de reuso de token revocado (mitigación de secuestro de sesión)
        if (refreshToken.revocado()) {
            refreshTokenRepository.revocarTodosPorUsuario(refreshToken.usuarioId());
            auditoriaService.registrarEvento(
                    refreshToken.usuarioId(),
                    AccionAuditable.LOGIN_FALLIDO,
                    "REFRESH_TOKEN",
                    String.valueOf(refreshToken.id()),
                    ResultadoAuditoria.BLOQUEADO,
                    ipOrigen
            );
            throw new TokenInvalidoException("Sesion revocada por seguridad debido a deteccion de reuso.");
        }

        // Token expirado
        if (refreshToken.estaExpirado()) {
            refreshTokenRepository.revocar(refreshToken.id());
            throw new TokenInvalidoException("El token de sesion ha expirado.");
        }

        Usuario usuario = usuarioRepository.buscarPorId(refreshToken.usuarioId())
                .orElseThrow(() -> new TokenInvalidoException("Usuario no encontrado para la sesion."));

        if (usuario.estaBloqueado() || !"ACTIVO".equalsIgnoreCase(usuario.estado())) {
            refreshTokenRepository.revocar(refreshToken.id());
            throw new AccesoNoAutorizadoException();
        }

        // Rotación: revocar el token usado
        refreshTokenRepository.revocar(refreshToken.id());

        // Emitir nuevo refresh token
        String nuevoRawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String nuevoTokenHash = TokenHashUtil.hash(nuevoRawRefreshToken);
        Instant nuevaExpiracion = Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS);
        refreshTokenRepository.crear(usuario.id(), nuevoTokenHash, nuevaExpiracion);

        List<String> roles = usuarioRepository.obtenerRoles(usuario.id());
        String nuevoAccessToken = jwtService.generarAccessToken(usuario.publicId(), usuario.email(), roles);

        AuthSessionResponse sessionResponse = new AuthSessionResponse(
                usuario.publicId(),
                usuario.email(),
                roles,
                "Sesion actualizada exitosamente."
        );

        return new AuthTokens(nuevoAccessToken, nuevoRawRefreshToken, sessionResponse);
    }

    /**
     * Invalida el refresh token y registra el evento de logout (ADR-002, HU-01).
     */
    @Transactional
    public void logout(String rawRefreshToken, String ipOrigen) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = TokenHashUtil.hash(rawRefreshToken);
            Optional<RefreshToken> tokenOpt = refreshTokenRepository.buscarPorTokenHash(tokenHash);
            if (tokenOpt.isPresent()) {
                RefreshToken rt = tokenOpt.get();
                refreshTokenRepository.revocar(rt.id());
                Usuario usuario = usuarioRepository.buscarPorId(rt.usuarioId()).orElse(null);
                auditoriaService.registrarEvento(
                        rt.usuarioId(),
                        AccionAuditable.LOGOUT,
                        "USUARIO",
                        usuario != null ? usuario.publicId() : null,
                        ResultadoAuditoria.EXITO,
                        ipOrigen
                );
                return;
            }
        }

        // Logout sin token o token inexistente
        auditoriaService.registrarEvento(
                AccionAuditable.LOGOUT,
                "USUARIO",
                null,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );
    }
}
