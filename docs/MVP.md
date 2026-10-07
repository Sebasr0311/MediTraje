# MediTriaje 2.0 — MVP (v0.1)

## 1. Objetivo
Demostrar, de forma segura y auditada, el flujo mínimo completo:

**Paciente se registra → hace triaje → recibe ruta → agenda cita → profesional registra atención y receta → paciente consulta su historia y receta.**

## 2. Alcance

| Dentro del MVP | Fase 2 (después del MVP) |
|---|---|
| Registro, login, roles (PACIENTE, PROFESIONAL, ADMIN) | MFA, recuperación de contraseña |
| Triaje con reglas simples y corte de emergencia | Motor de reglas avanzado, ajuste clínico validado |
| Instituciones, sedes, especialidades, profesionales | Servicios/modalidades complejas, telemedicina |
| Disponibilidad por slots, cita, cancelación | Reprogramación avanzada, lista de espera |
| Atención, signos vitales, diagnóstico CIE-10 (catálogo reducido) | Documentos/resultados, archivos |
| Receta + detalle, catálogo básico de medicamentos | Dispensación/reclamación, tratamientos |
| Historia y recetas del paciente (solo lectura) | Seguimiento post-atención, recordatorios |
| Consentimiento de datos, auditoría | Resumen de salud, QR temporal, acceso de emergencia |
| Dashboard mínimo por rol | Asistente/chatbot, reportes, notificaciones |

## 3. Historias de usuario y criterios de aceptación

**HU-01 Registro e inicio de sesión (paciente)**
- Registro con tipo y número de documento, nombres, fecha de nacimiento, correo y contraseña.
- Consentimiento de tratamiento de datos obligatorio; se guarda con fecha y versión del texto.
- Contraseña de mínimo 10 caracteres; bloqueo temporal tras 5 intentos fallidos.
- El error de login no revela si el correo existe. Logout invalida el refresh token.

**HU-02 Triaje**
- El paciente elige síntomas del catálogo, duración e intensidad (0–10).
- Si hay un síntoma de alarma: pantalla de emergencia (llamar al 123 / ir a urgencias), no se ofrece cita, el evento queda registrado.
- Resultado: nivel I–V, ruta sugerida, texto de orientación y aviso de que no sustituye valoración profesional.
- Determinista: mismas entradas → mismo resultado. Se guarda la versión de reglas usada.

**HU-03 Consultar disponibilidad**
- Filtros: especialidad, fecha, sede, modalidad. Solo devuelve slots LIBRES reales.

**HU-04 Agendar cita**
- Una cita ocupa un slot; opcionalmente se vincula al triaje.
- Dos solicitudes simultáneas al mismo slot: una gana, la otra recibe error `CitaNoDisponible` (con prueba concurrente).

**HU-05 Cancelar cita y registrar inasistencia**
- El paciente cancela hasta 2 horas antes de la hora fijada. El slot se libera a `LIBRE`. Queda auditado.
- El profesional asignado a la cita o el administrador pueden registrar inasistencia (`NO_ASISTIO`) una vez transcurrida o alcanzada la hora de inicio de la cita. El slot permanece `OCUPADO`.
- Solo transiciones de estado activas permitidas (ADR-006, Decisión D2: `PROGRAMADA → CANCELADA | NO_ASISTIO | ATENDIDA`). Los estados `CONFIRMADA` y `REPROGRAMADA` quedan reservados fuera de alcance.
- Si existe una atención clínica vinculada a la cita (abierta o cerrada), no se puede cancelar ni marcar inasistencia (409 Conflicto).

**HU-06 Agenda del profesional**
- El profesional ve únicamente sus citas.

**HU-07 Registrar atención**
- Solo el profesional con relación asistencial vigente con el paciente (ADR-007).
- Campos: motivo, evolución, signos vitales, diagnóstico CIE-10, indicaciones.
- Registro y consulta de alergias clínicas del paciente (y consulta de autorreportadas por el paciente). Inactivables con motivo obligatorio, sin borrado físico (`TR_ALERGIA_INMUTABILIDAD`, V016).
- Iniciar la atención mantiene la cita en estado PROGRAMADA.
- Al cerrar, la atención es inmutable; las correcciones se hacen con enmienda (ADR-008).
- La cita pasa a ATENDIDA al cerrar la atención (Decisión D2). El acceso queda auditado.

**HU-08 Crear receta**
- Solo el profesional responsable de la atención o con relación asistencial activa.
- Consulta previa de alergias activas del paciente en el panel de prescripción ("Sin alergias registradas (esto no confirma que no tenga)").
- Receta y detalles en una sola transacción atómica (todo o nada, rollback si falla un detalle).
- El detalle guarda copia (snapshot) del nombre y presentación del medicamento.

