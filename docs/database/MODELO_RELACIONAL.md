# MediTriaje 2.0 — Modelo Relacional y Normalización

> Contrato formal de base de datos para Oracle Autonomous Transaction Processing (ATP) / Oracle 21c+.
> Basado en `docs/database/MER.md`, `docs/DECISIONES.md` (ADR-003, ADR-005, ADR-006, ADR-008, ADR-011, ADR-013) y `docs/requirements/REGLAS_NEGOCIO.md`.

---

## 1. Convenciones Globales del Esquema

1. **Identificadores (ADR-003):**
   - **Clave Primaria Interna:** `ID NUMBER GENERATED ALWAYS AS IDENTITY`, optimizada para joins e índices B-Tree de alta velocidad.
   - **Clave Pública:** `PUBLIC_ID VARCHAR2(36) NOT NULL UNIQUE`, UUID v4 canónico expuesto en URLs y payloads JSON de la API para prevenir enumeración e IDOR.
2. **Convención de Nombres de Restricciones:**
   - Claves primarias: `PK_<TABLA>`
   - Claves foráneas: `FK_<TABLA_ORIGEN>_<TABLA_DESTINO>`
   - Restricciones de unicidad: `UQ_<TABLA>_<COLUMNA(S)>`
   - Restricciones de chequeo: `CK_<TABLA>_<COLUMNA>`
   - Índices secundarios: `IX_<TABLA>_<COLUMNA(S)>`
3. **Tipos de Datos y Temporalidad (ADR-005):**
   - Fechas y horas con zona horaria: `TIMESTAMP WITH TIME ZONE` (almacenadas y leídas en contexto `America/Bogota`).
   - Fechas sin componente horario: `DATE` (fechas de nacimiento).
   - Booleanos lógicos: `NUMBER(1)` con constraint `CHECK (col IN (0, 1))`.
4. **Política de Integridad Referencial (`ON DELETE`):**
   - **Tablas clínicas y asistenciales:** `ON DELETE RESTRICT` (default en Oracle; sin cascadas para impedir pérdida accidental de historia clínica).
   - **Tablas asociativas técnicas puras:** `ON DELETE CASCADE` únicamente en `USUARIO_ROL` y `REFRESH_TOKEN` dependientes del ciclo de vida de `USUARIO`.

---

## 2. Definición Detallada Tabla por Tabla

### 2.1 Módulo: Seguridad, Identidad y Auditoría

#### 1. `USUARIO`
Cuenta de autenticación global en el sistema.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `EMAIL`: `VARCHAR2(100)` | `NOT NULL`
  * `PASSWORD_HASH`: `VARCHAR2(255)` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20)` | `DEFAULT 'ACTIVO' NOT NULL`
  * `INTENTOS_FALLIDOS`: `NUMBER DEFAULT 0` | `NOT NULL`
  * `BLOQUEADO_HASTA`: `TIMESTAMP WITH TIME ZONE` | `NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
  * `UPDATED_AT`: `TIMESTAMP WITH TIME ZONE` | `NULL`
* **Constraints:**
  * `PK_USUARIO`: `PRIMARY KEY (ID)`
  * `UQ_USUARIO_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_USUARIO_EMAIL`: `UNIQUE (LOWER(EMAIL))`
  * `CK_USUARIO_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO', 'BLOQUEADO'))`
* **Índices:**
  * `IX_USUARIO_EMAIL_LOGIN`: `ON (LOWER(EMAIL))` (búsqueda rápida en autenticación).

#### 2. `ROL`
Catálogo de roles autorizados.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `NOMBRE`: `VARCHAR2(30)` | `NOT NULL`
  * `DESCRIPCION`: `VARCHAR2(150)` | `NULL`
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
  * `FK_USUARIO_ROL_ROL`: `FOREIGN KEY (ROL_ID) REFERENCES ROL(ID) ON DELETE RESTRICT`
* **Índices:**
  * `IX_USUARIO_ROL_ROL`: `ON (ROL_ID)` (soporta consultas de usuarios por rol).

