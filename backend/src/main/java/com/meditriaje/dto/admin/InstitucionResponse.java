package com.meditriaje.dto.admin;

import java.time.Instant;

public record InstitucionResponse(
        String publicId,
        String nit,
        String razonSocial,
        String estado,
        Instant createdAt
) {}
