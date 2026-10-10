package com.meditriaje.dto.affiliation;

import java.util.List;

public record LotePreviewResponse(
        String lotePublicId,
        String nombreArchivo,
        String epsCodigo,
        String epsNombre,
        int totalFilas,
        int filasValidas,
        int filasFallidas,
        String estado,
        List<FilaPreviewItem> muestraFilas
) {
    public record FilaPreviewItem(
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
}
