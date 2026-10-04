# MediTriaje 2.0 — Modelo Relacional y Normalización

> Contrato formal de base de datos para Oracle Autonomous Transaction Processing (ATP) / Oracle 21c+.
> Basado en `docs/database/MER.md`, `docs/DECISIONES.md` (ADR-003, ADR-005, ADR-006, ADR-008, ADR-009, ADR-011, ADR-012, ADR-013) y `docs/requirements/REGLAS_NEGOCIO.md`.

---

## 1. Convenciones Globales del Esquema

1. **Identificadores (ADR-003):**
   - **Clave Primaria Interna:** `ID NUMBER GENERATED ALWAYS AS IDENTITY`, optimizada para joins e índices B-Tree de alta velocidad.
   - **Clave Pública:** `PUBLIC_ID VARCHAR2(36 CHAR) NOT NULL UNIQUE`, UUID v4 canónico expuesto en URLs y payloads JSON de la API para prevenir enumeración e IDOR.
2. **Semántica de Caracteres (`CHAR`):**
   - Todas las columnas de texto se declaran explícitamente con semántica de caracteres: `VARCHAR2(n CHAR)` o `CLOB`. Esto garantiza almacenamiento íntegro de caracteres UTF-8 (tildes, eñes, símbolos) sin desbordes por conteo de bytes.
3. **Convención de Nombres de Restricciones:**
   - Claves primarias: `PK_<TABLA>`
   - Claves foráneas: `FK_<TABLA_ORIGEN>_<TABLA_DESTINO>`
   - Restricciones de unicidad: `UQ_<TABLA>_<COLUMNA(S)>`
   - Restricciones de chequeo: `CK_<TABLA>_<COLUMNA>`
   - Índices secundarios: `IX_<TABLA>_<COLUMNA(S)>`
4. **Tipos de Datos, Temporalidad y Pool (ADR-005):**
   - Fechas y horas con zona horaria: `TIMESTAMP WITH TIME ZONE`.
   - **Ajuste de Sesión en Pool de Conexiones:** El pool HikariCP de `MEDITRIAJE_APP` debe configurar obligatoriamente:
     ```properties
     connectionInitSql = "BEGIN EXECUTE IMMEDIATE 'ALTER SESSION SET TIME_ZONE = ''America/Bogota'''; EXECUTE IMMEDIATE 'ALTER SESSION SET CURRENT_SCHEMA = MEDITRIAJE_OWNER'; END;"
     ```
     asegurando que cada conexión física opere bajo la zona horaria del proyecto (`America/Bogota`) y resuelva objetos sobre `MEDITRIAJE_OWNER` sin calificar el esquema en el código.
   - Fechas sin componente horario: `DATE` (fechas de nacimiento).
   - Booleanos lógicos: `NUMBER(1)` con constraint `CHECK (col IN (0, 1))`.
5. **Segregación de Usuarios de Base de Datos y Privilegios (ADR-012):**
   - **`MEDITRIAJE_OWNER`:** Usuario propietario del esquema utilizado exclusivamente por Flyway para migraciones y operaciones DDL (`CREATE`, `ALTER`, `DROP`, índices, triggers, secuencias). No tiene acceso desde la aplicación web en runtime.
   - **`MEDITRIAJE_APP`:** Usuario de mínimos privilegios utilizado por la aplicación Spring Boot en runtime (HikariCP).
     - **GRANTs mínimos por migración:** Cada script Flyway (`database/migrations/V###__*.sql`), ejecutado por `MEDITRIAJE_OWNER`, concluye con los `GRANT` indispensables para `MEDITRIAJE_APP`.
     - Privilegios concedidos: `SELECT`, `INSERT`, `UPDATE` estrictamente sobre las tablas necesarias.
     - **Prohibición de `DELETE`:** Sin permisos `DELETE` sobre tablas clínicas y asistenciales (`ATENCION`, `SIGNO_VITAL`, `ATENCION_ENMIENDA`, `RECETA`, `RECETA_DETALLE`, `CONSENTIMIENTO`, `AUDITORIA`).
     - **Prohibición de `UPDATE` y `DELETE`:** Sin permisos `UPDATE` ni `DELETE` sobre `AUDITORIA` y `ATENCION_ENMIENDA` (tablas insert-only / append-only).
6. **Política de Integridad Referencial (`ON DELETE`):**
   - **Oracle NO soporta la sintaxis `ON DELETE RESTRICT`**. En consecuencia, en todas las claves foráneas donde se requiera proteger la integridad referencial se **omite** la cláusula `ON DELETE`. En Oracle, la omisión equivale al comportamiento estándar `NO ACTION` (el motor rechaza cualquier borrado de fila padre con filas dependientes activas mediante el error `ORA-02292`).
   - **Excepciones con `ON DELETE CASCADE`:** Únicamente se aplica `ON DELETE CASCADE` en las tablas técnicas secundarias dependientes del ciclo de vida de `USUARIO` (`USUARIO_ROL` y `REFRESH_TOKEN`). Ninguna tabla asistencial o clínica permite borrado en cascada.

---

## 2. Definición Detallada Tabla por Tabla

### 2.1 Módulo: Seguridad, Identidad y Auditoría

#### 1. `USUARIO`
Cuenta de autenticación global en el sistema.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `EMAIL`: `VARCHAR2(100 CHAR)` | `NOT NULL`
  * `PASSWORD_HASH`: `VARCHAR2(255 CHAR)` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20 CHAR)` | `DEFAULT 'ACTIVO' NOT NULL`
  * `INTENTOS_FALLIDOS`: `NUMBER DEFAULT 0` | `NOT NULL`
  * `BLOQUEADO_HASTA`: `TIMESTAMP WITH TIME ZONE` | `NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
  * `UPDATED_AT`: `TIMESTAMP WITH TIME ZONE` | `NULL`
* **Constraints:**
  * `PK_USUARIO`: `PRIMARY KEY (ID)`
  * `UQ_USUARIO_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_USUARIO_EMAIL`: `UNIQUE (EMAIL)`
  * `CK_USUARIO_EMAIL_LOWER`: `CHECK (EMAIL = LOWER(EMAIL))` (garantiza minúsculas sin necesidad de índice funcional duplicado)
  * `CK_USUARIO_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO', 'BLOQUEADO'))`

