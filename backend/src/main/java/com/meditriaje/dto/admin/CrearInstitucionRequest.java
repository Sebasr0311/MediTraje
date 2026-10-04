package com.meditriaje.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearInstitucionRequest(
        @NotBlank(message = "El NIT es obligatorio")
        @Size(max = 20, message = "El NIT no puede exceder 20 caracteres")
        String nit,

        @NotBlank(message = "La razon social es obligatoria")
        @Size(max = 120, message = "La razon social no puede exceder 120 caracteres")
        String razonSocial
) {}