#### 4. `REFRESH_TOKEN`
Almacenamiento seguro de tokens rotativos de 7 días (ADR-002).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `USUARIO_ID`: `NUMBER` | `NOT NULL`
  * `TOKEN_HASH`: `VARCHAR2(255)` | `NOT NULL`
  * `EXPIRACION`: `TIMESTAMP WITH TIME ZONE` | `NOT NULL`
  * `REVOCADO`: `NUMBER(1) DEFAULT 0` | `NOT NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_REFRESH_TOKEN`: `PRIMARY KEY (ID)`
  * `UQ_REFRESH_TOKEN_HASH`: `UNIQUE (TOKEN_HASH)`
  * `FK_REFRESH_TOKEN_USUARIO`: `FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID) ON DELETE CASCADE`
  * `CK_REFRESH_TOKEN_REVOCADO`: `CHECK (REVOCADO IN (0, 1))`
* **Índices:**
  * `IX_REFRESH_TOKEN_USER_EXP`: `ON (USUARIO_ID, EXPIRACION, REVOCADO)` (limpieza y verificación).

#### 5. `CONSENTIMIENTO`
Registro inmutable de consentimiento legal (Ley 1581 de 2012 / ADR-013).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `USUARIO_ID`: `NUMBER` | `NOT NULL`
  * `VERSION_TEXTO`: `VARCHAR2(20)` | `NOT NULL`
  * `ACEPTADO`: `NUMBER(1)` | `NOT NULL`
  * `FECHA_ACEPTACION`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
  * `IP_ORIGEN`: `VARCHAR2(45)` | `NOT NULL`
* **Constraints:**
  * `PK_CONSENTIMIENTO`: `PRIMARY KEY (ID)`
  * `FK_CONSENTIMIENTO_USUARIO`: `FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID) ON DELETE RESTRICT`
  * `CK_CONSENTIMIENTO_ACEPTADO`: `CHECK (ACEPTADO = 1)`

#### 6. `AUDITORIA`
Bitácora centralizada append-only sin datos clínicos ni secretos (ADR-011).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `USUARIO_ID`: `NUMBER` | `NULL`
  * `ACCION`: `VARCHAR2(50)` | `NOT NULL`
  * `TIPO_RECURSO`: `VARCHAR2(40)` | `NOT NULL`
  * `RECURSO_PUBLIC_ID`: `VARCHAR2(36)` | `NULL`
  * `RESULTADO`: `VARCHAR2(20)` | `NOT NULL`
  * `IP_ORIGEN`: `VARCHAR2(45)` | `NOT NULL`
  * `FECHA_HORA`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_AUDITORIA`: `PRIMARY KEY (ID)`
  * `CK_AUDITORIA_RESULTADO`: `CHECK (RESULTADO IN ('EXITO', 'FALLO', 'BLOQUEADO'))`
* **Índices:**
  * `IX_AUDITORIA_FECHA`: `ON (FECHA_HORA DESC)` (consultas operativas cronológicas).
  * `IX_AUDITORIA_USUARIO`: `ON (USUARIO_ID, FECHA_HORA DESC)` (trazabilidad por operador).

---

### 2.2 Módulo: Estructura Asistencial y Catálogos

#### 7. `INSTITUCION`
Entidad prestadora de salud.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `NIT`: `VARCHAR2(20)` | `NOT NULL`
  * `RAZON_SOCIAL`: `VARCHAR2(120)` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20)` | `DEFAULT 'ACTIVO' NOT NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_INSTITUCION`: `PRIMARY KEY (ID)`
  * `UQ_INSTITUCION_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_INSTITUCION_NIT`: `UNIQUE (NIT)`
  * `CK_INSTITUCION_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`

#### 8. `SEDE`
Sede física o centro de atención de una institución.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `INSTITUCION_ID`: `NUMBER` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `NOMBRE`: `VARCHAR2(80)` | `NOT NULL`
  * `DIRECCION`: `VARCHAR2(120)` | `NOT NULL`
  * `CIUDAD`: `VARCHAR2(60)` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20)` | `DEFAULT 'ACTIVO' NOT NULL`
* **Constraints:**
  * `PK_SEDE`: `PRIMARY KEY (ID)`
  * `UQ_SEDE_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `FK_SEDE_INSTITUCION`: `FOREIGN KEY (INSTITUCION_ID) REFERENCES INSTITUCION(ID) ON DELETE RESTRICT`
  * `CK_SEDE_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`
