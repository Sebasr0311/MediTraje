package com.meditriaje.dto.affiliation;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record ReprogramarCitaRequest(
        String nuevoSlotPublicId,
        LocalDate nuevaFecha,
        LocalTime nuevaHora,
        @Size(max = 500, message = "motivo no debe superar 500 caracteres")
        String motivo
) {
    public ReprogramarCitaRequest(LocalDate nuevaFecha, LocalTime nuevaHora, String motivo) {
        this(null, nuevaFecha, nuevaHora, motivo);
    }
}
