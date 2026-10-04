# MediTriaje 2.0 — Plan de trabajo con la CLI

Este plan convierte el MVP (`docs/MVP.md`) en **tareas pequeñas**, cada una con su explicación, su prompt listo para pegar y su forma de verificarla. Se trabaja **una tarea a la vez**. Nada de "hazme todo el backend".

---

## 0. Cómo funciona (léelo una vez)

### 0.1 Por qué por tareas pequeñas
La CLI no recuerda sesiones anteriores y, cuanto más larga es la sesión, más se desvía. Tareas pequeñas dan: diffs que puedes revisar de verdad, commits que puedes revertir y menos oportunidades de que invente cosas.

**Regla de tamaño:** una tarea = un commit = un diff que puedas revisar en 10–15 minutos. Si la CLI toca más de ~10 archivos, la tarea era demasiado grande: pídele partirla.

### 0.2 La memoria del proyecto: `docs/PROGRESO.md`
Como la CLI olvida, el estado vive en un archivo. Al empezar cada sesión la CLI lo lee; al terminar cada tarea lo actualiza (tarea hecha, decisiones, pendientes). Ya viene creado junto a este plan.

### 0.3 El ciclo de cada tarea (siempre igual)
1. **Sesión nueva** (o limpia el contexto). Rama de la fase: `feature/mX-nombre`.
2. **Pega el PREFIJO + la TAREA** (abajo).
3. La CLI responde con un **plan** (archivos, riesgos, preguntas). **No hay código todavía.**
4. **Tú revisas el plan.** Corrige o aprueba ("OK, procede").
5. La CLI implementa **solo esa tarea** y corre las pruebas.
6. **Tú revisas el diff** (`git diff`) y ejecutas tú mismo las pruebas.
7. Verificas con el **checklist de la tarea** (y con `docs/api/MX.http` si hay endpoints).
8. **Commit** con el mensaje propuesto. `PROGRESO.md` actualizado.
9. Siguiente tarea. Al terminar la fase: **puerta de salida** (sección de cada fase), merge a `develop` y etiqueta `v0.X`.

### 0.4 PREFIJO (se pega al inicio de TODA tarea)
> **Atajo en OpenCode:** el comando `/tarea M2.3` hace esto por ti (ver `docs/HERRAMIENTAS_CLI.md`). El texto de abajo es el equivalente manual.

```
Lee AGENTS.md, docs/DECISIONES.md, docs/MVP.md y docs/PROGRESO.md.
Estamos en la fase {M?}, tarea {M?.?}.
ANTES de escribir código: dame un plan corto (archivos que crearás o modificarás, riesgos, preguntas abiertas) y espera mi OK.
Tras mi OK: implementa SOLO esta tarea, ejecuta las pruebas, actualiza docs/PROGRESO.md y propón el mensaje de commit.
Si encuentras una contradicción o una decisión no definida, detente y pregúntame. No avances a otras tareas.
TAREA:
```

### 0.5 Si la CLI se desvía
| Síntoma | Qué hacer |
|---|---|
| Hizo más de lo pedido | `git restore .` o `git revert`; repite la tarea recordando "SOLO esta tarea". |
| Inventó una decisión o regla clínica | Recuérdale `AGENTS.md` ("no improvisar") y pídele que lo marque como PENDIENTE. |
| Sesión larga y confusa | Cierra, abre sesión nueva; el PREFIJO la reorienta. |
| Pruebas fallan y "las arregla" debilitándolas | Prohíbelo: "arregla el código, no la prueba". |
| Quiere cambiar una decisión de `DECISIONES.md` | Que lo proponga por escrito; tú decides y actualizas el ADR. |

### 0.5b Cada fase deja un archivo `docs/api/MX.http`
Peticiones HTTP de ejemplo (VS Code REST Client, Bruno o IntelliJ). Así pruebas cada endpoint sin esperar al frontend.

---

# FASE M0 — Cimientos de diseño (sin código)

**Objetivo:** dejar el repo ordenado y aprobar el diseño antes de programar.
**Por qué:** el documento maestro lo exige: modelo antes que código. Cambiar una tabla después, con código encima, cuesta mucho más.

### M0.1 Repo y estructura
**Qué:** `git init`, estructura de carpetas del documento maestro §38, `.gitignore` (wallet, `.env`, `target/`, `*.log`), README mínimo, mover los archivos a su lugar (`docs/requirements/DOCUMENTO_MAESTRO.md`, `AGENTS.md`, `docs/MVP.md`, `docs/DECISIONES.md`, `docs/PROGRESO.md`). Ramas `main` y `develop`.
**Por qué:** que ningún secreto llegue nunca a Git y que la CLI encuentre todo donde lo espera.
```
Crea la estructura del repositorio según el Documento Maestro §38, con .gitignore que excluya wallet de Oracle, .env, target/, logs e IDE. Crea README.md mínimo. No crees código todavía. Deja carpetas vacías con .gitkeep.
```
**Verifica:** `git status` limpio; `.gitignore` cubre wallet y `.env`; ramas creadas.

### M0.2 Casos de uso y reglas de negocio
**Qué:** `docs/requirements/CASOS_DE_USO.md`: actores (Paciente, Profesional, Admin), un caso de uso por cada HU del MVP con precondiciones, flujo principal, flujos alternos y errores. Y `REGLAS_NEGOCIO.md` con RB-01..RB-15 más las del MVP (cancelación 2 h, relación asistencial, etc.).
**Por qué:** de aquí salen las pruebas; sin esto, los criterios de aceptación quedan vagos.
```
Basándote SOLO en docs/MVP.md, docs/DECISIONES.md y las reglas RB-01..RB-15 del Documento Maestro §44, escribe docs/requirements/CASOS_DE_USO.md (un caso por HU) y docs/requirements/REGLAS_NEGOCIO.md. No añadas funcionalidades nuevas. Marca cualquier ambigüedad como PENDIENTE DE DECISIÓN.
```
**Verifica:** cada HU tiene caso de uso; ninguna regla contradice los ADR.

### M0.3 Modelo de dominio y MER
**Qué:** `docs/database/MER.md` con diagrama Mermaid (`erDiagram`) de las tablas mínimas de MVP §4, con cardinalidades.
**Por qué:** ves el modelo completo de un vistazo y detectas relaciones faltantes.
```
Crea docs/database/MER.md con un diagrama Mermaid erDiagram de las tablas mínimas listadas en docs/MVP.md §4. Incluye cardinalidades y una breve explicación por entidad. No generes SQL.
```
**Verifica:** el diagrama renderiza (GitHub o extensión Mermaid) y cubre todas las HU.

