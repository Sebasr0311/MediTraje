package com.meditriaje.controller;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.followup.CrearSeguimientoRequest;
import com.meditriaje.dto.followup.SeguimientoResponse;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.FollowUpService;
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
import java.time.LocalDate;
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

@WebMvcTest(controllers = FollowUpController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class FollowUpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FollowUpService followUpService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /api/v1/attentions/{id}/follow-ups - Profesional: 201 Created con cabecera Location")
    void prescribirSeguimiento_profesional_retorna201() throws Exception {
        String tokenProf = "valid.token.prof";
        when(jwtService.esValido(tokenProf)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenProf)).thenReturn("uuid-prof-1");
        when(jwtService.extraerRoles(tokenProf)).thenReturn(List.of("ROLE_PROFESIONAL"));

        SeguimientoResponse resp = new SeguimientoResponse(
                "seg-uuid-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Dra. Laura Perez", "Medicina General",
                "CONTROL_MEDICO", "Control de presión en 7 días",
                LocalDate.now().plusDays(7), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );

        when(followUpService.prescribirSeguimiento(eq("at-1"), any(CrearSeguimientoRequest.class), eq("uuid-prof-1"), anyString()))
                .thenReturn(resp);

        String jsonBody = """
                {
                    "tipo": "CONTROL_MEDICO",
                    "indicaciones": "Control de presión en 7 días",
                    "fechaSugeridaControl": "2026-10-15"
                }
                """;

        mockMvc.perform(post("/api/v1/attentions/at-1/follow-ups")
                        .cookie(new Cookie("access_token", tokenProf))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/follow-ups/seg-uuid-1"))
                .andExpect(jsonPath("$.publicId").value("seg-uuid-1"))
                .andExpect(jsonPath("$.tipo").value("CONTROL_MEDICO"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{id}/follow-ups - Paciente: 403 Forbidden")
    void prescribirSeguimiento_paciente_retorna403() throws Exception {
        String tokenPac = "valid.token.pac";
        when(jwtService.esValido(tokenPac)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenPac)).thenReturn("uuid-pac-1");
        when(jwtService.extraerRoles(tokenPac)).thenReturn(List.of("ROLE_PACIENTE"));

        String jsonBody = """
                {
                    "tipo": "CONTROL_MEDICO",
                    "indicaciones": "Indicaciones",
                    "fechaSugeridaControl": "2026-10-15"
                }
                """;

        mockMvc.perform(post("/api/v1/attentions/at-1/follow-ups")
                        .cookie(new Cookie("access_token", tokenPac))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{id}/follow-ups - Administrador: 403 Forbidden")
    void prescribirSeguimiento_admin_retorna403() throws Exception {
        String tokenAdmin = "valid.token.admin";
        when(jwtService.esValido(tokenAdmin)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenAdmin)).thenReturn("uuid-admin-1");
        when(jwtService.extraerRoles(tokenAdmin)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        String jsonBody = """
                {
                    "tipo": "CONTROL_MEDICO",
                    "indicaciones": "Indicaciones",
                    "fechaSugeridaControl": "2026-10-15"
                }
                """;

        mockMvc.perform(post("/api/v1/attentions/at-1/follow-ups")
                        .cookie(new Cookie("access_token", tokenAdmin))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{id}/follow-ups - Sin autenticación: 401 Unauthorized")
    void prescribirSeguimiento_sinAuth_retorna401() throws Exception {
        String jsonBody = """
                {
                    "tipo": "CONTROL_MEDICO",
                    "indicaciones": "Indicaciones"
                }
                """;

        mockMvc.perform(post("/api/v1/attentions/at-1/follow-ups")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/attentions/{id}/follow-ups - Usuario autenticado: 200 OK")
    void listarPorAtencion_autenticado_retorna200() throws Exception {
        String token = "valid.token";
        when(jwtService.esValido(token)).thenReturn(true);
        when(jwtService.extraerPublicId(token)).thenReturn("uuid-user-1");
        when(jwtService.extraerRoles(token)).thenReturn(List.of("ROLE_PROFESIONAL"));

        when(followUpService.listarPorAtencion(eq("at-1"), eq("uuid-user-1"), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/attentions/at-1/follow-ups")
                        .cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/follow-ups/{id} - Usuario autenticado: 200 OK")
    void obtenerPorPublicId_autenticado_retorna200() throws Exception {
        String token = "valid.token";
        when(jwtService.esValido(token)).thenReturn(true);
        when(jwtService.extraerPublicId(token)).thenReturn("uuid-user-1");
        when(jwtService.extraerRoles(token)).thenReturn(List.of("ROLE_PACIENTE"));

        SeguimientoResponse resp = new SeguimientoResponse(
                "seg-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Dra. Laura Perez", "Medicina General",
                "CONTROL_MEDICO", "Control de presión",
                null, "PENDIENTE", null, null, Instant.now(), Instant.now()
        );

        when(followUpService.obtenerPorPublicId(eq("seg-1"), eq("uuid-user-1"), any()))
                .thenReturn(resp);

        mockMvc.perform(get("/api/v1/follow-ups/seg-1")
                        .cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("seg-1"));
    }

    @Test
    @DisplayName("PATCH /api/v1/follow-ups/{id}/cancel - Profesional: 200 OK")
    void cancelarSeguimiento_profesional_retorna200() throws Exception {
        String tokenProf = "valid.token.prof";
        when(jwtService.esValido(tokenProf)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenProf)).thenReturn("uuid-prof-1");
        when(jwtService.extraerRoles(tokenProf)).thenReturn(List.of("ROLE_PROFESIONAL"));

        SeguimientoResponse cancelado = new SeguimientoResponse(
                "seg-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Dra. Laura Perez", "Medicina General",
                "CONTROL_MEDICO", "Control cancelado",
                null, "CANCELADO", null, null, Instant.now(), Instant.now()
        );

        when(followUpService.cancelarSeguimiento(eq("seg-1"), eq("uuid-prof-1"), any()))
                .thenReturn(cancelado);

        mockMvc.perform(patch("/api/v1/follow-ups/seg-1/cancel")
                        .cookie(new Cookie("access_token", tokenProf))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));
    }
}