* **Índices:**
  * `IX_SEDE_INSTITUCION`: `ON (INSTITUCION_ID)`

#### 9. `ESPECIALIDAD`
Especialidad médica con duración parametrizada de turnos.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `NOMBRE`: `VARCHAR2(60)` | `NOT NULL`
  * `DURACION_SLOT_MIN`: `NUMBER DEFAULT 20` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20)` | `DEFAULT 'ACTIVO' NOT NULL`
* **Constraints:**
  * `PK_ESPECIALIDAD`: `PRIMARY KEY (ID)`
  * `UQ_ESPECIALIDAD_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_ESPECIALIDAD_NOMBRE`: `UNIQUE (NOMBRE)`
  * `CK_ESPECIALIDAD_DURACION`: `CHECK (DURACION_SLOT_MIN > 0)`
  * `CK_ESPECIALIDAD_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`

#### 10. `PROFESIONAL`
Datos del médico / profesional de salud.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `USUARIO_ID`: `NUMBER` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `ESPECIALIDAD_ID`: `NUMBER` | `NOT NULL`
  * `REGISTRO_MEDICO`: `VARCHAR2(30)` | `NOT NULL`
  * `NOMBRES`: `VARCHAR2(60)` | `NOT NULL`
  * `APELLIDOS`: `VARCHAR2(60)` | `NOT NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
  * `UPDATED_AT`: `TIMESTAMP WITH TIME ZONE` | `NULL`
* **Constraints:**
  * `PK_PROFESIONAL`: `PRIMARY KEY (ID)`
  * `UQ_PROFESIONAL_USUARIO`: `UNIQUE (USUARIO_ID)`
  * `UQ_PROFESIONAL_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_PROFESIONAL_REGISTRO`: `UNIQUE (REGISTRO_MEDICO)`
  * `FK_PROFESIONAL_USUARIO`: `FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID) ON DELETE RESTRICT`
  * `FK_PROFESIONAL_ESPECIALIDAD`: `FOREIGN KEY (ESPECIALIDAD_ID) REFERENCES ESPECIALIDAD(ID) ON DELETE RESTRICT`
* **Índices:**
  * `IX_PROFESIONAL_ESPECIALIDAD`: `ON (ESPECIALIDAD_ID)`

#### 11. `PACIENTE`
Datos del paciente.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `USUARIO_ID`: `NUMBER` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `TIPO_DOCUMENTO`: `VARCHAR2(5)` | `NOT NULL`
  * `NUMERO_DOCUMENTO`: `VARCHAR2(20)` | `NOT NULL`
  * `NOMBRES`: `VARCHAR2(60)` | `NOT NULL`
  * `APELLIDOS`: `VARCHAR2(60)` | `NOT NULL`
  * `FECHA_NACIMIENTO`: `DATE` | `NOT NULL`
  * `TELEFONO`: `VARCHAR2(20)` | `NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
  * `UPDATED_AT`: `TIMESTAMP WITH TIME ZONE` | `NULL`
* **Constraints:**
  * `PK_PACIENTE`: `PRIMARY KEY (ID)`
  * `UQ_PACIENTE_USUARIO`: `UNIQUE (USUARIO_ID)`
  * `UQ_PACIENTE_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_PACIENTE_DOC`: `UNIQUE (TIPO_DOCUMENTO, NUMERO_DOCUMENTO)`
  * `FK_PACIENTE_USUARIO`: `FOREIGN KEY (USUARIO_ID) REFERENCES USUARIO(ID) ON DELETE RESTRICT`
  * `CK_PACIENTE_TIPO_DOC`: `CHECK (TIPO_DOCUMENTO IN ('CC', 'TI', 'RC', 'CE', 'PA'))`

---

### 2.3 Módulo: Agenda, Disponibilidad y Citas Concurrencia

#### 12. `DISPONIBILIDAD_SLOT`
Turnos pregenerados ofertados por la administración.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `PROFESIONAL_ID`: `NUMBER` | `NOT NULL`
  * `SEDE_ID`: `NUMBER` | `NOT NULL`
  * `ESPECIALIDAD_ID`: `NUMBER` | `NOT NULL`
  * `FECHA_HORA_INICIO`: `TIMESTAMP WITH TIME ZONE` | `NOT NULL`
  * `FECHA_HORA_FIN`: `TIMESTAMP WITH TIME ZONE` | `NOT NULL`
  * `MODALIDAD`: `VARCHAR2(20)` | `DEFAULT 'PRESENCIAL' NOT NULL`
  * `ESTADO`: `VARCHAR2(20)` | `DEFAULT 'LIBRE' NOT NULL`
* **Constraints:**
  * `PK_DISPONIBILIDAD_SLOT`: `PRIMARY KEY (ID)`
  * `UQ_SLOT_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `FK_SLOT_PROFESIONAL`: `FOREIGN KEY (PROFESIONAL_ID) REFERENCES PROFESIONAL(ID) ON DELETE RESTRICT`
  * `FK_SLOT_SEDE`: `FOREIGN KEY (SEDE_ID) REFERENCES SEDE(ID) ON DELETE RESTRICT`
  * `FK_SLOT_ESPECIALIDAD`: `FOREIGN KEY (ESPECIALIDAD_ID) REFERENCES ESPECIALIDAD(ID) ON DELETE RESTRICT`
  * `CK_SLOT_HORAS`: `CHECK (FECHA_HORA_FIN > FECHA_HORA_INICIO)`
  * `CK_SLOT_MODALIDAD`: `CHECK (MODALIDAD IN ('PRESENCIAL', 'TELEMEDICINA'))`
  * `CK_SLOT_ESTADO`: `CHECK (ESTADO IN ('LIBRE', 'OCUPADO', 'BLOQUEADO'))`