### M0.4 Modelo relacional y normalización
**Qué:** `docs/database/MODELO_RELACIONAL.md`: por tabla, columnas, tipos Oracle, PK, FK, `UNIQUE`, `CHECK`, `NOT NULL`, índices justificados, política `ON DELETE`, y análisis de 3FN.
**Por qué:** es el contrato de la BD. Aplica ADR-003 (IDs), 005 (fechas), 006 (slots), 008 (inmutabilidad).
```
Con base en docs/database/MER.md y docs/DECISIONES.md, escribe docs/database/MODELO_RELACIONAL.md: tabla por tabla con columnas, tipos Oracle, PK, FK, UNIQUE, CHECK, índices (justificando cada uno) y ON DELETE. Incluye el índice único funcional de CITA (ADR-006) y la explicación de 3FN. No escribas migraciones aún.
```
**Verifica:** cada tabla tiene PK; los estados usan `CHECK`; IDs siguen ADR-003; no hay borrado físico en tablas clínicas.

### M0.5 Aprobación (tú, sin CLI)
Lee `DECISIONES.md`, `MER.md` y `MODELO_RELACIONAL.md`. Cambia lo que no te guste. Cambia el estado de `DECISIONES.md` a **APROBADO**.

**Puerta de salida M0:** documentos aprobados; `PROGRESO.md` marca M0 completa. Etiqueta `v0.0`.

---

# FASE M1 — Proyecto base y conexión a Oracle

**Objetivo:** una API vacía que arranca, se conecta a Oracle ATP y aplica migraciones.
**Por qué:** si el entorno falla, todo lo demás falla. Mejor descubrirlo ahora, con cero lógica.

### Prerrequisito manual (lo haces tú)
1. Crea tu cuenta de Oracle Cloud y una **Autonomous Transaction Processing** (opción Always Free).
2. Descarga el **wallet** (Instance Wallet) a una carpeta **fuera del repo** (p. ej. `~/oracle/wallet`).
3. Crea en ATP los **dos usuarios** según ADR-012 (no uses `ADMIN`):
   - **`MEDITRIAJE_OWNER`** (para Flyway / DDL): privilegios `CREATE SESSION`, `CREATE TABLE`, `CREATE SEQUENCE`, `CREATE TRIGGER`, `CREATE INDEX`, cuota en el tablespace.
   - **`MEDITRIAJE_APP`** (para runtime de Spring Boot): privilegios `CREATE SESSION`, `SELECT`, `INSERT`, `UPDATE` sobre las tablas del esquema `MEDITRIAJE_OWNER`. Sin `DELETE` en tablas clínicas, y sin `UPDATE`/`DELETE` en `AUDITORIA` y `ATENCION_ENMIENDA`.
4. Define variables de entorno: `DB_URL` (formato `jdbc:oracle:thin:@<alias>_tp?TNS_ADMIN=/ruta/al/wallet`), `DB_USER` (`MEDITRIAJE_APP`), `DB_PASSWORD`, `FLYWAY_USER` (`MEDITRIAJE_OWNER`), `FLYWAY_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`.

### M1.1 Proyecto Spring Boot base
**Qué:** proyecto Maven (Java 21, Spring Boot 3) en `backend/`, paquetes por capas del documento maestro §8, perfiles `dev`/`test`/`prod`, endpoint `GET /api/v1/ping`.
```
Crea backend/ con Spring Boot 3, Java 21 y Maven. Dependencias: web, validation, security, jdbc, flyway, oracle jdbc, actuator, test. Paquetes: config, controller, dto, exception, mapper, model, repository, security, service, util. Perfiles dev/test/prod por variables de entorno (sin secretos en archivos). Un endpoint GET /api/v1/ping. Sin lógica de negocio.
```
**Verifica:** `mvn clean verify` pasa; `curl localhost:8080/api/v1/ping` responde.

### M1.2 Conexión a Oracle ATP
**Qué:** DataSource con pool (HikariCP) leyendo las variables de entorno, health check que consulta la BD, `.env.example` sin valores reales y `DATABASE.md` con los pasos de configuración.
**Por qué:** documentar el wallet evita perder horas más adelante.
```
Configura la conexión a Oracle ATP vía DB_URL, DB_USER, DB_PASSWORD (variables de entorno) con HikariCP. Añade health check que verifique la conexión. Crea .env.example (sin valores reales) y DATABASE.md con los pasos para configurar el wallet. Nada de secretos en el repo.
```
**Verifica:** `/actuator/health` muestra la BD `UP`; `git grep -i password` no encuentra secretos reales.

### M1.3 Flyway
**Qué:** Flyway apunta a `database/migrations/`, `V001__baseline.sql` mínimo (por ejemplo, una tabla técnica de control).
**Por qué:** confirma que las migraciones corren en ATP antes de crear las tablas reales. Si Flyway no es compatible con tu versión de Oracle, aquí lo sabrás y se cambia a Liquibase (ADR-004).
```
Configura Flyway para leer database/migrations/. Crea V001__baseline.sql con una tabla técnica mínima. Verifica que se aplica en Oracle ATP y documenta el comando de migración en DATABASE.md.
```
**Verifica:** `flyway_schema_history` existe y muestra V001.

### M1.4 Errores, logs y CORS
**Qué:** `ApiError` estructurado (código, mensaje, fecha, id de traza), `@RestControllerAdvice`, excepciones de dominio base, logging sin datos sensibles, CORS desde `CORS_ORIGINS`.
```
Implementa manejo global de errores con una respuesta ApiError consistente (sin stack traces), excepciones de dominio base (RecursoNoEncontrado, DatosInvalidos, AccesoNoAutorizado, CitaNoDisponible), logging que no registre datos personales, y CORS configurado desde CORS_ORIGINS. Agrega pruebas.
```
**Verifica:** un error provocado devuelve JSON estructurado sin stack trace.

### M1.5 Pruebas y CI
**Qué:** una prueba de integración con Testcontainers (Oracle Free) que cree ambos usuarios de BD (`MEDITRIAJE_OWNER` y `MEDITRIAJE_APP`) y un workflow de GitHub Actions que ejecute `mvn verify`.
```
Agrega una prueba de integración con Testcontainers (Oracle Free) que inicialice el contenedor aprovisionando ambos usuarios de BD (MEDITRIAJE_OWNER para ejecutar migraciones Flyway y MEDITRIAJE_APP con privilegios mínimos para el datasource de la aplicación), aplique las migraciones y valide la conectividad. Añade un workflow de GitHub Actions que ejecute mvn clean verify en cada push. Documenta cómo correr las pruebas en README.
```
**Verifica:** Testcontainers levanta Oracle Free, crea ambos usuarios, corre Flyway como `MEDITRIAJE_OWNER`, ejecuta la app como `MEDITRIAJE_APP` con sus `GRANT`s y `mvn clean verify` pasa en local y en CI.
**Puerta de salida M1:** la app arranca, conecta a ATP, migra, `mvn clean verify` pasa en local y en CI. Etiqueta `v0.1`.

