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
    CAMBIO_PASSWORD
}
