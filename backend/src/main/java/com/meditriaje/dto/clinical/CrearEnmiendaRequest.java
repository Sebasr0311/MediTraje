package com.meditriaje.dto.clinical;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de creación de enmienda o aclaración médica sobre una atención cerrada (ADR-008, HU-07).
 */
public record CrearEnmiendaRequest(
        @NotBlank(message = "El motivo de la enmienda es obligatorio.")
        @Size(max = 255, message = "El motivo no puede superar 255 caracteres.")
        String motivo,

        @NotBlank(message = "El contenido aclaratorio de la enmienda es obligatorio.")
        @Size(max = 2000, message = "El contenido no puede superar 2000 caracteres.")
        String contenido
) {
}
