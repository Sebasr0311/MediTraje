package com.meditriaje.dto.admin;

public record SedeResponse(
        String publicId,
        String institucionPublicId,
        String institucionRazonSocial,
        String nombre,
        String direccion,
        String ciudad,
        String estado
) {}
