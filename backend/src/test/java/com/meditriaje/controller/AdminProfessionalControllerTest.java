package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.admin.AdminProfessionalController;
import com.meditriaje.dto.admin.ActualizarProfesionalRequest;
import com.meditriaje.dto.admin.CrearProfesionalRequest;
import com.meditriaje.dto.admin.CrearProfesionalResponse;
import com.meditriaje.dto.admin.ProfesionalResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AdminProfessionalService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminProfessionalController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminProfessionalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminProfessionalService professionalService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_ADMIN = "token.admin.valido";
    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String ADMIN_UUID = "admin-uuid-1";

    @BeforeEach
    void setUpTokens() {
        when(jwtService.esValido(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_ADMIN)).thenReturn(ADMIN_UUID);
        when(jwtService.extraerRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        when(jwtService.esValido(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extraerPublicId(TOKEN_PACIENTE)).thenReturn("paciente-uuid-1");
        when(jwtService.extraerRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.esValido(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extraerRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));
    }

    // =========================================================================
    // SEGURIDAD Y CONTROL DE ACCESO
    // =========================================================================

    @Test
    void getProfessionals_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/professionals"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void getProfessionals_conRolPaciente_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/professionals")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void postProfessionals_conRolProfesional_retorna403Forbidden() throws Exception {
        CrearProfesionalRequest req = new CrearProfesionalRequest(
                "RM-12345", "Carlos", "Perez", "carlos@hospital.com", "esp-1"
        );
        mockMvc.perform(post("/api/v1/admin/professionals")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    // =========================================================================
    // OPERACIONES ADMINISTRADOR
    // =========================================================================

    @Test
    void postProfessionals_conRolAdmin_retorna201CreatedConPasswordTemporal() throws Exception {
        CrearProfesionalRequest req = new CrearProfesionalRequest(
                "RM-12345", "Carlos", "Perez", "carlos@hospital.com", "esp-med-int"
        );
        CrearProfesionalResponse resp = new CrearProfesionalResponse(
                "prof-uuid-1", "user-uuid-1", "RM-12345", "Carlos", "Perez",
                "carlos@hospital.com", "esp-med-int", "Medicina Interna",
                "Temporal123*#", true, Instant.now()
        );

        when(professionalService.altaProfesional(any(), eq(ADMIN_UUID), anyString())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/admin/professionals")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value("prof-uuid-1"))
                .andExpect(jsonPath("$.registroMedico").value("RM-12345"))
                .andExpect(jsonPath("$.passwordTemporal").value("Temporal123*#"))
                .andExpect(jsonPath("$.debeCambiarPassword").value(true));
    }

    @Test
    void postProfessionals_datosInvalidos_retorna400BadRequest() throws Exception {
        CrearProfesionalRequest reqInvalido = new CrearProfesionalRequest("", "", "", "no-es-email", "");

        mockMvc.perform(post("/api/v1/admin/professionals")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void postProfessionals_registroMedicoDuplicado_retorna400BadRequest() throws Exception {
        CrearProfesionalRequest req = new CrearProfesionalRequest(
                "RM-12345", "Carlos", "Perez", "carlos@hospital.com", "esp-med-int"
        );
        when(professionalService.altaProfesional(any(), eq(ADMIN_UUID), anyString()))
                .thenThrow(new DatosInvalidosException("El registro medico ya se encuentra registrado."));

        mockMvc.perform(post("/api/v1/admin/professionals")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("El registro medico ya se encuentra registrado."));
    }

    @Test
    void getProfessionals_conRolAdmin_retorna200YPaginado() throws Exception {
        ProfesionalResponse prof = new ProfesionalResponse(
                "prof-uuid-1", "user-uuid-1", "RM-12345", "Carlos", "Perez",
                "carlos@hospital.com", "esp-med-int", "Medicina Interna", "ACTIVO", false, Instant.now()
        );
        PaginatedResponse<ProfesionalResponse> pag = PaginatedResponse.of(List.of(prof), 0, 10, 1L);

        when(professionalService.listar(0, 10, null, null)).thenReturn(pag);

        mockMvc.perform(get("/api/v1/admin/professionals")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("prof-uuid-1"))
                .andExpect(jsonPath("$.content[0].registroMedico").value("RM-12345"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getProfessionalByPublicId_inexistente_retorna404NotFound() throws Exception {
        when(professionalService.obtenerPorPublicId("prof-no-existe"))
                .thenThrow(new RecursoNoEncontradoException("Profesional no encontrado: prof-no-existe"));

        mockMvc.perform(get("/api/v1/admin/professionals/prof-no-existe")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void putProfessional_conRolAdmin_retorna200OK() throws Exception {
        ActualizarProfesionalRequest req = new ActualizarProfesionalRequest("Carlos Alberto", "Perez Gomez", "esp-ped");
        ProfesionalResponse resp = new ProfesionalResponse(
                "prof-uuid-1", "user-uuid-1", "RM-12345", "Carlos Alberto", "Perez Gomez",
                "carlos@hospital.com", "esp-ped", "Pediatría", "ACTIVO", false, Instant.now()
        );

        when(professionalService.actualizarProfesional(eq("prof-uuid-1"), any(), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(put("/api/v1/admin/professionals/prof-uuid-1")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Carlos Alberto"))
                .andExpect(jsonPath("$.apellidos").value("Perez Gomez"))
                .andExpect(jsonPath("$.especialidadPublicId").value("esp-ped"));
    }

    @Test
    void patchDeactivateProfessional_conRolAdmin_retorna200OK() throws Exception {
        ProfesionalResponse resp = new ProfesionalResponse(
                "prof-uuid-1", "user-uuid-1", "RM-12345", "Carlos", "Perez",
                "carlos@hospital.com", "esp-med-int", "Medicina Interna", "INACTIVO", false, Instant.now()
        );
        when(professionalService.cambiarEstado(eq("prof-uuid-1"), eq("INACTIVO"), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/v1/admin/professionals/prof-uuid-1/deactivate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
    }

    @Test
    void patchActivateProfessional_conRolAdmin_retorna200OK() throws Exception {
        ProfesionalResponse resp = new ProfesionalResponse(
                "prof-uuid-1", "user-uuid-1", "RM-12345", "Carlos", "Perez",
                "carlos@hospital.com", "esp-med-int", "Medicina Interna", "ACTIVO", false, Instant.now()
        );
        when(professionalService.cambiarEstado(eq("prof-uuid-1"), eq("ACTIVO"), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/v1/admin/professionals/prof-uuid-1/activate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }
}
