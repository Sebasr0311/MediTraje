package com.meditriaje.service;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.followup.CrearSeguimientoRequest;
import com.meditriaje.dto.followup.ReportarEvolucionRequest;
import com.meditriaje.dto.followup.SeguimientoResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Atencion;
import com.meditriaje.model.EstadoSeguimiento;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.SeguimientoPostAtencion;
import com.meditriaje.model.TipoSeguimiento;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.SeguimientoRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de dominio para el seguimiento post-atención médica (ADR-015, HU-07, HU-09).
 * Gestiona prescripción de tareas de control, reporte de evolución del paciente y aislamiento de roles.
 */
@Service
public class FollowUpService {

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SeguimientoRepository seguimientoRepository;
    private final AtencionRepository atencionRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfesionalRepository profesionalRepository;
    private final UsuarioRepository usuarioRepository;
    private final AccesoClinicoService accesoClinicoService;
    private final AuditoriaService auditoriaService;
    private final AppointmentNotificationService notificationService;

    public FollowUpService(
            SeguimientoRepository seguimientoRepository,
            AtencionRepository atencionRepository,
            PacienteRepository pacienteRepository,
            ProfesionalRepository profesionalRepository,
            UsuarioRepository usuarioRepository,
            AccesoClinicoService accesoClinicoService,
            AuditoriaService auditoriaService,
            AppointmentNotificationService notificationService
    ) {
        this.seguimientoRepository = Objects.requireNonNull(seguimientoRepository, "seguimientoRepository no puede ser nulo");
        this.atencionRepository = Objects.requireNonNull(atencionRepository, "atencionRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "pacienteRepository no puede ser nulo");
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository, "profesionalRepository no puede ser nulo");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository no puede ser nulo");
        this.accesoClinicoService = Objects.requireNonNull(accesoClinicoService, "accesoClinicoService no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService no puede ser nulo");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService no puede ser nulo");
    }

