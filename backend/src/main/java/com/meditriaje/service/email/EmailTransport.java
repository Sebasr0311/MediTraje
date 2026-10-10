package com.meditriaje.service.email;

/**
 * Contrato de transporte para el envío físico o simulado de correos electrónicos.
 */
public interface EmailTransport {

    /**
     * Envía un correo electrónico en formato HTML a un destinatario.
     *
     * @param destinatarioEmail Dirección de correo electrónico destino.
     * @param asunto            Asunto del mensaje.
     * @param cuerpoHtml        Contenido en formato HTML.
     * @return {@link ResultadoEnvio} indicando éxito o detalle del fallo.
     */
    ResultadoEnvio enviar(String destinatarioEmail, String asunto, String cuerpoHtml);

    /**
     * Identificador del transporte activo ("brevo-api", "smtp", "log").
     */
    String getNombreTransporte();
}
