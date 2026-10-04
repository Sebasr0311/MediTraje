package com.meditriaje.controller;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.emergency.ConsultarResumenRequest;
import com.meditriaje.dto.emergency.VerificarQrResponse;
import com.meditriaje.dto.emergency.summary.PacienteEmergenciaDto;
import com.meditriaje.dto.emergency.summary.ResumenSaludResponse;
import com.meditriaje.exception.CredencialesInvalidasException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.EmergencyQrService;
import com.meditriaje.service.EmergencySummaryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmergencySummaryController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class EmergencySummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmergencyQrService emergencyQrService;

    @MockBean
    private EmergencySummaryService emergencySummaryService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("GET /api/v1/emergency-summary/{token}/check: público sin sesión retorna 200 OK")
    void verificarToken_publico_retorna200() throws Exception {
        when(emergencyQrService.verificarTokenPublico("token-abc"))
                .thenReturn(new VerificarQrResponse(true, true, "ACTIVO", Instant.now().plusSeconds(600)));

        mockMvc.perform(get("/api/v1/emergency-summary/token-abc/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.requierePin").value(true))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    @DisplayName("POST /api/v1/emergency-summary/{token}: público con PIN correcto retorna 200 OK y resumen")
    void consultarResumen_exito_retorna200() throws Exception {
        PacienteEmergenciaDto pacienteDto = new PacienteEmergenciaDto(
                "Carlos Gomez", "CC", "123456789",
                LocalDate.of(1990, 5, 15), 36, "3001234567", "carlos@test.com"
        );
        ResumenSaludResponse resumen = new ResumenSaludResponse(
                pacienteDto,
                List.of(),
                List.of(),
                List.of(),
                Instant.now(),
                "Aviso legal"
        );

        when(emergencySummaryService.consultarResumenPorToken(eq("token-abc"), any(), anyString()))
                .thenReturn(resumen);

        mockMvc.perform(post("/api/v1/emergency-summary/token-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paciente.nombreCompleto").value("Carlos Gomez"))
                .andExpect(jsonPath("$.paciente.tipoDocumento").value("CC"))
                .andExpect(jsonPath("$.advertenciaLegal").value("Aviso legal"));
    }

    @Test
    @DisplayName("POST /api/v1/emergency-summary/{token}: PIN incorrecto retorna 401 Unauthorized")
    void consultarResumen_pinInvalido_retorna401() throws Exception {
        when(emergencySummaryService.consultarResumenPorToken(eq("token-abc"), any(), anyString()))
                .thenThrow(new CredencialesInvalidasException("El PIN de seguridad proporcionado es incorrecto."));

        mockMvc.perform(post("/api/v1/emergency-summary/token-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"9999\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.mensaje").value("El PIN de seguridad proporcionado es incorrecto."));
    }

    @Test
    @DisplayName("POST /api/v1/emergency-summary/{token}: token inexistente retorna 404 Not Found")
    void consultarResumen_tokenNoEncontrado_retorna404() throws Exception {
        when(emergencySummaryService.consultarResumenPorToken(eq("token-inexistente"), any(), anyString()))
                .thenThrow(new RecursoNoEncontradoException("Acceso de emergencia no valido o inexistente."));

        mockMvc.perform(post("/api/v1/emergency-summary/token-inexistente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("POST /api/v1/emergency-summary/{token}: token expirado retorna 400 Bad Request")
    void consultarResumen_tokenExpirado_retorna400() throws Exception {
        when(emergencySummaryService.consultarResumenPorToken(eq("token-expirado"), any(), anyString()))
                .thenThrow(new DatosInvalidosException("El acceso QR ha expirado."));

        mockMvc.perform(post("/api/v1/emergency-summary/token-expirado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }
}
