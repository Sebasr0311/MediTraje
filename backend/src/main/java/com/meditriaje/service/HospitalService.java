package com.meditriaje.service;

import com.meditriaje.dto.hospital.*;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.*;
import com.meditriaje.repository.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de Gestión Hospitalaria, Camas, Movimientos y Egresos (Fase H, ADR-024).
 * Gobierna asignaciones concurrentes, transferencias longitudinales, quirófanos y egresos.
 */
@Service
public class HospitalService {

    private final AreaHospitalariaRepository areaRepository;
    private final HabitacionSalaRepository habitacionRepository;
    private final CamaHospitalariaRepository camaRepository;
    private final OcupacionCamaRepository ocupacionRepository;
    private final MovimientoPacienteRepository movimientoRepository;
    private final ProcedimientoHospitalarioRepository procedimientoRepository;
    private final EgresoHospitalarioRepository egresoRepository;
    private final EpisodioAtencionRepository episodioRepository;
    private final SedeRepository sedeRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProfesionalRepository profesionalRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public HospitalService(
            AreaHospitalariaRepository areaRepository,
            HabitacionSalaRepository habitacionRepository,
            CamaHospitalariaRepository camaRepository,
            OcupacionCamaRepository ocupacionRepository,
            MovimientoPacienteRepository movimientoRepository,
            ProcedimientoHospitalarioRepository procedimientoRepository,
            EgresoHospitalarioRepository egresoRepository,
            EpisodioAtencionRepository episodioRepository,
            SedeRepository sedeRepository,
            UsuarioRepository usuarioRepository,
            ProfesionalRepository profesionalRepository,
            AuditoriaService auditoriaService,
            Clock clock
    ) {
        this.areaRepository = Objects.requireNonNull(areaRepository);
        this.habitacionRepository = Objects.requireNonNull(habitacionRepository);
        this.camaRepository = Objects.requireNonNull(camaRepository);
        this.ocupacionRepository = Objects.requireNonNull(ocupacionRepository);
        this.movimientoRepository = Objects.requireNonNull(movimientoRepository);
        this.procedimientoRepository = Objects.requireNonNull(procedimientoRepository);
        this.egresoRepository = Objects.requireNonNull(egresoRepository);
        this.episodioRepository = Objects.requireNonNull(episodioRepository);
        this.sedeRepository = Objects.requireNonNull(sedeRepository);
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository);
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository);
        this.auditoriaService = Objects.requireNonNull(auditoriaService);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Asigna una cama disponible a un episodio hospitalario o de urgencias (H01, H02).
     */
    @Transactional
    public CamaDetalleResponse asignarCama(
            String episodePublicId,
            AsignarCamaRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        EpisodioAtencion episodio = episodioRepository.buscarPorPublicId(episodePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio no encontrado: " + episodePublicId));

        if ("EGRESADO".equals(episodio.estado()) || "CANCELADO".equals(episodio.estado())) {
            throw new ConflictoOperacionException("No es posible asignar cama a un episodio cerrado o egresado.");
        }

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));

        CamaHospitalaria cama = camaRepository.buscarPorPublicId(request.camaPublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cama no encontrada: " + request.camaPublicId()));

        if (!"DISPONIBLE".equals(cama.estado())) {
            throw new ConflictoOperacionException("La cama seleccionada no está disponible (Estado actual: " + cama.estado() + ").");
        }

        Optional<OcupacionCama> ocupacionActivaExistente = ocupacionRepository.buscarActivaPorCamaId(cama.id());
        if (ocupacionActivaExistente.isPresent()) {
            throw new ConflictoOperacionException("Conflicto de concurrencia: La cama ya tiene una ocupación activa.");
        }

        Instant ahora = Instant.now(clock);
        HabitacionSala habDestino = habitacionRepository.buscarPorId(cama.habitacionId())
                .orElseThrow(() -> new IllegalStateException("Habitación de destino no encontrada"));

        // Si el episodio ya tenía otra cama asignada, se finaliza la anterior y se traslada
        Optional<OcupacionCama> ocupacionPrevia = ocupacionRepository.buscarActivaPorEpisodioId(episodio.id());
        Long areaOrigenId = null;
        Long camaOrigenId = null;

        if (ocupacionPrevia.isPresent()) {
            OcupacionCama prev = ocupacionPrevia.get();
            ocupacionRepository.finalizarOcupacion(prev.id(), ahora);
            camaRepository.actualizarEstado(prev.camaId(), "LIMPIEZA");
            camaOrigenId = prev.camaId();

            CamaHospitalaria camaPrev = camaRepository.buscarPorId(prev.camaId()).orElse(null);
            if (camaPrev != null) {
                HabitacionSala habPrev = habitacionRepository.buscarPorId(camaPrev.habitacionId()).orElse(null);
                if (habPrev != null) {
                    areaOrigenId = habPrev.areaId();
                }
            }
        }

        // Crear nueva ocupación
        String ocupacionPubId = UUID.randomUUID().toString();
        OcupacionCama nuevaOcupacion = new OcupacionCama(
                null,
                ocupacionPubId,
                episodio.id(),
                cama.id(),
                ahora,
                null,
                "ACTIVA",
                usuario.id(),
                request.motivoAsignacion(),
                ahora
        );
        ocupacionRepository.guardar(nuevaOcupacion);

        // Actualizar estado de la cama a OCUPADA
        camaRepository.actualizarEstado(cama.id(), "OCUPADA");

        // Registrar movimiento intrahospitalario
        String movPubId = UUID.randomUUID().toString();
        MovimientoPaciente movimiento = new MovimientoPaciente(
                null,
                movPubId,
                episodio.id(),
                areaOrigenId,
                habDestino.areaId(),
                camaOrigenId,
                cama.id(),
                ahora,
                request.motivoAsignacion() != null ? request.motivoAsignacion() : "Asignación de cama inicial",
                usuario.id(),
                ahora
        );
        movimientoRepository.guardar(movimiento);

        // Actualizar estado del episodio si aplica
        if ("REGISTRADO".equals(episodio.estado()) || "EN_TRIAJE".equals(episodio.estado())) {
            episodioRepository.actualizarEstado(episodio.id(), "OBSERVACION");
        }

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.ASIGNACION_CAMA_REGISTRADA,
                "CAMA_HOSPITALARIA",
                cama.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return camaRepository.buscarDetallePorPublicId(cama.publicId())
                .orElseThrow(() -> new IllegalStateException("Error al recuperar detalle de cama asignada"));
    }

    /**
     * Traslada longitudinalmente a un paciente entre camas y áreas hospitalarias (H03).
     */
    @Transactional
    public MovimientoResponse trasladarPaciente(
            String episodePublicId,
            TrasladarPacienteRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        EpisodioAtencion episodio = episodioRepository.buscarPorPublicId(episodePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio no encontrado: " + episodePublicId));

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));

        OcupacionCama ocupacionActual = ocupacionRepository.buscarActivaPorEpisodioId(episodio.id())
                .orElseThrow(() -> new ConflictoOperacionException("El paciente no tiene una cama activa para trasladar."));

        CamaHospitalaria camaDestino = camaRepository.buscarPorPublicId(request.camaDestinoPublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cama destino no encontrada: " + request.camaDestinoPublicId()));

        if (camaDestino.id().equals(ocupacionActual.camaId())) {
            throw new ConflictoOperacionException("La cama de destino es idéntica a la cama de origen.");
        }

        if (!"DISPONIBLE".equals(camaDestino.estado())) {
            throw new ConflictoOperacionException("La cama de destino no está disponible (Estado: " + camaDestino.estado() + ").");
        }

        Instant ahora = Instant.now(clock);

        // Finalizar ocupación previa y pasar cama a LIMPIEZA
        ocupacionRepository.finalizarOcupacion(ocupacionActual.id(), ahora);
        camaRepository.actualizarEstado(ocupacionActual.camaId(), "LIMPIEZA");

        // Crear nueva ocupación
        OcupacionCama nuevaOcupacion = new OcupacionCama(
                null,
                UUID.randomUUID().toString(),
                episodio.id(),
                camaDestino.id(),
                ahora,
                null,
                "ACTIVA",
                usuario.id(),
                request.motivoTraslado(),
                ahora
        );
        ocupacionRepository.guardar(nuevaOcupacion);
        camaRepository.actualizarEstado(camaDestino.id(), "OCUPADA");

        // Obtener áreas de origen y destino
        CamaHospitalaria camaOrigen = camaRepository.buscarPorId(ocupacionActual.camaId()).orElse(null);
        HabitacionSala habOrigen = camaOrigen != null ? habitacionRepository.buscarPorId(camaOrigen.habitacionId()).orElse(null) : null;
        HabitacionSala habDestino = habitacionRepository.buscarPorId(camaDestino.habitacionId())
                .orElseThrow(() -> new IllegalStateException("Habitación destino no encontrada"));

        Long areaOrigenId = habOrigen != null ? habOrigen.areaId() : null;
        Long areaDestinoId = habDestino.areaId();

        String movPubId = UUID.randomUUID().toString();
        MovimientoPaciente mov = new MovimientoPaciente(
                null,
                movPubId,
                episodio.id(),
                areaOrigenId,
                areaDestinoId,
                camaOrigen != null ? camaOrigen.id() : null,
                camaDestino.id(),
                ahora,
                request.motivoTraslado(),
                usuario.id(),
                ahora
        );
        movimientoRepository.guardar(mov);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.MOVIMIENTO_PACIENTE_REGISTRADO,
                "MOVIMIENTO_PACIENTE",
                movPubId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        List<MovimientoResponse> lista = movimientoRepository.listarPorEpisodioId(episodio.id());
        return lista.stream()
                .filter(m -> m.publicId().equals(movPubId))
                .findFirst()
                .orElse(new MovimientoResponse(
                        movPubId,
                        episodio.publicId(),
                        habOrigen != null ? "Área Previa" : "Ingreso",
                        "Área Destino",
                        camaOrigen != null ? camaOrigen.codigo() : "—",
                        camaDestino.codigo(),
                        ahora,
                        request.motivoTraslado(),
                        usuario.email()
                ));
    }

    /**
     * Actualiza el estado operativo de una cama (ej. de LIMPIEZA a DISPONIBLE) (H01).
     */
    @Transactional
    public void cambiarEstadoCama(
            String camaPublicId,
            String nuevoEstado,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        CamaHospitalaria cama = camaRepository.buscarPorPublicId(camaPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cama no encontrada: " + camaPublicId));

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        if ("OCUPADA".equals(cama.estado()) && !"OCUPADA".equals(nuevoEstado)) {
            Optional<OcupacionCama> act = ocupacionRepository.buscarActivaPorCamaId(cama.id());
            if (act.isPresent()) {
                throw new ConflictoOperacionException("No se puede cambiar el estado de una cama con un paciente activo asignado.");
            }
        }

        camaRepository.actualizarEstado(cama.id(), nuevoEstado);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.ESTADO_CAMA_ACTUALIZADO,
                "CAMA_HOSPITALARIA",
                cama.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));
    }

    /**
     * Registra un procedimiento clínico, acto quirúrgico o internación en quirófano (H04).
     */
    @Transactional
    public ProcedimientoResponse registrarProcedimiento(
            String episodePublicId,
            RegistrarProcedimientoRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        EpisodioAtencion episodio = episodioRepository.buscarPorPublicId(episodePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio no encontrado: " + episodePublicId));

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));

        Profesional profesional = profesionalRepository.buscarPorPublicId(request.profesionalPublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Profesional quirúrgico no encontrado: " + request.profesionalPublicId()));

        Long salaId = null;
        if (request.salaPublicId() != null && !request.salaPublicId().isBlank()) {
            HabitacionSala sala = habitacionRepository.buscarPorPublicId(request.salaPublicId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Sala/Quirófano no encontrado: " + request.salaPublicId()));
            salaId = sala.id();
        }

        Instant ahora = Instant.now(clock);
        String procPubId = UUID.randomUUID().toString();
        ProcedimientoHospitalario proc = new ProcedimientoHospitalario(
                null,
                procPubId,
                episodio.id(),
                request.tipoProcedimiento(),
                request.descripcion(),
                profesional.id(),
                salaId,
                "PROGRAMADO",
                ahora,
                null,
                request.observaciones(),
                ahora
        );
        procedimientoRepository.guardar(proc);

        episodioRepository.actualizarEstado(episodio.id(), "QUIROFANO");

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.PROCEDIMIENTO_HOSPITALARIO_REGISTRADO,
                "PROCEDIMIENTO_HOSPITALARIO",
                procPubId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        List<ProcedimientoResponse> list = procedimientoRepository.listarPorEpisodioId(episodio.id());
        return list.stream()
                .filter(p -> p.publicId().equals(procPubId))
                .findFirst()
                .orElse(new ProcedimientoResponse(
                        procPubId,
                        episodio.publicId(),
                        request.tipoProcedimiento(),
                        request.descripcion(),
                        profesional.nombres() + " " + profesional.apellidos(),
                        "Quirófano",
                        "PROGRAMADO",
                        ahora,
                        null,
                        request.observaciones(),
                        ahora
                ));
    }

    /**
     * Transiciona el estado de un procedimiento quirúrgico (H04).
     */
    @Transactional
    public void actualizarEstadoProcedimiento(
            String procedimientoPublicId,
            ActualizarEstadoProcedimientoRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        ProcedimientoHospitalario proc = procedimientoRepository.buscarPorPublicId(procedimientoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Procedimiento no encontrado: " + procedimientoPublicId));

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Instant ahora = Instant.now(clock);
        Instant finAt = ("FINALIZADO".equals(request.nuevoEstado()) || "CANCELADO".equals(request.nuevoEstado())) ? ahora : null;

        procedimientoRepository.actualizarEstado(proc.id(), request.nuevoEstado(), finAt, request.observaciones());

        if ("RECUPERACION".equals(request.nuevoEstado())) {
            episodioRepository.actualizarEstado(proc.episodioId(), "RECUPERACION");
        } else if ("FINALIZADO".equals(request.nuevoEstado())) {
            episodioRepository.actualizarEstado(proc.episodioId(), "HOSPITALIZADO");
        }

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.PROCEDIMIENTO_ESTADO_ACTUALIZADO,
                "PROCEDIMIENTO_HOSPITALARIO",
                proc.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));
    }

    /**
     * Registra el egreso médico definitivo y libera la cama hospitalaria (H05).
     */
    @Transactional
    public void registrarEgresoHospitalario(
            String episodePublicId,
            RegistrarEgresoRequest request,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        if (!roles.contains("ROLE_PROFESIONAL")) {
            throw new AccesoNoAutorizadoException("Solo profesionales médicos pueden emitir órdenes de egreso y alta hospitalaria.");
        }

        EpisodioAtencion episodio = episodioRepository.buscarPorPublicId(episodePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio no encontrado: " + episodePublicId));

        if ("EGRESADO".equals(episodio.estado())) {
            throw new ConflictoOperacionException("El episodio ya fue egresado previamente.");
        }

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Profesional medico = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional médico."));

        Instant ahora = Instant.now(clock);

        // Si el paciente tiene cama activa asignada, se finaliza ocupación y se pasa a LIMPIEZA
        Optional<OcupacionCama> ocupacionActiva = ocupacionRepository.buscarActivaPorEpisodioId(episodio.id());
        if (ocupacionActiva.isPresent()) {
            OcupacionCama oc = ocupacionActiva.get();
            ocupacionRepository.finalizarOcupacion(oc.id(), ahora);
            camaRepository.actualizarEstado(oc.camaId(), "LIMPIEZA");
        }

        // Guardar Egreso Hospitalario
        String egresoPubId = UUID.randomUUID().toString();
        EgresoHospitalario egreso = new EgresoHospitalario(
                null,
                egresoPubId,
                episodio.id(),
                medico.id(),
                ahora,
                request.tipoDestino(),
                request.diagnosticoEgreso(),
                request.epicrisisResumen(),
                request.planManejo(),
                ahora
        );
        egresoRepository.guardar(egreso);

        // Actualizar estado del episodio a EGRESADO
        episodioRepository.cerrarEpisodio(episodio.id(), ahora);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.EGRESO_HOSPITALARIO_REGISTRADO,
                "EGRESO_HOSPITALARIO",
                egresoPubId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.EPISODIO_CERRADO,
                "EPISODIO_ATENCION",
                episodio.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));
    }

    /**
     * Centro de Control Hospitalario: censo de camas e indicadores de ocupación en tiempo real (H06).
     * No expone contenido clínico privado a perfiles administrativos.
     */
    public CensoCamasResponse obtenerCensoCamas(String sedePublicId) {
        Sede sede = sedeRepository.buscarPorPublicId(sedePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede no encontrada: " + sedePublicId));

        List<CamaDetalleResponse> camas = camaRepository.listarDetallePorSedeId(sede.id());
        int total = camas.size();
        int ocupadas = (int) camas.stream().filter(c -> "OCUPADA".equals(c.estado())).count();
        int disponibles = (int) camas.stream().filter(c -> "DISPONIBLE".equals(c.estado())).count();
        int limpieza = (int) camas.stream().filter(c -> "LIMPIEZA".equals(c.estado())).count();
        int mantenimiento = (int) camas.stream().filter(c -> "MANTENIMIENTO".equals(c.estado()) || "INACTIVA".equals(c.estado())).count();

        double tasaOcupacion = total > 0 ? (double) ocupadas / total * 100.0 : 0.0;

        List<AreaHospitalaria> areas = areaRepository.listarPorSedeId(sede.id());
        List<CensoCamasResponse.AreaCensoItem> areasCenso = areas.stream().map(a -> {
            List<CamaDetalleResponse> camasArea = camas.stream().filter(c -> a.nombre().equals(c.areaNombre())).toList();
            int totArea = camasArea.size();
            int ocuArea = (int) camasArea.stream().filter(c -> "OCUPADA".equals(c.estado())).count();
            int dispArea = (int) camasArea.stream().filter(c -> "DISPONIBLE".equals(c.estado())).count();
            return new CensoCamasResponse.AreaCensoItem(
                    a.publicId(),
                    a.codigo(),
                    a.nombre(),
                    a.tipo(),
                    totArea,
                    ocuArea,
                    dispArea
            );
        }).toList();

        return new CensoCamasResponse(
                sede.publicId(),
                sede.nombre(),
                total,
                ocupadas,
                disponibles,
                limpieza,
                mantenimiento,
                Math.round(tasaOcupacion * 10.0) / 10.0,
                areasCenso
        );
    }

    public List<CamaDetalleResponse> listarCamasDetalle(String sedePublicId) {
        Sede sede = sedeRepository.buscarPorPublicId(sedePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede no encontrada: " + sedePublicId));
        return camaRepository.listarDetallePorSedeId(sede.id());
    }

    public List<MovimientoResponse> listarMovimientosEpisodio(String episodePublicId) {
        EpisodioAtencion episodio = episodioRepository.buscarPorPublicId(episodePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio no encontrado: " + episodePublicId));
        return movimientoRepository.listarPorEpisodioId(episodio.id());
    }

    public List<ProcedimientoResponse> listarProcedimientosEpisodio(String episodePublicId) {
        EpisodioAtencion episodio = episodioRepository.buscarPorPublicId(episodePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio no encontrado: " + episodePublicId));
        return procedimientoRepository.listarPorEpisodioId(episodio.id());
    }
}
