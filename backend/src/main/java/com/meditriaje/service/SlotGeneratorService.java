package com.meditriaje.service;

import com.meditriaje.dto.admin.GenerarSlotsRequest;
import com.meditriaje.dto.admin.GenerarSlotsResponse;
import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.DisponibilidadSlot;
import com.meditriaje.model.Especialidad;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Sede;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.DisponibilidadSlotRepository;
import com.meditriaje.repository.EspecialidadRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.SedeRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de generación y gestión de slots de disponibilidad médica (HU-10, ADR-002, ADR-003, ADR-005, ADR-006, ADR-011).
 * Toda operación temporal se ejecuta bajo la zona horaria 'America/Bogota' (ADR-005).
 */
@Service
public class SlotGeneratorService {

    public static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_LIBRE = "LIBRE";
    private static final String ESTADO_BLOQUEADO = "BLOQUEADO";
    private static final String ESTADO_OCUPADO = "OCUPADO";
    private static final Set<String> ESTADOS_PERMITIDOS = Set.of(ESTADO_LIBRE, ESTADO_BLOQUEADO, ESTADO_OCUPADO);
    private static final String RECURSO_SLOT = "DISPONIBILIDAD_SLOT";

    private final DisponibilidadSlotRepository slotRepository;
    private final ProfesionalRepository profesionalRepository;
    private final UsuarioRepository usuarioRepository;
    private final SedeRepository sedeRepository;
    private final EspecialidadRepository especialidadRepository;
    private final AuditoriaService auditoriaService;

