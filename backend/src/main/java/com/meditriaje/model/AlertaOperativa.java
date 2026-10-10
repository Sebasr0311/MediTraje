package com.meditriaje.model;

import java.time.Instant;

/**
 * Modelo de alerta operativa de saturación y demoras hospitalarias (Fase O, ADR-027).
 */
public record AlertaOperativa(
        Long id,
        String publicId,
        Long sedeId,
        String tipoAlerta,
        String nivelSeveridad,
        String mensaje,
        String estado,
        Long reconocidoPorUsuarioId,
        Instant reconocidoAt,
        String motivoReconocimiento,
        Instant creadoAt
) {
}
