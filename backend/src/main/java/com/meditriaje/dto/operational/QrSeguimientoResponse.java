package com.meditriaje.dto.operational;

import java.time.Instant;

public record QrSeguimientoResponse(
        String publicId,
        String episodioPublicId,
        String codigoIdentidadProvisional,
        String tokenQr,
        String urlSeguimiento,
        String ubicacionActual,
        String estadoEpisodio,
        String nivelTriaje,
        String estado,
        Instant creadoAt,
        Instant expiraAt
) {
}
