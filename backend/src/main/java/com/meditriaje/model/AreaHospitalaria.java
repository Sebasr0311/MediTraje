package com.meditriaje.model;

import java.time.Instant;

/**
 * Área hospitalaria funcional dentro de una sede (Fase H, ADR-024).
 * Ejemplo: Pabellón de Urgencias, UCI Adultos, Quirófanos, Hospitalización Piso 3.
 */
public record AreaHospitalaria(
        Long id,
        String publicId,
        Long sedeId,
        String codigo,
        String nombre,
        String tipo,
        String piso,
        String estado,
        Instant creadoAt
) {
}
