package com.meditriaje.dto.appointment;

import jakarta.validation.constraints.NotBlank;

/**
 * Petición para agendar/reservar una cita médica en un slot disponible (ADR-003, ADR-006, HU-04).
 */
public record ReservarCitaRequest(
        @NotBlank(message = "El identificador del slot es obligatorio.")
        String slotPublicId,
        String triajePublicId
) {
    public ReservarCitaRequest(String slotPublicId) {
        this(slotPublicId, null);
    }
}
