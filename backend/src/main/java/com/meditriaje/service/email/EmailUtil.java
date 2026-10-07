package com.meditriaje.service.email;

/**
 * Utilitarios para manipulación segura de correos electrónicos, enmascaramiento de PII
 * y sanitización de mensajes de error de transporte (T3 Plan Post-Auditoría).
 */
public final class EmailUtil {

    private EmailUtil() {
    }

    /**
     * Enmascara una dirección de correo electrónico para logging seguro sin exponer datos personales (PII/PHI).
     * Ejemplo: "paciente.perez@ejemplo.com" -> "p***@ejemplo.com".
     */
    public static String enmascararEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        String trimmed = email.trim();
        int atIdx = trimmed.indexOf('@');
        if (atIdx <= 1) {
            return "***" + (atIdx >= 0 ? trimmed.substring(atIdx) : "");
        }
        String local = trimmed.substring(0, atIdx);
        String domain = trimmed.substring(atIdx);
        return local.charAt(0) + "***" + domain;
    }

    /**
     * Sanitiza y trunca mensajes de error para almacenamiento y logs, asegurando que:
     * - No contenga la API key.
     * - No contenga fragmentos HTML.
     * - Esté truncado a máximo 500 caracteres.
     */
    public static String sanitizarMensajeError(String mensaje, String apiKey) {
        if (mensaje == null || mensaje.isBlank()) {
            return null;
        }
        String limpio = mensaje;
        if (apiKey != null && !apiKey.isBlank()) {
            limpio = limpio.replace(apiKey, "[REDACTED_API_KEY]");
        }
        // Eliminar posibles etiquetas HTML
        limpio = limpio.replaceAll("<[^>]*>", " ");
        // Normalizar espacios múltiples
        limpio = limpio.replaceAll("\\s+", " ").trim();

        if (limpio.length() > 500) {
            limpio = limpio.substring(0, 500);
        }
        return limpio;
    }
}
