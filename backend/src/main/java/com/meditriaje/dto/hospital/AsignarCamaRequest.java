package com.meditriaje.dto.hospital;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AsignarCamaRequest(
        @NotBlank(message = "camaPublicId es obligatorio")
        String camaPublicId,

        @Size(max = 500, message = "motivoAsignacion no debe superar 500 caracteres")
        String motivoAsignacion
) {
}
