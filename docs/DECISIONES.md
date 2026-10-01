# MediTriaje 2.0 — Decisiones técnicas (ADRs)

Estado: **PROPUESTAS POR DEFECTO**. Quedan aprobadas cuando Juan las confirme; desde entonces el agente no las reabre sin avisar.
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
**Decisión:** zona del proyecto `America/Bogota`. Instantes en `TIMESTAMP WITH TIME ZONE`; fechas de nacimiento en `DATE`. La API usa ISO-8601 con offset.

## ADR-006 Citas, disponibilidad y concurrencia
**Disponibilidad:** tabla `DISPONIBILIDAD_SLOT` generada por el admin (profesional, sede, especialidad, modalidad, inicio, fin, estado LIBRE/OCUPADO/BLOQUEADO). Duración por defecto 20 min, configurable por especialidad.

**Estados de cita y transiciones permitidas:**
```
PROGRAMADA  → CONFIRMADA | CANCELADA | NO_ASISTIO | REPROGRAMADA
CONFIRMADA  → ATENDIDA   | CANCELADA | NO_ASISTIO | REPROGRAMADA
(ATENDIDA, CANCELADA, NO_ASISTIO, REPROGRAMADA son finales)
```
**Cancelación:** el paciente hasta 2 h antes del inicio; después, solo profesional/admin. Reprogramar = cancelar + nueva cita con `cita_origen_id`.

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
- Reglas en tabla `REGLA_TRIAJE` versionada (síntoma, condición, nivel, bandera de alarma), no en el código. Cada triaje guarda la versión de reglas usada.
- Síntomas de alarma ⇒ **corte de emergencia**: mensaje "llama al 123 o ve a urgencias", sin ofrecer cita, evento registrado.
- Catálogo semilla pequeño (≈20 síntomas), marcado como **reglas de prototipo, no validadas clínicamente**. Si se quiere validez clínica, debe revisarlas personal de salud.
- Ruta sugerida: Atención prioritaria / Cita presencial / Cita remota / Consulta programada.
- El triaje nunca produce un diagnóstico ni recomienda medicamentos.

## ADR-010 QR temporal (fase 2)
**Defaults:** token aleatorio de 256 bits, expira a los 15 min, máximo 3 accesos, revocable. Alcance mínimo: alergias, medicamentos activos y antecedentes relevantes. Lectura sin login, con límite de intentos y PIN opcional. Cada acceso se audita. El QR contiene solo una URL con el token, nunca datos.

## ADR-011 Auditoría
**Decisión:** tabla `AUDITORIA` insert-only (usuario, acción, tipo de recurso, id de recurso, resultado, IP, fecha). Prohibido guardar contenido clínico o secretos. Eventos del MVP listados en HU-11.

## ADR-012 Entornos, backups y despliegue
- Desarrollo local con Oracle Free en Docker (o ATP directo) y pruebas de integración con Testcontainers.
- Perfiles `dev`, `test`, `prod`; secretos por variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`). Wallet fuera del repo y en `.gitignore`.
- Backups: los automáticos de ATP; documentar en `DATABASE.md` cómo restaurar.
- Despliegue: demo local en el MVP; luego backend + frontend en OCI (cuenta Always Free) conservando HTTPS. El frontend nunca habla con Oracle.

## ADR-013 Datos personales y cumplimiento (verificar con la norma vigente)
- Consentimiento de tratamiento de datos (Ley 1581 de 2012) obligatorio en el registro, guardado en `CONSENTIMIENTO` con versión y fecha.
- Datos de salud = datos sensibles: mínimo acceso, mínima exposición, sin datos reales en pruebas.
- Historia clínica: no eliminación; conservación según Res. 1995 de 1999 (consultar plazo exacto). Interoperabilidad (Ley 2015 de 2020): fuera del MVP, pero no bloquear su evolución.
- Catálogos: CIE-10 reducido para diagnósticos; tipos de documento CC, TI, RC, CE, PA. Código CUM de medicamentos: fase 2.

---

## Pendientes reales
- **Reglas exactas de triaje**: requieren revisión de un profesional de salud.
- **Nombre/dominio definitivo** y **diseño visual**: no bloquean el MVP.
