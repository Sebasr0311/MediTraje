package com.meditriaje.dto.clinical;

import jakarta.validation.constraints.NotBlank;

/**
 * Solicitud para iniciar el acto asistencial de una cita médica.
 */
public record IniciarAtencionRequest(
        @NotBlank(message = "El identificador de la cita es obligatorio.")
        String citaPublicId
) {
}
