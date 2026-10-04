package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.admin.AdminReportController;
import com.meditriaje.dto.report.DistribucionItemDto;
import com.meditriaje.dto.report.MetricasBreakGlassDto;
import com.meditriaje.dto.report.MetricasCitasDto;
import com.meditriaje.dto.report.MetricasFarmaciaDto;
import com.meditriaje.dto.report.MetricasTriajeDto;
import com.meditriaje.dto.report.ResumenOperativoResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.ReporteService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminReportController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReporteService reporteService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_ADMIN = "token.admin.valido";
    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String ADMIN_UUID = "admin-uuid-1";

    @BeforeEach
    void setUpTokens() {
        when(jwtService.esValido(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ADMIN)).thenReturn(ADMIN_UUID);
        when(jwtService.extraerRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        when(jwtService.esValido(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PACIENTE)).thenReturn("paciente-uuid-1");
        when(jwtService.extraerRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.esValido(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extraerRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));
    }

    // =========================================================================
    // SEGURIDAD Y AUTORIZACIÓN (ADR-007, ADR-018)
    // =========================================================================

    @Test
    @DisplayName("GET /operational sin autenticación retorna 401 Unauthorized")
    void getOperational_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/operational"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("GET /operational con rol PACIENTE retorna 403 Forbidden")
    void getOperational_conRolPaciente_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/operational")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("GET /operational con rol PROFESIONAL retorna 403 Forbidden")
    void getOperational_conRolProfesional_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/operational")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    // =========================================================================
    // CONSULTA RESUMEN OPERATIVO
    // =========================================================================

    @Test
    @DisplayName("GET /operational con rol ADMIN retorna 200 OK con métricas agregadas")
    void getOperational_conRolAdmin_retorna200Ok() throws Exception {
        ResumenOperativoResponse mockResponse = new ResumenOperativoResponse(
                null,
                null,
                new MetricasCitasDto(100L, 20L, 30L, 40L, 5L, 3L, 2L, 40.0, 8.0,
                        List.of(new DistribucionItemDto("Medicina General", 60L)),
                        List.of(new DistribucionItemDto("Sede Norte", 100L))),
                new MetricasTriajeDto(50L, 5L, 10L, 20L, 10L, 5L, 5L, 10.0),
                new MetricasFarmaciaDto(80L, 10L, 20L, 50L, 240L),
                new MetricasBreakGlassDto(2L, 0L, List.of(new DistribucionItemDto("Urgencias", 2L))),
                Instant.now()
        );

        when(reporteService.obtenerResumenOperativo(any(), any(), eq(ADMIN_UUID), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/admin/reports/operational")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citas.totalCitas").value(100))
                .andExpect(jsonPath("$.citas.tasaCumplimiento").value(40.0))
                .andExpect(jsonPath("$.triaje.totalTriajes").value(50))
                .andExpect(jsonPath("$.triaje.emergencias").value(5))
                .andExpect(jsonPath("$.farmacia.totalRecetas").value(80))
                .andExpect(jsonPath("$.breakGlass.totalActivaciones").value(2));
    }

    @Test
    @DisplayName("GET /operational con fechas invertidas retorna 400 Bad Request")
    void getOperational_conFechasInvertidas_retorna400BadRequest() throws Exception {
        when(reporteService.obtenerResumenOperativo(eq(LocalDate.of(2026, 12, 31)), eq(LocalDate.of(2026, 1, 1)), eq(ADMIN_UUID), anyString()))
                .thenThrow(new DatosInvalidosException("La fecha desde no puede ser posterior a la fecha hasta."));

        mockMvc.perform(get("/api/v1/admin/reports/operational")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .param("desde", "2026-12-31")
                        .param("hasta", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("La fecha desde no puede ser posterior a la fecha hasta."));
    }

    // =========================================================================
    // CONSULTA REPORTES ESPECÍFICOS
    // =========================================================================

    @Test
    @DisplayName("GET /appointments con rol ADMIN retorna 200 OK")
    void getAppointments_conRolAdmin_retorna200Ok() throws Exception {
        MetricasCitasDto mockDto = new MetricasCitasDto(
                50L, 10L, 15L, 20L, 2L, 1L, 2L, 40.0, 6.0,
                List.of(new DistribucionItemDto("Pediatría", 30L)),
                List.of(new DistribucionItemDto("Sede Centro", 50L))
        );

        when(reporteService.obtenerReporteCitas(any(), any(), eq(ADMIN_UUID), anyString()))
                .thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/admin/reports/appointments")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCitas").value(50))
                .andExpect(jsonPath("$.porEspecialidad[0].etiqueta").value("Pediatría"));
    }

    @Test
    @DisplayName("GET /appointments con rol PACIENTE retorna 403 Forbidden")
    void getAppointments_conRolPaciente_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/appointments")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("GET /triage con rol ADMIN retorna 200 OK")
    void getTriage_conRolAdmin_retorna200Ok() throws Exception {
        MetricasTriajeDto mockDto = new MetricasTriajeDto(40L, 2L, 8L, 15L, 10L, 5L, 2L, 5.0);

        when(reporteService.obtenerReporteTriaje(any(), any(), eq(ADMIN_UUID), anyString()))
                .thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/admin/reports/triage")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTriajes").value(40))
                .andExpect(jsonPath("$.emergencias").value(2))
                .andExpect(jsonPath("$.tasaEmergencia").value(5.0));
    }

    @Test
    @DisplayName("GET /triage con rol PROFESIONAL retorna 403 Forbidden")
    void getTriage_conRolProfesional_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/triage")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }
}