---

# FASE M2 — Seguridad base (HU-01, HU-11)

**Objetivo:** registro, login, roles, consentimiento y auditoría.
**Por qué:** todo lo demás cuelga de esto. Hacerla primero evita retrofitear seguridad en endpoints ya escritos.
**Decisiones que aplican:** ADR-002 (cookies HttpOnly, Argon2id), ADR-011 (auditoría), ADR-013 (consentimiento).

### M2.1 Migración de seguridad
**Qué:** `USUARIO`, `ROL`, `USUARIO_ROL`, `REFRESH_TOKEN`, `CONSENTIMIENTO`, `AUDITORIA` y semillas de roles.
```
Crea la migración Flyway con USUARIO, ROL, USUARIO_ROL, REFRESH_TOKEN (guardado hasheado), CONSENTIMIENTO (versión y fecha) y AUDITORIA, exactamente según docs/database/MODELO_RELACIONAL.md. Siembra los roles PACIENTE, PROFESIONAL, ADMINISTRADOR. Prueba que la migración aplica en limpio.
```

### M2.2 Servicio de auditoría
**Qué:** `AuditoriaService` con una sola función: registrar eventos (insert-only). Sin datos clínicos.
```
Implementa AuditoriaService y su repositorio (solo INSERT). Define un enum de acciones auditables. Pruebas: se registra el evento y nunca se guardan campos de contenido clínico. El usuario de BD de la app no debe necesitar UPDATE/DELETE sobre AUDITORIA.
```

### M2.3 Registro de paciente
**Qué:** `POST /auth/register` crea `USUARIO` + `PACIENTE` + `CONSENTIMIENTO` en **una transacción**. Valida documento, correo y contraseña (≥10).
```
Implementa POST /api/v1/auth/register para pacientes: valida entrada, exige consentimiento, crea usuario, paciente y consentimiento en una sola transacción, hashea con Argon2id. Respuesta sin datos sensibles. Tests: éxito, correo duplicado, falta de consentimiento, rollback si falla un paso. Audita el evento.
```

### M2.4 Login, refresh y logout
**Qué:** JWT de 15 min y refresh de 7 días rotativo, ambos en cookies `HttpOnly; Secure; SameSite=Strict`; bloqueo tras 5 fallos; mensaje de error genérico.
```
Implementa login, refresh y logout según ADR-002: access JWT 15 min y refresh opaco rotativo guardado hasheado, en cookies HttpOnly/Secure/SameSite=Strict. Bloqueo temporal tras 5 fallos, error genérico que no revele si el correo existe, logout que invalida el refresh. Audita login exitoso y fallido. Tests de cada caso, incluido token reutilizado tras rotación.
```

### M2.5 Spring Security y autorización por rol
**Qué:** reglas por ruta y rol, protección CSRF (cabecera personalizada), 401 vs 403, `/me`.
```
Configura Spring Security: rutas públicas (register, login, ping), el resto autenticado, y @PreAuthorize por rol. Implementa la protección CSRF de ADR-002. Crea GET /api/v1/patients/me. Tests: sin token → 401; rol incorrecto → 403; token expirado o manipulado → rechazado.
```

### M2.6 Revisión de seguridad de la fase
```
Haz una revisión de seguridad de lo implementado en M2: busca secretos, logs con datos personales, endpoints sin protección, validaciones faltantes. Entrégame un informe en docs/security/REVISION_M2.md y NO corrijas nada sin mi aprobación.
```
**Puerta de salida M2:** HU-01 y HU-11 cumplidas; pruebas de tokens y roles verdes; `docs/api/M2.http` creado. Etiqueta `v0.2`.

**Decisión tuya:** texto del consentimiento (versión 1). Debe ser revisado idealmente por alguien con criterio legal.

---

# FASE M3 — Administración y catálogos (HU-10)

**Objetivo:** que el admin pueda cargar instituciones, especialidades, profesionales y slots.
**Por qué:** sin oferta de atención no hay citas que probar.

### M3.1 Migraciones de oferta
`ESPECIALIDAD`, `INSTITUCION`, `SEDE`, `PROFESIONAL`, `DISPONIBILIDAD_SLOT`.
```
Crea las migraciones de ESPECIALIDAD, INSTITUCION, SEDE, PROFESIONAL y DISPONIBILIDAD_SLOT según MODELO_RELACIONAL.md, con CHECK en estados y modalidad. Prueba que aplican en limpio.
```

### M3.2 CRUD de especialidades, instituciones y sedes
```
Implementa los endpoints admin de especialidades, instituciones y sedes (crear, listar con paginación, editar, desactivar; sin borrado físico). Solo rol ADMINISTRADOR. DTOs, validaciones, auditoría de cambios administrativos y pruebas de autorización.
```

### M3.3 Alta de profesionales
**Qué:** el admin crea un profesional, que genera su `USUARIO` con rol PROFESIONAL y contraseña temporal de un solo uso.
```
Implementa el alta de profesional por el admin: crea USUARIO (rol PROFESIONAL) + PROFESIONAL en una transacción, con contraseña temporal que obligue a cambio en el primer login. Valida que la especialidad exista. Pruebas y auditoría.
```

### M3.4 Generador de slots
**Qué:** a partir de un horario (profesional, sede, fechas, franja, duración) crea slots LIBRES sin solaparse.
```
Implementa el generador de slots del admin: dado profesional, sede, rango de fechas, franja horaria y duración (por defecto 20 min), crea slots LIBRES. Rechaza solapes con slots existentes del mismo profesional. Respeta America/Bogota. Pruebas con bordes (cambio de día, solape parcial).
```

### M3.5 Seeds ficticios
```
Crea database/seeds/ con datos 100% ficticios: 1 institución, 2 sedes, 4 especialidades, 4 profesionales y slots de los próximos 14 días. Un script para cargarlos solo en dev. Documenta que no deben usarse en prod.
```
**Puerta de salida M3:** admin crea oferta y slots; paciente/profesional reciben 403 en endpoints admin. `docs/api/M3.http`. Etiqueta `v0.3`.

---

# FASE M4 — Disponibilidad y citas (HU-03, 04, 05, 06)

**Objetivo:** buscar, reservar y cancelar sin doble reserva.
**Por qué:** es el núcleo transaccional y el punto técnicamente más delicado: la concurrencia.