* **Índices:**
  * `IX_SLOT_BUSQUEDA`: `ON (ESPECIALIDAD_ID, SEDE_ID, ESTADO, FECHA_HORA_INICIO)` (optimiza el buscador de disponibilidad de HU-03).
  * `IX_SLOT_PROFESIONAL`: `ON (PROFESIONAL_ID, FECHA_HORA_INICIO)` (previene solapes de horarios).

#### 13. `CITA`
Reserva de turno con protección concurrente a nivel de Oracle.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `SLOT_ID`: `NUMBER` | `NOT NULL`
  * `PACIENTE_ID`: `NUMBER` | `NOT NULL`
  * `TRIAJE_ID`: `NUMBER` | `NULL`
  * `CITA_ORIGEN_ID`: `NUMBER` | `NULL`
  * `ESTADO`: `VARCHAR2(20)` | `DEFAULT 'PROGRAMADA' NOT NULL`
  * `MOTIVO_CANCELACION`: `VARCHAR2(255)` | `NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
  * `UPDATED_AT`: `TIMESTAMP WITH TIME ZONE` | `NULL`
* **Constraints:**
  * `PK_CITA`: `PRIMARY KEY (ID)`
  * `UQ_CITA_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `FK_CITA_SLOT`: `FOREIGN KEY (SLOT_ID) REFERENCES DISPONIBILIDAD_SLOT(ID) ON DELETE RESTRICT`
  * `FK_CITA_PACIENTE`: `FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID) ON DELETE RESTRICT`
  * `FK_CITA_ORIGEN`: `FOREIGN KEY (CITA_ORIGEN_ID) REFERENCES CITA(ID) ON DELETE RESTRICT`
  * `CK_CITA_ESTADO`: `CHECK (ESTADO IN ('PROGRAMADA', 'CONFIRMADA', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO', 'REPROGRAMADA'))`
* **Índice Funcional Único de Concurrencia (ADR-006):**
  * `UQ_CITA_SLOT_ACTIVA`: `CREATE UNIQUE INDEX UQ_CITA_SLOT_ACTIVA ON CITA (CASE WHEN ESTADO IN ('PROGRAMADA', 'CONFIRMADA') THEN SLOT_ID END)`
    * *Justificación Técnica:* Impide a nivel de motor de base de datos que dos transacciones simultáneas inserten o actualicen una cita activa sobre el mismo slot, garantizando atomicidad y aislamiento independientemente de la lógica de aplicación.
* **Índices Secundarios:**
  * `IX_CITA_PACIENTE`: `ON (PACIENTE_ID, ESTADO, CREATED_AT DESC)`
  * `IX_CITA_SLOT`: `ON (SLOT_ID)`

