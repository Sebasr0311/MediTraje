package com.meditriaje.dto.appointment;

import jakarta.validation.constraints.Size;

/**
 * Petición para solicitar la cancelación de una cita médica agendada (ADR-006, HU-05).
 */
public record CancelarCitaRequest(
        @Size(max = 255, message = "El motivo de cancelacion no puede exceder 255 caracteres.")
        String motivo
) {
    public CancelarCitaRequest() {
        this(null);
    }
}
