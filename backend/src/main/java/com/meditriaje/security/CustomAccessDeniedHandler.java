package com.meditriaje.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Manejador para usuarios autenticados que carecen del rol/autorización requerida.
 * Retorna HTTP 403 Forbidden con formato estandarizado ApiError (ADR-002, M1.4).
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper no puede ser nulo");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiError error = ApiError.of(
                "ACCESO_DENEGADO",
                "No tiene permisos para acceder a este recurso.",
                null
        );

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
