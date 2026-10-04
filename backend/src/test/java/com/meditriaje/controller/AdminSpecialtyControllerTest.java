package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.admin.AdminSpecialtyController;
import com.meditriaje.dto.admin.ActualizarEspecialidadRequest;
import com.meditriaje.dto.admin.CrearEspecialidadRequest;
import com.meditriaje.dto.admin.EspecialidadResponse;
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

@WebMvcTest(controllers = AdminSpecialtyController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminSpecialtyControllerTest {

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

    // =========================================================================
    // SEGURIDAD Y AUTENTICACIÓN
    // =========================================================================

    @Test
    void getSpecialties_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/specialties"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void getSpecialties_conRolPaciente_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/specialties")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void postSpecialties_conRolProfesional_retorna403Forbidden() throws Exception {
        CrearEspecialidadRequest req = new CrearEspecialidadRequest("Cardiología", 30);
        mockMvc.perform(post("/api/v1/admin/specialties")
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
    void postSpecialties_conRolAdmin_retorna201Created() throws Exception {
        CrearEspecialidadRequest req = new CrearEspecialidadRequest("Medicina Interna", 20);
        EspecialidadResponse resp = new EspecialidadResponse("esp-uuid-1", "Medicina Interna", 20, "ACTIVO");

        when(catalogService.crearEspecialidad(any(), eq(ADMIN_UUID), anyString())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/admin/specialties")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value("esp-uuid-1"))
                .andExpect(jsonPath("$.nombre").value("Medicina Interna"))
                .andExpect(jsonPath("$.duracionSlotMin").value(20))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    void postSpecialties_datosInvalidos_retorna400BadRequest() throws Exception {
        // Nombre vacío y duración fuera de rango
        CrearEspecialidadRequest reqInvalido = new CrearEspecialidadRequest("", 2);

        mockMvc.perform(post("/api/v1/admin/specialties")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void postSpecialties_nombreDuplicado_retorna400BadRequest() throws Exception {
        CrearEspecialidadRequest req = new CrearEspecialidadRequest("Cardiología", 20);
        when(catalogService.crearEspecialidad(any(), eq(ADMIN_UUID), anyString()))
                .thenThrow(new DatosInvalidosException("Ya existe una especialidad con el nombre: Cardiología"));

        mockMvc.perform(post("/api/v1/admin/specialties")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("Ya existe una especialidad con el nombre: Cardiología"));
    }

    @Test
    void getSpecialties_conRolAdmin_retorna200YPaginado() throws Exception {
        EspecialidadResponse esp = new EspecialidadResponse("esp-uuid-1", "Pediatría", 30, "ACTIVO");
        PaginatedResponse<EspecialidadResponse> pag = PaginatedResponse.of(List.of(esp), 0, 10, 1L);

        when(catalogService.listarEspecialidades(0, 10, null)).thenReturn(pag);

        mockMvc.perform(get("/api/v1/admin/specialties")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("esp-uuid-1"))
                .andExpect(jsonPath("$.content[0].nombre").value("Pediatría"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getSpecialtyByPublicId_inexistente_retorna404NotFound() throws Exception {
        when(catalogService.obtenerEspecialidad("esp-no-existe"))
                .thenThrow(new RecursoNoEncontradoException("Especialidad: esp-no-existe"));

        mockMvc.perform(get("/api/v1/admin/specialties/esp-no-existe")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void putSpecialty_conRolAdmin_retorna200OK() throws Exception {
        ActualizarEspecialidadRequest req = new ActualizarEspecialidadRequest("Pediatría Avanzada", 25);
        EspecialidadResponse resp = new EspecialidadResponse("esp-uuid-1", "Pediatría Avanzada", 25, "ACTIVO");

        when(catalogService.actualizarEspecialidad(eq("esp-uuid-1"), any(), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(put("/api/v1/admin/specialties/esp-uuid-1")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Pediatría Avanzada"))
                .andExpect(jsonPath("$.duracionSlotMin").value(25));
    }

    @Test
    void patchDeactivateSpecialty_conRolAdmin_retorna200OK() throws Exception {
        EspecialidadResponse resp = new EspecialidadResponse("esp-uuid-1", "Pediatría", 30, "INACTIVO");
        when(catalogService.desactivarEspecialidad(eq("esp-uuid-1"), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/v1/admin/specialties/esp-uuid-1/deactivate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
    }

    @Test
    void patchActivateSpecialty_conRolAdmin_retorna200OK() throws Exception {
        EspecialidadResponse resp = new EspecialidadResponse("esp-uuid-1", "Pediatría", 30, "ACTIVO");
        when(catalogService.activarEspecialidad(eq("esp-uuid-1"), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/v1/admin/specialties/esp-uuid-1/activate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }
}
