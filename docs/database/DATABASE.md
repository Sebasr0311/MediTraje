# DATABASE.md — Guía de Configuración de Base de Datos (Oracle ATP)

> **MediTriaje 2.0** utiliza una base de datos **Oracle Autonomous Transaction Processing (ATP)** en Oracle Cloud Infrastructure (OCI, nivel Always Free) bajo el estándar JDBC Thin con Wallet de autenticación mutua (mTLS).

---

## 1. Descarga y Ubicación del Wallet

1. Ingresa a la consola de **Oracle Cloud Infrastructure (OCI)**.
2. Navega a **Oracle Database** → **Autonomous Databases** y selecciona tu instancia de ATP (ejemplo: `meditriaje`).
3. Haz clic en el botón **DB Connection** (Conexión a base de datos).
4. En tipo de wallet, selecciona **Instance Wallet** y haz clic en **Download Wallet**.
5. Define una contraseña para el archivo ZIP del wallet y descárgalo.
6. **Descomprime el wallet en una carpeta FUERA del repositorio Git**:
   * **Linux / macOS:** `~/oracle/wallet` (o `~/.oracle/wallet`)
   * **Windows:** `C:\oracle\wallet` (o en tu carpeta de usuario, p. ej. `C:\Users\<usuario>\oracle\wallet`)
7. **Regla de Seguridad No Negociable:**
   * El wallet contiene certificados SSL/TLS y llaves privadas (`cwallet.sso`, `ewallet.p12`, `tnsnames.ora`).
   * **NUNCA** coloques el wallet dentro de la carpeta del proyecto.
   * El archivo `.gitignore` del proyecto bloquea cualquier archivo `.sso`, `.p12`, `wallet*` y carpetas de credenciales por precaución.

---

## 2. Aprovisionamiento de Usuarios en Oracle ATP (ADR-012)

Para garantizar el principio de mínimos privilegios y la inmutabilidad clínica, se segregan las operaciones de base de datos en dos cuentas de usuario. Conéctate a ATP con el usuario administrativo `ADMIN` (mediante SQL Developer, DBeaver o SQLcl) y ejecuta:

```sql
-- =============================================================================
-- 1. Crear usuario propietario del esquema (MEDITRIAJE_OWNER)
--    Utilizado exclusivamente por Flyway para migraciones y operaciones DDL
-- =============================================================================
CREATE USER MEDITRIAJE_OWNER IDENTIFIED BY "<password_owner>";

-- Privilegios de conexión y creación de objetos
GRANT CREATE SESSION TO MEDITRIAJE_OWNER;
GRANT CREATE TABLE TO MEDITRIAJE_OWNER;
GRANT CREATE VIEW TO MEDITRIAJE_OWNER;
GRANT CREATE SEQUENCE TO MEDITRIAJE_OWNER;
GRANT CREATE TRIGGER TO MEDITRIAJE_OWNER;
GRANT CREATE PROCEDURE TO MEDITRIAJE_OWNER;
GRANT CREATE SYNONYM TO MEDITRIAJE_OWNER;

-- Cuota ilimitada en el tablespace por defecto de ATP
ALTER USER MEDITRIAJE_OWNER QUOTA UNLIMITED ON DATA;


-- =============================================================================
-- 2. Crear usuario de runtime de la aplicación (MEDITRIAJE_APP)
--    Utilizado por Spring Boot / HikariCP con mínimos privilegios
-- =============================================================================
CREATE USER MEDITRIAJE_APP IDENTIFIED BY "<password_app>";

-- Privilegio mínimo exclusivo de sesión
GRANT CREATE SESSION TO MEDITRIAJE_APP;

-- NOTA: Los permisos SELECT, INSERT, UPDATE sobre las tablas del esquema
-- son concedidos de forma granular por cada script de migración Flyway
-- ejecutado por MEDITRIAJE_OWNER (V###__*.sql).
-- MEDITRIAJE_APP NUNCA recibe permisos DELETE en tablas clínicas ni UPDATE/DELETE
-- en AUDITORIA y ATENCION_ENMIENDA.
```

---

## 3. Formato de la URL JDBC y Variables de Entorno

La conexión utiliza el driver JDBC oficial de Oracle (`ojdbc11`) con el protocolo Thin y el parámetro `TNS_ADMIN` apuntando a la ruta absoluta del wallet descomprimido:

