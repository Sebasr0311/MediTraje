package com.meditriaje.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud administrativa para actualizar los datos de un profesional asistencial (HU-10).
 */
public record ActualizarProfesionalRequest(
        @NotBlank(message = "Los nombres son obligatorios.")
        @Size(max = 60, message = "Los nombres no pueden superar los 60 caracteres.")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios.")
        @Size(max = 60, message = "Los apellidos no pueden superar los 60 caracteres.")
        String apellidos,

        @NotBlank(message = "La especialidad es obligatoria.")
        String especialidadPublicId,

        String telefono
) {
    public ActualizarProfesionalRequest(
            String nombres,
            String apellidos,
            String especialidadPublicId
    ) {
        this(nombres, apellidos, especialidadPublicId, null);
    }
}
