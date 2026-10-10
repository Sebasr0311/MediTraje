package com.meditriaje.service;

import com.meditriaje.dto.emergency.AsignarEquipoRequest;
import com.meditriaje.dto.emergency.EpisodioUrgenciaResponse;
import com.meditriaje.dto.emergency.ItemColaUrgenciaResponse;
import com.meditriaje.dto.emergency.ReconciliarIdentidadRequest;
import com.meditriaje.dto.emergency.RegistrarAdmisionUrgenciaRequest;
import com.meditriaje.dto.emergency.RegistrarValoracionTriajeRequest;
import com.meditriaje.dto.emergency.ValoracionTriajeResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.AsignacionAsistencial;
import com.meditriaje.model.EpisodioAtencion;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.IdentidadProvisional;
import com.meditriaje.model.IngresoUrgencia;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Sede;
import com.meditriaje.model.Usuario;
import com.meditriaje.model.ValoracionTriaje;
import com.meditriaje.repository.AsignacionAsistencialRepository;
import com.meditriaje.repository.EpisodioAtencionRepository;
import com.meditriaje.repository.IdentidadProvisionalRepository;
import com.meditriaje.repository.IngresoUrgenciaRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.SedeRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.repository.ValoracionTriajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio integral para admisiones de urgencias, identidades provisionales, triaje presencial
 * y asignación del equipo asistencial (Fase U, ADR-022, ADR-023, ADR-026, ADR-027).
 */
@Service
@Transactional(readOnly = true)
public class EmergencyService {

    public static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EpisodioAtencionRepository episodioRepository;
    private final IngresoUrgenciaRepository ingresoRepository;
    private final IdentidadProvisionalRepository identidadRepository;
    private final ValoracionTriajeRepository valoracionRepository;
    private final AsignacionAsistencialRepository asignacionRepository;
    private final SedeRepository sedeRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProfesionalRepository profesionalRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public EmergencyService(
            EpisodioAtencionRepository episodioRepository,
            IngresoUrgenciaRepository ingresoRepository,
            IdentidadProvisionalRepository identidadRepository,
            ValoracionTriajeRepository valoracionRepository,
            AsignacionAsistencialRepository asignacionRepository,
            SedeRepository sedeRepository,
            PacienteRepository pacienteRepository,
            UsuarioRepository usuarioRepository,
            ProfesionalRepository profesionalRepository,
            AuditoriaService auditoriaService
    ) {
        this(
                episodioRepository,
                ingresoRepository,
                identidadRepository,
                valoracionRepository,
                asignacionRepository,
                sedeRepository,
                pacienteRepository,
                usuarioRepository,
                profesionalRepository,
                auditoriaService,
                Clock.system(ZONE_BOGOTA)
        );
    }

