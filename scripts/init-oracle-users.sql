-- ==============================================================================
-- MediTriaje 2.0 — Provisionamiento de usuarios para Oracle Free en CI/E2E
-- Ejecutar como SYSDBA o SYSTEM conectado a FREEPDB1
-- ==============================================================================

ALTER SESSION SET CONTAINER = FREEPDB1;

-- Crear usuario DDL (propietario del esquema para migraciones Flyway)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM all_users WHERE username = 'MEDITRIAJE_OWNER';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE USER MEDITRIAJE_OWNER IDENTIFIED BY "Owner_Pass1"';
        EXECUTE IMMEDIATE 'GRANT CONNECT, RESOURCE, CREATE SESSION TO MEDITRIAJE_OWNER';
        EXECUTE IMMEDIATE 'ALTER USER MEDITRIAJE_OWNER QUOTA UNLIMITED ON USERS';
    END IF;
END;
/

-- Crear usuario DML (aplicación runtime con privilegios mínimos y sin DELETE clínico)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM all_users WHERE username = 'MEDITRIAJE_APP';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE USER MEDITRIAJE_APP IDENTIFIED BY "App_Pass1"';
        EXECUTE IMMEDIATE 'GRANT CREATE SESSION TO MEDITRIAJE_APP';
    END IF;
END;
/

EXIT;
