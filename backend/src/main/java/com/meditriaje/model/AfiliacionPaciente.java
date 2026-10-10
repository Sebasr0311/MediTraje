package com.meditriaje.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Registro de afiliación de un paciente a una EPS (Fase A, ADR-025).
 * Regímenes: CONTRIBUTIVO, SUBSIDIADO, ESPECIAL, NO_ASEGURADO.
 */
public record AfiliacionPaciente(
        Long id,
        String publicId,
        Long pacienteId,
        String tipoDocumento,
        String numeroDocumento,
        Long epsId,
        String regimen,
        String tipoAfiliado,
        String estado,
        LocalDate fechaAfiliacion,
        String fuenteVerificacion,
        Instant ultimaVerificacionAt,
        Instant creadoAt,
        Instant updatedAt
) {
}
