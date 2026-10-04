package com.meditriaje.controller;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.clinical.DiagnosticoCie10Response;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.repository.DiagnosticoCie10Repository;
import com.meditriaje.security.JwtService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = Cie10CatalogController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class Cie10CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DiagnosticoCie10Repository diagnosticoCie10Repository;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";

    @BeforeEach
    void setUpTokens() {
        when(jwtService.esValido(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PROFESIONAL)).thenReturn("usr-prof-1");
        when(jwtService.extraerRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));
    }

    @Test
    @DisplayName("GET /api/v1/catalogs/icd10 - Usuario autenticado: 200 OK")
    void listarDiagnosticos_autenticado_retorna200Ok() throws Exception {
        when(diagnosticoCie10Repository.listarActivos(any())).thenReturn(List.of(
                new DiagnosticoCie10Response("J00", "Rinofaringitis aguda (resfriado comun)"),
                new DiagnosticoCie10Response("J459", "Asma, no especificada")
        ));

        mockMvc.perform(get("/api/v1/catalogs/icd10")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigo").value("J00"))
                .andExpect(jsonPath("$[1].codigo").value("J459"));
    }

    @Test
    @DisplayName("GET /api/v1/catalogs/icd10 - Sin autenticación: 401 Unauthorized")
    void listarDiagnosticos_sinAuth_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/catalogs/icd10")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }
}
