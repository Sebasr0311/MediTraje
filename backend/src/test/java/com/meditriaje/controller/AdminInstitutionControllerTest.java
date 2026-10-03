package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.admin.AdminInstitutionController;
import com.meditriaje.dto.admin.ActualizarInstitucionRequest;
import com.meditriaje.dto.admin.CrearInstitucionRequest;
import com.meditriaje.dto.admin.InstitucionResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AdminCatalogService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminInstitutionController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminInstitutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminCatalogService catalogService;

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

    @Test
    void getInstitutions_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/institutions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void getInstitutions_conRolPaciente_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/institutions")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void postInstitutions_conRolProfesional_retorna403Forbidden() throws Exception {
        CrearInstitucionRequest req = new CrearInstitucionRequest("900111222-3", "Clinica EPS");
        mockMvc.perform(post("/api/v1/admin/institutions")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void postInstitutions_conRolAdmin_retorna201Created() throws Exception {
        CrearInstitucionRequest req = new CrearInstitucionRequest("900111222-3", "Clínica del Norte");
        InstitucionResponse resp = new InstitucionResponse("inst-uuid-1", "900111222-3", "Clínica del Norte", "ACTIVO", Instant.now());

        when(catalogService.crearInstitucion(any(), eq(ADMIN_UUID), anyString())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/admin/institutions")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value("inst-uuid-1"))
                .andExpect(jsonPath("$.nit").value("900111222-3"))
                .andExpect(jsonPath("$.razonSocial").value("Clínica del Norte"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    void postInstitutions_datosInvalidos_retorna400BadRequest() throws Exception {
        CrearInstitucionRequest reqInvalido = new CrearInstitucionRequest("", "");

        mockMvc.perform(post("/api/v1/admin/institutions")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void postInstitutions_nitDuplicado_retorna400BadRequest() throws Exception {
        CrearInstitucionRequest req = new CrearInstitucionRequest("900111222-3", "Clínica del Norte");
        when(catalogService.crearInstitucion(any(), eq(ADMIN_UUID), anyString()))
                .thenThrow(new DatosInvalidosException("Ya existe una institución con el NIT: 900111222-3"));

        mockMvc.perform(post("/api/v1/admin/institutions")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("Ya existe una institución con el NIT: 900111222-3"));
    }

    @Test
    void getInstitutions_conRolAdmin_retorna200YPaginado() throws Exception {
        InstitucionResponse inst = new InstitucionResponse("inst-uuid-1", "900111222-3", "Clínica", "ACTIVO", Instant.now());
        PaginatedResponse<InstitucionResponse> pag = PaginatedResponse.of(List.of(inst), 0, 10, 1L);

        when(catalogService.listarInstituciones(0, 10, null)).thenReturn(pag);

        mockMvc.perform(get("/api/v1/admin/institutions")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("inst-uuid-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getInstitutionByPublicId_inexistente_retorna404NotFound() throws Exception {
        when(catalogService.obtenerInstitucion("inst-no-existe"))
                .thenThrow(new RecursoNoEncontradoException("Institución: inst-no-existe"));

        mockMvc.perform(get("/api/v1/admin/institutions/inst-no-existe")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void putInstitution_conRolAdmin_retorna200OK() throws Exception {
        ActualizarInstitucionRequest req = new ActualizarInstitucionRequest("Clínica del Norte S.A.");
        InstitucionResponse resp = new InstitucionResponse("inst-uuid-1", "900111222-3", "Clínica del Norte S.A.", "ACTIVO", Instant.now());

        when(catalogService.actualizarInstitucion(eq("inst-uuid-1"), any(), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(put("/api/v1/admin/institutions/inst-uuid-1")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razonSocial").value("Clínica del Norte S.A."));
    }

    @Test
    void patchDeactivateInstitution_conRolAdmin_retorna200OK() throws Exception {
        InstitucionResponse resp = new InstitucionResponse("inst-uuid-1", "900111222-3", "Clínica", "INACTIVO", Instant.now());
        when(catalogService.desactivarInstitucion(eq("inst-uuid-1"), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/v1/admin/institutions/inst-uuid-1/deactivate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
    }

    @Test
    void patchActivateInstitution_conRolAdmin_retorna200OK() throws Exception {
        InstitucionResponse resp = new InstitucionResponse("inst-uuid-1", "900111222-3", "Clínica", "ACTIVO", Instant.now());
        when(catalogService.activarInstitucion(eq("inst-uuid-1"), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/v1/admin/institutions/inst-uuid-1/activate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }
}
