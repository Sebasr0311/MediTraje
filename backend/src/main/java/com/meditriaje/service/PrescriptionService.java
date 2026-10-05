package com.meditriaje.service;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.prescription.CrearRecetaDetalleRequest;
import com.meditriaje.dto.prescription.CrearRecetaRequest;
import com.meditriaje.dto.prescription.MedicamentoResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Atencion;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Medicamento;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.Receta;
import com.meditriaje.model.RecetaDetalle;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.MedicamentoRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.RecetaRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión de recetas médicas y catálogo de medicamentos
 * (M7.2, HU-08, ADR-007, ADR-008, ADR-011).
 */
@Service
public class PrescriptionService {

    public static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    private final UsuarioRepository usuarioRepository;
    private final ProfesionalRepository profesionalRepository;
    private final PacienteRepository pacienteRepository;
    private final AtencionRepository atencionRepository;
    private final MedicamentoRepository medicamentoRepository;
    private final RecetaRepository recetaRepository;
    private final AccesoClinicoService accesoClinicoService;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    @Autowired
    public PrescriptionService(
            UsuarioRepository usuarioRepository,
            ProfesionalRepository profesionalRepository,
            PacienteRepository pacienteRepository,
            AtencionRepository atencionRepository,
            MedicamentoRepository medicamentoRepository,
            RecetaRepository recetaRepository,
            AccesoClinicoService accesoClinicoService,
            AuditoriaService auditoriaService
    ) {
        this(
                usuarioRepository,
                profesionalRepository,
                pacienteRepository,
                atencionRepository,
                medicamentoRepository,
                recetaRepository,
                accesoClinicoService,
                auditoriaService,
                Clock.system(ZONE_BOGOTA)
        );
    }