#### 2. `ROL`
Catálogo de roles autorizados.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `NOMBRE`: `VARCHAR2(30 CHAR)` | `NOT NULL`
  * `DESCRIPCION`: `VARCHAR2(150 CHAR)` | `NULL`
* **Constraints:**
  * `PK_ROL`: `PRIMARY KEY (ID)`
  * `UQ_ROL_NOMBRE`: `UNIQUE (NOMBRE)`
  * `CK_ROL_NOMBRE`: `CHECK (NOMBRE IN ('ROLE_PACIENTE', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR'))`

#### 3. `USUARIO_ROL`
Asignación N:M de roles a usuarios.
* **Columnas:**
  * `USUARIO_ID`: `NUMBER` | `NOT NULL`
  * `ROL_ID`: `NUMBER` | `NOT NULL`
* **Constraints:**
  * `PK_USUARIO_ROL`: `PRIMARY KEY (USUARIO_ID, ROL_ID)`
  * `FK_USUARIO_ROL_USUARIO`: `FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID) ON DELETE CASCADE`
  * `FK_USUARIO_ROL_ROL`: `FOREIGN KEY (ROL_ID) REFERENCES ROL(ID)`
* **Índices:**
  * `IX_USUARIO_ROL_ROL`: `ON (ROL_ID)`

#### 4. `REFRESH_TOKEN`
Tokens de refresco rotativos de 7 días (ADR-002).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `USUARIO_ID`: `NUMBER` | `NOT NULL`
  * `TOKEN_HASH`: `VARCHAR2(255 CHAR)` | `NOT NULL`
  * `EXPIRACION`: `TIMESTAMP WITH TIME ZONE` | `NOT NULL`
  * `REVOCADO`: `NUMBER(1) DEFAULT 0` | `NOT NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_REFRESH_TOKEN`: `PRIMARY KEY (ID)`
  * `UQ_REFRESH_TOKEN_HASH`: `UNIQUE (TOKEN_HASH)`
  * `FK_REFRESH_TOKEN_USUARIO`: `FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID) ON DELETE CASCADE`
  * `CK_REFRESH_TOKEN_REVOCADO`: `CHECK (REVOCADO IN (0, 1))`
* **Índices:**
  * `IX_REFRESH_TOKEN_USER_EXP`: `ON (USUARIO_ID, EXPIRACION, REVOCADO)`

#### 5. `CONSENTIMIENTO`
Registro inmutable de consentimiento con soporte de revocación (Ley 1581 / ADR-013).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `USUARIO_ID`: `NUMBER` | `NOT NULL`
  * `VERSION_TEXTO`: `VARCHAR2(20 CHAR)` | `NOT NULL`
  * `ACEPTADO`: `NUMBER(1)` | `NOT NULL`
  * `FECHA_ACEPTACION`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
  * `IP_ORIGEN`: `VARCHAR2(45 CHAR)` | `NOT NULL`
  * `REVOCADO`: `NUMBER(1) DEFAULT 0` | `NOT NULL`
  * `FECHA_REVOCACION`: `TIMESTAMP WITH TIME ZONE` | `NULL`
* **Constraints:**
  * `PK_CONSENTIMIENTO`: `PRIMARY KEY (ID)`
  * `FK_CONSENTIMIENTO_USUARIO`: `FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID)`
  * `CK_CONSENTIMIENTO_ACEPTADO`: `CHECK (ACEPTADO = 1)`
  * `CK_CONSENTIMIENTO_REVOCADO`: `CHECK (REVOCADO IN (0, 1))`
  * `CK_CONSENTIMIENTO_FECHA_REV`: `CHECK ((REVOCADO = 0 AND FECHA_REVOCACION IS NULL) OR (REVOCADO = 1 AND FECHA_REVOCACION IS NOT NULL))`
* **Inmutabilidad:**
  * Trigger `TR_CONSENTIMIENTO_INMUTABILIDAD`: Bloquea sentencias `DELETE`. En `UPDATE`, solo permite modificar `REVOCADO` de 0 a 1 y fijar `FECHA_REVOCACION`. Cualquier otro cambio de columna es rechazado.

#### 6. `AUDITORIA`
Bitácora centralizada insert-only sin datos clínicos ni secretos (ADR-011).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `USUARIO_ID`: `NUMBER` | `NULL`
  * `ACCION`: `VARCHAR2(50 CHAR)` | `NOT NULL`
  * `TIPO_RECURSO`: `VARCHAR2(40 CHAR)` | `NOT NULL`
  * `RECURSO_PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NULL`
  * `RESULTADO`: `VARCHAR2(20 CHAR)` | `NOT NULL`
  * `IP_ORIGEN`: `VARCHAR2(45 CHAR)` | `NOT NULL`
  * `FECHA_HORA`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_AUDITORIA`: `PRIMARY KEY (ID)`
  * `CK_AUDITORIA_RESULTADO`: `CHECK (RESULTADO IN ('EXITO', 'FALLO', 'BLOQUEADO'))`
* **Inmutabilidad y Permisos:**
  * Trigger `TR_AUDITORIA_INMUTABILIDAD`: Bloquea incondicionalmente sentencias `UPDATE` y `DELETE`.
  * El usuario `MEDITRIAJE_APP` no tiene privilegios de `UPDATE` ni `DELETE` sobre `AUDITORIA`.
* **Índices:**
  * `IX_AUDITORIA_FECHA`: `ON (FECHA_HORA DESC)`
  * `IX_AUDITORIA_USUARIO`: `ON (USUARIO_ID, FECHA_HORA DESC)`

---

### 2.2 Módulo: Estructura Asistencial y Catálogos

#### 7. `INSTITUCION`
Entidad prestadora de salud.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `NIT`: `VARCHAR2(20 CHAR)` | `NOT NULL`
  * `RAZON_SOCIAL`: `VARCHAR2(120 CHAR)` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20 CHAR)` | `DEFAULT 'ACTIVO' NOT NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_INSTITUCION`: `PRIMARY KEY (ID)`
  * `UQ_INSTITUCION_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_INSTITUCION_NIT`: `UNIQUE (NIT)`
  * `CK_INSTITUCION_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`

