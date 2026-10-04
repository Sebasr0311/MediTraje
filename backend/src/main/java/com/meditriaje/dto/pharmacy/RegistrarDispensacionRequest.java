package com.meditriaje.dto.pharmacy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Solicitud de registro de evento de dispensación en farmacia (F2.4, ADR-016).
 */
public record RegistrarDispensacionRequest(
        @NotBlank(message = "El identificador de la receta es obligatorio.")
        String recetaPublicId,

        @NotBlank(message = "El identificador de la sede es obligatorio.")
        String sedePublicId,

        @Size(max = 500, message = "Las observaciones no pueden exceder 500 caracteres.")
        String observaciones,

        @NotEmpty(message = "Debe incluir al menos un detalle de entrega.")
        List<@Valid DetalleEntregaRequest> detalles
) {}
