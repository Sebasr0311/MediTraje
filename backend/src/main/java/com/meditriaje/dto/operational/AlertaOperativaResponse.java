package com.meditriaje.dto.operational;

import java.time.Instant;

public record AlertaOperativaResponse(
        String publicId,
        String sedePublicId,
        String sedeNombre,
        String tipoAlerta,
        String nivelSeveridad,
        String mensaje,
        String estado,
        String reconocidoPorEmail,
        Instant reconocidoAt,
        String motivoReconocimiento,
        Instant creadoAt
) {
}
