# MediTriaje 2.0 — Decisiones técnicas (ADRs)

Estado general: ADR-001 a ADR-010 aprobados por Juan el 2026-10-01 (con ADR-006 modificado según Decisión D2 en estado PROPUESTO). ADR-011 a ADR-020 se encuentran en **Estado: PROPUESTO — pendiente de aprobación de Juan**. Decisiones arquitectónicas firmes; no reabrir sin previa justificación y aviso.
Cada decisión resuelve uno o más de los 22 pendientes del Documento Maestro §54.

## Resumen

| # | Pendiente | Decisión | ADR |
|---|---|---|---|
| 1 | Framework backend | Spring Boot 3, Java 21, Maven | 001 |
| 2, 3 | Autenticación, tokens/sesiones | JWT corto + refresh rotativo en cookies HttpOnly | 002 |
| 4 | Hash de contraseñas | Argon2id (fallback BCrypt 12) | 002 |
| 5 | IDs | PK numérica interna + `public_id` UUID expuesto | 003 |
| 6, 11 | Estados y cancelación | Máquina de estados de cita; cancelación hasta 2 h antes | 006 |
| 7, 8, 9 | Reglas, síntomas, prioridades | Niveles I–V, reglas en tabla, corte de emergencia | 009 |
| 10 | Modelo de disponibilidad | Slots pregenerados con duración por especialidad | 006 |
| 12, 13 | Dispensación, seguimiento | Fuera del MVP (fase 2) | — |
| 14, 15 | Duración y alcance del QR | Fase 2; defaults en ADR-010 | 010 |
| 16 | Auditoría | Tabla insert-only, sin datos clínicos | 011 |
| 17 | Notificaciones | Fase 2, correo SMTP | — |
| 18 | Archivos/documentos | Fuera del MVP | — |
| 19 | Backups | Backups automáticos de ATP + documentar restauración | 012 |
| 20 | Despliegue | Local (Docker) en MVP; OCI Always Free al final | 012 |
| — | Migraciones | Flyway | 004 |
| — | Zona horaria | America/Bogota | 005 |
| — | Autorización del profesional | Relación asistencial | 007 |
| — | Historia inmutable | Append-only + enmiendas | 008 |
| — | Datos personales | Consentimiento + retención | 013 |
| 21, 22 | Dominio, diseño visual | **Siguen pendientes** (no bloquean el MVP) | — |

---

## ADR-001 Stack backend
**Decisión:** Java 21 (LTS), Spring Boot 3.x, Spring Web, Spring Security, Spring JDBC/JdbcTemplate (sin JPA/Hibernate), Maven.
**Razón:** el documento exige repositorios/DAO y SQL controlado; JdbcTemplate es transparente con Oracle y fácil de explicar en lo académico. Spring Security resuelve auth, roles y CORS.
**Alternativa:** JPA/Hibernate si se prefiere menos SQL manual.
**Pool:** HikariCP (o Oracle UCP). Conexión a ATP con wallet fuera del repo.

## ADR-002 Autenticación y contraseñas
**Decisión:**
- Access token JWT de 15 min + refresh token opaco de 7 días, rotativo y guardado hasheado en BD (revocable).
- Ambos en cookies `HttpOnly; Secure; SameSite=Strict`. **Nada en localStorage.**
- Protección CSRF: cabecera personalizada obligatoria + SameSite Strict.
- Contraseñas con Argon2id (Spring Security `Argon2PasswordEncoder`); si da problemas de dependencia, BCrypt con coste 12.
- Bloqueo temporal tras 5 fallos; límite de intentos por IP.
- MFA para PROFESIONAL y ADMIN: fase 2.

## ADR-003 Identificadores
**Decisión:** PK interna `NUMBER GENERATED ALWAYS AS IDENTITY`. Columna `public_id VARCHAR2(36)` (UUID) con `UNIQUE`, que es lo único que se expone en la API. Nunca exponer el ID interno.
**Razón:** evita enumeración de IDs y mantiene joins rápidos.

## ADR-004 Migraciones
**Decisión:** Flyway, scripts `V###__descripcion.sql` en `database/migrations/`. Migraciones aplicadas no se editan. Datos de prueba en `database/seeds/`, separados y solo para entornos no productivos.
**Nota:** verificar compatibilidad de la versión de Flyway con la versión de Oracle de ATP; si falla, usar Liquibase.

## ADR-005 Fechas y zona horaria
**Decisión:** zona del proyecto `America/Bogota`. Instantes en `TIMESTAMP WITH TIME ZONE`; fechas de nacimiento en `DATE`. La API usa ISO-8601 con offset. El pool de conexiones (HikariCP) de `MEDITRIAJE_APP` configura obligatoriamente en `connectionInitSql` un bloque anónimo PL/SQL (`BEGIN ... END;`) que fija tanto la zona horaria como el esquema por defecto en cada conexión física:
```sql
BEGIN
  EXECUTE IMMEDIATE 'ALTER SESSION SET TIME_ZONE = ''America/Bogota''';
  EXECUTE IMMEDIATE 'ALTER SESSION SET CURRENT_SCHEMA = MEDITRIAJE_OWNER';
END;
```

## ADR-006 Citas, disponibilidad y concurrencia
**Estado:** PROPUESTO — pendiente de aprobación de Juan (Decisión D2, 2026-10-06).
**Disponibilidad:** tabla `DISPONIBILIDAD_SLOT` generada por el admin (profesional, sede, especialidad, modalidad, inicio, fin, estado LIBRE/OCUPADO/BLOQUEADO). Duración por defecto 20 min, configurable por especialidad.

