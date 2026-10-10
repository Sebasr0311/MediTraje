package com.meditriaje.dto.hospital;

import java.time.Instant;

public record ProcedimientoResponse(
        String publicId,
        String episodioPublicId,
        String tipoProcedimiento,
        String descripcion,
        String profesionalNombre,
        String salaCodigo,
        String estado,
        Instant inicioAt,
        Instant finAt,
        String observaciones,
        Instant creadoAt
) {
}
