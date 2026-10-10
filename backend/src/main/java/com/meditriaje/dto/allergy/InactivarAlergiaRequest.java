package com.meditriaje.dto.allergy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Petición para inactivar una alergia con justificación clínica obligatoria (D3, T4).
 */
public record InactivarAlergiaRequest(
        @NotBlank(message = "El motivo de inactivacion es obligatorio.")
        @Size(min = 5, max = 500, message = "El motivo de inactivacion debe tener entre 5 y 500 caracteres.")
        String motivo
) {
}
