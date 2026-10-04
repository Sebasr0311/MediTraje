package com.meditriaje.dto.emergency;

import java.time.Instant;

/**
 * Detalle de un token temporal generado para consulta en el portal del paciente.
 */
public record AccesoQrResponse(
        String publicId,
        String estado,
        int maxAccesos,
        int accesosRealizados,
        boolean revocado,
        boolean requierePin,
        boolean incluirAlergias,
        boolean incluirMedicamentos,
        boolean incluirAtenciones,
        boolean incluirContacto,
        Instant expiraAt,
        Instant createdAt
) {
}
