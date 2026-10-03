package com.meditriaje.model;

/**
 * Acciones del sistema sujetas a registro inmutable en auditoría (ADR-011, HU-11).
 * Cubre autenticación, seguridad, accesos a historia clínica, transacciones asistenciales y administración.
 */
public enum AccionAuditable {
    LOGIN_EXITOSO,
    LOGIN_FALLIDO,
    LOGOUT,
    REGISTRO_PACIENTE,
    CONSULTA_HISTORIA,
    CREACION_ATENCION,
    CIERRE_ATENCION,
    ENMIENDA_ATENCION,
    CREACION_RECETA,
    RESERVA_CITA,
    CANCELACION_CITA,
    CAMBIO_ADMINISTRATIVO,
    CAMBIO_PASSWORD,
    /** Triaje evaluado (M5.4 lo conecta). Auditar solo usuario, acción, recurso e id; nunca síntomas. */
    TRIAJE_REALIZADO,
    /** Corte de emergencia activado (M5.4 lo conecta). Auditar solo usuario, acción, recurso e id; nunca síntomas. */
    TRIAJE_EMERGENCIA
}
