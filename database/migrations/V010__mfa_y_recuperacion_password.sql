-- =============================================================================
-- MediTriaje 2.0 — Migración V010: MFA y Recuperación de Contraseña (F2.1, ADR-014)
-- =============================================================================
-- Cambios:
--   1. USUARIO: Se agregan columnas para soporte TOTP (RFC 6238):
--      - MFA_HABILITADO: 0 = deshabilitado, 1 = habilitado.
--      - MFA_SECRET: Secreto criptográfico Base32 del usuario.
--      - MFA_CONFIGURADO_AT: Marca temporal de enrolamiento de MFA.
--   2. CODIGO_VERIFICACION: Almacena códigos numéricos OTP de 6 dígitos hasheados (SHA-256)
--      para recuperación de contraseña y validación de correo, con contador de intentos.
--   3. MFA_BACKUP_CODE: Almacena códigos de respaldo uniuso hasheados (SHA-256) para MFA.
--   4. Privilegios mínimos (ADR-012): Concesión granular a MEDITRIAJE_APP.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Modificación de tabla USUARIO (Campos MFA)
-- -----------------------------------------------------------------------------
ALTER TABLE USUARIO ADD (
    MFA_HABILITADO NUMBER(1) DEFAULT 0 NOT NULL,
    MFA_SECRET VARCHAR2(128 CHAR) NULL,
    MFA_CONFIGURADO_AT TIMESTAMP WITH TIME ZONE NULL
);

ALTER TABLE USUARIO ADD CONSTRAINT CK_USUARIO_MFA CHECK (MFA_HABILITADO IN (0, 1));

-- -----------------------------------------------------------------------------
-- 2. Tabla CODIGO_VERIFICACION (Recuperación de Contraseña con OTP)
-- -----------------------------------------------------------------------------
CREATE TABLE CODIGO_VERIFICACION (
    ID NUMBER GENERATED ALWAYS AS IDENTITY,
    PUBLIC_ID VARCHAR2(36 CHAR) NOT NULL,
    USUARIO_ID NUMBER NOT NULL,
    TIPO VARCHAR2(30 CHAR) DEFAULT 'RECUPERACION_PASSWORD' NOT NULL,
    CODIGO_HASH VARCHAR2(64 CHAR) NOT NULL,
    FECHA_EXPIRACION TIMESTAMP WITH TIME ZONE NOT NULL,
    INTENTOS_FALLIDOS NUMBER(3) DEFAULT 0 NOT NULL,
    MAX_INTENTOS NUMBER(3) DEFAULT 3 NOT NULL,
    USADO NUMBER(1) DEFAULT 0 NOT NULL,
    CREATED_AT TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_CODIGO_VERIFICACION PRIMARY KEY (ID),
    CONSTRAINT UQ_CODVERIF_PUBLIC_ID UNIQUE (PUBLIC_ID),
    CONSTRAINT FK_CODVERIF_USUARIO FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID),
    CONSTRAINT CK_CODVERIF_USADO CHECK (USADO IN (0, 1)),
    CONSTRAINT CK_CODVERIF_INTENTOS CHECK (INTENTOS_FALLIDOS >= 0 AND INTENTOS_FALLIDOS <= MAX_INTENTOS)
);

CREATE INDEX IX_CODVERIF_USUARIO ON CODIGO_VERIFICACION (USUARIO_ID, TIPO, USADO);
CREATE INDEX IX_CODVERIF_HASH ON CODIGO_VERIFICACION (CODIGO_HASH);

-- -----------------------------------------------------------------------------
-- 3. Tabla MFA_BACKUP_CODE (Códigos de respaldo de un solo uso para MFA)
-- -----------------------------------------------------------------------------
CREATE TABLE MFA_BACKUP_CODE (
    ID NUMBER GENERATED ALWAYS AS IDENTITY,
    USUARIO_ID NUMBER NOT NULL,
    CODE_HASH VARCHAR2(64 CHAR) NOT NULL,
    USADO NUMBER(1) DEFAULT 0 NOT NULL,
    USADO_AT TIMESTAMP WITH TIME ZONE NULL,
    CREATED_AT TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_MFA_BACKUP_CODE PRIMARY KEY (ID),
    CONSTRAINT FK_MFABACKUP_USUARIO FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID),
    CONSTRAINT CK_MFABACKUP_USADO CHECK (USADO IN (0, 1))
);

CREATE INDEX IX_MFABACKUP_USUARIO ON MFA_BACKUP_CODE (USUARIO_ID, USADO);

-- -----------------------------------------------------------------------------
-- 4. Privilegios para el usuario de runtime (MEDITRIAJE_APP)
-- -----------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON CODIGO_VERIFICACION TO MEDITRIAJE_APP;
GRANT SELECT, INSERT, UPDATE, DELETE ON MFA_BACKUP_CODE TO MEDITRIAJE_APP;
