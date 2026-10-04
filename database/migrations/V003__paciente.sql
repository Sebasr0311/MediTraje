-- =============================================================================
-- MediTriaje 2.0 — Migración V003: Módulo de Pacientes
-- =============================================================================
-- Tabla: PACIENTE
-- Incluye restricciones de documento, clave pública UUID y relación 1:1 con USUARIO.
-- Concesión de privilegios mínimos a MEDITRIAJE_APP según ADR-012.
-- =============================================================================

CREATE TABLE PACIENTE (
    ID NUMBER GENERATED ALWAYS AS IDENTITY,
    USUARIO_ID NUMBER NOT NULL,
    PUBLIC_ID VARCHAR2(36 CHAR) NOT NULL,
    TIPO_DOCUMENTO VARCHAR2(5 CHAR) NOT NULL,
    NUMERO_DOCUMENTO VARCHAR2(20 CHAR) NOT NULL,
    NOMBRES VARCHAR2(60 CHAR) NOT NULL,
    APELLIDOS VARCHAR2(60 CHAR) NOT NULL,
    FECHA_NACIMIENTO DATE NOT NULL,
    TELEFONO VARCHAR2(20 CHAR),
    CREATED_AT TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT TIMESTAMP WITH TIME ZONE,
    CONSTRAINT PK_PACIENTE PRIMARY KEY (ID),
    CONSTRAINT UQ_PACIENTE_USUARIO UNIQUE (USUARIO_ID),
    CONSTRAINT UQ_PACIENTE_PUBLIC_ID UNIQUE (PUBLIC_ID),
    CONSTRAINT UQ_PACIENTE_DOC UNIQUE (TIPO_DOCUMENTO, NUMERO_DOCUMENTO),
    CONSTRAINT FK_PACIENTE_USUARIO FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID),
    CONSTRAINT CK_PACIENTE_TIPO_DOC CHECK (TIPO_DOCUMENTO IN ('CC', 'TI', 'RC', 'CE', 'PA'))
);

CREATE INDEX IX_PACIENTE_USUARIO ON PACIENTE (USUARIO_ID);

-- -----------------------------------------------------------------------------
-- Concesión de privilegios mínimos a MEDITRIAJE_APP (ADR-012)
-- -----------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE ON PACIENTE TO MEDITRIAJE_APP;
