# PROGRESO — MediTriaje 2.0

> La CLI no recuerda sesiones anteriores. Este archivo es su memoria.
> **Al empezar una sesión:** léelo. **Al terminar cada tarea:** actualízalo (marca la tarea, anota decisiones y pendientes, agrega una línea a la bitácora).

## Estado actual
- **Fase actual:** M1
- **Tarea actual:** M1.5 (Pruebas y CI)
- **Última etiqueta:** v0.0
- **Rama de trabajo:** feature/m1-base-oracle

## Tareas
Leyenda: `[ ]` pendiente · `[~]` en curso · `[x]` hecha

### M0 — Cimientos de diseño
- [x] M0.1 Repo y estructura
- [x] M0.2 Casos de uso y reglas de negocio
- [x] M0.3 Modelo de dominio y MER
- [x] M0.4 Modelo relacional y normalización
- [x] M0.5 Aprobación (manual)

### M1 — Proyecto base y Oracle
- [ ] Prerrequisito manual: ATP, wallet, usuario, variables de entorno
- [x] M1.1 Proyecto Spring Boot base
- [x] M1.2 Conexión a Oracle ATP
- [x] M1.3 Flyway
- [x] M1.4 Errores, logs y CORS
- [ ] M1.5 Pruebas y CI

### M2 — Seguridad base
- [ ] M2.1 Migración de seguridad
- [ ] M2.2 Servicio de auditoría
- [ ] M2.3 Registro de paciente
- [ ] M2.4 Login, refresh y logout
- [ ] M2.5 Spring Security y roles
- [ ] M2.6 Revisión de seguridad

### M3 — Administración y catálogos
- [ ] M3.1 Migraciones de oferta
- [ ] M3.2 CRUD especialidades, instituciones, sedes
- [ ] M3.3 Alta de profesionales
- [ ] M3.4 Generador de slots
- [ ] M3.5 Seeds ficticios

### M4 — Disponibilidad y citas
- [ ] M4.1 Migración de citas
- [ ] M4.2 Consulta de disponibilidad
- [ ] M4.3 Reservar cita
- [ ] M4.4 Prueba de concurrencia
- [ ] M4.5 Cancelación y estados
- [ ] M4.6 Agenda del profesional

### M5 — Triaje
- [ ] M5.1 Migraciones y semillas
- [ ] M5.2 Motor de reglas
- [ ] M5.3 Corte de emergencia
- [ ] M5.4 Endpoints de triaje

### M6 — Atención e historia clínica
- [ ] M6.1 Migraciones clínicas
- [ ] M6.2 AccesoClinicoService
- [ ] M6.3 Crear y cerrar atención
- [ ] M6.4 Enmiendas
- [ ] M6.5 Historia del paciente

### M7 — Recetas
- [ ] M7.1 Migración
- [ ] M7.2 Crear receta
- [ ] M7.3 Consulta del paciente

### M8 — Frontend y cierre
- [ ] M8.1 Base del frontend
- [ ] M8.2a Paciente: registro, login, dashboard
- [ ] M8.2b Paciente: triaje, disponibilidad, reserva
- [ ] M8.2c Paciente: citas, historia, recetas
- [ ] M8.3 Pantallas de profesional
- [ ] M8.4 Pantallas de administración
- [ ] M8.5 Endurecimiento
- [ ] M8.6 Documentación final y demo

## Decisiones tomadas durante el desarrollo
(Fecha · decisión · motivo · ADR afectado)
- 2026-10-01 · Aprobación formal de decisiones de arquitectura ADR-001 a ADR-013, Casos de Uso, Reglas de Negocio, MER y Modelo Relacional · Cierre exitoso de Fase M0 · Todos los ADRs
- 2026-10-01 · Ajustes finales aprobados de M0: ES_ALARMA exclusivamente en SINTOMA con corte de emergencia (alarma O Nivel I) y default conservador Nivel III; EVOLUCION en VARCHAR2(4000 CHAR) con validación DTO @Size(max=4000) por MAX_STRING_SIZE; segregación dual de usuarios DB (MEDITRIAJE_OWNER y MEDITRIAJE_APP); SIGNO_VITAL trigger bloquea INSERT en atención CERRADA; RECETA emitida sobre atención CERRADA con inmutabilidad desde INSERT; coherencia CITA-TRIAJE por clave foránea compuesta UQ(ID, PACIENTE_ID) y FK(TRIAJE_ID, PACIENTE_ID); TIME_ZONE configurado en connectionInitSql de HikariCP · Robustez técnica y seguridad relacional en Oracle ATP · ADR-005, ADR-008, ADR-009, ADR-012

## Pendientes y dudas abiertas
(Todo lo marcado como PENDIENTE DE DECISIÓN)
- Catálogo definitivo y validación clínica formal de reglas de triaje (mantenidas como prototipo según ADR-009).
- Variables de entorno del entorno dev de M1: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `FLYWAY_USER`, `FLYWAY_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`.

