package com.meditriaje.service;

import com.meditriaje.dto.clinical.AccesoBreakGlassResponse;
import com.meditriaje.dto.clinical.ActivarBreakGlassRequest;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccesoBreakGlass;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.BreakGlassRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio para gestión y activación de accesos clínicos de emergencia Break-Glass (ADR-007, ADR-017).
 * Exclusivo para profesionales asistenciales (ROLE_PROFESIONAL) ante situaciones clínicas de urgencia vital.
 */
@Service
public class BreakGlassService {

    public static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    public static final int VIGENCIA_HORAS = 24;

    private final UsuarioRepository usuarioRepository;
    private final ProfesionalRepository profesionalRepository;
    private final PacienteRepository pacienteRepository;
    private final BreakGlassRepository breakGlassRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    @Autowired
    public BreakGlassService(
            UsuarioRepository usuarioRepository,
            ProfesionalRepository profesionalRepository,
            PacienteRepository pacienteRepository,
            BreakGlassRepository breakGlassRepository,
            AuditoriaService auditoriaService
    ) {
        this(
                usuarioRepository,
                profesionalRepository,
                pacienteRepository,
                breakGlassRepository,
                auditoriaService,
                Clock.system(ZONE_BOGOTA)
        );
    }

    public BreakGlassService(
            UsuarioRepository usuarioRepository,
            ProfesionalRepository profesionalRepository,
            PacienteRepository pacienteRepository,
            BreakGlassRepository breakGlassRepository,
            AuditoriaService auditoriaService,
            Clock clock
    ) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository no puede ser nulo");
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository, "profesionalRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "pacienteRepository no puede ser nulo");
        this.breakGlassRepository = Objects.requireNonNull(breakGlassRepository, "breakGlassRepository no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Activa una autorización de acceso clínico excepcional de emergencia Break-Glass (ADR-017).
     *
     * @param request                    Petición con pacientePublicId y motivo clínico de urgencia
     * @param usuarioAutenticadoPublicId Identificador del usuario que invoca la acción (debe ser ROLE_PROFESIONAL)
     * @param ipOrigen                   Dirección IP del cliente para la bitácora inmutable
     * @return DTO con la información de la autorización creada y su vigencia
     */
    @Transactional
    public AccesoBreakGlassResponse activarBreakGlass(
            ActivarBreakGlassRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        if (usuarioAutenticadoPublicId == null || usuarioAutenticadoPublicId.isBlank()) {
            throw new AccesoNoAutorizadoException("Usuario no autenticado.");
        }

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));

        Paciente paciente = pacienteRepository.buscarPorPublicId(request.pacientePublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado."));

        if (request.motivo() == null || request.motivo().trim().length() < 20) {
            throw new DatosInvalidosException("El motivo de justificacion de emergencia debe contener al menos 20 caracteres.");
        }

        Instant ahora = clock.instant();
        Instant expiracion = ahora.plus(VIGENCIA_HORAS, ChronoUnit.HOURS);
        String publicId = UUID.randomUUID().toString();

        AccesoBreakGlass acceso = new AccesoBreakGlass(
                null,
                publicId,
                profesional.id(),
                paciente.id(),
                request.motivo().trim(),
                expiracion,
                ahora
        );

        breakGlassRepository.registrarAcceso(acceso);

        // Auditoría reforzada e inmutable (ADR-011, ADR-017)
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.ACCESO_BREAK_GLASS,
                "PACIENTE",
                paciente.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return breakGlassRepository.buscarPorPublicId(publicId, ahora)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar el acceso break-glass recién registrado."));
    }

    /**
     * Lista los accesos Break-Glass activos (no expirados) del profesional autenticado.
     */
    public List<AccesoBreakGlassResponse> listarMisAccesosActivos(String usuarioAutenticadoPublicId) {
        if (usuarioAutenticadoPublicId == null || usuarioAutenticadoPublicId.isBlank()) {
            throw new AccesoNoAutorizadoException("Usuario no autenticado.");
        }

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));

        return breakGlassRepository.listarActivosPorProfesional(profesional.id(), clock.instant());
    }

    /**
     * Consulta el detalle de un acceso Break-Glass por su ID público.
     */
    public AccesoBreakGlassResponse obtenerPorPublicId(String publicId, String usuarioAutenticadoPublicId) {
        if (usuarioAutenticadoPublicId == null || usuarioAutenticadoPublicId.isBlank()) {
            throw new AccesoNoAutorizadoException("Usuario no autenticado.");
        }

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));

        Instant ahora = clock.instant();
        AccesoBreakGlassResponse response = breakGlassRepository.buscarPorPublicId(publicId, ahora)
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro de acceso Break-Glass no encontrado."));

        if (!response.profesionalPublicId().equals(profesional.publicId())) {
            throw new AccesoNoAutorizadoException("No tiene autorizacion para consultar el acceso break-glass de otro profesional.");
        }

        return response;
    }
}