### M4.1 Migración de citas
**Qué:** `CITA` con estado, `cita_origen_id`, `triaje_id` (nullable) y el **índice único funcional** de ADR-006.
```
Crea la migración de CITA con CHECK de estados, FKs, y el índice único funcional uq_cita_slot_activa del ADR-006. Escribe una prueba de integración que demuestre que la BD rechaza dos citas activas sobre el mismo slot aunque la app no lo valide.
```

### M4.2 Consulta de disponibilidad
```
Implementa GET /api/v1/availability con filtros por especialidad, fecha, sede y modalidad. Solo slots LIBRES y futuros, paginado. No inventes disponibilidad. Pruebas con filtros combinados y sin resultados.
```

### M4.3 Reservar cita
```
Implementa POST /api/v1/appointments en una transacción: valida que el slot esté LIBRE (UPDATE ... WHERE estado='LIBRE'), crea la cita PROGRAMADA y audita. Si el UPDATE afecta 0 filas lanza CitaNoDisponible. Verifica que el profesional corresponde a la especialidad.
```

### M4.4 Prueba de concurrencia
```
Escribe una prueba que lance al menos 10 hilos reservando el MISMO slot a la vez: exactamente una debe tener éxito y el resto CitaNoDisponible. La prueba debe ser determinista y correr en CI.
```
**Verifica tú:** ejecútala varias veces seguidas. Si falla una sola vez, no cierres la tarea.

### M4.5 Cancelación y estados
```
Implementa una máquina de estados de cita con las transiciones del ADR-006 (clase propia con pruebas). Implementa PATCH /appointments/{id}/cancel: el paciente solo la suya y hasta 2 h antes; libera el slot; audita. Pruebas de transición inválida, cancelación tardía y cancelar cita ajena.
```

### M4.6 Agenda del profesional
```
Implementa GET /api/v1/professionals/me/agenda: el profesional ve únicamente sus citas, filtrables por fecha. Pruebas de que no ve las de otro profesional.
```
**Puerta de salida M4:** HU-03..06 cumplidas, concurrencia verde varias veces, `docs/api/M4.http`. Etiqueta `v0.4`.

---

# FASE M5 — Triaje (HU-02)

**Objetivo:** orientar al paciente con reglas simples y un corte de emergencia.
**Por qué:** es la parte de mayor riesgo clínico/ético, por eso se aísla y se hace testeable sin BD.
**Importante:** las reglas son **de prototipo**. No se presentan como validadas clínicamente.

### M5.1 Migraciones y semillas del triaje
```
Crea la migración de SINTOMA, REGLA_TRIAJE (versionada, con bandera es_alarma), TRIAJE y TRIAJE_SINTOMA. Siembra ≈20 síntomas y reglas de PROTOTIPO, con un comentario visible que diga que no están validadas clínicamente. No inventes tiempos de espera oficiales: déjalos marcados como PENDIENTE.
```
**Decisión tuya:** revisa la lista de síntomas y reglas; lo ideal es que las valide alguien del área de salud.

### M5.2 Motor de reglas (puro)
**Qué:** una clase/interfaz `MotorTriaje` que recibe síntomas, duración e intensidad y devuelve nivel I–V + ruta. **Sin acceso a BD ni a HTTP.**
**Por qué:** así se prueba con tablas de casos, y se puede cambiar la estrategia sin tocar nada más (Strategy / Protected Variations).
```
Implementa MotorTriaje como interfaz con una implementación basada en reglas cargadas desde REGLA_TRIAJE. Debe ser puro y determinista (sin BD ni HTTP en la lógica). Entrada: síntomas, duración, intensidad. Salida: nivel I–V, ruta sugerida, versión de reglas, bandera de emergencia. Pruebas unitarias con tabla de casos.
```

### M5.3 Corte de emergencia
```
Si algún síntoma es de alarma, el resultado debe ser EMERGENCIA: mensaje de acudir a urgencias o llamar al 123, sin ruta de cita. Prueba exhaustiva: CADA síntoma marcado como alarma debe producir emergencia, sin excepción. Audita el evento.
```

### M5.4 Endpoints de triaje
```
Implementa POST /api/v1/triage y GET /api/v1/triage/{id}: guarda síntomas, resultado y versión de reglas; el paciente solo ve sus triajes. La respuesta incluye el aviso de que no sustituye valoración profesional. El POST de cita puede recibir triaje_id opcional (valida que sea del mismo paciente).
```
**Puerta de salida M5:** casos de prueba verdes, emergencia infalible, `docs/api/M5.http`. Etiqueta `v0.5`.

---

# FASE M6 — Atención e historia clínica (HU-07, HU-09)

**Objetivo:** registrar atenciones inmutables con acceso estrictamente controlado.
**Por qué:** es el módulo más sensible. Por eso las pruebas de acceso se escriben **antes** que los endpoints.

### M6.1 Migraciones clínicas
```
Crea la migración de ATENCION, SIGNO_VITAL, ATENCION_ENMIENDA, DIAGNOSTICO_CIE10 (catálogo reducido ficticio-seguro) y ALERGIA. Añade el trigger que bloquea UPDATE sobre atenciones cerradas (ADR-008) y una prueba que demuestre el bloqueo. El usuario de la app no debe tener DELETE sobre tablas clínicas.
```

### M6.2 AccesoClinicoService (primero las pruebas)
```
Implementa AccesoClinicoService según ADR-007 (relación asistencial: cita activa futura o atención propia en los últimos 12 meses, configurable). Escribe PRIMERO las pruebas: paciente dueño sí; otro paciente no; profesional con relación sí; profesional sin relación no; admin nunca. Luego la implementación.
```

### M6.3 Crear y cerrar atención
```
Implementa POST /attentions y POST /attentions/{id}/close: solo el profesional con relación asistencial; guarda motivo, evolución, signos vitales, diagnóstico CIE-10 e indicaciones; al cerrar pasa la cita a ATENDIDA y la atención queda inmutable. Audita acceso y creación. Sin datos clínicos en logs.
```

### M6.4 Enmiendas
```
Implementa POST /attentions/{id}/amendments: añade una enmienda (quién, cuándo, qué, motivo) sin modificar la atención original. Pruebas: no se puede editar una atención cerrada por ninguna vía; la enmienda queda ligada y auditada.
```

### M6.5 Historia del paciente (lectura)
```
Implementa GET /patients/me/history: el paciente ve sus atenciones y enmiendas, solo lectura, paginado, sin exponer IDs internos. Pruebas de acceso cruzado: manipular el ID de otro paciente debe dar 403/404.
```
**Puerta de salida M6:** todas las pruebas de acceso cruzado verdes; admin recibe 403 en toda ruta clínica. `docs/api/M6.http`. Etiqueta `v0.6`.

---

# FASE M7 — Recetas (HU-08)