    @Autowired
    public EmergencyService(
            EpisodioAtencionRepository episodioRepository,
            IngresoUrgenciaRepository ingresoRepository,
            IdentidadProvisionalRepository identidadRepository,
            ValoracionTriajeRepository valoracionRepository,
            AsignacionAsistencialRepository asignacionRepository,
            SedeRepository sedeRepository,
            PacienteRepository pacienteRepository,
            UsuarioRepository usuarioRepository,
            ProfesionalRepository profesionalRepository,
            AuditoriaService auditoriaService,
            Clock clock
    ) {
        this.episodioRepository = Objects.requireNonNull(episodioRepository);
        this.ingresoRepository = Objects.requireNonNull(ingresoRepository);
        this.identidadRepository = Objects.requireNonNull(identidadRepository);
        this.valoracionRepository = Objects.requireNonNull(valoracionRepository);
        this.asignacionRepository = Objects.requireNonNull(asignacionRepository);
        this.sedeRepository = Objects.requireNonNull(sedeRepository);
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository);
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository);
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository);
        this.auditoriaService = Objects.requireNonNull(auditoriaService);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Registra un ingreso de urgencia presencial (U02).
     * Acepta pacientes identificados o indocumentados (NN con código provisional opaco).
     */
    @Transactional
    public EpisodioUrgenciaResponse registrarAdmisionUrgencia(
            RegistrarAdmisionUrgenciaRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        Usuario registrador = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario registrador no encontrado."));

        Sede sede = sedeRepository.buscarPorPublicId(request.sitePublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede no encontrada: " + request.sitePublicId()));

        Long pacienteId = null;
        boolean esProvisional = request.esIdentidadProvisional() || request.pacientePublicId() == null || request.pacientePublicId().isBlank();

        if (!esProvisional) {
            Paciente paciente = pacienteRepository.buscarPorPublicId(request.pacientePublicId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado: " + request.pacientePublicId()));
            pacienteId = paciente.id();
        }

        String episodioPubId = UUID.randomUUID().toString();
        Instant ahora = Instant.now(clock);

        EpisodioAtencion episodio = new EpisodioAtencion(
                null,
                episodioPubId,
                pacienteId,
                "URGENCIA",
                sede.id(),
                "REGISTRADO",
                ahora,
                null,
                request.motivoLlegada(),
                ahora,
                ahora
        );
        EpisodioAtencion guardado = episodioRepository.guardar(episodio);

        // Guardar detalle de ingreso presencial
        IngresoUrgencia ingreso = new IngresoUrgencia(
                null,
                UUID.randomUUID().toString(),
                guardado.id(),
                request.medioLlegada(),
                request.acompananteNombre(),
                request.acompananteContacto(),
                request.motivoLlegada(),
                request.observaciones(),
                registrador.id(),
                ahora
        );
        ingresoRepository.guardar(ingreso);

        String codigoProvisional = null;
        if (esProvisional) {
            codigoProvisional = generarCodigoProvisional();
            IdentidadProvisional identidad = new IdentidadProvisional(
                    null,
                    UUID.randomUUID().toString(),
                    guardado.id(),
                    codigoProvisional,
                    request.descripcionFisica(),
                    request.edadAparente(),
                    request.generoAparente(),
                    request.condicionLlegada(),
                    "PROVISIONAL",
                    null,
                    null,
                    null,
                    ahora
            );
            identidadRepository.guardar(identidad);

            auditoriaService.auditar(new EventoAuditoria(
                    registrador.id(),
                    AccionAuditable.IDENTIDAD_PROVISIONAL_REGISTRADA,
                    "EPISODIO_ATENCION",
                    guardado.publicId(),
                    ResultadoAuditoria.EXITO,
                    ipOrigen
            ));
        }

        auditoriaService.auditar(new EventoAuditoria(
                registrador.id(),
                AccionAuditable.INGRESO_URGENCIA_REGISTRADO,
                "EPISODIO_ATENCION",
                guardado.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return episodioRepository.buscarDetallePorPublicId(episodioPubId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar episodio recién creado."));
    }

    /**
     * Consulta el detalle de un episodio de urgencias con comprobaciones de autorización (U02).
     */
    public EpisodioUrgenciaResponse obtenerDetalleEpisodio(
            String episodePublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        EpisodioUrgenciaResponse detalle = episodioRepository.buscarDetallePorPublicId(episodePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio de urgencias no encontrado: " + episodePublicId));

        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        if (roles.contains("ROLE_PACIENTE")) {
            Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));
            Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                    .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente."));

            if (!Objects.equals(detalle.pacientePublicId(), paciente.publicId())) {
                throw new AccesoNoAutorizadoException("No tiene autorización para consultar episodios de otro paciente.");
            }
        }

        return detalle;
    }

    /**
     * Reconcilia la identidad provisional de un paciente NN hacia su registro civil verificado (U03).
     */
    @Transactional
    public EpisodioUrgenciaResponse reconciliarIdentidad(
            String episodePublicId,
            ReconciliarIdentidadRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        Usuario confirmador = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario confirmador no encontrado."));

        EpisodioAtencion episodio = episodioRepository.buscarPorPublicId(episodePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio no encontrado: " + episodePublicId));

        IdentidadProvisional identidad = identidadRepository.buscarPorEpisodioId(episodio.id())
                .orElseThrow(() -> new DatosInvalidosException("El episodio no cuenta con una identidad provisional para reconciliar."));

        if (!"PROVISIONAL".equals(identidad.estado())) {
            throw new ConflictoOperacionException("La identidad provisional ya fue reconciliada o no está activa.");
        }

        Paciente pacienteVerificado = pacienteRepository.buscarPorPublicId(request.pacientePublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente civil verificado no encontrado: " + request.pacientePublicId()));

        Instant ahora = Instant.now(clock);
        identidadRepository.reconciliarIdentidad(identidad.id(), pacienteVerificado.id(), confirmador.id(), ahora);
        episodioRepository.vincularPaciente(episodio.id(), pacienteVerificado.id());

        auditoriaService.auditar(new EventoAuditoria(
                confirmador.id(),
                AccionAuditable.IDENTIDAD_PROVISIONAL_RECONCILIADA,
                "IDENTIDAD_PROVISIONAL",
                identidad.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return episodioRepository.buscarDetallePorPublicId(episodePublicId)
                .orElseThrow(() -> new IllegalStateException("Error al consultar episodio reconciliado."));
    }

    /**
     * Registra una valoración clínica presencial de triaje o reevaluación (U04).
     */
    @Transactional
    public ValoracionTriajeResponse registrarValoracionTriaje(
            String episodePublicId,
            RegistrarValoracionTriajeRequest request,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        // Solo personal de enfermería o profesionales asistenciales habilitados
        if (!roles.contains("ROLE_ENFERMERIA") && !roles.contains("ROLE_PROFESIONAL")) {
            throw new AccesoNoAutorizadoException("Solo personal de enfermería o profesionales asistenciales pueden realizar valoraciones de triaje.");
        }

        Usuario evaluador = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Evaluador no encontrado."));

        EpisodioAtencion episodio = episodioRepository.buscarPorPublicId(episodePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio no encontrado: " + episodePublicId));

        if ("EGRESADO".equals(episodio.estado()) || "CANCELADO".equals(episodio.estado())) {
            throw new ConflictoOperacionException("No es posible registrar triaje en un episodio cerrado o egresado.");
        }

        int siguienteVersion = valoracionRepository.obtenerSiguienteVersion(episodio.id());
        String valoracionPubId = UUID.randomUUID().toString();
        Instant ahora = Instant.now(clock);

        ValoracionTriaje valoracion = new ValoracionTriaje(
                null,
                valoracionPubId,
                episodio.id(),
                siguienteVersion,
                request.nivel(),
                request.motivoConsulta(),
                request.hallazgosClinicos(),
                request.presionArterial(),
                request.frecuenciaCardiaca(),
                request.frecuenciaRespiratoria(),
                request.saturacionOxigeno(),
                request.temperatura(),
                request.escalaGlasgow(),
                evaluador.id(),
                request.esReevaluacion() || siguienteVersion > 1,
                request.motivoReevaluacion(),
                ahora
        );
        valoracionRepository.guardar(valoracion);

        // Actualizar estado del episodio según la urgencia del nivel asignado
        if ("I".equals(request.nivel()) || "II".equals(request.nivel())) {
            episodioRepository.actualizarEstado(episodio.id(), "EN_ATENCION");
        } else if ("REGISTRADO".equals(episodio.estado())) {
            episodioRepository.actualizarEstado(episodio.id(), "EN_TRIAJE");
        }

        auditoriaService.auditar(new EventoAuditoria(
                evaluador.id(),
                AccionAuditable.VALORACION_TRIAJE_REGISTRADA,
                "VALORACION_TRIAJE",
                valoracionPubId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return new ValoracionTriajeResponse(
                valoracionPubId,
                episodio.publicId(),
                siguienteVersion,
                request.nivel(),
                request.motivoConsulta(),
                request.hallazgosClinicos(),
                request.presionArterial(),
                request.frecuenciaCardiaca(),
                request.frecuenciaRespiratoria(),
                request.saturacionOxigeno(),
                request.temperatura(),
                request.escalaGlasgow(),
                evaluador.email(),
                request.esReevaluacion() || siguienteVersion > 1,
                request.motivoReevaluacion(),
                ahora
        );
    }

    /**
     * Consulta el historial inmutable de valoraciones de triaje de un episodio (U04).
     */
    public List<ValoracionTriajeResponse> listarHistorialTriaje(
            String episodePublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        EpisodioAtencion episodio = episodioRepository.buscarPorPublicId(episodePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio no encontrado: " + episodePublicId));

        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        if (roles.contains("ROLE_ADMINISTRADOR")) {
            throw new AccesoNoAutorizadoException("El personal administrativo no tiene acceso a notas clínicas de triaje.");
        }

        if (roles.contains("ROLE_PACIENTE")) {
            Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));
            Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                    .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente."));

            if (!Objects.equals(episodio.pacienteId(), paciente.id())) {
                throw new AccesoNoAutorizadoException("No tiene autorización para consultar el triaje de otro paciente.");
            }
        }

        return valoracionRepository.listarPorEpisodioId(episodio.id());
    }

    /**
     * Asigna un profesional asistencial (médico tratante o interconsultor) al episodio (U06).
     */
    @Transactional
    public void asignarEquipoAsistencial(
            String episodePublicId,
            AsignarEquipoRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        Usuario asignador = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario asignador no encontrado."));

        EpisodioAtencion episodio = episodioRepository.buscarPorPublicId(episodePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio no encontrado: " + episodePublicId));

        Profesional profesional = profesionalRepository.buscarPorPublicId(request.profesionalPublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Profesional asistencial no encontrado: " + request.profesionalPublicId()));

        // Inactivar asignación previa para la misma función si es médico tratante
        if ("MEDICO_TRATANTE".equals(request.funcion())) {
            asignacionRepository.inactivarAsignacionPreviaPorFuncion(episodio.id(), "MEDICO_TRATANTE");
        }

        AsignacionAsistencial asignacion = new AsignacionAsistencial(
                null,
                UUID.randomUUID().toString(),
                episodio.id(),
                profesional.id(),
                request.funcion(),
                asignador.id(),
                Instant.now(clock),
                null,
                true,
                Instant.now(clock)
        );
        asignacionRepository.guardar(asignacion);

        if (!"EN_ATENCION".equals(episodio.estado()) && !"OBSERVACION".equals(episodio.estado())) {
            episodioRepository.actualizarEstado(episodio.id(), "EN_ATENCION");
        }

        auditoriaService.auditar(new EventoAuditoria(
                asignador.id(),
                AccionAuditable.ASIGNACION_ASISTENCIAL_REGISTRADA,
                "EPISODIO_ATENCION",
                episodio.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));
    }

    /**
     * Consulta la cola priorizada de urgencias por sede (U05).
     */
    public List<ItemColaUrgenciaResponse> listarColaUrgencias(String sitePublicId, String estadoFiltro) {
        Long sedeId = null;
        if (sitePublicId != null && !sitePublicId.isBlank()) {
            Sede sede = sedeRepository.buscarPorPublicId(sitePublicId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Sede no encontrada: " + sitePublicId));
            sedeId = sede.id();
        }
        return episodioRepository.listarColaUrgenciaPorSede(sedeId, estadoFiltro);
    }

    /**
     * Cierra y egresa un episodio de urgencias (U06).
     */
    @Transactional
    public void cerrarEpisodio(String episodePublicId, String usuarioAutenticadoPublicId, String ipOrigen) {
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        EpisodioAtencion episodio = episodioRepository.buscarPorPublicId(episodePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio no encontrado: " + episodePublicId));

        if ("EGRESADO".equals(episodio.estado()) || "CANCELADO".equals(episodio.estado())) {
            throw new ConflictoOperacionException("El episodio ya se encuentra cerrado.");
        }

        Instant ahora = Instant.now(clock);
        episodioRepository.cerrarEpisodio(episodio.id(), ahora);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.EPISODIO_CERRADO,
                "EPISODIO_ATENCION",
                episodio.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));
    }

    private String generarCodigoProvisional() {
        String fecha = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
        StringBuilder sufijo = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sufijo.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return "NN-" + fecha + "-" + sufijo;
    }
}
