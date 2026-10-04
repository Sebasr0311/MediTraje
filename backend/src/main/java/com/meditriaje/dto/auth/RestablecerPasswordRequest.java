package com.meditriaje.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de restablecimiento de contraseña utilizando el código OTP numérico de 6 dígitos (F2.1, ADR-014).
 */
public record RestablecerPasswordRequest(
        @NotBlank(message = "El correo electronico es obligatorio.")
        @Email(message = "El formato del correo electronico no es valido.")
        String email,

        @NotBlank(message = "El codigo de verificacion es obligatorio.")
        @Pattern(regexp = "^[0-9]{6}$", message = "El codigo de verificacion debe tener exactamente 6 digitos numericos.")
        String codigo,

        @NotBlank(message = "La nueva contrasena es obligatoria.")
        @Size(min = 10, max = 100, message = "La nueva contrasena debe tener entre 10 y 100 caracteres.")
        String passwordNuevo
) {
}
