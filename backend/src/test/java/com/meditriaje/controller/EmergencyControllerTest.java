package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.emergency.EpisodioUrgenciaResponse;
import com.meditriaje.dto.emergency.ItemColaUrgenciaResponse;
import com.meditriaje.dto.emergency.ReconciliarIdentidadRequest;
import com.meditriaje.dto.emergency.RegistrarAdmisionUrgenciaRequest;
import com.meditriaje.dto.emergency.RegistrarValoracionTriajeRequest;
import com.meditriaje.dto.emergency.ValoracionTriajeResponse;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.EmergencyService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmergencyController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class EmergencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmergencyService emergencyService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_ENFERMERIA = "token.enfermeria.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_ADMIN = "token.admin.valido";

    private static final String ENFERMERA_UUID = "usr-enf-uuid";
    private static final String PACIENTE_UUID = "usr-pac-uuid";
    private static final String ADMIN_UUID = "usr-adm-uuid";

    @BeforeEach
    void setUp() {
        when(jwtService.esValido(TOKEN_ENFERMERIA)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ENFERMERIA)).thenReturn(ENFERMERA_UUID);
        when(jwtService.extraerRoles(TOKEN_ENFERMERIA)).thenReturn(List.of("ROLE_ENFERMERIA"));

        when(jwtService.esValido(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PROFESIONAL)).thenReturn("usr-prof-uuid");
        when(jwtService.extraerRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));

        when(jwtService.esValido(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PACIENTE)).thenReturn(PACIENTE_UUID);
        when(jwtService.extraerRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.esValido(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ADMIN)).thenReturn(ADMIN_UUID);
        when(jwtService.extraerRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
    }

    private Cookie cookie(String token) {
        return new Cookie("access_token", token);
    }

    @Test
    @DisplayName("SEC01: Petición anónima a admisiones retorna 401 Unauthorized")
    void registrarAdmision_sinSesion_retorna401() throws Exception {
        RegistrarAdmisionUrgenciaRequest req = new RegistrarAdmisionUrgenciaRequest(
                "sede-pub", null, true, "ESPONTANEO", "Dolor fuerte", null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/emergency/admissions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SEC02: Paciente intentando registrar admisión retorna 403 Forbidden")
    void registrarAdmision_paciente_retorna403() throws Exception {
        RegistrarAdmisionUrgenciaRequest req = new RegistrarAdmisionUrgenciaRequest(
                "sede-pub", null, true, "ESPONTANEO", "Dolor fuerte", null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/emergency/admissions")
                        .cookie(cookie(TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("U02: Enfermería registrando admisión presencial retorna 201 Created y Location")
    void registrarAdmision_enfermeria_retorna201() throws Exception {
        RegistrarAdmisionUrgenciaRequest req = new RegistrarAdmisionUrgenciaRequest(
                "sede-pub", null, true, "AMBULANCIA", "Trauma", null, null, null, null, null, null, null
        );

        EpisodioUrgenciaResponse respMock = new EpisodioUrgenciaResponse(
                "ep-pub-123", null, null, "sede-pub", "Hospital", "URGENCIA",
                "REGISTRADO", true, "NN-20261010-ABCD", "PROVISIONAL", "AMBULANCIA",
                "Trauma", "PENDIENTE_VALORACION", null, Instant.now(), null, Instant.now()
        );
        when(emergencyService.registrarAdmisionUrgencia(any(), eq(ENFERMERA_UUID), anyString()))
                .thenReturn(respMock);

        mockMvc.perform(post("/api/v1/emergency/admissions")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/emergency/admissions/ep-pub-123"))
                .andExpect(jsonPath("$.episodioPublicId").value("ep-pub-123"))
                .andExpect(jsonPath("$.codigoProvisional").value("NN-20261010-ABCD"));
    }

    @Test
    @DisplayName("U04: Enfermería registrando valoración presencial retorna 201 Created")
    void registrarValoracionTriaje_enfermeria_retorna201() throws Exception {
        RegistrarValoracionTriajeRequest req = new RegistrarValoracionTriajeRequest(
                "II", "Dolor precordial", "Diaforesis", "130/80", 95, 20, 96, new BigDecimal("36.8"), 15, false, null
        );

        ValoracionTriajeResponse respMock = new ValoracionTriajeResponse(
                "val-pub-456", "ep-pub-123", 1, "II", "Dolor precordial",
                "Diaforesis", "130/80", 95, 20, 96, new BigDecimal("36.8"), 15,
                "Maria Perez", false, null, Instant.now()
        );
        when(emergencyService.registrarValoracionTriaje(eq("ep-pub-123"), any(), eq(ENFERMERA_UUID), any(), anyString()))
                .thenReturn(respMock);

        mockMvc.perform(post("/api/v1/emergency/episodes/ep-pub-123/triage-assessments")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/emergency/episodes/ep-pub-123/triage-assessments/val-pub-456"))
                .andExpect(jsonPath("$.nivel").value("II"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("SEC03: Administrador intentando registrar triaje clínico presencial recibe 403 Forbidden")
    void registrarValoracionTriaje_admin_retorna403() throws Exception {
        RegistrarValoracionTriajeRequest req = new RegistrarValoracionTriajeRequest(
                "III", "Consulta", null, null, null, null, null, null, null, false, null
        );

        mockMvc.perform(post("/api/v1/emergency/episodes/ep-pub-123/triage-assessments")
                        .cookie(cookie(TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("U05: Consultar cola de urgencias retorna 200 OK con elementos priorizados")
    void obtenerCola_enfermeria_retorna200() throws Exception {
        ItemColaUrgenciaResponse item = new ItemColaUrgenciaResponse(
                "ep-pub-123", null, "NN (NN-20261010-ABCD)", "NN-20261010-ABCD",
                "sede-pub", "Hospital", "URGENCIA", "EN_TRIAJE", "II", "Dolor", 10L, Instant.now(), null
        );
        when(emergencyService.listarColaUrgencias(any(), any())).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/emergency/queue")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].episodioPublicId").value("ep-pub-123"))
                .andExpect(jsonPath("$[0].nivelTriaje").value("II"));
    }

    @Test
    @DisplayName("U03: Reconciliar identidad provisional de paciente NN retorna 200 OK")
    void reconciliarIdentidad_enfermeria_retorna200() throws Exception {
        ReconciliarIdentidadRequest req = new ReconciliarIdentidadRequest("pac-real-pub", "Documento verificado");

        EpisodioUrgenciaResponse respMock = new EpisodioUrgenciaResponse(
                "ep-pub-123", "pac-real-pub", "Juan Gomez", "sede-pub", "Hospital", "URGENCIA",
                "REGISTRADO", true, "NN-20261010-ABCD", "VINCULADA", "AMBULANCIA",
                "Trauma", "PENDIENTE_VALORACION", null, Instant.now(), null, Instant.now()
        );
        when(emergencyService.reconciliarIdentidad(eq("ep-pub-123"), any(), eq(ENFERMERA_UUID), anyString()))
                .thenReturn(respMock);

        mockMvc.perform(post("/api/v1/emergency/episodes/ep-pub-123/reconcile")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identidadEstado").value("VINCULADA"))
                .andExpect(jsonPath("$.pacientePublicId").value("pac-real-pub"));
    }
}
