package com.meditriaje.dto.availability;

import java.time.Duration;
import java.time.Instant;

/**
 * DTO para la consulta pública y asistencial de disponibilidad de citas (HU-03, ADR-003, ADR-005, ADR-006).
 */
public record DisponibilidadSlotResponse(
        String slotPublicId,
        String profesionalPublicId,
        String profesionalNombre,
        String especialidadPublicId,
        String especialidadNombre,
        String sedePublicId,
        String sedeNombre,
        String sedeDireccion,
        String sedeCiudad,
        Instant fechaHoraInicio,
        Instant fechaHoraFin,
        String modalidad,
        int duracionMinutos
) {
    public DisponibilidadSlotResponse(
            String slotPublicId,
            String profesionalPublicId,
            String profesionalNombre,
            String especialidadPublicId,
            String especialidadNombre,
            String sedePublicId,
            String sedeNombre,
            String sedeDireccion,
            String sedeCiudad,
            Instant fechaHoraInicio,
            Instant fechaHoraFin,
            String modalidad
    ) {
        this(
                slotPublicId,
                profesionalPublicId,
                profesionalNombre,
                especialidadPublicId,
                especialidadNombre,
                sedePublicId,
                sedeNombre,
                sedeDireccion,
                sedeCiudad,
                fechaHoraInicio,
                fechaHoraFin,
                modalidad,
                (fechaHoraInicio != null && fechaHoraFin != null)
                        ? (int) Duration.between(fechaHoraInicio, fechaHoraFin).toMinutes()
                        : 0
        );
    }
}