### Formato General:
```text
jdbc:oracle:thin:@<alias_servicio>?TNS_ADMIN=<ruta_absoluta_al_wallet>
```
* `<alias_servicio>`: Nombre del alias que figura en el archivo `tnsnames.ora` dentro de tu wallet (generalmente `<nombre_db>_tp` o `<nombre_db>_high`). Para cargas OLTP de MediTriaje se recomienda el perfil transaccional `_tp`.

### Ejemplos por Sistema Operativo:
* **Linux / macOS:**
  ```bash
  export DB_URL="jdbc:oracle:thin:@meditriaje_tp?TNS_ADMIN=/Users/juan/oracle/wallet"
  export DB_USER="MEDITRIAJE_APP"
  export DB_PASSWORD="<password_app>"
  export FLYWAY_USER="MEDITRIAJE_OWNER"
  export FLYWAY_PASSWORD="<password_owner>"
  ```
* **Windows (PowerShell):**
  ```powershell
  $env:DB_URL = "jdbc:oracle:thin:@meditriaje_tp?TNS_ADMIN=C:/oracle/wallet"
  $env:DB_USER = "MEDITRIAJE_APP"
  $env:DB_PASSWORD = "<password_app>"
  $env:FLYWAY_USER = "MEDITRIAJE_OWNER"
  $env:FLYWAY_PASSWORD = "<password_owner>"
  ```

---

## 4. Configuración del Pool HikariCP y Sesión (ADR-005 / ADR-012)

El pool de conexiones HikariCP de la aplicación inicializa cada conexión física ejecutando un bloque anónimo PL/SQL mediante `connectionInitSql`:

```sql
BEGIN
  EXECUTE IMMEDIATE 'ALTER SESSION SET TIME_ZONE = ''America/Bogota''';
  EXECUTE IMMEDIATE 'ALTER SESSION SET CURRENT_SCHEMA = MEDITRIAJE_OWNER';
END;
```

**Efecto:**
1. **Zona horaria unificada:** Todas las consultas operan bajo la hora oficial de Colombia (`America/Bogota`), garantizando coherencia en `TIMESTAMP WITH TIME ZONE`.
2. **Resolución transparente:** El usuario `MEDITRIAJE_APP` consulta las tablas directamente (`SELECT * FROM CITA`) sin necesidad de calificar el nombre con el prefijo del propietario (`MEDITRIAJE_OWNER.CITA`).

---

## 5. Health Check y Verificación de Conectividad

Spring Boot Actuator expone el estado de salud de la base de datos a través de `GET /actuator/health`.

### Consulta de Verificación:
HikariCP está configurado con `connection-test-query: SELECT 1 FROM DUAL`.

### Respuesta esperada:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "Oracle",
        "validationQuery": "isValid()"
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

---

## 6. Respaldos y Restauración en Oracle ATP

Oracle Autonomous Database realiza copias de seguridad automáticas continuas:
1. **Frecuencia:** Copias de seguridad completas semanales, incrementales diarias y respaldos de redo logs cada pocos minutos (RPO < 5 minutos).
2. **Retención:** 60 días en almacenamiento seguro de OCI.
3. **Procedimiento de Restauración:**
   * En la consola de OCI, accede a tu base de datos ATP.
   * Haz clic en **More Actions** → **Restore**.
   * Selecciona **Select a Backup** o **Restore to a Point in Time (PITR)** (marca de tiempo exacta).
   * Confirma la operación. La base de datos pasará a estado `RESTORE_IN_PROGRESS` y volverá a `AVAILABLE` al finalizar.

---

## 7. Despliegue en la Nube (Render y Vercel)

El proyecto adopta una arquitectura desacoplada para el despliegue en producción (ADR-012):

### Backend en Render (Web Service):
1. **Manejo del Wallet sin exponer secretos en Git:**
   * En el dashboard de **Render**, en la configuración de tu servicio, accede a **Environment** → **Secret Files**.
   * Crea los archivos requeridos del wallet (p. ej. `cwallet.sso`, `tnsnames.ora`, `sqlnet.ora`, etc.) montados en la ruta `/etc/secrets/wallet/`.
   * Alternativamente, si en ATP habilitas **TLS sin mTLS (puerto 1522)**, puedes conectar directamente sin archivos de wallet.
2. **Variables de Entorno en Render:**
   * `DB_URL`: `jdbc:oracle:thin:@meditriaje_tp?TNS_ADMIN=/etc/secrets/wallet`
   * `DB_USER`: `MEDITRIAJE_APP`
   * `DB_PASSWORD`: *(valor secreto)*
   * `FLYWAY_USER`: `MEDITRIAJE_OWNER`
   * `FLYWAY_PASSWORD`: *(valor secreto)*
   * `JWT_SECRET`: *(secreto de al menos 256 bits)*
   * `CORS_ORIGINS`: `https://tu-proyecto.vercel.app`
