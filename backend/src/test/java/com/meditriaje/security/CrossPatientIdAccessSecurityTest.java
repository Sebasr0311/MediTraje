package com.meditriaje.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.AppointmentController;
import com.meditriaje.controller.ClinicalAllergyController;
import com.meditriaje.controller.ClinicalPatientHistoryController;
import com.meditriaje.controller.PacienteController;
import com.meditriaje.controller.PatientAllergyController;
import com.meditriaje.controller.PrescriptionController;
import com.meditriaje.controller.TriajeController;
import com.meditriaje.dto.allergy.InactivarAlergiaRequest;
import com.meditriaje.dto.appointment.CancelarCitaRequest;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.service.AllergyService;
import com.meditriaje.service.AppointmentService;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.service.DispensationService;
import com.meditriaje.service.EmergencyQrService;
import com.meditriaje.service.FollowUpService;
import com.meditriaje.service.PacienteService;
import com.meditriaje.service.PrescriptionService;
import com.meditriaje.service.TriajeService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T6 (Brechas MVP #1): Pruebas exhaustivas de control de acceso cruzado por ID.
 * Demuestra que un paciente autenticado (B) no puede acceder ni alterar
 * recursos pertenecientes a otro paciente (A): triaje, citas, historia clínica,
 * recetas, alergias y accesos QR temporales.
 */
@WebMvcTest(controllers = {
        TriajeController.class,
        AppointmentController.class,
        PrescriptionController.class,
        ClinicalPatientHistoryController.class,
        ClinicalAllergyController.class,
        PatientAllergyController.class,
        PacienteController.class
})
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class CrossPatientIdAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private TriajeService triajeService;

    @MockBean
    private AppointmentService appointmentService;

    @MockBean
    private PrescriptionService prescriptionService;

    @MockBean
    private ClinicalAttentionService clinicalAttentionService;

    @MockBean
    private AllergyService allergyService;

    @MockBean
    private PacienteService pacienteService;

    @MockBean
    private FollowUpService followUpService;

    @MockBean
    private EmergencyQrService emergencyQrService;

    @MockBean
    private DispensationService dispensationService;

    private static final String TOKEN_PACIENTE_B = "jwt.paciente.b";
    private static final String PACIENTE_B_UUID = "usr-paciente-b-uuid";

    private static final String ID_TRIAJE_A = "triaje-paciente-a-123";
    private static final String ID_CITA_A = "cita-paciente-a-456";
    private static final String ID_RECETA_A = "receta-paciente-a-789";
    private static final String ID_PACIENTE_A = "paciente-a-uuid";
    private static final String ID_ALERGIA_A = "alergia-paciente-a-111";
    private static final String ID_QR_A = "qr-paciente-a-222";

    @BeforeEach
    void setUp() {
        when(jwtService.esValido(TOKEN_PACIENTE_B)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PACIENTE_B)).thenReturn(PACIENTE_B_UUID);
        when(jwtService.extraerRoles(TOKEN_PACIENTE_B)).thenReturn(List.of("ROLE_PACIENTE"));
    }

    private Cookie cookieAuth() {
        return new Cookie("access_token", TOKEN_PACIENTE_B);
    }

    @Test
    @DisplayName("Triaje: Paciente B intentando leer triaje de Paciente A recibe 403 Forbidden")
    void triaje_pacienteBConsultaTriajeDeA_retorna403() throws Exception {
        when(triajeService.obtenerPorPublicId(eq(ID_TRIAJE_A), eq(PACIENTE_B_UUID), any()))
                .thenThrow(new AccesoNoAutorizadoException("No tiene autorizacion para acceder al triaje de otro paciente."));

        mockMvc.perform(get("/api/v1/triage/{publicId}", ID_TRIAJE_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }

    @Test
    @DisplayName("Citas: Paciente B intentando cancelar cita de Paciente A recibe 403 Forbidden")
    void citas_pacienteBCancelaCitaDeA_retorna403() throws Exception {
        CancelarCitaRequest req = new CancelarCitaRequest("Cancelacion arbitraria ajena");
        when(appointmentService.cancelarCita(eq(ID_CITA_A), any(), eq(PACIENTE_B_UUID), any(), anyString()))
                .thenThrow(new AccesoNoAutorizadoException("No tiene autorizacion para cancelar una cita ajena."));

        mockMvc.perform(patch("/api/v1/appointments/{publicId}/cancel", ID_CITA_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }

    @Test
    @DisplayName("Citas (No-Show): Paciente B intentando marcar no-asistio en cita recibe 403 Forbidden")
    void citas_pacienteBIntentaMarcarNoShow_retorna403() throws Exception {
        mockMvc.perform(patch("/api/v1/appointments/{publicId}/no-show", ID_CITA_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Recetas: Paciente B intentando leer receta de Paciente A recibe 403 Forbidden")
    void recetas_pacienteBConsultaRecetaDeA_retorna403() throws Exception {
        when(prescriptionService.obtenerPorPublicId(eq(ID_RECETA_A), eq(PACIENTE_B_UUID), any()))
                .thenThrow(new AccesoNoAutorizadoException("No tiene autorizacion para acceder a la receta de otro paciente."));

        mockMvc.perform(get("/api/v1/prescriptions/{publicId}", ID_RECETA_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }

    @Test
    @DisplayName("Historia Clínica: Paciente B intentando consultar historial clínico de Paciente A recibe 403 Forbidden")
    void historia_pacienteBConsultaHistoriaDeA_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/clinical/patients/{patientPublicId}/history", ID_PACIENTE_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Alergias Clínicas: Paciente B intentando consultar alergias de Paciente A vía endpoint asistencial recibe 403 Forbidden")
    void alergias_pacienteBConsultaAlergiasDeA_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/clinical/patients/{patientPublicId}/allergies", ID_PACIENTE_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Alergias Paciente: Paciente B intentando inactivar alergia de Paciente A recibe 403 Forbidden")
    void alergias_pacienteBInactivaAlergiaDeA_retorna403() throws Exception {
        InactivarAlergiaRequest req = new InactivarAlergiaRequest("Intento de inactivacion no autorizada");
        doThrow(new AccesoNoAutorizadoException("No tiene autorizacion para inactivar una alergia de otro paciente."))
                .when(allergyService).inactivarMiAlergia(eq(ID_ALERGIA_A), any(), eq(PACIENTE_B_UUID), anyString());

        mockMvc.perform(patch("/api/v1/patients/me/allergies/{publicId}/deactivate", ID_ALERGIA_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }

    @Test
    @DisplayName("QR de Emergencia: Paciente B intentando revocar acceso QR de Paciente A recibe 403 Forbidden")
    void qr_pacienteBRevocaQrDeA_retorna403() throws Exception {
        doThrow(new AccesoNoAutorizadoException("No tiene autorizacion para revocar un acceso QR ajeno."))
                .when(emergencyQrService).revocarAccesoQr(eq(ID_QR_A), eq(PACIENTE_B_UUID), anyString());

        mockMvc.perform(patch("/api/v1/patients/me/emergency-qr/{publicId}/revoke", ID_QR_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }
}
