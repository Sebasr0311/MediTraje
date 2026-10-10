# Guía de Despliegue en Producción y Operaciones — MediTriaje 2.0

> **Objetivo:** Instrucciones de despliegue reproducible en la nube para **Oracle Cloud ATP** (Base de datos), **Render** (Backend API Java 21 en contenedor Docker) y **Vercel** (Frontend SPA vanilla), integración de notificaciones transaccionales vía **Brevo API HTTP** y procedimientos de respaldo/restauración de datos.

---

## 1. Variables de Entorno de Producción (Runtime)

En estricto cumplimiento de las políticas de seguridad del proyecto, **ningún secreto ni contraseña se incluye en el repositorio ni en imágenes Docker**. Todas las configuraciones sensibles se inyectan en tiempo de ejecución a través de variables de entorno.

| Variable | Tipo / Formato | Obligatoria en `prod` | Descripción |
| :--- | :--- | :---: | :--- |
| `SPRING_PROFILES_ACTIVE` | Texto (`prod`) | **Sí** | Activa el perfil de producción endurecido en Spring Boot. |
| `DB_URL` | JDBC String | **Sí** | Cadena de conexión JDBC Oracle Thin. Ejemplo con mTLS: `jdbc:oracle:thin:@meditriaje_tp?TNS_ADMIN=/etc/secrets/wallet` o string TLS directo en puerto 1522. |
| `DB_USER` | Identificador | **Sí** | Usuario de base de datos de **aplicación en runtime** (`MEDITRIAJE_APP`). Cuenta con privilegios mínimos y carece de permisos DDL o de borrado clínico (`DELETE`). |
| `DB_PASSWORD` | Secreto | **Sí** | Contraseña del usuario de aplicación `MEDITRIAJE_APP`. |
| `FLYWAY_USER` | Identificador | Solo migraciones | Usuario propietario de tablas (`MEDITRIAJE_OWNER`), exclusivo para aplicar migraciones Flyway. |
| `FLYWAY_PASSWORD` | Secreto | Solo migraciones | Contraseña del usuario propietario `MEDITRIAJE_OWNER`. |
| `TNS_ADMIN` | Ruta absoluta | Condicional | Ruta al directorio donde residen los archivos de la Wallet de Oracle (`cwallet.sso`, `tnsnames.ora`, `sqlnet.ora`). Default: `/etc/secrets/wallet`. |
| `ORACLE_WALLET_BASE64` | Cadena Base64 | Opcional | Archivo `.zip` de la wallet codificado en Base64 para Render. Si se define, `scripts/prepare-wallet.sh` lo decodifica al arrancar. |
| `JWT_SECRET` | Secreto (≥ 256 bits) | **Sí** | Clave criptográfica para firma HMAC-SHA256 de access tokens. Generada con entropía alta (ej. `openssl rand -base64 48`). |
| `CORS_ORIGINS` | URL(s) separadas por coma | **Sí** | Orígenes autorizados en el navegador (ej. `https://meditriaje.vercel.app`). No se admiten comodines `*` en producción. |
| `FRONTEND_URL` | URL | Opcional | URL base del cliente web para enlaces en correos (ej. `https://meditriaje.vercel.app`). |
| `MAIL_TRANSPORT` | Texto (`brevo-api`) | **Sí** | Mecanismo de envío de correo. En producción debe ser `brevo-api` (HTTPS 443); Render Free bloquea puertos SMTP salientes (25, 465, 587). |
| `BREVO_API_KEY` | Secreto (`xkeysib-...`) | **Sí** (en `brevo-api`) | Clave de API v3 de Brevo para envío de correos transaccionales. |
| `MAIL_FROM` | Dirección de correo | **Sí** (en `brevo-api`) | Remitente verificado en la cuenta de Brevo. |
| `MAIL_FROM_NAME` | Texto | **Sí** (en `brevo-api`) | Nombre comercial del remitente (ej. `MediTriaje 2.0`). |
| `PORT` | Numérico | Auto (Render) | Puerto HTTP asignado dinámicamente por la plataforma (por defecto 8080). |
| `DEMO_SEED` | Booleano (`true`/`false`) | No (default `false`)| Activa el seeder controlado de datos de demostración si la BD está limpia o solo contiene cuentas `@demo.meditriaje.test`. |
| `DEMO_PASSWORD` | Secreto (≥ 12 caracteres) | Solo si `DEMO_SEED=true` | Contraseña asignada a las cuentas de demostración. Nunca registrar en código ni logs. |

