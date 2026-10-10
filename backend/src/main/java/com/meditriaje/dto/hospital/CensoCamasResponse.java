package com.meditriaje.dto.hospital;

import java.util.List;

public record CensoCamasResponse(
        String sedePublicId,
        String sedeNombre,
        int totalCamas,
        int ocupadas,
        int disponibles,
        int enLimpieza,
        int enMantenimiento,
        double tasaOcupacionPorcentaje,
        List<AreaCensoItem> areas
) {
    public record AreaCensoItem(
            String areaPublicId,
            String areaCodigo,
            String areaNombre,
            String tipoArea,
            int totalCamas,
            int ocupadas,
            int disponibles
    ) {
    }
}
