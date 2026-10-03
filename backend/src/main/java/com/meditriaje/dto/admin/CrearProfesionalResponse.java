package com.meditriaje.dto.admin;

import java.time.Instant;

/**
 * Respuesta a la creación exitosa de un profesional asistencial.
 * Incluye la contraseña temporal generada en texto plano para que el administrador
 * se la suministre al médico de forma segura por única vez (HU-10, ADR-002).
 */
public record CrearProfesionalResponse(
        String publicId,
        String usuarioPublicId,
        String registroMedico,
        String nombres,
        String apellidos,
        String email,
        String especialidadPublicId,
        String especialidadNombre,
        String passwordTemporal,
        boolean debeCambiarPassword,
        Instant createdAt
) {}