**Objetivo:** recetas atómicas con historial estable.
**Por qué:** si cambia el catálogo de medicamentos, una receta pasada no debe cambiar (snapshot).

### M7.1 Migración
```
Crea la migración de MEDICAMENTO (código, nombre, principio activo, presentación, estado), RECETA y RECETA_DETALLE (con copia snapshot del nombre y presentación). Siembra ≈15 medicamentos comunes (nombres genéricos). No incluyas dosis recomendadas por el sistema.
```

### M7.2 Crear receta
```
Implementa POST /prescriptions: solo el profesional responsable de la atención. Receta y detalles en UNA transacción. El detalle copia nombre y presentación del medicamento. El sistema NO sugiere dosis; son texto libre del profesional. Pruebas: éxito, rollback si falla un detalle, profesional ajeno rechazado.
```

### M7.3 Consulta del paciente
```
Implementa GET /patients/me/prescriptions (solo lectura, paginado). Prueba que cambiar el nombre del medicamento en el catálogo NO altera recetas ya emitidas, y que otro paciente no puede verlas.
```
**Puerta de salida M7:** HU-08 y HU-09 completas. Etiqueta `v0.7`.

---

# FASE M8 — Frontend, integración y cierre

**Objetivo:** que todo lo anterior se use desde el navegador y quede listo para demo.
**Por qué al final:** la API ya está probada; el frontend solo la consume. Recuerda que la seguridad no depende de ocultar botones.

### M8.0 Sistema de diseño (antes que cualquier pantalla)
**Qué:** componentes base y una página `styleguide.html` sobre los tokens de `frontend/css/tokens.css`. Guía completa en `docs/DISENO_UI_UX.md`.
**Por qué:** si primero se define cómo se ve cada botón, campo y tarjeta, las 20 pantallas salen consistentes. Corregirlo después cuesta mucho más.
```
Usa la skill meditriaje-ui-design y la skill frontend-design si está disponible. Lee docs/DISENO_UI_UX.md y frontend/css/tokens.css (no cambies los tokens sin avisarme).
Crea frontend/css/base.css y frontend/css/components.css con: botones (4 variantes + estado de carga), campos de formulario (label, ayuda, error), tarjetas, badges de cita y de triaje, alertas, toast, modal de confirmación, skeleton, estado vacío, navegación (superior, lateral, inferior móvil) y tabla responsive. Solo var(--...) de tokens; mobile-first; accesible.
Crea frontend/styleguide.html que muestre TODOS los componentes y estados, en tema claro y oscuro. Íconos SVG inline estilo Lucide en frontend/assets/icons/. Sin CDN ni fuentes externas.
```
**Verifica:** abre `styleguide.html` en el navegador y en tu celular; revisa contraste, foco con teclado y tema oscuro **antes de seguir**.

### M8.1 Base del frontend
```
Crea frontend/ con HTML, CSS y JS vanilla (módulos ES, sin build): index.html, navegación simple, cliente API (fetch con credentials: 'include' y la cabecera CSRF), manejo central de errores, y componentes de estados de carga, vacío y error. Responsive y semántico. Sin localStorage para tokens.
```

### M8.2 Pantallas de paciente
Hazlo en tres tareas separadas (cada una un commit):
- **M8.2a** registro/login y dashboard.
- **M8.2b** triaje + resultado + pantalla de emergencia, disponibilidad y reserva.
- **M8.2c** mis citas (cancelar), historia y recetas.
```
Implementa las pantallas del paciente indicadas ({M8.2a/b/c}) consumiendo la API existente. Validación de formularios en el cliente (UX) sin sustituir la del servidor. La pantalla de emergencia debe ser muy visible y sin botón de agendar. Accesibilidad básica (labels, foco, contraste).
```

### M8.3 Pantallas de profesional
```
Implementa: agenda del día, formulario de atención (signos vitales, evolución, diagnóstico, indicaciones), cierre y creación de receta. Confirmación antes de cerrar una atención (es irreversible).
```

### M8.4 Pantallas de administración
```
Implementa: CRUD de instituciones, sedes, especialidades, profesionales y generador de slots. Ninguna pantalla muestra contenido clínico.
```

### M8.5 Endurecimiento
```
Haz una revisión final de seguridad: cabeceras (CSP, X-Content-Type-Options, etc.), cookies, CORS, rate limit en login, logs, secretos, dependencias vulnerables (mvn dependency-check o equivalente). Entrégame un informe en docs/security/REVISION_FINAL.md con hallazgos priorizados. No corrijas sin mi aprobación.
```

### M8.6 Documentación final y demo
```
Completa README.md, ARCHITECTURE.md, API.md, DATABASE.md, SECURITY.md y CHANGELOG.md con lo realmente implementado (no lo planeado). Prepara el script de demo de docs/MVP.md §8 con datos ficticios.
```

**Puerta de salida M8 = MVP terminado:** checklist de `docs/MVP.md` §7 completo, demo ejecutable de principio a fin, sin secretos en el repo, `v1.0-mvp`.

---

# FASE 2 — Extensiones y Robustecimiento

## Módulo F2.1 — MFA para Profesionales y Recuperación de Contraseña

### F2.1.1 Migración de base de datos V010
```
Crea database/migrations/V010__mfa_y_recuperacion_password.sql:
- Añadir a USUARIO: MFA_HABILITADO NUMBER(1) DEFAULT 0 NOT NULL, MFA_SECRET VARCHAR2(128) NULL, MFA_CONFIGURADO_AT TIMESTAMP WITH TIME ZONE NULL.
- Tabla CODIGO_VERIFICACION: ID, PUBLIC_ID, USUARIO_ID (FK), TIPO (VARCHAR2(30)), CODIGO_HASH (VARCHAR2(64)), FECHA_EXPIRACION, INTENTOS_FALLIDOS (default 0), MAX_INTENTOS (default 3), USADO (0/1), CREATED_AT.
- Tabla MFA_BACKUP_CODE: ID, USUARIO_ID (FK), CODE_HASH (VARCHAR2(64)), USADO (0/1), USADO_AT.
- GRANTs mínimos a MEDITRIAJE_APP (ADR-012).
```

### F2.1.2 Servicio de correo y plantilla HTML institucional
```
Implementa EmailService e EmailTemplateService:
- Plantilla HTML responsiva en resources/templates/email/recuperacion-password.html (estilo corporativo con tokens de color de MediTriaje 2.0, caja de código de 6 dígitos en fuente destacada, aviso de expiración en 15 min y advertencia de seguridad).
- Configuración en application.yml con modo dev/test (log en consola de correo formateado) y modo prod (JavaMailSender SMTP).
```