---

### 2.4 Módulo: Triaje Clínico de Prototipo

#### 14. `SINTOMA`
Catálogo de síntomas observables.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `CODIGO`: `VARCHAR2(30)` | `NOT NULL`
  * `NOMBRE`: `VARCHAR2(100)` | `NOT NULL`
  * `CATEGORIA`: `VARCHAR2(50)` | `NOT NULL`
  * `ES_ALARMA`: `NUMBER(1) DEFAULT 0` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20)` | `DEFAULT 'ACTIVO' NOT NULL`
* **Constraints:**
  * `PK_SINTOMA`: `PRIMARY KEY (ID)`
  * `UQ_SINTOMA_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_SINTOMA_CODIGO`: `UNIQUE (CODIGO)`
  * `CK_SINTOMA_ALARMA`: `CHECK (ES_ALARMA IN (0, 1))`
  * `CK_SINTOMA_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`

#### 15. `REGLA_TRIAJE`
Reglas deterministas versionadas de orientación (ADR-009).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `VERSION`: `VARCHAR2(20)` | `NOT NULL`
  * `SINTOMA_ID`: `NUMBER` | `NOT NULL`
  * `CONDICION`: `VARCHAR2(100)` | `NOT NULL`
  * `NIVEL_PRIORIDAD`: `VARCHAR2(5)` | `NOT NULL`
  * `ES_ALARMA`: `NUMBER(1) DEFAULT 0` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20)` | `DEFAULT 'ACTIVO' NOT NULL`
* **Constraints:**
  * `PK_REGLA_TRIAJE`: `PRIMARY KEY (ID)`
  * `FK_REGLA_TRIAJE_SINTOMA`: `FOREIGN KEY (SINTOMA_ID) REFERENCES SINTOMA(ID) ON DELETE RESTRICT`
  * `CK_REGLA_NIVEL`: `CHECK (NIVEL_PRIORIDAD IN ('I', 'II', 'III', 'IV', 'V'))`
  * `CK_REGLA_ALARMA`: `CHECK (ES_ALARMA IN (0, 1))`
  * `CK_REGLA_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`
* **Índices:**
  * `IX_REGLA_TRIAJE_VERSION`: `ON (VERSION, ESTADO, SINTOMA_ID)`

#### 16. `TRIAJE`
Evaluación completada por un paciente.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `PACIENTE_ID`: `NUMBER` | `NOT NULL`
  * `VERSION_REGLAS`: `VARCHAR2(20)` | `NOT NULL`
  * `NIVEL_PRIORIDAD`: `VARCHAR2(5)` | `NOT NULL`
  * `RUTA_SUGERIDA`: `VARCHAR2(50)` | `NOT NULL`
  * `ES_EMERGENCIA`: `NUMBER(1) DEFAULT 0` | `NOT NULL`
  * `OBSERVACIONES`: `VARCHAR2(500)` | `NOT NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_TRIAJE`: `PRIMARY KEY (ID)`
  * `UQ_TRIAJE_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `FK_TRIAJE_PACIENTE`: `FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID) ON DELETE RESTRICT`
  * `CK_TRIAJE_NIVEL`: `CHECK (NIVEL_PRIORIDAD IN ('I', 'II', 'III', 'IV', 'V'))`
  * `CK_TRIAJE_EMERGENCIA`: `CHECK (ES_EMERGENCIA IN (0, 1))`
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
  * `FK_TS_TRIAJE`: `FOREIGN KEY (TRIAJE_ID) REFERENCES TRIAJE(ID) ON DELETE RESTRICT`
  * `FK_TS_SINTOMA`: `FOREIGN KEY (SINTOMA_ID) REFERENCES SINTOMA(ID) ON DELETE RESTRICT`
  * `CK_TS_INTENSIDAD`: `CHECK (INTENSIDAD BETWEEN 0 AND 10)`
  * `CK_TS_DURACION`: `CHECK (DURACION_HORAS >= 0)`

---

### 2.5 Módulo: Atención Clínica Inmutable

#### 18. `DIAGNOSTICO_CIE10`
Catálogo reducido de patologías.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `CODIGO`: `VARCHAR2(10)` | `NOT NULL`
  * `DESCRIPCION`: `VARCHAR2(255)` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20)` | `DEFAULT 'ACTIVO' NOT NULL`
