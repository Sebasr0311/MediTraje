# API.md — Catálogo y Especificación de la API REST

> **Versión de API:** v1  
> **Prefijo Base:** `/api/v1`  
> **Formato de Intercambio:** `application/json` (UTF-8)  
> **Zona Horaria del Sistema:** `America/Bogota` (UTC-5)  
> **Colecciones de Prueba:** `docs/api/M2.http` a `docs/api/M7.http`

---

## 1. Convenciones y Principios Generales

### Identificadores Públicos Ocultos (ADR-003)
Todas las rutas de la API utilizan identificadores únicos universales (`publicId` en formato UUIDv4). **Bajo ninguna circunstancia se exponen claves autonuméricas internas de la base de datos**.

### Autenticación y Manejo de Sesiones (ADR-002)
- La autenticación utiliza tokens JWT de acceso (vida útil de 15 minutos) y Refresh Tokens opacos rotativos (vida útil de 7 días).
- Los tokens son transmitidos **exclusivamente mediante cookies `HttpOnly; Secure; SameSite=Strict; Path=/`** generadas por el backend.
- Como mecanismo secundario/móvil se soporta la cabecera `Authorization: Bearer <token>`.

### Protección CSRF Obligatoria (ADR-002)
Toda solicitud que altere el estado en el servidor (`POST`, `PUT`, `PATCH`, `DELETE`) requiere obligatoriamente una cabecera personalizada de protección CSRF:
```http
X-Requested-With: XMLHttpRequest
```
Si la cabecera está ausente, el servidor rechaza la petición de inmediato con código `403 Forbidden` (`CSRF_REQUERIDO`).

### Estructura Uniforme de Errores (`ApiError`)
Todas las respuestas de error utilizan la estructura canónica sin trazas de depuración:
```json
{
  "codigo": "RECURSO_NO_ENCONTRADO",
  "mensaje": "La cita solicitada no existe.",
  "timestamp": "2026-10-03T20:30:00Z",
  "traceId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "detalles": null
}
```

### Respuestas Paginadas Estándar (`PaginatedResponse<T>`)
```json
{
  "content": [ ... ],
  "page": 0,
  "size": 10,
  "totalElements": 45,
  "totalPages": 5,
  "hasNext": true,
  "hasPrevious": false
}
```

---

## 2. Autenticación y Cuenta (`/api/v1/auth`)

| Método | Endpoint | Roles Permitidos | Descripción |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Anónimo | Registro atómico de paciente (crea usuario, perfil, consentimiento informado v1.0 y rol). |
| `POST` | `/api/v1/auth/login` | Anónimo | Inicio de sesión con correo y contraseña. Genera cookies `access_token` y `refresh_token`. Bloqueo tras 5 intentos. |
| `POST` | `/api/v1/auth/refresh` | Con cookie `refresh_token` | Rotación obligatoria de refresh token y emisión de nuevo token de acceso. Detección de reuso. |
| `POST` | `/api/v1/auth/logout` | Autenticado | Invalida el refresh token en base de datos y borra las cookies del navegador (`Max-Age=0`). |
| `POST` | `/api/v1/auth/change-password` | Autenticado | Cambio de contraseña (obliga al médico o paciente a actualizar clave temporal o expirable). |

---

## 3. Pacientes (`/api/v1/patients`)

