package com.meditriaje.controller;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.prescription.MedicamentoResponse;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.PrescriptionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MedicationCatalogController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class MedicationCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrescriptionService prescriptionService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String TOKEN_ADMIN = "token.admin.valido";

    @BeforeEach
    void setUpTokens() {
        when(jwtService.esValido(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PACIENTE)).thenReturn("usr-paciente-uuid");
        when(jwtService.extraerRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.esValido(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PROFESIONAL)).thenReturn("usr-profesional-uuid");
        when(jwtService.extraerRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));

        when(jwtService.esValido(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ADMIN)).thenReturn("usr-admin-uuid");
        when(jwtService.extraerRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
    }

    private PaginatedResponse<MedicamentoResponse> mockPaginatedResponse() {
        return PaginatedResponse.of(
                List.of(
                        new MedicamentoResponse(
                                "med-uuid-1", "MED-ACE-500", "Acetaminofen", "Acetaminofen", "Tableta", "500 mg", "ACTIVO"
                        ),
                        new MedicamentoResponse(
                                "med-uuid-2", "MED-IBU-400", "Ibuprofeno", "Ibuprofeno", "Tableta", "400 mg", "ACTIVO"
                        )
                ),
                0,
                10,
                2L
        );
    }

    @Test
    @DisplayName("GET /api/v1/catalogs/medications - Paciente: 200 OK")
    void listarMedicamentos_paciente_retorna200Ok() throws Exception {
        when(prescriptionService.listarCatalogo(anyString(), anyInt(), anyInt()))
                .thenReturn(mockPaginatedResponse());

        mockMvc.perform(get("/api/v1/catalogs/medications")
                        .param("q", "aceta")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].codigo").value("MED-ACE-500"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/catalogs/medications - Profesional: 200 OK")
    void listarMedicamentos_profesional_retorna200Ok() throws Exception {
        when(prescriptionService.listarCatalogo(any(), anyInt(), anyInt()))
                .thenReturn(mockPaginatedResponse());

        mockMvc.perform(get("/api/v1/catalogs/medications")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/catalogs/medications - Administrador: 200 OK")
    void listarMedicamentos_admin_retorna200Ok() throws Exception {
        when(prescriptionService.listarCatalogo(any(), anyInt(), anyInt()))
                .thenReturn(mockPaginatedResponse());

        mockMvc.perform(get("/api/v1/catalogs/medications")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/catalogs/medications - Sin autenticación: 401 Unauthorized")
    void listarMedicamentos_sinAuth_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/catalogs/medications")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }
}
