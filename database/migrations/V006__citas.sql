-- =============================================================================
-- MediTriaje 2.0 — Migración V006: Módulo de Citas y Control de Concurrencia
-- =============================================================================
-- Tabla: CITA
-- Características críticas:
--   1. Clave pública UUID expuesta en API (ADR-003).
--   2. FK a DISPONIBILIDAD_SLOT y a PACIENTE.
--   3. Relación recursiva opcional CITA_ORIGEN_ID para reprogramaciones (ADR-006).
--   4. Índice funcional único UQ_CITA_SLOT_ACTIVA (ADR-006):
--      Garantiza a nivel de motor Oracle que dos citas activas (PROGRAMADA o CONFIRMADA)
--      NUNCA puedan ocupar el mismo SLOT_ID, impidiendo la doble reserva concurrente
--      incluso ante fallas o carreras en la capa de aplicación.
--   5. Privilegios mínimos a MEDITRIAJE_APP según ADR-012 (sin DELETE físico).
-- =============================================================================

CREATE TABLE CITA (
    ID NUMBER GENERATED ALWAYS AS IDENTITY,
    PUBLIC_ID VARCHAR2(36 CHAR) NOT NULL,
    SLOT_ID NUMBER NOT NULL,
    PACIENTE_ID NUMBER NOT NULL,
    TRIAJE_ID NUMBER,
    CITA_ORIGEN_ID NUMBER,
    ESTADO VARCHAR2(20 CHAR) DEFAULT 'PROGRAMADA' NOT NULL,
    MOTIVO_CANCELACION VARCHAR2(255 CHAR),
    CREATED_AT TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT TIMESTAMP WITH TIME ZONE,
    CONSTRAINT PK_CITA PRIMARY KEY (ID),
    CONSTRAINT UQ_CITA_PUBLIC_ID UNIQUE (PUBLIC_ID),
    CONSTRAINT FK_CITA_SLOT FOREIGN KEY (SLOT_ID) REFERENCES DISPONIBILIDAD_SLOT(ID),
    CONSTRAINT FK_CITA_PACIENTE FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID),
    CONSTRAINT FK_CITA_ORIGEN FOREIGN KEY (CITA_ORIGEN_ID) REFERENCES CITA(ID),
    CONSTRAINT CK_CITA_ESTADO CHECK (ESTADO IN ('PROGRAMADA', 'CONFIRMADA', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO', 'REPROGRAMADA'))
);

-- -----------------------------------------------------------------------------
-- Índice Funcional Único de Concurrencia (ADR-006)
-- -----------------------------------------------------------------------------
-- En Oracle, si el CASE devuelve NULL, no se indexa. Si el estado es activo
-- ('PROGRAMADA' o 'CONFIRMADA'), devuelve el SLOT_ID, forzando unicidad estricta.
CREATE UNIQUE INDEX UQ_CITA_SLOT_ACTIVA ON CITA (
    CASE WHEN ESTADO IN ('PROGRAMADA', 'CONFIRMADA') THEN SLOT_ID END
);

-- -----------------------------------------------------------------------------
-- Índices Secundarios de Rendimiento
-- -----------------------------------------------------------------------------
CREATE INDEX IX_CITA_PACIENTE ON CITA (PACIENTE_ID, ESTADO, CREATED_AT DESC);
CREATE INDEX IX_CITA_SLOT ON CITA (SLOT_ID);
CREATE INDEX IX_CITA_ORIGEN ON CITA (CITA_ORIGEN_ID);

-- -----------------------------------------------------------------------------
-- Concesión de privilegios mínimos a MEDITRIAJE_APP (ADR-012)
-- -----------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE ON CITA TO MEDITRIAJE_APP;
