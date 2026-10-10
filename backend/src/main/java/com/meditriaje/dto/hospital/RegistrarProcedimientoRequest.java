package com.meditriaje.dto.hospital;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrarProcedimientoRequest(
        @NotBlank(message = "tipoProcedimiento es obligatorio")
        @Size(max = 100, message = "tipoProcedimiento no debe superar 100 caracteres")
        String tipoProcedimiento,

        @NotBlank(message = "descripcion es obligatoria")
        @Size(max = 1000, message = "descripcion no debe superar 1000 caracteres")
        String descripcion,

        @NotBlank(message = "profesionalPublicId es obligatorio")
        String profesionalPublicId,

        String salaPublicId,

        @Size(max = 1000, message = "observaciones no debe superar 1000 caracteres")
        String observaciones
) {
}