#### 8. `SEDE`
Sede física de atención.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `INSTITUCION_ID`: `NUMBER` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `NOMBRE`: `VARCHAR2(80 CHAR)` | `NOT NULL`
  * `DIRECCION`: `VARCHAR2(120 CHAR)` | `NOT NULL`
  * `CIUDAD`: `VARCHAR2(60 CHAR)` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20 CHAR)` | `DEFAULT 'ACTIVO' NOT NULL`
* **Constraints:**
  * `PK_SEDE`: `PRIMARY KEY (ID)`
  * `UQ_SEDE_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `FK_SEDE_INSTITUCION`: `FOREIGN KEY (INSTITUCION_ID) REFERENCES INSTITUCION(ID)`
  * `CK_SEDE_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`
* **Índices:**
  * `IX_SEDE_INSTITUCION`: `ON (INSTITUCION_ID)`

#### 9. `ESPECIALIDAD`
Especialidad médica con duración parametrizada de turnos.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `NOMBRE`: `VARCHAR2(60 CHAR)` | `NOT NULL`
  * `DURACION_SLOT_MIN`: `NUMBER DEFAULT 20` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20 CHAR)` | `DEFAULT 'ACTIVO' NOT NULL`
* **Constraints:**
  * `PK_ESPECIALIDAD`: `PRIMARY KEY (ID)`
  * `UQ_ESPECIALIDAD_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_ESPECIALIDAD_NOMBRE`: `UNIQUE (NOMBRE)`
  * `CK_ESPECIALIDAD_DURACION`: `CHECK (DURACION_SLOT_MIN > 0)`
  * `CK_ESPECIALIDAD_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`

#### 10. `PROFESIONAL`
Datos del profesional de salud.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `USUARIO_ID`: `NUMBER` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `ESPECIALIDAD_ID`: `NUMBER` | `NOT NULL`
  * `REGISTRO_MEDICO`: `VARCHAR2(30 CHAR)` | `NOT NULL`
  * `NOMBRES`: `VARCHAR2(60 CHAR)` | `NOT NULL`
  * `APELLIDOS`: `VARCHAR2(60 CHAR)` | `NOT NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
  * `UPDATED_AT`: `TIMESTAMP WITH TIME ZONE` | `NULL`
* **Constraints:**
  * `PK_PROFESIONAL`: `PRIMARY KEY (ID)`
  * `UQ_PROFESIONAL_USUARIO`: `UNIQUE (USUARIO_ID)`
  * `UQ_PROFESIONAL_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_PROFESIONAL_REGISTRO`: `UNIQUE (REGISTRO_MEDICO)`
  * `FK_PROFESIONAL_USUARIO`: `FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID)`
  * `FK_PROFESIONAL_ESPECIALIDAD`: `FOREIGN KEY (ESPECIALIDAD_ID) REFERENCES ESPECIALIDAD(ID)`
* **Índices:**
  * `IX_PROFESIONAL_ESPECIALIDAD`: `ON (ESPECIALIDAD_ID)`

#### 11. `PACIENTE`
Datos del paciente.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `USUARIO_ID`: `NUMBER` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `TIPO_DOCUMENTO`: `VARCHAR2(5 CHAR)` | `NOT NULL`
  * `NUMERO_DOCUMENTO`: `VARCHAR2(20 CHAR)` | `NOT NULL`
  * `NOMBRES`: `VARCHAR2(60 CHAR)` | `NOT NULL`
  * `APELLIDOS`: `VARCHAR2(60 CHAR)` | `NOT NULL`
  * `FECHA_NACIMIENTO`: `DATE` | `NOT NULL`
  * `TELEFONO`: `VARCHAR2(20 CHAR)` | `NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
  * `UPDATED_AT`: `TIMESTAMP WITH TIME ZONE` | `NULL`
* **Constraints:**
  * `PK_PACIENTE`: `PRIMARY KEY (ID)`
  * `UQ_PACIENTE_USUARIO`: `UNIQUE (USUARIO_ID)`
  * `UQ_PACIENTE_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_PACIENTE_DOC`: `UNIQUE (TIPO_DOCUMENTO, NUMERO_DOCUMENTO)`
  * `FK_PACIENTE_USUARIO`: `FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID)`
  * `CK_PACIENTE_TIPO_DOC`: `CHECK (TIPO_DOCUMENTO IN ('CC', 'TI', 'RC', 'CE', 'PA'))`

---

### 2.3 Módulo: Agenda, Disponibilidad y Citas Concurrencia

#### 12. `DISPONIBILIDAD_SLOT`
Turnos pregenerados ofertados por la administración.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `PROFESIONAL_ID`: `NUMBER` | `NOT NULL`
  * `SEDE_ID`: `NUMBER` | `NOT NULL`
  * `ESPECIALIDAD_ID`: `NUMBER` | `NOT NULL`
  * `FECHA_HORA_INICIO`: `TIMESTAMP WITH TIME ZONE` | `NOT NULL`
  * `FECHA_HORA_FIN`: `TIMESTAMP WITH TIME ZONE` | `NOT NULL`
  * `MODALIDAD`: `VARCHAR2(20 CHAR)` | `DEFAULT 'PRESENCIAL' NOT NULL`
  * `ESTADO`: `VARCHAR2(20 CHAR)` | `DEFAULT 'LIBRE' NOT NULL`
* **Constraints:**
  * `PK_DISPONIBILIDAD_SLOT`: `PRIMARY KEY (ID)`
  * `UQ_SLOT_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_SLOT_PROFESIONAL_INICIO`: `UNIQUE (PROFESIONAL_ID, FECHA_HORA_INICIO)`
  * `FK_SLOT_PROFESIONAL`: `FOREIGN KEY (PROFESIONAL_ID) REFERENCES PROFESIONAL(ID)`
  * `FK_SLOT_SEDE`: `FOREIGN KEY (SEDE_ID) REFERENCES SEDE(ID)`
  * `FK_SLOT_ESPECIALIDAD`: `FOREIGN KEY (ESPECIALIDAD_ID) REFERENCES ESPECIALIDAD(ID)`
  * `CK_SLOT_HORAS`: `CHECK (FECHA_HORA_FIN > FECHA_HORA_INICIO)`
  * `CK_SLOT_MODALIDAD`: `CHECK (MODALIDAD IN ('PRESENCIAL', 'TELEMEDICINA'))`
  * `CK_SLOT_ESTADO`: `CHECK (ESTADO IN ('LIBRE', 'OCUPADO', 'BLOQUEADO'))`
* **Control de Solapes Horarios:**
  * `UQ_SLOT_PROFESIONAL_INICIO` impide a nivel relacional que se creen dos turnos con idéntico instante de inicio para un mismo profesional.
  * El control de **solape general de intervalos** (ejemplo: turno 08:00–08:30 versus turno 08:15–08:45) se valida en la capa de servicio (`SlotGeneratorService`) mediante consulta transaccional de exclusión (`WHERE profesional_id = :p AND NOT (fecha_hora_fin <= :inicio OR fecha_hora_inicio >= :fin)`), dado que Oracle no posee índices de exclusión temporal nativos (tipo GiST).
* **Índices:**
  * `IX_SLOT_BUSQUEDA`: `ON (ESPECIALIDAD_ID, SEDE_ID, ESTADO, FECHA_HORA_INICIO)`

#### 13. `CITA`
Reserva de turno con protección concurrente a nivel de Oracle y FK compuesta con Triaje.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `SLOT_ID`: `NUMBER` | `NOT NULL`
  * `PACIENTE_ID`: `NUMBER` | `NOT NULL`
  * `TRIAJE_ID`: `NUMBER` | `NULL`
  * `CITA_ORIGEN_ID`: `NUMBER` | `NULL`
  * `ESTADO`: `VARCHAR2(20 CHAR)` | `DEFAULT 'PROGRAMADA' NOT NULL`
  * `MOTIVO_CANCELACION`: `VARCHAR2(255 CHAR)` | `NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
  * `UPDATED_AT`: `TIMESTAMP WITH TIME ZONE` | `NULL`
