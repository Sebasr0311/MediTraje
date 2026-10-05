package com.meditriaje.service.email;

/**
 * Interfaz para el envío de notificaciones y correos electrónicos de la plataforma.
 */
public interface EmailService {

    /**
     * Envía un correo con contenido HTML a un destinatario.
     *
     * @param destinatarioEmail Correo del destinatario
     * @param asunto            Asunto del mensaje
     * @param cuerpoHtml        Contenido en formato HTML
     */
    void enviarCorreoHtml(String destinatarioEmail, String asunto, String cuerpoHtml);

    /**
     * Envía el correo con el código de 6 dígitos para recuperación de contraseña.
     *
     * @param destinatarioEmail  Correo del usuario
     * @param destinatarioNombre Nombre para saludo personalizado
     * @param codigo             Código de 6 dígitos numéricos
     * @param minutosExpiracion  Tiempo de validez en minutos
     */
    void enviarCodigoRecuperacion(String destinatarioEmail, String destinatarioNombre, String codigo, int minutosExpiracion);

    /**
     * Envía las credenciales iniciales de acceso para un usuario o profesional recién creado.
     *
     * @param destinatarioEmail  Correo del destinatario
     * @param destinatarioNombre Nombre del destinatario
     * @param rol                Rol asignado
     * @param passwordTemporal   Contraseña temporal generada
     */
    void enviarCredencialesIniciales(String destinatarioEmail, String destinatarioNombre, String rol, String passwordTemporal);
}