    public PrescriptionService(
            UsuarioRepository usuarioRepository,
            ProfesionalRepository profesionalRepository,
            PacienteRepository pacienteRepository,
            AtencionRepository atencionRepository,
            MedicamentoRepository medicamentoRepository,
            RecetaRepository recetaRepository,
            AccesoClinicoService accesoClinicoService,
            AuditoriaService auditoriaService,
            Clock clock
    ) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository no puede ser nulo");
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository, "profesionalRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "pacienteRepository no puede ser nulo");
        this.atencionRepository = Objects.requireNonNull(atencionRepository, "atencionRepository no puede ser nulo");
        this.medicamentoRepository = Objects.requireNonNull(medicamentoRepository, "medicamentoRepository no puede ser nulo");
        this.recetaRepository = Objects.requireNonNull(recetaRepository, "recetaRepository no puede ser nulo");
        this.accesoClinicoService = Objects.requireNonNull(accesoClinicoService, "accesoClinicoService no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Emite una nueva receta médica asociada a una atención (HU-08).
     * Congela snapshots inmutables de los fármacos prescritos y audita la acción (ADR-008, ADR-011).
     *
     * @param request Datos de la prescripción y medicamentos solicitados
     * @param usuarioAutenticadoPublicId Identificador del usuario que emite la receta
     * @param ipOrigen Dirección IP del cliente
     * @return RecetaResponse con la receta y sus ítems consolidados
     */
    @Transactional
    public RecetaResponse emitirReceta(CrearRecetaRequest request, String usuarioAutenticadoPublicId, String ipOrigen) {
        Objects.requireNonNull(request, "request no puede ser nulo");

        // 1. Identificar profesional asistencial autenticado
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));
        Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));

        // 2. Buscar atención médica
        Atencion atencion = atencionRepository.buscarEntidadPorPublicId(request.atencionPublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Atencion medica no encontrada."));

        // 3. Autorización asistencial (ADR-007)
        if (!atencion.profesionalId().equals(profesional.id())) {
            accesoClinicoService.validarRelacionAsistencial(profesional.id(), atencion.pacienteId());
        }

        // 4. Validar medicamentos activos y preparar snapshot inmutable
        if (request.detalles() == null || request.detalles().isEmpty()) {
            throw new DatosInvalidosException("La receta debe contener al menos un medicamento prescrito.");
        }

        List<Medicamento> medicamentosValidados = new ArrayList<>();
        for (CrearRecetaDetalleRequest detalleReq : request.detalles()) {
            Medicamento med = medicamentoRepository.buscarPorPublicId(detalleReq.medicamentoPublicId())
                    .orElseThrow(() -> new DatosInvalidosException("El medicamento no existe o no se encuentra activo: " + detalleReq.medicamentoPublicId()));

            if (!med.estaActivo()) {
                throw new DatosInvalidosException("El medicamento no existe o no se encuentra activo: " + detalleReq.medicamentoPublicId());
            }
            medicamentosValidados.add(med);
        }

        // 5. Persistencia atómica de la receta
        String recetaPublicId = UUID.randomUUID().toString();
        int vigencia = request.vigenciaDias() != null ? request.vigenciaDias() : 30;

        Receta receta = new Receta(
                null,
                recetaPublicId,
                atencion.id(),
                atencion.pacienteId(),
                profesional.id(),
                vigencia,
                clock.instant()
        );

        Long recetaId = recetaRepository.crearReceta(receta);

        List<RecetaDetalle> detalles = new ArrayList<>();
        for (int i = 0; i < request.detalles().size(); i++) {
            CrearRecetaDetalleRequest detalleReq = request.detalles().get(i);
            Medicamento med = medicamentosValidados.get(i);

            detalles.add(new RecetaDetalle(
                    null,
                    recetaId,
                    med.id(),
                    med.nombreComercial(),
                    med.principioActivo(),
                    med.presentacion(),
                    med.concentracion(),
                    detalleReq.dosis(),
                    detalleReq.frecuencia(),
                    detalleReq.duracionDias(),
                    detalleReq.cantidad(),
                    detalleReq.indicaciones()
            ));
        }

        recetaRepository.guardarDetalles(recetaId, detalles);

        // 6. Auditoría inmutable obligatoria (ADR-011) — Cero datos clínicos ni medicamentos en logs
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.CREACION_RECETA,
                "RECETA",
                recetaPublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        // 7. Retornar receta consultada desde la BD
        return recetaRepository.buscarPorPublicId(recetaPublicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la receta medica recien emitida."));
    }

    /**
     * Consulta una receta médica por su identificador público, validando estrictamente los permisos
     * y relación asistencial según el rol del usuario autenticado (ADR-007, HU-08).
     *
     * @param publicId Identificador UUID de la receta
     * @param usuarioAutenticadoPublicId Identificador del usuario solicitante
     * @param authorities Autoridades/roles del usuario solicitante
     * @return RecetaResponse con el detalle inmutable de la receta
     */
    public RecetaResponse obtenerPorPublicId(
            String publicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        if (publicId == null || publicId.isBlank()) {
            throw new RecursoNoEncontradoException("Receta medica no encontrada.");
        }
        if (usuarioAutenticadoPublicId == null || authorities == null || authorities.isEmpty()) {
            throw new AccesoNoAutorizadoException("Usuario no autenticado o sin roles asignados.");
        }

        Receta receta = recetaRepository.buscarEntidadPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Receta medica no encontrada."));

        Set<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // Regla ADR-007: El administrador NUNCA accede a recetas ni contenido clínico
        if (roles.contains(AccesoClinicoService.ROL_ADMIN)) {
            throw new AccesoNoAutorizadoException("El personal administrativo no tiene acceso a recetas ni contenido clinico.");
        }

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));

        if (roles.contains(AccesoClinicoService.ROL_PACIENTE)) {
            Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                    .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente."));

            if (!receta.pacienteId().equals(paciente.id())) {
                throw new AccesoNoAutorizadoException("No tiene autorizacion para acceder a la receta de otro paciente.");
            }
        } else if (roles.contains(AccesoClinicoService.ROL_PROFESIONAL)) {
            Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                    .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));

            boolean esAutor = receta.profesionalId().equals(profesional.id());
            if (!esAutor) {
                accesoClinicoService.validarRelacionAsistencial(profesional.id(), receta.pacienteId());
            }
        } else {
            throw new AccesoNoAutorizadoException("Rol no autorizado para consultar recetas medicas.");
        }

        return recetaRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Receta medica no encontrada."));
    }

    /**
     * Consulta paginada del catálogo maestro de medicamentos activos (HU-08).
     *
     * @param query Término de búsqueda por nombre, principio activo o código (opcional)
     * @param page Número de página (0-indexed)
     * @param size Cantidad de elementos por página
     * @return Respuesta paginada con los medicamentos coincidentes
     */
    public PaginatedResponse<MedicamentoResponse> listarCatalogo(String query, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        List<MedicamentoResponse> content = medicamentoRepository.listarActivos(query, safePage, safeSize);
        int total = medicamentoRepository.contarActivos(query);

        return PaginatedResponse.of(content, safePage, safeSize, total);
    }

    /**
     * Consulta paginada de las recetas médicas emitidas para el paciente actualmente autenticado (HU-08, HU-09).
     * Solo lectura, sin exponer identificadores numéricos autonuméricos de base de datos.
     *
     * @param usuarioAutenticadoPublicId Identificador público UUID del usuario autenticado
     * @param page Número de página (0-indexed)
     * @param size Cantidad de elementos por página (1..100)
     * @param ipOrigen Dirección IP del cliente para fines de auditoría inmutable
     * @return PaginatedResponse con las recetas del paciente
     */
    public PaginatedResponse<RecetaResponse> obtenerMisRecetas(
            String usuarioAutenticadoPublicId,
            int page,
            int size,
            String ipOrigen
    ) {
        if (usuarioAutenticadoPublicId == null || usuarioAutenticadoPublicId.isBlank()) {
            throw new AccesoNoAutorizadoException("Usuario no autenticado.");
        }

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));
        Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente."));

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        List<RecetaResponse> content = recetaRepository.listarPorPacienteId(paciente.id(), safePage, safeSize);
        int total = recetaRepository.contarPorPacienteId(paciente.id());

        // Auditar consulta de recetas del paciente (ADR-011, sin datos clínicos ni fármacos)
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.CONSULTA_HISTORIA,
                "RECETA",
                paciente.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return PaginatedResponse.of(content, safePage, safeSize, total);
    }
}
