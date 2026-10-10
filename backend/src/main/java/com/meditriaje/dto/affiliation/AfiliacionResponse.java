package com.meditriaje.dto.affiliation;

import java.time.Instant;
import java.time.LocalDate;

public record AfiliacionResponse(
        String publicId,
        String pacientePublicId,
        String tipoDocumento,
        String numeroDocumento,
        String pacienteNombre,
        String epsCodigo,
        String epsNombre,
        String regimen,
        String tipoAfiliado,
        String estado,
        LocalDate fechaAfiliacion,
        String fuenteVerificacion,
        Instant ultimaVerificacionAt
) {
}
