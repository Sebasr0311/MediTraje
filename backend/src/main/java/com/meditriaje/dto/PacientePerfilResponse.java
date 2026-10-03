package com.meditriaje.dto;

import java.time.LocalDate;

/**
 * DTO con información de perfil del paciente autenticado (HU-09, ADR-003).
 * Solo expone publicId y datos demográficos; nunca IDs numéricos internos de base de datos.
 */
public record PacientePerfilResponse(
        String publicId,
        String tipoDocumento,
        String numeroDocumento,
        String nombres,
        String apellidos,
        LocalDate fechaNacimiento,
        String telefono,
        String email
) {}
