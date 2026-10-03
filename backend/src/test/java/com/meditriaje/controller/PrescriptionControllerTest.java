package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.prescription.CrearRecetaDetalleRequest;
import com.meditriaje.dto.prescription.CrearRecetaRequest;
import com.meditriaje.dto.prescription.RecetaDetalleResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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

@WebMvcTest(controllers = PrescriptionController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class PrescriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PrescriptionService prescriptionService;

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

    private RecetaResponse construirRecetaResponse(String publicId) {
        return new RecetaResponse(
                publicId,
                "atencion-uuid-1",
                "pac-uuid-1",
                "Juan Perez",
                "prof-uuid-1",
                "Carlos Gomez",
                "Medicina General",
                30,
                Instant.now(),
                List.of(new RecetaDetalleResponse(
                        "med-uuid-1",
                        "MED-ACE-500",
                        "Acetaminofen",
                        "Acetaminofen",
                        "Tableta",
                        "500 mg",
                        "500 mg",
                        "Cada 8 horas",
                        5,
                        15,
                        "Tomar con agua"
                ))
        );
    }

    @Test
    @DisplayName("POST /api/v1/prescriptions - Profesional: 201 Created con cabecera Location")
    void emitirReceta_profesional_retorna201Created() throws Exception {
        CrearRecetaRequest request = new CrearRecetaRequest(
                "atencion-uuid-1",
                30,
                List.of(new CrearRecetaDetalleRequest("med-uuid-1", "500 mg", "Cada 8 horas", 5, 15, "Tomar con agua"))
        );

        when(prescriptionService.emitirReceta(any(CrearRecetaRequest.class), eq(PROFESIONAL_UUID), anyString()))
                .thenReturn(construirRecetaResponse("receta-uuid-1"));

        mockMvc.perform(post("/api/v1/prescriptions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/prescriptions/receta-uuid-1"))
                .andExpect(jsonPath("$.publicId").value("receta-uuid-1"))
                .andExpect(jsonPath("$.pacienteNombre").value("Juan Perez"))
                .andExpect(jsonPath("$.detalles.length()").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/prescriptions - Paciente: 403 Forbidden")
    void emitirReceta_paciente_retorna403() throws Exception {
        CrearRecetaRequest request = new CrearRecetaRequest(
                "atencion-uuid-1",
                30,
                List.of(new CrearRecetaDetalleRequest("med-uuid-1", "500 mg", "Cada 8 horas", 5, 15, "Tomar con agua"))
        );

        mockMvc.perform(post("/api/v1/prescriptions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/prescriptions - Administrador: 403 Forbidden")
    void emitirReceta_admin_retorna403() throws Exception {
        CrearRecetaRequest request = new CrearRecetaRequest(
                "atencion-uuid-1",
                30,
                List.of(new CrearRecetaDetalleRequest("med-uuid-1", "500 mg", "Cada 8 horas", 5, 15, "Tomar con agua"))
        );

        mockMvc.perform(post("/api/v1/prescriptions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/prescriptions - Sin autenticación: 401 Unauthorized")
    void emitirReceta_sinAuth_retorna401() throws Exception {
        CrearRecetaRequest request = new CrearRecetaRequest(
                "atencion-uuid-1",
                30,
                List.of(new CrearRecetaDetalleRequest("med-uuid-1", "500 mg", "Cada 8 horas", 5, 15, "Tomar con agua"))
        );

        mockMvc.perform(post("/api/v1/prescriptions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/prescriptions - 400 Bad Request ante validaciones de DTO")
    void emitirReceta_dtoInvalido_retorna400() throws Exception {
        // Detalles vacíos y atención en blanco
        CrearRecetaRequest requestInvalido = new CrearRecetaRequest("", 30, List.of());

        mockMvc.perform(post("/api/v1/prescriptions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("GET /api/v1/prescriptions/{publicId} - 200 OK para usuario autorizado")
    void obtenerReceta_autorizado_retorna200Ok() throws Exception {
        when(prescriptionService.obtenerPorPublicId(eq("receta-uuid-1"), eq(PROFESIONAL_UUID), anyCollection()))
                .thenReturn(construirRecetaResponse("receta-uuid-1"));

        mockMvc.perform(get("/api/v1/prescriptions/receta-uuid-1")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("receta-uuid-1"))
                .andExpect(jsonPath("$.detalles.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/prescriptions/{publicId} - 403 Forbidden ante acceso no autorizado")
    void obtenerReceta_noAutorizado_retorna403() throws Exception {
        when(prescriptionService.obtenerPorPublicId(eq("receta-uuid-1"), eq(PACIENTE_UUID), anyCollection()))
                .thenThrow(new AccesoNoAutorizadoException("No tiene autorizacion para acceder a la receta de otro paciente."));

        mockMvc.perform(get("/api/v1/prescriptions/receta-uuid-1")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }
}
