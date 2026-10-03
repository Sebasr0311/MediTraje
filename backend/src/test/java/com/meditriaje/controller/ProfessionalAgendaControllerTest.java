package com.meditriaje.controller;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AppointmentService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProfessionalAgendaController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class ProfessionalAgendaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppointmentService appointmentService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String TOKEN_ADMIN = "token.admin.valido";

    private static final String PACIENTE_UUID = "paciente-usr-uuid";
    private static final String PROFESIONAL_UUID = "profesional-usr-uuid";
    private static final String ADMIN_UUID = "admin-usr-uuid";

    @BeforeEach
    void setUpTokens() {
        when(jwtService.esValido(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PACIENTE)).thenReturn(PACIENTE_UUID);
        when(jwtService.extraerRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.esValido(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PROFESIONAL)).thenReturn(PROFESIONAL_UUID);
        when(jwtService.extraerRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));

        when(jwtService.esValido(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ADMIN)).thenReturn(ADMIN_UUID);
        when(jwtService.extraerRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
    }

    // =========================================================================
    // CONTROL DE ACCESO Y AUTORIZACIÓN (HU-06, ADR-002, ADR-007)
    // =========================================================================

    @Test
    void getAgenda_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/professionals/me/agenda")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void getAgenda_conRolPaciente_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/professionals/me/agenda")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void getAgenda_conRolAdministrador_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/professionals/me/agenda")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    // =========================================================================
    // CONSULTA EXITOSA DE AGENDA (HU-06)
    // =========================================================================

    @Test
    void getAgenda_conRolProfesional_retorna200OkConAgenda() throws Exception {
        CitaResponse cita = new CitaResponse(
                "cita-100", "slot-100", "pac-100", "Carlos Gomez",
                "prof-100", "Dr. Carlos Mendoza", "esp-100", "Medicina General",
                "sede-100", "Sede Centro", "Calle 16",
                Instant.parse("2026-10-15T13:00:00Z"), Instant.parse("2026-10-15T13:20:00Z"),
                "PRESENCIAL", "PROGRAMADA", null, null, Instant.now()
        );

        PaginatedResponse<CitaResponse> mockPage = PaginatedResponse.of(List.of(cita), 0, 10, 1L);

        when(appointmentService.obtenerMiAgenda(eq(PROFESIONAL_UUID), any(), any(), eq(0), eq(10)))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/v1/professionals/me/agenda")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("cita-100"))
                .andExpect(jsonPath("$.content[0].pacienteNombre").value("Carlos Gomez"))
                .andExpect(jsonPath("$.content[0].profesionalNombre").value("Dr. Carlos Mendoza"))
                .andExpect(jsonPath("$.content[0].estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAgenda_conFiltrosFechaYEstado_pasaParametrosAlServicio() throws Exception {
        PaginatedResponse<CitaResponse> mockPage = PaginatedResponse.of(List.of(), 1, 20, 0L);

        when(appointmentService.obtenerMiAgenda(
                eq(PROFESIONAL_UUID),
                eq(LocalDate.of(2026, 10, 15)),
                eq("CONFIRMADA"),
                eq(1),
                eq(20)
        )).thenReturn(mockPage);

        mockMvc.perform(get("/api/v1/professionals/me/agenda")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .param("fecha", "2026-10-15")
                        .param("estado", "CONFIRMADA")
                        .param("page", "1")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20));

        verify(appointmentService).obtenerMiAgenda(
                eq(PROFESIONAL_UUID),
                eq(LocalDate.of(2026, 10, 15)),
                eq("CONFIRMADA"),
                eq(1),
                eq(20)
        );
    }
}
