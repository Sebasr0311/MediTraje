package com.meditriaje.dto.affiliation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ActualizarAfiliacionRequest(
        @NotBlank(message = "epsPublicId es obligatorio")
        String epsPublicId,

        @NotBlank(message = "regimen es obligatorio")
        @Pattern(regexp = "CONTRIBUTIVO|SUBSIDIADO|ESPECIAL|NO_ASEGURADO", message = "Regimen no válido")
        String regimen,

        @NotBlank(message = "tipoAfiliado es obligatorio")
        @Pattern(regexp = "COTIZANTE|BENEFICIARIO|ADICIONAL|CABEZA_FAMILIA", message = "tipoAfiliado no válido")
        String tipoAfiliado,

        @NotBlank(message = "estado es obligatorio")
        @Pattern(regexp = "ACTIVO|SUSPENDIDO|RETIRADO|NO_AFILIADO", message = "Estado no válido")
        String estado
) {
}
