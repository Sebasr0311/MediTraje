package com.meditriaje.dto.operational;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReconocerAlertaRequest(
        @NotBlank(message = "El motivo de reconocimiento es obligatorio")
        @Size(max = 500, message = "El motivo no puede exceder 500 caracteres")
        String motivo
) {
}
