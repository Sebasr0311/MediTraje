# Changelog

Todos los cambios notables en este proyecto serán documentados en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Migración V002 (`database/migrations/V002__seguridad.sql`) (M2.1)**:
  - Tablas: `USUARIO`, `ROL`, `USUARIO_ROL`, `REFRESH_TOKEN`, `CONSENTIMIENTO`, `AUDITORIA`.
  - Triggers de inmutabilidad: `TR_CONSENTIMIENTO_INMUTABILIDAD` (restringe updates y bloquea delete) y `TR_AUDITORIA_INMUTABILIDAD` (insert-only estricto).
  - Semillas de roles: `ROLE_PACIENTE`, `ROLE_PROFESIONAL`, `ROLE_ADMINISTRADOR`.
  - Concesión de privilegios mínimos a `MEDITRIAJE_APP` (ADR-012): sin DELETE clínico ni UPDATE/DELETE en `AUDITORIA`.
  - Pruebas de integración actualizadas en `OracleIntegrationTest.java` para verificar V002, 3 roles y segregación de auditoría.
- **Servicio y Repositorio de Auditoría (M2.2)**:
  - `AuditoriaService`: servicio centralizado para registrar eventos inmutables sin datos clínicos ni secretos (ADR-011, HU-11).
  - `AuditoriaRepository`: repositorio estrictamente insert-only usando `JdbcTemplate` y SQL parametrizado.
  - `AccionAuditable` y `ResultadoAuditoria`: enums tipados alineados con restricciones relacionales.
  - `EventoAuditoria`: record de dominio con validaciones de campos obligatorios.
  - Pruebas unitarias para `AuditoriaService` y `AuditoriaRepository` (verificación de exclusión de datos clínicos y ausencia de métodos de modificación).
- **Registro de Paciente y Migración V003 (M2.3)**:
  - Migración `database/migrations/V003__paciente.sql`: tabla `PACIENTE` con constraints de documento, clave pública UUID y permisos mínimos para `MEDITRIAJE_APP`.
  - Endpoint `POST /api/v1/auth/register` con DTOs `RegistroPacienteRequest` y `RegistroPacienteResponse`.
  - Transacción atómica en `AuthService` creando `USUARIO`, asociando `ROLE_PACIENTE`, creando `PACIENTE` y registrando `CONSENTIMIENTO`.
  - Hashing de contraseñas con Argon2id (OWASP v5.8) vía `bcprov-jdk18on` (ADR-002).
  - Auditoría automática `REGISTRO_PACIENTE` tanto para eventos exitosos como fallidos.
  - Manejo de `NoResourceFoundException` en `GlobalExceptionHandler` retornando 404 estandarizado.
  - Pruebas unitarias y de integración para `AuthService`, `AuthController` y `OracleIntegrationTest`.
- **Login, Rotación de Refresh Tokens y Logout (M2.4)**:
  - `JwtService`: Emisión y validación HMAC-SHA256 de access JWTs (15 min) con claims de `sub=publicId`, `email` y `roles`.
  - `TokenHashUtil`: Hashing criptográfico unidireccional SHA-256 para persistencia de refresh tokens opacos en BD.
  - `RefreshTokenRepository`: Repositorio para creación, revocación individual y revocación masiva de sesiones por usuario.
  - `UsuarioRepository`: Métodos de búsqueda por email/publicId/ID, obtención de roles y gestión de bloqueos por intentos fallidos.
  - `AuthService`:
    - Flujo de login con validación de bloqueo temporal (15 min) tras 5 intentos fallidos consecutivos y reseteo al éxito.
    - Mensaje de error genérico (`Credenciales invalidas.`) ante usuario inexistente, clave errónea o cuenta bloqueada para evitar enumeración.
    - Rotación de refresh token de 7 días y detección de reuso con revocación preventiva total de sesiones ante tokens ya revocados.
    - Logout con invalidación de sesión en BD y auditoría de eventos.
    - Auditoría automática de `LOGIN_EXITOSO`, `LOGIN_FALLIDO` (con resultado `FALLO` o `BLOQUEADO`) y `LOGOUT`.
  - `AuthController`: Endpoints `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` y `POST /api/v1/auth/logout` con cookies `HttpOnly; Secure; SameSite=Strict` (tokens nunca en el body según ADR-002).
  - Manejadores de `CredencialesInvalidasException` y `TokenInvalidoException` en `GlobalExceptionHandler` mapeados a HTTP 401.
  - 31 pruebas unitarias pasando al 100% (cobertura completa de casos de uso y edge cases).

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
