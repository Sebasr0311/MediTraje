package com.meditriaje.service;

import com.meditriaje.dto.emergency.AccesoQrResponse;
import com.meditriaje.dto.emergency.GenerarQrRequest;
import com.meditriaje.dto.emergency.GenerarQrResponse;
import com.meditriaje.dto.emergency.VerificarQrResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccesoTemporalQr;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EstadoAccesoQr;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AccesoTemporalQrRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.security.TokenHashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio para la generación, gestión y verificación de accesos temporales QR al resumen de salud (ADR-010, §5.17, §5.18).
 */
@Service
public class EmergencyQrService {

    public static final int VIGENCIA_MINUTOS = 15;
    public static final int MAX_ACCESOS_PERMITIDOS = 3;

    private final AccesoTemporalQrRepository accesoTemporalQrRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public EmergencyQrService(
            AccesoTemporalQrRepository accesoTemporalQrRepository,
            PacienteRepository pacienteRepository,
            UsuarioRepository usuarioRepository,
            AuditoriaService auditoriaService,
            PasswordEncoder passwordEncoder
    ) {
        this(
                accesoTemporalQrRepository,
                pacienteRepository,
                usuarioRepository,
                auditoriaService,
                passwordEncoder,
                Clock.systemUTC()
        );
    }

    public EmergencyQrService(
            AccesoTemporalQrRepository accesoTemporalQrRepository,
            PacienteRepository pacienteRepository,
            UsuarioRepository usuarioRepository,
            AuditoriaService auditoriaService,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.accesoTemporalQrRepository = Objects.requireNonNull(accesoTemporalQrRepository, "accesoTemporalQrRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "pacienteRepository no puede ser nulo");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService no puede ser nulo");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Genera un nuevo token temporal criptográfico de 256 bits y registra el acceso QR para el paciente autenticado.
     */
    @Transactional
    public GenerarQrResponse generarAccesoQr(GenerarQrRequest request, String usuarioAutenticadoPublicId, String ipOrigen) {
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Solo pacientes registrados pueden generar accesos QR de emergencia."));

        // Generar token criptográfico opaco de 256 bits (32 bytes)
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        String tokenHash = TokenHashUtil.hash(token);

        String pinHash = null;
        if (request != null && request.pin() != null && !request.pin().isBlank()) {
            pinHash = passwordEncoder.encode(request.pin().trim());
        }

        Instant now = Instant.now(clock);
        Instant expiraAt = now.plus(VIGENCIA_MINUTOS, ChronoUnit.MINUTES);
        String publicId = UUID.randomUUID().toString();

        boolean incluirAlergias = request != null ? request.getIncluirAlergias() : true;
        boolean incluirMedicamentos = request != null ? request.getIncluirMedicamentos() : true;
        boolean incluirAtenciones = request != null ? request.getIncluirAtenciones() : true;
        boolean incluirContacto = request != null ? request.getIncluirContacto() : true;

        AccesoTemporalQr nuevo = new AccesoTemporalQr(
                null,
                publicId,
                paciente.id(),
                tokenHash,
                pinHash,
                incluirAlergias,
                incluirMedicamentos,
                incluirAtenciones,
                incluirContacto,
                MAX_ACCESOS_PERMITIDOS,
                0,
                false,
                expiraAt,
                now,
                now
        );

        accesoTemporalQrRepository.crear(nuevo);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.GENERACION_QR_EMERGENCIA,
                "ACCESO_TEMPORAL_QR",
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        String qrUrl = "#/emergency-summary/" + token;

        return new GenerarQrResponse(
                publicId,
                token,
                qrUrl,
                expiraAt,
                MAX_ACCESOS_PERMITIDOS,
                pinHash != null,
                incluirAlergias,
                incluirMedicamentos,
                incluirAtenciones,
                incluirContacto,
                now
        );
    }

    /**
     * Lista los accesos QR generados por el paciente autenticado con su estado temporal resuelto.
     */
    @Transactional(readOnly = true)
    public List<AccesoQrResponse> listarMisAccesosQr(String usuarioAutenticadoPublicId) {
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Solo pacientes registrados pueden consultar sus accesos QR."));

        List<AccesoTemporalQr> accesos = accesoTemporalQrRepository.listarPorPacienteId(paciente.id());
        Instant now = Instant.now(clock);

        return accesos.stream()
                .map(a -> new AccesoQrResponse(
                        a.publicId(),
                        a.resolverEstado(now).name(),
                        a.maxAccesos(),
                        a.accesosRealizados(),
                        a.revocado(),
                        a.requierePin(),
                        a.incluirAlergias(),
                        a.incluirMedicamentos(),
                        a.incluirAtenciones(),
                        a.incluirContacto(),
                        a.expiraAt(),
                        a.createdAt()
                ))
                .toList();
    }

    /**
     * Revoca inmediatamente un acceso temporal QR a solicitud de su titular.
     */
    @Transactional
    public void revocarAccesoQr(String publicId, String usuarioAutenticadoPublicId, String ipOrigen) {
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Solo pacientes registrados pueden revocar accesos QR."));

        AccesoTemporalQr acceso = accesoTemporalQrRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Acceso temporal QR no encontrado."));

        if (!acceso.pacienteId().equals(paciente.id())) {
            throw new AccesoNoAutorizadoException("No tiene autorizacion para revocar un acceso QR ajeno.");
        }

        accesoTemporalQrRepository.revocar(publicId, paciente.id());

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.REVOCACION_QR_EMERGENCIA,
                "ACCESO_TEMPORAL_QR",
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));
    }

    /**
     * Verifica la validez pública de un token QR escaneado (indica vigencia y si requiere PIN).
     */
    @Transactional(readOnly = true)
    public VerificarQrResponse verificarTokenPublico(String token) {
        if (token == null || token.isBlank()) {
            return new VerificarQrResponse(false, false, EstadoAccesoQr.EXPIRADO.name(), null);
        }

        String tokenHash = TokenHashUtil.hash(token.trim());
        Optional<AccesoTemporalQr> accesoOpt = accesoTemporalQrRepository.buscarPorTokenHash(tokenHash);

        if (accesoOpt.isEmpty()) {
            return new VerificarQrResponse(false, false, EstadoAccesoQr.EXPIRADO.name(), null);
        }

        AccesoTemporalQr acceso = accesoOpt.get();
        Instant now = Instant.now(clock);
        EstadoAccesoQr estado = acceso.resolverEstado(now);
        boolean valido = (estado == EstadoAccesoQr.ACTIVO);

        return new VerificarQrResponse(
                valido,
                acceso.requierePin(),
                estado.name(),
                acceso.expiraAt()
        );
    }
}
