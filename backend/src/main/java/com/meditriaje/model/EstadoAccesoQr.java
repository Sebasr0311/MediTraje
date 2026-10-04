package com.meditriaje.model;

/**
 * Estados del ciclo de vida de un acceso temporal por código QR (F2.3, ADR-010).
 */
public enum EstadoAccesoQr {
    /** Token vigente y con lecturas disponibles. */
    ACTIVO,
    /** Token superó el tiempo máximo de vigencia (15 minutos). */
    EXPIRADO,
    /** Token alcanzó el número máximo permitido de lecturas (3 accesos). */
    AGOTADO,
    /** Token revocado explícitamente por el paciente antes de su expiración. */
    REVOCADO
}
