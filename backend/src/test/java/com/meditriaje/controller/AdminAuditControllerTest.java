package com.meditriaje.controller;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.admin.AdminAuditController;
import com.meditriaje.dto.audit.RegistroAuditoriaResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AuditoriaService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAuditController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditoriaService auditoriaService;

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
    // SEGURIDAD Y CONTROL DE ACCESO (RF-26, ADR-007, ADR-019)
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/admin/audit sin autenticación retorna 401 Unauthorized")
    void consultarBitacora_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit con rol PACIENTE retorna 403 Forbidden")
    void consultarBitacora_conRolPaciente_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit con rol PROFESIONAL retorna 403 Forbidden")
    void consultarBitacora_conRolProfesional_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    // =========================================================================
    // CONSULTA EXITOSA Y FILTROS
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/admin/audit con rol ADMIN retorna 200 OK y lista paginada")
    void consultarBitacora_conRolAdmin_retorna200Ok() throws Exception {
        RegistroAuditoriaResponse evento1 = new RegistroAuditoriaResponse(
                101L,
                "admin@meditriaje.com",
                "LOGIN_EXITOSO",
                "USUARIO",
                "u-123",
                "EXITO",
                "192.168.1.50",
                Instant.parse("2026-10-04T12:00:00Z")
        );
        PaginatedResponse<RegistroAuditoriaResponse> responsePaginada =
                PaginatedResponse.of(List.of(evento1), 0, 20, 1L);

        when(auditoriaService.consultarBitacora(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(responsePaginada);

        mockMvc.perform(get("/api/v1/admin/audit")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(101))
                .andExpect(jsonPath("$.content[0].usuarioEmail").value("admin@meditriaje.com"))
                .andExpect(jsonPath("$.content[0].accion").value("LOGIN_EXITOSO"))
                .andExpect(jsonPath("$.content[0].resultado").value("EXITO"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit con filtros de fecha y acción invoca servicio y retorna 200 OK")
    void consultarBitacora_conFiltros_retorna200Ok() throws Exception {
        PaginatedResponse<RegistroAuditoriaResponse> responsePaginada =
                PaginatedResponse.of(List.of(), 0, 10, 0L);

        when(auditoriaService.consultarBitacora(
                eq(LocalDate.of(2026, 10, 1)),
                eq(LocalDate.of(2026, 10, 4)),
                eq("RESERVA_CITA"),
                eq("EXITO"),
                eq(0),
                eq(10)
        )).thenReturn(responsePaginada);

        mockMvc.perform(get("/api/v1/admin/audit")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-04")
                        .param("accion", "RESERVA_CITA")
                        .param("resultado", "EXITO")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit con fechas invertidas retorna 400 Bad Request")
    void consultarBitacora_conFechasInvertidas_retorna400BadRequest() throws Exception {
        when(auditoriaService.consultarBitacora(
                eq(LocalDate.of(2026, 10, 10)),
                eq(LocalDate.of(2026, 10, 1)),
                any(),
                any(),
                anyInt(),
                anyInt()
        )).thenThrow(new DatosInvalidosException("La fecha desde no puede ser posterior a la fecha hasta."));

        mockMvc.perform(get("/api/v1/admin/audit")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .param("desde", "2026-10-10")
                        .param("hasta", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("La fecha desde no puede ser posterior a la fecha hasta."));
    }
}
