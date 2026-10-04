package com.meditriaje.dto.report;

/**
 * Elemento de distribución estadística para reportes operativos (F2.6, RF-30).
 */
public record DistribucionItemDto(
        String publicId,
        String etiqueta,
        long cantidad,
        double porcentaje
) {
    public DistribucionItemDto(String etiqueta, long cantidad) {
        this(null, etiqueta, cantidad, 0.0);
    }

    public DistribucionItemDto(String etiqueta, long cantidad, double porcentaje) {
        this(null, etiqueta, cantidad, porcentaje);
    }
}

