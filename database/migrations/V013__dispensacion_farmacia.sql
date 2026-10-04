-- =============================================================================
-- MediTriaje 2.0 — Migración V013: Dispensación Farmacéutica de Recetas (F2.4, ADR-016)
-- =============================================================================
-- Cambios:
--   1. Semilla del rol ROLE_FARMACEUTICO en la tabla ROL (actualizando el check constraint).
--   2. Tabla DISPENSACION: Cabecera del evento de dispensación en farmacia.
--      Inmutable tras su inserción: Trigger TR_DISPENSACION_INMUTABILIDAD bloquea UPDATE y DELETE.
--   3. Tabla DISPENSACION_DETALLE: Detalle de medicamentos entregados con cantidad y lote INVIMA.
--      Inmutable tras su inserción: Trigger TR_DISP_DETALLE_INMUTABILIDAD bloquea UPDATE y DELETE.
--   4. Índices relacionales de búsqueda e integridad referencial.
--   5. Concesión de privilegios mínimos a MEDITRIAJE_APP (SELECT, INSERT; sin UPDATE ni DELETE).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ROL: Habilitar ROLE_FARMACEUTICO
-- -----------------------------------------------------------------------------
ALTER TABLE ROL DROP CONSTRAINT CK_ROL_NOMBRE;
ALTER TABLE ROL ADD CONSTRAINT CK_ROL_NOMBRE CHECK (NOMBRE IN ('ROLE_PACIENTE', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR', 'ROLE_FARMACEUTICO'));

INSERT INTO ROL (NOMBRE, DESCRIPCION) VALUES ('ROLE_FARMACEUTICO', 'Personal de servicio farmacéutico y dispensación de medicamentos');

-- -----------------------------------------------------------------------------
-- 2. TABLA: DISPENSACION
-- -----------------------------------------------------------------------------
CREATE TABLE DISPENSACION (
    ID NUMBER GENERATED ALWAYS AS IDENTITY,
    PUBLIC_ID VARCHAR2(36 CHAR) NOT NULL,
    RECETA_ID NUMBER NOT NULL,
    SEDE_ID NUMBER NOT NULL,
    USUARIO_ID NUMBER NOT NULL,
    OBSERVACIONES VARCHAR2(500 CHAR) NULL,
    CREATED_AT TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_DISPENSACION PRIMARY KEY (ID),
    CONSTRAINT UQ_DISPENSACION_PUBLIC_ID UNIQUE (PUBLIC_ID),
    CONSTRAINT FK_DISPENSACION_RECETA FOREIGN KEY (RECETA_ID) REFERENCES RECETA(ID),
    CONSTRAINT FK_DISPENSACION_SEDE FOREIGN KEY (SEDE_ID) REFERENCES SEDE(ID),
    CONSTRAINT FK_DISPENSACION_USUARIO FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID)
);

CREATE INDEX IX_DISPENSACION_RECETA ON DISPENSACION (RECETA_ID, CREATED_AT DESC);
CREATE INDEX IX_DISPENSACION_SEDE ON DISPENSACION (SEDE_ID);
CREATE INDEX IX_DISPENSACION_USUARIO ON DISPENSACION (USUARIO_ID);

-- Trigger de inmutabilidad para DISPENSACION (ADR-008, ADR-016)
CREATE OR REPLACE TRIGGER TR_DISPENSACION_INMUTABILIDAD
BEFORE UPDATE OR DELETE ON DISPENSACION
FOR EACH ROW
BEGIN
    IF DELETING THEN
        RAISE_APPLICATION_ERROR(-20030, 'Prohibido eliminar registros de dispensacion farmaceutica.');
    END IF;
    IF UPDATING THEN
        RAISE_APPLICATION_ERROR(-20031, 'Los registros de dispensacion son inmutables tras su emision.');
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 3. TABLA: DISPENSACION_DETALLE
-- -----------------------------------------------------------------------------
CREATE TABLE DISPENSACION_DETALLE (
    ID NUMBER GENERATED ALWAYS AS IDENTITY,
    DISPENSACION_ID NUMBER NOT NULL,
    RECETA_DETALLE_ID NUMBER NOT NULL,
    CANTIDAD_ENTREGADA NUMBER NOT NULL,
    LOTE VARCHAR2(50 CHAR) NULL,
    FECHA_VENCIMIENTO_LOTE DATE NULL,
    CREATED_AT TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_DISPENSACION_DETALLE PRIMARY KEY (ID),
    CONSTRAINT FK_DD_DISPENSACION FOREIGN KEY (DISPENSACION_ID) REFERENCES DISPENSACION(ID),
    CONSTRAINT FK_DD_RECETA_DETALLE FOREIGN KEY (RECETA_DETALLE_ID) REFERENCES RECETA_DETALLE(ID),
    CONSTRAINT CK_DD_CANTIDAD CHECK (CANTIDAD_ENTREGADA > 0)
);

CREATE INDEX IX_DD_DISPENSACION ON DISPENSACION_DETALLE (DISPENSACION_ID);
CREATE INDEX IX_DD_RECETA_DETALLE ON DISPENSACION_DETALLE (RECETA_DETALLE_ID);

-- Trigger de inmutabilidad para DISPENSACION_DETALLE (ADR-008, ADR-016)
CREATE OR REPLACE TRIGGER TR_DISP_DETALLE_INMUTABILIDAD
BEFORE UPDATE OR DELETE ON DISPENSACION_DETALLE
FOR EACH ROW
BEGIN
    IF DELETING THEN
        RAISE_APPLICATION_ERROR(-20032, 'Prohibido eliminar detalles de dispensacion farmaceutica.');
    END IF;
    IF UPDATING THEN
        RAISE_APPLICATION_ERROR(-20033, 'Los detalles de dispensacion son inmutables tras su entrega.');
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 4. CONCESIÓN DE PRIVILEGIOS MÍNIMOS A MEDITRIAJE_APP (ADR-012)
-- -----------------------------------------------------------------------------
GRANT SELECT, INSERT ON DISPENSACION TO MEDITRIAJE_APP;
GRANT SELECT, INSERT ON DISPENSACION_DETALLE TO MEDITRIAJE_APP;
