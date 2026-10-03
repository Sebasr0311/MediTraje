package com.meditriaje.service;

import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.appointment.ReservarCitaRequest;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.CitaNoDisponibleException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Cita;
import com.meditriaje.model.DisponibilidadSlot;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de negocio para el agendamiento y gestión del ciclo de vida de citas médicas (ADR-002, ADR-003, ADR-006, HU-04).
 */
@Service
public class AppointmentService {

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
}
