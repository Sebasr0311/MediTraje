-- =============================================================================
-- MediTriaje 2.0 — Semillas de Prueba Ficticias: Fase M3 (Oferta y Administración)
-- =============================================================================
-- ADVERTENCIA ESTRICTA:
-- Este script contiene datos 100% ficticios generados EXCLUSIVAMENTE para entornos
-- de desarrollo local (dev) y pruebas de integración.
-- QUEDA TERMINANTEMENTE PROHIBIDO SU USO EN ENTORNOS PRODUCTIVOS (ADR-004, ADR-012).
-- =============================================================================
-- Contenido:
--   1. Usuario Administrador de plataforma (admin@meditriaje.com / Admin12345*)
--   2. 1 Institución prestadora de salud (IPS MediSalud Valledupar)
--   3. 2 Sedes asistenciales (Sede Centro, Sede Norte)
--   4. 4 Especialidades médicas (Medicina General, Pediatría, Med. Interna, Odontología)
--   5. 4 Profesionales de salud con usuarios y rol ROLE_PROFESIONAL
--   6. Slots de disponibilidad para los próximos 14 días hábiles (America/Bogota)
-- =============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_admin_id          NUMBER;
    v_rol_admin_id      NUMBER;
    v_rol_prof_id       NUMBER;
    v_inst_id           NUMBER;
    v_sede_centro_id    NUMBER;
    v_sede_norte_id     NUMBER;
    v_esp_mg_id         NUMBER;
    v_esp_ped_id        NUMBER;
    v_esp_mi_id         NUMBER;
    v_esp_odo_id        NUMBER;
    v_user_prof1_id     NUMBER;
    v_user_prof2_id     NUMBER;
    v_user_prof3_id     NUMBER;
    v_user_prof4_id     NUMBER;
    v_prof1_id          NUMBER;
    v_prof2_id          NUMBER;
    v_prof3_id          NUMBER;
    v_prof4_id          NUMBER;
    
    -- Hash Argon2id para Admin12345*
    c_hash_admin CONSTANT VARCHAR2(255) := '$argon2id$v=19$m=16384,t=2,p=1$Ajn68zVKb+PZDGBLtT7YwQ$aK1oBNJJqgw2hR3jPxB6EVk5dJIR5UFmKOR7Lh2TDBk';
    -- Hash Argon2id para Temporal12345*
    c_hash_temporal CONSTANT VARCHAR2(255) := '$argon2id$v=19$m=16384,t=2,p=1$pXTJsGBw1cexjB8XLdot9Q$l+/ivFYz8/1Z7GQUct0/25vzKAytYhlerVGb2DwALjs';

    v_base_date         DATE;
    v_curr_date         DATE;
    v_day_of_week       VARCHAR2(10);
    v_slot_inicio       TIMESTAMP WITH TIME ZONE;
    v_slot_fin          TIMESTAMP WITH TIME ZONE;
    v_slots_count       NUMBER := 0;
