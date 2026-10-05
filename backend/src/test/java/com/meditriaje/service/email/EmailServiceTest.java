package com.meditriaje.service.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private EmailTemplateService templateService;
    private DefaultEmailService emailService;

    @Mock
    private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        templateService = new EmailTemplateService(new DefaultResourceLoader());
        emailService = new DefaultEmailService(
                templateService,
                javaMailSender,
                "no-reply@meditriaje.com",
                "MediTriaje 2.0",
                "http://localhost:5500"
        );
    }

    @Test
    @DisplayName("Debe renderizar la plantilla HTML y registrar el correo en el buffer")
    void debeEnviarCorreoRecuperacionConPlantillaHtml() {
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        String email = "paciente.test@ejemplo.com";
        String nombre = "Carlos Mendoza";
        String codigo = "654321";
        int minutosExpiracion = 15;

        emailService.enviarCodigoRecuperacion(email, nombre, codigo, minutosExpiracion);

        Optional<CorreoEnviado> enviadoOpt = emailService.obtenerUltimoCorreoEnviado();
        assertThat(enviadoOpt).isPresent();

        CorreoEnviado enviado = enviadoOpt.get();
        assertThat(enviado.destinatario()).isEqualTo(email);
        assertThat(enviado.asunto()).contains("Código de recuperación");
        assertThat(enviado.cuerpoHtml())
                .contains("Hola, Carlos Mendoza:")
                .contains("654321")
                .contains("15 minutos")
                .contains("MediTriaje 2.0")
                .contains("Aviso de Seguridad");

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Debe despachar correo de bienvenida con credenciales iniciales para profesionales")
    void debeEnviarCredencialesInicialesHtml() {
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        String email = "medico.experto@hospital.com.co";
        String nombre = "Dra. Sofía Restrepo";
        String rol = "Profesional Asistencial (Medicina General)";
        String passwordTemporal = "Temp$Pass1234!";

        emailService.enviarCredencialesIniciales(email, nombre, rol, passwordTemporal);

        Optional<CorreoEnviado> enviadoOpt = emailService.obtenerUltimoCorreoEnviado();
        assertThat(enviadoOpt).isPresent();

        CorreoEnviado enviado = enviadoOpt.get();
        assertThat(enviado.destinatario()).isEqualTo(email);
        assertThat(enviado.asunto()).contains("credenciales de acceso institucional");
        assertThat(enviado.cuerpoHtml())
                .contains("Dra. Sofía Restrepo")
                .contains("Profesional Asistencial (Medicina General)")
                .contains("medico.experto@hospital.com.co")
                .contains("Temp$Pass1234!")
                .contains("http://localhost:5500/#/login")
                .contains("Aviso obligatorio de seguridad");

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Resiliencia: si el servidor SMTP falla, no se lanza excepción y el correo se conserva en buffer")
    void resilienciaAnteFalloSmtp() {
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("Error conectando con Brevo SMTP"))
                .when(javaMailSender).send(any(MimeMessage.class));

        emailService.enviarCredencialesIniciales("medico@test.com", "Dr. Pérez", "Médico", "Pass#12345");

        Optional<CorreoEnviado> enviadoOpt = emailService.obtenerUltimoCorreoEnviado();
        assertThat(enviadoOpt).isPresent();
        assertThat(enviadoOpt.get().destinatario()).isEqualTo("medico@test.com");
    }

    @Test
    @DisplayName("Debe usar 'Usuario' por defecto si el nombre es nulo o vacío")
    void debeUsarNombrePorDefecto() {
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        emailService.enviarCodigoRecuperacion("user@ejemplo.com", "   ", "123456", 15);

        Optional<CorreoEnviado> enviadoOpt = emailService.obtenerUltimoCorreoEnviado();
        assertThat(enviadoOpt).isPresent();
        assertThat(enviadoOpt.get().cuerpoHtml()).contains("Hola, Usuario:");
    }

    @Test
    @DisplayName("enviarCorreoHtml almacena el correo renderizado en el buffer")
    void debeEnviarCorreoHtmlGenerico() {
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        String email = "paciente@example.com";
        String asunto = "Prueba de notificación";
        String cuerpo = "<html><body>Hola</body></html>";

        emailService.enviarCorreoHtml(email, asunto, cuerpo);

        Optional<CorreoEnviado> enviadoOpt = emailService.obtenerUltimoCorreoEnviado();
        assertThat(enviadoOpt).isPresent();
        assertThat(enviadoOpt.get().destinatario()).isEqualTo(email);
        assertThat(enviadoOpt.get().asunto()).isEqualTo(asunto);
        assertThat(enviadoOpt.get().cuerpoHtml()).isEqualTo(cuerpo);
    }

    @Test
    @DisplayName("Las plantillas HTML de confirmación, cancelación y resumen cargan correctamente")
    void plantillasHtml_carganCorrectamente() {
        String htmlConfirmacion = templateService.renderizar("templates/email/confirmacion-cita.html", java.util.Map.of("pacienteNombre", "Ana"));
        assertThat(htmlConfirmacion).contains("Ana").contains("Confirmación de Cita Médica");

        String htmlCancelacion = templateService.renderizar("templates/email/cancelacion-cita.html", java.util.Map.of("pacienteNombre", "Ana"));
        assertThat(htmlCancelacion).contains("Ana").contains("Cancelación de Cita Médica");

        String htmlResumen = templateService.renderizar("templates/email/resumen-atencion-seguimiento.html", java.util.Map.of("pacienteNombre", "Ana"));
        assertThat(htmlResumen).contains("Ana").contains("Resumen de Atención");

        String htmlBienvenida = templateService.renderizar("templates/email/bienvenida-credenciales.html", java.util.Map.of(
                "nombre", "Dr. Mario",
                "rol", "Pediatra",
                "email", "mario@hospital.com",
                "passwordTemporal", "Pass#12345",
                "enlaceLogin", "http://localhost:5500/#/login"
        ));
        assertThat(htmlBienvenida).contains("Dr. Mario").contains("Pediatra").contains("Pass#12345");
    }
}
