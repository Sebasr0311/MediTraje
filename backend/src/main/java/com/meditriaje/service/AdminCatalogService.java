package com.meditriaje.service;

import com.meditriaje.dto.admin.ActualizarEspecialidadRequest;
import com.meditriaje.dto.admin.ActualizarInstitucionRequest;
import com.meditriaje.dto.admin.ActualizarSedeRequest;
import com.meditriaje.dto.admin.CrearEspecialidadRequest;
import com.meditriaje.dto.admin.CrearInstitucionRequest;
import com.meditriaje.dto.admin.CrearSedeRequest;
import com.meditriaje.dto.admin.EspecialidadResponse;
import com.meditriaje.dto.admin.InstitucionResponse;
import com.meditriaje.dto.admin.SedeResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Especialidad;
import com.meditriaje.model.Institucion;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Sede;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.EspecialidadRepository;
import com.meditriaje.repository.InstitucionRepository;
import com.meditriaje.repository.SedeRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de administración de catálogos asistenciales e infraestructura (HU-10, ADR-003, ADR-011).
 * Gestiona especialidades, instituciones prestadoras y sedes médicas.
 * Aplica auditoría inmutable en cada cambio administrativo.
 */
@Service
public class AdminCatalogService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";
    private static final Set<String> ESTADOS_PERMITIDOS = Set.of(ESTADO_ACTIVO, ESTADO_INACTIVO);

    private static final String RECURSO_ESPECIALIDAD = "ESPECIALIDAD";
    private static final String RECURSO_INSTITUCION = "INSTITUCION";
    private static final String RECURSO_SEDE = "SEDE";

    private final EspecialidadRepository especialidadRepository;
    private final InstitucionRepository institucionRepository;
    private final SedeRepository sedeRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public AdminCatalogService(
            EspecialidadRepository especialidadRepository,
            InstitucionRepository institucionRepository,
            SedeRepository sedeRepository,
            UsuarioRepository usuarioRepository,
            AuditoriaService auditoriaService
    ) {
        this.especialidadRepository = Objects.requireNonNull(especialidadRepository, "EspecialidadRepository no puede ser nulo");
        this.institucionRepository = Objects.requireNonNull(institucionRepository, "InstitucionRepository no puede ser nulo");
        this.sedeRepository = Objects.requireNonNull(sedeRepository, "SedeRepository no puede ser nulo");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "UsuarioRepository no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "AuditoriaService no puede ser nulo");
    }

    // =========================================================================
    // 1. ESPECIALIDADES
    // =========================================================================

    @Transactional
    public EspecialidadResponse crearEspecialidad(CrearEspecialidadRequest req, String adminPublicId, String ipOrigen) {
        String nombreLimpio = req.nombre().trim();
        if (especialidadRepository.existePorNombre(nombreLimpio)) {
            throw new DatosInvalidosException("Ya existe una especialidad con el nombre: " + nombreLimpio);
        }

        String publicId = UUID.randomUUID().toString();
        Especialidad especialidad = especialidadRepository.crear(publicId, nombreLimpio, req.duracionSlotMin());

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_ESPECIALIDAD,
                especialidad.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return toEspecialidadResponse(especialidad);
    }

    @Transactional
    public EspecialidadResponse actualizarEspecialidad(String publicId, ActualizarEspecialidadRequest req, String adminPublicId, String ipOrigen) {
        Especialidad actual = especialidadRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad: " + publicId));

        String nombreLimpio = req.nombre().trim();
        if (especialidadRepository.existePorNombreYNoPublicId(nombreLimpio, publicId)) {
            throw new DatosInvalidosException("Ya existe otra especialidad con el nombre: " + nombreLimpio);
        }

        especialidadRepository.actualizar(publicId, nombreLimpio, req.duracionSlotMin());

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_ESPECIALIDAD,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new EspecialidadResponse(publicId, nombreLimpio, req.duracionSlotMin(), actual.estado());
    }

    @Transactional(readOnly = true)
    public EspecialidadResponse obtenerEspecialidad(String publicId) {
        Especialidad especialidad = especialidadRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad: " + publicId));
        return toEspecialidadResponse(especialidad);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<EspecialidadResponse> listarEspecialidades(int page, int size, String estadoFiltro) {
        String estadoNormalizado = normalizarYValidarEstado(estadoFiltro);
        List<Especialidad> lista = especialidadRepository.listar(page, size, estadoNormalizado);
        long total = especialidadRepository.contar(estadoNormalizado);

        List<EspecialidadResponse> responses = lista.stream().map(this::toEspecialidadResponse).toList();
        return PaginatedResponse.of(responses, page, size, total);
    }

    @Transactional
    public EspecialidadResponse desactivarEspecialidad(String publicId, String adminPublicId, String ipOrigen) {
        Especialidad especialidad = especialidadRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad: " + publicId));

        especialidadRepository.cambiarEstado(publicId, ESTADO_INACTIVO);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_ESPECIALIDAD,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new EspecialidadResponse(especialidad.publicId(), especialidad.nombre(), especialidad.duracionSlotMin(), ESTADO_INACTIVO);
    }

    @Transactional
    public EspecialidadResponse activarEspecialidad(String publicId, String adminPublicId, String ipOrigen) {
        Especialidad especialidad = especialidadRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad: " + publicId));

        especialidadRepository.cambiarEstado(publicId, ESTADO_ACTIVO);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_ESPECIALIDAD,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new EspecialidadResponse(especialidad.publicId(), especialidad.nombre(), especialidad.duracionSlotMin(), ESTADO_ACTIVO);
    }

    // =========================================================================
    // 2. INSTITUCIONES
    // =========================================================================

    @Transactional
    public InstitucionResponse crearInstitucion(CrearInstitucionRequest req, String adminPublicId, String ipOrigen) {
        String nitLimpio = req.nit().trim();
        if (institucionRepository.existePorNit(nitLimpio)) {
            throw new DatosInvalidosException("Ya existe una institución con el NIT: " + nitLimpio);
        }

        String publicId = UUID.randomUUID().toString();
        Institucion institucion = institucionRepository.crear(publicId, nitLimpio, req.razonSocial().trim());

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_INSTITUCION,
                institucion.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return toInstitucionResponse(institucion);
    }

    @Transactional
    public InstitucionResponse actualizarInstitucion(String publicId, ActualizarInstitucionRequest req, String adminPublicId, String ipOrigen) {
        Institucion actual = institucionRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Institución: " + publicId));

        String razonSocialLimpia = req.razonSocial().trim();
        institucionRepository.actualizar(publicId, razonSocialLimpia);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_INSTITUCION,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new InstitucionResponse(publicId, actual.nit(), razonSocialLimpia, actual.estado(), actual.createdAt());
    }

    @Transactional(readOnly = true)
    public InstitucionResponse obtenerInstitucion(String publicId) {
        Institucion institucion = institucionRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Institución: " + publicId));
        return toInstitucionResponse(institucion);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<InstitucionResponse> listarInstituciones(int page, int size, String estadoFiltro) {
        String estadoNormalizado = normalizarYValidarEstado(estadoFiltro);
        List<Institucion> lista = institucionRepository.listar(page, size, estadoNormalizado);
        long total = institucionRepository.contar(estadoNormalizado);

        List<InstitucionResponse> responses = lista.stream().map(this::toInstitucionResponse).toList();
        return PaginatedResponse.of(responses, page, size, total);
    }

    @Transactional
    public InstitucionResponse desactivarInstitucion(String publicId, String adminPublicId, String ipOrigen) {
        Institucion institucion = institucionRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Institución: " + publicId));

        institucionRepository.cambiarEstado(publicId, ESTADO_INACTIVO);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_INSTITUCION,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new InstitucionResponse(institucion.publicId(), institucion.nit(), institucion.razonSocial(), ESTADO_INACTIVO, institucion.createdAt());
    }

    @Transactional
    public InstitucionResponse activarInstitucion(String publicId, String adminPublicId, String ipOrigen) {
        Institucion institucion = institucionRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Institución: " + publicId));

        institucionRepository.cambiarEstado(publicId, ESTADO_ACTIVO);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_INSTITUCION,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new InstitucionResponse(institucion.publicId(), institucion.nit(), institucion.razonSocial(), ESTADO_ACTIVO, institucion.createdAt());
    }

    // =========================================================================
    // 3. SEDES
    // =========================================================================

    @Transactional
    public SedeResponse crearSede(CrearSedeRequest req, String adminPublicId, String ipOrigen) {
        Institucion institucion = institucionRepository.buscarPorPublicId(req.institucionPublicId().trim())
                .orElseThrow(() -> new RecursoNoEncontradoException("Institución: " + req.institucionPublicId()));

        if (!ESTADO_ACTIVO.equalsIgnoreCase(institucion.estado())) {
            throw new DatosInvalidosException("No se pueden crear sedes en una institución inactiva.");
        }

        String publicId = UUID.randomUUID().toString();
        Sede sede = sedeRepository.crear(
                institucion.id(),
                publicId,
                req.nombre().trim(),
                req.direccion().trim(),
                req.ciudad().trim()
        );

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_SEDE,
                sede.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return toSedeResponse(sede, institucion);
    }

    @Transactional
    public SedeResponse actualizarSede(String publicId, ActualizarSedeRequest req, String adminPublicId, String ipOrigen) {
        Sede actual = sedeRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede: " + publicId));

        sedeRepository.actualizar(publicId, req.nombre().trim(), req.direccion().trim(), req.ciudad().trim());

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_SEDE,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        Institucion institucion = institucionRepository.buscarPorId(actual.institucionId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Institución vinculada a la sede"));

        return new SedeResponse(
                publicId,
                institucion.publicId(),
                institucion.razonSocial(),
                req.nombre().trim(),
                req.direccion().trim(),
                req.ciudad().trim(),
                actual.estado()
        );
    }

    @Transactional(readOnly = true)
    public SedeResponse obtenerSede(String publicId) {
        Sede sede = sedeRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede: " + publicId));

        Institucion institucion = institucionRepository.buscarPorId(sede.institucionId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Institución vinculada a la sede"));

        return toSedeResponse(sede, institucion);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SedeResponse> listarSedes(int page, int size, String institucionPublicId, String estadoFiltro) {
        String estadoNormalizado = normalizarYValidarEstado(estadoFiltro);
        String instPublicIdFiltro = (institucionPublicId != null && !institucionPublicId.isBlank())
                ? institucionPublicId.trim() : null;

        List<Sede> sedes = sedeRepository.listar(page, size, instPublicIdFiltro, estadoNormalizado);
        long total = sedeRepository.contar(instPublicIdFiltro, estadoNormalizado);

        Map<Long, Institucion> institucionCache = new HashMap<>();
        List<SedeResponse> responses = sedes.stream().map(s -> {
            Institucion inst = institucionCache.computeIfAbsent(s.institucionId(), id ->
                    institucionRepository.buscarPorId(id)
                            .orElseThrow(() -> new RecursoNoEncontradoException("Institución con ID: " + id))
            );
            return toSedeResponse(s, inst);
        }).toList();

        return PaginatedResponse.of(responses, page, size, total);
    }

    @Transactional
    public SedeResponse desactivarSede(String publicId, String adminPublicId, String ipOrigen) {
        Sede sede = sedeRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede: " + publicId));

        sedeRepository.cambiarEstado(publicId, ESTADO_INACTIVO);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_SEDE,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        Institucion institucion = institucionRepository.buscarPorId(sede.institucionId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Institución vinculada a la sede"));

        return new SedeResponse(
                sede.publicId(),
                institucion.publicId(),
                institucion.razonSocial(),
                sede.nombre(),
                sede.direccion(),
                sede.ciudad(),
                ESTADO_INACTIVO
        );
    }

    @Transactional
    public SedeResponse activarSede(String publicId, String adminPublicId, String ipOrigen) {
        Sede sede = sedeRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede: " + publicId));

        Institucion institucion = institucionRepository.buscarPorId(sede.institucionId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Institución vinculada a la sede"));

        if (!ESTADO_ACTIVO.equalsIgnoreCase(institucion.estado())) {
            throw new DatosInvalidosException("No se puede activar una sede de una institución inactiva.");
        }

        sedeRepository.cambiarEstado(publicId, ESTADO_ACTIVO);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_SEDE,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new SedeResponse(
                sede.publicId(),
                institucion.publicId(),
                institucion.razonSocial(),
                sede.nombre(),
                sede.direccion(),
                sede.ciudad(),
                ESTADO_ACTIVO
        );
    }

    // =========================================================================
    // MAPPERS Y HELPERS PRIVADOS
    // =========================================================================

    private Long obtenerAdminUsuarioId(String adminPublicId) {
        return usuarioRepository.buscarPorPublicId(adminPublicId)
                .map(Usuario::id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario administrador"));
    }

    private String normalizarYValidarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        String normalizado = estado.trim().toUpperCase();
        if (!ESTADOS_PERMITIDOS.contains(normalizado)) {
            throw new DatosInvalidosException("Estado invalido. Los valores permitidos son: ACTIVO, INACTIVO.");
        }
        return normalizado;
    }

    private EspecialidadResponse toEspecialidadResponse(Especialidad esp) {
        return new EspecialidadResponse(
                esp.publicId(),
                esp.nombre(),
                esp.duracionSlotMin(),
                esp.estado()
        );
    }

    private InstitucionResponse toInstitucionResponse(Institucion inst) {
        return new InstitucionResponse(
                inst.publicId(),
                inst.nit(),
                inst.razonSocial(),
                inst.estado(),
                inst.createdAt()
        );
    }

    private SedeResponse toSedeResponse(Sede sede, Institucion inst) {
        return new SedeResponse(
                sede.publicId(),
                inst.publicId(),
                inst.razonSocial(),
                sede.nombre(),
                sede.direccion(),
                sede.ciudad(),
                sede.estado()
        );
    }
}
