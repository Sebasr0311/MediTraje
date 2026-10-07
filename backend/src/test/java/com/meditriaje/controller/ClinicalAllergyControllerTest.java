package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.allergy.AlergiaResponse;
import com.meditriaje.dto.allergy.InactivarAlergiaRequest;
import com.meditriaje.dto.allergy.RegistrarAlergiaRequest;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AllergyService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClinicalAllergyController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class ClinicalAllergyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AllergyService allergyService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_ADMIN = "token.admin.valido";

    @BeforeEach
    void setUpTokens() {
        when(jwtService.esValido(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PROFESIONAL)).thenReturn("u-prof-1");
        when(jwtService.extraerRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));

        when(jwtService.esValido(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PACIENTE)).thenReturn("u-pac-1");
        when(jwtService.extraerRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.esValido(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ADMIN)).thenReturn("u-admin-1");
        when(jwtService.extraerRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
    }

    @Test
    @DisplayName("GET /api/v1/clinical/patients/{id}/allergies: 200 OK para ROLE_PROFESIONAL")
    void listarAlergias_profesional_200Ok() throws Exception {
        AlergiaResponse resp = new AlergiaResponse(
                "ale-1", "pac-1", "Penicilina", "Edema", "GRAVE", "ACTIVA", "PROFESIONAL", false, null, Instant.now(), null, null
        );
        when(allergyService.listarAlergiasPacienteParaProfesional(eq("pac-1"), anyBoolean(), eq("u-prof-1"), any(), anyString()))
                .thenReturn(List.of(resp));

        mockMvc.perform(get("/api/v1/clinical/patients/pac-1/allergies")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicId").value("ale-1"))
                .andExpect(jsonPath("$[0].sustancia").value("Penicilina"))
                .andExpect(jsonPath("$[0].severidad").value("GRAVE"));
    }

    @Test
    @DisplayName("POST /api/v1/clinical/patients/{id}/allergies: 201 Created para ROLE_PROFESIONAL con cabecera Location")
    void registrarAlergia_profesional_201Created() throws Exception {
        RegistrarAlergiaRequest req = new RegistrarAlergiaRequest("Dipirona", "Hipotension", "GRAVE");
        AlergiaResponse resp = new AlergiaResponse(
                "ale-dip-1", "pac-1", "Dipirona", "Hipotension", "GRAVE", "ACTIVA", "PROFESIONAL", false, null, Instant.now(), null, null
        );
        when(allergyService.registrarAlergiaPorProfesional(eq("pac-1"), any(RegistrarAlergiaRequest.class), eq("u-prof-1"), anyString()))
                .thenReturn(resp);

        mockMvc.perform(post("/api/v1/clinical/patients/pac-1/allergies")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/clinical/allergies/ale-dip-1"))
                .andExpect(jsonPath("$.publicId").value("ale-dip-1"))
                .andExpect(jsonPath("$.sustancia").value("Dipirona"));
    }

    @Test
    @DisplayName("PATCH /api/v1/clinical/allergies/{id}/deactivate: 200 OK para ROLE_PROFESIONAL")
    void inactivarAlergia_profesional_200Ok() throws Exception {
        InactivarAlergiaRequest req = new InactivarAlergiaRequest("Prueba de tolerancia negativa");
        AlergiaResponse resp = new AlergiaResponse(
                "ale-dip-1", "pac-1", "Dipirona", "Hipotension", "GRAVE", "INACTIVA", "PROFESIONAL", false, null, Instant.now(), Instant.now(), "Prueba de tolerancia negativa"
        );
        when(allergyService.inactivarAlergiaPorProfesional(eq("ale-dip-1"), any(InactivarAlergiaRequest.class), eq("u-prof-1"), anyString()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/v1/clinical/allergies/ale-dip-1/deactivate")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVA"))
                .andExpect(jsonPath("$.motivoInactivacion").value("Prueba de tolerancia negativa"));
    }

    @Test
    @DisplayName("POST /api/v1/clinical/patients/{id}/allergies: 403 Forbidden para ROLE_ADMINISTRADOR (ADR-007)")
    void registrarAlergia_administrador_403Forbidden() throws Exception {
        RegistrarAlergiaRequest req = new RegistrarAlergiaRequest("Dipirona", "Hipotension", "GRAVE");

        mockMvc.perform(post("/api/v1/clinical/patients/pac-1/allergies")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/clinical/patients/{id}/allergies: 403 Forbidden para ROLE_PACIENTE")
    void listarAlergias_paciente_403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/clinical/patients/pac-1/allergies")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/clinical/patients/{id}/allergies: 401 Unauthorized sin token")
    void listarAlergias_sinAuth_401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/clinical/patients/pac-1/allergies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/clinical/patients/{id}/allergies: 400 Bad Request ante severidad o sustancia inválida")
    void registrarAlergia_datosInvalidos_400BadRequest() throws Exception {
        RegistrarAlergiaRequest reqInvalido = new RegistrarAlergiaRequest("A", null, "EXTREMA");

        mockMvc.perform(post("/api/v1/clinical/patients/pac-1/allergies")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqInvalido)))
                .andExpect(status().isBadRequest());
    }
}
