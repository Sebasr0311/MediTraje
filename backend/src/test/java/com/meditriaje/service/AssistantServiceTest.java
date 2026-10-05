package com.meditriaje.service;

import com.meditriaje.dto.assistant.PreguntaAsistenteRequest;
import com.meditriaje.dto.assistant.RespuestaAsistenteResponse;
import com.meditriaje.exception.DatosInvalidosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantServiceTest {

    private AssistantService assistantService;

    @BeforeEach
    void setUp() {
        assistantService = new AssistantService();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Siento un dolor de pecho opresivo que se va al brazo",
            "Mi hermano no puede respirar y tiene mucho ahogo",
            "El paciente está inconsciente en el piso",
            "Presenta convulsiones continuas",
            "Hay un sangrado abundante incontrolable",
            "Posible acv con boca torcida y perdida de fuerza"
    })
    @DisplayName("Detección infalible de emergencias: activa alerta inmediata 123")
    void procesarConsulta_emergencia_activaAlerta(String mensaje) {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest(mensaje, null);

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.esEmergencia()).isTrue();
        assertThat(res.categoria()).isEqualTo("EMERGENCIA");
        assertThat(res.respuesta()).contains("123");
        assertThat(res.respuesta()).contains("urgencias");
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("tel:123"));
        assertThat(res.avisoLegal()).isNotBlank();
    }

    @Test
    @DisplayName("Consulta sobre triaje: explica los 5 niveles y ofrece enlace de triaje")
    void procesarConsulta_triaje_exito() {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("¿Cómo funciona el triaje y qué es el nivel II?", null);

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.esEmergencia()).isFalse();
        assertThat(res.categoria()).isEqualTo("TRIAJE");
        assertThat(res.respuesta()).contains("Nivel I");
        assertThat(res.respuesta()).contains("Nivel V");
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/patient/triage"));
    }

    @Test
    @DisplayName("Consulta sobre citas: explica regla de 2 horas y ofrece disponibilidad")
    void procesarConsulta_citas_exito() {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("Quiero agendar o cancelar una cita", null);

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.esEmergencia()).isFalse();
        assertThat(res.categoria()).isEqualTo("CITAS");
        assertThat(res.respuesta()).contains("2 horas de anticipación");
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/patient/book"));
    }

    @Test
    @DisplayName("Consulta sobre farmacia: explica código de reclamación y saldos")
    void procesarConsulta_farmacia_exito() {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("¿Cómo puedo reclamar mi medicamento con la receta?", null);

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.esEmergencia()).isFalse();
        assertThat(res.categoria()).isEqualTo("FARMACIA");
        assertThat(res.respuesta()).contains("REC-");
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/patient/prescriptions"));
    }

    @Test
    @DisplayName("Consulta sobre QR de emergencia: explica token de 15 minutos y PIN")
    void procesarConsulta_resumenQr_exito() {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("¿Cómo funciona el código QR de emergencia?", null);

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.esEmergencia()).isFalse();
        assertThat(res.categoria()).isEqualTo("RESUMEN_QR");
        assertThat(res.respuesta()).contains("15 minutos");
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/patient/emergency-qr"));
    }

    @Test
    @DisplayName("Consulta sobre historia clínica: explica inmutabilidad y enmiendas")
    void procesarConsulta_historia_exito() {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("¿Por qué mi historia clínica es inmutable y cómo pedir una corrección?", null);

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.esEmergencia()).isFalse();
        assertThat(res.categoria()).isEqualTo("HISTORIA_CLINICA");
        assertThat(res.respuesta()).contains("enmiendas");
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/patient/history"));
    }

    @Test
    @DisplayName("Consulta general: responde con orientación cordial y menú de opciones")
    void procesarConsulta_general_exito() {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("Hola, buenos días", null);

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.esEmergencia()).isFalse();
        assertThat(res.categoria()).isEqualTo("GENERAL");
        assertThat(res.sugerencias()).isNotEmpty();
    }

    @Test
    @DisplayName("Validación: mensaje vacío o nulo lanza DatosInvalidosException")
    void procesarConsulta_mensajeVacio_lanzaExcepcion() {
        assertThatThrownBy(() -> assistantService.procesarConsulta(new PreguntaAsistenteRequest("   ", null)))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("El mensaje de consulta no puede estar vacío.");
    }

    @Test
    @DisplayName("Modo Invitado (GUEST): consulta sobre citas indica iniciar sesión y ofrece login/registro")
    void procesarConsulta_guest_citasRequiereSesion() {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("¿Cómo agendar una cita?", "GUEST");

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.categoria()).isEqualTo("CITAS_GUEST");
        assertThat(res.respuesta()).contains("iniciar sesión");
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/login"));
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/register"));
    }

    @Test
    @DisplayName("Modo Invitado (GUEST): consulta general explica plataforma y ofrece login/registro")
    void procesarConsulta_guest_general() {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("¿Qué es MediTriaje?", "GUEST");

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.categoria()).isEqualTo("GENERAL_GUEST");
        assertThat(res.respuesta()).contains("MediTriaje 2.0");
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/login"));
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/register"));
    }

    @Test
    @DisplayName("Modo Administrador: consulta sobre reportes ofrece enlace a reportes operativos")
    void procesarConsulta_admin_reportes() {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("¿Cómo consultar los reportes operativos?", "ROLE_ADMINISTRADOR");

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.categoria()).isEqualTo("REPORTES_ADMIN");
        assertThat(res.respuesta()).contains("Reportes Operativos");
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/admin/reports"));
    }

    @Test
    @DisplayName("Modo Administrador: consulta sobre profesionales explica ReTHUS y ofrece gestión de profesionales")
    void procesarConsulta_admin_profesionales() {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("¿Cómo dar de alta a un médico?", "ROLE_ADMINISTRADOR");

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.categoria()).isEqualTo("PROFESIONALES_ADMIN");
        assertThat(res.respuesta()).contains("ReTHUS");
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/admin/professionals"));
    }

    @Test
    @DisplayName("Modo Administrador: consulta sobre citas orienta al rol de gestión de slots sin enlaces de paciente")
    void procesarConsulta_admin_citas() {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("Quiero ver las citas", "ROLE_ADMINISTRADOR");

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.categoria()).isEqualTo("ADMIN_ORIENTACION");
        assertThat(res.respuesta()).contains("Como Administrador");
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/admin/slots"));
        assertThat(res.sugerencias()).noneMatch(s -> s.rutaSpa().startsWith("#/patient/"));
    }

    @Test
    @DisplayName("Modo Profesional: consulta sobre agenda ofrece enlace a agenda médica")
    void procesarConsulta_profesional_agenda() {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("¿Cómo ver mi agenda de citas?", "ROLE_PROFESIONAL");

        RespuestaAsistenteResponse res = assistantService.procesarConsulta(req);

        assertThat(res.categoria()).isEqualTo("AGENDA_PROFESIONAL");
        assertThat(res.respuesta()).contains("Agenda Médica");
        assertThat(res.sugerencias()).anyMatch(s -> s.rutaSpa().equals("#/professional/agenda"));
    }
}
