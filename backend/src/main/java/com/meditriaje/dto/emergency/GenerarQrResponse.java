package com.meditriaje.dto.emergency;

import java.time.Instant;

/**
 * Respuesta inmediata a la creación del acceso temporal. Contiene el token en texto plano para renderizar el QR.
 */
public record GenerarQrResponse(
        String publicId,
        String token,
        String qrUrl,
        Instant expiraAt,
        int maxAccesos,
        boolean requierePin,
        boolean incluirAlergias,
        boolean incluirMedicamentos,
        boolean incluirAtenciones,
        boolean incluirContacto,
        Instant createdAt
) {
}
