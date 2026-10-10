package com.meditriaje.model;

import java.time.Instant;

/**
 * Procedimiento quirúrgico o invasivo realizado durante el episodio hospitalario (Fase H, ADR-024).
 * Estados: PROGRAMADO, EN_CURSO, RECUPERACION, FINALIZADO, CANCELADO.
 */
public record ProcedimientoHospitalario(
        Long id,
        String publicId,
        Long episodioId,
        String tipoProcedimiento,
        String descripcion,
        Long profesionalId,
        Long salaId,
        String estado,
        Instant inicioAt,
        Instant finAt,
        String observaciones,
        Instant creadoAt
) {
}