> [!IMPORTANT]
> **Validación Fail-Fast (`ProdEnvironmentValidator`):**
> Al iniciar con `SPRING_PROFILES_ACTIVE=prod`, el backend verifica inmediatamente la presencia de todas las variables requeridas. Si falta alguna variable obligatoria, la aplicación aborta el arranque con un mensaje claro en consola antes de abrir puertos de red.

---

## 2. Gestión de la Wallet de Oracle ATP en Render

Oracle Autonomous Transaction Processing (ATP) utiliza por defecto autenticación mutua TLS (mTLS) a través de una Wallet con certificados criptográficos. Para operar de forma segura en Render sin almacenar credenciales en el repositorio:

### Método A: Variable de Entorno en Base64 (Recomendado)
1. Descarga el archivo `Wallet_MEDITRIAJE.zip` desde la consola de Oracle Cloud (OCI ATP → **DB Connection** → **Download Wallet**).
2. Codifica el archivo `.zip` completo a Base64 en tu terminal local:
   ```bash
   # En Linux / macOS:
   base64 -w 0 Wallet_MEDITRIAJE.zip > wallet_base64.txt
   
   # En Windows PowerShell:
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("Wallet_MEDITRIAJE.zip")) | Out-File -Encoding ascii wallet_base64.txt
   ```
3. En el dashboard de Render, agrega una variable de entorno:
   - **Key:** `ORACLE_WALLET_BASE64`
   - **Value:** *(Pega el contenido íntegro de `wallet_base64.txt`)*
