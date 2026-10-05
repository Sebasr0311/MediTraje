package com.meditriaje.service;

import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.clinical.CerrarAtencionRequest;
import com.meditriaje.dto.clinical.CrearEnmiendaRequest;
import com.meditriaje.dto.clinical.EnmiendaResponse;
import com.meditriaje.dto.clinical.IniciarAtencionRequest;
import com.meditriaje.dto.clinical.SignosVitalesDto;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Atencion;
import com.meditriaje.model.Cita;
import com.meditriaje.model.CitaStateMachine;
import com.meditriaje.model.DiagnosticoCie10;
import com.meditriaje.model.DisponibilidadSlot;
import com.meditriaje.model.EstadoCita;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.SignoVital;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.CitaRepository;
import com.meditriaje.repository.DiagnosticoCie10Repository;
import com.meditriaje.repository.DisponibilidadSlotRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de negocio para el inicio, atención y cierre inmutable de actos médicos
 * (ADR-007, ADR-008, ADR-011, HU-07, HU-09).
 */
@Service
public class ClinicalAttentionService {

    public static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfesionalRepository profesionalRepository;
    private final CitaRepository citaRepository;
    private final DisponibilidadSlotRepository disponibilidadSlotRepository;
    private final AtencionRepository atencionRepository;
    private final DiagnosticoCie10Repository diagnosticoCie10Repository;
    private final AccesoClinicoService accesoClinicoService;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    @Autowired
    public ClinicalAttentionService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ProfesionalRepository profesionalRepository,
            CitaRepository citaRepository,
            DisponibilidadSlotRepository disponibilidadSlotRepository,
            AtencionRepository atencionRepository,
            DiagnosticoCie10Repository diagnosticoCie10Repository,
            AccesoClinicoService accesoClinicoService,
            AuditoriaService auditoriaService
    ) {
        this(
                usuarioRepository,
                pacienteRepository,
                profesionalRepository,
                citaRepository,
                disponibilidadSlotRepository,
                atencionRepository,
                diagnosticoCie10Repository,
                accesoClinicoService,
                auditoriaService,
                Clock.system(ZONE_BOGOTA)
        );
    }

    public ClinicalAttentionService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ProfesionalRepository profesionalRepository,
            CitaRepository citaRepository,
            DisponibilidadSlotRepository disponibilidadSlotRepository,
            AtencionRepository atencionRepository,
            DiagnosticoCie10Repository diagnosticoCie10Repository,
            AccesoClinicoService accesoClinicoService,
            AuditoriaService auditoriaService,
            Clock clock
    ) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "pacienteRepository no puede ser nulo");
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository, "profesionalRepository no puede ser nulo");
        this.citaRepository = Objects.requireNonNull(citaRepository, "citaRepository no puede ser nulo");
        this.disponibilidadSlotRepository = Objects.requireNonNull(disponibilidadSlotRepository, "disponibilidadSlotRepository no puede ser nulo");
        this.atencionRepository = Objects.requireNonNull(atencionRepository, "atencionRepository no puede ser nulo");
        this.diagnosticoCie10Repository = Objects.requireNonNull(diagnosticoCie10Repository, "diagnosticoCie10Repository no puede ser nulo");
        this.accesoClinicoService = Objects.requireNonNull(accesoClinicoService, "accesoClinicoService no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Inicia una atención médica vinculada a una cita (HU-07).
     * Solo el profesional asignado a la cita puede iniciar la atención.
     */
    @Transactional
    public AtencionResponse iniciarAtencion(
            IniciarAtencionRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));
        Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));

        Cita cita = citaRepository.buscarEntidadPorPublicId(request.citaPublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita medica no encontrada."));

        DisponibilidadSlot slot = disponibilidadSlotRepository.buscarPorId(cita.slotId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Slot de disponibilidad no encontrado."));

        // Validar que el médico autenticado es el asignado a la cita
        if (!slot.profesionalId().equals(profesional.id())) {
            throw new AccesoNoAutorizadoException("El profesional no esta asignado a esta cita medica.");
        }

        // Validar estado de la cita
        if (!"PROGRAMADA".equalsIgnoreCase(cita.estado()) && !"CONFIRMADA".equalsIgnoreCase(cita.estado())) {
            throw new DatosInvalidosException("La cita no se encuentra en un estado valido para ser atendida (estado actual: " + cita.estado() + ").");
        }

        // Validar relación 1:1 estricta
        if (atencionRepository.existePorCitaId(cita.id())) {
            throw new DatosInvalidosException("Ya existe una atencion clinica registrada para esta cita medica.");
        }

        // Si la cita estaba PROGRAMADA, se confirma al iniciar la atención
        if ("PROGRAMADA".equalsIgnoreCase(cita.estado())) {
            CitaStateMachine.validarTransicion(EstadoCita.PROGRAMADA, EstadoCita.CONFIRMADA);
            citaRepository.actualizarEstado(cita.id(), "CONFIRMADA", null);
        }

        String atencionPublicId = UUID.randomUUID().toString();
        Atencion atencion = new Atencion(
                null,
                atencionPublicId,
                cita.id(),
                cita.pacienteId(),
                profesional.id(),
                "ABIERTA"
        );

        atencionRepository.crear(atencion);

        // Auditoría inmutable sin datos clínicos (ADR-011)
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.CREACION_ATENCION,
                "ATENCION",
                atencionPublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return atencionRepository.buscarDetallePorPublicId(atencionPublicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la atencion recien creada."));
    }

    /**
     * Cierra y consolida una atención médica de forma inmutable (ADR-008, HU-07).
     * Pasa la cita a ATENDIDA y audita el evento.
     */
    @Transactional
    public AtencionResponse cerrarAtencion(
            String atencionPublicId,
            CerrarAtencionRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));
        Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));

        Atencion atencion = atencionRepository.buscarEntidadPorPublicId(atencionPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Atencion clinica no encontrada."));

        // Validar que el profesional es el asignado a la atención
        if (!atencion.profesionalId().equals(profesional.id())) {
            throw new AccesoNoAutorizadoException("No tiene autorizacion para cerrar la atencion de otro profesional.");
        }

        // Validar que la atención siga abierta (inmutabilidad ADR-008)
        if (!atencion.estaAbierta()) {
            throw new DatosInvalidosException("La atencion clinica ya se encuentra CERRADA y es inmutable.");
        }

        // Validar diagnóstico CIE-10
        DiagnosticoCie10 diag = diagnosticoCie10Repository.buscarPorCodigo(request.diagnosticoCie10Codigo())
                .orElseThrow(() -> new DatosInvalidosException("Codigo de diagnostico CIE-10 no encontrado: " + request.diagnosticoCie10Codigo()));
        if (!diag.estaActivo()) {
            throw new DatosInvalidosException("El diagnostico CIE-10 seleccionado se encuentra inactivo.");
        }

        // Validar y registrar signos vitales si fueron provistos
        SignosVitalesDto signos = request.signosVitales();
        if (signos != null && signos.tieneValores()) {
            if (signos.presionSistolica() != null && signos.presionDiastolica() != null
                    && signos.presionSistolica() <= signos.presionDiastolica()) {
                throw new DatosInvalidosException("La presion arterial sistolica debe ser estrictamente mayor a la presion diastolica.");
            }
            SignoVital sv = new SignoVital(
                    atencion.id(),
                    signos.presionSistolica(),
                    signos.presionDiastolica(),
                    signos.frecuenciaCardiaca(),
                    signos.frecuenciaRespiratoria(),
                    signos.temperatura(),
                    signos.saturacionOxigeno(),
                    signos.pesoKg(),
                    signos.tallaCm()
            );
            atencionRepository.guardarSignosVitales(sv);
        }

        // Cerrar atención en base de datos (inmutable)
        Instant fechaCierre = clock.instant();
        atencionRepository.cerrarAtencion(
                atencion.id(),
                diag.id(),
                request.motivoConsulta(),
                request.evolucion(),
                request.indicaciones(),
                fechaCierre
        );

        // Actualizar estado de la cita a ATENDIDA
        Cita cita = citaRepository.buscarEntidadPorId(atencion.citaId())
                .orElseThrow(() -> new IllegalStateException("Cita vinculada a la atencion no encontrada."));

        EstadoCita estadoOrigen = EstadoCita.valueOf(cita.estado());
        CitaStateMachine.validarTransicion(estadoOrigen, EstadoCita.ATENDIDA);
        citaRepository.actualizarEstado(cita.id(), EstadoCita.ATENDIDA.name(), null);

        // Auditoría inmutable sin datos clínicos ni diagnósticos en logs/auditoría (ADR-011)
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.CIERRE_ATENCION,
                "ATENCION",
                atencionPublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return atencionRepository.buscarDetallePorPublicId(atencionPublicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la atencion recien cerrada."));
    }

    /**
     * Consulta una atención médica por su ID público, validando autorización vía AccesoClinicoService (HU-07, HU-09).
     */
    public AtencionResponse obtenerPorPublicId(
            String atencionPublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        Atencion atencion = atencionRepository.buscarEntidadPorPublicId(atencionPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Atencion clinica no encontrada."));

        // Autorización centralizada de relación asistencial / pertenencia de paciente
        accesoClinicoService.validarAccesoHistorialClinico(usuarioAutenticadoPublicId, atencion.pacienteId(), authorities);

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        // Auditar consulta de historia clínica
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.CONSULTA_HISTORIA,
                "ATENCION",
                atencionPublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return atencionRepository.buscarDetallePorPublicId(atencionPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Atencion clinica no encontrada."));
    }

    /**
     * Registra una enmienda o aclaración médica inmutable sobre una atención cerrada (ADR-008, HU-07).
     * Garantiza que la atención esté CERRADA y que el profesional cuente con relación asistencial o sea el autor.
     */
    @Transactional
    public EnmiendaResponse crearEnmienda(
            String atencionPublicId,
            CrearEnmiendaRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));
        Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));

        Atencion atencion = atencionRepository.buscarEntidadPorPublicId(atencionPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Atencion clinica no encontrada."));

        // Inmutabilidad estricta (ADR-008): solo se admiten enmiendas sobre atenciones CERRADAS
        if (!atencion.estaCerrada()) {
            throw new DatosInvalidosException("Solo es posible registrar enmiendas sobre atenciones clinicas CERRADAS.");
        }

        // Autorización asistencial (ADR-007): el profesional debe ser el autor o tener relación asistencial activa
        if (!atencion.profesionalId().equals(profesional.id())) {
            accesoClinicoService.validarRelacionAsistencial(profesional.id(), atencion.pacienteId());
        }

        Instant ahora = clock.instant();
        atencionRepository.crearEnmienda(
                atencion.id(),
                profesional.id(),
                request.motivo(),
                request.contenido(),
                ahora
        );

        // Auditoría inmutable sin contenido clínico en bitácora (ADR-011)
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.ENMIENDA_ATENCION,
                "ATENCION",
                atencionPublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        String nombreCompleto = (profesional.nombres() + " " + profesional.apellidos()).trim();
        return new EnmiendaResponse(
                profesional.publicId(),
                nombreCompleto,
                request.motivo(),
                request.contenido(),
                ahora
        );
    }

    /**
     * Consulta paginada del historial clínico del paciente actualmente autenticado (HU-09).
     * Incluye atenciones, diagnósticos, signos vitales y enmiendas sin exponer IDs numéricos.
     */
    public PaginatedResponse<AtencionResponse> obtenerMiHistoriaClinica(
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

        List<AtencionResponse> contenido = atencionRepository.listarHistoriaPaciente(paciente.id(), safePage, safeSize);
        long total = atencionRepository.contarHistoriaPaciente(paciente.id());

        // Auditar consulta de historia clínica del paciente (ADR-011)
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.CONSULTA_HISTORIA,
                "HISTORIA_CLINICA",
                paciente.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return PaginatedResponse.of(contenido, safePage, safeSize, total);
    }

    /**
     * Consulta paginada de la historia clínica de un paciente para profesionales asistenciales (HU-07, HU-09, ADR-007, ADR-017).
     * Valida autorización asistencial (cita futura, atención previa en 12 meses o acceso Break-Glass activo).
     */
    public PaginatedResponse<AtencionResponse> obtenerHistoriaClinicaPaciente(
            String pacientePublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            int page,
            int size,
            String ipOrigen
    ) {
        if (usuarioAutenticadoPublicId == null || usuarioAutenticadoPublicId.isBlank()) {
            throw new AccesoNoAutorizadoException("Usuario no autenticado.");
        }
        if (pacientePublicId == null || pacientePublicId.isBlank()) {
            throw new DatosInvalidosException("Identificador de paciente no proporcionado.");
        }

        // Valida que el profesional tenga relación asistencial activa o break-glass
        accesoClinicoService.validarAccesoHistorialClinico(usuarioAutenticadoPublicId, pacientePublicId, authorities);

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Paciente paciente = pacienteRepository.buscarPorPublicId(pacientePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado."));

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        List<AtencionResponse> contenido = atencionRepository.listarHistoriaPaciente(paciente.id(), safePage, safeSize);
        long total = atencionRepository.contarHistoriaPaciente(paciente.id());

        // Auditar consulta de historia clínica médica (ADR-011)
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.CONSULTA_HISTORIA,
                "HISTORIA_CLINICA",
                paciente.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return PaginatedResponse.of(contenido, safePage, safeSize, total);
    }
}