* **Constraints:**
  * `PK_DIAGNOSTICO_CIE10`: `PRIMARY KEY (ID)`
  * `UQ_CIE10_CODIGO`: `UNIQUE (CODIGO)`
  * `CK_CIE10_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`

#### 19. `ATENCION`
Acto médico profesional inmutable (ADR-008).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `CITA_ID`: `NUMBER` | `NOT NULL`
  * `PACIENTE_ID`: `NUMBER` | `NOT NULL`
  * `PROFESIONAL_ID`: `NUMBER` | `NOT NULL`
  * `DIAGNOSTICO_PRINCIPAL_ID`: `NUMBER` | `NOT NULL`
  * `MOTIVO_CONSULTA`: `VARCHAR2(500)` | `NOT NULL`
  * `EVOLUCION`: `VARCHAR2(2000)` | `NOT NULL`
  * `INDICACIONES`: `VARCHAR2(1000)` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20)` | `DEFAULT 'CERRADA' NOT NULL`
  * `FECHA_CIERRE`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_ATENCION`: `PRIMARY KEY (ID)`
  * `UQ_ATENCION_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_ATENCION_CITA`: `UNIQUE (CITA_ID)` (relación estricta 1 a 1 con Cita)
  * `FK_ATENCION_CITA`: `FOREIGN KEY (CITA_ID) REFERENCES CITA(ID) ON DELETE RESTRICT`
  * `FK_ATENCION_PACIENTE`: `FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID) ON DELETE RESTRICT`
  * `FK_ATENCION_PROFESIONAL`: `FOREIGN KEY (PROFESIONAL_ID) REFERENCES PROFESIONAL(ID) ON DELETE RESTRICT`
  * `FK_ATENCION_CIE10`: `FOREIGN KEY (DIAGNOSTICO_PRINCIPAL_ID) REFERENCES DIAGNOSTICO_CIE10(ID) ON DELETE RESTRICT`
  * `CK_ATENCION_ESTADO`: `CHECK (ESTADO IN ('ABIERTA', 'CERRADA'))`
* **Garantía de Inmutabilidad mediante Trigger (ADR-008):**
  * Se define trigger `TR_BLOQUEO_ATENCION_CERRADA` que lanza `RAISE_APPLICATION_ERROR(-20001, 'No se permite UPDATE ni DELETE sobre atenciones cerradas')` si la fila ya se encuentra en estado `CERRADA`.
* **Índices Secundarios:**
  * `IX_ATENCION_PACIENTE`: `ON (PACIENTE_ID, FECHA_CIERRE DESC)`
  * `IX_ATENCION_PROFESIONAL`: `ON (PROFESIONAL_ID, FECHA_CIERRE DESC)`

#### 20. `SIGNO_VITAL`
Parámetros vitales registrados durante la consulta.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `ATENCION_ID`: `NUMBER` | `NOT NULL`
  * `TENSION_ARTERIAL`: `VARCHAR2(15)` | `NULL`
  * `FRECUENCIA_CARDIACA`: `NUMBER` | `NULL`
  * `FRECUENCIA_RESPIRATORIA`: `NUMBER` | `NULL`
  * `TEMPERATURA`: `NUMBER(4,1)` | `NULL`
  * `SATURACION_OXIGENO`: `NUMBER` | `NULL`
  * `PESO_KG`: `NUMBER(5,2)` | `NULL`
  * `TALLA_CM`: `NUMBER(4,1)` | `NULL`
* **Constraints:**
  * `PK_SIGNO_VITAL`: `PRIMARY KEY (ID)`
  * `FK_SIGNO_VITAL_ATENCION`: `FOREIGN KEY (ATENCION_ID) REFERENCES ATENCION(ID) ON DELETE RESTRICT`
* **Índices:**
  * `IX_SIGNO_VITAL_ATENCION`: `ON (ATENCION_ID)`

