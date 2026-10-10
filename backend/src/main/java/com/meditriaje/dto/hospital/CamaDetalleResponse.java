package com.meditriaje.dto.hospital;

public record CamaDetalleResponse(
        String publicId,
        String habitacionPublicId,
        String habitacionCodigo,
        String areaPublicId,
        String areaNombre,
        String sedePublicId,
        String sedeNombre,
        String codigo,
        String estado,
        String episodioPublicId,
        String pacienteNombre,
        String ocupacionPublicId
) {
}
