package com.meditriaje.service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EmailServiceTest {

    private EmailTemplateService templateService;
    private DefaultEmailService emailService;

    @BeforeEach
    void setUp() {
        templateService = new EmailTemplateService(new DefaultResourceLoader());
        emailService = new DefaultEmailService(templateService);
    }

    @Test
    @DisplayName("Debe renderizar la plantilla HTML y registrar el correo en el buffer")
    void debeEnviarCorreoRecuperacionConPlantillaHtml() {
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
    }

    @Test
    @DisplayName("Debe usar 'Usuario' por defecto si el nombre es nulo o vacío")
    void debeUsarNombrePorDefecto() {
        emailService.enviarCodigoRecuperacion("user@ejemplo.com", "   ", "123456", 15);

        Optional<CorreoEnviado> enviadoOpt = emailService.obtenerUltimoCorreoEnviado();
        assertThat(enviadoOpt).isPresent();
        assertThat(enviadoOpt.get().cuerpoHtml()).contains("Hola, Usuario:");
    }

    @Test
    @DisplayName("enviarCorreoHtml almacena el correo renderizado en el buffer")
    void debeEnviarCorreoHtmlGenerico() {
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
    }
}