**HU-09 Paciente consulta su información**
- Ve sus citas, historia, recetas y alergias propias. Intentar ver las de otro paciente → 403/404 (con prueba).

**HU-10 Administración**
- Gestiona instituciones, sedes, especialidades, profesionales y generación de slots.
- Intentar leer contenido clínico → 403 (con prueba).

**HU-11 Auditoría**
- Se registran: login (éxito/fallo), acceso a historia, creación/cierre de atención, creación de receta, cancelación de cita, inasistencia, cambios administrativos.
- Sin datos clínicos en el registro, solo usuario, acción, recurso, id y resultado.

## 4. Tablas mínimas (propuesta)
`USUARIO, ROL, USUARIO_ROL, PACIENTE, PROFESIONAL, ESPECIALIDAD, INSTITUCION, SEDE, DISPONIBILIDAD_SLOT, SINTOMA, REGLA_TRIAJE, TRIAJE, TRIAJE_SINTOMA, CITA, ATENCION, SIGNO_VITAL, ATENCION_ENMIENDA, DIAGNOSTICO_CIE10, ALERGIA, MEDICAMENTO, RECETA, RECETA_DETALLE, CONSENTIMIENTO, AUDITORIA`

Se omiten por ahora: PERMISO/ROL_PERMISO (roles simples), SERVICIO (se fusiona con ESPECIALIDAD), DISPENSACION, TRATAMIENTO, SEGUIMIENTO, RESUMEN_SALUD, ACCESO_QR.

## 5. Endpoints del MVP
```
POST  /api/v1/auth/register | login | refresh | logout
GET   /api/v1/patients/me
GET   /api/v1/patients/me/appointments | history | prescriptions | allergies
POST  /api/v1/patients/me/allergies | PATCH /api/v1/patients/me/allergies/{id}/deactivate
POST  /api/v1/triage            GET /api/v1/triage/{id}
GET   /api/v1/availability
POST  /api/v1/appointments      PATCH /api/v1/appointments/{id}/cancel | /no-show
GET   /api/v1/professionals/me/agenda
POST  /api/v1/attentions        POST /api/v1/attentions/{id}/close
POST  /api/v1/attentions/{id}/amendments
GET/POST /api/v1/clinical/patients/{id}/allergies | PATCH /api/v1/clinical/allergies/{id}/deactivate
POST  /api/v1/prescriptions
GET   /api/v1/medications       GET /api/v1/catalogs/icd10
CRUD  /api/v1/admin/{institutions|sites|specialties|professionals|slots}
```

## 6. Fases del MVP

| Fase | Entregable |
|---|---|
| M0 | Requisitos finales, MER, modelo relacional, ADRs aprobados |
| M1 | Proyecto Java base, Oracle ATP conectado, Flyway, CI de pruebas |
| M2 | Auth, roles, consentimiento, auditoría (HU-01, HU-11) |
| M3 | Admin: instituciones, especialidades, profesionales, slots (HU-10) |
| M4 | Disponibilidad y citas con concurrencia (HU-03, 04, 05, 06) |
| M5 | Triaje con corte de emergencia (HU-02) |
| M6 | Atención e historia clínica inmutable (HU-07, HU-09) |
| M7 | Recetas y medicamentos (HU-08) |
| M8 | Frontend de punta a punta, dashboards mínimos, pruebas de seguridad, demo |

El triaje (M5) puede ir antes de las citas si se prefiere el orden natural del flujo; solo depende de M1–M2.

## 7. Pruebas obligatorias antes de cerrar el MVP
- Paciente A no accede a nada de paciente B (por ID manipulado).
- Admin no accede a historia clínica ni recetas.
- Profesional sin relación asistencial no accede a un paciente.
- Doble reserva concurrente del mismo slot.
- Atención cerrada no se puede modificar (ni por API ni por SQL directo desde la app).
- Rollback de receta si falla un detalle.
- Triaje: síntoma de alarma siempre produce corte de emergencia.
- Tokens expirados, revocados o manipulados son rechazados.
- No hay datos clínicos ni secretos en logs.

## 8. Guion de demo
1. Paciente se registra y acepta el consentimiento.
2. Hace un triaje de baja prioridad → ruta → ve disponibilidad → agenda.
3. Hace un triaje con síntoma de alarma → pantalla de emergencia, sin cita.
4. Profesional abre su agenda, atiende, registra diagnóstico y receta.
5. Paciente ve su historia y su receta.
6. Intento de acceso cruzado → denegado. Admin intenta ver la historia → denegado.
7. Se muestra la tabla de auditoría.
