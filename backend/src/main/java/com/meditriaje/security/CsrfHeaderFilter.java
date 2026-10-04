package com.meditriaje.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.meditriaje.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

/**
 * Filtro de protección CSRF mediante cabecera personalizada obligatoria (ADR-002).
 * <p>
 * En combinación con cookies SameSite=Strict, exige una cabecera personalizada
 * (X-Requested-With o X-CSRF-Protection) para operaciones mutantes (POST, PUT, DELETE, PATCH).
 * Los navegadores impiden que peticiones cross-site establezcan cabeceras personalizadas
 * sin autorización previa por CORS preflight.
 * </p>
 */
public class CsrfHeaderFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_MUTANTES = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final Set<String> RUTAS_EXENTAS = Set.of(
            "/api/v1/ping",
            "/api/v1/auth/login",
            "/api/v1/auth/register"
    );

    private final ObjectMapper objectMapper;

    public CsrfHeaderFilter(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "ObjectMapper no puede ser nulo");
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String method = request.getMethod().toUpperCase();

        if (METODOS_MUTANTES.contains(method)) {
            String path = request.getRequestURI();
            if (!esRutaExenta(path)) {
                String csrfHeader = request.getHeader("X-Requested-With");
                String altCsrfHeader = request.getHeader("X-CSRF-Protection");

                if ((csrfHeader == null || csrfHeader.isBlank()) && (altCsrfHeader == null || altCsrfHeader.isBlank())) {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

                    ApiError error = ApiError.of(
                            "CSRF_REQUERIDO",
                            "Peticion rechazada por falta de cabecera de proteccion CSRF (X-Requested-With).",
                            null
                    );

                    response.getWriter().write(objectMapper.writeValueAsString(error));
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean esRutaExenta(String path) {
        if (path == null) {
            return false;
        }
        if (path.startsWith("/api/v1/emergency-summary/")) {
            return true;
        }
        return RUTAS_EXENTAS.stream().anyMatch(path::equalsIgnoreCase);
    }
}
