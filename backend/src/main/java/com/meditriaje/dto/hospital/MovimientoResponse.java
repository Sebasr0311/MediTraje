package com.meditriaje.dto.hospital;

import java.time.Instant;

public record MovimientoResponse(
        String publicId,
        String episodioPublicId,
        String areaOrigenNombre,
        String areaDestinoNombre,
        String camaOrigenCodigo,
        String camaDestinoCodigo,
        Instant fechaMovimiento,
        String motivoTraslado,
        String registradoPorNombre
) {
}
