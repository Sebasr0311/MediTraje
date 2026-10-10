package com.meditriaje.dto.affiliation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RepresentacionLegalRequest(
        @NotBlank(message = "menorPublicId es obligatorio")
        String menorPublicId,

        @NotBlank(message = "representantePublicId es obligatorio")
        String representantePublicId,

        @NotBlank(message = "parentesco es obligatorio")
        @Pattern(regexp = "PADRE|MADRE|TUTOR_LEGAL|CUIDADOR_AUTORIZADO", message = "Parentesco no válido")
        String parentesco,

        String documentoSoporte
) {
}
