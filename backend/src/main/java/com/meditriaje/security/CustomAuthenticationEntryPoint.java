package com.meditriaje.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Punto de entrada para peticiones no autenticadas en recursos protegidos.
 * Retorna HTTP 401 Unauthorized con formato estandarizado ApiError (ADR-002, M1.4).
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper no puede ser nulo");
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiError error = ApiError.of(
                "NO_AUTENTICADO",
                "Debe iniciar sesion para acceder a este recurso.",
                null
        );

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