### F2.1.3 Endpoints y lógica de recuperación de contraseña
```
Implementa en AuthService y AuthController:
- POST /api/v1/auth/forgot-password: genera código numérico de 6 dígitos (SecureRandom), guarda hash SHA-256 en BD con expiración a 15 min, renderiza plantilla y envía correo. Respuesta genérica 200 OK (sin enumeración).
- POST /api/v1/auth/reset-password: valida email, código y contraseña (>=10 caracteres). Máximo 3 intentos fallidos por código. Actualiza password con Argon2id, marca código como USADO, revoca masivamente todos los refresh tokens previos de la cuenta y audita. Pruebas completas.
```

### F2.1.4 Autenticación Multifactor (MFA TOTP) en Backend
```
Implementa servicio TOTP RFC 6238 (paso 30s, HMAC-SHA1, Base32):
- POST /api/v1/auth/mfa/setup: genera secreto y URI otpauth://.
- POST /api/v1/auth/mfa/verify: verifica primer código, activa MFA en USUARIO y devuelve 8 códigos de respaldo uniuso hasheados en BD.
- Actualiza POST /api/v1/auth/login: si usuario tiene MFA activo, responde mfaRequerido: true y mfaChallengeToken temporal (5 min).
- POST /api/v1/auth/mfa/authenticate: valida código TOTP o código de respaldo contra el challenge y emite cookies definitivas access_token y refresh_token.
```

### F2.1.5 Pantallas en Frontend (Login, Recuperación y Enrolamiento MFA)
```
Actualiza el frontend Vanilla:
- Modal / vista "¿Olvidaste tu contraseña?" en login con flujo en 2 pasos: ingreso de correo -> ingreso de código y nueva clave con validación en cliente.
- Desafío MFA en login cuando la API responda mfaRequerido: true.
- Enrolamiento y visualización de códigos de respaldo en el panel del profesional asistencial.
```

### F2.1.6 Pruebas, colección HTTP y cierre F2.1
```
Colección docs/api/F2.1.http, pruebas de integración y E2E Playwright. Puerta de salida F2.1: etiqueta v1.1-mfa.
```

---

## Módulo F2.2 — Seguimiento Post-Atención y Recordatorios por Correo

### F2.2.1 Migración de base de datos V011 (Seguimiento post-atención y recordatorios)
```
Crea database/migrations/V011__seguimiento_post_atencion.sql:
- Tabla SEGUIMIENTO_POST_ATENCION: ID, PUBLIC_ID, ATENCION_ID (FK), PACIENTE_ID (FK), PROFESIONAL_ID (FK), TIPO (CHECK: CONTROL_MEDICO, EVOLUCION_SINTOMAS, EXAMEN_PENDIENTE, ADHERENCIA_TRATAMIENTO), INDICACIONES, FECHA_SUGERIDA_CONTROL, ESTADO (CHECK: PENDIENTE, COMPLETADO, CANCELADO), FECHA_RESPUESTA_PACIENTE, REPORTE_PACIENTE, CREATED_AT, UPDATED_AT.
- Tabla RECORDATORIO_CITA: ID, PUBLIC_ID, CITA_ID (FK), PACIENTE_ID (FK), TIPO (CHECK: CONFIRMACION_RESERVA, RECORDATORIO_PREVIO, CANCELACION), CANAL (CHECK: EMAIL), DESTINATARIO, ESTADO_ENVIO (CHECK: ENVIADO, FALLIDO, PENDIENTE), ERROR_MENSAJE, ENVIADO_AT.
- GRANTs mínimos a MEDITRIAJE_APP (ADR-012).
```

### F2.2.2 Plantillas HTML y notificaciones por correo de citas y atención
```
Implementa plantillas y despacho de notificaciones:
- Plantillas HTML responsivas en resources/templates/email/: confirmacion-cita.html, cancelacion-cita.html, resumen-atencion-seguimiento.html.
- Integración en AppointmentService (reserva y cancelación) y ClinicalAttentionService (cierre de atención) con desacoplamiento y tolerancia a fallos SMTP.
```

### F2.2.3 Lógica de dominio y endpoints de seguimiento post-atención
```
Implementa servicio FollowUpService y endpoints:
- POST /api/v1/attentions/{publicId}/follow-ups: creación de tarea de seguimiento por profesional con relación asistencial o autor.
- GET /api/v1/patients/me/follow-ups: consulta paginada de tareas de seguimiento del paciente.
- POST /api/v1/patients/me/follow-ups/{publicId}/report: registro de reporte de evolución por el paciente (sin diagnóstico automático, §5.16).
- Aislamiento estricto de roles (403 para administradores y pacientes ajenos). Pruebas unitarias y MockMvc.
```

### F2.2.4 Pantallas en Frontend (Seguimiento del paciente y revisión médica)
```
Actualiza el frontend Vanilla:
- Sección de seguimientos y controles en la vista del paciente con formulario interactivo para reporte de evolución.
- Visualización de seguimientos y reportes del paciente en la ficha médica del profesional.
```

### F2.2.5 Pruebas, colección HTTP y cierre F2.2
```
Colección docs/api/F2.2.http, pruebas completas de integración y verificación. Puerta de salida F2.2: etiqueta v1.2-seguimiento.
```

---

## Módulo F2.3 — Resumen de Salud y Acceso por Código QR Temporal

### F2.3.1 Migración de base de datos V012 (Acceso temporal QR)
```
Crea database/migrations/V012__acceso_temporal_qr.sql:
- Tabla ACCESO_TEMPORAL_QR: ID, PUBLIC_ID, PACIENTE_ID (FK), TOKEN_HASH (VARCHAR2(64), UNIQUE), PIN_HASH (VARCHAR2(100), NULLABLE), INCLUIR_ALERGIAS (NUMBER(1) DEFAULT 1), INCLUIR_MEDICAMENTOS (NUMBER(1) DEFAULT 1), INCLUIR_ATENCIONES (NUMBER(1) DEFAULT 1), INCLUIR_CONTACTO (NUMBER(1) DEFAULT 1), MAX_ACCESOS (NUMBER(3) DEFAULT 3), ACCESOS_REALIZADOS (NUMBER(3) DEFAULT 0), REVOCADO (NUMBER(1) DEFAULT 0), EXPIRA_AT (TIMESTAMP WITH TIME ZONE), CREATED_AT, UPDATED_AT.
- Índices IX_ACCESO_QR_TOKEN y IX_ACCESO_QR_PACIENTE.
- Constraints CHECK para flags booleanos y rangos.
- GRANTs mínimos a MEDITRIAJE_APP (ADR-012).
```

