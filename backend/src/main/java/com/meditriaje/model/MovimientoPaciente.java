package com.meditriaje.model;

import java.time.Instant;

/**
 * Traslado intrahospitalario inmutable del paciente (Fase H, ADR-024).
 * Registra origen, destino, motivo y profesional responsable.
 */
public record MovimientoPaciente(
        Long id,
        String publicId,
        Long episodioId,
        Long areaOrigenId,
        Long areaDestinoId,
        Long camaOrigenId,
        Long camaDestinoId,
        Instant fechaMovimiento,
        String motivoTraslado,
        Long registradoPorUsuarioId,
        Instant creadoAt
) {
}