#### 21. `ATENCION_ENMIENDA`
Aclaración o adición append-only a una atención cerrada (ADR-008).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `ATENCION_ID`: `NUMBER` | `NOT NULL`
  * `PROFESIONAL_ID`: `NUMBER` | `NOT NULL`
  * `MOTIVO`: `VARCHAR2(255)` | `NOT NULL`
  * `CONTENIDO`: `VARCHAR2(2000)` | `NOT NULL`
  * `FECHA_ENMIENDA`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_ATENCION_ENMIENDA`: `PRIMARY KEY (ID)`
  * `FK_ENMIENDA_ATENCION`: `FOREIGN KEY (ATENCION_ID) REFERENCES ATENCION(ID) ON DELETE RESTRICT`
  * `FK_ENMIENDA_PROFESIONAL`: `FOREIGN KEY (PROFESIONAL_ID) REFERENCES PROFESIONAL(ID) ON DELETE RESTRICT`
* **Índices:**
  * `IX_ENMIENDA_ATENCION`: `ON (ATENCION_ID, FECHA_ENMIENDA ASC)`

#### 22. `ALERGIA`
Registro de hipersensibilidades del paciente.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PACIENTE_ID`: `NUMBER` | `NOT NULL`
  * `SUSTANCIA`: `VARCHAR2(100)` | `NOT NULL`
  * `REACCION`: `VARCHAR2(200)` | `NULL`
  * `SEVERIDAD`: `VARCHAR2(20)` | `NOT NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_ALERGIA`: `PRIMARY KEY (ID)`
  * `FK_ALERGIA_PACIENTE`: `FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID) ON DELETE RESTRICT`
  * `CK_ALERGIA_SEVERIDAD`: `CHECK (SEVERIDAD IN ('LEVE', 'MODERADA', 'GRAVE'))`
* **Índices:**
  * `IX_ALERGIA_PACIENTE`: `ON (PACIENTE_ID)`

---

### 2.6 Módulo: Farmacia y Recetas Médicas

#### 23. `MEDICAMENTO`
Catálogo maestro de fármacos.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `CODIGO`: `VARCHAR2(30)` | `NOT NULL`
  * `NOMBRE_COMERCIAL`: `VARCHAR2(100)` | `NOT NULL`
  * `PRINCIPIO_ACTIVO`: `VARCHAR2(100)` | `NOT NULL`
  * `PRESENTACION`: `VARCHAR2(100)` | `NOT NULL`
  * `CONCENTRACION`: `VARCHAR2(50)` | `NOT NULL`
  * `ESTADO`: `VARCHAR2(20)` | `DEFAULT 'ACTIVO' NOT NULL`
* **Constraints:**
  * `PK_MEDICAMENTO`: `PRIMARY KEY (ID)`
  * `UQ_MEDICAMENTO_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `UQ_MEDICAMENTO_CODIGO`: `UNIQUE (CODIGO)`
  * `CK_MEDICAMENTO_ESTADO`: `CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))`

#### 24. `RECETA`
Cabecera de prescripción médica.
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `PUBLIC_ID`: `VARCHAR2(36)` | `NOT NULL`
  * `ATENCION_ID`: `NUMBER` | `NOT NULL`
  * `PACIENTE_ID`: `NUMBER` | `NOT NULL`
  * `PROFESIONAL_ID`: `NUMBER` | `NOT NULL`
  * `VIGENCIA_DIAS`: `NUMBER DEFAULT 30` | `NOT NULL`
  * `CREATED_AT`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` | `NOT NULL`
* **Constraints:**
  * `PK_RECETA`: `PRIMARY KEY (ID)`
  * `UQ_RECETA_PUBLIC_ID`: `UNIQUE (PUBLIC_ID)`
  * `FK_RECETA_ATENCION`: `FOREIGN KEY (ATENCION_ID) REFERENCES ATENCION(ID) ON DELETE RESTRICT`
  * `FK_RECETA_PACIENTE`: `FOREIGN KEY (PACIENTE_ID) REFERENCES PACIENTE(ID) ON DELETE RESTRICT`
  * `FK_RECETA_PROFESIONAL`: `FOREIGN KEY (PROFESIONAL_ID) REFERENCES PROFESIONAL(ID) ON DELETE RESTRICT`
  * `CK_RECETA_VIGENCIA`: `CHECK (VIGENCIA_DIAS > 0)`