**Estados de cita y transiciones permitidas (Decisión D2):**
- Flujo estándar simplificado:
```
PROGRAMADA  → ATENDIDA | CANCELADA | NO_ASISTIO
```
- Iniciar una atención clínica mantiene la cita en estado `PROGRAMADA`. Al cerrar la atención se pasa a `ATENDIDA`.
- Si una cita ya cuenta con una atención clínica vinculada (abierta o cerrada), se rechaza cualquier intento de cancelación o inasistencia con `409 ConflictoOperacionException`.
- Los estados `CONFIRMADA` y `REPROGRAMADA` quedan **reservados** (permanecen en el enum, el `CHECK` y el índice de unicidad para evitar migraciones DDL destructivas).
- Por compatibilidad histórica con registros legacy, `CONFIRMADA` permite transicionar a:
```
CONFIRMADA  → ATENDIDA | CANCELADA | NO_ASISTIO
```
- Estados finales inmutables: `ATENDIDA`, `CANCELADA`, `NO_ASISTIO`, `REPROGRAMADA`.
- **Cancelación:** el paciente hasta 2 h antes del inicio; después, solo profesional/admin (si no existe atención clínica vinculada).

**Doble reserva (en BD, no solo en Java):** `CITA.slot_id` con índice único funcional que solo cuenta citas activas:
```sql
CREATE UNIQUE INDEX uq_cita_slot_activa ON cita (
  CASE WHEN estado IN ('PROGRAMADA','CONFIRMADA') THEN slot_id END
);
```
Reservar en una transacción: `UPDATE slot SET estado='OCUPADO' WHERE id=? AND estado='LIBRE'`; si afecta 0 filas → `CitaNoDisponible`.

## ADR-007 Autorización del profesional (relación asistencial)
**Decisión:** un profesional puede ver a un paciente solo si existe una cita PROGRAMADA/CONFIRMADA futura, o una atención previa propia en los últimos 12 meses (parámetro configurable). El admin no tiene acceso clínico. Acceso de emergencia (*break-glass*): fase 2, con justificación obligatoria y auditoría reforzada.
**Implementación:** la regla vive en un `AccesoClinicoService` único, usado por todos los endpoints clínicos y con pruebas propias.

## ADR-008 Historia clínica inmutable
**Decisión:** las atenciones cerradas no se modifican ni se borran. Correcciones = registro en `ATENCION_ENMIENDA` (quién, cuándo, qué, motivo) enlazado a la atención original. El usuario de BD de la aplicación no tiene permiso `DELETE` sobre tablas clínicas; un trigger bloquea `UPDATE` de atenciones cerradas.

## ADR-009 Triaje
**Decisión:**
- Prioridad en 5 niveles (I–V), alineada con la Resolución 5596 de 2015. **Verificar la norma vigente y los tiempos objetivo antes de mostrarlos.**
- Reglas en tabla `REGLA_TRIAJE` versionada con columnas estructuradas (`duracion_min_horas`, `duracion_max_horas`, `intensidad_min`, `intensidad_max`, `nivel_prioridad`), no en código. Cada triaje guarda la versión de reglas usada.
- **Bandera de alarma:** `ES_ALARMA` reside **exclusivamente en `SINTOMA`** (alarma incondicional e intrínseca). Se elimina de `REGLA_TRIAJE`.
- **Corte de emergencia:** Emergencia = presencia de síntoma con `ES_ALARMA = 1` O evaluación de `NIVEL_PRIORIDAD = 'I'`. Mensaje "llama al 123 o ve a urgencias", sin ofrecer cita, evento registrado en auditoría.
- **Nivel por defecto conservador:** Si los síntomas reportados no coinciden con ninguna regla específica en `REGLA_TRIAJE`, el motor asigna por defecto **Nivel III (Urgencia menor / Prioritaria)**. **Nunca se asigna Nivel V** ante sintomatología no tipificada.
- Catálogo semilla pequeño (≈20 síntomas), marcado como **reglas de prototipo, no validadas clínicamente**. Si se quiere validez clínica, debe revisarlas personal de salud.
- Ruta sugerida: Urgencias / Atención prioritaria / Cita presencial / Cita remota / Consulta programada.
- El triaje nunca produce un diagnóstico ni recomienda medicamentos.

**Adenda M5.2 (prototipo)** — precisiones de implementación del motor, no validadas clínicamente:
- Duración: rango de regla `[DURACION_MIN_HORAS, DURACION_MAX_HORAS)` (mínimo inclusivo, máximo exclusivo; `NULL` = sin tope). Duración de entrada ≥ 0, puede ser fraccionaria. Intensidad: rango cerrado `[INTENSIDAD_MIN, INTENSIDAD_MAX]`, entero 0–10.
- Varios síntomas: se evalúa cada uno y el nivel final es el **más urgente** (I más urgente … V menos). Un síntoma sin regla aplicable aporta Nivel III (nunca V). Si varias reglas del mismo síntoma solapan, gana la más urgente.
- Nivel → ruta (mapeo de prototipo): I→URGENCIAS; II→ATENCION_PRIORITARIA; III→CITA_PRESENCIAL; IV→CITA_TELEMEDICINA; V→CONSULTA_PROGRAMADA.
- Emergencia = (algún síntoma con `ES_ALARMA`) O (nivel final = I). Entonces nivel = I, ruta = URGENCIAS, sin ruta de cita y mensaje «Llama al 123 o acude a urgencias de inmediato.».
- No se inventan tiempos de espera. Todo resultado incluye el aviso: «Esta orientación es un prototipo, no sustituye la valoración de un profesional de la salud.»
- El evento de emergencia se audita con `TRIAJE_EMERGENCIA` (solo usuario, acción, recurso e id; sin síntomas). Su conexión a endpoints corresponde a M5.4.

