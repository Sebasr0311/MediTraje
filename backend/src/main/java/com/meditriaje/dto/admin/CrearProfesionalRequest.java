package com.meditriaje.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud administrativa para dar de alta a un profesional asistencial (HU-10, ADR-002, ADR-003, Ley 1164/2007).
 */
public record CrearProfesionalRequest(
        String tipoDocumento,

        String numeroDocumento,

        @NotBlank(message = "El registro medico es obligatorio.")
        @Size(max = 30, message = "El registro medico no puede superar los 30 caracteres.")
        String registroMedico,

        @NotBlank(message = "Los nombres son obligatorios.")
        @Size(max = 60, message = "Los nombres no pueden superar los 60 caracteres.")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios.")
        @Size(max = 60, message = "Los apellidos no pueden superar los 60 caracteres.")
        String apellidos,

        @NotBlank(message = "El correo electronico es obligatorio.")
        @Email(message = "El formato del correo electronico es invalido.")
        @Size(max = 100, message = "El correo electronico no puede superar los 100 caracteres.")
        String email,

        String telefono,

        @NotBlank(message = "La especialidad es obligatoria.")
        String especialidadPublicId,

        String rol
) {
    public CrearProfesionalRequest {
        if (tipoDocumento == null || tipoDocumento.isBlank()) {
            tipoDocumento = "CC";
        }
        if (rol == null || rol.isBlank()) {
            rol = "ROLE_PROFESIONAL";
        }
    }

    public CrearProfesionalRequest(
            String tipoDocumento,
            String numeroDocumento,
            String registroMedico,
            String nombres,
            String apellidos,
            String email,
            String telefono,
            String especialidadPublicId
    ) {
        this(
                tipoDocumento,
                numeroDocumento,
                registroMedico,
                nombres,
                apellidos,
                email,
                telefono,
                especialidadPublicId,
                "ROLE_PROFESIONAL"
        );
    }

    public CrearProfesionalRequest(
            String registroMedico,
            String nombres,
            String apellidos,
            String email,
            String especialidadPublicId
    ) {
        this(
                "CC",
                "10" + (Math.abs(registroMedico != null ? registroMedico.hashCode() % 90000000 + 10000000 : 12345678)),
                registroMedico,
                nombres,
                apellidos,
                email,
                null,
                especialidadPublicId,
                "ROLE_PROFESIONAL"
        );
    }
}
