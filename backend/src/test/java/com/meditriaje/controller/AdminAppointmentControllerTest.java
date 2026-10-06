package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.admin.AdminAppointmentController;
import com.meditriaje.dto.admin.AdminCitaDetalleResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AdminAppointmentService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAppointmentController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminAppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAppointmentService adminAppointmentService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_ADMIN = "token.admin.valido";
    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";

    @BeforeEach
    void setUpTokens() {
        when(jwtService.esValido(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ADMIN)).thenReturn("admin-uuid-1");
        when(jwtService.extraerRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        when(jwtService.esValido(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PACIENTE)).thenReturn("paciente-uuid-1");
        when(jwtService.extraerRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.esValido(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extraerRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));
    }

    @Test
    @DisplayName("401 Unauthorized sin autenticación")
    void listarCitas_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/appointments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("403 Forbidden con rol PACIENTE")
    void listarCitas_rolPaciente_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/appointments")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("403 Forbidden con rol PROFESIONAL")
    void listarCitas_rolProfesional_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/appointments")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("200 OK con rol ADMINISTRADOR y respuesta paginada")
    void listarCitas_rolAdmin_retorna200() throws Exception {
        AdminCitaDetalleResponse cita = new AdminCitaDetalleResponse(
                "cita-uuid-1",
                "pac-uuid-1",
                "Juan Pérez",
                "CC",
                "12345678",
                "3001234567",
                "juan@ejemplo.com",
                "prof-uuid-1",
                "Dr. Carlos Mendoza",
                "RM-9999",
                "esp-uuid-1",
                "Medicina General",
                "sede-uuid-1",
                "Sede Principal",
                "Bogotá",
                Instant.now(),
                Instant.now().plusSeconds(1800),
                "PRESENCIAL",
                "PROGRAMADA",
                null,
                "triaje-uuid-1",
                "III",
                "Dolor de cabeza",
                Instant.now()
        );

        when(adminAppointmentService.listarCitas(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PaginatedResponse.of(List.of(cita), 0, 50, 1L));

        mockMvc.perform(get("/api/v1/admin/appointments")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .param("fechaDesde", "2026-10-01")
                        .param("fechaHasta", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].citaPublicId").value("cita-uuid-1"))
                .andExpect(jsonPath("$.content[0].pacienteNombre").value("Juan Pérez"))
                .andExpect(jsonPath("$.content[0].profesionalNombre").value("Dr. Carlos Mendoza"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("200 OK exportar citas en CSV con headers correspondientes")
    void exportarCitas_rolAdmin_retornaCsv() throws Exception {
        byte[] csvBytes = "\uFEFFCódigo Cita;Fecha Cita\r\ncita-1;2026-10-06".getBytes(StandardCharsets.UTF_8);

        when(adminAppointmentService.exportarCitasExcelCsv(any(), any(), any(), any(), any()))
                .thenReturn(csvBytes);

        mockMvc.perform(get("/api/v1/admin/appointments/export")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8"))
                .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(content().bytes(csvBytes));
    }
}
