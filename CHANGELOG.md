# Changelog

Todos los cambios notables en este proyecto serán documentados en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Migración V002 (`database/migrations/V002__seguridad.sql`)**:
  - Tablas: `USUARIO`, `ROL`, `USUARIO_ROL`, `REFRESH_TOKEN`, `CONSENTIMIENTO`, `AUDITORIA`.
  - Triggers de inmutabilidad: `TR_CONSENTIMIENTO_INMUTABILIDAD` (restringe updates y bloquea delete) y `TR_AUDITORIA_INMUTABILIDAD` (insert-only estricto).
  - Semillas de roles: `ROLE_PACIENTE`, `ROLE_PROFESIONAL`, `ROLE_ADMINISTRADOR`.
  - Concesión de privilegios mínimos a `MEDITRIAJE_APP` (ADR-012): sin DELETE clínico ni UPDATE/DELETE en `AUDITORIA`.
  - Pruebas de integración actualizadas en `OracleIntegrationTest.java` para verificar V002, 3 roles y segregación de auditoría.

## [0.1.0] - 2026-10-01

### Added
- **Proyecto Base Spring Boot 3 con Java 21**:
  - Arquitectura modular por capas y perfiles `dev`, `test`, `prod`.
  - Endpoint de salud y conectividad `GET /api/v1/ping`.
- **Conexión a Oracle ATP**:
  - DataSource HikariCP con configuración de zona horaria (`America/Bogota`) y esquema por defecto (`MEDITRIAJE_OWNER`) en `connectionInitSql`.
  - Health check en Spring Actuator.
  - Documentación de configuración y despliegue en `docs/database/DATABASE.md`.
- **Migraciones Flyway**:
  - Migración base `V001__baseline.sql` con tabla `CONTROL_SISTEMA`.
  - Empaquetado automático en classpath y plugin Maven.
- **Manejo Global de Errores y Seguridad Web**:
  - DTO `ApiError` estructurado sin exposición de stack traces ni datos sensibles.
  - `@RestControllerAdvice` con mapeo a excepciones de dominio (`RecursoNoEncontrado`, `DatosInvalidos`, `AccesoNoAutorizado`, `CitaNoDisponible`).
  - Configuración centralizada de CORS vía variable de entorno `CORS_ORIGINS`.
- **Pruebas y CI**:
  - Suite de integración con Testcontainers Oracle Free (`OracleIntegrationTest`).
  - Pipeline de GitHub Actions en `.github/workflows/ci.yml`.

## [0.0.0] - 2026-10-01

### Added
- Estructura inicial del repositorio y cimientos de arquitectura.
- Especificación formal de requisitos, casos de uso (CU-01 a CU-11) y reglas de negocio (RB-01 a RB-25).
- Modelo de dominio y Diagrama Entidad-Relación (MER).
- Modelo Relacional normalizado en 3FN para Oracle ATP (25 entidades) y catálogo de decisiones ADR-001 a ADR-013.