4. Al arrancar el contenedor Docker, el script [`scripts/prepare-wallet.sh`](file:///C:/Users/JUAN/Antigravity%20IDE/MediTraje/MediTraje/scripts/prepare-wallet.sh) detecta la variable, la decodifica en un directorio seguro en memoria/disco temporal, extrae `cwallet.sso`, `tnsnames.ora`, `sqlnet.ora`, asigna permisos restrictivos `chmod 600` y exporta `TNS_ADMIN`.

### Método B: Secret Files en Render
1. En la configuración del servicio en Render, navega a **Environment** → **Secret Files**.
2. Sube individualmente los tres archivos indispensables del zip de la wallet:
   - `cwallet.sso` $\rightarrow$ `/etc/secrets/wallet/cwallet.sso`
   - `tnsnames.ora` $\rightarrow$ `/etc/secrets/wallet/tnsnames.ora`
   - `sqlnet.ora` $\rightarrow$ `/etc/secrets/wallet/sqlnet.ora`
3. Configura `TNS_ADMIN=/etc/secrets/wallet`.

### Método C: Conexión TLS Directa sin Wallet (Puerto 1522)
Si configuras tu instancia de Oracle ATP para permitir TLS estándar en el puerto 1522 (Mutual TLS deshabilitado en OCI Access Control):
- No se requiere Wallet ni `TNS_ADMIN`.
- Se utiliza el connection string JDBC con `tcps` y hostname directo de Oracle Cloud.

---

## 3. Segregación Estricta de Usuarios de Base de Datos (ADR-012)

La arquitectura de MediTriaje 2.0 impone el principio de menor privilegio mediante dos esquemas/usuarios en Oracle ATP:

1. **`MEDITRIAJE_OWNER` (Propietario / Migraciones):**
   - Posee las tablas, índices, secuencias y triggers.
   - Es el único usuario con privilegios DDL (`CREATE TABLE`, `ALTER TABLE`, etc.).
   - Se utiliza **únicamente durante la ejecución de Flyway** (`FLYWAY_USER` / `FLYWAY_PASSWORD`) en el pipeline de despliegue o durante la fase inicial de inicialización.
2. **`MEDITRIAJE_APP` (Runtime de la Aplicación):**
   - Es el usuario configurado en `DB_USER` / `DB_PASSWORD`.
   - Únicamente posee permisos DML mínimos (`SELECT`, `INSERT`, `UPDATE` en tablas operativas).
   - **Carece de permisos `DELETE`** en todas las tablas clínicas (`ATENCION`, `RECETA`, `ALERGIA`, `CONSENTIMIENTO`) y carece de `UPDATE`/`DELETE` en `AUDITORIA`.

---

## 4. Endurecimiento de Seguridad en Runtime

- **Cookies `Secure` y `HttpOnly`:**
  En producción, `security.cookie.secure: true` está activo. Render y Vercel sirven el tráfico exclusivamente sobre HTTPS con certificados TLS emitidos automáticamente. Los tokens JWT (`access_token`, `refresh_token`) nunca se exponen al código JavaScript de la página.
- **Configuración de CORS:**
  El backend valida la cabecera `Origin` contra `CORS_ORIGINS`. Peticiones provenientes de otros dominios son rechazadas con código 403.
- **Health Checks y Monitoreo:**
  - Endpoint de comprobación básica: `GET /api/v1/ping`
  - Health check detallado de Spring Boot Actuator: `GET /actuator/health` (valida conectividad con Oracle ATP).

---

## 5. Limitaciones Conocidas de Infraestructura Gratuita

> [!WARNING]
> MediTriaje 2.0 es un prototipo académico configurado para operar en niveles gratuitos de nube (*Free Tiers*). Ten en cuenta las siguientes restricciones operativas:

1. **Arranque en frío en Render (*Cold Starts*):**
   Las instancias gratuitas de Render se suspenden automáticamente tras 15 minutos sin peticiones entrantes. La primera solicitud posterior puede experimentar una latencia de **30 a 50 segundos** mientras se levanta el contenedor y se conecta a Oracle ATP.
2. **Detención por inactividad en Oracle ATP (*Always Free*):**
   Las bases de datos autónomas gratuitas de Oracle Cloud se detienen si transcurren más de 7 días consecutivos sin conexiones activas.
   - **Acción preventiva antes de demostraciones:** Ingresar a la consola de Oracle Cloud Infrastructure (OCI) y verificar que la base de datos figure en estado **Available** (Verde). Si figura en **Stopped**, presionar **Start** y esperar 2 minutos antes de realizar la prueba.

---

## 6. Procedimientos de Respaldo (Backup) y Restauración en Oracle ATP

Oracle Autonomous Database gestiona automáticamente la continuidad operativa de los datos.

### 6.1 Copias de Seguridad Automáticas
- OCI genera respaldos automáticos continuos con retención estándar de 60 días sin costo adicional.
- Permite recuperación a un punto específico en el tiempo (*Point-in-Time Recovery - PITR*) con precisión al segundo.

### 6.2 Generar un Respaldo Manual Bajo Demanda
Antes de cualquier actualización mayor o demostración importante:
1. Inicia sesión en la **Consola de OCI**.
2. Navega a **Oracle Database** $\rightarrow$ **Autonomous Database**.
3. Selecciona tu instancia `MEDITRIAJE`.
4. En el panel izquierdo de recursos, haz clic en **Backups**.
5. Haz clic en **Create Manual Backup**.
6. Asigna un nombre al respaldo (ej. `backup_pre_demo_20261006`) y confirma.
7. El respaldo quedará disponible en cuestión de minutos.

### 6.3 Procedimiento de Restauración (*Restore*)
En caso de requerir volver a un estado previo:
1. En la consola de la instancia en OCI, haz clic en el menú **More Actions** $\rightarrow$ **Restore**.
2. Selecciona una de las dos modalidades:
   - **Restore to a Timestamp:** Especifica la fecha y hora exacta (UTC) a la que deseas rebobinar la base de datos.
   - **Select Backup:** Escoge un respaldo manual o automático de la lista.
3. Confirma la operación. Durante la restauración, la base de datos pasará temporalmente a estado *Restoring* y reiniciará con el estado consistente seleccionado.

### 6.4 Lista de Verificación para Prueba de Restauración
Para validar que el procedimiento de respaldo es confiable:
- [ ] 1. Crear un respaldo manual en OCI con nombre `backup_verificacion_inicial`.
- [ ] 2. Registrar un paciente ficticio en la plataforma y verificar que exista en base de datos.
- [ ] 3. Ejecutar la restauración de la base de datos al momento previo del registro utilizando el respaldo creado.
- [ ] 4. Comprobar que la base de datos retorna a estado *Available*.
- [ ] 5. Intentar iniciar sesión con el paciente recién creado y comprobar que ya no existe (confirmando restauración exitosa del estado anterior).
- [ ] 6. Verificar que las tablas clínicas conserven sus triggers e integridad referencial intactos.

---

## 7. Integración Continua y Pruebas Automatizadas (CI/CD)

El repositorio cuenta con dos tuberías en GitHub Actions para asegurar la calidad y protección del código:
- **CI (`.github/workflows/ci.yml`):** Detección de secretos con Gitleaks sobre todo el historial y verificación completa de 944 pruebas unitarias Surefire y pruebas de integración Failsafe con Testcontainers (Oracle Free).
- **E2E (`.github/workflows/e2e.yml`):** Despliegue de servicio Oracle Free, arranque de backend con `DemoDataSeeder`, frontend estático y ejecución de pruebas Playwright en modo headless para los tres flujos críticos (triaje a cita, atención a receta, y aislamiento 403 para admin).

Para instrucciones de ejecución local y gestión de secretos en GitHub Actions, consulta la guía detallada en [`docs/CI_CD.md`](CI_CD.md).

