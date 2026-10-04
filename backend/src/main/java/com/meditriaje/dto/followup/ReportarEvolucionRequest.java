package com.meditriaje.dto.followup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud del paciente para registrar su evolución en una tarea de seguimiento (ADR-015, §5.16).
 */
public record ReportarEvolucionRequest(
        @NotBlank(message = "El reporte de evolucion es obligatorio.")
        @Size(max = 2000, message = "El reporte no puede exceder 2000 caracteres.")
        String reporte
) {
}
