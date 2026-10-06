package com.meditriaje.dto.admin;

import java.time.Instant;

/**
 * DTO que consolida la información administrativa de una cita médica para supervisión,
 * calendario de citas y exportación a reportes analíticos (ADR-003, ADR-006, ADR-007, RF-26).
 * No expone datos de historia clínica privada de pacientes.
 */
public record AdminCitaDetalleResponse(
        String citaPublicId,
        String pacientePublicId,
        String pacienteNombre,
        String pacienteDocumentoTipo,
        String pacienteDocumentoNumero,
        String pacienteTelefono,
        String pacienteEmail,
        String profesionalPublicId,
        String profesionalNombre,
        String registroMedico,
        String especialidadPublicId,
        String especialidadNombre,
        String sedePublicId,
        String sedeNombre,
        String sedeCiudad,
        Instant fechaHoraInicio,
        Instant fechaHoraFin,
        String modalidad,
        String estado,
        String motivoCancelacion,
        String triajePublicId,
        String triajeNivel,
        String motivoConsulta,
        Instant createdAt
) {}
