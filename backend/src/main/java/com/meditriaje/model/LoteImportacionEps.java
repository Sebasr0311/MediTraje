package com.meditriaje.model;

import java.time.Instant;

/**
 * Cabecera de auditoría y staging para la importación masiva de afiliados EPS (Fase A, ADR-025).
 */
public record LoteImportacionEps(
        Long id,
        String publicId,
        String nombreArchivo,
        String hashSha256,
        Long epsId,
        int totalFilas,
        int filasValidas,
        int filasFallidas,
        String estado,
        Long subidoPorUsuarioId,
        Instant creadoAt,
        Instant procesadoAt
) {
}
