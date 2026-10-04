package com.meditriaje.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActualizarInstitucionRequest(
        @NotBlank(message = "La razon social es obligatoria")
        @Size(max = 120, message = "La razon social no puede exceder 120 caracteres")
        String razonSocial
) {}