## ADR-010 QR temporal (fase 2)
**Defaults:** token aleatorio de 256 bits, expira a los 15 min, máximo 3 accesos, revocable. Alcance mínimo: alergias, medicamentos activos y antecedentes relevantes. Lectura sin login, con límite de intentos y PIN opcional. Cada acceso se audita. El QR contiene solo una URL con el token, nunca datos.

## ADR-011 Auditoría
**Estado:** PROPUESTO — pendiente de aprobación de Juan.
**Decisión:** tabla `AUDITORIA` insert-only (usuario, acción, tipo de recurso, id de recurso, resultado, IP, fecha). Prohibido guardar contenido clínico o secretos. Eventos del MVP listados en HU-11.

## ADR-012 Entornos, usuarios de BD, backups y despliegue
**Estado:** PROPUESTO — pendiente de aprobación de Juan.
- **Segregación de usuarios de base de datos:**
  - `MEDITRIAJE_OWNER`: propietario del esquema, utilizado exclusivamente por Flyway para migraciones y operaciones DDL (`CREATE`, `ALTER`, `DROP`, triggers, secuencias).
  - `MEDITRIAJE_APP`: usuario de mínimos privilegios utilizado por la aplicación en runtime (Spring Boot / HikariCP). En la inicialización de cada conexión física, su pool ejecuta en `connectionInitSql` un bloque anónimo PL/SQL (`BEGIN ... END;`) que fija `CURRENT_SCHEMA = MEDITRIAJE_OWNER` y `TIME_ZONE = 'America/Bogota'`, permitiendo acceder a los objetos sin prefijo de esquema y garantizando la zona horaria del proyecto.
  - **GRANTs mínimos por migración:** cada script Flyway (`database/migrations/V###__*.sql`), ejecutado por `MEDITRIAJE_OWNER`, incluye al final las sentencias `GRANT` mínimas indispensables para `MEDITRIAJE_APP` (`SELECT`, `INSERT`, `UPDATE` estrictamente necesarios; sin privilegios de `DELETE` en tablas clínicas y sin `UPDATE`/`DELETE` en `AUDITORIA` y `ATENCION_ENMIENDA`).
- Desarrollo local con Oracle Free en Docker (o ATP directo) y pruebas de integración con Testcontainers.
- Perfiles `dev`, `test`, `prod`; secretos por variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`). Wallet fuera del repo y en `.gitignore`.
- Despliegue en la nube:
  - **Frontend:** Alojado en **Vercel** como sitio web estático (HTML/CSS/JS vanilla sin build, entrega CDN global, HTTPS nativo).
  - **Backend:** Desplegado en **Render** como Web Service (Java 21 / Docker). Render asigna dinámicamente la variable de entorno `$PORT` (manejada con `server.port = ${PORT:${SERVER_PORT:8080}}`). Las credenciales y el wallet de Oracle ATP se inyectan como variables de entorno y *Secret Files* (`/etc/secrets/wallet`) sin tocar el repositorio.
  - **Base de Datos:** Oracle ATP permanece en OCI Always Free.
  - El frontend nunca interactúa directamente con Oracle; `CORS_ORIGINS` en Render se configura con el dominio de Vercel.

## ADR-013 Datos personales y cumplimiento (verificar con la norma vigente)
**Estado:** PROPUESTO — pendiente de aprobación de Juan.
- Consentimiento de tratamiento de datos (Ley 1581 de 2012) obligatorio en el registro, guardado en `CONSENTIMIENTO` con versión y fecha.
- Datos de salud = datos sensibles: mínimo acceso, mínima exposición, sin datos reales en pruebas.
- Historia clínica: no eliminación; conservación según Res. 1995 de 1999 (consultar plazo exacto). Interoperabilidad (Ley 2015 de 2020): fuera del MVP, pero no bloquear su evolución.
- Catálogos: CIE-10 reducido para diagnósticos; tipos de documento CC, TI, RC, CE, PA. Código CUM de medicamentos: fase 2.

## ADR-014 Autenticación multifactor (MFA TOTP) y recuperación de contraseña con código OTP por correo
**Estado:** PROPUESTO — pendiente de aprobación de Juan.
**Decisión:**
1. **Recuperación de Contraseña con Código OTP y Plantilla de Correo:**
   - Para restablecer contraseña, el usuario solicita un código ingresando su correo en `POST /api/v1/auth/forgot-password`.
   - El sistema genera un código numérico de 6 dígitos (`SecureRandom`, rango 100000–999999) con expiración de 15 minutos.
   - El código se almacena **hasheado con SHA-256** en la tabla `CODIGO_VERIFICACION` con contador de intentos (máximo 3 intentos fallidos antes de invalidarse).
   - Se envía un correo electrónico formateado con **plantilla HTML institucional** responsiva alineada con el sistema de diseño (`docs/DISENO_UI_UX.md`, tokens de color de `tokens.css`), que destaca el código de 6 dígitos en caja prominente, tiempo de expiración y advertencias de seguridad contra suplantación.
   - La respuesta HTTP es siempre genérica 200 OK (*"Si el correo se encuentra registrado, recibirás un código de verificación."*) para neutralizar ataques de enumeración.
   - El endpoint `POST /api/v1/auth/reset-password` valida el correo, el código OTP y la nueva contraseña (mínimo 10 caracteres). Al completarse, hashea con Argon2id, marca el código como usado, audita el evento e invalida de forma inmediata todas las sesiones activas (refresh tokens) de la cuenta.
2. **Autenticación Multifactor (MFA TOTP) para Profesionales y Administradores:**
   - Estándar RFC 6238 (TOTP con pasos de 30 segundos, HMAC-SHA1 y secreto Base32) compatible con aplicaciones autenticadoras estándar (Google Authenticator, Microsoft Authenticator).
   - Enrolamiento en dos pasos: generación de secreto + URI `otpauth://` y confirmación del primer código válido.
   - Login con desafío de segundo factor: si el usuario tiene MFA habilitado, el login por contraseña devuelve `mfaRequerido: true` y un token temporal de desafío (`mfaChallengeToken`, vida útil de 5 minutos). Las cookies definitivas `access_token` y `refresh_token` solo se emiten tras validar el código TOTP en `POST /api/v1/auth/mfa/authenticate`.
   - Generación de 8 códigos de respaldo (backup codes) alfanuméricos uniuso, almacenados hasheados con SHA-256 en la tabla `MFA_BACKUP_CODE`.
