package com.meditriaje.controller;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.ClinicalAttentionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClinicalPatientHistoryController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class ClinicalPatientHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClinicalAttentionService clinicalAttentionService;

    @MockBean
    private JwtService jwtService;

    private final String tokenProfesional = "token.profesional.valido";
    private final String tokenPaciente = "token.paciente.valido";
    private final String tokenAdmin = "token.admin.valido";

    private void mockAuth(String token, String publicId, String role) {
        when(jwtService.esValido(token)).thenReturn(true);
        when(jwtService.extraerPublicId(token)).thenReturn(publicId);
        when(jwtService.extraerRoles(token)).thenReturn(List.of(role));
    }

    @Test
    @DisplayName("GET /clinical/patients/{id}/history: 200 OK para ROLE_PROFESIONAL con relación o break-glass")
    void obtenerHistoriaPaciente_profesional_200Ok() throws Exception {
        mockAuth(tokenProfesional, "u-prof-1", "ROLE_PROFESIONAL");

        Instant ahora = Instant.now();
        AtencionResponse atencion = new AtencionResponse(
                "atn-1",
                "cita-1",
                "pac-1234",
                "Carlos Sanchez",
                "prof-1",
                "Dr. Roberto Gomez",
                "Medicina General",
                "CERRADA",
                ahora,
                ahora,
                "J00",
                "Rinofaringitis aguda",
                "Dolor toracico",
                "Evolucion normal",
                "Reposo",
                null,
                List.of()
        );
        PaginatedResponse<AtencionResponse> paginado = PaginatedResponse.of(List.of(atencion), 0, 10, 1);

        when(clinicalAttentionService.obtenerHistoriaClinicaPaciente(
                eq("pac-1234"),
                eq("u-prof-1"),
                any(),
                eq(0),
                eq(10),
                anyString()
        )).thenReturn(paginado);

        mockMvc.perform(get("/api/v1/clinical/patients/pac-1234/history")
                        .cookie(new Cookie("access_token", tokenProfesional)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("atn-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /clinical/patients/{id}/history: 403 Forbidden cuando el profesional no tiene relación ni break-glass")
    void obtenerHistoriaPaciente_sinRelacionNiBreakGlass_403Forbidden() throws Exception {
        mockAuth(tokenProfesional, "u-prof-1", "ROLE_PROFESIONAL");

        when(clinicalAttentionService.obtenerHistoriaClinicaPaciente(
                eq("pac-1234"),
                eq("u-prof-1"),
                any(),
                anyInt(),
                anyInt(),
                anyString()
        )).thenThrow(new AccesoNoAutorizadoException("No existe una relacion asistencial activa con el paciente."));

        mockMvc.perform(get("/api/v1/clinical/patients/pac-1234/history")
                        .cookie(new Cookie("access_token", tokenProfesional)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No existe una relacion asistencial activa con el paciente."));
    }

    @Test
    @DisplayName("GET /clinical/patients/{id}/history: 403 Forbidden para ROLE_PACIENTE")
    void obtenerHistoriaPaciente_paciente_403Forbidden() throws Exception {
        mockAuth(tokenPaciente, "u-pac-1", "ROLE_PACIENTE");

        mockMvc.perform(get("/api/v1/clinical/patients/pac-1234/history")
                        .cookie(new Cookie("access_token", tokenPaciente)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /clinical/patients/{id}/history: 403 Forbidden para ROLE_ADMINISTRADOR")
    void obtenerHistoriaPaciente_admin_403Forbidden() throws Exception {
        mockAuth(tokenAdmin, "u-admin-1", "ROLE_ADMINISTRADOR");

        mockMvc.perform(get("/api/v1/clinical/patients/pac-1234/history")
                        .cookie(new Cookie("access_token", tokenAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /clinical/patients/{id}/history: 401 Unauthorized sin sesión")
    void obtenerHistoriaPaciente_sinSesion_401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/clinical/patients/pac-1234/history"))
                .andExpect(status().isUnauthorized());
    }
}
