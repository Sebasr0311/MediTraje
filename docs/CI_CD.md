# Guía de Integración Continua (CI/CD) y Pruebas E2E — MediTriaje 2.0

> **Objetivo:** Documentación operativa de las tuberías automatizadas de GitHub Actions (`.github/workflows/ci.yml` y `.github/workflows/e2e.yml`), ejecución local de las suites y gestión de secretos en el pipeline.

---

## 1. Tuberías Automatizadas en GitHub Actions

MediTriaje 2.0 implementa dos flujos de trabajo automatizados para garantizar la integridad del código, la seguridad estricta y la verificación de flujos críticos de usuario:

### 1.1 Tubería de Integración Continua (`ci.yml`)
- **Archivo:** [`.github/workflows/ci.yml`](file:///.github/workflows/ci.yml)
- **Activación:** Cada `push` a cualquier rama y cada `pull_request` hacia ramas principales (`develop`, `main`, `integration/**`).
- **Trabajos (*Jobs*):**
  1. **Escaneo de Secretos (`gitleaks`):**  
     Descarga el historial Git completo (`fetch-depth: 0`) y ejecuta [Gitleaks v8](https://github.com/gitleaks/gitleaks) para detectar credenciales, tokens, certificados o claves privadas en todo el historial. Falla de forma inmediata ante cualquier coincidencia no autorizada.
  2. **Compilación y Pruebas (`build-and-test`):**  
     Configura Java 21 (Temurin con caché de Maven), compila el backend y ejecuta la suite completa con `mvn clean verify`:
     - **Surefire:** 944 pruebas unitarias rápidas bajo perfil `test` (Spring Boot aislado sin dependencias de base de datos externa).
     - **Failsafe con Testcontainers:** Pruebas de integración relacional (`OracleIntegrationTest`) contra un contenedor efímero de Oracle Free (`gvenzl/oracle-free:23-slim-faststart`), validando migraciones Flyway V1 a V16, inmutabilidad de triggers (`ORA-20001` a `ORA-20041`) y restricciones de privilegios mínimos para `MEDITRIAJE_APP`.
     - **Artefactos:** Publica reportes XML de Surefire y Failsafe conservados por 7 días.

---

### 1.2 Tubería End-to-End con Playwright (`e2e.yml`)
- **Archivo:** [`.github/workflows/e2e.yml`](file:///.github/workflows/e2e.yml)
- **Activación:** `push` / `pull_request` en ramas de integración y principales, o ejecución manual (`workflow_dispatch`).
- **Infraestructura:**
  - **Service Container:** Contenedor de Oracle Free (`gvenzl/oracle-free:23-slim-faststart`) con healthcheck en puerto 1521.
  - **Aprovisionamiento:** Ejecución de [`scripts/init-oracle-users.sql`](file:///scripts/init-oracle-users.sql) creando `MEDITRIAJE_OWNER` (DDL) y `MEDITRIAJE_APP` (DML).
  - **Backend API:** Spring Boot en background con perfil `dev`, migraciones Flyway automáticas y siembra controlada mediante `DemoDataSeeder` (`DEMO_SEED=true`, `DEMO_PASSWORD`).
  - **Frontend:** Servidor web estático local (`serve`) sirviendo la SPA vanilla en `http://localhost:3000`.
- **Flujos Críticos Evaluados (Playwright en modo headless):**
  1. [`01-patient-triage-appointment.spec.js`](file:///e2e/tests/01-patient-triage-appointment.spec.js):  
     Paciente autenticado realiza triaje leve, visualiza el aviso de prototipo legal, obtiene ruta sugerida, avanza a la agenda, selecciona slot libre y confirma la cita.
  2. [`02-professional-attention-recipe.spec.js`](file:///e2e/tests/02-professional-attention-recipe.spec.js):  
     Médico atiende cita desde su agenda, inspecciona el panel de alergias, diligencia signos vitales y CIE-10, cierra la atención de forma inmutable y emite receta médica asociada.
  3. [`03-admin-security-isolation.spec.js`](file:///e2e/tests/03-admin-security-isolation.spec.js):  
     Administrador autenticado verifica ausencia de opciones clínicas en la interfaz y recibe código `403 Forbidden` ante cualquier intento de consulta directa a endpoints clínicos.
- **Artefactos:** Trazas completas (`trace.zip`), capturas de pantalla y videos generados en caso de fallo conservados por 7 días.

---

## 2. Ejecución Local de las Pruebas

### 2.1 Pruebas Unitarias de Backend
No requieren base de datos externa ni Docker:
```bash
# Windows PowerShell:
$env:JAVA_HOME = "C:\Users\JUAN\.jdks\ms-21.0.11"
cd backend
mvn clean test

# Linux / macOS:
cd backend
mvn clean test
```

### 2.2 Pruebas de Integración con Testcontainers
Requieren que el motor de Docker (Docker Desktop o daemon local) esté activo:
```bash
cd backend
mvn clean verify
```

### 2.3 Escaneo Local de Secretos (Gitleaks)
```bash
# Con gitleaks instalado en PATH:
gitleaks detect --verbose --redact
```

### 2.4 Pruebas End-to-End con Playwright
Para ejecutar los flujos E2E en tu entorno local:

1. **Instalar dependencias de Playwright (solo la primera vez):**
   ```bash
   cd e2e
   npm install
   npx playwright install chromium
   ```

2. **Iniciar Backend y Frontend locales:**
   - Asegúrate de tener el backend corriendo en `http://localhost:8080` (con datos demo sembrados o base de datos de desarrollo).
   - Inicia un servidor estático para la carpeta `frontend/`:
     ```bash
     npx serve frontend -p 3000
     # o con python:
     python -m http.server 3000 --directory frontend
     ```

3. **Lanzar la suite E2E:**
   ```bash
   cd e2e
   
   # Modo headless rápido (como en CI):
   npx playwright test

   # Modo con interfaz gráfica interactiva (para depuración visual):
   npx playwright test --ui

   # Modo con navegador visible paso a paso:
   npx playwright test --headed
   ```

4. **Ver el reporte HTML interactivo:**
   ```bash
   npx playwright show-report
   ```

---

## 3. Secretos y Variables de Entorno en GitHub Actions

En conformidad con las políticas de seguridad de MediTriaje 2.0, **ninguna credencial real está versionada**. A continuación se detalla el listado de secretos y variables utilizadas por los pipelines:

| Variable / Secreto | Entorno | Propósito | Valor / Origen |
| :--- | :--- | :--- | :--- |
| `GITHUB_TOKEN` | CI (`ci.yml`) | Token efímero gestionado por GitHub Actions para autenticar el checkout profundo y reportes de Gitleaks. | Automático de GitHub. |
| `DEMO_PASSWORD` | E2E (`e2e.yml`) | Contraseña asignada a las cuentas del dominio `@demo.meditriaje.test` generadas por `DemoDataSeeder`. | Definido en workflow como valor seguro de prueba para CI (`DemoSecretPass123!`). |
| `E2E_BASE_URL` | E2E (`e2e.yml`) | URL base del servidor frontend durante la ejecución de pruebas. | `http://localhost:3000`. |
| `ORACLE_PASSWORD` | E2E (`e2e.yml`) | Contraseña del usuario administrativo del contenedor efímero de Oracle Free. | `testPassword1` (solo para CI efímero). |

> [!NOTE]
> **Aviso de Entorno Local (Windows CLI):**  
> Las tuberías `.github/workflows/ci.yml` y `.github/workflows/e2e.yml` están definidas y validadas sintácticamente para ejecutarse en runners `ubuntu-latest` de GitHub Actions. Si el entorno local de desarrollo carece de Docker daemon activo, los tests de Testcontainers en Windows son omitidos ordenadamente (`SKIPPED`) y la validación final del pipeline en la nube quedará a cargo de Juan en el primer run tras el push a GitHub.
