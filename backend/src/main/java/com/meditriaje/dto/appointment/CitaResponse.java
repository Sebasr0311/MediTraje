package com.meditriaje.dto.appointment;

import java.time.Instant;

/**
 * Respuesta inmutable que detalla la información consolidada de una cita agendada (ADR-003, ADR-006, HU-04, HU-06).
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
        String triajeNivel,
        String motivoConsulta,
        Instant createdAt,
        boolean tieneAtencion,
        String atencionPublicId,
        String atencionEstado
) {
    /**
     * Constructor de compatibilidad (20 parámetros) para preservar retrocompatibilidad.
     */
    public CitaResponse(
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
            String triajeNivel,
            String motivoConsulta,
            Instant createdAt
    ) {
        this(
                publicId,
                slotPublicId,
                pacientePublicId,
                pacienteNombre,
                profesionalPublicId,
                profesionalNombre,
                especialidadPublicId,
                especialidadNombre,
                sedePublicId,
                sedeNombre,
                sedeDireccion,
                fechaHoraInicio,
                fechaHoraFin,
                modalidad,
                estado,
                triajePublicId,
                citaOrigenPublicId,
                triajeNivel,
                motivoConsulta,
                createdAt,
                false,
                null,
                null
        );
    }

    /**
     * Constructor de compatibilidad (18 parámetros) para preservar retrocompatibilidad con tests y servicios existentes.
     */
    public CitaResponse(
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
    ) {
        this(
                publicId,
                slotPublicId,
                pacientePublicId,
                pacienteNombre,
                profesionalPublicId,
                profesionalNombre,
                especialidadPublicId,
                especialidadNombre,
                sedePublicId,
                sedeNombre,
                sedeDireccion,
                fechaHoraInicio,
                fechaHoraFin,
                modalidad,
                estado,
                triajePublicId,
                citaOrigenPublicId,
                null,
                null,
                createdAt,
                false,
                null,
                null
        );
    }
}
