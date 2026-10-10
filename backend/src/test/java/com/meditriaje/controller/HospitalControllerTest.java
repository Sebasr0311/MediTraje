package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.hospital.*;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.HospitalService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HospitalController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class HospitalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HospitalService hospitalService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_ENFERMERIA = "token.enfermeria.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_ADMIN = "token.admin.valido";

    @BeforeEach
    void setUp() {
        when(jwtService.esValido(TOKEN_ENFERMERIA)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ENFERMERIA)).thenReturn("usr-enf-uuid");
        when(jwtService.extraerRoles(TOKEN_ENFERMERIA)).thenReturn(List.of("ROLE_ENFERMERIA"));

        when(jwtService.esValido(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PROFESIONAL)).thenReturn("usr-prof-uuid");
        when(jwtService.extraerRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));

        when(jwtService.esValido(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PACIENTE)).thenReturn("usr-pac-uuid");
        when(jwtService.extraerRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.esValido(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ADMIN)).thenReturn("usr-admin-uuid");
        when(jwtService.extraerRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
    }

    private Cookie cookie(String token) {
        return new Cookie("access_token", token);
    }

    @Test
    @DisplayName("SEC01: Petición anónima a asignación de cama retorna 401 Unauthorized")
    void asignarCama_sinSesion_retorna401() throws Exception {
        AsignarCamaRequest req = new AsignarCamaRequest("cama-pub", "Obs");

        mockMvc.perform(post("/api/v1/hospital/episodes/ep-1/beds/assign")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SEC02: Paciente intentando asignar cama retorna 403 Forbidden")
    void asignarCama_paciente_retorna403() throws Exception {
        AsignarCamaRequest req = new AsignarCamaRequest("cama-pub", "Obs");

        mockMvc.perform(post("/api/v1/hospital/episodes/ep-1/beds/assign")
                        .cookie(cookie(TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H01/H02: Enfermería asignando cama disponible retorna 201 Created")
    void asignarCama_enfermeria_retorna201() throws Exception {
        AsignarCamaRequest req = new AsignarCamaRequest("cama-pub", "Asignación inicial");
        CamaDetalleResponse resp = new CamaDetalleResponse(
                "cama-pub", "hab-pub", "H1", "area-pub", "Urgencias",
                "sede-pub", "Hospital", "CAMA-01", "OCUPADA", "ep-1", "Carlos", "oc-1"
        );

        when(hospitalService.asignarCama(eq("ep-1"), any(), anyString(), anyString())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/hospital/episodes/ep-1/beds/assign")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value("cama-pub"))
                .andExpect(jsonPath("$.estado").value("OCUPADA"));
    }

    @Test
    @DisplayName("H03: Profesional trasladando paciente retorna 200 OK con movimiento")
    void trasladarPaciente_profesional_retorna200() throws Exception {
        TrasladarPacienteRequest req = new TrasladarPacienteRequest("cama-dest-pub", "Pasa a UCI");
        MovimientoResponse mov = new MovimientoResponse(
                "mov-pub", "ep-1", "Urgencias", "UCI", "CAMA-01", "CAMA-02",
                Instant.now(), "Pasa a UCI", "medico@hospital.com"
        );

        when(hospitalService.trasladarPaciente(eq("ep-1"), any(), anyString(), anyString())).thenReturn(mov);

        mockMvc.perform(post("/api/v1/hospital/episodes/ep-1/beds/transfer")
                        .cookie(cookie(TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("mov-pub"))
                .andExpect(jsonPath("$.areaDestinoNombre").value("UCI"));
    }

    @Test
    @DisplayName("H05: Enfermería intentando egreso médico definitivo retorna 403 Forbidden")
    void registrarEgreso_enfermeria_retorna403() throws Exception {
        RegistrarEgresoRequest req = new RegistrarEgresoRequest("ALTA_DOMICILIO", "Diag", "Epicrisis", "Plan");

        mockMvc.perform(post("/api/v1/hospital/episodes/ep-1/discharge")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H05: Profesional médico registrando alta hospitalaria retorna 204 No Content")
    void registrarEgreso_profesional_retorna204() throws Exception {
        RegistrarEgresoRequest req = new RegistrarEgresoRequest("ALTA_DOMICILIO", "Diag", "Epicrisis", "Plan");

        mockMvc.perform(post("/api/v1/hospital/episodes/ep-1/discharge")
                        .cookie(cookie(TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("H06: Consulta de censo de camas por administrador retorna 200 OK con métricas")
    void obtenerCensoCamas_admin_retorna200() throws Exception {
        CensoCamasResponse censo = new CensoCamasResponse("sede-pub", "Hospital Central", 20, 15, 3, 1, 1, 75.0, List.of());
        when(hospitalService.obtenerCensoCamas("sede-pub")).thenReturn(censo);

        mockMvc.perform(get("/api/v1/hospital/census/sede-pub")
                        .cookie(cookie(TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCamas").value(20))
                .andExpect(jsonPath("$.ocupadas").value(15))
                .andExpect(jsonPath("$.tasaOcupacionPorcentaje").value(75.0));
    }
}
