package com.meditriaje.service;

import com.meditriaje.dto.allergy.AlergiaResponse;
import com.meditriaje.dto.allergy.InactivarAlergiaRequest;
import com.meditriaje.dto.allergy.RegistrarAlergiaRequest;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Alergia;
import com.meditriaje.model.Atencion;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AlergiaRepository;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de negocio para la gestión e inmutabilidad de alergias e hipersensibilidades (D3, T4, ADR-007, ADR-008).
 * Garantiza autorización estricta por capas: relación asistencial ordinaria para profesionales (break-glass solo lectura),
 * gestión de alergias autorreportadas para pacientes, y denegación absoluta para administradores.
 */
@Service
public class AllergyService {

    private final AlergiaRepository alergiaRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProfesionalRepository profesionalRepository;
    private final AtencionRepository atencionRepository;
    private final AccesoClinicoService accesoClinicoService;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    @Autowired
    public AllergyService(
            AlergiaRepository alergiaRepository,
            PacienteRepository pacienteRepository,
            UsuarioRepository usuarioRepository,
            ProfesionalRepository profesionalRepository,
            AtencionRepository atencionRepository,
            AccesoClinicoService accesoClinicoService,
            AuditoriaService auditoriaService
    ) {
        this(
                alergiaRepository,
                pacienteRepository,
                usuarioRepository,
                profesionalRepository,
                atencionRepository,
                accesoClinicoService,
                auditoriaService,
                Clock.systemUTC()
        );
    }

