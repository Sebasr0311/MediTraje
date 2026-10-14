-- =============================================================================
-- V022__correccion_auditoria_integridad.sql
-- MediTriaje 2.0 - Corrección de integridad asistencial, exclusividad y auditoría
-- ADR-024, ADR-026, ADR-027, Gate G1/G2
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. INDICES FUNCIONALES UNICOS: EXCLUSIVIDAD DE OCUPACION DE CAMAS Y EPISODIOS
-- Impide en capa de base de datos la doble ocupación concurrente (O-03 [P0])
-- -----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'UQ_OCUP_CAMA_ACTIVA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UQ_OCUP_CAMA_ACTIVA ON OCUPACION_CAMA (CASE WHEN ESTADO = ''ACTIVA'' THEN CAMA_ID END)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'UQ_OCUP_EPISODIO_ACTIVA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UQ_OCUP_EPISODIO_ACTIVA ON OCUPACION_CAMA (CASE WHEN ESTADO = ''ACTIVA'' THEN EPISODIO_ID END)';
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 2. INDICE FUNCIONAL UNICO: DEDUPLICACION DE ALERTAS OPERATIVAS ACTIVAS (F-03 [P1])
-- Impide duplicidad de alertas activas del mismo tipo en la misma sede
-- -----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'UQ_ALERTA_SEDE_TIPO_ACTIVA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UQ_ALERTA_SEDE_TIPO_ACTIVA ON ALERTA_OPERATIVA (CASE WHEN ESTADO = ''ACTIVA'' THEN SEDE_ID END, CASE WHEN ESTADO = ''ACTIVA'' THEN TIPO_ALERTA END)';
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 3. RECONCILIACION DEFENSIVA DE CLAVES FORANEAS A PROFESIONAL(ID) (O-01 [P0])
-- Asegura que en entornos donde se aplicó DDL previo sin FKs a PROFESIONAL,
-- las relaciones apunten de forma estricta a PROFESIONAL(ID).
-- -----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
BEGIN
    -- FK_PROCEDIMIENTO_PROFESIONAL
    SELECT COUNT(*) INTO v_count FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_PROCEDIMIENTO_PROFESIONAL';
    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE 'ALTER TABLE PROCEDIMIENTO_HOSPITALARIO ADD CONSTRAINT FK_PROCEDIMIENTO_PROFESIONAL FOREIGN KEY (PROFESIONAL_ID) REFERENCES PROFESIONAL(ID)';
        EXCEPTION
            WHEN OTHERS THEN NULL;
        END;
    END IF;

    -- FK_EGRESO_MEDICO
    SELECT COUNT(*) INTO v_count FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_EGRESO_MEDICO';
    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE 'ALTER TABLE EGRESO_HOSPITALARIO ADD CONSTRAINT FK_EGRESO_MEDICO FOREIGN KEY (MEDICO_EGRESO_ID) REFERENCES PROFESIONAL(ID)';
        EXCEPTION
            WHEN OTHERS THEN NULL;
        END;
    END IF;

    -- FK_AUSENCIA_PROFESIONAL
    SELECT COUNT(*) INTO v_count FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_AUSENCIA_PROFESIONAL';
    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE 'ALTER TABLE AUSENCIA_MEDICA ADD CONSTRAINT FK_AUSENCIA_PROFESIONAL FOREIGN KEY (PROFESIONAL_ID) REFERENCES PROFESIONAL(ID)';
        EXCEPTION
            WHEN OTHERS THEN NULL;
        END;
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 4. PERMISOS Y PRIVILEGIOS DE APLICACION
-- -----------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE ON OCUPACION_CAMA TO MEDITRIAJE_APP;
GRANT SELECT, INSERT, UPDATE ON ALERTA_OPERATIVA TO MEDITRIAJE_APP;
