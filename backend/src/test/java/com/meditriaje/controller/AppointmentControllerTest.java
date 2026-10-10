package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.appointment.CancelarCitaRequest;
import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.appointment.ReservarCitaRequest;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.CitaNoDisponibleException;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AppointmentService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AppointmentController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AppointmentService appointmentService;

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

    // =========================================================================
    // CONTROL DE ACCESO Y AUTORIZACIÓN (ADR-002, HU-04)
    // =========================================================================

    @Test
    void postAppointment_sinAutenticacion_retorna401Unauthorized() throws Exception {
        ReservarCitaRequest req = new ReservarCitaRequest("slot-uuid-1");

        mockMvc.perform(post("/api/v1/appointments")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void postAppointment_conRolProfesional_retorna403Forbidden() throws Exception {
        ReservarCitaRequest req = new ReservarCitaRequest("slot-uuid-1");

        mockMvc.perform(post("/api/v1/appointments")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    // =========================================================================
    // CASOS DE NEGOCIO Y VALIDACIÓN (HU-04, ADR-003, ADR-006)
    // =========================================================================

    @Test
    void postAppointment_conRolPacienteYDatosValidos_retorna201CreatedYLocationHeader() throws Exception {
        ReservarCitaRequest req = new ReservarCitaRequest("slot-uuid-1");
        Instant ahora = Instant.now();

        CitaResponse mockResponse = new CitaResponse(
                "cita-uuid-100", "slot-uuid-1", "pac-uuid-1", "Carlos Sanchez",
                "prof-uuid-1", "Dra. Gomez", "esp-uuid-1", "Pediatria",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                ahora.plusSeconds(3600), ahora.plusSeconds(4800),
                "PRESENCIAL", "PROGRAMADA", null, null, ahora
        );

        when(appointmentService.reservarCita(any(ReservarCitaRequest.class), eq(PACIENTE_UUID), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/appointments")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/appointments/cita-uuid-100"))
                .andExpect(jsonPath("$.publicId").value("cita-uuid-100"))
                .andExpect(jsonPath("$.slotPublicId").value("slot-uuid-1"))
                .andExpect(jsonPath("$.pacienteNombre").value("Carlos Sanchez"))
                .andExpect(jsonPath("$.profesionalNombre").value("Dra. Gomez"))
                .andExpect(jsonPath("$.especialidadNombre").value("Pediatria"))
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"));
    }

    @Test
    void postAppointment_slotNoDisponible_retorna409Conflict() throws Exception {
        ReservarCitaRequest req = new ReservarCitaRequest("slot-ocupado-uuid");

        when(appointmentService.reservarCita(any(ReservarCitaRequest.class), eq(PACIENTE_UUID), anyString()))
                .thenThrow(new CitaNoDisponibleException("El slot de atencion ya ha sido reservado o no esta disponible."));

        mockMvc.perform(post("/api/v1/appointments")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CITA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje").value("El slot de atencion ya ha sido reservado o no esta disponible."));
    }

    @Test
    void postAppointment_cuerpoInvalidoSlotBlanco_retorna400BadRequest() throws Exception {
        ReservarCitaRequest req = new ReservarCitaRequest("   ");

        mockMvc.perform(post("/api/v1/appointments")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("El identificador del slot es obligatorio.")));
    }

    // =========================================================================
    // CANCELACIÓN DE CITAS (PATCH /api/v1/appointments/{publicId}/cancel)
    // =========================================================================

    @Test
    void cancelAppointment_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/cancel")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void cancelAppointment_conRolPacienteYAnticipacion_retorna200Ok() throws Exception {
        CancelarCitaRequest req = new CancelarCitaRequest("Cancelacion anticipada");
        Instant ahora = Instant.now();
        CitaResponse mockResponse = new CitaResponse(
                "cita-uuid-1", "slot-uuid-1", "pac-uuid-1", "Carlos Sanchez",
                "prof-uuid-1", "Dra. Gomez", "esp-uuid-1", "Pediatria",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                ahora.plusSeconds(10800), ahora.plusSeconds(12000),
                "PRESENCIAL", "CANCELADA", null, null, ahora
        );

        when(appointmentService.cancelarCita(eq("cita-uuid-1"), any(CancelarCitaRequest.class), eq(PACIENTE_UUID), any(), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/cancel")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("cita-uuid-1"))
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void cancelAppointment_sinCuerpo_retorna200Ok() throws Exception {
        Instant ahora = Instant.now();
        CitaResponse mockResponse = new CitaResponse(
                "cita-uuid-1", "slot-uuid-1", "pac-uuid-1", "Carlos Sanchez",
                "prof-uuid-1", "Dra. Gomez", "esp-uuid-1", "Pediatria",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                ahora.plusSeconds(10800), ahora.plusSeconds(12000),
                "PRESENCIAL", "CANCELADA", null, null, ahora
        );

        when(appointmentService.cancelarCita(eq("cita-uuid-1"), any(), eq(PACIENTE_UUID), any(), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/cancel")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("cita-uuid-1"))
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void cancelAppointment_conRolProfesional_retorna200Ok() throws Exception {
        Instant ahora = Instant.now();
        CitaResponse mockResponse = new CitaResponse(
                "cita-uuid-1", "slot-uuid-1", "pac-uuid-1", "Carlos Sanchez",
                "prof-uuid-1", "Dra. Gomez", "esp-uuid-1", "Pediatria",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                ahora.plusSeconds(1800), ahora.plusSeconds(3000),
                "PRESENCIAL", "CANCELADA", null, null, ahora
        );

        when(appointmentService.cancelarCita(eq("cita-uuid-1"), any(), eq(PROFESIONAL_UUID), any(), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/cancel")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("cita-uuid-1"))
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void cancelAppointment_conRolAdministrador_retorna200Ok() throws Exception {
        Instant ahora = Instant.now();
        CitaResponse mockResponse = new CitaResponse(
                "cita-uuid-1", "slot-uuid-1", "pac-uuid-1", "Carlos Sanchez",
                "prof-uuid-1", "Dra. Gomez", "esp-uuid-1", "Pediatria",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                ahora.plusSeconds(600), ahora.plusSeconds(1800),
                "PRESENCIAL", "CANCELADA", null, null, ahora
        );

        when(appointmentService.cancelarCita(eq("cita-uuid-1"), any(), eq(ADMIN_UUID), any(), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/cancel")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("cita-uuid-1"))
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void cancelAppointment_pacienteCancelaCitaAjena_retorna403Forbidden() throws Exception {
        when(appointmentService.cancelarCita(eq("cita-uuid-1"), any(), eq(PACIENTE_UUID), any(), anyString()))
                .thenThrow(new AccesoNoAutorizadoException("No tiene autorizacion para cancelar una cita ajena."));

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/cancel")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"))
                .andExpect(jsonPath("$.mensaje").value("No tiene autorizacion para cancelar una cita ajena."));
    }

    @Test
    void cancelAppointment_cancelacionTardia_retorna400BadRequest() throws Exception {
        when(appointmentService.cancelarCita(eq("cita-uuid-1"), any(), eq(PACIENTE_UUID), any(), anyString()))
                .thenThrow(new DatosInvalidosException("La cancelacion por parte del paciente solo esta permitida hasta 2 horas antes de la cita."));

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/cancel")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("La cancelacion por parte del paciente solo esta permitida hasta 2 horas antes de la cita."));
    }

    @Test
    void cancelAppointment_transicionInvalida_retorna400BadRequest() throws Exception {
        when(appointmentService.cancelarCita(eq("cita-uuid-1"), any(), eq(PACIENTE_UUID), any(), anyString()))
                .thenThrow(new DatosInvalidosException("Transicion de estado no permitida de ATENDIDA a CANCELADA."));

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/cancel")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("Transicion de estado no permitida de ATENDIDA a CANCELADA."));
    }

    @Test
    void cancelAppointment_conAtencionVinculada_retorna409Conflict() throws Exception {
        when(appointmentService.cancelarCita(eq("cita-uuid-1"), any(), eq(PACIENTE_UUID), any(), anyString()))
                .thenThrow(new ConflictoOperacionException("No es posible cancelar una cita que ya cuenta con una atencion clinica vinculada."));

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/cancel")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_OPERACION"))
                .andExpect(jsonPath("$.mensaje").value("No es posible cancelar una cita que ya cuenta con una atencion clinica vinculada."));
    }

    @Test
    void cancelAppointment_motivoExcede255Caracteres_retorna400BadRequest() throws Exception {
        CancelarCitaRequest req = new CancelarCitaRequest("A".repeat(256));

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/cancel")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("El motivo de cancelacion no puede exceder 255 caracteres.")));
    }

    // =========================================================================
    // INASISTENCIA (NO_ASISTIO) - D4, T5
    // =========================================================================

    @Test
    void noShow_porProfesionalAsignado_retorna200Ok() throws Exception {
        CitaResponse mockResponse = new CitaResponse(
                "cita-uuid-1", "slot-uuid-1", "paciente-uuid-1", "Pepito Perez",
                "profesional-uuid-1", "Dr. House", "esp-uuid-1", "Medicina",
                "sede-uuid-1", "Sede Central", "Calle 123",
                Instant.now().minusSeconds(1800), Instant.now(), "PRESENCIAL",
                "NO_ASISTIO", null, null, Instant.now()
        );

        when(appointmentService.marcarNoAsistio(eq("cita-uuid-1"), eq(PROFESIONAL_UUID), any(), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/no-show")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("cita-uuid-1"))
                .andExpect(jsonPath("$.estado").value("NO_ASISTIO"));
    }

    @Test
    void noShow_porAdmin_retorna200Ok() throws Exception {
        CitaResponse mockResponse = new CitaResponse(
                "cita-uuid-1", "slot-uuid-1", "paciente-uuid-1", "Pepito Perez",
                "profesional-uuid-1", "Dr. House", "esp-uuid-1", "Medicina",
                "sede-uuid-1", "Sede Central", "Calle 123",
                Instant.now().minusSeconds(1800), Instant.now(), "PRESENCIAL",
                "NO_ASISTIO", null, null, Instant.now()
        );

        when(appointmentService.marcarNoAsistio(eq("cita-uuid-1"), eq(ADMIN_UUID), any(), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/no-show")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("cita-uuid-1"))
                .andExpect(jsonPath("$.estado").value("NO_ASISTIO"));
    }

    @Test
    void noShow_porPaciente_retorna403Forbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/no-show")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isForbidden());
    }

    @Test
    void noShow_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/no-show")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noShow_otroProfesional_retorna403Forbidden() throws Exception {
        when(appointmentService.marcarNoAsistio(eq("cita-uuid-1"), eq(PROFESIONAL_UUID), any(), anyString()))
                .thenThrow(new AccesoNoAutorizadoException("El profesional no tiene autorizacion para marcar inasistencia de citas de otro colega."));

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/no-show")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"))
                .andExpect(jsonPath("$.mensaje").value("El profesional no tiene autorizacion para marcar inasistencia de citas de otro colega."));
    }

    @Test
    void noShow_antesDeHoraInicio_retorna400BadRequest() throws Exception {
        when(appointmentService.marcarNoAsistio(eq("cita-uuid-1"), eq(PROFESIONAL_UUID), any(), anyString()))
                .thenThrow(new DatosInvalidosException("No es posible marcar inasistencia antes de la hora de inicio programada de la cita."));

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/no-show")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("No es posible marcar inasistencia antes de la hora de inicio programada de la cita."));
    }

    @Test
    void noShow_conAtencionVinculada_retorna409Conflict() throws Exception {
        when(appointmentService.marcarNoAsistio(eq("cita-uuid-1"), eq(PROFESIONAL_UUID), any(), anyString()))
                .thenThrow(new ConflictoOperacionException("No es posible marcar inasistencia para una cita que ya cuenta con una atencion clinica vinculada."));

        mockMvc.perform(patch("/api/v1/appointments/cita-uuid-1/no-show")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_OPERACION"))
                .andExpect(jsonPath("$.mensaje").value("No es posible marcar inasistencia para una cita que ya cuenta con una atencion clinica vinculada."));
    }

    @Test
    void noShow_citaInexistente_retorna404NotFound() throws Exception {
        when(appointmentService.marcarNoAsistio(eq("cita-fantasma"), eq(PROFESIONAL_UUID), any(), anyString()))
                .thenThrow(new com.meditriaje.exception.RecursoNoEncontradoException("Cita no encontrada."));

        mockMvc.perform(patch("/api/v1/appointments/cita-fantasma/no-show")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("Cita no encontrada.")));
    }
}
