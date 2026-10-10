-- =============================================================================
-- MediTriaje 2.0 — Migración V017: Contador de PIN Fallidos en Acceso QR (T11, SEC-002)
-- =============================================================================
-- Añade columna INTENTOS_PIN_FALLIDOS a ACCESO_TEMPORAL_QR para mitigación de
-- ataques de fuerza bruta sobre el PIN de emergencia (3 intentos máximos revocan
-- el token de forma irreversible y cuentan para el límite de lecturas).
-- =============================================================================

ALTER TABLE ACCESO_TEMPORAL_QR ADD (
    INTENTOS_PIN_FALLIDOS NUMBER(2) DEFAULT 0 NOT NULL,
    CONSTRAINT CK_ACCESO_QR_PIN_FALLIDOS CHECK (INTENTOS_PIN_FALLIDOS >= 0)
);
