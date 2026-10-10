-- =============================================================================
-- MediTriaje 2.0 — Migración V016: Gestión e Inmutabilidad de Alergias Clínicas (T4)
-- =============================================================================
-- Evolución de la tabla ALERGIA (ADR-008, D3, T4):
--   1. Añade identificador público UUID (PUBLIC_ID), estado de actividad,
--      origen clínico (PROFESIONAL vs PACIENTE autorreportada), trazabilidad
--      de autoría/atención médica y metadatos de inactivación.
--   2. Índice compuesto de búsqueda y unicidad funcional para alergias ACTIVAS
--      por paciente y sustancia.
--   3. Trigger TR_ALERGIA_INMUTABILIDAD: bloquea DELETE; en UPDATE solo permite
--      pasar ACTIVA -> INACTIVA con motivo obligatorio y usuario responsable.
--   4. Privilegios mínimos: SELECT, INSERT, UPDATE para MEDITRIAJE_APP (sin DELETE).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ALTER TABLE ALERGIA: Añadir columnas requeridas
-- -----------------------------------------------------------------------------
ALTER TABLE ALERGIA ADD (
    PUBLIC_ID VARCHAR2(36 CHAR),
    ESTADO VARCHAR2(20 CHAR) DEFAULT 'ACTIVA' NOT NULL,
    ORIGEN VARCHAR2(20 CHAR) DEFAULT 'PROFESIONAL' NOT NULL,
    REGISTRADA_POR_USUARIO_ID NUMBER,
    ATENCION_ID NUMBER,
    FECHA_INACTIVACION TIMESTAMP WITH TIME ZONE,
    INACTIVADA_POR_USUARIO_ID NUMBER,
    MOTIVO_INACTIVACION VARCHAR2(500 CHAR),
    UPDATED_AT TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Backfill determinista de filas preexistentes con formato UUID canónico
UPDATE ALERGIA
SET PUBLIC_ID = LOWER(REGEXP_REPLACE(RAWTOHEX(SYS_GUID()), '([A-F0-9]{8})([A-F0-9]{4})([A-F0-9]{4})([A-F0-9]{4})([A-F0-9]{12})', '\1-\2-\3-\4-\5'))
WHERE PUBLIC_ID IS NULL;

ALTER TABLE ALERGIA MODIFY PUBLIC_ID VARCHAR2(36 CHAR) NOT NULL;
ALTER TABLE ALERGIA ADD CONSTRAINT UQ_ALERGIA_PUBLIC_ID UNIQUE (PUBLIC_ID);

-- Constraints de integridad y orígenes válidos
ALTER TABLE ALERGIA ADD CONSTRAINT CK_ALERGIA_ESTADO CHECK (ESTADO IN ('ACTIVA', 'INACTIVA'));
ALTER TABLE ALERGIA ADD CONSTRAINT CK_ALERGIA_ORIGEN CHECK (ORIGEN IN ('PROFESIONAL', 'PACIENTE'));
ALTER TABLE ALERGIA ADD CONSTRAINT FK_ALERGIA_REGISTRADA_POR FOREIGN KEY (REGISTRADA_POR_USUARIO_ID) REFERENCES USUARIO(ID);
ALTER TABLE ALERGIA ADD CONSTRAINT FK_ALERGIA_ATENCION FOREIGN KEY (ATENCION_ID) REFERENCES ATENCION(ID);
ALTER TABLE ALERGIA ADD CONSTRAINT FK_ALERGIA_INACTIVADA_POR FOREIGN KEY (INACTIVADA_POR_USUARIO_ID) REFERENCES USUARIO(ID);

-- -----------------------------------------------------------------------------
-- 2. ÍNDICES: Consulta por paciente/estado y unicidad de sustancia activa
-- -----------------------------------------------------------------------------
CREATE INDEX IX_ALERGIA_PACIENTE_ESTADO ON ALERGIA (PACIENTE_ID, ESTADO);

-- Unicidad de sustancia ACTIVA por paciente (múltiples inactivas permitidas históricamente)
CREATE UNIQUE INDEX UQ_ALERGIA_PAC_SUST_ACTIVA ON ALERGIA (
    PACIENTE_ID,
    UPPER(SUSTANCIA),
    CASE WHEN ESTADO = 'ACTIVA' THEN 'ACTIVA' ELSE NULL END
);

-- -----------------------------------------------------------------------------
-- 3. TRIGGER: Inmutabilidad clínica estricta (D3, ADR-008)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TR_ALERGIA_INMUTABILIDAD
BEFORE UPDATE OR DELETE ON ALERGIA
FOR EACH ROW
BEGIN
    IF DELETING THEN
        RAISE_APPLICATION_ERROR(-20020, 'Prohibido eliminar registros de alergias e hipersensibilidades clinicas.');
    END IF;

    IF UPDATING THEN
        IF :OLD.ESTADO = 'INACTIVA' THEN
            RAISE_APPLICATION_ERROR(-20021, 'Una alergia inactiva no puede ser modificada ni reactivada.');
        END IF;

        IF :NEW.ESTADO <> 'INACTIVA' THEN
            RAISE_APPLICATION_ERROR(-20022, 'El estado de una alergia activa solo puede cambiar a INACTIVA.');
        END IF;

        -- Prohibir alteración de datos clínicos originales o autorías
        IF :NEW.ID <> :OLD.ID
           OR :NEW.PUBLIC_ID <> :OLD.PUBLIC_ID
           OR :NEW.PACIENTE_ID <> :OLD.PACIENTE_ID
           OR UPPER(:NEW.SUSTANCIA) <> UPPER(:OLD.SUSTANCIA)
           OR NVL(:NEW.REACCION, '~NULL~') <> NVL(:OLD.REACCION, '~NULL~')
           OR :NEW.SEVERIDAD <> :OLD.SEVERIDAD
           OR :NEW.ORIGEN <> :OLD.ORIGEN
           OR NVL(:NEW.REGISTRADA_POR_USUARIO_ID, -1) <> NVL(:OLD.REGISTRADA_POR_USUARIO_ID, -1)
           OR NVL(:NEW.ATENCION_ID, -1) <> NVL(:OLD.ATENCION_ID, -1)
           OR :NEW.CREATED_AT <> :OLD.CREATED_AT THEN
            RAISE_APPLICATION_ERROR(-20023, 'Los datos clinicos de una alergia son inmutables. Para corregir, inactive y cree una nueva.');
        END IF;

        IF :NEW.MOTIVO_INACTIVACION IS NULL OR LENGTH(TRIM(:NEW.MOTIVO_INACTIVACION)) = 0 THEN
            RAISE_APPLICATION_ERROR(-20024, 'El motivo de inactivacion es obligatorio.');
        END IF;

        IF :NEW.INACTIVADA_POR_USUARIO_ID IS NULL THEN
            RAISE_APPLICATION_ERROR(-20025, 'El usuario que inactiva la alergia es obligatorio.');
        END IF;

        IF :NEW.FECHA_INACTIVACION IS NULL THEN
            :NEW.FECHA_INACTIVACION := CURRENT_TIMESTAMP;
        END IF;

        :NEW.UPDATED_AT := CURRENT_TIMESTAMP;
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 4. PRIVILEGIOS MÍNIMOS A MEDITRIAJE_APP (ADR-012)
-- -----------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE ON ALERGIA TO MEDITRIAJE_APP;
