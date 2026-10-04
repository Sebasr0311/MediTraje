package com.meditriaje.dto.followup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Solicitud médica para prescribir una tarea de seguimiento post-atención (ADR-015).
 */
public record CrearSeguimientoRequest(
        @NotBlank(message = "El tipo de seguimiento es obligatorio.")
        String tipo,

        @NotBlank(message = "Las indicaciones de seguimiento son obligatorias.")
        @Size(max = 1000, message = "Las indicaciones no pueden exceder 1000 caracteres.")
        String indicaciones,

        LocalDate fechaSugeridaControl
) {
}
