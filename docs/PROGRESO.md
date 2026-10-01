# PROGRESO — MediTriaje 2.0

> La CLI no recuerda sesiones anteriores. Este archivo es su memoria.
> **Al empezar una sesión:** léelo. **Al terminar cada tarea:** actualízalo (marca la tarea, anota decisiones y pendientes, agrega una línea a la bitácora).

## Estado actual
- **Fase actual:** M0
- **Tarea actual:** M0.2
- **Última etiqueta:** (ninguna)
- **Rama de trabajo:** feature/m0-cimientos

## Tareas
Leyenda: `[ ]` pendiente · `[~]` en curso · `[x]` hecha

### M0 — Cimientos de diseño
- [x] M0.1 Repo y estructura
- [ ] M0.2 Casos de uso y reglas de negocio
- [ ] M0.3 Modelo de dominio y MER
- [ ] M0.4 Modelo relacional y normalización
- [ ] M0.5 Aprobación (manual)

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

## Pendientes y dudas abiertas
(Todo lo marcado como PENDIENTE DE DECISIÓN)

## Bitácora de sesiones
(Fecha · tarea · qué se hizo · pruebas ejecutadas · commit)
- 2026-10-01 · M0.1 · Creación de estructura de carpetas según §38, .gitignore estricto para Oracle ATP y secretos, ubicación definitiva de documentos de especificación (AGENTS.md, MVP.md, DECISIONES.md, PLAN_DE_TRABAJO.md, PROGRESO.md, DISENO_UI_UX.md, HERRAMIENTAS_CLI.md, DOCUMENTO_MAESTRO.md, tokens.css, skills y comandos .opencode), README.md inicial y configuración de ramas main, develop y feature/m0-cimientos · Verificación de git status limpio y exclusión de wallet/secretos · chore: estructura inicial del repositorio y cimientos m0.1