### F2.3.2 Repositorio y lógica de tokens temporales criptográficos
```
Implementa repositorio y servicio de tokens de emergencia:
- Generación de token criptográfico de 256 bits (SecureRandom Base64Url).
- Hash SHA-256 en reposo (TokenHashUtil).
- Hashing de PIN opcional con Argon2id / PasswordEncoder.
- Control de vigencia (15 minutos), límite de 3 accesos, incremento atómico y revocación por el paciente.
- Registro inmutable de auditoría para GENERACION_QR_EMERGENCIA, REVOCACION_QR_EMERGENCIA y ACCESO_EMERGENCIA_QR.
```

### F2.3.3 Servicio de agregación de resumen clínico de salud
```
Implementa EmergencySummaryService:
- Agrega datos esenciales del paciente (datos básicos, documento, edad).
- Agrega alergias registradas del paciente (ALERGIA).
- Agrega medicamentos activos de recetas vigentes (RECETA y RECETA_DETALLE).
- Agrega antecedentes clínicos relevantes / últimas atenciones (ATENCION con CIE-10).
- Aplica filtros según los flags de alcance autorizados por el paciente.
```

### F2.3.4 Endpoints REST de resumen y QR de emergencia
```
Implementa controladores REST:
- POST /api/v1/patients/me/emergency-qr: generación de QR por el paciente con alcance y PIN opcional.
- GET /api/v1/patients/me/emergency-qr: listado de tokens generados y estado (ACTIVO, EXPIRADO, AGOTADO, REVOCADO).
- PATCH /api/v1/patients/me/emergency-qr/{publicId}/revoke: revocación inmediata por el paciente.
- GET /api/v1/emergency-summary/{token}/check: verificación pública de vigencia del token y si requiere PIN.
- POST /api/v1/emergency-summary/{token}: lectura pública del resumen con validación de PIN opcional, incremento de accesos y auditoría.
- Pruebas unitarias y MockMvc con seguridad.
```

### F2.3.5 Pantallas en Frontend (Generador en portal del paciente y visor público)
```
Actualiza el frontend Vanilla:
- Nueva vista / tarjeta en el portal del paciente para generar y administrar QRs de emergencia (alcance configurable, PIN opcional, contador de 15 min en vivo, botón de revocación y renderizado visual SVG del código QR).
- Vista pública accesible #/emergency-summary/:token con formulario para PIN si aplica y visualización clínica de grado médico.
```

### F2.3.6 Pruebas, colección HTTP y cierre F2.3
```
Colección docs/api/F2.3.http, pruebas completas de integración y verificación. Puerta de salida F2.3: etiqueta v1.3-resumen-qr.
```

---

## Módulo F2.4 — Dispensación y Reclamación Farmacéutica de Recetas (ADR-016)

### F2.4.1 Migración de base de datos V013 (Dispensación de Farmacia y Rol)
```
Crea database/migrations/V013__dispensacion_farmacia.sql:
- Semilla del rol ROLE_FARMACEUTICO en la tabla ROL (si no existe).
- Tabla DISPENSACION: ID, PUBLIC_ID, RECETA_ID (FK a RECETA), SEDE_ID (FK a SEDE), USUARIO_ID (FK a USUARIO), OBSERVACIONES, CREATED_AT.
- Tabla DISPENSACION_DETALLE: ID, DISPENSACION_ID (FK a DISPENSACION), RECETA_DETALLE_ID (FK a RECETA_DETALLE), CANTIDAD_ENTREGADA (NUMBER > 0), LOTE (VARCHAR2(50)), FECHA_VENCIMIENTO_LOTE (DATE), CREATED_AT.
- Triggers TR_DISPENSACION_INMUTABILIDAD y TR_DISP_DETALLE_INMUTABILIDAD (bloquean UPDATE y DELETE).
- Índices relacionales y GRANTs mínimos a MEDITRIAJE_APP (ADR-012).
```

### F2.4.2 Modelos de dominio y repositorios JDBC
```
Implementa en el backend:
- Modelos inmutables: Dispensacion, DispensacionDetalle, EstadoRecetaDispensacion.
- DTOs: RegistrarDispensacionRequest, DetalleEntregaRequest, DispensacionResponse, RecetaDispensacionResponse, SaldoMedicamentoDto.
- Repositorio DispensacionRepository con JdbcTemplate y SQL 100% parametrizado.
- Consultas de saldo acumulado entregado vs. prescrito.
```

### F2.4.3 Servicio de dispensación farmacéutica y reglas de negocio
```
Implementa DispensationService:
- Validación de vigencia de la receta (rechazar si expiró).
- Validación de rol del dispensador (ROLE_FARMACEUTICO).
- Validación estricta de saldo disponible por ítem prescrito (prohibido sobre-dispensar).
- Cálculo dinámico de estado de entrega de la receta (PENDIENTE, DISPENSADA_PARCIAL, DISPENSADA_TOTAL).
- Persistencia atómica de dispensación y detalles.
- Auditoría inmutable obligatoria DISPENSACION_RECETA vía AuditoriaService.
```

### F2.4.4 Controladores REST para farmacia y portal del paciente
```
Implementa endpoints:
- POST /api/v1/pharmacy/dispensations: registrar dispensación (ROLE_FARMACEUTICO).
- GET /api/v1/pharmacy/prescriptions: búsqueda de recetas por código de reclamación o documento del paciente (ROLE_FARMACEUTICO).
- GET /api/v1/pharmacy/prescriptions/{publicId}: detalle de receta con saldos pendientes para dispensación.
- GET /api/v1/patients/me/prescriptions/{publicId}/dispensation: consulta del paciente sobre el estado de entrega y código de reclamación (ROLE_PACIENTE).
- Pruebas unitarias y MockMvc con seguridad y aislamiento (403 para otros roles).
```

### F2.4.5 Pantallas en Frontend (Ventanilla de Farmacia y Reclamación del Paciente)
```
Actualiza el frontend Vanilla:
- Nueva vista de farmacia #/pharmacy/dispensation: buscador de receta por código/documento, visualización de medicamentos, campos para cantidad a entregar y lote INVIMA, y confirmación de entrega con recibo/comprobante.
- Actualización de #/patient/prescriptions: visualización del estado de dispensación por medicamento (badge de reclamado/pendiente/parcial) y código de reclamación alfanumérico / QR para ventanilla.
```

### F2.4.6 Pruebas, colección HTTP y cierre F2.4
```
Colección docs/api/F2.4.http, pruebas completas de integración y verificación. Puerta de salida F2.4: etiqueta v1.4-dispensacion.
```

---

## Módulo F2.5 — Acceso Clínico de Emergencia (Break-Glass)

