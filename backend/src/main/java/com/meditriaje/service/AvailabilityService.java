package com.meditriaje.service;

import com.meditriaje.dto.availability.DisponibilidadSlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.repository.DisponibilidadSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Servicio para la consulta pública y asistencial de disponibilidad de citas (HU-03, ADR-003, ADR-005, ADR-006).
 * Toda operación temporal se ejecuta bajo la zona horaria 'America/Bogota' (ADR-005).
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    public static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    private static final Set<String> MODALIDADES_PERMITIDAS = Set.of("PRESENCIAL", "TELEMEDICINA");

    private final DisponibilidadSlotRepository slotRepository;
    private final Clock clock;

    @Autowired
    public AvailabilityService(DisponibilidadSlotRepository slotRepository) {
        this(slotRepository, Clock.systemUTC());
    }

    public AvailabilityService(DisponibilidadSlotRepository slotRepository, Clock clock) {
        this.slotRepository = Objects.requireNonNull(slotRepository, "DisponibilidadSlotRepository no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "Clock no puede ser nulo");
    }

    /**
     * Consulta disponibilidad de slots libres futuros con filtros opcionales y paginación.
     *
     * @param especialidadPublicId UUID público de la especialidad (opcional)
     * @param sedePublicId UUID público de la sede (opcional)
     * @param modalidad Modalidad de atención ('PRESENCIAL' o 'TELEMEDICINA', opcional)
     * @param fecha Fecha específica a consultar en zona horaria America/Bogota (opcional)
     * @param page Número de página (>= 0)
     * @param size Tamaño de página (1 a 100)
     * @return Respuesta paginada con slots disponibles
     */
    public PaginatedResponse<DisponibilidadSlotResponse> consultarDisponibilidad(
            String especialidadPublicId,
            String sedePublicId,
            String modalidad,
            LocalDate fecha,
            int page,
            int size
    ) {
        // Validación de paginación
        if (page < 0) {
            throw new DatosInvalidosException("El número de página no puede ser menor a 0.");
        }
        if (size < 1 || size > 100) {
            throw new DatosInvalidosException("El tamaño de página debe estar entre 1 y 100.");
        }

        // Validación de modalidad
        String modalidadFiltro = null;
        if (modalidad != null && !modalidad.isBlank()) {
            modalidadFiltro = modalidad.trim().toUpperCase(Locale.ROOT);
            if (!MODALIDADES_PERMITIDAS.contains(modalidadFiltro)) {
                throw new DatosInvalidosException(
                        "Modalidad inválida: '" + modalidad + "'. Los valores permitidos son: PRESENCIAL, TELEMEDICINA."
                );
            }
        }

        String espFiltro = (especialidadPublicId != null && !especialidadPublicId.isBlank())
                ? especialidadPublicId.trim()
                : null;
        String sedeFiltro = (sedePublicId != null && !sedePublicId.isBlank())
                ? sedePublicId.trim()
                : null;

        Instant now = clock.instant();
        Instant fechaDesde;
        Instant fechaHasta;

        if (fecha != null) {
            ZonedDateTime startOfDay = fecha.atStartOfDay(ZONE_BOGOTA);
            ZonedDateTime endOfDay = fecha.atTime(23, 59, 59, 999_999_999).atZone(ZONE_BOGOTA);
            Instant startInstant = startOfDay.toInstant();
            Instant endInstant = endOfDay.toInstant();

            // Si todo el día ya pasó, retornar lista vacía sin consultar slots pasados
            if (!endInstant.isAfter(now)) {
                return PaginatedResponse.of(List.of(), page, size, 0L);
            }

            // Acotar rango al día solicitado y asegurar que sea estrictamente futuro respecto a now
            fechaDesde = startInstant.isAfter(now) ? startInstant : now;
            fechaHasta = endInstant;
        } else {
            // Sin fecha específica: buscar desde el instante actual hacia el futuro sin límite superior
            fechaDesde = now;
            fechaHasta = null;
        }

        List<DisponibilidadSlotResponse> items = slotRepository.consultarDisponibles(
                espFiltro,
                sedeFiltro,
                modalidadFiltro,
                fechaDesde,
                fechaHasta,
                page,
                size
        );
        int total = slotRepository.contarDisponibles(
                espFiltro,
                sedeFiltro,
                modalidadFiltro,
                fechaDesde,
                fechaHasta
        );

        return PaginatedResponse.of(items, page, size, (long) total);
    }
}
