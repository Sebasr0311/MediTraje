package com.meditriaje.dto.emergency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Petición de ingreso presencial y apertura de episodio de urgencias (Fase U, ADR-022, U02).
 */
public record RegistrarAdmisionUrgenciaRequest(
        @NotBlank(message = "El identificador de la sede es obligatorio.")
        String sitePublicId,

        String pacientePublicId,

        boolean esIdentidadProvisional,

        @NotBlank(message = "El medio de llegada es obligatorio.")
        String medioLlegada,

        @NotBlank(message = "El motivo de llegada es obligatorio.")
        @Size(max = 500, message = "El motivo de llegada no puede exceder 500 caracteres.")
        String motivoLlegada,

        @Size(max = 150, message = "El nombre del acompañante no puede exceder 150 caracteres.")
        String acompananteNombre,

        @Size(max = 100, message = "El contacto del acompañante no puede exceder 100 caracteres.")
        String acompananteContacto,

        @Size(max = 1000, message = "Las observaciones no pueden exceder 1000 caracteres.")
        String observaciones,

        // Atributos de paciente no identificado (NN / Provisional)
        @Size(max = 500, message = "La descripción física no puede exceder 500 caracteres.")
        String descripcionFisica,

        Integer edadAparente,

        String generoAparente,

        @Size(max = 100, message = "La condición de llegada no puede exceder 100 caracteres.")
        String condicionLlegada
) {
}