### F2.5.1 ADR-017 y Migración V014 (Acceso Break-Glass)
```
Crea database/migrations/V014__acceso_break_glass.sql:
- Tabla ACCESO_BREAK_GLASS: ID, PUBLIC_ID, PROFESIONAL_ID (FK), PACIENTE_ID (FK), MOTIVO (VARCHAR2(500)), FECHA_EXPIRACION (TIMESTAMP WITH TIME ZONE), CREATED_AT.
- Trigger TR_BREAK_GLASS_INMUTABILIDAD que bloquea UPDATE y DELETE.
- Índices IX_BREAK_GLASS_PROF_PAC y IX_BREAK_GLASS_EXPIRACION.
- GRANTs mínimos a MEDITRIAJE_APP (SELECT, INSERT).
- Registro de acción auditable ACCESO_BREAK_GLASS en AccionAuditable.
```

### F2.5.2 Modelos de Dominio, DTOs y Repositorio BreakGlassRepository
```
Implementa:
- Record inmutable AccesoBreakGlass.
- DTOs ActivarBreakGlassRequest y AccesoBreakGlassResponse.
- BreakGlassRepository con JdbcTemplate y SQL parametrizado: registrarAcceso, existeAccesoActivo(profesionalId, pacienteId, ahora), listarActivosPorProfesional.
```

### F2.5.3 Integración con AccesoClinicoService y BreakGlassService
```
Implementa BreakGlassService y conecta con AccesoClinicoService:
- Verificación en AccesoClinicoService: si no hay cita futura ni atención propia en 12 meses, verificar si existe un acceso break-glass activo no expirado.
- BreakGlassService: validación de profesional asistencial, existencia del paciente, justificación obligatoria (mínimo 20 caracteres), cálculo de vigencia (24 horas) y auditoría reforzada ACCESO_BREAK_GLASS.
```

### F2.5.4 Endpoints REST de Break-Glass e Historia Clínica para Profesionales
```
Implementa controladores REST:
- POST /api/v1/clinical/break-glass: activación de emergencia por profesional (201 Created).
- GET /api/v1/clinical/break-glass/active: listado de accesos de emergencia vigentes del profesional.
- GET /api/v1/clinical/patients/{publicId}/history: consulta de historia clínica del paciente por profesional con relación asistencial activa o break-glass vigente (200 OK con atenciones, signos vitales y enmiendas).
- Control de seguridad: 403 Forbidden para administradores y pacientes; 401 Unauthorized sin sesión; 400 Bad Request ante datos inválidos.
```

### F2.5.5 Frontend: Modal de Justificación Legal y Consulta Asistencial Excepcional
```
Actualiza el frontend Vanilla:
- Modal de advertencia legal/ética con confirmación y campo de justificación de urgencia (mínimo 20 caracteres) en la vista profesional.
- Indicador visual claro (badge/alerta "Acceso de Emergencia Activo") en la vista del paciente.
- Navegación e integración en router.js y api.js.
```

### F2.5.6 Pruebas, Colección HTTP y Cierre F2.5
```
Colección docs/api/F2.5.http, pruebas completas de integración y verificación. Puerta de salida F2.5: etiqueta v1.5-break-glass.
```

---

## Módulo F2.6 — Asistente del Sistema y Reportes Administrativos (RF-27, RF-30)

### F2.6.1 ADR-018: Reportes Operativos Administrativos y Asistente del Sistema
```
Formaliza ADR-018 en docs/DECISIONES.md y estructura el alcance en docs/PLAN_DE_TRABAJO.md.
```

### F2.6.2 Repositorio de Métricas y Servicio de Reportes Operativos
```
Implementa en backend:
- DTOs en com.meditriaje.dto.report: ResumenOperativoResponse, MetricasCitasDto, MetricasTriajeDto, MetricasFarmaciaDto, MetricasBreakGlassDto, DistribucionItemDto.
- ReporteRepository con JdbcTemplate y SQL parametrizado de agregación matemática (COUNT, GROUP BY) sobre CITA, TRIAJE, RECETA, DISPENSACION, ACCESO_BREAK_GLASS, ESPECIALIDAD, SEDE.
- ReporteService: consolidación analítica, cálculo de tasas (inasistencia, cancelación, emergencias) y filtro por ventana temporal en zona horaria America/Bogota.
```

### F2.6.3 Servicio y Motor del Asistente del Sistema
```
Implementa en backend:
- DTOs en com.meditriaje.dto.assistant: PreguntaAsistenteRequest, RespuestaAsistenteResponse, SugerenciaAccionDto.
- AssistantService: motor de orientación determinista con base de conocimiento clínica/asistencial estructurada.
- Detección prioritaria de emergencias vitales (alerta inmediata 123 y orientación de urgencias).
- Procesamiento de tópicos de plataforma (triaje I-V, agendamiento de citas, reclamación de medicamentos, QR de emergencia, inmutabilidad).
- Enlaces y sugerencias interactivas hacia rutas SPA, disclaimer médico no negociable.
```

### F2.6.4 Controladores REST para Reportes y Asistente
```
Implementa controladores REST:
- AdminReportController en /api/v1/admin/reports (@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")):
  - GET /operational: resumen integral de métricas operativas.
  - GET /appointments: estadísticas específicas de agendamiento y estados.
  - GET /triage: distribución y tasas de triaje y emergencias.
  - Seguridad: 403 Forbidden para pacientes y profesionales.
- AssistantController en /api/v1/assistant:
  - POST /chat: consulta interactiva con el asistente para usuarios y pacientes.
```

### F2.6.5 Frontend: Dashboard Analítico de Reportes y Widget Flotante del Asistente
```
Actualiza el frontend Vanilla:
- Vista de reportes administrativos en frontend/js/views/admin-reports.js (#/admin/reports) integrada al dashboard de administración con tarjetas KPI, barras de distribución y filtro por fechas.
- Widget interactivo del asistente frontend/js/views/system-assistant-widget.js accesible globalmente desde un botón flotante con conversación, atajos frecuentes y detección visual de urgencias.
- Integración en router.js, admin-views.js y app.js.
```

### F2.6.6 Pruebas, Colección HTTP y Cierre F2.6
```
Colección docs/api/F2.6.http, pruebas completas de integración y verificación. Puerta de salida F2.6: etiqueta v1.6-asistente-reportes.
```

---

# Otras iniciativas de Fase 2 (orden sugerido)
1. Integraciones externas y mejoras analíticas adicionales.

---

# Checklist rápido que usas en CADA tarea
- [ ] El plan de la CLI era razonable y lo aprobé.
- [ ] El diff solo toca lo de la tarea.
- [ ] Corrí las pruebas yo mismo y pasan.
- [ ] Hay prueba de autorización si la tarea toca datos de personas.
- [ ] Sin secretos ni datos reales; sin datos clínicos en logs.
- [ ] Migración versionada si cambió la BD.
- [ ] `PROGRESO.md` actualizado y commit con mensaje claro.
