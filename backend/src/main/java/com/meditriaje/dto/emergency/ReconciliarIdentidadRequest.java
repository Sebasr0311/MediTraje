package com.meditriaje.dto.emergency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Petición para reconciliar una identidad provisional con un expediente civil verificado (Fase U, ADR-023, U03).
 */
public record ReconciliarIdentidadRequest(
        @NotBlank(message = "El identificador público del paciente verificado es obligatorio.")
        String pacientePublicId,

        @NotBlank(message = "El motivo de verificación humana es obligatorio.")
        @Size(max = 500, message = "El motivo no puede exceder 500 caracteres.")
        String motivoVerificacion
) {
}
