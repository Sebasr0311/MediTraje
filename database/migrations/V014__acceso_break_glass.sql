-- =============================================================================
-- MediTriaje 2.0 — Migración V014: Acceso Clínico de Emergencia (Break-Glass) (F2.5, ADR-017)
-- =============================================================================
-- Cambios:
--   1. Tabla ACCESO_BREAK_GLASS: Registro inmutable de autorizaciones temporales
--      extraordinarias invocadas por profesionales asistenciales en emergencias.
--   2. Triggers de inmutabilidad: Bloqueo de UPDATE y DELETE (ADR-008, ADR-017).
--   3. Índices de búsqueda por profesional, paciente y expiración.
--   4. Concesión de privilegios mínimos a MEDITRIAJE_APP (SELECT, INSERT).
-- =============================================================================

CREATE TABLE ACCESO_BREAK_GLASS (
    ID NUMBER GENERATED ALWAYS AS IDENTITY,
    PUBLIC_ID VARCHAR2(36 CHAR) NOT NULL,
    PROFESIONAL_ID NUMBER NOT NULL,
    PACIENTE_ID NUMBER NOT NULL,
    MOTIVO VARCHAR2(500 CHAR) NOT NULL,
    FECHA_EXPIRACION TIMESTAMP WITH TIME ZONE NOT NULL,
    CREATED_AT TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_ACCESO_BREAK_GLASS PRIMARY KEY (ID),
    CONSTRAINT UQ_BREAK_GLASS_PUBLIC_ID UNIQUE (PUBLIC_ID),
    CONSTRAINT FK_BG_PROFESIONAL FOREIGN KEY (PROFESIONAL_ID) REFERENCES PROFESIONAL(ID),
    CONSTRAINT FK_BG_PACIENTE FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID),
    CONSTRAINT CK_BG_MOTIVO_LEN CHECK (LENGTH(TRIM(MOTIVO)) >= 20)
);

CREATE INDEX IX_BG_PROF_PAC ON ACCESO_BREAK_GLASS (PROFESIONAL_ID, PACIENTE_ID, FECHA_EXPIRACION DESC);
CREATE INDEX IX_BG_PACIENTE ON ACCESO_BREAK_GLASS (PACIENTE_ID, FECHA_EXPIRACION DESC);
CREATE INDEX IX_BG_EXPIRACION ON ACCESO_BREAK_GLASS (FECHA_EXPIRACION);

-- Trigger de inmutabilidad para ACCESO_BREAK_GLASS (ADR-008, ADR-017)
CREATE OR REPLACE TRIGGER TR_BREAK_GLASS_INMUTABILIDAD
BEFORE UPDATE OR DELETE ON ACCESO_BREAK_GLASS
FOR EACH ROW
BEGIN
    IF DELETING THEN
        RAISE_APPLICATION_ERROR(-20040, 'Prohibido eliminar registros de acceso break-glass de emergencia.');
    END IF;
    IF UPDATING THEN
        RAISE_APPLICATION_ERROR(-20041, 'Los registros de acceso break-glass son inmutables tras su emision.');
    END IF;
END;
/

-- Concesión de privilegios mínimos a MEDITRIAJE_APP (ADR-012)
GRANT SELECT, INSERT ON ACCESO_BREAK_GLASS TO MEDITRIAJE_APP;
