package com.meditriaje.dto.admin;

import java.util.List;

/**
 * Respuesta a la generación en lote de turnos de disponibilidad asistencial (HU-10, ADR-006).
 */
public record GenerarSlotsResponse(
        int slotsGenerados,
        List<SlotResponse> slots,
        String mensaje
) {
}