* **Constraints:**
  * `PK_CITA`: `PRIMARY KEY (ID)`
  * `UQ_CITA_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `FK_CITA_SLOT`: `FOREIGN KEY (SLOT_ID) REFERENCES DISPONIBILIDAD_SLOT(ID)`
  * `FK_CITA_PACIENTE`: `FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID)`
  * `FK_CITA_TRIAJE_PACIENTE`: `FOREIGN KEY (TRIAJE_ID, PACIENTE_ID) REFERENCES TRIAJE(ID, PACIENTE_ID)` (Clave foránea compuesta que reemplaza definitivamente a la FK simple sobre `TRIAJE_ID`; garantiza a nivel relacional en BD que el triaje pertenezca estrictamente al mismo paciente de la cita)
  * `FK_CITA_ORIGEN`: `FOREIGN KEY (CITA_ORIGEN_ID) REFERENCES CITA(ID)`
  * `CK_CITA_ESTADO`: `CHECK (ESTADO IN ('PROGRAMADA', 'CONFIRMADA', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO', 'REPROGRAMADA'))`
* **Índice Funcional Único de Concurrencia (ADR-006):**
  * `UQ_CITA_SLOT_ACTIVA`: `CREATE UNIQUE INDEX UQ_CITA_SLOT_ACTIVA ON CITA (CASE WHEN ESTADO IN ('PROGRAMADA', 'CONFIRMADA') THEN SLOT_ID END)`
* **Índices Secundarios:**
  * `IX_CITA_PACIENTE`: `ON (PACIENTE_ID, ESTADO, CREATED_AT DESC)`
  * `IX_CITA_SLOT`: `ON (SLOT_ID)`
  * `IX_CITA_TRIAJE_PACIENTE`: `ON (TRIAJE_ID, PACIENTE_ID)` (Índice compuesto que cubre ambas columnas para respaldar de forma óptima la FK compuesta; reemplaza a cualquier índice simple sobre `TRIAJE_ID`)

---

### 2.4 Módulo: Triaje Clínico de Prototipo

#### 14. `SINTOMA`
Catálogo de síntomas observables.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `CODIGO`: `VARCHAR2(30 CHAR)` | `NOT NULL`
  * `NOMBRE`: `VARCHAR2(100 CHAR)` | `NOT NULL`
  * `CATEGORIA`: `VARCHAR2(50 CHAR)` | `NOT NULL`
  * `ES_ALARMA`: `NUMBER(1) DEFAULT 0` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20 CHAR)` | `DEFAULT 'ACTIVO' NOT NULL`
* **Constraints:**
  * `PK_SINTOMA`: `PRIMARY KEY (ID)`
  * `UQ_SINTOMA_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_SINTOMA_CODIGO`: `UNIQUE (CODIGO)`
  * `CK_SINTOMA_ALARMA`: `CHECK (ES_ALARMA IN (0, 1))`
  * `CK_SINTOMA_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`
* **Regla de Alarma Incondicional (ADR-009):**
  * `ES_ALARMA` reside **exclusivamente en `SINTOMA`**. Si un síntoma tiene `ES_ALARMA = 1`, se considera alarma incondicional y activa de inmediato el corte de emergencia al 123 / urgencias, sin importar su intensidad o duración.

#### 15. `REGLA_TRIAJE`
Reglas deterministas versionadas con rangos numéricos estructurados (ADR-009).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `VERSION`: `VARCHAR2(20 CHAR)` | `NOT NULL`
  * `SINTOMA_ID`: `NUMBER` | `NOT NULL`
  * `DURACION_MIN_HORAS`: `NUMBER DEFAULT 0` | `NOT NULL`
  * `DURACION_MAX_HORAS`: `NUMBER` | `NULL`
  * `INTENSIDAD_MIN`: `NUMBER(2) DEFAULT 0` | `NOT NULL`
  * `INTENSIDAD_MAX`: `NUMBER(2) DEFAULT 10` | `NOT NULL`
  * `NIVEL_PRIORIDAD`: `VARCHAR2(5 CHAR)` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20 CHAR)` | `DEFAULT 'ACTIVO' NOT NULL`
* **Constraints:**
  * `PK_REGLA_TRIAJE`: `PRIMARY KEY (ID)`
  * `FK_REGLA_TRIAJE_SINTOMA`: `FOREIGN KEY (SINTOMA_ID) REFERENCES SINTOMA(ID)`
  * `CK_REGLA_INTENSIDAD`: `CHECK (INTENSIDAD_MIN >= 0 AND INTENSIDAD_MAX <= 10 AND INTENSIDAD_MIN <= INTENSIDAD_MAX)`
  * `CK_REGLA_DURACION`: `CHECK (DURACION_MIN_HORAS >= 0 AND (DURACION_MAX_HORAS IS NULL OR DURACION_MAX_HORAS >= DURACION_MIN_HORAS))`
  * `CK_REGLA_NIVEL`: `CHECK (NIVEL_PRIORIDAD IN ('I', 'II', 'III', 'IV', 'V'))`
  * `CK_REGLA_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`
* **Nivel por Defecto Conservador:**
  * Si un paciente reporta síntomas que no coinciden con ninguna regla en `REGLA_TRIAJE`, el motor de triaje asigna por defecto **Nivel III (Urgencia menor / Prioritaria)**. **Bajo ninguna circunstancia se asigna Nivel V (no urgente) a síntomas sin tipificación.**
* **Índices:**
  * `IX_REGLA_TRIAJE_VERSION`: `ON (VERSION, ESTADO, SINTOMA_ID)`

#### 16. `TRIAJE`
Evaluación completada por un paciente.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `PACIENTE_ID`: `NUMBER` | `NOT NULL`
  * `VERSION_REGLAS`: `VARCHAR2(20 CHAR)` | `NOT NULL`
  * `NIVEL_PRIORIDAD`: `VARCHAR2(5 CHAR)` | `NOT NULL`
  * `RUTA_SUGERIDA`: `VARCHAR2(50 CHAR)` | `NOT NULL`
  * `ES_EMERGENCIA`: `NUMBER(1) DEFAULT 0` | `NOT NULL`
  * `OBSERVACIONES`: `VARCHAR2(500 CHAR)` | `NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_TRIAJE`: `PRIMARY KEY (ID)`
  * `UQ_TRIAJE_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_TRIAJE_PACIENTE`: `UNIQUE (ID, PACIENTE_ID)` (Habilita la FK compuesta desde `CITA`)
  * `FK_TRIAJE_PACIENTE`: `FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID)`
  * `CK_TRIAJE_NIVEL`: `CHECK (NIVEL_PRIORIDAD IN ('I', 'II', 'III', 'IV', 'V'))`
  * `CK_TRIAJE_RUTA`: `CHECK (RUTA_SUGERIDA IN ('URGENCIAS', 'ATENCION_PRIORITARIA', 'CITA_PRESENCIAL', 'CITA_TELEMEDICINA', 'CONSULTA_PROGRAMADA'))`
  * `CK_TRIAJE_EMERGENCIA`: `CHECK (ES_EMERGENCIA IN (0, 1))`
* **Definición de Emergencia:**
  * `ES_EMERGENCIA = 1` si al menos un síntoma seleccionado tiene `SINTOMA.ES_ALARMA = 1` O si la regla clínica evaluada determina `NIVEL_PRIORIDAD = 'I'`.
* **Índices:**
  * `IX_TRIAJE_PACIENTE`: `ON (PACIENTE_ID, CREATED_AT DESC)`

#### 17. `TRIAJE_SINTOMA`
Síntomas declarados en el evento de triaje.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `TRIAJE_ID`: `NUMBER` | `NOT NULL`
  * `SINTOMA_ID`: `NUMBER` | `NOT NULL`
  * `DURACION_HORAS`: `NUMBER` | `NOT NULL`
  * `INTENSIDAD`: `NUMBER` | `NOT NULL`
* **Constraints:**
  * `PK_TRIAJE_SINTOMA`: `PRIMARY KEY (ID)`
  * `UQ_TRIAJE_SINTOMA_UNICO`: `UNIQUE (TRIAJE_ID, SINTOMA_ID)`
  * `FK_TS_TRIAJE`: `FOREIGN KEY (TRIAJE_ID) REFERENCES TRIAJE(ID)`
  * `FK_TS_SINTOMA`: `FOREIGN KEY (SINTOMA_ID) REFERENCES SINTOMA(ID)`
  * `CK_TS_INTENSIDAD`: `CHECK (INTENSIDAD BETWEEN 0 AND 10)`
  * `CK_TS_DURACION`: `CHECK (DURACION_HORAS >= 0)`

---

### 2.5 Módulo: Atención Clínica Inmutable

#### 18. `DIAGNOSTICO_CIE10`
Catálogo reducido de patologías.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `CODIGO`: `VARCHAR2(10 CHAR)` | `NOT NULL`
  * `DESCRIPCION`: `VARCHAR2(255 CHAR)` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20 CHAR)` | `DEFAULT 'ACTIVO' NOT NULL`
* **Constraints:**
  * `PK_DIAGNOSTICO_CIE10`: `PRIMARY KEY (ID)`
  * `UQ_CIE10_CODIGO`: `UNIQUE (CODIGO)`
  * `CK_CIE10_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`

#### 19. `ATENCION`
Acto médico profesional inmutable (ADR-008).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `CITA_ID`: `NUMBER` | `NOT NULL`
  * `PACIENTE_ID`: `NUMBER` | `NOT NULL`
  * `PROFESIONAL_ID`: `NUMBER` | `NOT NULL`
  * `DIAGNOSTICO_PRINCIPAL_ID`: `NUMBER` | `NULL`
  * `MOTIVO_CONSULTA`: `VARCHAR2(500 CHAR)` | `NULL`
  * `EVOLUCION`: `VARCHAR2(4000 CHAR)` | `NULL`
  * `INDICACIONES`: `VARCHAR2(1000 CHAR)` | `NULL`
  * `ESTADO`: `VARCHAR2(20 CHAR)` | `DEFAULT 'ABIERTA' NOT NULL`
  * `FECHA_CIERRE`: `TIMESTAMP WITH TIME ZONE` | `NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_ATENCION`: `PRIMARY KEY (ID)`
  * `UQ_ATENCION_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_ATENCION_CITA`: `UNIQUE (CITA_ID)` (relación estricta 1 a 1 con Cita)
  * `FK_ATENCION_CITA`: `FOREIGN KEY (CITA_ID) REFERENCES CITA(ID)`
  * `FK_ATENCION_PACIENTE`: `FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID)`
  * `FK_ATENCION_PROFESIONAL`: `FOREIGN KEY (PROFESIONAL_ID) REFERENCES PROFESIONAL(ID)`
  * `FK_ATENCION_CIE10`: `FOREIGN KEY (DIAGNOSTICO_PRINCIPAL_ID) REFERENCES DIAGNOSTICO_CIE10(ID)`
  * `CK_ATENCION_ESTADO`: `CHECK (ESTADO IN ('ABIERTA', 'CERRADA'))`
  * `CK_ATENCION_CAMPOS_CIERRE`: `CHECK (ESTADO = 'ABIERTA' OR (FECHA_CIERRE IS NOT NULL AND MOTIVO_CONSULTA IS NOT NULL AND EVOLUCION IS NOT NULL AND DIAGNOSTICO_PRINCIPAL_ID IS NOT NULL AND INDICACIONES IS NOT NULL))`
* **Consideración de Longitud de `EVOLUCION`:**
  * El tipo es `VARCHAR2(4000 CHAR)`. En Oracle con `MAX_STRING_SIZE = STANDARD` (por defecto en muchas instancias), el límite absoluto de la fila son 4000 **bytes**. Caracteres UTF-8 multibyte (ej. tildes que ocupan 2 bytes) consumen dicho espacio.
  * Por diseño seguro, el DTO en el backend valida `@Size(max = 4000)` a nivel de caracteres y comprueba el límite de bytes antes de persistir, evitando desbordes `ORA-01461`.
* **Garantía de Inmutabilidad mediante Trigger (ADR-008):**
  * Trigger `TR_ATENCION_INMUTABILIDAD`:
    ```sql
    CREATE OR REPLACE TRIGGER TR_ATENCION_INMUTABILIDAD
    BEFORE UPDATE OR DELETE ON ATENCION
    FOR EACH ROW
    BEGIN
      IF DELETING THEN
        RAISE_APPLICATION_ERROR(-20001, 'Prohibido eliminar registros de atenciones clinicas.');
      END IF;
      IF UPDATING THEN
        IF :OLD.ESTADO = 'CERRADA' THEN
          RAISE_APPLICATION_ERROR(-20002, 'La atencion se encuentra CERRADA y es inmutable.');
        END IF;
      END IF;
    END;
    ```
    *Efecto:* Permite modificaciones mientras está en `ABIERTA`, admite la transición `ABIERTA -> CERRADA` (registrando `FECHA_CIERRE`), y a partir de ese instante bloquea cualquier modificación o borrado posterior.
* **Índices Secundarios:**
  * `IX_ATENCION_PACIENTE`: `ON (PACIENTE_ID, FECHA_CIERRE DESC)`
  * `IX_ATENCION_PROFESIONAL`: `ON (PROFESIONAL_ID, FECHA_CIERRE DESC)`

#### 20. `SIGNO_VITAL`
Parámetros fisiológicos medidos durante la atención médica.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `ATENCION_ID`: `NUMBER` | `NOT NULL`
  * `PRESION_SISTOLICA`: `NUMBER(3)` | `NULL`
  * `PRESION_DIASTOLICA`: `NUMBER(3)` | `NULL`
  * `FRECUENCIA_CARDIACA`: `NUMBER(3)` | `NULL`
  * `FRECUENCIA_RESPIRATORIA`: `NUMBER(2)` | `NULL`
  * `TEMPERATURA`: `NUMBER(4,1)` | `NULL`
  * `SATURACION_OXIGENO`: `NUMBER(3)` | `NULL`
  * `PESO_KG`: `NUMBER(5,2)` | `NULL`
  * `TALLA_CM`: `NUMBER(4,1)` | `NULL`
* **Constraints:**
  * `PK_SIGNO_VITAL`: `PRIMARY KEY (ID)`
  * `FK_SIGNO_VITAL_ATENCION`: `FOREIGN KEY (ATENCION_ID) REFERENCES ATENCION(ID)`
  * `CK_SV_SISTOLICA`: `CHECK (PRESION_SISTOLICA IS NULL OR PRESION_SISTOLICA BETWEEN 40 AND 300)`
  * `CK_SV_DIASTOLICA`: `CHECK (PRESION_DIASTOLICA IS NULL OR PRESION_DIASTOLICA BETWEEN 20 AND 200)`
  * `CK_SV_PRESION_REL`: `CHECK (PRESION_SISTOLICA IS NULL OR PRESION_DIASTOLICA IS NULL OR PRESION_SISTOLICA > PRESION_DIASTOLICA)`
  * `CK_SV_CARDIACA`: `CHECK (FRECUENCIA_CARDIACA IS NULL OR FRECUENCIA_CARDIACA BETWEEN 30 AND 250)`
  * `CK_SV_RESPIRATORIA`: `CHECK (FRECUENCIA_RESPIRATORIA IS NULL OR FRECUENCIA_RESPIRATORIA BETWEEN 5 AND 60)`
  * `CK_SV_TEMPERATURA`: `CHECK (TEMPERATURA IS NULL OR TEMPERATURA BETWEEN 30.0 AND 45.0)`
  * `CK_SV_SATURACION`: `CHECK (SATURACION_OXIGENO IS NULL OR SATURACION_OXIGENO BETWEEN 0 AND 100)`
  * `CK_SV_PESO`: `CHECK (PESO_KG IS NULL OR PESO_KG BETWEEN 0.5 AND 500.0)`
  * `CK_SV_TALLA`: `CHECK (TALLA_CM IS NULL OR TALLA_CM BETWEEN 20.0 AND 260.0)`
* **Inmutabilidad Reforzada:**
  * Trigger `TR_SIGNO_VITAL_INMUTABILIDAD`: Prohíbe **`INSERT`, `UPDATE` y `DELETE`** si la atención vinculada (`ATENCION_ID`) ya tiene `ESTADO = 'CERRADA'`.
* **Índices:**
  * `IX_SIGNO_VITAL_ATENCION`: `ON (ATENCION_ID)`

#### 21. `ATENCION_ENMIENDA`
Aclaración append-only a una atención cerrada (ADR-008).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `ATENCION_ID`: `NUMBER` | `NOT NULL`
  * `PROFESIONAL_ID`: `NUMBER` | `NOT NULL`
  * `MOTIVO`: `VARCHAR2(255 CHAR)` | `NOT NULL`
  * `CONTENIDO`: `VARCHAR2(2000 CHAR)` | `NOT NULL`
  * `FECHA_ENMIENDA`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_ATENCION_ENMIENDA`: `PRIMARY KEY (ID)`
  * `FK_ENMIENDA_ATENCION`: `FOREIGN KEY (ATENCION_ID) REFERENCES ATENCION(ID)`
  * `FK_ENMIENDA_PROFESIONAL`: `FOREIGN KEY (PROFESIONAL_ID) REFERENCES PROFESIONAL(ID)`
