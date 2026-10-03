package com.meditriaje.dto.admin;

import java.time.Instant;

/**
 * Respuesta DTO para el profesional asistencial (HU-10).
 */
public record ProfesionalResponse(
        String publicId,
        String usuarioPublicId,
        String registroMedico,
        String nombres,
        String apellidos,
        String email,
        String especialidadPublicId,
        String especialidadNombre,
        String estado,
        boolean debeCambiarPassword,
        Instant createdAt
) {}