3. **Puerto Dinámico:**
   * Render asigna automáticamente la variable de entorno `$PORT`. La aplicación Spring Boot la detecta y enlaza a través de `server.port = ${PORT:${SERVER_PORT:8080}}`.

### Frontend en Vercel:
* El directorio `frontend/` se despliega como sitio estático sin fase de compilación.
* Las peticiones hacia la API se direccionan al dominio del Web Service en Render (`https://tu-backend.onrender.com/api/v1`).

---

## 8. Gestión de Migraciones con Flyway (M1.3 / ADR-004)

Las transformaciones del esquema en Oracle ATP son estrictamente versionadas e inmutables.

### 1. Convención de Nomenclatura y Ubicación:
* **Directorio raíz:** `database/migrations/`
* **Formato de archivo:** `V###__descripcion_corta.sql` (tres dígitos numéricos, doble guion bajo, descripción en minúsculas).
* **Ejemplos:**
  * `V001__baseline.sql`: Línea base técnica y tabla de control del sistema.
  * `V002__seguridad_y_usuarios.sql`: Tablas de identidad, roles y auditoría (Fase M2).
* **Regla Inmutable:** Un archivo de migración ya aplicado en algún entorno **NUNCA se edita**. Cualquier corrección se efectúa mediante una nueva migración `V###` hacia adelante.

### 2. Privilegios de Ejecución y Segregación (ADR-012):
* Flyway corre **exclusivamente con la cuenta `MEDITRIAJE_OWNER`** (definida en `FLYWAY_USER` y `FLYWAY_PASSWORD`).
* Cada script `V###__*.sql` debe incluir al final las sentencias `GRANT` explícitas para `MEDITRIAJE_APP` (`SELECT`, `INSERT`, `UPDATE` estrictamente necesarios; sin `DELETE` clínico).

### 3. Comandos de Ejecución de Migraciones:

* **Opción A: Ejecución mediante Maven Flyway Plugin:**
  ```powershell
  # Desde la carpeta backend/:
  cd backend
  mvn flyway:migrate
  ```
  Para consultar el historial y estado de migraciones:
  ```powershell
  mvn flyway:info
  ```

* **Opción B: Migración Automática al Iniciar Spring Boot:**
  En entornos donde se desee aplicar migraciones durante el arranque (o en el despliegue de Render):
  ```powershell
  # Linux / macOS / Render:
  export FLYWAY_ENABLED=true
  mvn spring-boot:run

  # Windows (PowerShell):
  $env:FLYWAY_ENABLED = "true"
  mvn spring-boot:run
  ```

### 4. Tabla de Control de Versiones:
Flyway registra cada ejecución en la tabla técnica `flyway_schema_history` bajo el esquema `MEDITRIAJE_OWNER`, registrando checksums criptográficos, tiempos de ejecución y estado exitoso (`SUCCESS = 1`).

---

## 9. Inventario Consolidado de Migraciones Aplicadas (V001 a V009)

| Versión | Archivo SQL | Fase | Tablas Creadas / Modificadas | Triggers y Mecanismos Clave |
|---|---|---|---|---|
| **V001** | `V001__baseline.sql` | M1 | `CONTROL_SISTEMA` | Línea base técnica y validación inicial de permisos. |
| **V002** | `V002__seguridad.sql` | M2 | `USUARIO`, `ROL`, `USUARIO_ROL`, `REFRESH_TOKEN`, `CONSENTIMIENTO`, `AUDITORIA` | Triggers `TR_CONSENTIMIENTO_INMUTABILIDAD` y `TR_AUDITORIA_INMUTABILIDAD`. Semillas de roles básicos. |
| **V003** | `V003__paciente.sql` | M2 | `PACIENTE` | Restricciones de unicidad en número de documento y vínculo `USUARIO_ID`. |
| **V004** | `V004__oferta_administracion.sql` | M3 | `INSTITUCION`, `SEDE`, `ESPECIALIDAD`, `PROFESIONAL`, `DISPONIBILIDAD_SLOT` | Control de solapes, duraciones configurables y estados lógicos sin borrado físico. |
| **V005** | `V005__usuario_cambio_password.sql` | M3 | `USUARIO` (alter) | Añade columna `DEBE_CAMBIAR_PASSWORD` para forzar cambio de contraseña temporal. |
| **V006** | `V006__citas.sql` | M4 | `CITA` | Índice funcional único `UQ_CITA_SLOT_ACTIVA` para prevenir colisiones concurrentes. |
| **V007** | `V007__triaje.sql` | M5 | `SINTOMA`, `REGLA_TRIAJE`, `TRIAJE`, `TRIAJE_SINTOMA` | FK compuesta `(TRIAJE_ID, PACIENTE_ID)` en `CITA`. Semillas de síntomas con 6 banderas de alarma. |
| **V008** | `V008__atencion_historia_clinica.sql` | M6 | `DIAGNOSTICO_CIE10`, `ATENCION`, `SIGNO_VITAL`, `ATENCION_ENMIENDA` | Triggers `TR_ATENCION_INMUTABILIDAD`, `TR_SIGNO_VITAL_INMUTABILIDAD` y `TR_ENMIENDA_INMUTABILIDAD`. Semillas CIE-10. |
| **V009** | `V009__recetas_medicamentos.sql` | M7 | `MEDICAMENTO`, `RECETA`, `RECETA_DETALLE` | Triggers `TR_RECETA_INMUTABILIDAD` y `TR_RECETA_DETALLE_INMUTABILIDAD`. Snapshot cuádruple de fármacos. |

