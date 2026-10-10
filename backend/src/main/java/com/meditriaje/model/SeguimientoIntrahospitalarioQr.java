package com.meditriaje.model;

import java.time.Instant;

/**
 * Modelo para el código QR seguro de seguimiento intrahospitalario del paciente (Fase O, ADR-028).
 */
public record SeguimientoIntrahospitalarioQr(
        Long id,
        String publicId,
        Long episodioId,
        String codigoQrToken,
        String ubicacionActualTexto,
        String estado,
        Instant creadoAt,
        Instant expiraAt
) {
}
