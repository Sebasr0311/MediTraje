-- =============================================================================
-- MediTriaje 2.0 — Migración V012: Acceso Temporal QR y Resumen de Emergencia (F2.3, ADR-010, §5.17, §5.18)
-- =============================================================================
-- Cambios:
--   1. ACCESO_TEMPORAL_QR: Registro de tokens temporales criptográficos de 256 bits
--      (hasheados con SHA-256 en reposo) para acceso a resumen de salud en emergencias.
--      Expiración a 15 minutos, máximo 3 accesos auditados, revocable y PIN opcional.
--   2. Concesión de privilegios mínimos a MEDITRIAJE_APP (ADR-012).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Tabla ACCESO_TEMPORAL_QR
-- -----------------------------------------------------------------------------
CREATE TABLE ACCESO_TEMPORAL_QR (
    ID NUMBER GENERATED ALWAYS AS IDENTITY,
    PUBLIC_ID VARCHAR2(36 CHAR) NOT NULL,
    PACIENTE_ID NUMBER NOT NULL,
    TOKEN_HASH VARCHAR2(64 CHAR) NOT NULL,
    PIN_HASH VARCHAR2(100 CHAR) NULL,
    INCLUIR_ALERGIAS NUMBER(1) DEFAULT 1 NOT NULL,
    INCLUIR_MEDICAMENTOS NUMBER(1) DEFAULT 1 NOT NULL,
    INCLUIR_ATENCIONES NUMBER(1) DEFAULT 1 NOT NULL,
    INCLUIR_CONTACTO NUMBER(1) DEFAULT 1 NOT NULL,
    MAX_ACCESOS NUMBER(3) DEFAULT 3 NOT NULL,
    ACCESOS_REALIZADOS NUMBER(3) DEFAULT 0 NOT NULL,
    REVOCADO NUMBER(1) DEFAULT 0 NOT NULL,
    EXPIRA_AT TIMESTAMP WITH TIME ZONE NOT NULL,
    CREATED_AT TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_ACCESO_TEMPORAL_QR PRIMARY KEY (ID),
    CONSTRAINT UQ_ACCESO_QR_PUBLIC_ID UNIQUE (PUBLIC_ID),
    CONSTRAINT UQ_ACCESO_QR_TOKEN_HASH UNIQUE (TOKEN_HASH),
    CONSTRAINT FK_ACCESO_QR_PACIENTE FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID),
    CONSTRAINT CK_ACCESO_QR_ALERGIAS CHECK (INCLUIR_ALERGIAS IN (0, 1)),
    CONSTRAINT CK_ACCESO_QR_MEDICAMENTOS CHECK (INCLUIR_MEDICAMENTOS IN (0, 1)),
    CONSTRAINT CK_ACCESO_QR_ATENCIONES CHECK (INCLUIR_ATENCIONES IN (0, 1)),
    CONSTRAINT CK_ACCESO_QR_CONTACTO CHECK (INCLUIR_CONTACTO IN (0, 1)),
    CONSTRAINT CK_ACCESO_QR_REVOCADO CHECK (REVOCADO IN (0, 1)),
    CONSTRAINT CK_ACCESO_QR_MAX_ACCESOS CHECK (MAX_ACCESOS >= 1),
    CONSTRAINT CK_ACCESO_QR_ACCESOS_REAL CHECK (ACCESOS_REALIZADOS >= 0 AND ACCESOS_REALIZADOS <= MAX_ACCESOS)
);

CREATE INDEX IX_ACCESO_QR_PACIENTE ON ACCESO_TEMPORAL_QR (PACIENTE_ID, REVOCADO);
-- Nota: TOKEN_HASH ya está indexado automáticamente por UQ_ACCESO_QR_TOKEN_HASH.

-- -----------------------------------------------------------------------------
-- 2. Concesión de privilegios a MEDITRIAJE_APP (ADR-012)
-- -----------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE ON ACCESO_TEMPORAL_QR TO MEDITRIAJE_APP;
