package com.meditriaje.dto.emergency;

import java.time.Instant;

/**
 * Información pública preliminar de un token QR escaneado (indica vigencia y si requiere PIN).
 */
public record VerificarQrResponse(
        boolean valido,
        boolean requierePin,
        String estado,
        Instant expiraAt
) {
}
