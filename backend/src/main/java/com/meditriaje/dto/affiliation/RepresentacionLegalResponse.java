package com.meditriaje.dto.affiliation;

public record RepresentacionLegalResponse(
        String publicId,
        String menorPublicId,
        String menorNombre,
        String menorDocumento,
        String representantePublicId,
        String representanteNombre,
        String representanteDocumento,
        String parentesco,
        boolean verificado
) {
}
