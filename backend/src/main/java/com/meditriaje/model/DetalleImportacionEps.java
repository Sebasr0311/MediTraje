package com.meditriaje.model;

/**
 * Fila en staging previo al commit de un lote de importación masiva EPS (Fase A, ADR-025).
 */
public record DetalleImportacionEps(
        Long id,
        Long loteId,
        int numeroFila,
        String tipoDocumento,
        String numeroDocumento,
        String nombres,
        String apellidos,
        String regimen,
        String tipoAfiliado,
        String estadoFila,
        String errorMotivo
) {
}