| Método | Endpoint | Roles Permitidos | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/patients/me` | `ROLE_PACIENTE` | Retorna los datos del paciente autenticado (nombres, documento, fecha de nacimiento, estado). |
| `GET` | `/api/v1/patients/me/appointments` | `ROLE_PACIENTE` | Listado paginado de todas las citas del paciente autenticado con filtros opcionales por estado y fecha. |
| `GET` | `/api/v1/patients/me/history` | `ROLE_PACIENTE` | Historia clínica del paciente: atenciones cerradas con diagnósticos CIE-10, signos vitales y enmiendas. |
| `GET` | `/api/v1/patients/me/prescriptions` | `ROLE_PACIENTE` | Historial de recetas emitidas al paciente, con snapshots inmutables de fármacos y vigencia. |

---

## 4. Disponibilidad y Citas (`/api/v1/availability`, `/api/v1/appointments`)

| Método | Endpoint | Roles Permitidos | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/availability` | Autenticado | Consulta de slots libres futuros (`ESTADO = 'LIBRE'`). Filtros: `especialidad`, `sede`, `fecha`, `modalidad`. |
| `POST` | `/api/v1/appointments` | `ROLE_PACIENTE`, `ROLE_ADMINISTRADOR` | Reserva atómica de un slot libre. Maneja colisiones concurrentes (`409 Conflict`). Vincula triaje opcional. |
| `GET` | `/api/v1/appointments/{publicId}` | Autenticado (con autorización) | Consulta detallada de una cita médica. Aislada por paciente o profesional responsable. |
| `PATCH` | `/api/v1/appointments/{publicId}/cancel` | Autenticado (con autorización) | Cancelación de cita. Paciente: hasta 2 horas antes de la cita. Médico o Admin: sin restricción de 2 horas. |

---

## 5. Agenda del Profesional Asistencial (`/api/v1/professionals`)

| Método | Endpoint | Roles Permitidos | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/professionals/me/agenda` | `ROLE_PROFESIONAL` | Agenda del médico autenticado. Administradores y pacientes reciben `403 Forbidden`. Filtro por fecha (`yyyy-MM-dd`) y estado. |

---

## 6. Triaje Clínico (`/api/v1/triage`)

| Método | Endpoint | Roles Permitidos | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/triage/symptoms` | Autenticado | Catálogo de síntomas activos clasificados por categoría, con indicación de bandera `esAlarma`. |
| `POST` | `/api/v1/triage` | `ROLE_PACIENTE` | Evaluación del triaje por motor determinista. Si hay síntomas de alarma o Nivel I, genera corte de emergencia infalible. |
| `GET` | `/api/v1/triage/{publicId}` | Autenticado (propietario) | Consulta del resultado del triaje. Pacientes aislados entre sí (`403 Forbidden` ante triaje ajeno). |

---

## 7. Atención Médica e Historia Clínica (`/api/v1/attentions`)

| Método | Endpoint | Roles Permitidos | Descripción |
|---|---|---|---|
| `POST` | `/api/v1/attentions` | `ROLE_PROFESIONAL` | Inicia una atención médica vinculada a una cita programada. La cita pasa atómicamente a `CONFIRMADA`. |
| `POST` | `/api/v1/attentions/{publicId}/close` | `ROLE_PROFESIONAL` (autor) | Cierra irreversiblemente la atención registrando signos vitales y diagnóstico CIE-10. Cita pasa a `ATENDIDA`. |
| `GET` | `/api/v1/attentions/{publicId}` | Autenticado (con relación) | Consulta el detalle clínico de la atención. Requiere relación asistencial activa (ADR-007). Admin bloqueado (`403`). |
| `POST` | `/api/v1/attentions/{publicId}/amendments` | `ROLE_PROFESIONAL` | Registra una enmienda append-only sobre una atención ya cerrada. Inmutable y auditada. |
| `GET` | `/api/v1/catalogs/icd10` | Autenticado | Catálogo estándar de diagnósticos CIE-10 activos con búsqueda por código o descripción. |

---

## 8. Recetas Médicas (`/api/v1/prescriptions`, `/api/v1/catalogs/medications`)

