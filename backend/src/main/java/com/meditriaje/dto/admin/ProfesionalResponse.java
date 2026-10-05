package com.meditriaje.dto.admin;

import java.time.Instant;

/**
 * Respuesta DTO para el profesional asistencial (HU-10, Ley 1164/2007).
 */
public record ProfesionalResponse(
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
        String estado,
        boolean debeCambiarPassword,
        Instant createdAt
) {
    public ProfesionalResponse(
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
                estado,
                debeCambiarPassword,
                createdAt
        );
    }
}
