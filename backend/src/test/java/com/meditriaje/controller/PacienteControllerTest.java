package com.meditriaje.controller;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.PacientePerfilResponse;
import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.service.PacienteService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PacienteController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class PacienteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PacienteService pacienteService;

    @MockBean
    private ClinicalAttentionService clinicalAttentionService;

    @MockBean
    private JwtService jwtService;

    @Test
    void getMe_sinToken_retorna401NoAutenticado() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value("Debe iniciar sesion para acceder a este recurso."));
    }

    @Test
    void getMe_conTokenInvalidoOExpirado_retorna401NoAutenticado() throws Exception {
        when(jwtService.esValido("token.invalido")).thenReturn(false);

        mockMvc.perform(get("/api/v1/patients/me")
                        .cookie(new Cookie("access_token", "token.invalido")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void getMe_conRolIncorrecto_retorna403AccesoDenegado() throws Exception {
        String tokenProf = "valid.token.profesional";
        when(jwtService.esValido(tokenProf)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenProf)).thenReturn("uuid-prof-1");
        when(jwtService.extraerRoles(tokenProf)).thenReturn(List.of("ROLE_PROFESIONAL"));

        mockMvc.perform(get("/api/v1/patients/me")
                        .cookie(new Cookie("access_token", tokenProf)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.mensaje").value("No tiene permisos para acceder a este recurso."));
    }

    @Test
    void getMe_conRolPacienteValido_retorna200YPerfil() throws Exception {
        String tokenPaciente = "valid.token.paciente";
        when(jwtService.esValido(tokenPaciente)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenPaciente)).thenReturn("uuid-user-paciente");
        when(jwtService.extraerRoles(tokenPaciente)).thenReturn(List.of("ROLE_PACIENTE"));

        PacientePerfilResponse mockPerfil = new PacientePerfilResponse(
                "uuid-paciente-1",
                "CC",
                "1020304050",
                "Carlos",
                "Perez",
                LocalDate.of(1995, 5, 20),
                "3001234567",
                "carlos.perez@example.com"
        );
        when(pacienteService.obtenerMiPerfil("uuid-user-paciente")).thenReturn(mockPerfil);

        mockMvc.perform(get("/api/v1/patients/me")
                        .cookie(new Cookie("access_token", tokenPaciente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("uuid-paciente-1"))
                .andExpect(jsonPath("$.tipoDocumento").value("CC"))
                .andExpect(jsonPath("$.numeroDocumento").value("1020304050"))
                .andExpect(jsonPath("$.nombres").value("Carlos"))
                .andExpect(jsonPath("$.apellidos").value("Perez"))
                .andExpect(jsonPath("$.email").value("carlos.perez@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/history - Paciente autenticado: 200 OK con historial paginado")
    void getMiHistoria_pacienteValido_retorna200YPagina() throws Exception {
        String tokenPaciente = "valid.token.paciente";
        when(jwtService.esValido(tokenPaciente)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenPaciente)).thenReturn("uuid-user-paciente");
        when(jwtService.extraerRoles(tokenPaciente)).thenReturn(List.of("ROLE_PACIENTE"));

        AtencionResponse mockAtencion = new AtencionResponse(
                "atencion-uuid-1", "cita-uuid-1", "uuid-paciente-1", "Carlos Perez",
                "prof-uuid-1", "Dr. Gomez", "Medicina General", "CERRADA",
                Instant.now().minusSeconds(3600), Instant.now(), "J00", "Rinofaringitis aguda",
                "Congestión nasal", "Cuadro viral", "Reposo", null
        );
        PaginatedResponse<AtencionResponse> paginatedResponse = PaginatedResponse.of(List.of(mockAtencion), 0, 10, 1L);

        when(clinicalAttentionService.obtenerMiHistoriaClinica(eq("uuid-user-paciente"), eq(0), eq(10), anyString()))
                .thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/v1/patients/me/history")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", tokenPaciente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("atencion-uuid-1"))
                .andExpect(jsonPath("$.content[0].estado").value("CERRADA"))
                .andExpect(jsonPath("$.content[0].diagnosticoCodigo").value("J00"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/history - Profesional: 403 Forbidden")
    void getMiHistoria_profesional_retorna403() throws Exception {
        String tokenProf = "valid.token.profesional";
        when(jwtService.esValido(tokenProf)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenProf)).thenReturn("uuid-prof-1");
        when(jwtService.extraerRoles(tokenProf)).thenReturn(List.of("ROLE_PROFESIONAL"));

        mockMvc.perform(get("/api/v1/patients/me/history")
                        .cookie(new Cookie("access_token", tokenProf)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/history - Administrador: 403 Forbidden (Admin bloqueado de contenido clínico)")
    void getMiHistoria_admin_retorna403() throws Exception {
        String tokenAdmin = "valid.token.admin";
        when(jwtService.esValido(tokenAdmin)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenAdmin)).thenReturn("uuid-admin-1");
        when(jwtService.extraerRoles(tokenAdmin)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        mockMvc.perform(get("/api/v1/patients/me/history")
                        .cookie(new Cookie("access_token", tokenAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/history - Sin autenticación: 401 Unauthorized")
    void getMiHistoria_sinAuth_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me/history"))
                .andExpect(status().isUnauthorized());
    }
}
