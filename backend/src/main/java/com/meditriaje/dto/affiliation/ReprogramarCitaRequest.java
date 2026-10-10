package com.meditriaje.dto.affiliation;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record ReprogramarCitaRequest(
        @NotNull(message = "nuevaFecha es obligatoria")
        @Future(message = "nuevaFecha debe ser futura")
        LocalDate nuevaFecha,

        @NotNull(message = "nuevaHora es obligatoria")
        LocalTime nuevaHora,

        @NotBlank(message = "motivo es obligatorio")
        @Size(max = 500, message = "motivo no debe superar 500 caracteres")
        String motivo
) {
}