    public SlotGeneratorService(
            DisponibilidadSlotRepository slotRepository,
            ProfesionalRepository profesionalRepository,
            UsuarioRepository usuarioRepository,
            SedeRepository sedeRepository,
            EspecialidadRepository especialidadRepository,
            AuditoriaService auditoriaService
    ) {
        this.slotRepository = Objects.requireNonNull(slotRepository, "DisponibilidadSlotRepository no puede ser nulo");
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository, "ProfesionalRepository no puede ser nulo");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "UsuarioRepository no puede ser nulo");
        this.sedeRepository = Objects.requireNonNull(sedeRepository, "SedeRepository no puede ser nulo");
        this.especialidadRepository = Objects.requireNonNull(especialidadRepository, "EspecialidadRepository no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "AuditoriaService no puede ser nulo");
    }

    /**
     * Genera en lote los slots de disponibilidad según los parámetros indicados.
     * Valida reglas de negocio, inexistencia de solapes y registra auditoría inmutable.
     */
    @Transactional
    public GenerarSlotsResponse generarSlots(GenerarSlotsRequest request, String adminPublicId, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de generacion no puede ser nula");

        // 1. Validar profesional existente y con usuario activo
        Profesional profesional = profesionalRepository.buscarPorPublicId(request.profesionalPublicId().trim())
                .orElseThrow(() -> new RecursoNoEncontradoException("Profesional no encontrado: " + request.profesionalPublicId()));

        Usuario usuario = usuarioRepository.buscarPorId(profesional.usuarioId())
                .orElseThrow(() -> new DatosInvalidosException("El profesional no tiene un usuario valido en el sistema."));

        if (!ESTADO_ACTIVO.equalsIgnoreCase(usuario.estado())) {
            throw new DatosInvalidosException("El profesional no tiene un usuario activo en el sistema.");
        }

        // 2. Validar sede existente y activa
        Sede sede = sedeRepository.buscarPorPublicId(request.sedePublicId().trim())
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede no encontrada: " + request.sedePublicId()));

        if (!ESTADO_ACTIVO.equalsIgnoreCase(sede.estado())) {
            throw new DatosInvalidosException("La sede seleccionada no se encuentra activa.");
        }

        // 3. Validar rango de fechas (zona America/Bogota)
        LocalDate hoyBogota = LocalDate.now(ZONE_BOGOTA);
        if (request.fechaInicio().isBefore(hoyBogota)) {
            throw new DatosInvalidosException("La fecha de inicio no puede ser anterior a la fecha actual (" + hoyBogota + ").");
        }
        if (request.fechaFin().isBefore(request.fechaInicio())) {
            throw new DatosInvalidosException("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }
        if (ChronoUnit.DAYS.between(request.fechaInicio(), request.fechaFin()) > 90) {
            throw new DatosInvalidosException("El rango de fechas no puede superar los 90 dias.");
        }

        // 4. Validar horas
        if (!request.horaFin().isAfter(request.horaInicio())) {
            throw new DatosInvalidosException("La hora de fin debe ser posterior a la hora de inicio.");
        }

        // 5. Determinar duracion del slot
        Especialidad esp = especialidadRepository.buscarPorId(profesional.especialidadId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad del profesional"));

        int duracion = (request.duracionMinutos() != null)
                ? request.duracionMinutos()
                : (esp.duracionSlotMin() > 0 ? esp.duracionSlotMin() : 20);

        if (duracion < 5 || duracion > 120) {
            throw new DatosInvalidosException("La duracion del slot debe estar entre 5 y 120 minutos.");
        }

        // 6. Modalidad
        String modalidad = "PRESENCIAL";
        if (request.modalidad() != null && !request.modalidad().isBlank()) {
            modalidad = request.modalidad().trim().toUpperCase(Locale.ROOT);
            if (!"PRESENCIAL".equals(modalidad) && !"TELEMEDICINA".equals(modalidad)) {
                throw new DatosInvalidosException("Modalidad invalida. Los valores permitidos son: PRESENCIAL, TELEMEDICINA.");
            }
        }

        // 7. Algoritmo generador de turnos
        List<DayOfWeek> diasSemana = request.diasSemana();
        List<DisponibilidadSlot> slotsGenerados = new ArrayList<>();

        LocalDate fechaActual = request.fechaInicio();
        while (!fechaActual.isAfter(request.fechaFin())) {
            if (diasSemana != null && !diasSemana.isEmpty() && !diasSemana.contains(fechaActual.getDayOfWeek())) {
                fechaActual = fechaActual.plusDays(1);
                continue;
            }

            ZonedDateTime windowStart = ZonedDateTime.of(fechaActual, request.horaInicio(), ZONE_BOGOTA);
            ZonedDateTime windowEnd = ZonedDateTime.of(fechaActual, request.horaFin(), ZONE_BOGOTA);

            Instant slotInicio = windowStart.toInstant();
            Instant slotFin = slotInicio.plus(Duration.ofMinutes(duracion));

            while (!slotFin.isAfter(windowEnd.toInstant())) {
                String publicId = UUID.randomUUID().toString();
                DisponibilidadSlot slot = new DisponibilidadSlot(
                        publicId,
                        profesional.id(),
                        sede.id(),
                        profesional.especialidadId(),
                        slotInicio,
                        slotFin,
                        modalidad,
                        ESTADO_LIBRE
                );
                slotsGenerados.add(slot);

                slotInicio = slotFin;
                slotFin = slotInicio.plus(Duration.ofMinutes(duracion));
            }

            fechaActual = fechaActual.plusDays(1);
        }

        if (slotsGenerados.isEmpty()) {
            throw new DatosInvalidosException("No fue posible generar ningun slot con los criterios especificados.");
        }

        // 8. Control de solapes con BD
        for (DisponibilidadSlot slot : slotsGenerados) {
            if (slotRepository.existeSolape(profesional.id(), slot.fechaHoraInicio(), slot.fechaHoraFin())) {
                ZonedDateTime zdtInicio = slot.fechaHoraInicio().atZone(ZONE_BOGOTA);
                ZonedDateTime zdtFin = slot.fechaHoraFin().atZone(ZONE_BOGOTA);
                throw new DatosInvalidosException(
                        "Existe un solape de horario para el profesional en el intervalo "
                        + zdtInicio.toLocalDateTime() + " - " + zdtFin.toLocalDateTime()
                );
            }
        }

        // 9. Guardar lote
        slotRepository.guardarLote(slotsGenerados);

        // 10. Auditar
        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_SLOT,
                profesional.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        // 11. Mapear respuesta
        String profesionalNombre = profesional.nombres() + " " + profesional.apellidos();
        List<SlotResponse> responseList = slotsGenerados.stream()
                .map(s -> new SlotResponse(
                        s.publicId(),
                        profesional.publicId(),
                        profesionalNombre,
                        sede.publicId(),
                        sede.nombre(),
                        esp.publicId(),
                        esp.nombre(),
                        s.fechaHoraInicio(),
                        s.fechaHoraFin(),
                        s.modalidad(),
                        s.estado()
                ))
                .toList();

        return new GenerarSlotsResponse(
                responseList.size(),
                responseList,
                "Se generaron exitosamente " + responseList.size() + " slots de disponibilidad."
        );
    }

    /**
     * Bloquea un slot que se encuentre en estado LIBRE.
     */
    @Transactional
    public SlotResponse bloquearSlot(String publicId, String adminPublicId, String ipOrigen) {
        SlotResponse slot = slotRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Slot no encontrado: " + publicId));

        if (!ESTADO_LIBRE.equalsIgnoreCase(slot.estado())) {
            throw new DatosInvalidosException("Solo se pueden bloquear slots en estado LIBRE. Estado actual: " + slot.estado());
        }

        slotRepository.cambiarEstado(publicId, ESTADO_BLOQUEADO);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_SLOT,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new SlotResponse(
                slot.publicId(),
                slot.profesionalPublicId(),
                slot.profesionalNombre(),
                slot.sedePublicId(),
                slot.sedeNombre(),
                slot.especialidadPublicId(),
                slot.especialidadNombre(),
                slot.fechaHoraInicio(),
                slot.fechaHoraFin(),
                slot.modalidad(),
                ESTADO_BLOQUEADO
        );
    }

    /**
     * Desbloquea un slot que se encuentre en estado BLOQUEADO, regresándolo a LIBRE.
     */
    @Transactional
    public SlotResponse desbloquearSlot(String publicId, String adminPublicId, String ipOrigen) {
        SlotResponse slot = slotRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Slot no encontrado: " + publicId));

        if (!ESTADO_BLOQUEADO.equalsIgnoreCase(slot.estado())) {
            throw new DatosInvalidosException("Solo se pueden desbloquear slots en estado BLOQUEADO. Estado actual: " + slot.estado());
        }

        slotRepository.cambiarEstado(publicId, ESTADO_LIBRE);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_SLOT,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new SlotResponse(
                slot.publicId(),
                slot.profesionalPublicId(),
                slot.profesionalNombre(),
                slot.sedePublicId(),
                slot.sedeNombre(),
                slot.especialidadPublicId(),
                slot.especialidadNombre(),
                slot.fechaHoraInicio(),
                slot.fechaHoraFin(),
                slot.modalidad(),
                ESTADO_LIBRE
        );
    }

    /**
     * Elimina físicamente un slot si se encuentra en estado LIBRE.
     */
    @Transactional
    public void eliminarSlot(String publicId, String adminPublicId, String ipOrigen) {
        SlotResponse slot = slotRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Slot no encontrado: " + publicId));

        if (!ESTADO_LIBRE.equalsIgnoreCase(slot.estado())) {
            throw new DatosInvalidosException("Solo se pueden eliminar slots en estado LIBRE. Estado actual: " + slot.estado());
        }

        int eliminados = slotRepository.eliminar(publicId);
        if (eliminados == 0) {
            throw new DatosInvalidosException("No fue posible eliminar el slot.");
        }

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_SLOT,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );
    }

    /**
     * Obtiene el detalle de un slot por su publicId.
     */
    public SlotResponse obtenerPorPublicId(String publicId) {
        return slotRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Slot no encontrado: " + publicId));
    }

    /**
     * Lista slots paginados con filtros dinámicos.
     */
    public PaginatedResponse<SlotResponse> listar(
            int page,
            int size,
            String profesionalPublicId,
            String sedePublicId,
            String especialidadPublicId,
            Instant fechaDesde,
            Instant fechaHasta,
            String estado
    ) {
        String estadoFiltro = null;
        if (estado != null && !estado.isBlank()) {
            estadoFiltro = estado.trim().toUpperCase(Locale.ROOT);
            if (!ESTADOS_PERMITIDOS.contains(estadoFiltro)) {
                throw new DatosInvalidosException("Estado invalido. Los valores permitidos son: LIBRE, OCUPADO, BLOQUEADO.");
            }
        }

        String profFiltro = (profesionalPublicId != null && !profesionalPublicId.isBlank()) ? profesionalPublicId.trim() : null;
        String sedeFiltro = (sedePublicId != null && !sedePublicId.isBlank()) ? sedePublicId.trim() : null;
        String espFiltro = (especialidadPublicId != null && !especialidadPublicId.isBlank()) ? especialidadPublicId.trim() : null;

        List<SlotResponse> items = slotRepository.listar(
                page, size, profFiltro, sedeFiltro, espFiltro, fechaDesde, fechaHasta, estadoFiltro
        );
        int total = slotRepository.contar(
                profFiltro, sedeFiltro, espFiltro, fechaDesde, fechaHasta, estadoFiltro
        );

        return PaginatedResponse.of(items, page, size, (long) total);
    }

    private Long obtenerAdminUsuarioId(String adminPublicId) {
        return usuarioRepository.buscarPorPublicId(adminPublicId)
                .map(Usuario::id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario administrador"));
    }
}
