package com.meditriaje.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de código OTP para recuperación de contraseña (F2.1, ADR-014).
 */
public record SolicitarRecuperacionRequest(
        @NotBlank(message = "El correo electronico es obligatorio.")
        @Email(message = "El formato del correo electronico no es valido.")
        @Size(max = 120, message = "El correo electronico no puede superar 120 caracteres.")
        String email
) {
}