* **Inmutabilidad y Permisos:**
  * Trigger `TR_ENMIENDA_INMUTABILIDAD`: Bloquea de forma categórica cualquier `UPDATE` o `DELETE`.
  * La aplicación solo tiene permiso `GRANT INSERT, SELECT ON ATENCION_ENMIENDA`.
* **Índices:**
  * `IX_ENMIENDA_ATENCION`: `ON (ATENCION_ID, FECHA_ENMIENDA ASC)`

#### 22. `ALERGIA`
Registro de hipersensibilidades del paciente.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PACIENTE_ID`: `NUMBER` | `NOT NULL`
  * `SUSTANCIA`: `VARCHAR2(100 CHAR)` | `NOT NULL`
  * `REACCION`: `VARCHAR2(200 CHAR)` | `NULL`
  * `SEVERIDAD`: `VARCHAR2(20 CHAR)` | `NOT NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_ALERGIA`: `PRIMARY KEY (ID)`
  * `FK_ALERGIA_PACIENTE`: `FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID)`
  * `CK_ALERGIA_SEVERIDAD`: `CHECK (SEVERIDAD IN ('LEVE', 'MODERADA', 'GRAVE'))`
* **Índices:**
  * `IX_ALERGIA_PACIENTE`: `ON (PACIENTE_ID)`

---

### 2.6 Módulo: Farmacia y Recetas Médicas

#### 23. `MEDICAMENTO`
Catálogo maestro de fármacos.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `CODIGO`: `VARCHAR2(30 CHAR)` | `NOT NULL`
  * `NOMBRE_COMERCIAL`: `VARCHAR2(100 CHAR)` | `NOT NULL`
  * `PRINCIPIO_ACTIVO`: `VARCHAR2(100 CHAR)` | `NOT NULL`
  * `PRESENTACION`: `VARCHAR2(100 CHAR)` | `NOT NULL`
  * `CONCENTRACION`: `VARCHAR2(50 CHAR)` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20 CHAR)` | `DEFAULT 'ACTIVO' NOT NULL`