3. **Servicio de Correo Electrónico:**
   - Interfaz `EmailService` con carga de plantillas HTML desde `resources/templates/email/`.
   - En perfiles `dev` y `test`, si no hay servidor SMTP configurado, registra el correo renderizado en logs de forma segura para permitir pruebas funcionales y automatizadas. En perfil `prod`, utiliza `JavaMailSender` con TLS/STARTTLS o Brevo API HTTP (ADR-020).

## ADR-015 Seguimiento post-atención y recordatorios de citas por correo
**Estado:** PROPUESTO — pendiente de aprobación de Juan.
**Decisión:**
1. **Notificaciones de Citas por Correo Institucional:**
   - Confirmación inmediata al reservar cita (`POST /api/v1/appointments`): envía correo con plantilla `confirmacion-cita.html` indicando fecha, hora local en `America/Bogota`, profesional, especialidad, sede/modalidad, preparación previa y enlace a la plataforma.
   - Notificación de cancelación de cita (`PATCH /api/v1/appointments/{id}/cancel`): envía correo con plantilla `cancelacion-cita.html` informando la liberación del slot y el motivo (si fue indicado).
   - Trazabilidad en tabla `RECORDATORIO_CITA` con estado de envío (`ENVIADO`, `FALLIDO`), fecha y destinatario.
2. **Seguimiento Post-Atención y Tareas de Control:**
   - Al cerrarse una atención médica (`POST /api/v1/attentions/{id}/close`), o como acción médica posterior sobre una atención cerrada propia o con relación asistencial activa, el profesional asistencial puede prescribir tareas de seguimiento en `POST /api/v1/attentions/{id}/follow-ups`.
   - Tipos de seguimiento permitidos: `CONTROL_MEDICO`, `EVOLUCION_SINTOMAS`, `EXAMEN_PENDIENTE`, `ADHERENCIA_TRATAMIENTO`.
   - Se notifica al paciente vía correo electrónico con plantilla `resumen-atencion-seguimiento.html` con las indicaciones del profesional y fecha sugerida de control.
   - El paciente puede consultar sus tareas de seguimiento en `GET /api/v1/patients/me/follow-ups` y registrar reportes de evolución en `POST /api/v1/patients/me/follow-ups/{id}/report`.
   - **Regla estricta (§5.16 Documento Maestro):** El reporte de evolución del paciente jamás se convierte automáticamente en un diagnóstico médico. Queda registrado como insumo clínico accesible únicamente por profesionales con relación asistencial activa.
3. **Resiliencia y Tolerancia a Fallos en el Envío de Notificaciones:**
   - El despacho de correos no debe interferir con la atomicidad ni la finalización de las transacciones principales de negocio (reserva de cita o cierre de atención médica). En caso de excepción de envío SMTP, se registra el fallo en logs y en `RECORDATORIO_CITA`, pero la transacción principal permanece consolidada.
4. **Aislamiento y Privacidad (ADR-007 / ADR-011):**
   - Personal administrativo tiene acceso estrictamente bloqueado (403 Forbidden) a seguimientos post-atención y reportes de evolución.
   - Cero contenido clínico ni indicaciones en logs ni en la tabla de auditoría.

## ADR-016 Dispensación y reclamación farmacéutica de recetas
**Estado:** PROPUESTO — pendiente de aprobación de Juan.
**Contexto:** En el marco asistencial colombiano (Decreto 780 de 2016 y Resolución 1403 de 2007), la prescripción médica generada en la consulta debe ser dispensada de forma controlada por el servicio farmacéutico hospitalario o ambulatorio. Es imperativo garantizar que los medicamentos no se entreguen después de la vigencia de la receta, que no se sobrepase la cantidad formulada (control de entregas parciales y totales), que exista trazabilidad de lotes INVIMA y que el personal farmacéutico no acceda a evoluciones médicas reservadas (ADR-007).
**Decisión:**
1. **Rol de Farmacia (`ROLE_FARMACEUTICO`):**
   - Se crea el rol `ROLE_FARMACEUTICO` en `ROL`. Los usuarios con este rol representan al regente de farmacia o químico farmacéutico responsable de la entrega.
   - Segregación estricta (ADR-007): Tienen autorización para buscar y consultar recetas médicas vigentes y registrar dispensaciones. Tienen **prohibido el acceso** a notas de evolución médica, historias clínicas completas, triajes y gestión administrativa.
2. **Modelo Relacional de Dispensación:**
   - Tabla `DISPENSACION`: Registra cada evento de entrega en farmacia (`ID`, `PUBLIC_ID`, `RECETA_ID`, `SEDE_ID`, `USUARIO_ID`, `OBSERVACIONES`, `CREATED_AT`).
   - Tabla `DISPENSACION_DETALLE`: Registra los medicamentos entregados en ese evento (`ID`, `DISPENSACION_ID`, `RECETA_DETALLE_ID`, `CANTIDAD_ENTREGADA`, `LOTE`, `FECHA_VENCIMIENTO_LOTE`, `CREATED_AT`).
   - Triggers de inmutabilidad: `TR_DISPENSACION_INMUTABILIDAD` y `TR_DISP_DETALLE_INMUTABILIDAD` bloquean `UPDATE` y `DELETE`.