* **Índices:**
  * `IX_RECETA_PACIENTE`: `ON (PACIENTE_ID, CREATED_AT DESC)`
  * `IX_RECETA_ATENCION`: `ON (ATENCION_ID)`

#### 25. `RECETA_DETALLE`
Ítem prescrito con preservación histórica (snapshot inmutable / HU-08).
* **Columnas:**
  * `ID`: `NUMBER GENERATED ALWAYS AS IDENTITY` | `NOT NULL`
  * `RECETA_ID`: `NUMBER` | `NOT NULL`
  * `MEDICAMENTO_ID`: `NUMBER` | `NOT NULL`
  * `SNAPSHOT_NOMBRE`: `VARCHAR2(100)` | `NOT NULL`
  * `SNAPSHOT_PRESENTACION`: `VARCHAR2(100)` | `NOT NULL`
  * `DOSIS`: `VARCHAR2(100)` | `NOT NULL`
  * `FRECUENCIA`: `VARCHAR2(100)` | `NOT NULL`
  * `DURACION_DIAS`: `NUMBER` | `NOT NULL`
  * `CANTIDAD`: `NUMBER` | `NOT NULL`
  * `INDICACIONES`: `VARCHAR2(300)` | `NULL`
* **Constraints:**
  * `PK_RECETA_DETALLE`: `PRIMARY KEY (ID)`
  * `FK_RD_RECETA`: `FOREIGN KEY (RECETA_ID) REFERENCES RECETA(ID) ON DELETE RESTRICT`
  * `FK_RD_MEDICAMENTO`: `FOREIGN KEY (MEDICAMENTO_ID) REFERENCES MEDICAMENTO(ID) ON DELETE RESTRICT`
  * `CK_RD_CANTIDAD`: `CHECK (CANTIDAD > 0)`
  * `CK_RD_DURACION`: `CHECK (DURACION_DIAS > 0)`
* **Índices:**
  * `IX_RECETA_DETALLE_RECETA`: `ON (RECETA_ID)`

---

## 3. Análisis de Formas Normales (1FN, 2FN, 3FN)

El diseño del esquema ha sido auditado formalmente contra las reglas de normalización de Boyce-Codd y Tercera Forma Normal (3FN):

### 3.1 Primera Forma Normal (1FN)
* **Atributos Atómicos:** No existen arrays ni estructuras repetitivas anidadas en ninguna columna. Atributos multivaluados (síntomas de un triaje, detalles de una receta, signos vitales) fueron extraídos a tablas hijas dedicadas (`TRIAJE_SINTOMA`, `RECETA_DETALLE`, `SIGNO_VITAL`).
* **Unicidad de Fila:** Toda tabla cuenta con una Clave Primaria no nula, inmutable y única (`ID NUMBER GENERATED ALWAYS AS IDENTITY`).

### 3.2 Segunda Forma Normal (2FN)
* **Dependencia Funcional Completa:** En todas las tablas con claves primarias compuestas (`USUARIO_ROL`), cada columna depende funcionalmente de la totalidad de la clave primaria compuesta, no de un subconjunto de ella.

### 3.3 Tercera Forma Normal (3FN)
* **Ausencia de Dependencias Transitivas:** Ningún atributo no clave depende transitivamente de otra columna no clave. Los datos de sede dependen de la sede, no de la cita; los datos del paciente residen en `PACIENTE`, no duplicados en `CITA` o `ATENCION`.

### 3.4 Desnormalización Controlada Justificada (Snapshot en `RECETA_DETALLE`)
* **Caso:** Las columnas `SNAPSHOT_NOMBRE` y `SNAPSHOT_PRESENTACION` en `RECETA_DETALLE`.
* **Justificación Médico-Legal:** Si bien en un modelo relacional estrictamente académico se leerían siempre desde `MEDICAMENTO` vía join, en el contexto de prescripciones clínicas y farmacológicas esto constituye una vulnerabilidad grave. Si el catálogo maestro actualiza o corrige la presentación de un fármaco 3 años después, la receta histórica no debe variar su texto legal bajo ninguna circunstancia. El snapshot asegura **inmutabilidad de la historia clínica** con mínimo sobrecosto de almacenamiento.
