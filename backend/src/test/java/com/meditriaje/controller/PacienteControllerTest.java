package com.meditriaje.controller;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.PacientePerfilResponse;
import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.dto.followup.ReportarEvolucionRequest;
import com.meditriaje.dto.followup.SeguimientoResponse;
import com.meditriaje.service.AppointmentService;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.service.FollowUpService;
import com.meditriaje.service.PacienteService;
import com.meditriaje.service.PrescriptionService;
import com.meditriaje.service.EmergencyQrService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PacienteController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class PacienteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PacienteService pacienteService;

    @MockBean
    private ClinicalAttentionService clinicalAttentionService;

    @MockBean
    private PrescriptionService prescriptionService;

    @MockBean
    private AppointmentService appointmentService;

    @MockBean
    private FollowUpService followUpService;

    @MockBean
    private EmergencyQrService emergencyQrService;

    @MockBean
    private JwtService jwtService;

    @Test
    void getMe_sinToken_retorna401NoAutenticado() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value("Debe iniciar sesion para acceder a este recurso."));
    }

    @Test
    void getMe_conTokenInvalidoOExpirado_retorna401NoAutenticado() throws Exception {
        when(jwtService.esValido("token.invalido")).thenReturn(false);

        mockMvc.perform(get("/api/v1/patients/me")
                        .cookie(new Cookie("access_token", "token.invalido")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void getMe_conRolIncorrecto_retorna403AccesoDenegado() throws Exception {
        String tokenProf = "valid.token.profesional";
        when(jwtService.esValido(tokenProf)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenProf)).thenReturn("uuid-prof-1");
        when(jwtService.extraerRoles(tokenProf)).thenReturn(List.of("ROLE_PROFESIONAL"));

        mockMvc.perform(get("/api/v1/patients/me")
                        .cookie(new Cookie("access_token", tokenProf)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.mensaje").value("No tiene permisos para acceder a este recurso."));
    }

    @Test
    void getMe_conRolPacienteValido_retorna200YPerfil() throws Exception {
        String tokenPaciente = "valid.token.paciente";
        when(jwtService.esValido(tokenPaciente)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenPaciente)).thenReturn("uuid-user-paciente");
        when(jwtService.extraerRoles(tokenPaciente)).thenReturn(List.of("ROLE_PACIENTE"));

        PacientePerfilResponse mockPerfil = new PacientePerfilResponse(
                "uuid-paciente-1",
                "CC",
                "1020304050",
                "Carlos",
                "Perez",
                LocalDate.of(1995, 5, 20),
                "3001234567",
                "carlos.perez@example.com"
        );
        when(pacienteService.obtenerMiPerfil("uuid-user-paciente")).thenReturn(mockPerfil);

        mockMvc.perform(get("/api/v1/patients/me")
                        .cookie(new Cookie("access_token", tokenPaciente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("uuid-paciente-1"))
                .andExpect(jsonPath("$.tipoDocumento").value("CC"))
                .andExpect(jsonPath("$.numeroDocumento").value("1020304050"))
                .andExpect(jsonPath("$.nombres").value("Carlos"))
                .andExpect(jsonPath("$.apellidos").value("Perez"))
                .andExpect(jsonPath("$.email").value("carlos.perez@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/history - Paciente autenticado: 200 OK con historial paginado")
    void getMiHistoria_pacienteValido_retorna200YPagina() throws Exception {
        String tokenPaciente = "valid.token.paciente";
        when(jwtService.esValido(tokenPaciente)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenPaciente)).thenReturn("uuid-user-paciente");
        when(jwtService.extraerRoles(tokenPaciente)).thenReturn(List.of("ROLE_PACIENTE"));

        AtencionResponse mockAtencion = new AtencionResponse(
                "atencion-uuid-1", "cita-uuid-1", "uuid-paciente-1", "Carlos Perez",
                "prof-uuid-1", "Dr. Gomez", "Medicina General", "CERRADA",
                Instant.now().minusSeconds(3600), Instant.now(), "J00", "Rinofaringitis aguda",
                "Congestión nasal", "Cuadro viral", "Reposo", null
        );
        PaginatedResponse<AtencionResponse> paginatedResponse = PaginatedResponse.of(List.of(mockAtencion), 0, 10, 1L);

        when(clinicalAttentionService.obtenerMiHistoriaClinica(eq("uuid-user-paciente"), eq(0), eq(10), anyString()))
                .thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/v1/patients/me/history")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", tokenPaciente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("atencion-uuid-1"))
                .andExpect(jsonPath("$.content[0].estado").value("CERRADA"))
                .andExpect(jsonPath("$.content[0].diagnosticoCodigo").value("J00"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/history - Profesional: 403 Forbidden")
    void getMiHistoria_profesional_retorna403() throws Exception {
        String tokenProf = "valid.token.profesional";
        when(jwtService.esValido(tokenProf)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenProf)).thenReturn("uuid-prof-1");
        when(jwtService.extraerRoles(tokenProf)).thenReturn(List.of("ROLE_PROFESIONAL"));

        mockMvc.perform(get("/api/v1/patients/me/history")
                        .cookie(new Cookie("access_token", tokenProf)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/history - Administrador: 403 Forbidden (Admin bloqueado de contenido clínico)")
    void getMiHistoria_admin_retorna403() throws Exception {
        String tokenAdmin = "valid.token.admin";
        when(jwtService.esValido(tokenAdmin)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenAdmin)).thenReturn("uuid-admin-1");
        when(jwtService.extraerRoles(tokenAdmin)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        mockMvc.perform(get("/api/v1/patients/me/history")
                        .cookie(new Cookie("access_token", tokenAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/history - Sin autenticación: 401 Unauthorized")
    void getMiHistoria_sinAuth_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/prescriptions - Paciente autenticado: 200 OK con recetas paginadas")
    void getMisRecetas_pacienteValido_retorna200YPagina() throws Exception {
        String tokenPaciente = "valid.token.paciente";
        when(jwtService.esValido(tokenPaciente)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenPaciente)).thenReturn("uuid-user-paciente");
        when(jwtService.extraerRoles(tokenPaciente)).thenReturn(List.of("ROLE_PACIENTE"));

        RecetaResponse mockReceta = new RecetaResponse(
                "receta-uuid-1", "atencion-uuid-1", "uuid-paciente-1", "Carlos Perez",
                "prof-uuid-1", "Dr. Gomez", "Medicina General", 30,
                Instant.now(), List.of()
        );
        PaginatedResponse<RecetaResponse> paginatedResponse = PaginatedResponse.of(List.of(mockReceta), 0, 10, 1L);

        when(prescriptionService.obtenerMisRecetas(eq("uuid-user-paciente"), eq(0), eq(10), anyString()))
                .thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/v1/patients/me/prescriptions")
                        .cookie(new Cookie("access_token", tokenPaciente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("receta-uuid-1"))
                .andExpect(jsonPath("$.content[0].vigenciaDias").value(30))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/prescriptions - Profesional: 403 Forbidden")
    void getMisRecetas_profesional_retorna403() throws Exception {
        String tokenProf = "valid.token.profesional";
        when(jwtService.esValido(tokenProf)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenProf)).thenReturn("uuid-prof-1");
        when(jwtService.extraerRoles(tokenProf)).thenReturn(List.of("ROLE_PROFESIONAL"));

        mockMvc.perform(get("/api/v1/patients/me/prescriptions")
                        .cookie(new Cookie("access_token", tokenProf)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/prescriptions - Administrador: 403 Forbidden")
    void getMisRecetas_admin_retorna403() throws Exception {
        String tokenAdmin = "valid.token.admin";
        when(jwtService.esValido(tokenAdmin)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenAdmin)).thenReturn("uuid-admin-1");
        when(jwtService.extraerRoles(tokenAdmin)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        mockMvc.perform(get("/api/v1/patients/me/prescriptions")
                        .cookie(new Cookie("access_token", tokenAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/prescriptions - Sin autenticación: 401 Unauthorized")
    void getMisRecetas_sinAuth_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me/prescriptions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/appointments - Paciente autenticado: 200 OK")
    void getMisCitas_paciente_retorna200Ok() throws Exception {
        String tokenPaciente = "valid.token.paciente";
        when(jwtService.esValido(tokenPaciente)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenPaciente)).thenReturn("uuid-user-paciente");
        when(jwtService.extraerRoles(tokenPaciente)).thenReturn(List.of("ROLE_PACIENTE"));

        CitaResponse mockCita = new CitaResponse(
                "cita-uuid-1", "slot-uuid-1", "paciente-uuid-1", "Carlos Perez",
                "prof-uuid-1", "Dr. Gomez", "esp-uuid-1", "Medicina General",
                "sede-uuid-1", "Sede Central", "Calle 100 # 15-20",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(4800),
                "PRESENCIAL", "PROGRAMADA", null, null, Instant.now()
        );
        PaginatedResponse<CitaResponse> paginatedResponse = PaginatedResponse.of(List.of(mockCita), 0, 10, 1L);

        when(appointmentService.obtenerMisCitas(eq("uuid-user-paciente"), eq(0), eq(10)))
                .thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/v1/patients/me/appointments")
                        .cookie(new Cookie("access_token", tokenPaciente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("cita-uuid-1"))
                .andExpect(jsonPath("$.content[0].estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$.content[0].especialidadNombre").value("Medicina General"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/appointments - Profesional: 403 Forbidden")
    void getMisCitas_profesional_retorna403() throws Exception {
        String tokenProf = "valid.token.profesional";
        when(jwtService.esValido(tokenProf)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenProf)).thenReturn("uuid-prof-1");
        when(jwtService.extraerRoles(tokenProf)).thenReturn(List.of("ROLE_PROFESIONAL"));

        mockMvc.perform(get("/api/v1/patients/me/appointments")
                        .cookie(new Cookie("access_token", tokenProf)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/appointments - Administrador: 403 Forbidden")
    void getMisCitas_admin_retorna403() throws Exception {
        String tokenAdmin = "valid.token.admin";
        when(jwtService.esValido(tokenAdmin)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenAdmin)).thenReturn("uuid-admin-1");
        when(jwtService.extraerRoles(tokenAdmin)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        mockMvc.perform(get("/api/v1/patients/me/appointments")
                        .cookie(new Cookie("access_token", tokenAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/appointments - Sin autenticación: 401 Unauthorized")
    void getMisCitas_sinAuth_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me/appointments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/follow-ups - Paciente autenticado: 200 OK con lista paginada")
    void getMisSeguimientos_paciente_retorna200() throws Exception {
        String tokenPaciente = "valid.token.paciente";
        when(jwtService.esValido(tokenPaciente)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenPaciente)).thenReturn("uuid-pac-1");
        when(jwtService.extraerRoles(tokenPaciente)).thenReturn(List.of("ROLE_PACIENTE"));

        SeguimientoResponse item = new SeguimientoResponse(
                "seg-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Dra. Laura Perez", "Medicina General",
                "EVOLUCION_SINTOMAS", "Monitorear dolor de garganta",
                LocalDate.now().plusDays(3), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );
        PaginatedResponse<SeguimientoResponse> pageResponse = new PaginatedResponse<>(
                List.of(item), 0, 10, 1, 1, false, false
        );

        when(followUpService.listarMisSeguimientos(eq("uuid-pac-1"), any(), anyInt(), anyInt()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/patients/me/follow-ups")
                        .cookie(new Cookie("access_token", tokenPaciente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("seg-1"))
                .andExpect(jsonPath("$.content[0].tipo").value("EVOLUCION_SINTOMAS"))
                .andExpect(jsonPath("$.content[0].estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("POST /api/v1/patients/me/follow-ups/{publicId}/report - Paciente reporta evolución: 200 OK")
    void reportarEvolucion_paciente_retorna200() throws Exception {
        String tokenPaciente = "valid.token.paciente";
        when(jwtService.esValido(tokenPaciente)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenPaciente)).thenReturn("uuid-pac-1");
        when(jwtService.extraerRoles(tokenPaciente)).thenReturn(List.of("ROLE_PACIENTE"));

        SeguimientoResponse actualizado = new SeguimientoResponse(
                "seg-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Dra. Laura Perez", "Medicina General",
                "EVOLUCION_SINTOMAS", "Monitorear dolor de garganta",
                LocalDate.now().plusDays(3), "COMPLETADO",
                Instant.now(), "Ya no presento dolor ni fiebre.", Instant.now(), Instant.now()
        );

        when(followUpService.reportarEvolucion(eq("seg-1"), any(ReportarEvolucionRequest.class), eq("uuid-pac-1"), anyString()))
                .thenReturn(actualizado);

        String jsonBody = """
                {
                    "reporte": "Ya no presento dolor ni fiebre."
                }
                """;

        mockMvc.perform(post("/api/v1/patients/me/follow-ups/seg-1/report")
                        .cookie(new Cookie("access_token", tokenPaciente))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("seg-1"))
                .andExpect(jsonPath("$.estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.reportePaciente").value("Ya no presento dolor ni fiebre."));
    }

    @Test
    @DisplayName("POST /api/v1/patients/me/follow-ups/{publicId}/report - Administrador: 403 Forbidden")
    void reportarEvolucion_admin_retorna403() throws Exception {
        String tokenAdmin = "valid.token.admin";
        when(jwtService.esValido(tokenAdmin)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenAdmin)).thenReturn("uuid-admin-1");
        when(jwtService.extraerRoles(tokenAdmin)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        mockMvc.perform(post("/api/v1/patients/me/follow-ups/seg-1/report")
                        .cookie(new Cookie("access_token", tokenAdmin))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reporte\": \"Reporte admin invalido\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/patients/me/emergency-qr - Paciente: 201 Created")
    void generarAccesoQr_paciente_retorna201() throws Exception {
        String tokenPaciente = "valid.token.paciente";
        when(jwtService.esValido(tokenPaciente)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenPaciente)).thenReturn("uuid-pac-1");
        when(jwtService.extraerRoles(tokenPaciente)).thenReturn(List.of("ROLE_PACIENTE"));

        com.meditriaje.dto.emergency.GenerarQrResponse response = new com.meditriaje.dto.emergency.GenerarQrResponse(
                "qr-uuid-1", "plain-token-xyz", "#/emergency-summary/plain-token-xyz",
                Instant.now().plusSeconds(900), 3, false,
                true, true, true, true, Instant.now()
        );

        when(emergencyQrService.generarAccesoQr(any(), eq("uuid-pac-1"), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/patients/me/emergency-qr")
                        .cookie(new Cookie("access_token", tokenPaciente))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": null, \"incluirAlergias\": true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value("qr-uuid-1"))
                .andExpect(jsonPath("$.token").value("plain-token-xyz"))
                .andExpect(jsonPath("$.maxAccesos").value(3));
    }

    @Test
    @DisplayName("POST /api/v1/patients/me/emergency-qr - Profesional: 403 Forbidden")
    void generarAccesoQr_profesional_retorna403() throws Exception {
        String tokenProf = "valid.token.prof";
        when(jwtService.esValido(tokenProf)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenProf)).thenReturn("uuid-prof-1");
        when(jwtService.extraerRoles(tokenProf)).thenReturn(List.of("ROLE_PROFESIONAL"));

        mockMvc.perform(post("/api/v1/patients/me/emergency-qr")
                        .cookie(new Cookie("access_token", tokenProf))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/patients/me/emergency-qr - Paciente: 200 OK")
    void listarMisAccesosQr_paciente_retorna200() throws Exception {
        String tokenPaciente = "valid.token.paciente";
        when(jwtService.esValido(tokenPaciente)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenPaciente)).thenReturn("uuid-pac-1");
        when(jwtService.extraerRoles(tokenPaciente)).thenReturn(List.of("ROLE_PACIENTE"));

        com.meditriaje.dto.emergency.AccesoQrResponse item = new com.meditriaje.dto.emergency.AccesoQrResponse(
                "qr-uuid-1", "ACTIVO", 3, 1, false, false,
                true, true, true, true, Instant.now().plusSeconds(600), Instant.now()
        );

        when(emergencyQrService.listarMisAccesosQr("uuid-pac-1")).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/patients/me/emergency-qr")
                        .cookie(new Cookie("access_token", tokenPaciente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicId").value("qr-uuid-1"))
                .andExpect(jsonPath("$[0].estado").value("ACTIVO"));
    }

    @Test
    @DisplayName("PATCH /api/v1/patients/me/emergency-qr/{publicId}/revoke - Paciente: 204 No Content")
    void revocarAccesoQr_paciente_retorna204() throws Exception {
        String tokenPaciente = "valid.token.paciente";
        when(jwtService.esValido(tokenPaciente)).thenReturn(true);
        when(jwtService.extraerPublicId(tokenPaciente)).thenReturn("uuid-pac-1");
        when(jwtService.extraerRoles(tokenPaciente)).thenReturn(List.of("ROLE_PACIENTE"));

        mockMvc.perform(patch("/api/v1/patients/me/emergency-qr/qr-uuid-1/revoke")
                        .cookie(new Cookie("access_token", tokenPaciente))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent());
    }
}