    public AllergyService(
            AlergiaRepository alergiaRepository,
            PacienteRepository pacienteRepository,
            UsuarioRepository usuarioRepository,
            ProfesionalRepository profesionalRepository,
            AtencionRepository atencionRepository,
            AccesoClinicoService accesoClinicoService,
            AuditoriaService auditoriaService,
            Clock clock
    ) {
        this.alergiaRepository = Objects.requireNonNull(alergiaRepository, "alergiaRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "pacienteRepository no puede ser nulo");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository no puede ser nulo");
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository, "profesionalRepository no puede ser nulo");
        this.atencionRepository = Objects.requireNonNull(atencionRepository, "atencionRepository no puede ser nulo");
        this.accesoClinicoService = Objects.requireNonNull(accesoClinicoService, "accesoClinicoService no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    // =========================================================================
    // SECCIÓN PROFESIONAL ASISTENCIAL
    // =========================================================================

    /**
     * Consulta las alergias de un paciente por parte de un profesional asistencial (ADR-007, T4).
     * Requiere relación asistencial activa u autorización Break-Glass válida.
     */
    @Transactional(readOnly = true)
    public List<AlergiaResponse> listarAlergiasPacienteParaProfesional(
            String pacientePublicId,
            boolean incluirInactivas,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        Usuario usuario = obtenerUsuarioPorPublicId(usuarioAutenticadoPublicId);
        accesoClinicoService.validarAccesoHistorialClinico(usuarioAutenticadoPublicId, pacientePublicId, authorities);

        Paciente paciente = pacienteRepository.buscarPorPublicId(pacientePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado."));

        List<AlergiaResponse> lista = alergiaRepository.listarPorPacientePublicId(paciente.publicId(), incluirInactivas);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.ALERGIA_CONSULTADA,
                "PACIENTE",
                paciente.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return lista;
    }

    /**
     * Registra una alergia clínica durante o fuera de una atención por parte de un profesional asistencial (D3, T4).
     * Requiere relación asistencial ordinaria vigente. El acceso Break-Glass es estrictamente de solo lectura.
     */
    @Transactional
    public AlergiaResponse registrarAlergiaPorProfesional(
            String pacientePublicId,
            RegistrarAlergiaRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        Usuario usuario = obtenerUsuarioPorPublicId(usuarioAutenticadoPublicId);
        Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));

        Paciente paciente = pacienteRepository.buscarPorPublicId(pacientePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado."));

        // Autorización de escritura clínica (Break-Glass es SOLO LECTURA)
        accesoClinicoService.validarEscrituraClinica(profesional.id(), paciente.id());

        Long atencionId = null;
        if (request.atencionPublicId() != null && !request.atencionPublicId().isBlank()) {
            Atencion atencion = atencionRepository.buscarEntidadPorPublicId(request.atencionPublicId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Atencion medica no encontrada."));
            if (!atencion.pacienteId().equals(paciente.id())) {
                throw new DatosInvalidosException("La atencion medica no corresponde al paciente indicado.");
            }
            atencionId = atencion.id();
        }

        // Unicidad de sustancia activa
        if (alergiaRepository.existeActivaPorSustancia(paciente.id(), request.sustancia())) {
            throw new DatosInvalidosException("El paciente ya cuenta con una alergia activa registrada para la sustancia: " + request.sustancia().trim());
        }

        String publicId = UUID.randomUUID().toString();
        Instant ahora = Instant.now(clock);

        Alergia nueva = new Alergia(
                null,
                publicId,
                paciente.id(),
                request.sustancia().trim(),
                request.reaccion(),
                request.severidad(),
                "ACTIVA",
                "PROFESIONAL",
                usuario.id(),
                atencionId,
                null,
                null,
                null,
                ahora,
                ahora
        );

        alergiaRepository.crear(nueva);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.ALERGIA_REGISTRADA,
                "ALERGIA",
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return alergiaRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la alergia recién registrada."));
    }

    /**
     * Inactiva una alergia por parte de un profesional asistencial con justificación clínica obligatoria (D3, T4).
     * Requiere relación asistencial ordinaria. Break-Glass es solo lectura.
     */
    @Transactional
    public AlergiaResponse inactivarAlergiaPorProfesional(
            String alergiaPublicId,
            InactivarAlergiaRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        Usuario usuario = obtenerUsuarioPorPublicId(usuarioAutenticadoPublicId);
        Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));

        Alergia alergia = alergiaRepository.buscarEntidadPorPublicId(alergiaPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Alergia no encontrada."));

        // Autorización de escritura clínica
        accesoClinicoService.validarEscrituraClinica(profesional.id(), alergia.pacienteId());

        if (!"ACTIVA".equalsIgnoreCase(alergia.estado())) {
            throw new DatosInvalidosException("La alergia ya se encuentra inactiva.");
        }

        alergiaRepository.inactivar(alergia.id(), usuario.id(), request.motivo(), Instant.now(clock));

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.ALERGIA_INACTIVADA,
                "ALERGIA",
                alergiaPublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return alergiaRepository.buscarPorPublicId(alergiaPublicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la alergia recién inactivada."));
    }

    // =========================================================================
    // SECCIÓN PACIENTE (AUTORREPORTADAS)
    // =========================================================================

    /**
     * Consulta las alergias del propio paciente autenticado (D3, T4).
     */
    @Transactional(readOnly = true)
    public List<AlergiaResponse> listarMisAlergias(String usuarioAutenticadoPublicId, String ipOrigen) {
        Usuario usuario = obtenerUsuarioPorPublicId(usuarioAutenticadoPublicId);
        Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente."));

        List<AlergiaResponse> lista = alergiaRepository.listarPorPacientePublicId(paciente.publicId(), true);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.ALERGIA_CONSULTADA,
                "PACIENTE",
                paciente.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return lista;
    }

    /**
     * Registra una alergia autorreportada por el propio paciente (D3, T4).
     */
    @Transactional
    public AlergiaResponse registrarMiAlergia(
            RegistrarAlergiaRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        Usuario usuario = obtenerUsuarioPorPublicId(usuarioAutenticadoPublicId);
        Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente."));

        if (alergiaRepository.existeActivaPorSustancia(paciente.id(), request.sustancia())) {
            throw new DatosInvalidosException("Ya cuenta con una alergia activa registrada para la sustancia: " + request.sustancia().trim());
        }

        String publicId = UUID.randomUUID().toString();
        Instant ahora = Instant.now(clock);

        Alergia nueva = new Alergia(
                null,
                publicId,
                paciente.id(),
                request.sustancia().trim(),
                request.reaccion(),
                request.severidad(),
                "ACTIVA",
                "PACIENTE",
                usuario.id(),
                null,
                null,
                null,
                null,
                ahora,
                ahora
        );

        alergiaRepository.crear(nueva);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.ALERGIA_REGISTRADA,
                "ALERGIA",
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return alergiaRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la alergia autorreportada recién registrada."));
    }

    /**
     * Inactiva una alergia autorreportada por el propio paciente (D3, T4).
     * El paciente SOLO puede inactivar alergias registradas con origen 'PACIENTE'.
     */
    @Transactional
    public AlergiaResponse inactivarMiAlergia(
            String alergiaPublicId,
            InactivarAlergiaRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        Usuario usuario = obtenerUsuarioPorPublicId(usuarioAutenticadoPublicId);
        Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente."));

        Alergia alergia = alergiaRepository.buscarEntidadPorPublicId(alergiaPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Alergia no encontrada."));

        if (!alergia.pacienteId().equals(paciente.id())) {
            throw new AccesoNoAutorizadoException("No tiene autorizacion para inactivar una alergia de otro paciente.");
        }

        if (!"PACIENTE".equalsIgnoreCase(alergia.origen())) {
            throw new AccesoNoAutorizadoException("El paciente solo puede inactivar alergias autorreportadas por si mismo. Las alergias registradas por profesionales deben ser modificadas por el personal de salud.");
        }

        if (!"ACTIVA".equalsIgnoreCase(alergia.estado())) {
            throw new DatosInvalidosException("La alergia ya se encuentra inactiva.");
        }

        alergiaRepository.inactivar(alergia.id(), usuario.id(), request.motivo(), Instant.now(clock));

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.ALERGIA_INACTIVADA,
                "ALERGIA",
                alergiaPublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return alergiaRepository.buscarPorPublicId(alergiaPublicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la alergia recién inactivada."));
    }

    // =========================================================================
    // UTILIDADES PRIVADAS
    // =========================================================================

    private Usuario obtenerUsuarioPorPublicId(String usuarioPublicId) {
        if (usuarioPublicId == null || usuarioPublicId.isBlank()) {
            throw new AccesoNoAutorizadoException("Usuario no autenticado.");
        }
        return usuarioRepository.buscarPorPublicId(usuarioPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));
    }
}
