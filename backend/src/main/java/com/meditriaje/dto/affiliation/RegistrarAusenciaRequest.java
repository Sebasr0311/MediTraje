package com.meditriaje.dto.affiliation;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RegistrarAusenciaRequest(
        @NotBlank(message = "profesionalPublicId es obligatorio")
        String profesionalPublicId,

        @NotNull(message = "fechaInicio es obligatoria")
        Instant fechaInicio,

        @NotNull(message = "fechaFin es obligatoria")
        Instant fechaFin,

        @NotBlank(message = "motivo es obligatorio")
        @Size(max = 500, message = "motivo no debe superar 500 caracteres")
        String motivo
) {
}
