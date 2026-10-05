# Guía de Despliegue en Producción — MediTriaje 2.0
> **Objetivo:** Desplegar la plataforma desacoplada en **Oracle ATP** (Base de datos), **Render** (Backend API Java 21) y **Vercel** (Frontend Web SPA), integrando **Brevo** para notificaciones y recuperación de contraseñas.

---

## 1. Arquitectura de Despliegue Cloud

```
┌────────────────────────────────────────────────────────┐
│                   CLIENTE (Navegador)                   │
└───────────────────────────┬────────────────────────────┘
                            │ HTTPS
                            ▼
┌────────────────────────────────────────────────────────┐
│                    VERCEL (Frontend)                   │
│  - Sitio estático (HTML5, CSS Tokens, ES Modules)      │
│  - Proxy inverso transparente (/api/* -> Render)       │
│  - Cookies de sesión Same-Origin seguras (HttpOnly)    │
└───────────────────────────┬────────────────────────────┘
                            │ HTTPS (mTLS / Proxy)
                            ▼
┌────────────────────────────────────────────────────────┐
│                    RENDER (Backend)                    │
│  - Web Service en contenedor Docker (Java 21 Temurin)  │
│  - Spring Boot 3 + Argon2id + JJWT + Actuator          │
│  - Flyway (Migraciones automáticas al inicio)          │
│  - Despacho SMTP asíncrono hacia Brevo                 │
└─────────────┬────────────────────────────┬─────────────┘
              │ JDBC Thin (Port 1522)      │ SMTP TLS (Port 587)
              ▼                            ▼
┌───────────────────────────┐  ┌─────────────────────────┐
│     ORACLE CLOUD ATP      │  │        BREVO            │
│  - Autonomous Database    │  │  - SMTP Relay Oficial   │
│  - Esquema MEDITRIAJE     │  │  - Bienvenida & Claves  │
│  - Restricciones DDL/DML  │  │  - OTP Recuperación     │
└───────────────────────────┘  └─────────────────────────┘
```

---

## 2. Paso 1: Configurar Oracle Autonomous Database (ATP)

### A. Preparar los Usuarios de Base de Datos (ADR-012)
En el SQL Worksheet de la consola de Oracle Cloud (conectado como `ADMIN`), ejecuta los scripts para crear los dos usuarios segregados:
- **`MEDITRIAJE_OWNER`**: Propietario de las tablas y ejecutor de migraciones DDL con Flyway.
- **`MEDITRIAJE_APP`**: Usuario de runtime de la API con permisos mínimos (DML).

*(Ver `docs/database/DATABASE.md` y `database/scripts/setup_users.sql` para los scripts completos).*

### B. Obtener la Conexión a Oracle ATP
Tienes dos opciones de conexión:

#### Opción 1: Conexión con Wallet mTLS (Recomendada por defecto)
1. En la consola de Oracle Cloud ATP, haz clic en **DB Connection** y presiona **Download Wallet**.
2. Descomprime el archivo `.zip`. Necesitarás los archivos: `cwallet.sso`, `tnsnames.ora` y `sqlnet.ora`.
3. En Render se montarán como **Secret Files** en `/etc/secrets/wallet`.
4. La URL JDBC será:
   ```text
   jdbc:oracle:thin:@<nombre_base>_tp?TNS_ADMIN=/etc/secrets/wallet
   ```

#### Opción 2: Conexión TLS directa sin Wallet (Puerto 1522)
1. En la consola de ATP, ve a **Access Control** y asegúrate de permitir conexiones TLS mutuas o TLS estándar en el puerto 1522.
2. Copia el string de conexión TLS (que incluye el hostname `adb.<region>.oraclecloud.com` y el nombre del servicio).
3. La URL JDBC será:
   ```text
   jdbc:oracle:thin:@(description=(retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=1522)(host=adb.<region>.oraclecloud.com))(connect_data=(service_name=<service_name>_tp.adb.oraclecloud.com))(security=(ssl_server_dn_match=yes)))
   ```

---

## 3. Paso 2: Desplegar el Backend en Render

