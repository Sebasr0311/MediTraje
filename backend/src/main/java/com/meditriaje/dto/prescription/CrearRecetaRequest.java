package com.meditriaje.dto.prescription;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO para la emisión de una receta médica.
 */
public record CrearRecetaRequest(
        @NotBlank(message = "El identificador de la atencion medica es obligatorio.")
        String atencionPublicId,

        @Min(value = 1, message = "La vigencia debe ser de al menos 1 dia.")
        @Max(value = 365, message = "La vigencia no puede superar 365 dias.")
        Integer vigenciaDias,

        @NotEmpty(message = "La receta debe contener al menos un medicamento prescrito.")
        @Size(max = 20, message = "No se pueden incluir mas de 20 medicamentos en una sola receta.")
        List<@Valid CrearRecetaDetalleRequest> detalles
) {}
