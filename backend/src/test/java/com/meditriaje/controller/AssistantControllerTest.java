package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.assistant.PreguntaAsistenteRequest;
import com.meditriaje.dto.assistant.RespuestaAsistenteResponse;
import com.meditriaje.dto.assistant.SugerenciaAccionDto;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AssistantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AssistantController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AssistantService assistantService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /chat con orientación válida retorna 200 OK y contenido guiado")
    void chat_conPreguntaValida_retorna200Ok() throws Exception {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("¿Cómo agendo una cita médica?");
        RespuestaAsistenteResponse resp = new RespuestaAsistenteResponse(
                "Para agendar una cita médica en MediTriaje 2.0, ingresa a la sección Agendar Cita.",
                false,
                "CITAS",
                List.of(new SugerenciaAccionDto("Agendar Cita", "#/patient/book", "calendar")),
                AssistantService.AVISO_LEGAL
        );

        when(assistantService.procesarConsulta(any(PreguntaAsistenteRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esEmergencia").value(false))
                .andExpect(jsonPath("$.categoria").value("CITAS"))
                .andExpect(jsonPath("$.respuesta").value("Para agendar una cita médica en MediTriaje 2.0, ingresa a la sección Agendar Cita."))
                .andExpect(jsonPath("$.sugerencias[0].etiqueta").value("Agendar Cita"))
                .andExpect(jsonPath("$.avisoLegal").value(AssistantService.AVISO_LEGAL));
    }

    @Test
    @DisplayName("POST /chat con alarma médica retorna 200 OK con alerta de emergencia y llamada al 123")
    void chat_conSintomaAlarma_retorna200OkConEmergencia() throws Exception {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("Tengo un fuerte dolor de pecho y me falta el aire");
        RespuestaAsistenteResponse resp = new RespuestaAsistenteResponse(
                "⚠️ ALERTA DE EMERGENCIA MÉDICA: Por favor comunícate de inmediato al 123.",
                true,
                "EMERGENCIA",
                List.of(new SugerenciaAccionDto("Llamar al 123", "tel:123", "phone")),
                AssistantService.AVISO_LEGAL
        );

        when(assistantService.procesarConsulta(any(PreguntaAsistenteRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esEmergencia").value(true))
                .andExpect(jsonPath("$.categoria").value("EMERGENCIA"))
                .andExpect(jsonPath("$.sugerencias[0].rutaSpa").value("tel:123"));
    }

    @Test
    @DisplayName("POST /chat sin mensaje o mensaje en blanco retorna 400 Bad Request")
    void chat_conMensajeBlanco_retorna400BadRequest() throws Exception {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("   ");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    @DisplayName("POST /chat sin autenticación previa es permitido (acceso universal de orientación)")
    void chat_sinSesion_esPermitido() throws Exception {
        PreguntaAsistenteRequest req = new PreguntaAsistenteRequest("¿Qué es el triaje?");
        RespuestaAsistenteResponse resp = new RespuestaAsistenteResponse(
                "El triaje clasifica la urgencia clínica.",
                false,
                "TRIAJE",
                List.of(),
                AssistantService.AVISO_LEGAL
        );

        when(assistantService.procesarConsulta(any(PreguntaAsistenteRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("TRIAJE"));
    }
}
