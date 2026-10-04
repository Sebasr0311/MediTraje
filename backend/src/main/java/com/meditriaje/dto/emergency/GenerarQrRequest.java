package com.meditriaje.dto.emergency;

import jakarta.validation.constraints.Pattern;

/**
 * Solicitud de generación de token y código QR de acceso temporal al resumen de salud (ADR-010).
 */
public record GenerarQrRequest(
        @Pattern(regexp = "^[0-9]{4}$", message = "El PIN opcional debe ser exactamente de 4 dígitos numéricos.")
        String pin,
        Boolean incluirAlergias,
        Boolean incluirMedicamentos,
        Boolean incluirAtenciones,
        Boolean incluirContacto
) {
    public boolean getIncluirAlergias() {
        return incluirAlergias == null || incluirAlergias;
    }

    public boolean getIncluirMedicamentos() {
        return incluirMedicamentos == null || incluirMedicamentos;
    }

    public boolean getIncluirAtenciones() {
        return incluirAtenciones == null || incluirAtenciones;
    }

    public boolean getIncluirContacto() {
        return incluirContacto == null || incluirContacto;
    }
}
