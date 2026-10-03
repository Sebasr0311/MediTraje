package com.meditriaje.model;

import java.util.Objects;

/**
 * Representa un evento inmutable registrado en la bitácora de auditoría.
 *
 * <p><strong>Regla de privacidad médica (ADR-011):</strong> Esta estructura no contiene
 * ni acepta campos de contenido clínico (diagnósticos, evolución, signos vitales, medicamentos)
 * ni credenciales/secretos. Solo almacena metadatos del evento.</p>
 */
public record EventoAuditoria(
        Long usuarioId,
        AccionAuditable accion,
        String tipoRecurso,
        String recursoPublicId,
        ResultadoAuditoria resultado,
        String ipOrigen
) {
    public EventoAuditoria {
        Objects.requireNonNull(accion, "La accion auditable no puede ser nula");
        if (tipoRecurso == null || tipoRecurso.isBlank()) {
            throw new IllegalArgumentException("El tipo de recurso es obligatorio");
        }
        Objects.requireNonNull(resultado, "El resultado de auditoria no puede ser nulo");
        if (ipOrigen == null || ipOrigen.isBlank()) {
            throw new IllegalArgumentException("La IP de origen es obligatoria");
        }
    }
}