3. **Reglas de Negocio:**
   - **Vigencia estricta:** Una receta solo puede dispensarse si `CREATED_AT + VIGENCIA_DIAS >= CURRENT_TIMESTAMP`. Si está vencida, el sistema rechaza la dispensación con código `400 DatosInvalidosException`.
   - **Control de saldo y entregas parciales:** Para cada `RECETA_DETALLE`, la sumatoria de `CANTIDAD_ENTREGADA` histórica no puede exceder `CANTIDAD` prescrita (`saldo = prescrita - entregada`). Si el saldo es 0, no se puede dispensar más de ese ítem. Si todos los ítems de la receta alcanzan saldo 0, el estado calculado de la receta es `DISPENSADA_TOTAL`; si al menos uno tiene entrega mayor a 0 pero saldo > 0, es `DISPENSADA_PARCIAL`; si no tiene entregas, es `PENDIENTE`.
   - **Código de Reclamación:** Cada receta expone un código alfanumérico legible de reclamación derivado de su `PUBLIC_ID` (o token corto) para que el paciente lo presente en farmacia junto con su documento de identidad.
4. **Auditoría Obligatoria (ADR-011):**
   - Toda entrega farmacéutica genera un evento `DISPENSACION_RECETA` en `AUDITORIA` con el ID del dispensador, IP de origen y el `publicId` de la receta. Cero nombres de fármacos o diagnósticos en los logs o auditoría.

## ADR-017 Acceso clínico de emergencia (Break-Glass)
**Estado:** PROPUESTO — pendiente de aprobación de Juan.
**Contexto:** En situaciones clínicas de urgencia o emergencia médica (inconsciencia, trauma mayor, shock, alteración aguda del estado de conciencia o remisión urgente), el profesional de salud necesita consultar de manera inmediata el historial médico completo del paciente (diagnósticos previos, atenciones, signos vitales, alergias, recetas) sin que medie una cita previa agendada ni una atención propia en los últimos 12 meses (ADR-007). Sin embargo, permitir el acceso irrestricto violaría la reserva legal de la historia clínica (Resolución 1995 de 1999 y Ley Estatutaria 1581 de 2012). Es indispensable un protocolo formal de *Break-Glass* ("romper el vidrio") que habilite el acceso excepcional pero garantice justificación obligatoria, temporalidad estricta y auditoría indeleble.
**Decisión:**
1. **Autorización y Segregación de Roles (ADR-007):**
   - Únicamente usuarios con `ROLE_PROFESIONAL` (médicos asistenciales matriculados) pueden invocar la activación del *Break-Glass*.
   - El personal administrativo (`ROLE_ADMINISTRADOR`) tiene acceso estrictamente prohibido (`403 Forbidden`).
   - Los pacientes y farmacéuticos no pueden invocar *Break-Glass*.
2. **Justificación Médica Obligatoria:**
   - La solicitud de activación requiere un motivo clínico de urgencia explícito (`motivo`, mínimo 20 caracteres, máximo 500).
   - No se permiten justificaciones vacías, genéricas o en blanco (`400 DatosInvalidosException`).
3. **Temporalidad y Ventana de Vigencia:**
   - Todo acceso *Break-Glass* otorgado expira de forma automática transcurridas 24 horas desde el momento de su activación (`FECHA_EXPIRACION = CREATED_AT + 24 HOURS`).
   - Durante la ventana de 24 horas, `AccesoClinicoService` considera autorizadas las consultas clínicas y de historia clínica de ese paciente por ese profesional específico.
4. **Persistencia e Inmutabilidad en Base de Datos (ADR-008, ADR-012):**
   - Tabla relacional inmutable `ACCESO_BREAK_GLASS`: `ID`, `PUBLIC_ID`, `PROFESIONAL_ID`, `PACIENTE_ID`, `MOTIVO`, `FECHA_EXPIRACION`, `CREATED_AT`.
   - Trigger `TR_BREAK_GLASS_INMUTABILIDAD` que bloquea irrevocablemente cualquier intento de `UPDATE` o `DELETE`.
   - Concesión de privilegios mínimos a `MEDITRIAJE_APP`: `SELECT, INSERT` únicamente.
5. **Auditoría Reforzada (ADR-011):**
   - La activación genera un evento inmutable `ACCESO_BREAK_GLASS` en `AUDITORIA` registrando el `usuarioId`, la IP de origen, el tipo de recurso `"PACIENTE"`, el `publicId` del paciente y el resultado `EXITO`.
   - Cero contenido clínico confidencial en la tabla general de auditoría o en logs de aplicación.

## ADR-018 Reportes operativos administrativos y asistente del sistema (RF-27, RF-30)
**Estado:** PROPUESTO — pendiente de aprobación de Juan.
**Contexto:**
1. *Métricas y reportes operativos (RF-30):* La gestión de infraestructura hospitalaria, disponibilidad de profesionales y evaluación del triaje requiere que el personal administrativo (`ROLE_ADMINISTRADOR`) cuente con indicadores y estadísticas de rendimiento del servicio (volumen de citas por estado, tasas de cancelación e inasistencia, distribución de triajes por nivel de prioridad I-V, demanda de especialidades y volumen de dispensación farmacéutica). Sin embargo, conforme a ADR-007, el administrador tiene prohibido el acceso a la historia clínica de los pacientes. Por ende, los reportes deben construirse exclusivamente mediante agregaciones matemáticas y estadísticas anónimas en base de datos (`COUNT`, `SUM`, `GROUP BY`), sin retornar jamás identificadores de pacientes, diagnósticos individuales ni datos sensibles de salud.
2. *Asistente del sistema / chatbot (RF-27, DOCUMENTO_MAESTRO §5.19):* Los usuarios (pacientes y personal) necesitan una herramienta interactiva para resolver dudas operativas frecuentes (orientación sobre el triaje, preparación para citas, reclamación de medicamentos, uso del QR de emergencia y comprensión de estados). Para salvaguardar la seguridad del paciente, el asistente debe contar con reglas no negociables: jamás diagnostica ni prescribe fármacos, detecta inmediatamente expresiones de alarma médica para remitir al 123 o a urgencias, y opera con respuestas estructuradas y deterministas basadas en el conocimiento de la plataforma.

