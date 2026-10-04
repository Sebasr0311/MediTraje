package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.admin.AdminSlotController;
import com.meditriaje.dto.admin.GenerarSlotsRequest;
import com.meditriaje.dto.admin.GenerarSlotsResponse;
import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.SlotGeneratorService;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSlotController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminSlotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SlotGeneratorService slotGeneratorService;

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
    // SEGURIDAD Y CONTROL DE ACCESO (HU-10, ADR-002)
    // =========================================================================

    @Test
    void getSlots_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/slots"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void getSlots_conRolPaciente_retorna403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/slots")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void postGenerate_conRolProfesional_retorna403Forbidden() throws Exception {
        GenerarSlotsRequest req = new GenerarSlotsRequest(
                "prof-1", "sede-1", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 20, "PRESENCIAL", null
        );

        mockMvc.perform(post("/api/v1/admin/slots/generate")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    // =========================================================================
    // OPERACIONES CRUD Y GENERACION CON ROLE_ADMINISTRADOR
    // =========================================================================

    @Test
    void postGenerate_conDatosValidos_retorna201Created() throws Exception {
        GenerarSlotsRequest req = new GenerarSlotsRequest(
                "prof-1", "sede-1", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(9, 0), 20, "PRESENCIAL", List.of(DayOfWeek.MONDAY)
        );

        SlotResponse slotResp = new SlotResponse(
                "slot-1", "prof-1", "Carlos Perez", "sede-1", "Sede Norte",
                "esp-1", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        GenerarSlotsResponse resp = new GenerarSlotsResponse(1, List.of(slotResp), "Se generaron exitosamente 1 slots.");

        when(slotGeneratorService.generarSlots(any(GenerarSlotsRequest.class), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(post("/api/v1/admin/slots/generate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotsGenerados").value(1))
                .andExpect(jsonPath("$.slots[0].publicId").value("slot-1"))
                .andExpect(jsonPath("$.slots[0].profesionalNombre").value("Carlos Perez"));
    }

    @Test
    void postGenerate_datosInvalidosEnCuerpo_retorna400BadRequest() throws Exception {
        // Falta sedePublicId y fechaInicio
        GenerarSlotsRequest req = new GenerarSlotsRequest(
                "prof-1", "", null, LocalDate.now().plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 20, "PRESENCIAL", null
        );

        mockMvc.perform(post("/api/v1/admin/slots/generate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void postGenerate_duracionFueraDeRango_retorna400BadRequest() throws Exception {
        // Duración menor a 5 min
        GenerarSlotsRequest req = new GenerarSlotsRequest(
                "prof-1", "sede-1", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 3, "PRESENCIAL", null
        );

        mockMvc.perform(post("/api/v1/admin/slots/generate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void postGenerate_conflictoSolape_retorna400BadRequest() throws Exception {
        GenerarSlotsRequest req = new GenerarSlotsRequest(
                "prof-1", "sede-1", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(9, 0), 20, "PRESENCIAL", null
        );

        when(slotGeneratorService.generarSlots(any(), eq(ADMIN_UUID), anyString()))
                .thenThrow(new DatosInvalidosException("Existe un solape de horario para el profesional."));

        mockMvc.perform(post("/api/v1/admin/slots/generate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void getSlots_paginado_retorna200Ok() throws Exception {
        SlotResponse slotResp = new SlotResponse(
                "slot-1", "prof-1", "Carlos Perez", "sede-1", "Sede Norte",
                "esp-1", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        PaginatedResponse<SlotResponse> paginated = PaginatedResponse.of(List.of(slotResp), 0, 10, 1L);

        when(slotGeneratorService.listar(anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn(paginated);

        mockMvc.perform(get("/api/v1/admin/slots")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("slot-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getSlot_existente_retorna200Ok() throws Exception {
        SlotResponse slotResp = new SlotResponse(
                "slot-1", "prof-1", "Carlos Perez", "sede-1", "Sede Norte",
                "esp-1", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        when(slotGeneratorService.obtenerPorPublicId("slot-1")).thenReturn(slotResp);

        mockMvc.perform(get("/api/v1/admin/slots/slot-1")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("slot-1"))
                .andExpect(jsonPath("$.estado").value("LIBRE"));
    }

    @Test
    void getSlot_inexistente_retorna404NotFound() throws Exception {
        when(slotGeneratorService.obtenerPorPublicId("inexistente"))
                .thenThrow(new RecursoNoEncontradoException("Slot no encontrado: inexistente"));

        mockMvc.perform(get("/api/v1/admin/slots/inexistente")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void patchBlock_exitoso_retorna200Ok() throws Exception {
        SlotResponse slotResp = new SlotResponse(
                "slot-1", "prof-1", "Carlos Perez", "sede-1", "Sede Norte",
                "esp-1", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "BLOQUEADO"
        );
        when(slotGeneratorService.bloquearSlot(eq("slot-1"), eq(ADMIN_UUID), anyString()))
                .thenReturn(slotResp);

        mockMvc.perform(patch("/api/v1/admin/slots/slot-1/block")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADO"));
    }

    @Test
    void patchUnblock_exitoso_retorna200Ok() throws Exception {
        SlotResponse slotResp = new SlotResponse(
                "slot-1", "prof-1", "Carlos Perez", "sede-1", "Sede Norte",
                "esp-1", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        when(slotGeneratorService.desbloquearSlot(eq("slot-1"), eq(ADMIN_UUID), anyString()))
                .thenReturn(slotResp);

        mockMvc.perform(patch("/api/v1/admin/slots/slot-1/unblock")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LIBRE"));
    }

    @Test
    void deleteSlot_exitoso_retorna204NoContent() throws Exception {
        doNothing().when(slotGeneratorService).eliminarSlot(eq("slot-1"), eq(ADMIN_UUID), anyString());

        mockMvc.perform(delete("/api/v1/admin/slots/slot-1")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSlot_noLibre_retorna400BadRequest() throws Exception {
        doThrow(new DatosInvalidosException("Solo se pueden eliminar slots en estado LIBRE."))
                .when(slotGeneratorService).eliminarSlot(eq("slot-1"), eq(ADMIN_UUID), anyString());

        mockMvc.perform(delete("/api/v1/admin/slots/slot-1")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }
}
