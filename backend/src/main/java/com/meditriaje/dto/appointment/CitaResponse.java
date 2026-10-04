package com.meditriaje.dto.appointment;

import java.time.Instant;

/**
 * Respuesta inmutable que detalla la información consolidada de una cita agendada (ADR-003, ADR-006, HU-04).
 */
public record CitaResponse(
        String publicId,
        String slotPublicId,
        String pacientePublicId,
        String pacienteNombre,
        String profesionalPublicId,
        String profesionalNombre,
        String especialidadPublicId,
        String especialidadNombre,
        String sedePublicId,
        String sedeNombre,
        String sedeDireccion,
        Instant fechaHoraInicio,
        Instant fechaHoraFin,
        String modalidad,
        String estado,
        String triajePublicId,
        String citaOrigenPublicId,
        Instant createdAt
) {}
