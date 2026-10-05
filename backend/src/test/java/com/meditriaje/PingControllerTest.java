package com.meditriaje;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.PingController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PingController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@ActiveProfiles("test")
class PingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.meditriaje.security.JwtService jwtService;

    @Test
    @DisplayName("GET /api/v1/ping debe retornar status UP en formato JSON")
    void shouldReturnUpStatusOnPing() throws Exception {
        mockMvc.perform(get("/api/v1/ping")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Debe incluir cabeceras HTTP de seguridad reforzadas (CSP, Referrer, Permissions, X-Frame-Options)")
    void shouldIncludeSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Frame-Options", "DENY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Security-Policy",
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none';"));
    }

    @Test
    @DisplayName("GET /ping y GET /health deben retornar status UP sin autenticación")
    void shouldReturnUpOnShortHealthRoutes() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET / debe retornar status UP e información de servicio")
    void shouldReturnUpOnRootRoute() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("MediTriaje 2.0 API"));
    }
}
