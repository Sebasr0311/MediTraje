package com.meditriaje.dto;

/**
 * Respuesta del registro de paciente.
 * No expone IDs internos, contraseñas ni datos sensibles (ADR-003).
 */
public record RegistroPacienteResponse(
        String pacientePublicId,
        String email,
        String nombres,
        String apellidos,
        String mensaje
) {}
