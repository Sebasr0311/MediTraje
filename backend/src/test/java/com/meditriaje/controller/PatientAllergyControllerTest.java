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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientAllergyController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class PatientAllergyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AllergyService allergyService;

    @MockBean
    private JwtService jwtService;

    private final String tokenPaciente = "token.paciente.valido";
    private final String tokenProfesional = "token.profesional.valido";
    private final String tokenAdmin = "token.admin.valido";

    private void mockAuth(String token, String publicId, String role) {
        when(jwtService.esValido(token)).thenReturn(true);
        when(jwtService.extraerPublicId(token)).thenReturn(publicId);
        when(jwtService.extraerRoles(token)).thenReturn(List.of(role));
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/allergies: 200 OK para ROLE_PACIENTE")
    void listarMisAlergias_paciente_200Ok() throws Exception {
        mockAuth(tokenPaciente, "u-pac-1", "ROLE_PACIENTE");

        AlergiaResponse resp = new AlergiaResponse(
                "ale-p1", "pac-1", "Maní", "Prurito", "LEVE", "ACTIVA", "PACIENTE", true, null, Instant.now(), null, null
        );
        when(allergyService.listarMisAlergias(eq("u-pac-1"), anyString())).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/v1/patients/me/allergies")
                        .cookie(new Cookie("access_token", tokenPaciente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicId").value("ale-p1"))
                .andExpect(jsonPath("$[0].sustancia").value("Maní"))
                .andExpect(jsonPath("$[0].autorreportada").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/patients/me/allergies: 201 Created para ROLE_PACIENTE con cabecera Location")
    void registrarMiAlergia_paciente_201Created() throws Exception {
        mockAuth(tokenPaciente, "u-pac-1", "ROLE_PACIENTE");

        RegistrarAlergiaRequest req = new RegistrarAlergiaRequest("Mariscos", "Urticaria", "MODERADA");
        AlergiaResponse resp = new AlergiaResponse(
                "ale-mar-1", "pac-1", "Mariscos", "Urticaria", "MODERADA", "ACTIVA", "PACIENTE", true, null, Instant.now(), null, null
        );
        when(allergyService.registrarMiAlergia(any(RegistrarAlergiaRequest.class), eq("u-pac-1"), anyString()))
                .thenReturn(resp);

        mockMvc.perform(post("/api/v1/patients/me/allergies")
                        .cookie(new Cookie("access_token", tokenPaciente))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/patients/me/allergies/ale-mar-1"))
                .andExpect(jsonPath("$.publicId").value("ale-mar-1"))
                .andExpect(jsonPath("$.sustancia").value("Mariscos"))
                .andExpect(jsonPath("$.autorreportada").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/patients/me/allergies/{id}/deactivate: 200 OK para ROLE_PACIENTE")
    void inactivarMiAlergia_paciente_200Ok() throws Exception {
        mockAuth(tokenPaciente, "u-pac-1", "ROLE_PACIENTE");

        InactivarAlergiaRequest req = new InactivarAlergiaRequest("Prueba médica posterior negativa");
        AlergiaResponse resp = new AlergiaResponse(
                "ale-mar-1", "pac-1", "Mariscos", "Urticaria", "MODERADA", "INACTIVA", "PACIENTE", true, null, Instant.now(), Instant.now(), "Prueba médica posterior negativa"
        );
        when(allergyService.inactivarMiAlergia(eq("ale-mar-1"), any(InactivarAlergiaRequest.class), eq("u-pac-1"), anyString()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/v1/patients/me/allergies/ale-mar-1/deactivate")
                        .cookie(new Cookie("access_token", tokenPaciente))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVA"))
                .andExpect(jsonPath("$.motivoInactivacion").value("Prueba médica posterior negativa"));
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/allergies: 403 Forbidden para ROLE_PROFESIONAL y ROLE_ADMINISTRADOR")
    void listarMisAlergias_otrosRoles_403Forbidden() throws Exception {
        mockAuth(tokenProfesional, "u-prof-1", "ROLE_PROFESIONAL");
        mockMvc.perform(get("/api/v1/patients/me/allergies")
                        .cookie(new Cookie("access_token", tokenProfesional)))
                .andExpect(status().isForbidden());

        mockAuth(tokenAdmin, "u-admin-1", "ROLE_ADMINISTRADOR");
        mockMvc.perform(get("/api/v1/patients/me/allergies")
                        .cookie(new Cookie("access_token", tokenAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/allergies: 401 Unauthorized sin token")
    void listarMisAlergias_sinAuth_401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me/allergies"))
                .andExpect(status().isUnauthorized());
    }
}
