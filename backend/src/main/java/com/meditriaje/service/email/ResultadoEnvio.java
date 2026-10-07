package com.meditriaje.service.email;

/**
 * Representa el resultado explícito del intento de envío de un correo electrónico.
 * Garantiza visibilidad operativa ante fallos (ADR-015, T3 Plan Post-Auditoría).
 */
public record ResultadoEnvio(
        boolean exito,
        String codigo,
        String mensaje
) {
    public ResultadoEnvio {
        codigo = (codigo != null && !codigo.isBlank()) ? codigo.trim() : (exito ? "OK" : "ERROR");
        mensaje = EmailUtil.sanitizarMensajeError(mensaje, null);
    }

    public static ResultadoEnvio exitoso() {
        return new ResultadoEnvio(true, "OK", "Correo enviado exitosamente.");
    }

    public static ResultadoEnvio exitosoSimulado(String transporte) {
        return new ResultadoEnvio(true, "SIMULADO", "Correo simulado por transporte " + transporte + ".");
    }

    public static ResultadoEnvio fallido(String codigo, String mensaje) {
        return new ResultadoEnvio(false, codigo, mensaje);
    }
}
