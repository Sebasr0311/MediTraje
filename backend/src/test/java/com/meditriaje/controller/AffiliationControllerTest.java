package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.affiliation.*;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.model.EntidadEps;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AffiliationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AffiliationController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AffiliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AffiliationService affiliationService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_ADMIN = "token.admin.valido";
    private static final String TOKEN_ENFERMERIA = "token.enfermeria.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String TOKEN_PACIENTE = "token.paciente.valido";

    private final Instant now = Instant.parse("2026-03-30T10:00:00Z");

    @BeforeEach
    void setUp() {
        when(jwtService.esValido(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ADMIN)).thenReturn("usr-admin-uuid");
        when(jwtService.extraerRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        when(jwtService.esValido(TOKEN_ENFERMERIA)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ENFERMERIA)).thenReturn("usr-enf-uuid");
        when(jwtService.extraerRoles(TOKEN_ENFERMERIA)).thenReturn(List.of("ROLE_ENFERMERIA"));

        when(jwtService.esValido(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PROFESIONAL)).thenReturn("usr-prof-uuid");
        when(jwtService.extraerRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));

        when(jwtService.esValido(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PACIENTE)).thenReturn("usr-pac-uuid");
        when(jwtService.extraerRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));
    }

    private Cookie cookie(String token) {
        return new Cookie("access_token", token);
    }

    @Test
    @DisplayName("SEC-01: Carga de archivo anónima retorna 401 Unauthorized")
    void uploadPreview_sinSesion_retorna401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/affiliations/upload-preview")
                        .file(file)
                        .param("epsPublicId", "eps-1")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SEC-02: Rol PACIENTE intentando cargar archivo masivo retorna 403 Forbidden")
    void uploadPreview_rolPaciente_retorna403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/affiliations/upload-preview")
                        .file(file)
                        .param("epsPublicId", "eps-1")
                        .cookie(cookie(TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A02: Administrador cargando preview XLSX retorna 200 OK con desglose")
    void uploadPreview_administrador_retorna200Ok() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "afiliados.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        LotePreviewResponse resp = new LotePreviewResponse(
                "lote-123", "afiliados.xlsx", "EPS001", "Nueva EPS", 10, 8, 2, "PREVIEW", List.of()
        );
        when(affiliationService.procesarArchivoExcelPreview(any(), eq("afiliados.xlsx"), eq("eps-123"), eq("usr-admin-uuid"), anyString()))
                .thenReturn(resp);

        mockMvc.perform(multipart("/api/v1/affiliations/upload-preview")
                        .file(file)
                        .param("epsPublicId", "eps-123")
                        .cookie(cookie(TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotePublicId").value("lote-123"))
                .andExpect(jsonPath("$.filasValidas").value(8))
                .andExpect(jsonPath("$.filasFallidas").value(2));
    }

    @Test
    @DisplayName("A05: Rol ENFERMERIA consultando afiliación de paciente retorna 200 OK")
    void consultarAfiliacion_enfermeria_retorna200Ok() throws Exception {
        AfiliacionResponse resp = new AfiliacionResponse(
                "afil-1", "pac-1", "CC", "1065123456", "eps-1", "EPS001", "Nueva EPS",
                "CONTRIBUTIVO", "COTIZANTE", "ACTIVO", null, "EXCEL_IMPORT", now
        );
        when(affiliationService.consultarAfiliacion(eq("CC"), eq("1065123456"), eq("usr-enf-uuid"), anyString()))
                .thenReturn(resp);

        mockMvc.perform(get("/api/v1/affiliations/patients/CC/1065123456")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroDocumento").value("1065123456"))
                .andExpect(jsonPath("$.regimen").value("CONTRIBUTIVO"))
                .andExpect(jsonPath("$.epsNombre").value("Nueva EPS"));
    }

    @Test
    @DisplayName("C02: Rol PROFESIONAL registrando ausencia médica retorna 201 Created")
    void registrarAusencia_profesional_retorna201Created() throws Exception {
        RegistrarAusenciaRequest req = new RegistrarAusenciaRequest(
                "prof-1", now, now.plusSeconds(3600), "Capacitación Quirúrgica"
        );
        AusenciaResponse resp = new AusenciaResponse(
                "aus-1", "prof-1", "Dr. Pérez", now, now.plusSeconds(3600), "Capacitación Quirúrgica", "ACTIVA"
        );
        when(affiliationService.registrarAusenciaMedica(any(), eq("usr-prof-uuid"), anyString()))
                .thenReturn(resp);

        mockMvc.perform(post("/api/v1/affiliations/absences")
                        .cookie(cookie(TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value("aus-1"))
                .andExpect(jsonPath("$.motivo").value("Capacitación Quirúrgica"));
    }

    @Test
    @DisplayName("C03: Rol PACIENTE registrando representación legal retorna 201 Created")
    void registrarRepresentacion_paciente_retorna201Created() throws Exception {
        RepresentacionLegalRequest req = new RepresentacionLegalRequest(
                "pac-hijo", "pac-padre", "PADRE", "RC-456"
        );
        RepresentacionLegalResponse resp = new RepresentacionLegalResponse(
                "rep-1", "pac-hijo", "Juan Hijo", "RC-111", "pac-padre", "Carlos Padre", "CC-222", "PADRE", true
        );
        when(affiliationService.registrarRepresentacionLegal(any(), eq("usr-pac-uuid"), anyString()))
                .thenReturn(resp);

        mockMvc.perform(post("/api/v1/affiliations/guardians")
                        .cookie(cookie(TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value("rep-1"))
                .andExpect(jsonPath("$.parentesco").value("PADRE"));
    }
}