BEGIN
    DBMS_OUTPUT.PUT_LINE('Cargando semillas ficticias de la Fase M3 (Ambiente DEV)...');

    -- Obtener IDs de roles de sistema
    SELECT ID INTO v_rol_admin_id FROM ROL WHERE NOMBRE = 'ROLE_ADMINISTRADOR';
    SELECT ID INTO v_rol_prof_id  FROM ROL WHERE NOMBRE = 'ROLE_PROFESIONAL';

    -- -------------------------------------------------------------------------
    -- 1. USUARIO ADMINISTRADOR
    -- -------------------------------------------------------------------------
    BEGIN
        SELECT ID INTO v_admin_id FROM USUARIO WHERE EMAIL = 'admin@meditriaje.com';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, DEBE_CAMBIAR_PASSWORD)
            VALUES ('usr-admin-valledupar-001', 'admin@meditriaje.com', c_hash_admin, 'ACTIVO', 0, 0)
            RETURNING ID INTO v_admin_id;

            INSERT INTO USUARIO_ROL (USUARIO_ID, ROL_ID) VALUES (v_admin_id, v_rol_admin_id);
            DBMS_OUTPUT.PUT_LINE('Usuario Administrador creado: admin@meditriaje.com');
    END;

    -- -------------------------------------------------------------------------
    -- 2. INSTITUCIÓN DE SALUD (1)
    -- -------------------------------------------------------------------------
    BEGIN
        SELECT ID INTO v_inst_id FROM INSTITUCION WHERE NIT = '900123456-1';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO INSTITUCION (PUBLIC_ID, NIT, RAZON_SOCIAL, ESTADO)
            VALUES ('inst-medisalud-valledupar-001', '900123456-1', 'IPS MediSalud Valledupar S.A.S.', 'ACTIVO')
            RETURNING ID INTO v_inst_id;
            DBMS_OUTPUT.PUT_LINE('Institución creada: IPS MediSalud Valledupar');
    END;

    -- -------------------------------------------------------------------------
    -- 3. SEDES ASISTENCIALES (2)
    -- -------------------------------------------------------------------------
    BEGIN
        SELECT ID INTO v_sede_centro_id FROM SEDE WHERE PUBLIC_ID = 'sede-centro-valledupar-001';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO SEDE (INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD, ESTADO)
            VALUES (v_inst_id, 'sede-centro-valledupar-001', 'Sede Centro Valledupar', 'Calle 16 # 12-45', 'Valledupar', 'ACTIVO')
            RETURNING ID INTO v_sede_centro_id;
    END;

    BEGIN
        SELECT ID INTO v_sede_norte_id FROM SEDE WHERE PUBLIC_ID = 'sede-norte-valledupar-002';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO SEDE (INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD, ESTADO)
            VALUES (v_inst_id, 'sede-norte-valledupar-002', 'Sede Norte Valledupar', 'Carrera 19D # 5-30', 'Valledupar', 'ACTIVO')
            RETURNING ID INTO v_sede_norte_id;
    END;

    -- -------------------------------------------------------------------------
    -- 4. ESPECIALIDADES MÉDICAS (4)
    -- -------------------------------------------------------------------------
    BEGIN
        SELECT ID INTO v_esp_mg_id FROM ESPECIALIDAD WHERE NOMBRE = 'Medicina General';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO ESPECIALIDAD (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO)
            VALUES ('esp-medicina-general-001', 'Medicina General', 20, 'ACTIVO')
            RETURNING ID INTO v_esp_mg_id;
    END;

    BEGIN
        SELECT ID INTO v_esp_ped_id FROM ESPECIALIDAD WHERE NOMBRE = 'Pediatria';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO ESPECIALIDAD (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO)
            VALUES ('esp-pediatria-002', 'Pediatria', 30, 'ACTIVO')
            RETURNING ID INTO v_esp_ped_id;
    END;

    BEGIN
        SELECT ID INTO v_esp_mi_id FROM ESPECIALIDAD WHERE NOMBRE = 'Medicina Interna';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO ESPECIALIDAD (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO)
            VALUES ('esp-medicina-interna-003', 'Medicina Interna', 30, 'ACTIVO')
            RETURNING ID INTO v_esp_mi_id;
    END;

    BEGIN
        SELECT ID INTO v_esp_odo_id FROM ESPECIALIDAD WHERE NOMBRE = 'Odontologia';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO ESPECIALIDAD (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO)
            VALUES ('esp-odontologia-004', 'Odontologia', 30, 'ACTIVO')
            RETURNING ID INTO v_esp_odo_id;
    END;

    -- -------------------------------------------------------------------------
    -- 5. PROFESIONALES ASISTENCIALES Y SUS USUARIOS (4)
    -- -------------------------------------------------------------------------
    -- Prof 1: Dr. Carlos Mendoza (Medicina General)
    BEGIN
        SELECT ID INTO v_user_prof1_id FROM USUARIO WHERE EMAIL = 'carlos.mendoza@meditriaje.com';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, DEBE_CAMBIAR_PASSWORD)
            VALUES ('usr-prof-carlos-mendoza-001', 'carlos.mendoza@meditriaje.com', c_hash_temporal, 'ACTIVO', 0, 1)
            RETURNING ID INTO v_user_prof1_id;
            INSERT INTO USUARIO_ROL (USUARIO_ID, ROL_ID) VALUES (v_user_prof1_id, v_rol_prof_id);
    END;

    BEGIN
        SELECT ID INTO v_prof1_id FROM PROFESIONAL WHERE REGISTRO_MEDICO = 'RM-102938';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS)
            VALUES (v_user_prof1_id, 'prof-carlos-mendoza-001', v_esp_mg_id, 'RM-102938', 'Carlos Alberto', 'Mendoza Vega')
            RETURNING ID INTO v_prof1_id;
    END;

    -- Prof 2: Dra. Laura Gómez (Pediatría)
    BEGIN
        SELECT ID INTO v_user_prof2_id FROM USUARIO WHERE EMAIL = 'laura.gomez@meditriaje.com';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, DEBE_CAMBIAR_PASSWORD)
            VALUES ('usr-prof-laura-gomez-002', 'laura.gomez@meditriaje.com', c_hash_temporal, 'ACTIVO', 0, 1)
            RETURNING ID INTO v_user_prof2_id;
            INSERT INTO USUARIO_ROL (USUARIO_ID, ROL_ID) VALUES (v_user_prof2_id, v_rol_prof_id);
    END;

    BEGIN
        SELECT ID INTO v_prof2_id FROM PROFESIONAL WHERE REGISTRO_MEDICO = 'RM-203948';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS)
            VALUES (v_user_prof2_id, 'prof-laura-gomez-002', v_esp_ped_id, 'RM-203948', 'Laura Sofia', 'Gomez Rueda')
            RETURNING ID INTO v_prof2_id;
    END;

    -- Prof 3: Dr. Andrés Castro (Medicina Interna)
    BEGIN
        SELECT ID INTO v_user_prof3_id FROM USUARIO WHERE EMAIL = 'andres.castro@meditriaje.com';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, DEBE_CAMBIAR_PASSWORD)
            VALUES ('usr-prof-andres-castro-003', 'andres.castro@meditriaje.com', c_hash_temporal, 'ACTIVO', 0, 1)
            RETURNING ID INTO v_user_prof3_id;
            INSERT INTO USUARIO_ROL (USUARIO_ID, ROL_ID) VALUES (v_user_prof3_id, v_rol_prof_id);
    END;

    BEGIN
        SELECT ID INTO v_prof3_id FROM PROFESIONAL WHERE REGISTRO_MEDICO = 'RM-304958';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS)
            VALUES (v_user_prof3_id, 'prof-andres-castro-003', v_esp_mi_id, 'RM-304958', 'Andres Felipe', 'Castro Ortiz')
            RETURNING ID INTO v_prof3_id;
    END;

    -- Prof 4: Dra. Marcela Morales (Odontología)
    BEGIN
        SELECT ID INTO v_user_prof4_id FROM USUARIO WHERE EMAIL = 'marcela.morales@meditriaje.com';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, DEBE_CAMBIAR_PASSWORD)
            VALUES ('usr-prof-marcela-morales-004', 'marcela.morales@meditriaje.com', c_hash_temporal, 'ACTIVO', 0, 1)
            RETURNING ID INTO v_user_prof4_id;
            INSERT INTO USUARIO_ROL (USUARIO_ID, ROL_ID) VALUES (v_user_prof4_id, v_rol_prof_id);
    END;

    BEGIN
        SELECT ID INTO v_prof4_id FROM PROFESIONAL WHERE REGISTRO_MEDICO = 'RM-405968';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS)
            VALUES (v_user_prof4_id, 'prof-marcela-morales-004', v_esp_odo_id, 'RM-405968', 'Marcela Patricia', 'Morales Diaz')
            RETURNING ID INTO v_prof4_id;
    END;

    -- -------------------------------------------------------------------------
    -- 6. SLOTS DE DISPONIBILIDAD PARA LOS PRÓXIMOS 14 DÍAS HÁBILES
    -- -------------------------------------------------------------------------
    v_base_date := TRUNC(SYSDATE) + 1; -- Empezando desde mañana

    FOR i IN 0..13 LOOP
        v_curr_date := v_base_date + i;
        -- Verificar si es día hábil (lunes a viernes: 2..6 en formato NLS o comprobación con TO_CHAR)
        IF TO_CHAR(v_curr_date, 'DY', 'NLS_DATE_LANGUAGE=ENGLISH') NOT IN ('SAT', 'SUN') THEN
            -- Generar 3 slots matutinos y 3 slots vespertinos para cada profesional
            -- Prof 1 (Medicina General - 20 min en Sede Centro)
            FOR h IN 0..2 LOOP
                v_slot_inicio := FROM_TZ(CAST(v_curr_date + (8 + h * 0.5) / 24 AS TIMESTAMP), 'America/Bogota');
                v_slot_fin    := v_slot_inicio + NUMTODSINTERVAL(20, 'MINUTE');
                BEGIN
                    INSERT INTO DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, SEDE_ID, ESPECIALIDAD_ID, FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO)
                    VALUES (SYS_GUID(), v_prof1_id, v_sede_centro_id, v_esp_mg_id, v_slot_inicio, v_slot_fin, 'PRESENCIAL', 'LIBRE');
                    v_slots_count := v_slots_count + 1;
                EXCEPTION WHEN DUP_VAL_ON_INDEX THEN NULL;
                END;
            END LOOP;

            -- Prof 2 (Pediatría - 30 min en Sede Centro)
            FOR h IN 0..1 LOOP
                v_slot_inicio := FROM_TZ(CAST(v_curr_date + (9 + h * 0.5) / 24 AS TIMESTAMP), 'America/Bogota');
                v_slot_fin    := v_slot_inicio + NUMTODSINTERVAL(30, 'MINUTE');
                BEGIN
                    INSERT INTO DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, SEDE_ID, ESPECIALIDAD_ID, FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO)
                    VALUES (SYS_GUID(), v_prof2_id, v_sede_centro_id, v_esp_ped_id, v_slot_inicio, v_slot_fin, 'PRESENCIAL', 'LIBRE');
                    v_slots_count := v_slots_count + 1;
                EXCEPTION WHEN DUP_VAL_ON_INDEX THEN NULL;
                END;
            END LOOP;

            -- Prof 3 (Medicina Interna - 30 min en Sede Norte, Telemedicina)
            FOR h IN 0..1 LOOP
                v_slot_inicio := FROM_TZ(CAST(v_curr_date + (14 + h * 0.5) / 24 AS TIMESTAMP), 'America/Bogota');
                v_slot_fin    := v_slot_inicio + NUMTODSINTERVAL(30, 'MINUTE');
                BEGIN
                    INSERT INTO DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, SEDE_ID, ESPECIALIDAD_ID, FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO)
                    VALUES (SYS_GUID(), v_prof3_id, v_sede_norte_id, v_esp_mi_id, v_slot_inicio, v_slot_fin, 'TELEMEDICINA', 'LIBRE');
                    v_slots_count := v_slots_count + 1;
                EXCEPTION WHEN DUP_VAL_ON_INDEX THEN NULL;
                END;
            END LOOP;

            -- Prof 4 (Odontología - 30 min en Sede Norte)
            FOR h IN 0..1 LOOP
                v_slot_inicio := FROM_TZ(CAST(v_curr_date + (15 + h * 0.5) / 24 AS TIMESTAMP), 'America/Bogota');
                v_slot_fin    := v_slot_inicio + NUMTODSINTERVAL(30, 'MINUTE');
                BEGIN
                    INSERT INTO DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, SEDE_ID, ESPECIALIDAD_ID, FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO)
                    VALUES (SYS_GUID(), v_prof4_id, v_sede_norte_id, v_esp_odo_id, v_slot_inicio, v_slot_fin, 'PRESENCIAL', 'LIBRE');
                    v_slots_count := v_slots_count + 1;
                EXCEPTION WHEN DUP_VAL_ON_INDEX THEN NULL;
                END;
            END LOOP;
        END IF;
    END LOOP;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Semillas de oferta cargadas exitosamente.');
    DBMS_OUTPUT.PUT_LINE('Slots generados en este lote: ' || v_slots_count);
END;
/
