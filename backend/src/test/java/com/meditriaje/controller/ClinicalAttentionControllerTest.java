package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.clinical.CerrarAtencionRequest;
import com.meditriaje.dto.clinical.CrearEnmiendaRequest;
import com.meditriaje.dto.clinical.EnmiendaResponse;
import com.meditriaje.dto.clinical.IniciarAtencionRequest;
import com.meditriaje.dto.clinical.SignosVitalesDto;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.ClinicalAttentionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClinicalAttentionController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class ClinicalAttentionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClinicalAttentionService clinicalAttentionService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String TOKEN_ADMIN = "token.admin.valido";

    private static final String PACIENTE_UUID = "usr-paciente-uuid";
    private static final String PROFESIONAL_UUID = "usr-profesional-uuid";
    private static final String ADMIN_UUID = "usr-admin-uuid";

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

    private AtencionResponse construirAtencionResponse(String atencionPublicId, String estado) {
        return new AtencionResponse(
                atencionPublicId,
                "cita-uuid-1",
                "pac-uuid-1",
                "Juan Perez",
                "prof-uuid-1",
                "Carlos Gomez",
                "Medicina General",
                estado,
                Instant.now().minusSeconds(1800),
                "CERRADA".equals(estado) ? Instant.now() : null,
                "J00",
                "Rinofaringitis aguda",
                "Cefalea y congestion nasal",
                "Cuadro viral agudo",
                "Reposo e hidratacion",
                new SignosVitalesDto(120, 80, 72, 16, new BigDecimal("36.5"), 98, new BigDecimal("70.0"), new BigDecimal("170.0"))
        );
    }

    @Test
    @DisplayName("POST /api/v1/attentions - Profesional: 201 Created con cabecera Location")
    void iniciarAtencion_profesional_retorna201Created() throws Exception {
        IniciarAtencionRequest request = new IniciarAtencionRequest("cita-uuid-1");
        AtencionResponse mockResponse = construirAtencionResponse("atencion-uuid-1", "ABIERTA");

        when(clinicalAttentionService.iniciarAtencion(any(IniciarAtencionRequest.class), eq(PROFESIONAL_UUID), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/attentions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/attentions/atencion-uuid-1"))
                .andExpect(jsonPath("$.publicId").value("atencion-uuid-1"))
                .andExpect(jsonPath("$.estado").value("ABIERTA"));
    }

    @Test
    @DisplayName("POST /api/v1/attentions - Paciente: 403 Forbidden")
    void iniciarAtencion_paciente_retorna403() throws Exception {
        IniciarAtencionRequest request = new IniciarAtencionRequest("cita-uuid-1");

        mockMvc.perform(post("/api/v1/attentions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/attentions - Administrador: 403 Forbidden (Admin sin acceso clínico)")
    void iniciarAtencion_admin_retorna403() throws Exception {
        IniciarAtencionRequest request = new IniciarAtencionRequest("cita-uuid-1");

        mockMvc.perform(post("/api/v1/attentions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/attentions - Sin autenticación: 401 Unauthorized")
    void iniciarAtencion_sinAuth_retorna401() throws Exception {
        IniciarAtencionRequest request = new IniciarAtencionRequest("cita-uuid-1");

        mockMvc.perform(post("/api/v1/attentions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/attentions - Body inválido: 400 Bad Request")
    void iniciarAtencion_bodyInvalido_retorna400() throws Exception {
        IniciarAtencionRequest requestInvalido = new IniciarAtencionRequest("  ");

        mockMvc.perform(post("/api/v1/attentions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{publicId}/close - Profesional: 200 OK")
    void cerrarAtencion_profesional_retorna200Ok() throws Exception {
        CerrarAtencionRequest request = new CerrarAtencionRequest(
                "J00", "Motivo de consulta", "Evolución clínica", "Indicaciones y plan de manejo",
                new SignosVitalesDto(120, 80, 72, 16, new BigDecimal("36.5"), 98, new BigDecimal("70.0"), new BigDecimal("170.0"))
        );
        AtencionResponse mockResponse = construirAtencionResponse("atencion-uuid-1", "CERRADA");

        when(clinicalAttentionService.cerrarAtencion(eq("atencion-uuid-1"), any(CerrarAtencionRequest.class), eq(PROFESIONAL_UUID), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/attentions/atencion-uuid-1/close")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("atencion-uuid-1"))
                .andExpect(jsonPath("$.estado").value("CERRADA"))
                .andExpect(jsonPath("$.diagnosticoCodigo").value("J00"));
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{publicId}/close - Paciente: 403 Forbidden")
    void cerrarAtencion_paciente_retorna403() throws Exception {
        CerrarAtencionRequest request = new CerrarAtencionRequest(
                "J00", "Motivo de consulta", "Evolución clínica", "Indicaciones", null
        );

        mockMvc.perform(post("/api/v1/attentions/atencion-uuid-1/close")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{publicId}/close - Administrador: 403 Forbidden")
    void cerrarAtencion_admin_retorna403() throws Exception {
        CerrarAtencionRequest request = new CerrarAtencionRequest(
                "J00", "Motivo de consulta", "Evolución clínica", "Indicaciones", null
        );

        mockMvc.perform(post("/api/v1/attentions/atencion-uuid-1/close")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/attentions/{publicId} - Usuario autenticado: 200 OK")
    void obtenerAtencion_autenticado_retorna200Ok() throws Exception {
        AtencionResponse mockResponse = construirAtencionResponse("atencion-uuid-1", "CERRADA");

        when(clinicalAttentionService.obtenerPorPublicId(eq("atencion-uuid-1"), eq(PACIENTE_UUID), anyCollection(), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/attentions/atencion-uuid-1")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("atencion-uuid-1"))
                .andExpect(jsonPath("$.estado").value("CERRADA"));
    }

    @Test
    @DisplayName("GET /api/v1/attentions/{publicId} - Sin autenticación: 401 Unauthorized")
    void obtenerAtencion_sinAuth_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/attentions/atencion-uuid-1")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{publicId}/amendments - Profesional: 201 Created con Location")
    void crearEnmienda_profesional_retorna201Created() throws Exception {
        CrearEnmiendaRequest request = new CrearEnmiendaRequest("Corrección de posología", "La frecuencia indicada es cada 8 horas.");
        EnmiendaResponse mockResponse = new EnmiendaResponse(
                PROFESIONAL_UUID, "Carlos Gomez", "Corrección de posología", "La frecuencia indicada es cada 8 horas.", Instant.now()
        );

        when(clinicalAttentionService.crearEnmienda(eq("atencion-uuid-1"), any(CrearEnmiendaRequest.class), eq(PROFESIONAL_UUID), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/attentions/atencion-uuid-1/amendments")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/attentions/atencion-uuid-1"))
                .andExpect(jsonPath("$.profesionalPublicId").value(PROFESIONAL_UUID))
                .andExpect(jsonPath("$.motivo").value("Corrección de posología"))
                .andExpect(jsonPath("$.contenido").value("La frecuencia indicada es cada 8 horas."));
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{publicId}/amendments - Paciente: 403 Forbidden")
    void crearEnmienda_paciente_retorna403() throws Exception {
        CrearEnmiendaRequest request = new CrearEnmiendaRequest("Motivo", "Contenido");

        mockMvc.perform(post("/api/v1/attentions/atencion-uuid-1/amendments")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{publicId}/amendments - Administrador: 403 Forbidden")
    void crearEnmienda_admin_retorna403() throws Exception {
        CrearEnmiendaRequest request = new CrearEnmiendaRequest("Motivo", "Contenido");

        mockMvc.perform(post("/api/v1/attentions/atencion-uuid-1/amendments")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{publicId}/amendments - Sin autenticación: 401 Unauthorized")
    void crearEnmienda_sinAuth_retorna401() throws Exception {
        CrearEnmiendaRequest request = new CrearEnmiendaRequest("Motivo", "Contenido");

        mockMvc.perform(post("/api/v1/attentions/atencion-uuid-1/amendments")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{publicId}/amendments - Body inválido: 400 Bad Request")
    void crearEnmienda_bodyInvalido_retorna400() throws Exception {
        CrearEnmiendaRequest requestInvalido = new CrearEnmiendaRequest("  ", "");

        mockMvc.perform(post("/api/v1/attentions/atencion-uuid-1/amendments")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }
}