**Decisión:**
1. **Segregación Estricta de Reportes Administrativos (ADR-007):**
   - Endpoints bajo `/api/v1/admin/reports/**` protegidos con `@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")`.
   - Consultas SQL 100% agregadas:
     - `CITA`: conteo agrupado por `ESTADO`, por `ESPECIALIDAD` y por `SEDE`, calculando tasa de cumplimiento y tasa de inasistencia/cancelación.
     - `TRIAJE`: conteo por `NIVEL_PRIORIDAD` (I al V) y total de cortes de emergencia activados (`ES_EMERGENCIA = 1`).
     - `RECETA` y `DISPENSACION`: total de recetas emitidas, conteo por estado de dispensación (`PENDIENTE`, `DISPENSADA_PARCIAL`, `DISPENSADA_TOTAL`) y total de unidades farmacológicas entregadas.
     - `ACCESO_BREAK_GLASS`: total de activaciones de emergencia y distribución mensual por especialidad para control del comité asistencial.
   - Parámetros opcionales de ventana temporal (`fechaDesde`, `fechaHasta` calculados en zona horaria `America/Bogota`).
   - Cero exposición de datos personales ni clínicos identificables en DTOs de reporte.
2. **Motor y Servicio del Asistente del Sistema (RF-27):**
   - Servicio `AssistantService` expuesto vía `POST /api/v1/assistant/chat`:
     - Detección inmediata de síntomas o términos de alarma (dolor torácico, ahogo severo, pérdida de conocimiento, sangrado masivo, etc.): genera respuesta prioritaria de emergencia con instrucciones de acudir a urgencias o llamar al 123 y enlaces de soporte inmediato.
     - Procesamiento de intenciones temáticas basado en base de conocimiento curada de MediTriaje 2.0:
        - Triaje y niveles de prioridad (I a V).
        - Agendamiento y cancelación de citas (regla de anticipación de 2 horas).
        - Farmacia y reclamación con código `REC-XXXXXXXX`.
        - Resumen de salud y QR temporal de emergencia.
        - Derechos del paciente, inmutabilidad de historia clínica y enmiendas.
     - Inclusión en cada respuesta de sugerencias interactivas de acción (rutas directas SPA como `#/patient/triage`, `#/patient/book`, `#/patient/prescriptions`) y aviso legal permanente: *"Soy un asistente de orientación para MediTriaje 2.0. No sustituyo la valoración médica profesional."*

## ADR-019 Visor de auditoría de seguridad y exportación de reportes operativos (RF-26, RF-30, RNF-11)
**Estado:** PROPUESTO — pendiente de aprobación de Juan.
**Contexto:**
1. *Visor de Auditoría de Seguridad (RF-26, RNF-11):* La tabla inmutable `AUDITORIA` registra de forma fidedigna y no repudiable todos los eventos sensibles del sistema (inicios de sesión, creación de atenciones, emisiones de recetas, dispensación farmacéutica, cortes de emergencia de triaje y activaciones Break-Glass). No obstante, para facilitar la labor del Oficial de Seguridad de la Información y Cumplimiento Hospitalario, se requiere una interfaz web protegida que permita consultar, filtrar y revisar la trazabilidad de accesos sin exponer diagnósticos ni notas confidenciales (ADR-007, ADR-011).
2. *Exportación de Reportes Operativos (RF-30):* El personal administrativo necesita descargar y consolidar las métricas de rendimiento hospitalario (citas por estado, triajes por nivel, demanda por especialidad y sede, y balance de farmacia) en archivos planos estandarizados (CSV delimitado con UTF-8) para su análisis en herramientas de BI o informes a comités directivos.
**Decisión:**
1. **Consulta Controlada de Auditoría (ADR-007, ADR-011):**
   - Endpoints bajo `/api/v1/admin/audit` protegidos estrictamente con `@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")`.
   - Repositorio `AuditoriaRepository` expone métodos de lectura paginada (`OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`) filtrando por rango de fechas (`America/Bogota`), tipo de acción (`AccionAuditable`) y resultado (`EXITO` / `FALLO`).
   - Se proyecta `USUARIO.EMAIL`, `ACCION`, `TIPO_RECURSO`, `RECURSO_PUBLIC_ID`, `RESULTADO`, `IP_ORIGEN` y `FECHA_HORA`.
   - Cero exposición de datos clínicos ni notas sensibles del paciente.
2. **Exportación Estructurada de Reportes a CSV (RF-30):**
   - El dashboard de reportes del frontend (`admin-reports.js`) genera y descarga archivos CSV client-side (`text/csv;charset=utf-8;`) respetando el rango temporal seleccionado.
   - Incluye secciones para Resumen de Indicadores, Desglose de Citas, Distribución de Triajes y Farmacia.

