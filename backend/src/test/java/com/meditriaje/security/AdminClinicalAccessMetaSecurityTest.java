package com.meditriaje.security;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.ClinicalAllergyController;
import com.meditriaje.controller.ClinicalAttentionController;
import com.meditriaje.controller.ClinicalBreakGlassController;
import com.meditriaje.controller.ClinicalPatientHistoryController;
import com.meditriaje.controller.FollowUpController;
import com.meditriaje.controller.PacienteController;
import com.meditriaje.controller.PatientAllergyController;
import com.meditriaje.controller.PharmacyDispensationController;
import com.meditriaje.controller.PrescriptionController;
import com.meditriaje.controller.ProfessionalAgendaController;
import com.meditriaje.controller.TriajeController;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.service.AllergyService;
import com.meditriaje.service.AppointmentService;
import com.meditriaje.service.BreakGlassService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T6 (Brechas MVP #3): Meta-test de seguridad que audita y garantiza que
 * {@code ROLE_ADMINISTRADOR} NUNCA tiene acceso a rutas clínicas ni contenido de pacientes
 * (ADR-007, ADR-011).
 *
 * <p>Verifica:</p>
 * <ol>
 *   <li>Auditoría estática por reflexión: ningún endpoint clínico expone {@code ROLE_ADMINISTRADOR} en {@code @PreAuthorize}.</li>
 *   <li>Auditoría dinámica vía MockMvc: toda invocación autenticada como Administrador recibe 403 Forbidden.</li>
 * </ol>
 */
@WebMvcTest(controllers = {
        ClinicalAttentionController.class,
        ClinicalBreakGlassController.class,
        ClinicalPatientHistoryController.class,
        ClinicalAllergyController.class,
        PatientAllergyController.class,
        PacienteController.class,
        PrescriptionController.class,
        FollowUpController.class,
        PharmacyDispensationController.class,
        ProfessionalAgendaController.class,
        TriajeController.class
})
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminClinicalAccessMetaSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private ClinicalAttentionService clinicalAttentionService;

    @MockBean
    private BreakGlassService breakGlassService;

    @MockBean
    private AllergyService allergyService;

    @MockBean
    private PacienteService pacienteService;

    @MockBean
    private PrescriptionService prescriptionService;

    @MockBean
    private FollowUpService followUpService;

    @MockBean
    private EmergencyQrService emergencyQrService;

    @MockBean
    private DispensationService dispensationService;

    @MockBean
    private AppointmentService appointmentService;

    @MockBean
    private TriajeService triajeService;

    private static final String TOKEN_ADMIN = "jwt.admin.meta";
    private static final String ADMIN_UUID = "usr-admin-uuid";

    @BeforeEach
    void setUp() {
        when(jwtService.esValido(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ADMIN)).thenReturn(ADMIN_UUID);
        when(jwtService.extraerRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
    }

    private Cookie adminCookie() {
        return new Cookie("access_token", TOKEN_ADMIN);
    }

    @Test
    @DisplayName("Meta-inspección: Ningún controlador clínico autoriza a ROLE_ADMINISTRADOR en @PreAuthorize")
    void metaInspeccion_ningunControladorClinicoAutorizaAdmin() {
        List<Class<?>> controladoresClinicos = List.of(
                ClinicalAttentionController.class,
                ClinicalBreakGlassController.class,
                ClinicalPatientHistoryController.class,
                ClinicalAllergyController.class,
                PatientAllergyController.class,
                PacienteController.class,
                PrescriptionController.class,
                FollowUpController.class,
                PharmacyDispensationController.class,
                ProfessionalAgendaController.class,
                TriajeController.class
        );

        for (Class<?> clazz : controladoresClinicos) {
            PreAuthorize classPreAuth = clazz.getAnnotation(PreAuthorize.class);
            if (classPreAuth != null) {
                assertThat(classPreAuth.value())
                        .as("Clase clínica %s no debe otorgar acceso a ROLE_ADMINISTRADOR", clazz.getSimpleName())
                        .doesNotContain("ROLE_ADMINISTRADOR");
            }

            for (Method method : clazz.getDeclaredMethods()) {
                PreAuthorize methodPreAuth = method.getAnnotation(PreAuthorize.class);
                if (methodPreAuth != null) {
                    assertThat(methodPreAuth.value())
                            .as("Método %s.%s() no debe otorgar acceso a ROLE_ADMINISTRADOR", clazz.getSimpleName(), method.getName())
                            .doesNotContain("ROLE_ADMINISTRADOR");
                }
            }
        }
    }

    // --- Pruebas dinámicas HTTP: ROLE_ADMINISTRADOR recibe 403 en cada módulo clínico ---

    @Test
    @DisplayName("Atención médica: Admin recibe 403 en iniciar, cerrar y enmiendas")
    void atencionMedica_adminRecibe403() throws Exception {
        mockMvc.perform(post("/api/v1/attentions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"citaPublicId\":\"c-123\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/attentions/a-123/close")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnosticoCie10Codigo\":\"J00\",\"motivoConsulta\":\"Dolor\",\"evolucion\":\"Evolucion\",\"indicaciones\":\"Reposo\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/attentions/a-123/amendments")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Correccion aclaratoria\",\"contenido\":\"Texto detallado enmienda\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Break-Glass y acceso excepcional: Admin recibe 403")
    void breakGlass_adminRecibe403() throws Exception {
        mockMvc.perform(post("/api/v1/clinical/break-glass")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacientePublicId\":\"p-1\",\"motivo\":\"Emergencia medica con justificacion superior a 20 chars\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/clinical/break-glass/active")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Historia clínica y alergias profesionales: Admin recibe 403")
    void historiaYAlergiasProfesionales_adminRecibe403() throws Exception {
        mockMvc.perform(get("/api/v1/clinical/patients/p-100/history")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/clinical/patients/p-100/allergies")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/clinical/allergies/ale-100/deactivate")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Error de diagnostico confirmado\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Portal personal del paciente (/patients/me/**): Admin recibe 403")
    void portalPersonalPaciente_adminRecibe403() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me")
                        .cookie(adminCookie()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/patients/me/history")
                        .cookie(adminCookie()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/patients/me/prescriptions")
                        .cookie(adminCookie()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/patients/me/allergies")
                        .cookie(adminCookie()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/patients/me/emergency-qr")
                        .cookie(adminCookie()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/patients/me/follow-ups")
                        .cookie(adminCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Prescripciones: Admin recibe 403 al intentar emitir y al consultar receta")
    void prescripciones_adminRecibe403() throws Exception {
        mockMvc.perform(post("/api/v1/prescriptions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"atencionPublicId\":\"atn-1\",\"detalles\":[{\"medicamentoPublicId\":\"m-1\",\"dosis\":\"500mg\",\"frecuencia\":\"8h\",\"duracionDias\":3,\"cantidad\":1,\"indicaciones\":\"Tomar con agua\"}]}"))
                .andExpect(status().isForbidden());

        when(prescriptionService.obtenerPorPublicId(eq("rx-1"), eq(ADMIN_UUID), any()))
                .thenThrow(new AccesoNoAutorizadoException("El personal administrativo no tiene acceso a recetas ni contenido clinico."));

        mockMvc.perform(get("/api/v1/prescriptions/rx-1")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Seguimiento y Farmacia: Admin recibe 403")
    void seguimientoYFarmacia_adminRecibe403() throws Exception {
        mockMvc.perform(post("/api/v1/attentions/atn-1/follow-ups")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"CONTROL_SINTOMAS\",\"indicaciones\":\"Reposo en cama e hidratacion adecuada\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/pharmacy/dispensations")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recetaPublicId\":\"rx-1\",\"sedePublicId\":\"s-1\",\"detalles\":[{\"medicamentoPublicId\":\"med-1\",\"cantidadEntregada\":1}]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/pharmacy/prescriptions")
                        .cookie(adminCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Agenda Médica y Triaje: Admin recibe 403")
    void agendaMedicaYTriaje_adminRecibe403() throws Exception {
        mockMvc.perform(get("/api/v1/professionals/me/agenda")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sintomas\":[{\"codigo\":\"FIEBRE\",\"duracionHoras\":2.0,\"intensidad\":5}]}"))
                .andExpect(status().isForbidden());
    }
}