    /**
     * Prescribe una nueva tarea o plan de seguimiento post-atención (ADR-015).
     * Exclusivo para profesionales asistenciales (autor o con relación asistencial activa).
     */
    @Transactional
    public SeguimientoResponse prescribirSeguimiento(
            String atencionPublicId,
            CrearSeguimientoRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Usuario usuario = obtenerUsuario(usuarioAutenticadoPublicId);
        Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));

        if (!TipoSeguimiento.esValido(request.tipo())) {
            throw new DatosInvalidosException("Tipo de seguimiento invalido: " + request.tipo()
                    + ". Tipos permitidos: CONTROL_MEDICO, EVOLUCION_SINTOMAS, EXAMEN_PENDIENTE, ADHERENCIA_TRATAMIENTO.");
        }

        if (request.fechaSugeridaControl() != null) {
            LocalDate hoy = LocalDate.now(ZONE_BOGOTA);
            if (request.fechaSugeridaControl().isBefore(hoy)) {
                throw new DatosInvalidosException("La fecha sugerida de control no puede ser anterior a la fecha actual.");
            }
        }

        Atencion atencion = atencionRepository.buscarEntidadPorPublicId(atencionPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Atencion medica no encontrada."));

        if (!"CERRADA".equalsIgnoreCase(atencion.estado())) {
            throw new DatosInvalidosException("Solo se pueden prescribir tareas de seguimiento sobre atenciones medicas cerradas.");
        }

        boolean esAutor = atencion.profesionalId().equals(profesional.id());
        if (!esAutor) {
            accesoClinicoService.validarRelacionAsistencial(profesional.id(), atencion.pacienteId());
        }

        String publicId = UUID.randomUUID().toString();
        SeguimientoPostAtencion nuevo = new SeguimientoPostAtencion(
                null,
                publicId,
                atencion.id(),
                atencion.pacienteId(),
                profesional.id(),
                request.tipo().toUpperCase().trim(),
                request.indicaciones().trim(),
                request.fechaSugeridaControl(),
                "PENDIENTE",
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        seguimientoRepository.guardar(nuevo);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.CREACION_SEGUIMIENTO,
                "SEGUIMIENTO_POST_ATENCION",
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        SeguimientoResponse response = seguimientoRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar el seguimiento recién creado."));

        despacharNotificacionAsincrona(atencion, response);

        return response;
    }

    /**
     * Registra el reporte de evolución aportado por el paciente sobre una tarea de seguimiento (ADR-015, §5.16).
     * El reporte del paciente nunca se convierte automáticamente en un diagnóstico médico.
     */
    @Transactional
    public SeguimientoResponse reportarEvolucion(
            String seguimientoPublicId,
            ReportarEvolucionRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Usuario usuario = obtenerUsuario(usuarioAutenticadoPublicId);
        Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente."));

        SeguimientoPostAtencion seguimiento = seguimientoRepository.buscarEntidadPorPublicId(seguimientoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Seguimiento post-atencion no encontrado."));

        if (!seguimiento.pacienteId().equals(paciente.id())) {
            throw new AccesoNoAutorizadoException("No tiene autorizacion para reportar evolucion en un seguimiento ajeno.");
        }

        if (!"PENDIENTE".equalsIgnoreCase(seguimiento.estado())) {
            throw new DatosInvalidosException("El seguimiento ya se encuentra " + seguimiento.estado().toLowerCase() + ".");
        }

        seguimientoRepository.registrarReportePaciente(
                seguimiento.id(),
                request.reporte().trim(),
                Instant.now()
        );

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.REPORTE_EVOLUCION_SEGUIMIENTO,
                "SEGUIMIENTO_POST_ATENCION",
                seguimiento.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return seguimientoRepository.buscarPorPublicId(seguimiento.publicId())
                .orElseThrow(() -> new IllegalStateException("Error al recuperar el seguimiento actualizado."));
    }

    /**
     * Consulta el detalle de un seguimiento puntual con validación de autorización (ADR-007, ADR-015).
     */
    public SeguimientoResponse obtenerPorPublicId(
            String seguimientoPublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        validarNoEsAdministrador(authorities);

        Usuario usuario = obtenerUsuario(usuarioAutenticadoPublicId);
        SeguimientoPostAtencion seguimiento = seguimientoRepository.buscarEntidadPorPublicId(seguimientoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Seguimiento post-atencion no encontrado."));

        if (tieneRol(authorities, "ROLE_PACIENTE")) {
            Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                    .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente."));
            if (!seguimiento.pacienteId().equals(paciente.id())) {
                throw new AccesoNoAutorizadoException("No tiene autorizacion para consultar el seguimiento de otro paciente.");
            }
        } else if (tieneRol(authorities, "ROLE_PROFESIONAL")) {
            Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                    .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional."));
            boolean esAutor = seguimiento.profesionalId().equals(profesional.id());
            if (!esAutor) {
                accesoClinicoService.validarRelacionAsistencial(profesional.id(), seguimiento.pacienteId());
            }
        } else {
            throw new AccesoNoAutorizadoException("Rol no autorizado para acceder al seguimiento.");
        }

        return seguimientoRepository.buscarPorPublicId(seguimientoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Seguimiento post-atencion no encontrado."));
    }

    /**
     * Lista los seguimientos asociados a una atención médica específica (ADR-007, ADR-015).
     */
    public List<SeguimientoResponse> listarPorAtencion(
            String atencionPublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        validarNoEsAdministrador(authorities);

        Usuario usuario = obtenerUsuario(usuarioAutenticadoPublicId);
        Atencion atencion = atencionRepository.buscarEntidadPorPublicId(atencionPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Atencion medica no encontrada."));

        if (tieneRol(authorities, "ROLE_PACIENTE")) {
            Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                    .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente."));
            if (!atencion.pacienteId().equals(paciente.id())) {
                throw new AccesoNoAutorizadoException("No tiene autorizacion para consultar los seguimientos de otro paciente.");
            }
        } else if (tieneRol(authorities, "ROLE_PROFESIONAL")) {
            Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                    .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional."));
            boolean esAutor = atencion.profesionalId().equals(profesional.id());
            if (!esAutor) {
                accesoClinicoService.validarRelacionAsistencial(profesional.id(), atencion.pacienteId());
            }
        } else {
            throw new AccesoNoAutorizadoException("Rol no autorizado.");
        }

        return seguimientoRepository.listarPorAtencionId(atencion.id());
    }

    /**
     * Consulta paginada de las tareas de seguimiento pertenecientes al paciente autenticado.
     */
    public PaginatedResponse<SeguimientoResponse> listarMisSeguimientos(
            String usuarioAutenticadoPublicId,
            String estadoFiltro,
            int page,
            int size
    ) {
        Usuario usuario = obtenerUsuario(usuarioAutenticadoPublicId);
        Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente."));

        int safePage = Math.max(page, 0);
        int safeSize = (size < 1 || size > 100) ? 10 : size;

        String filtro = null;
        if (estadoFiltro != null && !estadoFiltro.isBlank()) {
            if (!EstadoSeguimiento.esValido(estadoFiltro)) {
                throw new DatosInvalidosException("Filtro de estado invalido: " + estadoFiltro
                        + ". Estados permitidos: PENDIENTE, COMPLETADO, CANCELADO.");
            }
            filtro = estadoFiltro.toUpperCase().trim();
        }

        List<SeguimientoResponse> items = seguimientoRepository.listarPorPacienteId(paciente.id(), filtro, safePage, safeSize);
        int totalElements = seguimientoRepository.contarPorPacienteId(paciente.id(), filtro);
        int totalPages = (int) Math.ceil((double) totalElements / safeSize);
        boolean hasNext = safePage < totalPages - 1;
        boolean hasPrevious = safePage > 0;

        return new PaginatedResponse<>(items, safePage, safeSize, totalElements, totalPages, hasNext, hasPrevious);
    }

    /**
     * Cancela una tarea de seguimiento pendiente.
     * Solo permitido para profesionales asistenciales (autor o con relación activa).
     */
    @Transactional
    public SeguimientoResponse cancelarSeguimiento(
            String seguimientoPublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        validarNoEsAdministrador(authorities);

        if (!tieneRol(authorities, "ROLE_PROFESIONAL")) {
            throw new AccesoNoAutorizadoException("Solo profesionales asistenciales pueden cancelar seguimientos.");
        }

        Usuario usuario = obtenerUsuario(usuarioAutenticadoPublicId);
        Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional."));

        SeguimientoPostAtencion seguimiento = seguimientoRepository.buscarEntidadPorPublicId(seguimientoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Seguimiento post-atencion no encontrado."));

        boolean esAutor = seguimiento.profesionalId().equals(profesional.id());
        if (!esAutor) {
            accesoClinicoService.validarRelacionAsistencial(profesional.id(), seguimiento.pacienteId());
        }

        if (!"PENDIENTE".equalsIgnoreCase(seguimiento.estado())) {
            throw new DatosInvalidosException("Solo se pueden cancelar seguimientos en estado PENDIENTE. Estado actual: " + seguimiento.estado());
        }

        seguimientoRepository.actualizarEstado(seguimiento.id(), "CANCELADO");

        return seguimientoRepository.buscarPorPublicId(seguimiento.publicId())
                .orElseThrow(() -> new IllegalStateException("Error al recuperar el seguimiento cancelado."));
    }

    private void despacharNotificacionAsincrona(Atencion atencion, SeguimientoResponse response) {
        try {
            String email = notificationService.resolverEmailPaciente(atencion.pacienteId());
            if (email != null && !email.isBlank()) {
                String fechaAtencionStr = atencion.fechaCierre() != null
                        ? DATE_FORMATTER.format(atencion.fechaCierre().atZone(ZONE_BOGOTA))
                        : (atencion.createdAt() != null ? DATE_FORMATTER.format(atencion.createdAt().atZone(ZONE_BOGOTA)) : "");

                String fechaControlStr = response.fechaSugeridaControl() != null
                        ? DATE_FORMATTER.format(response.fechaSugeridaControl())
                        : "Según evolución médica";

                notificationService.enviarResumenAtencionSeguimiento(
                        email,
                        response.pacienteNombre(),
                        response.profesionalNombre(),
                        response.profesionalEspecialidad(),
                        fechaAtencionStr,
                        response.tipo(),
                        response.indicaciones(),
                        fechaControlStr
                );
            }
        } catch (Exception ex) {
            // Tolerancia a fallos: no interrumpe la transacción clínica
        }
    }

    private Usuario obtenerUsuario(String usuarioPublicId) {
        return usuarioRepository.buscarPorPublicId(usuarioPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    private void validarNoEsAdministrador(Collection<? extends GrantedAuthority> authorities) {
        if (tieneRol(authorities, "ROLE_ADMINISTRADOR")) {
            throw new AccesoNoAutorizadoException("El personal administrativo no tiene acceso a contenido clinico.");
        }
    }

    private boolean tieneRol(Collection<? extends GrantedAuthority> authorities, String rol) {
        return authorities != null && authorities.stream().anyMatch(a -> a.getAuthority().equals(rol));
    }
}
