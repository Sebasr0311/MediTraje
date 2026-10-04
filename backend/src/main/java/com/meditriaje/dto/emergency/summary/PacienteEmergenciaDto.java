package com.meditriaje.dto.emergency.summary;

import java.time.LocalDate;

/**
 * Datos básicos del paciente visibles en el resumen de emergencia (ADR-010, §5.17).
 */
public record PacienteEmergenciaDto(
        String nombreCompleto,
        String tipoDocumento,
        String numeroDocumento,
        LocalDate fechaNacimiento,
        int edad,
        String telefono,
        String email
) {
}
