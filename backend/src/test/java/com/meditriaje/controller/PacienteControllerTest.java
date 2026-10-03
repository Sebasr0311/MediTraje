package com.meditriaje.controller;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.PacientePerfilResponse;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.CsrfHeaderFilter;
import com.meditriaje.security.CustomAccessDeniedHandler;
import com.meditriaje.security.CustomAuthenticationEntryPoint;
import com.meditriaje.security.JwtAuthenticationFilter;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.PacienteService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

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
}
