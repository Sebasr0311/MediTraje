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
    DISPENSACION_RECETA,
    /** Activación de acceso clínico excepcional de emergencia Break-Glass (F2.5, ADR-017). */
    ACCESO_BREAK_GLASS,
    /** Consulta de reportes y métricas operativas por administradores (F2.6, RF-30, ADR-018). */
    CONSULTA_REPORTE_ADMINISTRATIVO,
    /** Fallo en el despacho de correo transaccional sin exponer datos sensibles (D1, T3). */
    EMAIL_FALLIDO,
    /** Registro de alergia o hipersensibilidad clínica o autorreportada (D3, T4). */
    ALERGIA_REGISTRADA,
    /** Consulta de alergias e historial de hipersensibilidades (D3, T4). */
    ALERGIA_CONSULTADA,
    /** Inactivación de alergia por profesional o paciente con motivo obligatorio (D3, T4). */
    ALERGIA_INACTIVADA,
    /** Registro de inasistencia (no-show) del paciente a la cita médica por profesional o admin (D4, T5). */
    CITA_NO_ASISTIO,
    /** Registro de ingreso a urgencias y creación de episodio (Fase U, ADR-022). */
    INGRESO_URGENCIA_REGISTRADO,
    /** Registro de identidad provisional para paciente no identificado NN (Fase U, ADR-023). */
    IDENTIDAD_PROVISIONAL_REGISTRADA,
    /** Reconciliación de identidad provisional hacia paciente civil confirmado (Fase U, ADR-023). */
    IDENTIDAD_PROVISIONAL_RECONCILIADA,
    /** Registro o reevaluación de valoración de triaje presencial (Fase U, ADR-027). */
    VALORACION_TRIAJE_REGISTRADA,
    /** Asignación de equipo asistencial a episodio de urgencias (Fase U, ADR-022). */
    ASIGNACION_ASISTENCIAL_REGISTRADA,
    /** Cierre o egreso de episodio de urgencias (Fase U, ADR-022). */
    EPISODIO_CERRADO,
    /** Asignación u ocupación de cama hospitalaria (Fase H, ADR-024). */
    ASIGNACION_CAMA_REGISTRADA,
    /** Liberación y cambio a limpieza de cama hospitalaria (Fase H, ADR-024). */
    LIBERACION_CAMA_REGISTRADA,
    /** Traslado o movimiento intrahospitalario del paciente (Fase H, ADR-024). */
    MOVIMIENTO_PACIENTE_REGISTRADO,
    /** Solicitud o registro de procedimiento/cirugía hospitalaria (Fase H, ADR-024). */
    PROCEDIMIENTO_HOSPITALARIO_REGISTRADO,
    /** Transición de estado en procedimiento quirúrgico/recuperación (Fase H, ADR-024). */
    PROCEDIMIENTO_ESTADO_ACTUALIZADO,
    /** Egreso hospitalario con epicrisis y destino de alta (Fase H, ADR-024). */
    EGRESO_HOSPITALARIO_REGISTRADO,
    /** Actualización de estado operativo o mantenimiento de cama (Fase H, ADR-024). */
    ESTADO_CAMA_ACTUALIZADO,
    /** Previsualización y validación de lote de afiliados EPS en Excel (Fase A, ADR-025). */
    IMPORTACION_EPS_PREVIEW,
    /** Confirmación e importación atómica de lote de afiliados EPS (Fase A, ADR-025). */
    IMPORTACION_EPS_COMMIT,
    /** Consulta de aseguramiento y estado de afiliación EPS (Fase A, ADR-025). */
    AFILIACION_CONSULTADA,
    /** Registro de ausencia médica o bloqueo de agenda (Fase C, ADR-026). */
    AUSENCIA_MEDICA_REGISTRADA,
    /** Reprogramación atómica de cita médica (Fase C, ADR-026). */
    CITA_REPROGRAMADA,
    /** Registro de representación legal o tutor para menor de edad (Fase C, ADR-026). */
    REPRESENTACION_LEGAL_REGISTRADA,
    /** Reconocimiento/atención de alerta operativa de saturación hospitalaria (Fase O, ADR-027). */
    ALERTA_OPERATIVA_RECONOCIDA,
    /** Generación de código QR seguro para seguimiento intrahospitalario del paciente (Fase O, ADR-028). */
    QR_SEGUIMIENTO_GENERADO,
    /** Lectura o escaneo de código QR de seguimiento de paciente (Fase O, ADR-028). */
    QR_SEGUIMIENTO_ESCANEADO
}
