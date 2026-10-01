-- =============================================================================
-- MediTriaje 2.0 — Migración V001: Línea Base Técnica
-- =============================================================================
-- Crea una tabla técnica de control para verificar la correcta aplicación
-- de migraciones Flyway sobre Oracle ATP y los privilegios hacia MEDITRIAJE_APP.
-- =============================================================================

CREATE TABLE CONTROL_SISTEMA (
    ID NUMBER GENERATED ALWAYS AS IDENTITY,
    CLAVE VARCHAR2(50 CHAR) NOT NULL,
    VALOR VARCHAR2(255 CHAR) NOT NULL,
    DESCRIPCION VARCHAR2(500 CHAR),
    APLICADO_EN TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_CONTROL_SISTEMA PRIMARY KEY (ID),
    CONSTRAINT UQ_CONTROL_SISTEMA_CLAVE UNIQUE (CLAVE)
);

-- Registro inicial de prueba técnica
INSERT INTO CONTROL_SISTEMA (CLAVE, VALOR, DESCRIPCION)
VALUES ('BASELINE_VERSION', '1.0.0', 'Línea base técnica inicial de MediTriaje 2.0');

-- -----------------------------------------------------------------------------
-- Concesión de privilegios mínimos a MEDITRIAJE_APP (ADR-012)
-- -----------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE ON CONTROL_SISTEMA TO MEDITRIAJE_APP;
