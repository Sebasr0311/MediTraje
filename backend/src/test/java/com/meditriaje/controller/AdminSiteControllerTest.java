package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.admin.AdminSiteController;
import com.meditriaje.dto.admin.ActualizarSedeRequest;
import com.meditriaje.dto.admin.CrearSedeRequest;
import com.meditriaje.dto.admin.SedeResponse;
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

@WebMvcTest(controllers = AdminSiteController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminSiteControllerTest {

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
    void getSites_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sites"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void getSites_conRolPaciente_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sites")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void postSites_conRolProfesional_retorna403Forbidden() throws Exception {
        CrearSedeRequest req = new CrearSedeRequest("inst-1", "Sede Central", "Calle 1 #2-3", "Cali");
        mockMvc.perform(post("/api/v1/admin/sites")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void postSites_conRolAdmin_retorna201Created() throws Exception {
        CrearSedeRequest req = new CrearSedeRequest("inst-uuid-1", "Sede Chapinero", "Cra 7 #53-10", "Bogotá");
        SedeResponse resp = new SedeResponse("sede-uuid-1", "inst-uuid-1", "IPS Salud Total", "Sede Chapinero", "Cra 7 #53-10", "Bogotá", "ACTIVO");

        when(catalogService.crearSede(any(), eq(ADMIN_UUID), anyString())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/admin/sites")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value("sede-uuid-1"))
                .andExpect(jsonPath("$.institucionPublicId").value("inst-uuid-1"))
                .andExpect(jsonPath("$.institucionRazonSocial").value("IPS Salud Total"))
                .andExpect(jsonPath("$.nombre").value("Sede Chapinero"))
                .andExpect(jsonPath("$.ciudad").value("Bogotá"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    void postSites_datosInvalidos_retorna400BadRequest() throws Exception {
        CrearSedeRequest reqInvalido = new CrearSedeRequest("", "", "", "");

        mockMvc.perform(post("/api/v1/admin/sites")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void postSites_institucionInactiva_retorna400BadRequest() throws Exception {
        CrearSedeRequest req = new CrearSedeRequest("inst-inactiva", "Sede Norte", "Calle 100", "Bogotá");
        when(catalogService.crearSede(any(), eq(ADMIN_UUID), anyString()))
                .thenThrow(new DatosInvalidosException("No se pueden crear sedes en una institución inactiva."));

        mockMvc.perform(post("/api/v1/admin/sites")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("No se pueden crear sedes en una institución inactiva."));
    }

    @Test
    void getSites_conRolAdmin_retorna200YPaginado() throws Exception {
        SedeResponse sede = new SedeResponse("sede-uuid-1", "inst-uuid-1", "IPS Salud Total", "Sede Centro", "Cra 10 #15-20", "Bogotá", "ACTIVO");
        PaginatedResponse<SedeResponse> pag = PaginatedResponse.of(List.of(sede), 0, 10, 1L);

        when(catalogService.listarSedes(0, 10, null, null)).thenReturn(pag);

        mockMvc.perform(get("/api/v1/admin/sites")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("sede-uuid-1"))
                .andExpect(jsonPath("$.content[0].nombre").value("Sede Centro"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getSiteByPublicId_inexistente_retorna404NotFound() throws Exception {
        when(catalogService.obtenerSede("sede-no-existe"))
                .thenThrow(new RecursoNoEncontradoException("Sede: sede-no-existe"));

        mockMvc.perform(get("/api/v1/admin/sites/sede-no-existe")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void putSite_conRolAdmin_retorna200OK() throws Exception {
        ActualizarSedeRequest req = new ActualizarSedeRequest("Sede Chapinero Norte", "Cra 7 #55-20", "Bogotá");
        SedeResponse resp = new SedeResponse("sede-uuid-1", "inst-uuid-1", "IPS", "Sede Chapinero Norte", "Cra 7 #55-20", "Bogotá", "ACTIVO");

        when(catalogService.actualizarSede(eq("sede-uuid-1"), any(), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(put("/api/v1/admin/sites/sede-uuid-1")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Sede Chapinero Norte"))
                .andExpect(jsonPath("$.direccion").value("Cra 7 #55-20"));
    }

    @Test
    void patchDeactivateSite_conRolAdmin_retorna200OK() throws Exception {
        SedeResponse resp = new SedeResponse("sede-uuid-1", "inst-uuid-1", "IPS", "Sede", "Dir", "Bogotá", "INACTIVO");
        when(catalogService.desactivarSede(eq("sede-uuid-1"), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/v1/admin/sites/sede-uuid-1/deactivate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
    }

    @Test
    void patchActivateSite_conRolAdmin_retorna200OK() throws Exception {
        SedeResponse resp = new SedeResponse("sede-uuid-1", "inst-uuid-1", "IPS", "Sede", "Dir", "Bogotá", "ACTIVO");
        when(catalogService.activarSede(eq("sede-uuid-1"), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/v1/admin/sites/sede-uuid-1/activate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }
}