## ADR-020 Validación de formato de identidad y transporte de correo (F2.8)
**Estado:** PROPUESTO — pendiente de aprobación de Juan.
**Contexto:** En el registro de pacientes, gestión de usuarios y despacho de notificaciones, el sistema requiere validar la sintaxis y rangos etarios de documentos nacionales, números de contacto celular, y asegurar el transporte de correos institucionales sin bloqueos de infraestructura.
**Decisión:**
1. **Validación de Identidad y Coherencia Etaria (en memoria):**
   - El sistema valida exclusivamente en memoria (mediante `NormaColombianaValidator`) el formato y la coherencia de datos, sin consultar servicios externos (no consulta la Registraduría Nacional ni ReTHUS):
     - Tipos documentales admitidos: CC (6–10 dígitos numéricos), TI (10–11 dígitos numéricos), RC (10–11 dígitos numéricos), CE (3–10 caracteres alfanuméricos), PA (5–20 caracteres alfanuméricos).
     - Rango de edad por documento: CC (≥ 18 años), TI (7 a 17 años cumplidos), RC (< 7 años). CE y PA sin restricción de rango etario (máximo 125 años; fecha de nacimiento no futura).
     - Nombres y apellidos: entre 2 y 60 caracteres alfabéticos válidos en español (incluye espacios, tildes y guiones).
     - Teléfono celular: formato opcional para Colombia de 10 dígitos iniciando por 3 o prefijo internacional `+573XXXXXXXXX`.
     - Registro médico de profesionales: validación de tamaño y formato en `AdminProfessionalService`; el sistema almacena el registro médico provisto sin conectarse a bases de datos externas de talento humano en salud.
2. **Transporte de Correo por API HTTP de Brevo:**
   - En producción, el envío de correos se efectúa mediante la **API HTTP de Brevo** (puerto HTTPS 443 estándar a `https://api.brevo.com/v3/smtp/email`) con `HttpClient` nativo de Java 21, mitigando el bloqueo de puertos SMTP salientes (25, 465, 587) en la capa gratuita de Render.
   - En perfiles locales o de desarrollo, se admite transporte SMTP o simulación en log (`LogEmailTransport`).
   - `EmailService` devuelve el record explícito `ResultadoEnvio(boolean exito, String codigo, String mensaje)`. Los fallos de envío de recordatorios de citas se persisten como `FALLIDO` en `RECORDATORIO_CITA`.
   - Recuperación de contraseña (`forgot-password`) audita internamente `EMAIL_FALLIDO` ante fallos de entrega pero mantiene la respuesta HTTP 200 genérica anti-enumeración.
   - Sanitización estricta: censura de API key, censura de etiquetas HTML y enmascaramiento de direcciones de correo PII en logs. Cero variables clínicas o terminología de salud en plantillas de correo.

## ADR-021 Ruta de Actualización y Transición hacia Spring Boot 4.1 (Trabajo Futuro)
**Estado:** PROPUESTO — Plan Estratégico de Evolución Técnica (Trabajo Futuro).
**Contexto:**
Con la ejecución de la tarea T12, MediTriaje 2.0 opera sobre **Spring Boot 3.5.16** (el último parche abierto y mantenido de la línea 3.x), resolviendo la obsolescencia de 3.3.4 (SEC-006) y alcanzando estabilidad completa con 948 pruebas verdes.
No obstante, el ecosistema Spring avanza hacia su próxima generación mayor (**Spring Boot 4.x / 4.1**), la cual involucra tres evoluciones estructurales de ruptura (*breaking changes*):
1. **Spring Framework 7.x:** Nuevo baseline centrado en Java 21/25, APIs reactivas refinadas, optimización exhaustiva para hilos virtuales (Project Loom) y compilación nativa AOT (GraalVM).
2. **Spring Security 7.x:** Retiro definitivo de APIs y adaptadores declarados obsoletos en la serie 6.x, reestructuración de la jerarquía de `SecurityFilterChain` y actualización de políticas de autorización modular.
3. **Jackson 3 (`tools.jackson`):** Cambio radical de coordenadas y paquetes desde `com.fasterxml.jackson` hacia `tools.jackson`, reescritura de deserializadores y optimización nativa para Java Records y sellado de tipos (`sealed classes`) sin configuración reflectiva adicional.

**Decisión y Hoja de Ruta:**
1. **Estabilidad Actual en 3.5.16 (Fase Vigente):**
   - El entorno productivo y académico del MVP permanecerá en Spring Boot 3.5.16 para garantizar estabilidad operativa, compatibilidad garantizada con Oracle ATP (OJDBC 23.5) y cero riesgo de regresión en las 948 pruebas automatizadas.
2. **Preparación Previa a la Migración:**
   - Mantener el aislamiento estricto de DTOs y modelos de dominio basados en Java 21 Records inmutables, facilitando la futura transición a los deserializadores nativos de Jackson 3.
   - Evitar el acoplamiento directo en controladores a clases internas de `com.fasterxml.jackson` más allá de la serialización estándar HTTP de Spring Web.
   - Resolver oportunamente advertencias de compilación (`-Xlint:deprecation`) en controladores y configuraciones de seguridad.
3. **Migración Experimental Controlada (Spike Futuro):**
   - La transición a Spring Boot 4.1 se ejecutará como un hito formal independiente en una rama experimental (`spike/spring-boot-4`), una vez que Spring Boot 4.1 cuente con versiones de producción estables (GA).
   - Validar compatibilidad de librerías criptográficas satélite: BouncyCastle, JJWT (soporte para Jackson 3) y el driver de Oracle JDBC.
   - Ejecutar la suite integral de verificación: 948+ pruebas unitarias Surefire, integración en base de datos real con Testcontainers (Failsafe) y pruebas de extremo a extremo con Playwright.

## ADR-022 Arquitectura de Urgencias y Episodios Clínicos Desacoplados de Citas
**Estado:** PROPUESTO — Plan Maestro Hospitalario.
**Contexto:**
El modelo original vincula estrictamente cada atención médica (`ATENCION_MEDICA`) a una cita previa obligatoria (`cita_id NOT NULL UNIQUE`). En urgencias hospitalarias, los pacientes ingresan de manera espontánea, por ambulancia o remitidos, sin cita previa agendada.
**Decisión Propuesta:**
1. Introducir el concepto de `EPISODIO_ATENCION` (tipo: URGENCIA, CONSULTA, HOSPITALIZACION) con ciclo de vida independiente.
2. Mantener retrocompatibilidad absoluta: la consulta externa continúa usando citas sin regresión; en urgencias, la atención se vincula al `episodio_id`.
3. Ningún ingreso de urgencia puede ser bloqueado por ausencia de cita, falta de correo electrónico o falta de cuenta web.