---

## 10. Triggers PL/SQL de Inmutabilidad Clínica

Para garantizar inmutabilidad legal en el motor de base de datos Oracle ATP, se implementaron triggers `BEFORE UPDATE OR DELETE` que cancelan la transacción con excepciones de aplicación:

| Trigger | Tabla Protegida | Evento | Código de Error PL/SQL | Justificación |
|---|---|---|---|---|
| `TR_CONSENTIMIENTO_INMUTABILIDAD` | `CONSENTIMIENTO` | `UPDATE / DELETE` | `ORA-20000` | Un consentimiento legal firmado jamás puede ser modificado ni destruido. |
| `TR_AUDITORIA_INMUTABILIDAD` | `AUDITORIA` | `UPDATE / DELETE` | `ORA-20000` | Las trazas de auditoría son de solo inserción (*insert-only*). |
| `TR_ATENCION_INMUTABILIDAD` | `ATENCION` | `UPDATE` (en CERRADA) / `DELETE` | `ORA-20001` / `ORA-20002` | Una vez cerrada la atención, sus campos clínicos quedan congelados. Modificaciones requieren enmiendas. |
| `TR_SIGNO_VITAL_INMUTABILIDAD` | `SIGNO_VITAL` | `UPDATE / DELETE` | `ORA-20003` / `ORA-20004` | Los signos vitales registrados durante la atención son inmutables. |
| `TR_ENMIENDA_INMUTABILIDAD` | `ATENCION_ENMIENDA` | `UPDATE / DELETE` | `ORA-20005` | Las aclaraciones y enmiendas son estrictamente append-only. |
| `TR_RECETA_INMUTABILIDAD` | `RECETA` | `UPDATE / DELETE` | `ORA-20007` / `ORA-20006` | Las prescripciones emitidas no admiten edición ni borrado posterior. |
| `TR_RECETA_DETALLE_INMUTABILIDAD` | `RECETA_DETALLE` | `UPDATE / DELETE` | `ORA-20009` / `ORA-20008` | Los detalles farmacológicos de la receta quedan fijados permanentemente. |

---

## 11. Segregación de Privilegios de `MEDITRIAJE_APP` (ADR-012)

La cuenta de conexión en tiempo de ejecución (`MEDITRIAJE_APP`) posee privilegios estrictamente recortados:

* **Tablas de Solo Lectura (SELECT):** `SINTOMA`, `REGLA_TRIAJE`, `DIAGNOSTICO_CIE10`, `MEDICAMENTO`, `ROL`.
* **Tablas Insert-Only (SELECT, INSERT):** `CONSENTIMIENTO`, `AUDITORIA`, `TRIAJE`, `TRIAJE_SINTOMA`, `SIGNO_VITAL`, `ATENCION_ENMIENDA`, `RECETA`, `RECETA_DETALLE`.
* **Tablas Transaccionales Controladas (SELECT, INSERT, UPDATE):** `USUARIO`, `PACIENTE`, `PROFESIONAL`, `INSTITUCION`, `SEDE`, `ESPECIALIDAD`, `DISPONIBILIDAD_SLOT`, `CITA`, `ATENCION`, `REFRESH_TOKEN`.
* **Privilegios DELETE:** Vedados en todas las tablas clínicas, auditorías y catálogos. Únicamente se permite `DELETE` físico condicional en `DISPONIBILIDAD_SLOT` cuando `ESTADO = 'LIBRE'`.



