package com.meditriaje.dto.admin;

public record EspecialidadResponse(
        String publicId,
        String nombre,
        int duracionSlotMin,
        String estado
) {}
