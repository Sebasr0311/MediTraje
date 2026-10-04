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
    TRIAJE_EMERGENCIA,
    /** Solicitud de código OTP para restablecimiento de contraseña (F2.1.3). */
    SOLICITUD_RECUPERACION_PASSWORD,
    /** Restablecimiento exitoso de contraseña mediante código OTP (F2.1.3). */
    RECUPERACION_PASSWORD_EXITO,
    /** Intento fallido de restablecimiento de contraseña con código inválido/expirado (F2.1.3). */
    RECUPERACION_PASSWORD_FALLO,
    /** Solicitud de configuración/enrolamiento MFA TOTP (F2.1.4). */
    MFA_SETUP,
    /** Verificación y activación exitosa de MFA TOTP (F2.1.4). */
    MFA_VERIFY,
    /** Segundo factor completado exitosamente en autenticación (F2.1.4). */
    MFA_LOGIN_EXITOSO,
    /** Fallo en validación de segundo factor MFA (F2.1.4). */
    MFA_LOGIN_FALLIDO,
    /** Prescripción de seguimiento post-atención médica (F2.2.3, ADR-015). */
    CREACION_SEGUIMIENTO,
    /** Reporte de evolución registrado por el paciente (F2.2.3, ADR-015, §5.16). */
    REPORTE_EVOLUCION_SEGUIMIENTO,
    /** Generación de acceso temporal QR para resumen de emergencia (F2.3, ADR-010). */
    GENERACION_QR_EMERGENCIA,
    /** Revocación de acceso temporal QR por el paciente (F2.3, ADR-010). */
    REVOCACION_QR_EMERGENCIA,
    /** Lectura/consulta pública de resumen de salud por QR de emergencia (F2.3, ADR-010). */
    ACCESO_EMERGENCIA_QR,
    /** Dispensación y entrega de medicamentos en farmacia (F2.4, ADR-016). */
    DISPENSACION_RECETA
}