* **Constraints:**
  * `PK_MEDICAMENTO`: `PRIMARY KEY (ID)`
  * `UQ_MEDICAMENTO_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_MEDICAMENTO_CODIGO`: `UNIQUE (CODIGO)`
  * `CK_MEDICAMENTO_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`

#### 24. `RECETA`
Cabecera de prescripción médica.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36 CHAR)` | `NOT NULL`
  * `ATENCION_ID`: `NUMBER` | `NOT NULL`
  * `PACIENTE_ID`: `NUMBER` | `NOT NULL`
  * `PROFESIONAL_ID`: `NUMBER` | `NOT NULL`
  * `VIGENCIA_DIAS`: `NUMBER DEFAULT 30` | `NOT NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_RECETA`: `PRIMARY KEY (ID)`
  * `UQ_RECETA_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `FK_RECETA_ATENCION`: `FOREIGN KEY (ATENCION_ID) REFERENCES ATENCION(ID)`
  * `FK_RECETA_PACIENTE`: `FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID)`
  * `FK_RECETA_PROFESIONAL`: `FOREIGN KEY (PROFESIONAL_ID) REFERENCES PROFESIONAL(ID)`
  * `CK_RECETA_VIGENCIA`: `CHECK (VIGENCIA_DIAS > 0)`
