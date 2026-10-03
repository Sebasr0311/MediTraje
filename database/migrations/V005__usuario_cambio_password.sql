-- =============================================================================
-- MediTriaje 2.0 — Migración V005: Control de Cambio Obligatorio de Contraseña
-- =============================================================================
-- Añade columna DEBE_CAMBIAR_PASSWORD a la tabla USUARIO para forzar primer cambio
-- en altas administrativas de profesionales (HU-10, ADR-002).
-- =============================================================================

ALTER TABLE USUARIO ADD (
    DEBE_CAMBIAR_PASSWORD NUMBER(1) DEFAULT 0 NOT NULL,
    CONSTRAINT CK_USUARIO_CAMBIO_PASS CHECK (DEBE_CAMBIAR_PASSWORD IN (0, 1))
);
