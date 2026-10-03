# Changelog

Todos los cambios notables en este proyecto serán documentados en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Migración V004 (`database/migrations/V004__oferta_administracion.sql`) (M3.1)**:
  - Tablas: `INSTITUCION`, `SEDE`, `ESPECIALIDAD`, `PROFESIONAL`, `DISPONIBILIDAD_SLOT`.
  - Restricciones relacionales: FKs, checks de estados (`ACTIVO`/`INACTIVO`, `LIBRE`/`OCUPADO`/`BLOQUEADO`), modalidades (`PRESENCIAL`/`TELEMEDICINA`), coherencia horaria (`FECHA_HORA_FIN > FECHA_HORA_INICIO`) y unicidad (`UQ_SLOT_PROFESIONAL_INICIO`).
  - Índices de rendimiento: `IX_SEDE_INSTITUCION`, `IX_PROFESIONAL_ESPECIALIDAD`, `IX_SLOT_BUSQUEDA` e `IX_SLOT_SEDE`.
  - Concesión de privilegios mínimos a `MEDITRIAJE_APP` (ADR-012): `SELECT`, `INSERT`, `UPDATE` en instituciones, sedes, especialidades y profesionales; y `SELECT`, `INSERT`, `UPDATE`, `DELETE` en slots de disponibilidad.
  - Actualización de pruebas de integración en `OracleIntegrationTest.java` para verificar V004 y acceso de `MEDITRIAJE_APP` a las 5 tablas de oferta.

## [0.2.0] - 2026-10-03

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
- **Spring Security, Autorización por Rol y Endpoint /me (M2.5)**:
  - `SecurityConfig`: Configuración declarativa con `@EnableMethodSecurity(prePostEnabled = true)`. Rutas públicas (`/api/v1/ping`, `/api/v1/auth/**`), resto autenticado.
  - `JwtAuthenticationFilter`: Extracción de token de cookie HttpOnly `access_token` (o header `Authorization: Bearer`), validación y poblado de `SecurityContextHolder`.
  - `CsrfHeaderFilter`: Defensa en profundidad contra CSRF (ADR-002) exigiendo cabecera personalizada `X-Requested-With` o `X-CSRF-Protection` en operaciones mutantes (`POST`, `PUT`, `DELETE`, `PATCH`).
  - `CustomAuthenticationEntryPoint` y `CustomAccessDeniedHandler`: Retornan JSON estructurado con `ApiError` para 401 y 403 respectivamente sin stack traces.
  - Manejo de `AccessDeniedException` y `AuthenticationException` de Spring Security en `GlobalExceptionHandler`.
  - `CorsConfig`: Permitidas las cabeceras `X-Requested-With` y `X-CSRF-Protection` en preflights CORS.
  - `PacienteController` y `PacienteService`: Endpoint `GET /api/v1/patients/me` protegido para rol `ROLE_PACIENTE`.
  - `PacienteRepository`: Consulta de perfil demográfico y contacto por UUID expuesto `publicId` (ADR-003, sin exponer IDs internos).
  - 48 pruebas unitarias y de controladores pasando al 100%.
- **Revisión de Seguridad de la Fase M2 y Colección HTTP (M2.6)**:
  - Auditoría exhaustiva de seguridad documentada en `docs/security/REVISION_M2.md`: verificación de cero secretos en repositorio, ausencia de datos clínicos en logs, SQL parametrizado, políticas de contraseñas (Argon2id), mitigación de robo de sesión y defensa en profundidad CSRF.
  - Colección de pruebas de integración HTTP en formato estándar REST Client en `docs/api/M2.http` cubriendo ping, registro, login (éxito/fallo), perfil `/patients/me`, refresh rotativo y logout con validación de CSRF y cookies.
  - Puerta de salida M2 lista para revisión y aprobación por Juan.

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