## Bitácora de sesiones
(Fecha · tarea · qué se hizo · pruebas ejecutadas · commit)
- 2026-10-01 · M0.1 · Creación de estructura de carpetas según §38, .gitignore estricto para Oracle ATP y secretos, ubicación definitiva de documentos de especificación (AGENTS.md, MVP.md, DECISIONES.md, PLAN_DE_TRABAJO.md, PROGRESO.md, DISENO_UI_UX.md, HERRAMIENTAS_CLI.md, DOCUMENTO_MAESTRO.md, tokens.css, skills y comandos .opencode), README.md inicial y configuración de ramas main, develop y feature/m0-cimientos · Verificación de git status limpio y exclusión de wallet/secretos · chore: estructura inicial del repositorio y cimientos m0.1
- 2026-10-01 · M0.2 · Especificación formal de casos de uso (CU-01 a CU-11) y reglas de negocio (RB-01 a RB-25) alineadas con ADR-001..013 y Documento Maestro §44 · Verificación de consistencia y ausencia de contradicciones con ADRs · docs(requirements): casos de uso y reglas de negocio m0.2
- 2026-10-01 · M0.3 · Diseño del modelo de dominio y diagrama entidad-relación (MER) en Mermaid cubriendo 25 entidades, cardinalidades y descripción conceptual · Verificación visual de relaciones y atributos clave · docs(database): modelo entidad-relacion mer m0.3
- 2026-10-01 · M0.4 · Especificación completa del modelo relacional en Oracle ATP (25 tablas, tipos, constraints, índice funcional único uq_cita_slot_activa, triggers de inmutabilidad clínica, política ON DELETE y análisis 3FN) · Verificación de tipos Oracle y ausencia de borrado en tablas clínicas · docs(database): modelo relacional y normalizacion m0.4
- 2026-10-01 · M0.4-Rev · Corrección profunda del modelo relacional: eliminación de ON DELETE RESTRICT (NO ACTION en Oracle), EMAIL minúsculas con CHECK y sin índice redundante, ATENCION con transición ABIERTA->CERRADA y campos obligatorios al cierre, triggers/grants de inmutabilidad para todas las entidades clínicas, FK cita-triaje, condiciones estructuradas de triaje, UQ slot-profesional-inicio, reescritura de 3FN sin afirmar BCNF con 4 desnormalizaciones controladas, snapshot cuádruple en receta, rangos en signos vitales y consentimiento revocable · docs(database): correccion integral del modelo relacional segun observaciones
- 2026-10-01 · M0.5 · Aprobación formal de especificaciones y diseño de la Fase M0 por parte de Juan. Actualización de DECISIONES.md a APROBADO, cierre de fase M0, merge a develop y creación de etiqueta v0.0 · Puerta de salida M0 cumplida · chore: aprobacion de diseno y cierre de fase m0 (v0.0)
- 2026-10-01 · M0.5-Ajustes · Incorporación de decisiones finales aprobadas: ES_ALARMA en SINTOMA con corte de emergencia y default Nivel III, EVOLUCION VARCHAR2(4000 CHAR) con validación DTO, segregación MEDITRIAJE_OWNER/MEDITRIAJE_APP en ADR-012 y PLAN_DE_TRABAJO, trigger de SIGNO_VITAL bloqueando INSERT en CERRADA, emisión de RECETA sobre atención cerrada, validación de integridad referencial CITA-TRIAJE mediante clave compuesta UQ y FK en BD, y time zone en HikariCP · Verificación de consistencia cruzada en docs/ · docs(database): ajustes finales de modelo relacional, adrs y prerrequisitos m0
- 2026-10-01 · M1.1 · Creación de backend/ con Spring Boot 3 y Java 21, configuración de dependencias Maven (web, validation, security, jdbc, flyway, ojdbc11, actuator, test), estructura de paquetes por capas (§8), perfiles dev/test/prod sin secretos y endpoint GET /api/v1/ping · mvn clean verify exitoso y verificación de respuesta UP en vivo · feat(backend): inicializar proyecto base spring boot 3 con java 21 y endpoint de ping m1.1
- 2026-10-01 · M1.2 · Configuración de DataSource HikariCP leyendo DB_URL, DB_USER y DB_PASSWORD, conexión inicial con CURRENT_SCHEMA=MEDITRIAJE_OWNER y TIME_ZONE=America/Bogota, health check de base de datos en Actuator, creación de .env.example sin secretos y docs/database/DATABASE.md con guía de wallet y usuarios ATP · Pruebas unitarias pasando y verificación git grep -i password limpia de secretos · feat(database): configurar datasource hikari para oracle atp, health check y documentacion m1.2
- 2026-10-01 · M1.3 · Configuración de migraciones Flyway empaquetadas en classpath y vía plugin Maven, creación de migración inicial V001__baseline.sql con tabla CONTROL_SISTEMA y GRANTs a MEDITRIAJE_APP según ADR-012, y documentación de comandos en DATABASE.md · mvn clean verify exitoso con empaquetado de recursos de migración · feat(flyway): configurar migraciones flyway con linea base v001 y grants m1.3
- 2026-10-01 · M1.4 · ApiError record (codigo, mensaje, timestamp, traceId), jerarquía de excepciones de dominio (RecursoNoEncontrado 404, DatosInvalidos 400, AccesoNoAutorizado 403, CitaNoDisponible 409, MediTriajeException base), GlobalExceptionHandler @RestControllerAdvice sin stack traces, CorsConfig leyendo CORS_ORIGINS, SecurityConfig integrado con CorsConfigurationSource, logging configurado sin datos sensibles · mvn clean verify exitoso - Tests run: 2, Failures: 0 · feat(error-handling): manejo global de errores cors y logging m1.4
