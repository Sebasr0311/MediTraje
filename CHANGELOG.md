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
- **CRUD de Especialidades, Instituciones y Sedes (M3.2, HU-10, ADR-002, ADR-003, ADR-011)**:
  - Modelos de dominio inmutables en `com.meditriaje.model`: `Especialidad`, `Institucion`, `Sede`.
  - DTOs y paginación en `com.meditriaje.dto.admin` y `com.meditriaje.dto.common`: `CrearEspecialidadRequest`, `ActualizarEspecialidadRequest`, `EspecialidadResponse`, `CrearInstitucionRequest`, `ActualizarInstitucionRequest`, `InstitucionResponse`, `CrearSedeRequest`, `ActualizarSedeRequest`, `SedeResponse`, `PaginatedResponse<T>`.
  - Repositorios JDBC con SQL 100% parametrizado y paginación Oracle (`OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`): `EspecialidadRepository`, `InstitucionRepository`, `SedeRepository`.
  - Servicio de negocio `AdminCatalogService`: validaciones de unicidad de nombre de especialidad y NIT de institución, verificación de institución activa para sedes, transiciones de estado (`ACTIVO`/`INACTIVO`, sin borrado físico) y registro inmutable obligatorio de auditoría (`CAMBIO_ADMINISTRATIVO`) vía `AuditoriaService`.
  - Controladores REST protegidos con `@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")`:
    - `AdminSpecialtyController` en `/api/v1/admin/specialties` (POST, GET paginado, GET por id, PUT, PATCH deactivate/activate).
    - `AdminInstitutionController` en `/api/v1/admin/institutions` (POST, GET paginado, GET por id, PUT, PATCH deactivate/activate).
    - `AdminSiteController` en `/api/v1/admin/sites` (POST, GET paginado con filtro de institución, GET por id, PUT, PATCH deactivate/activate).
  - Pruebas unitarias completas de negocio y auditoría en `AdminCatalogServiceTest` (22 pruebas).
  - Pruebas de integración MockMvc con seguridad en `AdminSpecialtyControllerTest`, `AdminInstitutionControllerTest` y `AdminSiteControllerTest` validando permisos para ADMINISTRADOR, rechazo 403 para PACIENTE/PROFESIONAL, 401 sin autenticación, 400 datos inválidos y 404 recursos inexistentes.
- **Alta y Gestión de Profesionales Asistenciales (M3.3, HU-10, ADR-002, ADR-003, ADR-011, ADR-012)**:
  - Migración Flyway V005 (`database/migrations/V005__usuario_cambio_password.sql`):
    - Columna `DEBE_CAMBIAR_PASSWORD NUMBER(1) DEFAULT 0 NOT NULL` y restricción `CK_USUARIO_CAMBIO_PASS` en tabla `USUARIO`.
  - Modelos de dominio en `com.meditriaje.model`:
    - `Usuario`: soporte para `debeCambiarPassword` y sobrecarga de constructor retrocompatible.
    - `Profesional`: modelo inmutable para profesional asistencial.
    - `AccionAuditable`: incorporación de acción `CAMBIO_PASSWORD`.
  - DTOs en `com.meditriaje.dto`:
    - `CrearProfesionalRequest`, `ActualizarProfesionalRequest`, `ProfesionalResponse`, `CrearProfesionalResponse`, `CambiarPasswordRequest` y actualización retrocompatible de `AuthSessionResponse`.
  - Repositorios JDBC con SQL 100% parametrizado:
    - `UsuarioRepository`: mapeo de `DEBE_CAMBIAR_PASSWORD`, sobrecarga de creación, actualización de contraseña y de estado.
    - `ProfesionalRepository`: creación, actualización, búsquedas por IDs y clave pública, validación de duplicidad de registro médico, listado paginado con filtros (`especialidadPublicId`, `estado`) y conteo.
  - Servicios de negocio:
    - `AdminProfessionalService`: alta con generación criptográfica segura de contraseña temporal de un solo uso (Argon2id), asignación de `ROLE_PROFESIONAL`, validación de especialidad activa, unicidad de correo y registro médico, actualización de datos asistenciales, activación/desactivación y auditoría inmutable obligatoria (`CAMBIO_ADMINISTRATIVO`).
    - `AuthService`: soporte para `debeCambiarPassword` en login/refresh y método `cambiarPassword` con validación de credenciales actuales, no reuso de la clave anterior, actualización a `debeCambiarPassword = false` y auditoría (`CAMBIO_PASSWORD`).
  - Controladores REST y seguridad (Spring Security):
    - `AdminProfessionalController` en `/api/v1/admin/professionals` protegido con `@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")` (POST, GET paginado, GET por id, PUT, PATCH deactivate/activate).
    - `AuthController` en `/api/v1/auth/change-password` para usuarios autenticados.
    - Configuración en `SecurityConfig` delimitando endpoints públicos e integrando autenticación para el cambio de credenciales.
  - Pruebas unitarias y de integración:
    - `AdminProfessionalServiceTest` (16 pruebas), `AdminProfessionalControllerTest` (11 pruebas), pruebas de cambio de contraseña en `AuthServiceTest` y `AuthControllerTest`, pruebas de repositorio en `ProfesionalRepositoryTest` y `UsuarioRepositoryTest` (147 pruebas en total pasando exitosamente).
