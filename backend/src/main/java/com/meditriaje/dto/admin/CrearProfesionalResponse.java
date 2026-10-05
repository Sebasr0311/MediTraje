package com.meditriaje.dto.admin;

import java.time.Instant;

/**
 * Respuesta a la creación exitosa de un profesional asistencial (HU-10, ADR-002, Ley 1164/2007).
 * Incluye la contraseña temporal generada en texto plano para que el administrador
 * se la suministre al médico de forma segura por única vez.
 */
public record CrearProfesionalResponse(
        String publicId,
        String usuarioPublicId,
        String tipoDocumento,
        String numeroDocumento,
        String registroMedico,
        String nombres,
        String apellidos,
        String email,
        String telefono,
        String especialidadPublicId,
        String especialidadNombre,
        String passwordTemporal,
        boolean debeCambiarPassword,
        Instant createdAt
) {
    public CrearProfesionalResponse(
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
    ) {
        this(
                publicId,
                usuarioPublicId,
                "CC",
                null,
                registroMedico,
                nombres,
                apellidos,
                email,
                null,
                especialidadPublicId,
                especialidadNombre,
                passwordTemporal,
                debeCambiarPassword,
                createdAt
        );
    }
}
