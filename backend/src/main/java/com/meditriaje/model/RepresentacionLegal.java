package com.meditriaje.model;

import java.time.Instant;

/**
 * Vinculación de un menor de edad o persona bajo custodia con su tutor o representante legal (Fase C, ADR-026).
 */
public record RepresentacionLegal(
        Long id,
        String publicId,
        Long pacienteMenorId,
        Long representantePacienteId,
        String parentesco,
        String documentoSoporte,
        boolean verificado,
        Instant creadoAt
) {
}