### A. Crear el Web Service en Render
1. Inicia sesión en [render.com](https://render.com) y ve al Dashboard.
2. Haz clic en **New +** → **Web Service**.
3. Conecta tu repositorio de GitHub `MediTraje` y selecciona la rama `develop` (o `main`).
4. Configuración básica:
   - **Name:** `meditriaje-backend` (o el nombre que elijas).
   - **Region:** `Oregon (US West)` o la más cercana a tu base de datos de Oracle.
   - **Language / Runtime:** **Docker** (Render detectará automáticamente el archivo [`Dockerfile`](file:///C:/Users/JUAN/Antigravity%20IDE/MediTraje/MediTraje/Dockerfile) en la raíz).
   - **Instance Type:** `Free` (o `Starter`).

### B. Configurar Secret Files (Solo si usas Wallet mTLS)
Si elegiste la Opción 1 con Wallet:
1. En la configuración del Web Service en Render, ve a la pestaña **Environment**.
2. En la sección **Secret Files**, haz clic en **Add Secret File**:
   - `cwallet.sso` montado en `/etc/secrets/wallet/cwallet.sso` (sube el binario o su contenido).
   - `tnsnames.ora` montado en `/etc/secrets/wallet/tnsnames.ora`.
   - `sqlnet.ora` montado en `/etc/secrets/wallet/sqlnet.ora`.

### C. Configurar las Variables de Entorno en Render
En la pestaña **Environment** de tu servicio en Render, agrega las siguientes variables:

| Variable | Valor de Ejemplo / Descripción |
| :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:oracle:thin:@meditriaje_tp?TNS_ADMIN=/etc/secrets/wallet` (o string TLS) |
| `DB_USER` | `MEDITRIAJE_APP` |
| `DB_PASSWORD` | *(contraseña segura de MEDITRIAJE_APP)* |
| `FLYWAY_USER` | `MEDITRIAJE_OWNER` |
| `FLYWAY_PASSWORD` | *(contraseña segura de MEDITRIAJE_OWNER)* |
| `JWT_SECRET` | *(string aleatorio de al menos 256 bits, ej. `openssl rand -base64 48`)* |
| `CORS_ORIGINS` | `https://tu-proyecto.vercel.app` |
| `FRONTEND_URL` | `https://tu-proyecto.vercel.app` |
| `SMTP_HOST` | `smtp-relay.brevo.com` |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME` | *(usuario/correo de tu cuenta en Brevo)* |
| `SMTP_PASSWORD` | *(clave maestra SMTP generada en Brevo)* |
| `SMTP_FROM` | `notificaciones@tudominio.com` *(remitente validado en Brevo)* |
| `SMTP_FROM_NAME` | `MediTriaje 2.0` |

### D. Iniciar Despliegue y Verificar
1. Haz clic en **Deploy Web Service**.
2. En la pestaña **Logs**, observarás:
   - Compilación multi-stage de Maven y empaquetado del JAR.
   - Arranque de Spring Boot con perfil `prod`.
   - Ejecución de migraciones automáticas de Flyway (`V001` a `V014`) aplicando todas las tablas e índices en Oracle ATP.
   - Inicio del servidor HTTP en el puerto dinámico asignado por Render.
3. Copia la URL pública generada por Render (por ejemplo: `https://meditriaje-backend.onrender.com`).

---

## 4. Paso 3: Desplegar el Frontend en Vercel

El frontend es una Single Page Application (SPA) vanilla moderna con ES Modules, sin dependencias de compiladores pesados ni NodeJS de build.

### A. Crear Proyecto en Vercel
1. Inicia sesión en [vercel.com](https://vercel.com) y ve al Dashboard.
2. Haz clic en **Add New...** → **Project**.
3. Importa el repositorio de GitHub `MediTraje`.
4. En la pantalla de configuración:
   - **Project Name:** `meditriaje` (o el de tu preferencia).
   - **Framework Preset:** `Other`.
   - **Root Directory:** Haz clic en **Edit** y selecciona `frontend` (o deja la raíz `.` si usas el [`vercel.json`](file:///C:/Users/JUAN/Antigravity%20IDE/MediTraje/MediTraje/vercel.json) raíz).
   - **Build Command:** Dejar vacío (no requiere build).
   - **Output Directory:** Dejar vacío.

### B. Configurar el Proxy Inverso en `vercel.json`
Para que las cookies seguras `HttpOnly` (`access_token`, `refresh_token`) funcionen de manera transparente sin bloqueos de terceros entre dominios distintos, Vercel redirige automáticamente las peticiones de `/api/*` hacia Render.

1. Abre el archivo [`vercel.json`](file:///C:/Users/JUAN/Antigravity%20IDE/MediTraje/MediTraje/vercel.json) (o [`frontend/vercel.json`](file:///C:/Users/JUAN/Antigravity%20IDE/MediTraje/MediTraje/frontend/vercel.json)):
2. Sustituye la URL de destino por tu dominio real de Render:
   ```json
   {
     "version": 2,
     "cleanUrls": true,
     "rewrites": [
       {
         "source": "/api/:path*",
         "destination": "https://TU-SERVICIO-REAL.onrender.com/api/:path*"
       }
     ]
   }
   ```
3. Realiza el commit y push a tu repositorio. Vercel desplegará automáticamente.

---

## 5. Paso 4: Configurar y Probar Brevo (SMTP Relay)

1. En el panel de [Brevo](https://app.brevo.com), accede a **Transactional** → **Settings** → **Configuration**.
2. Verifica que el remitente configurado en `SMTP_FROM` esté validado en **Senders & IPs**.
3. Copia tu clave API SMTP y asígnala a la variable `SMTP_PASSWORD` en Render.
4. Con esto, tanto la entrega de credenciales iniciales para profesionales como el envío de códigos OTP de recuperación funcionarán inmediatamente.

---

## 6. Paso 5: Verificación Integral Post-Despliegue (Smoke Test)

Una vez completados los despliegues, realiza las siguientes comprobaciones en tu navegador:

1. **Health Check:**
   - Abre `https://tu-proyecto.vercel.app/api/v1/health` o `https://tu-backend.onrender.com/actuator/health`.
   - Respuesta esperada: `{"status":"UP"}` (código 200).
2. **Acceso al Frontend:**
   - Entra a `https://tu-proyecto.vercel.app`.
   - Comprueba que la pantalla de inicio cargue los estilos de `tokens.css` y el branding institucional.
3. **Registro de Paciente en Vivo:**
   - Ve a `#/register`.
   - Selecciona **Cédula de Ciudadanía (CC)** y pon una fecha de menor de edad $\rightarrow$ Comprueba que la validación en tiempo real te indique que CC requiere $\ge 18$ años.
   - Pon fecha válida, celular iniciando en 3 (10 dígitos) y completa el registro.
   - Deberás quedar autenticado en `#/patient/dashboard`.
4. **Alta de Profesional & Brevo:**
   - Inicia sesión con el Administrador.
   - En el panel administrativo, da de alta un profesional asistencial.
   - Comprueba que aparezca la modal con la contraseña temporal y que el correo de bienvenida llegue a la bandeja de entrada del médico vía Brevo.
5. **Restablecimiento de Contraseña:**
   - En `#/forgot-password`, solicita el código para tu correo de prueba.
   - Verifica la recepción del código numérico de 6 dígitos e ingresa la nueva clave en la pantalla de recuperación.

---

## 7. Mantenimiento y Troubleshooting

- **¿El backend en Render tarda en responder en el plan Free?**
  El plan gratuito de Render suspende el contenedor tras 15 minutos de inactividad. La primera petición puede tardar unos 40 segundos mientras arranca el contenedor.
- **¿Error de conexión a Oracle ATP?**
  Verifica que en OCI la lista de control de acceso (ACL) de la base de datos permita el tráfico entrante de internet (`0.0.0.0/0` para ATP con mTLS o TLS).
- **¿Cookies no se guardan en el navegador?**
  Asegúrate de que estás consumiendo la API a través del dominio de Vercel gracias a los `rewrites` de `vercel.json`, o que el backend tiene configurado `security.cookie.secure: true` con HTTPS.
