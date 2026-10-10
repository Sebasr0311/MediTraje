# MediTriaje 2.0 — Plataforma Web de Triaje, Atención Médica y Seguridad Clínica

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Tests](https://img.shields.io/badge/tests-940%20passing-brightgreen.svg)]()
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)]()
[![Database](https://img.shields.io/badge/Database-Oracle%20ATP%20Cloud-red.svg)]()
[![Migrations](https://img.shields.io/badge/Flyway-16%20migraciones-blue.svg)]()
[![Security](https://img.shields.io/badge/Security-Argon2id%20%7C%20JWT%20%7C%20MFA%20%7C%20CSP-blue.svg)]()
[![Architecture](https://img.shields.io/badge/ADRs-ADR--001%20a%20020-teal.svg)]()
[![Accessibility](https://img.shields.io/badge/WCAG-2.1%20AA-success.svg)]()

> **Plataforma web integral de orientación médica preliminar, triaje clínico estructurado, agendamiento de citas, atención médica inmutable, prescripción farmacológica auditada, dispensación, seguimiento y contingencia de emergencia.**  
> Diseñada bajo estrictos estándares de *Security by Design*, arquitectura limpia por capas, inmutabilidad relacional en Oracle ATP y estricto cumplimiento normativo colombiano (Ley 1581 de 2012 de Protección de Datos Personales y Ley 2015 de 2020 de Historia Clínica Electrónica).

---

## 1. Alcance y Funcionalidades de MediTriaje 2.0 (v2.0)

### 1.1 Núcleo Asistencial del MVP (v1.0)
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
  - Prevención atómica de doble reserva concurrente (`409 Conflict`) con índices únicos en Oracle.
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

### 1.2 Extensiones y Robustecimiento (Fase 2)
* **Autenticación Multifactor y Recuperación OTP (F2.1, ADR-014):**
  - Autenticación Multifactor TOTP (RFC 6238) con códigos secretos Base32, validación en ventana de tolerancia y códigos de respaldo cifrados para profesionales y administradores.
  - Recuperación de contraseña mediante código numérico OTP de 6 dígitos enviado por correo HTML institucional (expiración a 15 min).
* **Seguimiento Post-Atención y Notificaciones (F2.2, ADR-015):**
  - Planes de cuidado y tareas de recuperación posteriores a la consulta clínica.
  - Checklist interactivo de tareas para el paciente y reporte de evolución con semáforo de alerta clínica (verde/amarillo/rojo).
  - Despacho desacoplado y tolerante a fallos de correos institucionales para confirmación y cancelación de citas.
* **Resumen de Salud Portátil y Acceso QR Temporal (F2.3, ADR-010):**
  - Generación de resumen clínico consolidado (alergias, diagnósticos recientes, signos vitales y medicamentos activos).
  - Acceso paramédico de emergencia mediante código QR con token criptográfico temporal (1h a 24h), revocable y protegido contra visualización tras expiración sin requerir credenciales.
* **Ventanilla de Dispensación Farmacéutica (F2.4, ADR-016):**
  - Estación de trabajo para el regente de farmacia (`ROLE_FARMACEUTICO`) con búsqueda reactiva por documento o código alfanumérico (`REC-XXXXXXXX`).
  - Control de saldos acumulados por medicamento, entregas parciales y completas, y trazabilidad obligatoria de lotes INVIMA y fechas de vencimiento.
* **Acceso Clínico de Emergencia Break-Glass (F2.5, ADR-017):**
  - Protocolo médico excepcional para consultar historias clínicas de pacientes sin cita previa ante urgencias vitales inminentes.
  - Declaración juramentada, justificación médica obligatoria (mínimo 20 caracteres), expiración automática a 24 horas y auditoría reforzada inmutable. Prohibido estrictamente a administradores.
* **Asistente Virtual y Reportes Operativos (F2.6, ADR-018):**
  - Widget interactivo flotante con base de conocimiento estructurada de la plataforma, orientación asistencial y corte prioritario ante emergencias vitales (enlace al 123).
  - Tablero analítico administrativo con métricas agregadas anónimas (citas, triaje por niveles I–V, farmacia y activaciones Break-Glass).
* **Visor de Auditoría de Seguridad y Exportación CSV (F2.7, ADR-019):**
  - Consulta administrativa supervisada de la bitácora inmutable `AUDITORIA` con filtros por fecha, acción y resultado, y paginación ANSI SQL.
  - Exportación client-side de reportes hospitalarios en formato plano CSV estructurado con BOM UTF-8 para compatibilidad nativa con Microsoft Excel.

---

## 2. Stack Tecnológico

| Capa | Tecnologías |
|---|---|
| **Backend** | Java 21 LTS, Spring Boot 3.3.4, Spring Security 6, Spring JDBC (`JdbcTemplate`), BouncyCastle 1.78.1, JJWT 0.12.6, JavaMail Sender, Maven 3.9+. |
| **Base de Datos** | Oracle Autonomous Transaction Processing (ATP Cloud) / Oracle Free 23c (Docker), JDBC Thin (`ojdbc11` 23.5), Flyway 10 (14 migraciones versionadas). |
| **Frontend** | HTML5 semántico, CSS3 Vanilla (Design Tokens, WCAG 2.1 AA, soporte tema claro/oscuro), JavaScript ES Modules (sin frameworks ni bundlers). |
| **Seguridad** | Cookies HttpOnly + SameSite=Strict, CsrfHeaderFilter (`X-Requested-With`), CSP, Permissions-Policy, FrameOptions DENY, TOTP RFC 6238, Argon2id. |
| **Testing** | JUnit 5, Mockito, Spring Test (MockMvc), AssertJ, Playwright (E2E y visual), Testcontainers. |

---

## 3. Estructura del Repositorio

```text
MediTriaje/
├── backend/                 # API REST Spring Boot 3 (Java 21)
│   ├── src/main/java/com/meditriaje/
│   │   ├── config/          # Seguridad, CORS, HikariCP, Clock, Email
│   │   ├── controller/      # Endpoints REST (Auth, Citas, Triaje, Clínica, Recetas, Farmacia, Admin, Asistente, QR)
│   │   ├── dto/             # Contratos inmutables de API (Java Records)
│   │   ├── model/           # Modelos de dominio inmutables y máquinas de estado
│   │   ├── repository/      # Acceso a datos JDBC con SQL 100% parametrizado
│   │   ├── security/        # Filtros JWT y CSRF, JwtService, Argon2id, TotpService
│   │   ├── service/         # Lógica de negocio, transacciones, correo y notificaciones
│   │   └── triage/          # Motor puro determinista de triaje
│   └── src/test/java/       # 940 pruebas unitarias y de integración (100% pasando)
├── database/                # Base de datos y migraciones
│   ├── migrations/          # 16 scripts Flyway versionados (V001__baseline a V016__alergias_clinicas)
│   └── seeds/               # Semillas ficticias de desarrollo y catálogos CIE-10/medicamentos
├── docs/                    # Documentación técnica completa
│   ├── api/                 # API.md y 12 colecciones interactivas .http (M2 a M7, F2.1 a F2.7)
│   ├── architecture/        # ARCHITECTURE.md (diagramas C4, principios arquitectónicos)
│   ├── database/            # DATABASE.md, MODELO_RELACIONAL.md, MER.md
│   ├── demo/                # GUION_DEMO.md (guion interactivo paso a paso)
│   ├── requirements/        # DOCUMENTO_MAESTRO.md, CASOS_DE_USO.md, REGLAS_NEGOCIO.md
│   ├── security/            # SECURITY.md y REVISION_FINAL.md (informe de hardening y auditoría)
│   ├── DECISIONES.md        # Registro formal de 20 ADRs (ADR-001 a ADR-020)
│   ├── DISENO_UI_UX.md      # Guía de diseño, accesibilidad WCAG y tokens CSS
│   ├── MVP.md               # Alcance, historias de usuario y criterios de aceptación
│   ├── PLAN_DE_TRABAJO.md   # Desglose en microtareas M0 a M8, F2.1 a F2.8 y Plan Post-Auditoría
│   └── PROGRESO.md          # Bitácora viva de avance y sesiones de trabajo
├── frontend/                # Single Page Application (HTML/CSS/JS Vanilla ES Modules)
│   ├── assets/icons/        # Iconos SVG vectoriales inline
│   ├── css/                 # base.css, components.css, tokens.css
│   ├── js/                  # api.js, app.js, auth.js, router.js, ui.js, views/
│   ├── index.html           # Punto de entrada de la SPA
│   └── styleguide.html      # Catálogo interactivo de componentes y diseño
└── CHANGELOG.md             # Registro de cambios formal (Keep a Changelog)
```

---

## 4. Puesta en Marcha Rápida (Entorno Local)

### 4.1 Requisitos Previos
* **Java Development Kit (JDK):** Versión 21 LTS (ej. Microsoft Build of OpenJDK 21 o Eclipse Temurin).
* **Apache Maven:** Versión 3.9 o superior.
* **Navegador Web:** Chrome, Firefox, Edge o Safari moderno.

### 4.2 Ejecución de las Pruebas Automatizadas
Para verificar la suite de **940 pruebas unitarias y de integración**:
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

Las identidades de prueba provistas en los scripts de desarrollo son:

| Rol | Correo Electrónico | Contraseña Inicial | Funcionalidad Principal |
|---|---|---|---|
| **Administrador** | `admin@meditriaje.com` | `Admin12345*` | Infraestructura, especialidades, médicos, slots, reportes, exportación CSV y visor de auditoría. |
| **Médico (Medicina General)** | `carlos.mendoza@meditriaje.com` | `Temporal12345*` | Agenda médica diaria, inicio y cierre de atención, recetas CIE-10, Break-Glass y seguimiento. |
| **Médico (Pediatría)** | `laura.gomez@meditriaje.com` | `Temporal12345*` | Atención asistencial pediátrica y agenda aislada. |
| **Farmacéutico** | `farmacia@meditriaje.com` | `Farmacia12345*` | Ventanilla de farmacia, entrega de fármacos, control de saldos y trazabilidad INVIMA. |
| **Paciente** | *Registro libre en pantalla* | `Paciente12345*` | Triaje con corte 123, citas, historial, recetas, código de reclamación, QR temporal y seguimiento. |

---

## 6. Documentación Detallada del Sistema

| Documento | Enlace | Descripción |
|---|---|---|
| **Arquitectura** | [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md) | Diagrama C4, capas de backend, frontend SPA, principios y ADRs. |
| **Catálogo de API** | [`docs/api/API.md`](docs/api/API.md) | Especificación de endpoints REST, cookies, DTOs y colecciones `.http`. |
| **Base de Datos** | [`docs/database/DATABASE.md`](docs/database/DATABASE.md) | Configuración de Oracle ATP, Wallet, 14 migraciones Flyway y triggers. |
| **Seguridad** | [`docs/security/SECURITY.md`](docs/security/SECURITY.md) | Modelo de seguridad, defensas CSRF/XSS, inmutabilidad y matriz OWASP Top 10. |
| **Auditoría Final** | [`docs/security/REVISION_FINAL.md`](docs/security/REVISION_FINAL.md) | Informe de hardening de seguridad M8.5 y remediaciones aplicadas. |
| **Guion de Demostración** | [`docs/demo/GUION_DEMO.md`](docs/demo/GUION_DEMO.md) | Flujo paso a paso con datos ficticios para reproducir la demo integral. |
| **Styleguide** | [`frontend/styleguide.html`](frontend/styleguide.html) | Catálogo interactivo de componentes visuales en tema claro y oscuro. |
| **Bitácora de Progreso** | [`docs/PROGRESO.md`](docs/PROGRESO.md) | Bitácora viva de avance y cierre de fases M0 a M8 y F2.1 a F2.7. |
| **Registro de Cambios** | [`CHANGELOG.md`](CHANGELOG.md) | Historial formal de versiones y entregables. |

---

## 7. Limitaciones Conocidas y Alcance del Prototipo

MediTriaje 2.0 es un prototipo desarrollado en un marco académico y demostrativo. Presenta las siguientes limitaciones deliberadas de alcance:
1. **Confirmación y reprogramación de citas:** Las funcionalidades de confirmación explícita (`CONFIRMADA`) y reprogramación (`REPROGRAMADA`) no están implementadas en el flujo activo; los estados correspondientes permanecen reservados en la base de datos y la máquina de estados para compatibilidad futura (Decisiones D2 y D5).
2. **Reglas de triaje clínico de prototipo:** Las reglas del motor (`v1-prototipo`) y la ponderación de severidad son demostrativas y **no cuentan con validación clínica formal**. Ante cualquier síntoma de alarma o emergencia vital, el sistema corta infaliblemente hacia el canal de emergencias 123 y servicio presencial de urgencias.
3. **Datos de demostración ficticios:** Todas las instituciones, sedes, medicamentos, usuarios, pacientes y diagnósticos precargados son estrictamente ficticios y de prueba.
4. **Despliegue e infraestructura en capa gratuita:** Las instancias de demostración en la nube (Render Web Services y Oracle ATP Always Free) están sujetas a arranque en frío (*cold start*) y suspensión por inactividad, por lo que **no son aptas ni están autorizadas para uso en producción médica real**.

---

## 8. Licencia y Contexto Académico

Proyecto desarrollado con fines académicos en el contexto del sistema general de seguridad social en salud de Colombia. Los algoritmos de triaje corresponden a prototipos deterministas para demostración y **no sustituyen la valoración ni el criterio de un profesional de la salud matriculado**.
