# MediTriaje 2.0 — Plataforma Web de Triaje, Atención Médica y Seguridad Clínica

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Tests](https://img.shields.io/badge/tests-1002%20passing%20(backend)%20%2B%2013%20(frontend)-brightgreen.svg)]()
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen.svg)]()
[![Database](https://img.shields.io/badge/Database-Oracle%20ATP%20Cloud-red.svg)]()
[![Migrations](https://img.shields.io/badge/Flyway-21%20migraciones-blue.svg)]()
[![Security](https://img.shields.io/badge/Security-Argon2id%20%7C%20JWT%20%7C%20MFA%20%7C%20CSP%20%7C%20ACL-blue.svg)]()
[![Architecture](https://img.shields.io/badge/ADRs-ADR--001%20a%20028-teal.svg)]()
[![Accessibility](https://img.shields.io/badge/WCAG-2.1%20AA-success.svg)]()

> **Plataforma web integral de orientación médica preliminar, triaje clínico estructurado, agendamiento de citas, atención médica inmutable, prescripción farmacológica auditada, dispensación, enfermería de urgencias, hospitalización, aseguramiento EPS y centro de mando.**  
> Diseñada bajo estrictos estándares de *Security by Design*, arquitectura limpia por capas, inmutabilidad relacional en Oracle ATP y estricto cumplimiento normativo colombiano (Ley 1581 de 2012 de Protección de Datos Personales, Ley 2015 de 2020 de Historia Clínica Electrónica, Resolución 2275 de 2023 RIPS y Ley Estatutaria 1751 de 2015 de Urgencias Médicas).

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

### 1.2 Extensiones y Robustecimiento (Fase 2 a Fase 5)
* **Enfermería y Admisión de Urgencias (Lote U / Fase 2, V018):**
  - Admisión ágil de urgencias presenciales sin exigir cuenta de usuario ni documento previo.
  - Registro de personas indocumentadas (NN) con generación de identidades provisionales opacas (`NN-XXXXXX`) y posterior vinculación trazable al documento definitivo.
  - Valoración humana de triaje I–V con toma de signos vitales, escala Glasgow y reevaluaciones dinámicas append-only.
  - Cola priorizada de urgencias por sede en tiempo real ordenada por severidad clínica.
* **Gestión Hospitalaria, Camas y Movimientos (Lote H / Fase 3, V019):**
  - Censo hospitalario interactivo de salas, consultorios y camas con estados controlados (`DISPONIBLE`, `OCUPADA`, `EN_LIMPIEZA`, `EN_MANTENIMIENTO`).
  - Asignación concurrente segura de camas y trazabilidad inmutable de traslados intrahospitalarios.
  - Soporte de pase a quirófano y recuperación posquirúrgica.
  - Egreso hospitalario médico que concluye el episodio asistencial y desocupa la cama para desinfección.
* **Aseguramiento EPS y Citas Avanzadas (Lotes A y C / Fase 4, V020):**
  - Carga masiva de afiliados EPS mediante Excel (XLSX) con motor Apache POI endurecido contra bombas de descompresión (Zip Bomb) y sanitización de inyección de fórmulas CSV/Excel.
  - Modos de confirmación atómica (`ATOMIC_ALL`) y resiliente (`VALID_ROWS`) sin creación de credenciales ficticias.
  - Consulta de aseguramiento no bloqueante: la ausencia de afiliación nunca impide la atención de urgencias (Ley 1751 de 2015).
  - Bloqueo de agendas por ausencias médicas y registro de tutores legales para atención de menores de edad.
* **Centro de Mando, Analítica y QR de Seguimiento (Lote O / Fase 5, V021):**
  - Tablero centralizado de KPIs operativos en tiempo real (ocupación, tiempos de espera, cirugías activas).
  - Motor de alertas operativas con reconocimiento auditable por personal asistencial y administrativo.
  - Identificación intrahospitalaria mediante código QR seguro con tokens aleatorios SHA-256 no clínicos (cero PHI expuesto).
  - Especificación formal de interoperabilidad para Colombia (`docs/INTEROPERABILIDAD_HOSPITALARIA_COLOMBIA.md`: RIPS JSON Resolución 2275 de 2023, CUPS, CIE-10 y FHIR R4).
* **Autenticación Multifactor y Recuperación OTP (F2.1, ADR-014):**
  - Autenticación Multifactor TOTP (RFC 6238) con códigos secretos Base32 y códigos de respaldo cifrados.
  - Recuperación de contraseña mediante código numérico OTP de 6 dígitos enviado por correo HTML institucional.
* **Ventanilla de Dispensación Farmacéutica (F2.4, ADR-016):**
  - Estación de trabajo para el regente de farmacia (`ROLE_FARMACEUTICO`) con control de saldos y lotes INVIMA.
* **Acceso Clínico de Emergencia Break-Glass (F2.5, ADR-017):**
  - Protocolo médico excepcional para consultar historias clínicas ante urgencias vitales inminentes (24 h de vigencia).

---

## 2. Stack Tecnológico

| Capa | Tecnologías |
|---|---|
| **Backend** | Java 21 LTS, Spring Boot 3.5.16, Spring Security 6.5.11, Spring JDBC (`JdbcTemplate`), Apache POI 5.3.0, BouncyCastle 1.78.1, JJWT 0.12.6, JavaMail Sender, Maven 3.9+. |
| **Base de Datos** | Oracle Autonomous Transaction Processing (ATP Cloud) / Oracle Free 23c (Docker), JDBC Thin (`ojdbc11` 23.5), Flyway 10 (21 migraciones versionadas inmutables). |
| **Frontend** | HTML5 semántico, CSS3 Vanilla (Design Tokens, WCAG 2.1 AA, soporte tema claro/oscuro), JavaScript ES Modules (sin frameworks ni bundlers pesados). |
| **Seguridad** | Cookies HttpOnly + SameSite=Strict, CsrfHeaderFilter (`X-Requested-With`), CSP, Permissions-Policy, FrameOptions DENY, TOTP RFC 6238, Argon2id, Zip Bomb Guard. |
| **Testing** | JUnit 5, Mockito, Spring Test (MockMvc), AssertJ, Playwright (E2E), Testcontainers, Node.js Test Runner. |

---

## 3. Estructura del Repositorio

```text
MediTriaje/
├── backend/                 # API REST Spring Boot 3 (Java 21)
│   ├── src/main/java/com/meditriaje/
│   │   ├── config/          # Seguridad, CORS, HikariCP, Clock, Email
│   │   ├── controller/      # Endpoints REST (Auth, Citas, Triaje, Urgencias, Hospital, Afiliaciones, Operacional, etc.)
│   │   ├── dto/             # Contratos inmutables de API (Java Records)
│   │   ├── model/           # Modelos de dominio inmutables y máquinas de estado
│   │   ├── repository/      # Acceso a datos JDBC con SQL 100% parametrizado
│   │   ├── security/        # Filtros JWT y CSRF, JwtService, Argon2id, TotpService
│   │   ├── service/         # Lógica asistencial, hospitalaria, transacciones y notificaciones
│   │   └── triage/          # Motor determinista de triaje presencial y asistencial
│   └── src/test/java/       # 1002 pruebas unitarias y de integración (100% verdes)
├── database/                # Base de datos y migraciones
│   ├── migrations/          # 21 scripts Flyway versionados (V001__baseline a V021__analitica_alertas_operativas_qr)
│   └── seeds/               # Semillas de desarrollo y catálogos CIE-10/medicamentos/EPS
├── docs/                    # Documentación técnica completa
│   ├── api/                 # API.md y 12 colecciones interactivas .http (M2 a M7, F2.1 a F2.7)
│   ├── architecture/        # ARCHITECTURE.md (diagramas C4, principios arquitectónicos)
│   ├── database/            # DATABASE.md, MODELO_RELACIONAL.md, MER.md
│   ├── demo/                # GUION_DEMO.md (guion interactivo paso a paso)
│   ├── requirements/        # DOCUMENTO_MAESTRO.md, CASOS_DE_USO.md, REGLAS_NEGOCIO.md
│   ├── security/            # SECURITY.md y REVISION_FINAL.md (informe de hardening y auditoría)
│   ├── DECISIONES.md        # Registro formal de 28 ADRs (ADR-001 a ADR-028)
│   ├── DISENO_UI_UX.md      # Guía de diseño, accesibilidad WCAG y tokens CSS
│   ├── MVP.md               # Alcance, historias de usuario y criterios de aceptación
│   ├── PLAN_DE_TRABAJO.md   # Desglose en microtareas M0 a M8, F2.1 a F2.8 y Plan Maestro
│   ├── MATRIZ_TRAZABILIDAD_PLAN_MAESTRO.md # Matriz de trazabilidad integral RF-001 a RF-027
│   └── PROGRESO_IMPLEMENTACION_PLAN_MAESTRO.md # Bitácora viva de avance de fases
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
