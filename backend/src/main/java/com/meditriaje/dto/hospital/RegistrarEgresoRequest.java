package com.meditriaje.dto.hospital;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrarEgresoRequest(
        @NotBlank(message = "tipoDestino es obligatorio")
        @Pattern(regexp = "ALTA_DOMICILIO|CONTRAREFERENCIA|TRASLADO_OTRA_IPS|HOSPITALIZACION_DOMICILIARIA|FALLECIMIENTO", message = "tipoDestino no válido")
        String tipoDestino,

        @NotBlank(message = "diagnosticoEgreso es obligatorio")
        @Size(max = 500, message = "diagnosticoEgreso no debe superar 500 caracteres")
        String diagnosticoEgreso,

        @Size(max = 2000, message = "epicrisisResumen no debe superar 2000 caracteres")
        String epicrisisResumen,

        @Size(max = 1000, message = "planManejo no debe superar 1000 caracteres")
        String planManejo
) {
}
