package com.meditriaje.service.email;

/**
 * Interfaz para el envío de notificaciones y correos electrónicos de la plataforma.
 * Retorna {@link ResultadoEnvio} para visibilidad explícita de éxito o fallo (T3, ADR-015).
 */
public interface EmailService {

    /**
     * Envía un correo con contenido HTML a un destinatario.
     *
     * @param destinatarioEmail Correo del destinatario
     * @param asunto            Asunto del mensaje
     * @param cuerpoHtml        Contenido en formato HTML
     * @return {@link ResultadoEnvio} con el resultado explícito del intento.
     */
    ResultadoEnvio enviarCorreoHtml(String destinatarioEmail, String asunto, String cuerpoHtml);

    /**
     * Envía el correo con el código de 6 dígitos para recuperación de contraseña.
     *
     * @param destinatarioEmail  Correo del usuario
     * @param destinatarioNombre Nombre para saludo personalizado
     * @param codigo             Código de 6 dígitos numéricos
     * @param minutosExpiracion  Tiempo de validez en minutos
     * @return {@link ResultadoEnvio} con el resultado explícito del intento.
     */
    ResultadoEnvio enviarCodigoRecuperacion(String destinatarioEmail, String destinatarioNombre, String codigo, int minutosExpiracion);

    /**
     * Envía las credenciales iniciales de acceso para un usuario o profesional recién creado.
     *
     * @param destinatarioEmail  Correo del destinatario
     * @param destinatarioNombre Nombre del destinatario
     * @param rol                Rol asignado
     * @param passwordTemporal   Contraseña temporal generada
     * @return {@link ResultadoEnvio} con el resultado explícito del intento.
     */
    ResultadoEnvio enviarCredencialesIniciales(String destinatarioEmail, String destinatarioNombre, String rol, String passwordTemporal);
}
