package com.meditriaje.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearEspecialidadRequest(
        @NotBlank(message = "El nombre de la especialidad es obligatorio")
        @Size(max = 60, message = "El nombre no puede exceder 60 caracteres")
        String nombre,

        @NotNull(message = "La duracion del slot es obligatoria")
        @Min(value = 5, message = "La duracion minima del slot es de 5 minutos")
        @Max(value = 240, message = "La duracion maxima del slot es de 240 minutos")
        Integer duracionSlotMin
) {}
