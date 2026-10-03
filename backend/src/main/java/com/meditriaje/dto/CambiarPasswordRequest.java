package com.meditriaje.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de cambio de contraseña por parte del usuario autenticado (ADR-002).
 */
public record CambiarPasswordRequest(
        @NotBlank(message = "La contrasena actual es obligatoria.")
        String passwordActual,

        @NotBlank(message = "La nueva contrasena es obligatoria.")
        @Size(min = 10, max = 100, message = "La nueva contrasena debe tener entre 10 y 100 caracteres.")
        String passwordNuevo
) {}
