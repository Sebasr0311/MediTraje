package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.AuthSessionResponse;
import com.meditriaje.dto.AuthTokens;
import com.meditriaje.dto.LoginRequest;
import com.meditriaje.dto.RegistroPacienteRequest;
import com.meditriaje.dto.RegistroPacienteResponse;
import com.meditriaje.exception.CredencialesInvalidasException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.CsrfHeaderFilter;
import com.meditriaje.security.CustomAccessDeniedHandler;
import com.meditriaje.security.CustomAuthenticationEntryPoint;
import com.meditriaje.security.JwtAuthenticationFilter;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @MockBean
    private com.meditriaje.security.JwtService jwtService;

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
                false
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    // -------------------------------------------------------------------------
    // LOGIN
    // -------------------------------------------------------------------------

    @Test
    void login_credencialesValidas_retorna200YSeteaCookiesHttpOnly() throws Exception {
        LoginRequest request = new LoginRequest("maria.gomez@example.com", "PasswordSegura123*");

        AuthSessionResponse session = new AuthSessionResponse(
                "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
                "maria.gomez@example.com",
                List.of("ROLE_PACIENTE"),
                "Inicio de sesion exitoso."
        );
        AuthTokens tokens = new AuthTokens("mock.jwt.token", "mock-refresh-token", session);

        when(authService.login(any(), anyString())).thenReturn(tokens);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("f81d4fae-7dec-11d0-a765-00a0c91e6bf6"))
                .andExpect(jsonPath("$.email").value("maria.gomez@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_PACIENTE"))
                .andExpect(jsonPath("$.token").doesNotExist()) // Tokens NO en body
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("access_token=mock.jwt.token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));
    }

    @Test
    void login_credencialesInvalidas_retorna401Unauthorized() throws Exception {
        LoginRequest request = new LoginRequest("maria.gomez@example.com", "ClaveErronea123*");

        when(authService.login(any(), anyString())).thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.mensaje").value("Credenciales invalidas."));
    }

    // -------------------------------------------------------------------------
    // REFRESH
    // -------------------------------------------------------------------------

    @Test
    void refresh_conCookieValida_retorna200YNuevasCookies() throws Exception {
        AuthSessionResponse session = new AuthSessionResponse(
                "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
                "maria.gomez@example.com",
                List.of("ROLE_PACIENTE"),
                "Sesion actualizada exitosamente."
        );
        AuthTokens tokens = new AuthTokens("new.jwt.token", "new-refresh-token", session);

        when(authService.refresh(anyString(), anyString())).thenReturn(tokens);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("refresh_token", "existing-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("f81d4fae-7dec-11d0-a765-00a0c91e6bf6"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("access_token=new.jwt.token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));
    }

    @Test
    void refresh_sinCabeceraCsrf_retorna403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "existing-refresh-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CSRF_REQUERIDO"));
    }

    // -------------------------------------------------------------------------
    // LOGOUT
    // -------------------------------------------------------------------------

    @Test
    void logout_limpiaCookiesYRetorna200() throws Exception {
        doNothing().when(authService).logout(any(), anyString());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("refresh_token", "existing-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Sesion cerrada exitosamente."))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }
}
