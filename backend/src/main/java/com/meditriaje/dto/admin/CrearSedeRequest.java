package com.meditriaje.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearSedeRequest(
        @NotBlank(message = "El identificador de la institucion es obligatorio")
        String institucionPublicId,

        @NotBlank(message = "El nombre de la sede es obligatorio")
        @Size(max = 80, message = "El nombre de la sede no puede exceder 80 caracteres")
        String nombre,

        @NotBlank(message = "La direccion es obligatoria")
        @Size(max = 120, message = "La direccion no puede exceder 120 caracteres")
        String direccion,

        @NotBlank(message = "La ciudad es obligatoria")
        @Size(max = 60, message = "La ciudad no puede exceder 60 caracteres")
        String ciudad
) {}
