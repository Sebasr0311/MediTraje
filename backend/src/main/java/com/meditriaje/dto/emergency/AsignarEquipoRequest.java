package com.meditriaje.dto.emergency;

import jakarta.validation.constraints.NotBlank;

/**
 * Petición para asignar un profesional de la salud a un episodio de urgencias (Fase U, ADR-022, U06).
 */
public record AsignarEquipoRequest(
        @NotBlank(message = "El identificador público del profesional es obligatorio.")
        String profesionalPublicId,

        @NotBlank(message = "La función asistencial es obligatoria (MEDICO_TRATANTE, ENFERMERO_CARGO, INTERCONSULTOR).")
        String funcion
) {
}
