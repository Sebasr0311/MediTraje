# MediTriaje 2.0 — Plataforma Web de Triaje y Atención Médica

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)]()
[![Oracle Database](https://img.shields.io/badge/Database-Oracle%20ATP%20Cloud-red.svg)]()
[![Security](https://img.shields.io/badge/Security-Argon2id%20%7C%20JWT%20%7C%20CSP-blue.svg)]()
[![Accessibility](https://img.shields.io/badge/WCAG-2.1%20AA-success.svg)]()

> **Plataforma web integral de orientación médica preliminar, triaje clínico estructurado, agendamiento de citas, atención médica inmutable y prescripción farmacológica auditada.**  
> Diseñada bajo estrictos estándares de *Security by Design*, arquitectura limpia por capas y cumplimiento normativo colombiano (Ley 1581 de 2012 de Protección de Datos Personales).

---

## 1. Alcance y Funcionalidades del MVP (v1.0)

* **Autenticación y Cuentas Seguras:**
  - Registro de pacientes con aceptación obligatoria de Consentimiento Informado (v1.0, Ley 1581).
  - Hashing criptográfico resistente con **Argon2id v5.8** (OWASP).
  - Sesiones gestionadas por **Cookies `HttpOnly; Secure; SameSite=Strict`** (cero almacenamiento de tokens en `localStorage`).
  - Access Token JWT (15 min) y Refresh Token opaco rotativo de alta entropía con detección inmediata de reuso.
  - Bloqueo preventivo por 15 minutos ante 5 intentos fallidos consecutivos de login.
* **Motor de Triaje Clínico Determinista:**
  - Clasificación de severidad y cálculo determinista de Nivel I a V con versión de reglas trazable.
  - **Corte de Emergencia Infalible:** Detección de síntomas de alarma (dolor torácico opresivo, disnea severa, signos de ACV, etc.) que bloquea el agendamiento y orienta inmediatamente al número de emergencias 123.
* **Disponibilidad y Agendamiento Transaccional:**
  - Consulta de slots libres en tiempo real agrupados por fecha y horario en zona `America/Bogota` (UTC-5).
  - Prevención atómica de doble reserva concurrente (`409 Conflict`).
  - Cancelación de citas por parte del paciente permitida hasta 2 horas antes de la hora fijada.
* **Módulo Asistencial del Profesional Médico:**
  - Agenda del día aislada exclusivamente para el profesional responsable.
  - Registro de atención clínica con validación fisiológica de signos vitales (presión sistólica > diastólica) y codificación CIE-10 estándar.
  - **Inmutabilidad Absoluta:** Cierre irreversible de atenciones protegido por triggers PL/SQL en Oracle ATP.
  - **Enmiendas Médicas Append-Only:** Correcciones y aclaraciones sucesivas sin alterar el registro original.
* **Prescripción Farmacológica Auditada:**
  - Emisión de recetas con catálogo maestro de medicamentos.
  - **Snapshot Cuádruple:** Congela el nombre comercial, principio activo, concentración y presentación al momento exacto de la prescripción para evitar adulteraciones históricas.
* **Aislamiento de Administración y Mínimo Privilegio:**
  - Panel unificado en pestañas para gestión de instituciones, sedes, especialidades, profesionales y slots.
  - **Aislamiento Clínico Estricto:** El personal administrativo tiene **estrictamente vedado el acceso al contenido clínico** (`403 Forbidden`).
* **Auditoría Inmutable:**
  - Trazabilidad de accesos, atenciones y recetas sin registrar datos clínicos ni contraseñas.

---

## 2. Stack Tecnológico

| Capa | Tecnologías |
|---|---|
| **Backend** | Java 21 LTS, Spring Boot 3.3.4, Spring Security 6, Spring JDBC (`JdbcTemplate`), BouncyCastle 1.78.1, JJWT 0.12.6, Maven 3.9+. |
| **Base de Datos** | Oracle Autonomous Transaction Processing (ATP Cloud) / Oracle Free 23c (Docker), JDBC Thin (`ojdbc11` 23.5), Flyway 10. |
| **Frontend** | HTML5 semántico, CSS3 Vanilla (Design Tokens, WCAG AA, soporte tema claro/oscuro), JavaScript ES Modules (sin frameworks ni bundlers). |
| **Seguridad** | Cookies HttpOnly + SameSite=Strict, CsrfHeaderFilter (`X-Requested-With`), CSP, Permissions-Policy, Referrer-Policy, FrameOptions DENY. |
| **Testing** | JUnit 5, Mockito, Spring Test (MockMvc), AssertJ, Playwright (E2E y visual), Testcontainers. |

---

## 3. Estructura del Repositorio

```text
MediTriaje/
├── backend/                 # API REST Spring Boot 3 (Java 21)
│   ├── src/main/java/com/meditriaje/
│   │   ├── config/          # Seguridad, CORS, HikariCP, Clock
│   │   ├── controller/      # Endpoints REST (Auth, Citas, Triaje, Clínica, Recetas, Admin)
│   │   ├── dto/             # Contratos inmutables de API (Java Records)
│   │   ├── model/           # Modelos de dominio inmutables y máquinas de estado
│   │   ├── repository/      # Acceso a datos JDBC con SQL parametrizado
│   │   ├── security/        # Filtros JWT y CSRF, JwtService, Argon2
│   │   ├── service/         # Lógica de negocio y transacciones
│   │   └── triage/          # Motor puro determinista de triaje
│   └── src/test/java/       # 501 pruebas unitarias y de integración
├── database/                # Base de datos y migraciones
│   ├── migrations/          # 9 scripts Flyway versionados (V001__baseline a V009__recetas)
│   └── seeds/               # Semillas ficticias de desarrollo y script de carga
├── docs/                    # Documentación técnica completa
│   ├── api/                 # API.md y colecciones interactivas M2.http a M7.http
│   ├── architecture/        # ARCHITECTURE.md (diagramas C4, ADRs)
│   ├── database/            # DATABASE.md, MODELO_RELACIONAL.md, MER.md
│   ├── demo/                # GUION_DEMO.md (guion interactivo paso a paso)
│   ├── requirements/        # DOCUMENTO_MAESTRO.md, CASOS_DE_USO.md, REGLAS_NEGOCIO.md
│   ├── security/            # SECURITY.md y REVISION_FINAL.md (informe de hardening M8.5)
│   ├── DECISIONES.md        # Registro formal de 13 ADRs aprobados
│   ├── DISENO_UI_UX.md      # Guía de diseño, accesibilidad y tokens
│   ├── MVP.md               # Alcance, historias y criterios de aceptación
│   ├── PLAN_DE_TRABAJO.md   # Desglose en microtareas M0 a M8
│   └── PROGRESO.md          # Bitácora viva y estado consolidado
├── frontend/                # Single Page Application (HTML/CSS/JS Vanilla)
│   ├── assets/icons/        # 26 Iconos SVG vectoriales inline
│   ├── css/                 # base.css, components.css, tokens.css
│   ├── js/                  # api.js, app.js, auth.js, router.js, ui.js, views/
│   ├── index.html           # Punto de entrada de la SPA
│   └── styleguide.html      # Catálogo interactivo de componentes y diseño
└── CHANGELOG.md             # Registro de cambios formal (Keep a Changelog)
```

---

## 4. Puesta en Marcha Rápida (Entorno Local)

### 4.1 Requisitos Previos
* **Java Development Kit (JDK):** Versión 21 LTS (ej. Eclipse Temurin o Microsoft Build of OpenJDK 21).
* **Apache Maven:** Versión 3.9 o superior.
* **Navegador Web:** Chrome, Firefox, Edge o Safari moderno.

### 4.2 Ejecución de las Pruebas Automatizadas
Para verificar la suite de **501 pruebas unitarias y de integración**:
```powershell
cd backend
mvn clean test
```
*Todas las pruebas unitarias y MockMvc corren en memoria utilizando H2/mocks sin requerir Docker ni base de datos externa.*

### 4.3 Ejecución del Backend
```powershell
cd backend
mvn spring-boot:run
```
La API iniciará en `http://localhost:8080`. Puedes verificar su salud consultando:
```http
GET http://localhost:8080/api/v1/ping
```
Respuesta: `{"status":"UP"}` con cabeceras de seguridad reforzadas.

### 4.4 Ejecución del Frontend
Dado que el frontend es Vanilla ES Modules, debe servirse a través de un servidor HTTP local para respetar la política CORS de módulos ES:
```powershell
# Opción 1: Con npm/npx
npx serve frontend -l 5500

# Opción 2: Con Python 3
python -m http.server 5500 --directory frontend
```
Accede desde tu navegador a: **`http://localhost:5500`**.

---

## 5. Cuentas y Credenciales de Demostración

El script de semillas de desarrollo (`database/seeds/dev_seeds_m3.sql`) aprovisiona las siguientes identidades de prueba:

| Rol | Correo Electrónico | Contraseña Inicial | Funcionalidad Principal |
|---|---|---|---|
| **Administrador** | `admin@meditriaje.com` | `Admin12345*` | Gestión de instituciones, sedes, especialidades, médicos y generador de turnos. |
| **Médico (Medicina General)** | `carlos.mendoza@meditriaje.com` | `Temporal12345*` | Agenda médica diaria, inicio y cierre de atención, recetas con CIE-10. |
| **Médico (Pediatría)** | `laura.gomez@meditriaje.com` | `Temporal12345*` | Atención y agenda de la especialidad pediátrica. |
| **Paciente** | *Registro libre en pantalla* | `Paciente12345*` | Triaje, agendamiento de cita, consulta de historia clínica y recetas. |

---

## 6. Documentación Detallada del Sistema

| Documento | Enlace | Descripción |
|---|---|---|
| **Arquitectura** | [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md) | Diagrama C4, capas de backend, frontend SPA, principios y ADRs. |
| **Catálogo de API** | [`docs/api/API.md`](docs/api/API.md) | Especificación de endpoints REST, cookies, DTOs y colecciones `.http`. |
| **Base de Datos** | [`docs/database/DATABASE.md`](docs/database/DATABASE.md) | Configuración de Oracle ATP, Wallet, migraciones Flyway V1-V9 y triggers. |
| **Seguridad** | [`docs/security/SECURITY.md`](docs/security/SECURITY.md) | Modelo de seguridad, defensas CSRF/XSS, inmutabilidad y matriz OWASP Top 10. |
| **Auditoría Final** | [`docs/security/REVISION_FINAL.md`](docs/security/REVISION_FINAL.md) | Informe de hardening de seguridad M8.5 y remediaciones aplicadas. |
| **Guion de Demostración** | [`docs/demo/GUION_DEMO.md`](docs/demo/GUION_DEMO.md) | Flujo paso a paso con datos ficticios para reproducir la demo del MVP. |
| **Styleguide** | [`frontend/styleguide.html`](frontend/styleguide.html) | Catálogo interactivo de componentes visuales en tema claro y oscuro. |
| **Bitácora de Progreso** | [`docs/PROGRESO.md`](docs/PROGRESO.md) | Bitácora viva de avance y cierre de fases M0 a M8. |
| **Registro de Cambios** | [`CHANGELOG.md`](CHANGELOG.md) | Historial de versiones y entregables del MVP. |

---

## 7. Licencia y Contexto Académico

Proyecto desarrollado con fines académicos en el contexto del sistema general de seguridad social en salud de Colombia. Los algoritmos de triaje corresponden a prototipos deterministas para demostración y **no sustituyen la valoración ni el criterio de un profesional de la salud matriculado**.
