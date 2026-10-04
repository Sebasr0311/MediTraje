-- =============================================================================
-- MediTriaje 2.0 — Semillas de Prueba Ficticias: Fase F2.4 (Dispensación Farmacéutica)
-- =============================================================================
-- ADVERTENCIA ESTRICTA:
-- Este script contiene datos 100% ficticios generados EXCLUSIVAMENTE para entornos
-- de desarrollo local (dev) y pruebas de integración.
-- QUEDA TERMINANTEMENTE PROHIBIDO SU USO EN ENTORNOS PRODUCTIVOS (ADR-004, ADR-012, ADR-016).
-- =============================================================================
-- Contenido:
--   1. Usuario Farmacéutico (farmacia@meditriaje.com / Admin12345*) con ROLE_FARMACEUTICO
-- =============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_rol_farm_id       NUMBER;
    v_user_farm_id      NUMBER;
    v_count             NUMBER;
    
    -- Hash Argon2id para Admin12345*
    c_hash_admin CONSTANT VARCHAR2(255) := '$argon2id$v=19$m=16384,t=2,p=1$Ajn68zVKb+PZDGBLtT7YwQ$aK1oBNJJqgw2hR3jPxB6EVk5dJIR5UFmKOR7Lh2TDBk';
BEGIN
    DBMS_OUTPUT.PUT_LINE('Cargando semillas ficticias de la Fase F2.4 (Ambiente DEV)...');

    -- Obtener ID del rol ROLE_FARMACEUTICO
    SELECT ID INTO v_rol_farm_id FROM ROL WHERE NOMBRE = 'ROLE_FARMACEUTICO';

    -- Verificar si el usuario de farmacia ya existe
    SELECT COUNT(*) INTO v_count FROM USUARIO WHERE EMAIL = 'farmacia@meditriaje.com';

    IF v_count = 0 THEN
        INSERT INTO USUARIO (
            PUBLIC_ID,
            EMAIL,
            PASSWORD_HASH,
            ESTADO,
            INTENTOS_FALLIDOS,
            BLOQUEADO_HASTA,
            CREATED_AT,
            DEBE_CAMBIAR_PASSWORD
        ) VALUES (
            'f0000000-0000-0000-0000-000000000001',
            'farmacia@meditriaje.com',
            c_hash_admin,
            'ACTIVO',
            0,
            NULL,
            CURRENT_TIMESTAMP,
            0
        ) RETURNING ID INTO v_user_farm_id;

        INSERT INTO USUARIO_ROL (USUARIO_ID, ROL_ID)
        VALUES (v_user_farm_id, v_rol_farm_id);

        DBMS_OUTPUT.PUT_LINE('Usuario Farmacia creado: farmacia@meditriaje.com (ID: ' || v_user_farm_id || ')');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Usuario farmacia@meditriaje.com ya existe. Omitiendo inserción.');
    END IF;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Semillas F2.4 aplicadas exitosamente.');
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('Error al aplicar semillas F2.4: ' || SQLERRM);
        RAISE;
END;
/
