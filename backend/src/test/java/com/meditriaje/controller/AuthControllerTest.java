package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.RegistroPacienteRequest;
import com.meditriaje.dto.RegistroPacienteResponse;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void register_conPayloadValido_retorna201Created() throws Exception {
        RegistroPacienteRequest request = new RegistroPacienteRequest(
                "CC",
                "1234567890",
                "Maria",
                "Gomez",
                LocalDate.of(1990, 1, 15),
                "3109876543",
                "maria.gomez@example.com",
                "PasswordSegura123*",
                "v1.0",
                true
        );

        RegistroPacienteResponse mockResponse = new RegistroPacienteResponse(
                "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
                "maria.gomez@example.com",
                "Maria",
                "Gomez",
                "Registro completado exitosamente."
        );

        when(authService.registrarPaciente(any(), anyString())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pacientePublicId").value("f81d4fae-7dec-11d0-a765-00a0c91e6bf6"))
                .andExpect(jsonPath("$.email").value("maria.gomez@example.com"))
                .andExpect(jsonPath("$.nombres").value("Maria"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_conContrasenaCorta_retorna400BadRequest() throws Exception {
        RegistroPacienteRequest request = new RegistroPacienteRequest(
                "CC",
                "1234567890",
                "Maria",
                "Gomez",
                LocalDate.of(1990, 1, 15),
                "3109876543",
                "maria.gomez@example.com",
                "corta9", // < 10 caracteres
                "v1.0",
                true
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void register_sinAceptarConsentimiento_retorna400BadRequest() throws Exception {
        RegistroPacienteRequest request = new RegistroPacienteRequest(
                "CC",
                "1234567890",
                "Maria",
                "Gomez",
                LocalDate.of(1990, 1, 15),
                "3109876543",
                "maria.gomez@example.com",
                "PasswordSegura123*",
                "v1.0",
                false // Rechaza consentimiento
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.mensaje").exists());
    }
}
