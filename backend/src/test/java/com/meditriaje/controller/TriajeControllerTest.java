package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.triage.CatalogoSintomaResponse;
import com.meditriaje.dto.triage.CrearTriajeRequest;
import com.meditriaje.dto.triage.SintomaItemRequest;
import com.meditriaje.dto.triage.SintomaItemResponse;
import com.meditriaje.dto.triage.TriajeResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.TriajeService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TriajeController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class TriajeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TriajeService triajeService;

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

    @Test
    void postTriaje_pacienteAutenticado_retorna201CreatedConLocation() throws Exception {
        CrearTriajeRequest request = new CrearTriajeRequest(
                List.of(new SintomaItemRequest("FIEBRE", BigDecimal.valueOf(12.0), 3)),
                "Observacion leve"
        );

        TriajeResponse mockResponse = new TriajeResponse(
                "triaje-uuid-1", "pac-uuid-1", "IV", "CITA_TELEMEDICINA", false, "v1-prototipo",
                "Se sugiere una cita de telemedicina.", "Aviso", List.of(),
                List.of(new SintomaItemResponse("FIEBRE", "Fiebre", BigDecimal.valueOf(12.0), 3, false)),
                "Observacion leve", Instant.now()
        );

        when(triajeService.evaluarYGuardarTriaje(any(), eq(PACIENTE_UUID), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/triage/triaje-uuid-1"))
                .andExpect(jsonPath("$.publicId").value("triaje-uuid-1"))
                .andExpect(jsonPath("$.nivelPrioridad").value("IV"))
                .andExpect(jsonPath("$.rutaSugerida").value("CITA_TELEMEDICINA"))
                .andExpect(jsonPath("$.esEmergencia").value(false));
    }

    @Test
    void postTriaje_profesionalAutenticado_retorna403Forbidden() throws Exception {
        CrearTriajeRequest request = new CrearTriajeRequest(
                List.of(new SintomaItemRequest("FIEBRE", BigDecimal.valueOf(12.0), 3)),
                null
        );

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postTriaje_adminAutenticado_retorna403Forbidden() throws Exception {
        CrearTriajeRequest request = new CrearTriajeRequest(
                List.of(new SintomaItemRequest("FIEBRE", BigDecimal.valueOf(12.0), 3)),
                null
        );

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postTriaje_sinSesion_retorna401Unauthorized() throws Exception {
        CrearTriajeRequest request = new CrearTriajeRequest(
                List.of(new SintomaItemRequest("FIEBRE", BigDecimal.valueOf(12.0), 3)),
                null
        );

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postTriaje_datosInvalidos_sintomasVacios_retorna400BadRequest() throws Exception {
        String bodyInvalido = """
            {
              "sintomas": []
            }
            """;

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void postTriaje_datosInvalidos_intensidadMayorA10_retorna400BadRequest() throws Exception {
        String bodyInvalido = """
            {
              "sintomas": [
                {
                  "codigo": "FIEBRE",
                  "duracionHoras": 2.0,
                  "intensidad": 15
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void getTriaje_propioPaciente_retorna200Ok() throws Exception {
        String triajePublicId = "triaje-uuid-1";
        TriajeResponse mockResponse = new TriajeResponse(
                triajePublicId, "pac-uuid-1", "IV", "CITA_TELEMEDICINA", false, "v1-prototipo",
                "Se sugiere una cita de telemedicina.", "Aviso", List.of(),
                List.of(new SintomaItemResponse("FIEBRE", "Fiebre", BigDecimal.valueOf(12.0), 3, false)),
                null, Instant.now()
        );

        when(triajeService.obtenerPorPublicId(eq(triajePublicId), eq(PACIENTE_UUID), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/triage/{publicId}", triajePublicId)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(triajePublicId))
                .andExpect(jsonPath("$.nivelPrioridad").value("IV"));
    }

    @Test
    void getTriaje_otroPaciente_retorna403Forbidden() throws Exception {
        String triajePublicId = "triaje-uuid-ajeno";

        when(triajeService.obtenerPorPublicId(eq(triajePublicId), eq(PACIENTE_UUID), any()))
                .thenThrow(new AccesoNoAutorizadoException("No tiene autorizacion para acceder al triaje de otro paciente."));

        mockMvc.perform(get("/api/v1/triage/{publicId}", triajePublicId)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }

    @Test
    void getTriaje_sinSesion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/triage/{publicId}", "triaje-uuid-1")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSymptoms_autenticado_retorna200OkConCatalogo() throws Exception {
        List<CatalogoSintomaResponse> catalogoMock = List.of(
                new CatalogoSintomaResponse("sintoma-uuid-1", "DOLOR_TORACICO_OPRESIVO", "Dolor torácico opresivo", "CARDIOVASCULAR", true),
                new CatalogoSintomaResponse("sintoma-uuid-2", "FIEBRE", "Fiebre", "GENERAL", false)
        );

        when(triajeService.obtenerCatalogoSintomas()).thenReturn(catalogoMock);

        mockMvc.perform(get("/api/v1/triage/symptoms")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigo").value("DOLOR_TORACICO_OPRESIVO"))
                .andExpect(jsonPath("$[0].esAlarma").value(true))
                .andExpect(jsonPath("$[1].codigo").value("FIEBRE"))
                .andExpect(jsonPath("$[1].esAlarma").value(false));
    }
}
