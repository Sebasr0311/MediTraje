package com.meditriaje.dto.emergency;

/**
 * Petición para resolver y obtener el resumen clínico de emergencia con el PIN opcional.
 */
public record ConsultarResumenRequest(
        String pin
) {
}
