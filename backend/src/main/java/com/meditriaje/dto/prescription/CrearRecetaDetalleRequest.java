package com.meditriaje.dto.prescription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para la prescripción de un fármaco dentro de una receta médica.
 */
public record CrearRecetaDetalleRequest(
        @NotBlank(message = "El identificador del medicamento es obligatorio.")
        String medicamentoPublicId,

        @NotBlank(message = "La dosis es obligatoria.")
        @Size(max = 100, message = "La dosis no puede exceder 100 caracteres.")
        String dosis,

        @NotBlank(message = "La frecuencia es obligatoria.")
        @Size(max = 100, message = "La frecuencia no puede exceder 100 caracteres.")
        String frecuencia,

        @NotNull(message = "La duracion en dias es obligatoria.")
        @Min(value = 1, message = "La duracion minima es de 1 dia.")
        Integer duracionDias,

        @NotNull(message = "La cantidad es obligatoria.")
        @Min(value = 1, message = "La cantidad minima es de 1 unidad.")
        Integer cantidad,

        @Size(max = 300, message = "Las indicaciones no pueden exceder 300 caracteres.")
        String indicaciones
) {}
