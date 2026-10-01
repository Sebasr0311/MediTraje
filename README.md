# MediTriaje 2.0

> Plataforma web de orientación clínica, triaje estructurado, agendamiento de citas médicas, atención clínica inmutable y emisión de recetas auditadas. Proyecto académico en contexto Colombia.

---

## 1. Stack Tecnológico

* **Backend:** Java 21 (LTS), Spring Boot 3.x, Spring Web, Spring Security, Spring JDBC / `JdbcTemplate` (sin JPA/Hibernate), Maven.
* **Base de datos:** Oracle Autonomous Transaction Processing (ATP) Cloud / Oracle Free Local (Docker/Testcontainers).
* **Migraciones de BD:** Flyway (`database/migrations/`).
* **Frontend:** HTML5, CSS3 (Design Tokens WCAG AA), JavaScript Vanilla (módulos ES nativos, sin frameworks ni build tools pesados).
* **Seguridad:** Cookies `HttpOnly; Secure; SameSite=Strict`, Access JWT corto (15 min) + Refresh Token rotativo y revocable (7 días) hasheado con Argon2id, cabecera CSRF personalizada, inmutabilidad clínica mediante triggers y enmiendas append-only.

---

## 2. Estructura del Repositorio

```text
MediTriaje/
├── .opencode/               # Configuración de agentes, comandos (/tarea) y skills
│   ├── agents/
│   ├── commands/
│   └── skills/
├── backend/                 # Código fuente de la API Spring Boot (Maven)
├── database/                # Migraciones Flyway, scripts de seeds y queries
│   ├── migrations/
│   ├── queries/
│   └── seeds/
├── docs/                    # Documentación y especificación formal
│   ├── api/                 # Colecciones .http por fase
│   ├── architecture/        # Diseños y decisiones de arquitectura
│   ├── database/            # MER, Modelo Relacional y normalización
│   ├── requirements/        # Documento Maestro, casos de uso y reglas de negocio
│   ├── security/            # Informes de auditoría de seguridad
│   ├── DECISIONES.md        # Registro de Decisiones de Arquitectura (ADRs)
│   ├── DISENO_UI_UX.md      # Guía de diseño visual y accesibilidad
│   ├── HERRAMIENTAS_CLI.md  # Guía de herramientas, MCP y skills
│   ├── MVP.md               # Alcance y criterios de aceptación v0.1
│   ├── PLAN_DE_TRABAJO.md   # Desglose en microtareas M0 a M8
│   └── PROGRESO.md          # Bitácora viva y estado actual del proyecto
├── frontend/                # Aplicación web estática (Vanilla JS, CSS Tokens)
│   ├── assets/
│   ├── css/
│   └── js/
├── scripts/                 # Scripts de utilidad y automatización
├── tests/                   # Pruebas e integración
├── .gitignore               # Exclusión de wallets, secretos y temporales
├── AGENTS.md                # Reglas no negociables para el desarrollo con agentes
├── opencode.json            # Configuración de permisos y MCP
└── README.md                # Documentación del proyecto
```

---

## 3. Fuentes de Verdad

1. **`docs/DECISIONES.md`**: Decisiones técnicas tomadas (ADR-001 a ADR-013).
2. **`docs/MVP.md`**: Alcance de la versión MVP, historias de usuario y criterios de aceptación.
3. **`docs/requirements/DOCUMENTO_MAESTRO.md`**: Especificación funcional y de dominio completa.
4. **`docs/PLAN_DE_TRABAJO.md`**: Plan de ejecución por tareas pequeñas.
5. **`docs/PROGRESO.md`**: Estado actual de avance y bitácora de tareas.
6. **`docs/DISENO_UI_UX.md`** y **`frontend/css/tokens.css`**: Sistema de diseño UI/UX obligatorio.

---

## 4. Metodología de Trabajo

El desarrollo se realiza mediante microtareas guiadas:
* Una tarea a la vez (`M0.1`, `M0.2`, etc.).
* Plan previo validado antes de escribir código.
* Sin inclusión de secretos ni wallets en el repositorio.
* Registro continuo en `docs/PROGRESO.md`.
