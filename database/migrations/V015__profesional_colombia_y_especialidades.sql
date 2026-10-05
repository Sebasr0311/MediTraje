-- =============================================================================
-- MediTriaje 2.0 — Migración V015: Marco Normativo Colombiano para Profesionales
-- de la Salud (Ley 1164/2007, ReTHUS) y Catálogo Ampliado de Especialidades (Res. 3100/2019)
-- =============================================================================
-- 1. Modificación a la tabla PROFESIONAL:
--    - TIPO_DOCUMENTO (CC, CE): Identificación legal obligatoria para personal habilitado.
--    - NUMERO_DOCUMENTO: Número de identificación civil único.
--    - TELEFONO: Celular de contacto institucional / móvil colombiano.
--    - Restricciones CHECK y UNIQUE para integridad referencial y de identidad.
--
-- 2. Catálogo Oficial de Especialidades Médicas para Colombia (MinSalud Res. 3100 de 2019):
--    - Siembra idempotente (MERGE) de las especialidades fundamentales de consulta
--      ambulatoria con duraciones reguladas de slot (20, 30, 45 minutos).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. EXTENSIÓN DE TABLA PROFESIONAL
-- -----------------------------------------------------------------------------
ALTER TABLE PROFESIONAL ADD (
    TIPO_DOCUMENTO VARCHAR2(5 CHAR) DEFAULT 'CC' NOT NULL,
    NUMERO_DOCUMENTO VARCHAR2(20 CHAR),
    TELEFONO VARCHAR2(20 CHAR)
);

ALTER TABLE PROFESIONAL ADD CONSTRAINT CK_PROFESIONAL_TIPO_DOC 
    CHECK (TIPO_DOCUMENTO IN ('CC', 'CE'));

-- Backfill determinista para registros existentes en caso de haber sido sembrados
UPDATE PROFESIONAL 
SET NUMERO_DOCUMENTO = TO_CHAR(1000000000 + ID)
WHERE NUMERO_DOCUMENTO IS NULL;

UPDATE PROFESIONAL 
SET TELEFONO = '300' || LPAD(TO_CHAR(ID), 7, '0')
WHERE TELEFONO IS NULL;

ALTER TABLE PROFESIONAL MODIFY (NUMERO_DOCUMENTO VARCHAR2(20 CHAR) NOT NULL);

ALTER TABLE PROFESIONAL ADD CONSTRAINT UQ_PROFESIONAL_DOC 
    UNIQUE (TIPO_DOCUMENTO, NUMERO_DOCUMENTO);

-- -----------------------------------------------------------------------------
-- 2. CATÁLOGO AMPLIADO DE ESPECIALIDADES MÉDICAS (RESOLUCIÓN 3100 DE 2019 MINSALUD)
-- -----------------------------------------------------------------------------
MERGE INTO ESPECIALIDAD e
USING (
    SELECT 'esp-medicina-general-001' AS PUBLIC_ID, 'Medicina General' AS NOMBRE, 20 AS DURACION_SLOT_MIN, 'ACTIVO' AS ESTADO FROM DUAL UNION ALL
    SELECT 'esp-pediatria-002', 'Pediatria', 30, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-medicina-interna-003', 'Medicina Interna', 30, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-odontologia-004', 'Odontologia', 30, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-ginecologia-005', 'Ginecologia y Obstetricia', 30, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-cirugia-general-006', 'Cirugia General', 20, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-ortopedia-007', 'Ortopedia y Traumatologia', 20, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-cardiologia-008', 'Cardiologia', 30, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-dermatologia-009', 'Dermatologia', 20, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-psiquiatria-010', 'Psiquiatria', 45, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-oftalmologia-011', 'Oftalmologia', 20, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-otorrino-012', 'Otorrinolaringologia', 20, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-urologia-013', 'Urologia', 20, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-neurologia-014', 'Neurologia', 30, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-endocrinologia-015', 'Endocrinologia', 30, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-neumologia-016', 'Neumologia', 30, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-reumatologia-017', 'Reumatologia', 30, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-gastroenterologia-018', 'Gastroenterologia', 30, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-psicologia-019', 'Psicologia', 45, 'ACTIVO' FROM DUAL UNION ALL
    SELECT 'esp-nutricion-020', 'Nutricion y Dietetica', 30, 'ACTIVO' FROM DUAL
) src
ON (e.NOMBRE = src.NOMBRE)
WHEN NOT MATCHED THEN
    INSERT (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO)
    VALUES (src.PUBLIC_ID, src.NOMBRE, src.DURACION_SLOT_MIN, src.ESTADO);
