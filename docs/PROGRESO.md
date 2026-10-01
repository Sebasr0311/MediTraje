# PROGRESO — MediTriaje 2.0

> La CLI no recuerda sesiones anteriores. Este archivo es su memoria.
> **Al empezar una sesión:** léelo. **Al terminar cada tarea:** actualízalo (marca la tarea, anota decisiones y pendientes, agrega una línea a la bitácora).

## Estado actual
- **Fase actual:** M1
- **Tarea actual:** M1.1
- **Última etiqueta:** v0.0
- **Rama de trabajo:** develop

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
- [ ] M1.1 Proyecto Spring Boot base
- [ ] M1.2 Conexión a Oracle ATP
- [ ] M1.3 Flyway
- [ ] M1.4 Errores, logs y CORS
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

## Pendientes y dudas abiertas
(Todo lo marcado como PENDIENTE DE DECISIÓN)
- Catálogo definitivo y validación clínica formal de reglas de triaje (mantenidas como prototipo según ADR-009).
- Datos de conexión a Oracle ATP (wallet, usuario y clave para el perfil dev de M1).

## Bitácora de sesiones
(Fecha · tarea · qué se hizo · pruebas ejecutadas · commit)
- 2026-10-01 · M0.1 · Creación de estructura de carpetas según §38, .gitignore estricto para Oracle ATP y secretos, ubicación definitiva de documentos de especificación (AGENTS.md, MVP.md, DECISIONES.md, PLAN_DE_TRABAJO.md, PROGRESO.md, DISENO_UI_UX.md, HERRAMIENTAS_CLI.md, DOCUMENTO_MAESTRO.md, tokens.css, skills y comandos .opencode), README.md inicial y configuración de ramas main, develop y feature/m0-cimientos · Verificación de git status limpio y exclusión de wallet/secretos · chore: estructura inicial del repositorio y cimientos m0.1
- 2026-10-01 · M0.2 · Especificación formal de casos de uso (CU-01 a CU-11) y reglas de negocio (RB-01 a RB-25) alineadas con ADR-001..013 y Documento Maestro §44 · Verificación de consistencia y ausencia de contradicciones con ADRs · docs(requirements): casos de uso y reglas de negocio m0.2
- 2026-10-01 · M0.3 · Diseño del modelo de dominio y diagrama entidad-relación (MER) en Mermaid cubriendo 25 entidades, cardinalidades y descripción conceptual · Verificación visual de relaciones y atributos clave · docs(database): modelo entidad-relacion mer m0.3
- 2026-10-01 · M0.4 · Especificación completa del modelo relacional en Oracle ATP (25 tablas, tipos, constraints, índice funcional único uq_cita_slot_activa, triggers de inmutabilidad clínica, política ON DELETE y análisis 3FN) · Verificación de tipos Oracle y ausencia de borrado en tablas clínicas · docs(database): modelo relacional y normalizacion m0.4
- 2026-10-01 · M0.5 · Aprobación formal de especificaciones y diseño de la Fase M0 por parte de Juan. Actualización de DECISIONES.md a APROBADO, cierre de fase M0, merge a develop y creación de etiqueta v0.0 · Puerta de salida M0 cumplida · chore: aprobacion de diseno y cierre de fase m0 (v0.0)
