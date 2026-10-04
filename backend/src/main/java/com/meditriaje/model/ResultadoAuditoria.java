package com.meditriaje.model;

/**
 * Resultado de una operación sujeta a auditoría.
 * Alineado estrictamente con la restricción CK_AUDITORIA_RESULTADO de Oracle ATP.
 */
public enum ResultadoAuditoria {
    EXITO,
    FALLO,
    BLOQUEADO
}
