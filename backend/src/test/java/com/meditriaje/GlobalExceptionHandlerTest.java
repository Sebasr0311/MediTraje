package com.meditriaje;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.exception.RecursoNoEncontradoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifica que las excepciones de dominio retornan ApiError JSON estructurado
 * sin stack traces en el body de respuesta.
 */
@WebMvcTest
@Import({GlobalExceptionHandler.class, CorsConfig.class, GlobalExceptionHandlerTest.StubController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    /** Controller stub que lanza la excepción bajo prueba. */
    @RestController
    static class StubController {
        @GetMapping("/api/v1/test/not-found")
        public String triggerNotFound() {
            throw new RecursoNoEncontradoException("Paciente#99");
        }
    }

    @Test
    @WithMockUser
    void cuandoRecursoNoEncontrado_retornaApiErrorJson() throws Exception {
        mockMvc.perform(get("/api/v1/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                // Verificar que NO hay stack trace en el body
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
}