## ADR-023 Identidad Provisional y Atención a Pacientes No Identificados (NN)
**Estado:** PROPUESTO — Plan Maestro Hospitalario.
**Contexto:**
En urgencias pueden ingresar pacientes inconscientes, menores sin documento o personas indocumentadas (NN). El modelo actual de `PACIENTE` exige documento civil y usuario web obligatorio.
**Decisión Propuesta:**
1. Crear `IDENTIDAD_PROVISIONAL` con un código aleatorio opaco único (sin asignar cédula falsa ni inventar datos).
2. Permitir la reconciliación transaccional auditada hacia un expediente verificado cuando la identidad civil sea confirmada por personal autorizado, preservando inalterado el historial asistencial previo.

## ADR-024 Jerarquía de Instalaciones y Gestión Concurrente de Camas Hospitalarias
**Estado:** PROPUESTO — Plan Maestro Hospitalario.
**Contexto:**
La plataforma cuenta únicamente con sedes institucionales. La gestión hospitalaria requiere unidades, zonas, salas, habitaciones y camas con control estricto de concurrencia.
**Decisión Propuesta:**
1. Estructura jerárquica acotada: `SEDE -> UNIDAD -> ZONA -> SALA -> HABITACION -> CAMA`.
2. Ocupación transaccional con restricción única en Oracle: exactamente una ocupación abierta por cama (`OCUPACION_CAMA`). Bloqueo pesimista o índice funcional único que garantice rechazo con 409 ante concurrencia sin sobreasignación.
3. Registro append-only de movimientos hospitalarios (`MOVIMIENTO_PACIENTE`).

## ADR-025 Afiliaciones Administrativas e Importación Asíncrona en Dos Pasos de Lotes Excel
**Estado:** PROPUESTO — Plan Maestro Hospitalario.
**Contexto:**
La verificación de cobertura de EPS requiere procesar padrones administrativos masivos sin exponer el sistema a vulnerabilidades de subida ni bloquear la atención médica.
**Decisión Propuesta:**
1. Proceso de importación seguro en 2 pasos: `preview` (validación en memoria, neutralización de fórmulas, reporte por fila de errores) y `commit` (aplicación atómica o por filas válidas con token de lote e idempotencia por hash).
2. La importación de afiliaciones NO crea cuentas web ni contraseñas.
3. La ausencia de afiliación a EPS o el estado desconocido NUNCA bloquea el ingreso ni la estabilización en urgencias (cumplimiento Ley Estatutaria de Salud Colombia).

## ADR-026 Perfil y Permisos Asistenciales de Enfermería en el Modelo RBAC
**Estado:** PROPUESTO — Plan Maestro Hospitalario.
**Contexto:**
El sistema actual soporta PACIENTE, PROFESIONAL, ADMINISTRADOR y FARMACEUTICO. El circuito hospitalario de urgencias requiere el rol de ENFERMERIA para recepción, admisión provisional, triaje presencial y administración de cuidados.
**Decisión Propuesta:**
1. Incorporar la autoridad `ROLE_ENFERMERIA` con menú, vistas y endpoints acotados.
2. Enfermería puede admitir urgencias, registrar identidades provisionales, realizar valoración presencial de triaje y reevaluaciones.
3. Se prohíbe a enfermería prescribir recetas médicas, cerrar historias clínicas diagnósticas o alterar registros administrativos globales.

## ADR-027 Protocolo de Triaje Presencial Humano con Estados Pendientes y Reevaluación Append-Only
**Estado:** PROPUESTO — Plan Maestro Hospitalario.
**Contexto:**
El triaje actual es un cuestionario web de autoorientación para cita ambulatoria (20 síntomas, prototipo con fallback III). En urgencias presenciales rige la Resolución 5596/2015 del Ministerio de Salud.
**Decisión Propuesta:**
1. Separar tajantemente el triaje web ambulatorio de la valoración clínica presencial de urgencias.
2. En urgencias, todo paciente recién ingresado inicia en `PENDIENTE_VALORACION`; nunca se le asigna un nivel automático por omisión.
3. La clasificación en niveles I a V la firma un profesional de salud habilitado (enfermería/médico) registrando signos vitales y motivo.
4. Cada reevaluación genera un nuevo registro inmutable (`VALORACION_TRIAJE`), conservando el historial cronológico completo.

## ADR-028 Política de Retención de Documentos, Trazabilidad y Diseño de Interoperabilidad
**Estado:** PROPUESTO — Plan Maestro Hospitalario.
**Contexto:**
Se requiere trazabilidad completa para eventos clínicos y administrativos, salvaguardando la confidencialidad (Ley 1581/2012) y preparando el sistema para futura interoperabilidad.
**Decisión Propuesta:**
1. Trazabilidad append-only en `EVENTO_AUDITORIA` sin registrar datos clínicos ni identificaciones legibles en logs técnicos.
2. Identificación operativa mediante pulsera con código QR opaco, independiente del QR de resumen clínico temporal.
3. Diseño de contratos candidatos alineados conceptualmente con estándares de interoperabilidad (RIPS, HL7 FHIR), sin asumir conectores activos no autorizados.

---

## Pendientes reales
- **Reglas exactas de triaje**: requieren revisión de un profesional de salud.
- **Nombre/dominio definitivo** y **diseño visual**: no bloquean el MVP.


