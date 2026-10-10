package com.meditriaje.dto.hospital;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrasladarPacienteRequest(
        @NotBlank(message = "camaDestinoPublicId es obligatorio")
        String camaDestinoPublicId,

        @NotBlank(message = "motivoTraslado es obligatorio")
        @Size(max = 500, message = "motivoTraslado no debe superar 500 caracteres")
        String motivoTraslado
) {
}