- **Generador de Slots de Disponibilidad (M3.4, HU-10, ADR-002, ADR-003, ADR-005, ADR-006, ADR-011, ADR-012)**:
  - Manejo temporal centralizado con zona horaria `America/Bogota` (ADR-005) y persistencia en `TIMESTAMP WITH TIME ZONE` (`Instant`).
  - Modelo de dominio inmutable en `com.meditriaje.model`: `DisponibilidadSlot` (id, publicId, profesionalId, sedeId, especialidadId, fechaHoraInicio, fechaHoraFin, modalidad, estado).
  - DTOs en `com.meditriaje.dto.admin`: `GenerarSlotsRequest`, `GenerarSlotsResponse`, `SlotResponse`.
  - Repositorio JDBC `DisponibilidadSlotRepository` con SQL 100% parametrizado:
    - Inserción unitaria y por lotes (`guardarLote` con `BatchPreparedStatementSetter`).
    - Búsqueda por `publicId` (con JOINs a `PROFESIONAL`, `SEDE` y `ESPECIALIDAD`), por `id` y por entidad.
    - Detección de solapes de agenda mediante consulta relacional de intervalos: `FECHA_HORA_INICIO < fin AND FECHA_HORA_FIN > inicio`.
    - Transición de estados (`cambiarEstado`) y borrado físico condicional (`eliminar` solo en estado `LIBRE`).
    - Consulta paginada dinámica con filtros (`profesionalPublicId`, `sedePublicId`, `especialidadPublicId`, `fechaDesde`, `fechaHasta`, `estado`) y conteo total.
  - Servicio de negocio `SlotGeneratorService`:
    - Validaciones estrictas: profesional existente con usuario activo, sede activa, coherencia de rango de fechas (máx. 90 días, no anterior a hoy en Bogotá), hora fin > hora inicio, duración de turno (5-120 min, default de especialidad o 20 min) y modalidad (`PRESENCIAL` o `TELEMEDICINA`).
    - Algoritmo generador de turnos: iteración por fechas, filtrado opcional por días de la semana (`diasSemana`), generación de intervalos sin superar la ventana diaria y verificación transaccional de colisiones contra agenda previa del profesional (`DatosInvalidosException`).
    - Gestión de turnos: bloqueo de slots (`LIBRE` -> `BLOQUEADO`), desbloqueo (`BLOQUEADO` -> `LIBRE`), eliminación física condicionada y auditoría inmutable obligatoria (`CAMBIO_ADMINISTRATIVO`) vía `AuditoriaService`.
  - Controlador REST `AdminSlotController` en `/api/v1/admin/slots` protegido con `@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")`:
    - `POST /generate` (201 Created), `GET` paginado (200 OK), `GET /{publicId}` (200 OK), `PATCH /{publicId}/block` (200 OK), `PATCH /{publicId}/unblock` (200 OK), `DELETE /{publicId}` (204 No Content).
  - Pruebas unitarias y de integración exhaustivas:
    - `DisponibilidadSlotRepositoryTest` (8 pruebas), `SlotGeneratorServiceTest` (21 pruebas), `AdminSlotControllerTest` (14 pruebas) con verificación de permisos, CSRF (`X-Requested-With`), casos de borde y 190 pruebas en total pasando exitosamente.
- **Seeds Ficticios y Colección HTTP de Pruebas (M3.5, HU-10, ADR-004, ADR-012)**:
  - Script SQL de datos ficticios para desarrollo en `database/seeds/dev_seeds_m3.sql`:
    - 1 usuario administrador (`admin@meditriaje.com` / `Admin12345*`).
    - 1 institución prestadora de salud (`IPS MediSalud Valledupar S.A.S.`).
    - 2 sedes asistenciales (`Sede Centro Valledupar`, `Sede Norte Valledupar`).
    - 4 especialidades médicas (`Medicina General`, `Pediatria`, `Medicina Interna`, `Odontologia`).
    - 4 profesionales asistenciales con sus usuarios, contraseñas temporales iniciales (`Temporal12345*`) y rol `ROLE_PROFESIONAL`.
    - Slots de disponibilidad distribuidos en días hábiles de los próximos 14 días en zona `America/Bogota`.
  - Documentación de advertencia y guía de ejecución en `database/seeds/README.md` (prohibido en producción).
  - Script PowerShell de conveniencia `database/seeds/cargar_seeds_dev.ps1`.
  - Colección completa de pruebas de integración HTTP en formato REST Client en `docs/api/M3.http`: 11 escenarios cubriendo login de admin, CRUD de especialidades, instituciones, sedes, alta de profesional, primer login con clave temporal, cambio obligatorio de contraseña, generación masiva de slots, gestión de slots y aislamiento de roles (403 Forbidden para paciente/profesional, 401 Unauthorized sin token y 400 Bad Request en solapes).
  - Puerta de salida M3 completada con éxito.

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
