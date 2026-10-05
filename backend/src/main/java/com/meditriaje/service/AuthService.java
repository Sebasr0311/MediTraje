package com.meditriaje.service;

import com.meditriaje.dto.AuthSessionResponse;
import com.meditriaje.dto.AuthTokens;
import com.meditriaje.dto.CambiarPasswordRequest;
import com.meditriaje.dto.LoginRequest;
import com.meditriaje.dto.RegistroPacienteRequest;
import com.meditriaje.dto.RegistroPacienteResponse;
import com.meditriaje.dto.auth.MfaAuthenticateRequest;
import com.meditriaje.dto.auth.MfaSetupResponse;
import com.meditriaje.dto.auth.MfaVerifyRequest;
import com.meditriaje.dto.auth.MfaVerifyResponse;
import com.meditriaje.dto.auth.RestablecerPasswordRequest;
import com.meditriaje.dto.auth.RestablecerPasswordResponse;
import com.meditriaje.dto.auth.SolicitarRecuperacionRequest;
import com.meditriaje.dto.auth.SolicitarRecuperacionResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.CredencialesInvalidasException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.TokenInvalidoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.CodigoVerificacion;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
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
import com.meditriaje.security.JwtService;
import com.meditriaje.security.TokenHashUtil;
import com.meditriaje.security.TotpService;
import com.meditriaje.service.email.EmailService;
import com.meditriaje.util.NormaColombianaValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de autenticación, registro de usuarios y ciclo de vida de sesiones (HU-01, ADR-002, ADR-013, ADR-014).
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfesionalRepository profesionalRepository;
    private final ConsentimientoRepository consentimientoRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CodigoVerificacionRepository codigoVerificacionRepository;
    private final MfaBackupCodeRepository mfaBackupCodeRepository;
    private final AuditoriaService auditoriaService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final EmailService emailService;
    private final long refreshExpirationDays;

    @Autowired
    public AuthService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ProfesionalRepository profesionalRepository,
            ConsentimientoRepository consentimientoRepository,
            RefreshTokenRepository refreshTokenRepository,
            CodigoVerificacionRepository codigoVerificacionRepository,
            MfaBackupCodeRepository mfaBackupCodeRepository,
            AuditoriaService auditoriaService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TotpService totpService,
            EmailService emailService,
            @Value("${security.jwt.refresh-expiration-days:7}") long refreshExpirationDays
    ) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "UsuarioRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "PacienteRepository no puede ser nulo");
        this.profesionalRepository = profesionalRepository;
        this.consentimientoRepository = Objects.requireNonNull(consentimientoRepository, "ConsentimientoRepository no puede ser nulo");
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "RefreshTokenRepository no puede ser nulo");
        this.codigoVerificacionRepository = codigoVerificacionRepository;
        this.mfaBackupCodeRepository = mfaBackupCodeRepository;
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "AuditoriaService no puede ser nulo");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder no puede ser nulo");
        this.jwtService = Objects.requireNonNull(jwtService, "JwtService no puede ser nulo");
        this.totpService = totpService;
        this.emailService = emailService;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    public AuthService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ProfesionalRepository profesionalRepository,
            ConsentimientoRepository consentimientoRepository,
            RefreshTokenRepository refreshTokenRepository,
            CodigoVerificacionRepository codigoVerificacionRepository,
            AuditoriaService auditoriaService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService,
            long refreshExpirationDays
    ) {
        this(
                usuarioRepository,
                pacienteRepository,
                profesionalRepository,
                consentimientoRepository,
                refreshTokenRepository,
                codigoVerificacionRepository,
                null,
                auditoriaService,
                passwordEncoder,
                jwtService,
                null,
                emailService,
                refreshExpirationDays
        );
    }

    public AuthService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ConsentimientoRepository consentimientoRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuditoriaService auditoriaService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            long refreshExpirationDays
    ) {
        this(
                usuarioRepository,
                pacienteRepository,
                null,
                consentimientoRepository,
                refreshTokenRepository,
                null,
                null,
                auditoriaService,
                passwordEncoder,
                jwtService,
                null,
                null,
                refreshExpirationDays
        );
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

        // 2. Validar reglas de identificación y datos según norma colombiana
        try {
            NormaColombianaValidator.validarDocumento(request.tipoDocumento(), request.numeroDocumento());
            NormaColombianaValidator.validarCoherenciaDocumentoEdad(request.tipoDocumento(), request.fechaNacimiento(), null);
            NormaColombianaValidator.validarNombresOApellidos("nombres", request.nombres());
            NormaColombianaValidator.validarNombresOApellidos("apellidos", request.apellidos());
            NormaColombianaValidator.validarCelularColombia(request.telefono());
        } catch (DatosInvalidosException e) {
            auditoriaService.registrarEvento(
                    AccionAuditable.REGISTRO_PACIENTE,
                    "USUARIO",
                    null,
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw e;
        }

        // 3. Normalizar correo y verificar duplicados
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

        // Si el usuario tiene MFA habilitado, requerir segundo factor (ADR-014, F2.1.4)
        if (usuario.mfaHabilitado()) {
            String challengeToken = jwtService.generarMfaChallengeToken(usuario.publicId(), usuario.email(), roles);
            AuthSessionResponse challengeResponse = AuthSessionResponse.mfaRequerido(
                    usuario.publicId(),
                    usuario.email(),
                    roles,
                    challengeToken
            );
            return new AuthTokens(null, null, challengeResponse);
        }

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
                "Inicio de sesion exitoso.",
                usuario.debeCambiarPassword()
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
                "Sesion actualizada exitosamente.",
                usuario.debeCambiarPassword()
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

    /**
     * Cambia la contraseña del usuario autenticado (ADR-002, M3.3).
     * Valida la contraseña actual, que la nueva no sea idéntica y actualiza restableciendo debeCambiarPassword = false.
     */
    @Transactional
    public void cambiarPassword(String usuarioPublicId, CambiarPasswordRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de cambio de contrasena no puede ser nula");

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioPublicId)
                .orElseThrow(() -> new CredencialesInvalidasException("Credenciales invalidas."));

        if (!passwordEncoder.matches(request.passwordActual(), usuario.passwordHash())) {
            auditoriaService.registrarEvento(
                    usuario.id(),
                    AccionAuditable.CAMBIO_PASSWORD,
                    "USUARIO",
                    usuario.publicId(),
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new CredencialesInvalidasException("Credenciales invalidas.");
        }

        if (passwordEncoder.matches(request.passwordNuevo(), usuario.passwordHash())) {
            auditoriaService.registrarEvento(
                    usuario.id(),
                    AccionAuditable.CAMBIO_PASSWORD,
                    "USUARIO",
                    usuario.publicId(),
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new DatosInvalidosException("La nueva contrasena no puede ser igual a la anterior.");
        }

        String nuevoHash = passwordEncoder.encode(request.passwordNuevo());
        usuarioRepository.actualizarPassword(usuario.id(), nuevoHash, false);

        auditoriaService.registrarEvento(
                usuario.id(),
                AccionAuditable.CAMBIO_PASSWORD,
                "USUARIO",
                usuario.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );
    }

    /**
     * Procesa la solicitud de recuperación de contraseña enviando un código OTP de 6 dígitos por correo (ADR-014, F2.1.3).
     * Siempre retorna una respuesta idéntica informativa para mitigar ataques de enumeración de usuarios.
     */
    @Transactional
    public SolicitarRecuperacionResponse solicitarRecuperacionPassword(SolicitarRecuperacionRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de recuperacion no puede ser nula");
        String emailNormalizado = request.email().trim().toLowerCase(Locale.ROOT);

        Optional<Usuario> usuarioOpt = usuarioRepository.buscarPorEmail(emailNormalizado);
        if (usuarioOpt.isEmpty()) {
            auditoriaService.registrarEvento(
                    AccionAuditable.SOLICITUD_RECUPERACION_PASSWORD,
                    "USUARIO",
                    null,
                    ResultadoAuditoria.EXITO,
                    ipOrigen
            );
            return SolicitarRecuperacionResponse.defaultResponse();
        }

        Usuario usuario = usuarioOpt.get();

        // Si la cuenta está expresamente inactiva, no se emite código
        if (!"ACTIVO".equalsIgnoreCase(usuario.estado()) && !"BLOQUEADO".equalsIgnoreCase(usuario.estado())) {
            return SolicitarRecuperacionResponse.defaultResponse();
        }

        if (codigoVerificacionRepository != null) {
            // Invalidar códigos de recuperación pendientes anteriores
            codigoVerificacionRepository.invalidarCodigosPrevios(usuario.id(), CodigoVerificacion.TIPO_RECUPERACION_PASSWORD);

            // Generar código numérico de 6 dígitos con SecureRandom (100000 - 999999)
            SecureRandom random = new SecureRandom();
            int codigoNum = 100000 + random.nextInt(900000);
            String codigo = String.valueOf(codigoNum);

            // Hash criptográfico SHA-256
            String codigoHash = TokenHashUtil.hash(codigo);
            Instant expiracion = Instant.now().plus(15, ChronoUnit.MINUTES);

            CodigoVerificacion nuevoCodigo = new CodigoVerificacion(
                    null,
                    UUID.randomUUID().toString(),
                    usuario.id(),
                    CodigoVerificacion.TIPO_RECUPERACION_PASSWORD,
                    codigoHash,
                    expiracion,
                    0,
                    3,
                    false,
                    null
            );
            codigoVerificacionRepository.crear(nuevoCodigo);

            String destinatarioNombre = resolverNombreDestinatario(usuario);

            if (emailService != null) {
                emailService.enviarCodigoRecuperacion(usuario.email(), destinatarioNombre, codigo, 15);
            }
        }

        auditoriaService.registrarEvento(
                usuario.id(),
                AccionAuditable.SOLICITUD_RECUPERACION_PASSWORD,
                "USUARIO",
                usuario.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return SolicitarRecuperacionResponse.defaultResponse();
    }

    /**
     * Restablece la contraseña del usuario validando el código OTP de 6 dígitos (ADR-014, F2.1.3).
     * Tras restablecer con éxito, revoca todas las sesiones previas (refresh tokens) del usuario.
     */
    @Transactional
    public RestablecerPasswordResponse restablecerPassword(RestablecerPasswordRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de restablecimiento no puede ser nula");
        String emailNormalizado = request.email().trim().toLowerCase(Locale.ROOT);

        Usuario usuario = usuarioRepository.buscarPorEmail(emailNormalizado)
                .orElseThrow(() -> new CredencialesInvalidasException("Codigo de verificacion invalido o expirado."));

        if (!"ACTIVO".equalsIgnoreCase(usuario.estado()) && !"BLOQUEADO".equalsIgnoreCase(usuario.estado())) {
            throw new AccesoNoAutorizadoException("La cuenta de usuario se encuentra inactiva.");
        }

        if (codigoVerificacionRepository == null) {
            throw new IllegalStateException("CodigoVerificacionRepository no esta disponible.");
        }

        Optional<CodigoVerificacion> codigoOpt = codigoVerificacionRepository
                .buscarUltimoPendientePorUsuarioYTipo(usuario.id(), CodigoVerificacion.TIPO_RECUPERACION_PASSWORD);

        if (codigoOpt.isEmpty()) {
            auditoriaService.registrarEvento(
                    usuario.id(),
                    AccionAuditable.RECUPERACION_PASSWORD_FALLO,
                    "USUARIO",
                    usuario.publicId(),
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new CredencialesInvalidasException("Codigo de verificacion invalido o expirado.");
        }

        CodigoVerificacion codigoVerif = codigoOpt.get();
        Instant ahora = Instant.now();

        if (codigoVerif.estaExpirado(ahora)) {
            codigoVerificacionRepository.marcarComoUsado(codigoVerif.id());
            auditoriaService.registrarEvento(
                    usuario.id(),
                    AccionAuditable.RECUPERACION_PASSWORD_FALLO,
                    "USUARIO",
                    usuario.publicId(),
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new CredencialesInvalidasException("Codigo de verificacion expirado.");
        }

        if (codigoVerif.alcanzoMaxIntentos()) {
            codigoVerificacionRepository.marcarComoUsado(codigoVerif.id());
            auditoriaService.registrarEvento(
                    usuario.id(),
                    AccionAuditable.RECUPERACION_PASSWORD_FALLO,
                    "USUARIO",
                    usuario.publicId(),
                    ResultadoAuditoria.BLOQUEADO,
                    ipOrigen
            );
            throw new CredencialesInvalidasException("El codigo ha superado el numero maximo de intentos permitidos.");
        }

        String inputHash = TokenHashUtil.hash(request.codigo());
        boolean coincide = MessageDigest.isEqual(
                inputHash.getBytes(StandardCharsets.UTF_8),
                codigoVerif.codigoHash().getBytes(StandardCharsets.UTF_8)
        );

        if (!coincide) {
            codigoVerificacionRepository.incrementarIntentos(codigoVerif.id());
            int nuevosIntentos = codigoVerif.intentosFallidos() + 1;
            if (nuevosIntentos >= codigoVerif.maxIntentos()) {
                codigoVerificacionRepository.marcarComoUsado(codigoVerif.id());
            }

            auditoriaService.registrarEvento(
                    usuario.id(),
                    AccionAuditable.RECUPERACION_PASSWORD_FALLO,
                    "USUARIO",
                    usuario.publicId(),
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new CredencialesInvalidasException("Codigo de verificacion incorrecto.");
        }

        if (passwordEncoder.matches(request.passwordNuevo(), usuario.passwordHash())) {
            auditoriaService.registrarEvento(
                    usuario.id(),
                    AccionAuditable.RECUPERACION_PASSWORD_FALLO,
                    "USUARIO",
                    usuario.publicId(),
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new DatosInvalidosException("La nueva contrasena no puede ser igual a la anterior.");
        }

        String nuevoHash = passwordEncoder.encode(request.passwordNuevo());
        usuarioRepository.actualizarPassword(usuario.id(), nuevoHash, false);
        usuarioRepository.restablecerIntentos(usuario.id());

        codigoVerificacionRepository.marcarComoUsado(codigoVerif.id());
        refreshTokenRepository.revocarTodosPorUsuario(usuario.id());

        auditoriaService.registrarEvento(
                usuario.id(),
                AccionAuditable.RECUPERACION_PASSWORD_EXITO,
                "USUARIO",
                usuario.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return RestablecerPasswordResponse.defaultResponse();
    }

    private String resolverNombreDestinatario(Usuario usuario) {
        if (pacienteRepository != null) {
            var pacienteOpt = pacienteRepository.buscarPorUsuarioId(usuario.id());
            if (pacienteOpt.isPresent()) {
                return pacienteOpt.get().nombres();
            }
        }
        if (profesionalRepository != null) {
            var profesionalOpt = profesionalRepository.buscarPorUsuarioId(usuario.id());
            if (profesionalOpt.isPresent()) {
                return profesionalOpt.get().nombres();
            }
        }
        return usuario.email();
    }

    /**
     * Inicia el enrolamiento MFA generando un nuevo secreto Base32 y la URI otpauth (ADR-014, F2.1.4).
     */
    @Transactional
    public MfaSetupResponse setupMfa(String usuarioPublicId, String ipOrigen) {
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioPublicId)
                .orElseThrow(() -> new CredencialesInvalidasException("Usuario no encontrado."));

        if (!"ACTIVO".equalsIgnoreCase(usuario.estado())) {
            throw new AccesoNoAutorizadoException("La cuenta de usuario no se encuentra activa.");
        }

        if (totpService == null) {
            throw new IllegalStateException("TotpService no se encuentra configurado.");
        }

        String secret = totpService.generarNuevoSecreto();
        usuarioRepository.guardarMfaSecret(usuario.id(), secret);

        String qrUri = totpService.generarOtpAuthUri("MediTriaje", usuario.email(), secret);

        auditoriaService.registrarEvento(
                usuario.id(),
                AccionAuditable.MFA_SETUP,
                "USUARIO",
                usuario.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new MfaSetupResponse(secret, qrUri, "MediTriaje", usuario.email());
    }

    /**
     * Valida el primer código TOTP para activar definitivamente MFA y genera 8 códigos de respaldo (ADR-014, F2.1.4).
     */
    @Transactional
    public MfaVerifyResponse verifyMfa(String usuarioPublicId, MfaVerifyRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de verificacion MFA no puede ser nula");
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioPublicId)
                .orElseThrow(() -> new CredencialesInvalidasException("Usuario no encontrado."));

        if (!"ACTIVO".equalsIgnoreCase(usuario.estado())) {
            throw new AccesoNoAutorizadoException("La cuenta de usuario no se encuentra activa.");
        }

        if (usuario.mfaSecret() == null || usuario.mfaSecret().isBlank()) {
            throw new DatosInvalidosException("No hay una configuracion de MFA en proceso para este usuario.");
        }

        if (totpService == null) {
            throw new IllegalStateException("TotpService no se encuentra configurado.");
        }

        boolean valido = totpService.validarCodigo(usuario.mfaSecret(), request.codigo(), Instant.now());
        if (!valido) {
            auditoriaService.registrarEvento(
                    usuario.id(),
                    AccionAuditable.MFA_VERIFY,
                    "USUARIO",
                    usuario.publicId(),
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new CredencialesInvalidasException("Codigo de verificacion TOTP incorrecto o expirado.");
        }

        // Activar MFA en USUARIO
        usuarioRepository.activarMfa(usuario.id(), usuario.mfaSecret());

        // Generar 8 códigos de respaldo uniuso (formato: 4 dígitos - 4 dígitos)
        SecureRandom random = new SecureRandom();
        List<String> codigosPlano = new ArrayList<>(8);
        List<String> codigosHash = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            int p1 = 1000 + random.nextInt(9000);
            int p2 = 1000 + random.nextInt(9000);
            String rawCode = p1 + "-" + p2;
            codigosPlano.add(rawCode);
            codigosHash.add(TokenHashUtil.hash(rawCode));
        }

        if (mfaBackupCodeRepository != null) {
            mfaBackupCodeRepository.eliminarPorUsuario(usuario.id());
            mfaBackupCodeRepository.guardarLote(usuario.id(), codigosHash);
        }

        auditoriaService.registrarEvento(
                usuario.id(),
                AccionAuditable.MFA_VERIFY,
                "USUARIO",
                usuario.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new MfaVerifyResponse(
                true,
                codigosPlano,
                "Autenticacion de dos factores habilitada exitosamente. Guarda tus codigos de respaldo en un lugar seguro."
        );
    }

    /**
     * Valida el segundo factor de autenticación (TOTP o código de respaldo) y emite los tokens definitivos (ADR-014, F2.1.4).
     */
    @Transactional
    public AuthTokens autenticarMfa(MfaAuthenticateRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de autenticacion MFA no puede ser nula");

        if (!jwtService.esMfaChallengeValido(request.challengeToken())) {
            throw new TokenInvalidoException("El desafio de autenticacion MFA ha expirado o es invalido.");
        }

        String publicId = jwtService.extraerPublicId(request.challengeToken());
        Usuario usuario = usuarioRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new TokenInvalidoException("Usuario no encontrado para el desafio MFA."));

        if (!"ACTIVO".equalsIgnoreCase(usuario.estado()) || usuario.estaBloqueado()) {
            throw new AccesoNoAutorizadoException("La cuenta de usuario no se encuentra activa o esta bloqueada.");
        }

        if (!usuario.mfaHabilitado() || usuario.mfaSecret() == null) {
            throw new DatosInvalidosException("El usuario no tiene la autenticacion multifactor habilitada.");
        }

        String codigo = request.codigo().trim();
        boolean autenticado = false;

        // 1. Intentar validar como código numérico TOTP de 6 dígitos
        if (codigo.matches("^[0-9]{6}$") && totpService != null) {
            autenticado = totpService.validarCodigo(usuario.mfaSecret(), codigo, Instant.now());
        }

        // 2. Si no es TOTP válido, intentar validar como código de respaldo uniuso
        if (!autenticado && mfaBackupCodeRepository != null) {
            String codeHash = TokenHashUtil.hash(codigo);
            autenticado = mfaBackupCodeRepository.consumirCodigo(usuario.id(), codeHash);
        }

        if (!autenticado) {
            auditoriaService.registrarEvento(
                    usuario.id(),
                    AccionAuditable.MFA_LOGIN_FALLIDO,
                    "USUARIO",
                    usuario.publicId(),
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new CredencialesInvalidasException("Codigo de verificacion TOTP o codigo de respaldo invalido.");
        }

        // Éxito: emitir tokens de sesión definitivos
        List<String> roles = usuarioRepository.obtenerRoles(usuario.id());
        String rawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String tokenHash = TokenHashUtil.hash(rawRefreshToken);
        Instant expiracionRefresh = Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS);
        refreshTokenRepository.crear(usuario.id(), tokenHash, expiracionRefresh);

        String accessToken = jwtService.generarAccessToken(usuario.publicId(), usuario.email(), roles);

        AuthSessionResponse sessionResponse = new AuthSessionResponse(
                usuario.publicId(),
                usuario.email(),
                roles,
                "Inicio de sesion completado exitosamente con MFA.",
                usuario.debeCambiarPassword()
        );

        auditoriaService.registrarEvento(
                usuario.id(),
                AccionAuditable.MFA_LOGIN_EXITOSO,
                "USUARIO",
                usuario.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );
        auditoriaService.registrarEvento(
                usuario.id(),
                AccionAuditable.LOGIN_EXITOSO,
                "USUARIO",
                usuario.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new AuthTokens(accessToken, rawRefreshToken, sessionResponse);
    }
}
