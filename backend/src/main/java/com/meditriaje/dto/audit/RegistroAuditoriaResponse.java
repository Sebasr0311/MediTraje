package com.meditriaje.dto.audit;

import java.time.Instant;

/**
 * Elemento de bitácora para el visor de auditoría de seguridad (RF-26, ADR-019).
 * Proyecta metadatos del evento sin exponer contenido clínico confidencial (ADR-007).
 */
public record RegistroAuditoriaResponse(
        Long id,
        String usuarioEmail,
        String accion,
        String tipoRecurso,
        String recursoPublicId,
        String resultado,
        String ipOrigen,
        Instant fechaHora
) {}
