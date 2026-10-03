package com.meditriaje.service;

import com.meditriaje.dto.appointment.CancelarCitaRequest;
import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.appointment.ReservarCitaRequest;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.CitaNoDisponibleException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Cita;
import com.meditriaje.model.CitaStateMachine;
import com.meditriaje.model.DisponibilidadSlot;
import com.meditriaje.model.EstadoCita;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.CitaRepository;
import com.meditriaje.repository.DisponibilidadSlotRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.dto.common.PaginatedResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para el agendamiento y gestión del ciclo de vida de citas médicas (ADR-002, ADR-003, ADR-006, HU-04).
 */
@Service
public class AppointmentService {

    public static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final DisponibilidadSlotRepository disponibilidadSlotRepository;
    private final ProfesionalRepository profesionalRepository;
    private final CitaRepository citaRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public AppointmentService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            DisponibilidadSlotRepository disponibilidadSlotRepository,
            ProfesionalRepository profesionalRepository,
            CitaRepository citaRepository,
            AuditoriaService auditoriaService
    ) {
        this(
                usuarioRepository,
                pacienteRepository,
                disponibilidadSlotRepository,
                profesionalRepository,
                citaRepository,
                auditoriaService,
                Clock.systemUTC()
        );
    }

    public AppointmentService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            DisponibilidadSlotRepository disponibilidadSlotRepository,
            ProfesionalRepository profesionalRepository,
            CitaRepository citaRepository,
            AuditoriaService auditoriaService,
            Clock clock
    ) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "UsuarioRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "PacienteRepository no puede ser nulo");
        this.disponibilidadSlotRepository = Objects.requireNonNull(disponibilidadSlotRepository, "DisponibilidadSlotRepository no puede ser nulo");
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository, "ProfesionalRepository no puede ser nulo");
        this.citaRepository = Objects.requireNonNull(citaRepository, "CitaRepository no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "AuditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "Clock no puede ser nulo");
    }

    /**
     * Reserva transaccionalmente una cita médica para el paciente autenticado (ADR-006, HU-04).
     *
     * @param request                       Datos del slot y triaje opcional.
     * @param usuarioAutenticadoPublicId     UUID público del usuario en sesión.
     * @param ipOrigen                      Dirección IP cliente para la bitácora de auditoría.
     * @return {@link CitaResponse} con los datos consolidados de la cita programada.
     */
    @Transactional
    public CitaResponse reservarCita(ReservarCitaRequest request, String usuarioAutenticadoPublicId, String ipOrigen) {
        Objects.requireNonNull(request, "El request de reserva no puede ser nulo");
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        // 1. Obtener usuario autenticado
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        // 2. Obtener paciente vinculado al usuario (autenticado != autorizado, ADR-002)
        Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Solo pacientes registrados pueden agendar citas."));

        // 3. Buscar slot de disponibilidad por publicId
        DisponibilidadSlot slot = disponibilidadSlotRepository.buscarEntidadPorPublicId(request.slotPublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Slot de disponibilidad no encontrado."));

        // 4. Validar que el horario no esté en el pasado
        if (slot.fechaHoraInicio().isBefore(Instant.now(clock))) {
            throw new DatosInvalidosException("No es posible agendar una cita en un horario pasado.");
        }

        // 5. Validar correspondencia profesional-especialidad del slot
        Profesional profesional = profesionalRepository.buscarPorId(slot.profesionalId())
                .orElseThrow(() -> new DatosInvalidosException("El profesional no corresponde a la especialidad del slot."));

        if (!Objects.equals(profesional.especialidadId(), slot.especialidadId())) {
            throw new DatosInvalidosException("El profesional no corresponde a la especialidad del slot.");
        }

        // 6. Intentar reservar atómicamente el slot (cambio de estado LIBRE -> OCUPADO)
        int filas = disponibilidadSlotRepository.reservarSlot(slot.id());
        if (filas == 0) {
            throw new CitaNoDisponibleException("El slot de atencion ya ha sido reservado o no esta disponible.");
        }

        // 7. Insertar la cita en estado PROGRAMADA y manejar restricción de concurrencia
        String citaPublicId = UUID.randomUUID().toString();
        Cita cita = new Cita(
                null,
                citaPublicId,
                slot.id(),
                paciente.id(),
                null, // TRIAJE_ID desacoplado hasta M5.1
                null, // CITA_ORIGEN_ID para reprogramaciones
                "PROGRAMADA",
                null,
                null,
                null
        );

        try {
            citaRepository.crear(cita);
        } catch (DataIntegrityViolationException ex) {
            throw new CitaNoDisponibleException("El slot de atencion ya cuenta con una cita activa.");
        }

        // 8. Auditar evento exitoso inmutable
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.RESERVA_CITA,
                "CITA",
                citaPublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        // 9. Retornar vista consolidada de la cita
        return citaRepository.buscarPorPublicId(citaPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada tras creacion."));
    }

    /**
     * Cancela una cita médica agendada aplicando las reglas de máquina de estados,
     * anticipación y autorización según el rol (ADR-002, ADR-003, ADR-006, HU-05).
     *
     * @param citaPublicId                 UUID público de la cita a cancelar.
     * @param request                      DTO opcional con el motivo de cancelación.
     * @param usuarioAutenticadoPublicId   UUID público del usuario en sesión.
     * @param authorities                  Colección de roles/autoridades del usuario autenticado.
     * @param ipOrigen                     Dirección IP cliente para la bitácora de auditoría.
     * @return {@link CitaResponse} con los datos consolidados de la cita cancelada.
     */
    @Transactional
    public CitaResponse cancelarCita(
            String citaPublicId,
            CancelarCitaRequest request,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        Objects.requireNonNull(citaPublicId, "El identificador de la cita no puede ser nulo");
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        // 1. Obtener usuario autenticado
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        // 2. Obtener cita por citaPublicId
        Cita cita = citaRepository.buscarEntidadPorPublicId(citaPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada."));

        // 3. Validar transición de estado según CitaStateMachine
        EstadoCita estadoActual;
        try {
            estadoActual = EstadoCita.valueOf(cita.estado());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new DatosInvalidosException("Estado de cita no reconocido: " + cita.estado());
        }
        CitaStateMachine.validarTransicion(estadoActual, EstadoCita.CANCELADA);

        // 4. Obtener slot
        DisponibilidadSlot slot = disponibilidadSlotRepository.buscarPorId(cita.slotId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Slot de disponibilidad no encontrado."));

        // 5. Reglas de Autorización y Anticipación (ADR-006, HU-05, HU-09)
        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        if (roles.contains("ROLE_ADMINISTRADOR")) {
            // Administrador puede cancelar cualquier cita activa sin restricción de 2 horas.
        } else if (roles.contains("ROLE_PROFESIONAL")) {
            // Validar que el slot pertenezca al profesional autenticado
            Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                    .orElseThrow(() -> new AccesoNoAutorizadoException("El profesional no tiene autorizacion para cancelar citas de otro colega."));

            if (!Objects.equals(slot.profesionalId(), profesional.id())) {
                throw new AccesoNoAutorizadoException("El profesional no tiene autorizacion para cancelar citas de otro colega.");
            }
            // No aplica la regla de las 2 horas para profesionales
        } else if (roles.contains("ROLE_PACIENTE")) {
            // Validar que la cita le pertenezca
            Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                    .orElseThrow(() -> new AccesoNoAutorizadoException("Solo pacientes registrados pueden cancelar citas."));

            if (!Objects.equals(cita.pacienteId(), paciente.id())) {
                throw new AccesoNoAutorizadoException("No tiene autorizacion para cancelar una cita ajena.");
            }

            // Regla de las 2 horas (ADR-006, HU-05): la cancelación por paciente debe realizarse con al menos 2 horas de anticipación
            Instant limiteCancelacion = slot.fechaHoraInicio().minus(2, ChronoUnit.HOURS);
            if (Instant.now(clock).isAfter(limiteCancelacion)) {
                throw new DatosInvalidosException("La cancelacion por parte del paciente solo esta permitida hasta 2 horas antes de la cita.");
            }
        } else {
            throw new AccesoNoAutorizadoException("No tiene autorizacion para cancelar citas.");
        }

        // 6. Actualizar la cita a CANCELADA y registrar motivo
        String motivoCancelacion = request != null ? request.motivo() : null;
        citaRepository.actualizarEstado(cita.id(), EstadoCita.CANCELADA.name(), motivoCancelacion);

        // 7. Liberar el slot de disponibilidad: pasar el slot a 'LIBRE'
        disponibilidadSlotRepository.liberarSlot(cita.slotId());

        // 8. Auditar evento exitoso inmutable
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.CANCELACION_CITA,
                "CITA",
                cita.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        // 9. Retornar CitaResponse actualizado
        return citaRepository.buscarPorPublicId(cita.publicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada tras cancelacion."));
    }

    /**
     * Consulta la agenda asistencial del profesional autenticado con filtros opcionales de fecha y estado (HU-06, ADR-007).
     *
     * @param usuarioAutenticadoPublicId UUID público del usuario profesional en sesión.
     * @param fecha                      Fecha específica en zona horaria America/Bogota (opcional).
     * @param estado                     Filtro por estado de cita (opcional).
     * @param page                       Número de página (0-indexed).
     * @param size                       Tamaño de página (1 a 100).
     * @return {@link PaginatedResponse} conteniendo la lista de citas {@link CitaResponse}.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CitaResponse> obtenerMiAgenda(
            String usuarioAutenticadoPublicId,
            LocalDate fecha,
            String estado,
            int page,
            int size
    ) {
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        if (page < 0) {
            throw new DatosInvalidosException("El número de página no puede ser menor a 0.");
        }
        if (size < 1 || size > 100) {
            throw new DatosInvalidosException("El tamaño de página debe estar entre 1 y 100.");
        }

        String estadoFiltro = null;
        if (estado != null && !estado.isBlank()) {
            estadoFiltro = estado.trim().toUpperCase(Locale.ROOT);
            try {
                EstadoCita.valueOf(estadoFiltro);
            } catch (IllegalArgumentException ex) {
                throw new DatosInvalidosException("Estado de cita no válido: " + estado);
            }
        }

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Solo profesionales registrados pueden consultar su agenda."));

        Instant fechaDesde = null;
        Instant fechaHasta = null;

        if (fecha != null) {
            ZonedDateTime startOfDay = fecha.atStartOfDay(ZONE_BOGOTA);
            ZonedDateTime endOfDay = fecha.atTime(23, 59, 59, 999_999_999).atZone(ZONE_BOGOTA);
            fechaDesde = startOfDay.toInstant();
            fechaHasta = endOfDay.toInstant();
        }

        List<CitaResponse> citas = citaRepository.listarAgendaProfesional(
                profesional.id(),
                fechaDesde,
                fechaHasta,
                estadoFiltro,
                page,
                size
        );
        int total = citaRepository.contarAgendaProfesional(
                profesional.id(),
                fechaDesde,
                fechaHasta,
                estadoFiltro
        );

        return PaginatedResponse.of(citas, page, size, (long) total);
    }
}