* **Regla de Emisión e Inmutabilidad:**
  * Una receta **puede ser emitida sobre una atención que ya se encuentra `CERRADA`** (o en el mismo flujo de cierre por el profesional responsable).
  * La inmutabilidad de `RECETA` comienza **inmediatamente al insertarse**: el trigger `TR_RECETA_INMUTABILIDAD` bloquea incondicionalmente cualquier `UPDATE` o `DELETE`.
* **Índices:**
  * `IX_RECETA_PACIENTE`: `ON (PACIENTE_ID, CREATED_AT DESC)`
  * `IX_RECETA_ATENCION`: `ON (ATENCION_ID)`

#### 25. `RECETA_DETALLE`
Ítem prescrito con preservación histórica (snapshot inmutable / HU-08).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `RECETA_ID`: `NUMBER` | `NOT NULL`
  * `MEDICAMENTO_ID`: `NUMBER` | `NOT NULL`
  * `SNAPSHOT_NOMBRE`: `VARCHAR2(100 CHAR)` | `NOT NULL`
  * `SNAPSHOT_PRINCIPIO_ACTIVO`: `VARCHAR2(100 CHAR)` | `NOT NULL`
  * `SNAPSHOT_PRESENTACION`: `VARCHAR2(100 CHAR)` | `NOT NULL`
  * `SNAPSHOT_CONCENTRACION`: `VARCHAR2(50 CHAR)` | `NOT NULL`
  * `DOSIS`: `VARCHAR2(100 CHAR)` | `NOT NULL`
  * `FRECUENCIA`: `VARCHAR2(100 CHAR)` | `NOT NULL`
  * `DURACION_DIAS`: `NUMBER` | `NOT NULL`
  * `CANTIDAD`: `NUMBER` | `NOT NULL`
  * `INDICACIONES`: `VARCHAR2(300 CHAR)` | `NULL`
* **Constraints:**
  * `PK_RECETA_DETALLE`: `PRIMARY KEY (ID)`
  * `FK_RD_RECETA`: `FOREIGN KEY (RECETA_ID) REFERENCES RECETA(ID)`
  * `FK_RD_MEDICAMENTO`: `FOREIGN KEY (MEDICAMENTO_ID) REFERENCES MEDICAMENTO(ID)`
  * `CK_RD_CANTIDAD`: `CHECK (CANTIDAD > 0)`
  * `CK_RD_DURACION`: `CHECK (DURACION_DIAS > 0)`
