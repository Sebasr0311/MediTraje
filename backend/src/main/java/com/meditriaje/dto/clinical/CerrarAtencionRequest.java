package com.meditriaje.dto.clinical;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud obligatoria para cerrar y consolidar una atención médica inmutable (ADR-008, HU-07).
 */
public record CerrarAtencionRequest(
        @NotBlank(message = "El codigo de diagnostico CIE-10 es obligatorio al cerrar la atencion.")
        @Size(max = 10, message = "El codigo CIE-10 no puede superar 10 caracteres.")
        String diagnosticoCie10Codigo,

        @NotBlank(message = "El motivo de consulta es obligatorio al cerrar la atencion.")
        @Size(max = 500, message = "El motivo de consulta no puede superar 500 caracteres.")
        String motivoConsulta,

        @NotBlank(message = "La evolucion clinica es obligatoria al cerrar la atencion.")
        @Size(max = 4000, message = "La evolucion clinica no puede superar 4000 caracteres.")
        String evolucion,

        @NotBlank(message = "Las indicaciones medicas son obligatorias al cerrar la atencion.")
        @Size(max = 1000, message = "Las indicaciones no pueden superar 1000 caracteres.")
        String indicaciones,

        @Valid
        SignosVitalesDto signosVitales
) {
}