| Método | Endpoint | Roles Permitidos | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/catalogs/medications` | Autenticado | Catálogo maestro de medicamentos activos con búsqueda por nombre comercial, principio activo o código. |
| `POST` | `/api/v1/prescriptions` | `ROLE_PROFESIONAL` | Emisión atómica de receta médica sobre una atención médica. Congela snapshot histórico cuádruple de cada medicamento. |
| `GET` | `/api/v1/prescriptions/{publicId}` | Autenticado (con relación) | Consulta de receta médica con sus ítems prescritos e indicaciones. Admin bloqueado (`403 Forbidden`). |

---

## 9. Administración del Sistema (`/api/v1/admin/*`)

> **Restricción Universal:** Todos los endpoints bajo `/api/v1/admin/` exigen `@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")`. Ninguno de estos endpoints expone ni procesa contenido clínico.

### Instituciones de Salud
* `POST /api/v1/admin/institutions`: Alta de institución de salud con NIT y Razón Social.
* `GET /api/v1/admin/institutions`: Listado paginado de instituciones con filtros por estado.
* `GET /api/v1/admin/institutions/{id}`: Detalle de institución.
* `PUT /api/v1/admin/institutions/{id}`: Edición de razón social.
* `PATCH /api/v1/admin/institutions/{id}/deactivate`: Desactivación lógica de institución.
* `PATCH /api/v1/admin/institutions/{id}/activate`: Activación lógica.

### Sedes Asistenciales
* `POST /api/v1/admin/sites`: Alta de sede vinculada a una institución activa preexistente.
* `GET /api/v1/admin/sites`: Listado paginado con filtro por institución y estado.
* `GET /api/v1/admin/sites/{id}`: Detalle de sede.
* `PUT /api/v1/admin/sites/{id}`: Edición de nombre, dirección y ciudad.
* `PATCH /api/v1/admin/sites/{id}/deactivate`: Desactivación lógica.
* `PATCH /api/v1/admin/sites/{id}/activate`: Activación lógica.

### Especialidades Médicas
* `POST /api/v1/admin/specialties`: Alta de especialidad médica con duración por defecto de turno (5 a 240 min).
* `GET /api/v1/admin/specialties`: Listado paginado con filtro de estado.
* `GET /api/v1/admin/specialties/{id}`: Detalle de especialidad.
* `PUT /api/v1/admin/specialties/{id}`: Edición de nombre y duración.
* `PATCH /api/v1/admin/specialties/{id}/deactivate`: Desactivación lógica.
* `PATCH /api/v1/admin/specialties/{id}/activate`: Activación lógica.

### Profesionales Asistenciales
* `POST /api/v1/admin/professionals`: Alta de profesional médico. Genera credenciales de acceso con contraseña temporal segura (Argon2id) que se devuelve una sola vez al administrador.
* `GET /api/v1/admin/professionals`: Listado paginado con filtro por especialidad y estado.
* `GET /api/v1/admin/professionals/{id}`: Detalle de profesional.
* `PUT /api/v1/admin/professionals/{id}`: Edición de nombres, apellidos y especialidad.
* `PATCH /api/v1/admin/professionals/{id}/deactivate`: Desactivación de cuenta asistencial.
* `PATCH /api/v1/admin/professionals/{id}/activate`: Activación de cuenta asistencial.

### Generador y Gestión de Slots
* `POST /api/v1/admin/slots/generate`: Generación masiva de turnos de atención continua en zona `America/Bogota` por rango de fechas, franja horaria diaria, días seleccionados y modalidad (Presencial/Telemedicina). Previene solapes atómicamente.
* `GET /api/v1/admin/slots`: Consulta paginada de turnos con filtros por profesional, sede, fechas y estado (`LIBRE`, `BLOQUEADO`, `OCUPADO`).
* `GET /api/v1/admin/slots/{id}`: Detalle del slot.
* `PATCH /api/v1/admin/slots/{id}/block`: Bloqueo administrativo de slot libre.
* `PATCH /api/v1/admin/slots/{id}/unblock`: Desbloqueo de slot.
* `DELETE /api/v1/admin/slots/{id}`: Eliminación física condicionada exclusivamente a slots en estado `LIBRE`.

---

## 10. Monitoreo y Diagnóstico

| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/ping` | Público | Verificación de estado del servidor (`{"status":"UP"}`). Retorna cabeceras HTTP de seguridad. |
| `GET` | `/actuator/health` | Público (básico) / Autorizado | Health check de la API y verificación del pool de base de datos HikariCP Oracle. |
| `GET` | `/actuator/info` | Público | Información de compilación y versión del artefacto. |