* **Inmutabilidad:**
  * Trigger `TR_RECETA_DETALLE_INMUTABILIDAD`: Bloquea `UPDATE` y `DELETE` desde el momento de inserción.
* **Índices:**
  * `IX_RECETA_DETALLE_RECETA`: `ON (RECETA_ID)`

---

## 3. Análisis de Normalización y Desnormalizaciones Controladas

El modelo sigue los principios de la **Tercera Forma Normal (3FN)** con un conjunto explícito y deliberado de **desnormalizaciones controladas por razones de seguridad, rendimiento e integridad médico-legal** (no se afirma BCNF estricta debido a estas dependencias intencionales):

### 3.1 Cumplimiento de Formas Normales
1. **Primera Forma Normal (1FN):** Todos los atributos son atómicos. No existen listas separadas por comas ni arrays en columnas; atributos repetitivos fueron descompuestos en tablas dedicadas (`TRIAJE_SINTOMA`, `SIGNO_VITAL`, `RECETA_DETALLE`). Toda tabla tiene clave primaria fija `ID`.
2. **Segunda Forma Normal (2FN):** En tablas con claves compuestas (`USUARIO_ROL`), no existen dependencias parciales.
3. **Tercera Forma Normal (3FN) y Desnormalizaciones Aceptadas:**
   El esquema minimiza dependencias transitivas no deseadas, pero introduce cuatro desnormalizaciones controladas arquitectónicamente:

### 3.2 Catálogo de Desnormalizaciones Controladas y Mecanismos de Consistencia

| Desnormalización | Tablas Involucradas | Justificación Arquitectónica | Mecanismo de Consistencia Obligatorio |
|---|---|---|---|
| **1. Paciente y Profesional en `ATENCION`** | `ATENCION(PACIENTE_ID, PROFESIONAL_ID)` redundante con `CITA(PACIENTE_ID, PROFESIONAL_ID)` | **Rendimiento y seguridad asistencial:** Permite consultas directas de historia clínica (`WHERE PACIENTE_ID = :p`) y agenda profesional sin obligar a joins costosos con `CITA` y `DISPONIBILIDAD_SLOT`. | **Capa Service:** `AtencionService` valida al crear la atención que `paciente_id` y `profesional_id` coincidan de forma idéntica con los de la `CITA`. |
| **2. Paciente y Profesional en `RECETA`** | `RECETA(PACIENTE_ID, PROFESIONAL_ID)` redundante con `ATENCION` | **Aislamiento y auditoría médica:** Facilita la consulta directa de recetas del paciente y la verificación de responsabilidad legal del médico emisor de forma desacoplada de la consulta. | **Transacción atómica:** `PrescriptionService` extrae y valida los identificadores directamente desde la entidad `ATENCION` en la misma transacción. |
| **3. Especialidad en `DISPONIBILIDAD_SLOT`** | `DISPONIBILIDAD_SLOT.ESPECIALIDAD_ID` redundante con `PROFESIONAL.ESPECIALIDAD_ID` | **Búsqueda eficiente de turnos y extensibilidad:** Optimiza el índice compuesto `IX_SLOT_BUSQUEDA` para el buscador de citas. Permite en el futuro que un profesional habilite slots de distintas subespecialidades. | **Validación administrativa:** `SlotGeneratorService` comprueba que el profesional posea la especialidad requerida antes de generar los slots. |
| **4. Snapshot farmacológico en `RECETA_DETALLE`** | `SNAPSHOT_NOMBRE`, `SNAPSHOT_PRINCIPIO_ACTIVO`, `SNAPSHOT_PRESENTACION`, `SNAPSHOT_CONCENTRACION` en `RECETA_DETALLE` | **Inmutabilidad médico-legal:** Si un fármaco es modificado o dado de baja en `MEDICAMENTO`, la receta médica histórica emitida hace meses o años debe preservar la denominación exacta prescrita. | **Copia atómica:** `PrescriptionService` copia los valores textuales de `MEDICAMENTO` en el `INSERT` del detalle dentro de la transacción de prescripción. |

---

## 4. Estado de Decisiones y Criterios Aprobados

Todas las decisiones y criterios técnicos de la Fase M0 han sido formalmente aprobados por Juan (2026-10-01):
* [x] **`ES_ALARMA` exclusivamente en `SINTOMA`:** Alarma incondicional. Eliminada de `REGLA_TRIAJE`. Emergencia = síntoma con alarma O Nivel I. Nivel por defecto conservador ante falta de coincidencia: Nivel III.
* [x] **`ATENCION.EVOLUCION`:** `VARCHAR2(4000 CHAR)`. Documentada la restricción de `MAX_STRING_SIZE = STANDARD` (4000 bytes) y validación `@Size(max = 4000)` en DTO.
* [x] **Segregación de usuarios de BD:** `MEDITRIAJE_OWNER` (DDL / Flyway) y `MEDITRIAJE_APP` (DML restringido, sin DELETE clínico ni UPDATE/DELETE en auditoría/enmiendas).
* [x] **Inmutabilidad de `SIGNO_VITAL`:** Trigger bloquea `INSERT`, `UPDATE` y `DELETE` si la atención está `CERRADA`.
* [x] **Emisión de `RECETA`:** Puede emitirse sobre atenciones cerradas; inmutabilidad estricta a partir del `INSERT`.
* [x] **Coherencia Cita-Triaje por BD:** Clave única `UQ_TRIAJE_PACIENTE` en `TRIAJE` y clave foránea compuesta `FK_CITA_TRIAJE_PACIENTE` en `CITA`.
* [x] **Ajuste de zona horaria:** `connectionInitSql = "ALTER SESSION SET TIME_ZONE = 'America/Bogota'"` configurado en HikariCP.
* [x] **Omisión de `ON DELETE RESTRICT`:** Uso de `NO ACTION` nativo y `CASCADE` exclusivo en `USUARIO_ROL` y `REFRESH_TOKEN`.
* [x] **3FN sin afirmaciones de BCNF:** Catálogo de 4 desnormalizaciones controladas y mecanismos de consistencia.
