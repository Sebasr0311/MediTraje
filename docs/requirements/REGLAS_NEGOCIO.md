# MediTriaje 2.0 — Reglas de Negocio del Sistema

> Especificación formal de reglas de negocio para MediTriaje 2.0 (v0.1 MVP).
> Consolida las reglas fundacionales **RB-01 a RB-15** del Documento Maestro §44 y las reglas transaccionales/seguridad **RB-16 a RB-25** establecidas en `docs/DECISIONES.md` (ADR-001 a ADR-013) y `docs/MVP.md`.

---

## 1. Matriz de Reglas de Negocio

| Código | Nombre de la Regla | Capa de Aplicación Principal | Fuente de Verdad |
|---|---|---|---|
| **RB-01** | Aislamiento estricto entre pacientes | Backend (Service / Controller) | Doc. Maestro §44, HU-09 |
| **RB-02** | Principio de mínimo privilegio (Autenticado ≠ Autorizado) | Backend (Spring Security) | Doc. Maestro §44, AGENTS.md |
| **RB-03** | Autorización soberana en Backend | Backend (Security Filters / Service) | Doc. Maestro §44, AGENTS.md |
| **RB-04** | Validez y vigencia de la disponibilidad de cita | Base de Datos + Backend | Doc. Maestro §44, ADR-006 |
| **RB-05** | Prohibición absoluta de doble reserva | Base de Datos (Oracle) + Backend | Doc. Maestro §44, ADR-006 |
| **RB-06** | Coherencia de relación asistencial en atención | Backend + Base de Datos | Doc. Maestro §44, ADR-007 |
| **RB-07** | Responsabilidad profesional en la receta médica | Backend + Base de Datos | Doc. Maestro §44, HU-08 |
| **RB-08** | Integridad e inmutabilidad de registros clínicos | Base de Datos (Triggers) + Backend | Doc. Maestro §44, ADR-008 |
| **RB-09** | Trazabilidad de dispensaciones *(Fase 2)* | Backend | Doc. Maestro §44 |
| **RB-10** | Expiración y revocación de accesos temporales *(Fase 2)* | Backend + Base de Datos | Doc. Maestro §44, ADR-010 |
| **RB-11** | Auditoría obligatoria de eventos sensibles | Backend (AuditoriaService) + BD | Doc. Maestro §44, ADR-011 |
| **RB-12** | Prohibición de invención o alucinación de datos | Backend / Sistema | Doc. Maestro §44, AGENTS.md |
| **RB-13** | El Frontend no es frontera de seguridad | Arquitectura Global | Doc. Maestro §44, AGENTS.md |
| **RB-14** | Residencia exclusiva de reglas en el Backend | Backend | Doc. Maestro §44 |
| **RB-15** | Protección redundante en motor Oracle | Base de Datos (Oracle ATP) | Doc. Maestro §44, AGENTS.md |
| **RB-16** | Ventana temporal de cancelación de cita por el paciente | Backend | ADR-006, HU-05 |
| **RB-17** | Máquina de estados determinista para citas | Backend + Base de Datos | ADR-006 |
| **RB-18** | Concurrencia de slots mediante índice funcional único | Base de Datos (Oracle) | ADR-006 |
| **RB-19** | Relación asistencial previa o activa para acceso clínico | Backend (AccesoClinicoService) | ADR-007 |
| **RB-20** | Segregación total: Administrador sin acceso clínico | Backend (PreAuthorize / Service) | ADR-007, AGENTS.md |
| **RB-21** | Inmutabilidad de atenciones cerradas y enmiendas append-only | Base de Datos + Backend | ADR-008, HU-07 |
| **RB-22** | Corte de emergencia determinista en triaje | Backend (MotorTriaje puro) | ADR-009, HU-02 |
| **RB-23** | Atomicidad transaccional y snapshot en recetas | Backend + Base de Datos | HU-08 |
| **RB-24** | Consentimiento de tratamiento de datos personales obligatorio | Backend + Base de Datos | ADR-013, Ley 1581 |
| **RB-25** | Auditoría insert-only desprovista de contenido clínico | Backend + Base de Datos | ADR-011, HU-11 |

---

## 2. Definición Detallada de Reglas de Negocio

### RB-01: Aislamiento estricto entre pacientes
* **Enunciado:** Un paciente jamás podrá consultar, modificar, cancelar ni inferir información de citas, triajes, historias clínicas o recetas pertenecientes a otro paciente, aun conociendo o alterando los identificadores de recursos en peticiones HTTP.
* **Mecanismo:** Validación forzosa en la capa Service contra el `paciente_id` extraído del contexto autenticado (JWT). Todo acceso no coincidente emite HTTP 403 Forbidden o 404 Not Found y genera alarma en auditoría.

### RB-02: Principio de mínimo privilegio (Autenticado ≠ Autorizado)
* **Enunciado:** La sola posesión de credenciales válidas y una sesión activa no concede acceso indiscriminado a los módulos del sistema.
* **Mecanismo:** Control de acceso basado en roles (`ROLE_PACIENTE`, `ROLE_PROFESIONAL`, `ROLE_ADMINISTRADOR`) mediante anotaciones `@PreAuthorize` en endpoints y control de pertenencia a nivel de recurso.

### RB-03: Autorización soberana en Backend
* **Enunciado:** Todas las decisiones de autorización, visibilidad y ejecución residen única y exclusivamente en el servidor backend.
* **Mecanismo:** El cliente web jamás decide permisos; cualquier interfaz solo refleja las restricciones impuestas por las respuestas de la API.

### RB-04: Validez y vigencia de la disponibilidad de cita
* **Enunciado:** Una cita solo puede originarse a partir de un slot formalmente preexistente en la tabla `DISPONIBILIDAD_SLOT`, con estado `LIBRE` y cuya fecha y hora de inicio pertenezca estrictamente al futuro en zona horaria `America/Bogota`.
* **Mecanismo:** Filtro SQL parametrizado `WHERE estado = 'LIBRE' AND fecha_hora_inicio > CURRENT_TIMESTAMP`.

### RB-05: Prohibición absoluta de doble reserva
* **Enunciado:** Bajo ninguna circunstancia dos citas activas podrán coexistir sobre el mismo slot de disponibilidad horaria o el mismo intervalo de tiempo de un profesional.
* **Mecanismo:** Bloqueo transaccional `UPDATE ... WHERE estado = 'LIBRE'` e índice único funcional en Oracle (ver RB-18).

### RB-06: Coherencia de relación asistencial en atención
* **Enunciado:** Toda atención clínica registrada en el sistema debe estar asociada inequívocamente a un paciente real, a un profesional de la salud con matrícula válida y a una cita formalmente programada/confirmada.
* **Mecanismo:** Claves foráneas obligatorias (`NOT NULL`) en tabla `ATENCION` hacia `PACIENTE`, `PROFESIONAL` y `CITA`.

### RB-07: Responsabilidad profesional en la receta médica
* **Enunciado:** Toda prescripción médica debe contar con un profesional de la salud emisor plenamente identificado, quien asume la responsabilidad clínica de las indicaciones y medicamentos formulados.
* **Mecanismo:** Vinculación obligatoria del `profesional_id` en la tabla `RECETA`.

### RB-08: Integridad e inmutabilidad de registros clínicos
* **Enunciado:** Los registros clínicos históricos (atenciones, signos vitales, notas, diagnósticos, recetas) deben garantizar su integridad a lo largo del tiempo, prohibiéndose su eliminación física o alteración extemporánea.
* **Mecanismo:** Prohibición de permisos `DELETE` para el usuario de base de datos de la app; arquitectura append-only con enmiendas (ver RB-21).

### RB-09: Trazabilidad de dispensaciones *(Fase 2)*
* **Enunciado:** Toda dispensación de medicamentos formulados deberá registrarse con validación de identidad del dispensador y estado de la entrega. *(Fuera de alcance del MVP)*.

### RB-10: Expiración y revocación de accesos temporales *(Fase 2)*
* **Enunciado:** Los tokens temporales o enlaces generados por QR deben expirar a los 15 minutos o tras un máximo de 3 accesos auditados. *(Fuera de alcance del MVP, ADR-010)*.

### RB-11: Auditoría obligatoria de eventos sensibles
* **Enunciado:** Cualquier operación crítica para la seguridad o privacidad (autenticación, acceso a datos de salud, emisión de recetas, cancelación de citas y cambios de configuración) debe quedar registrada en una bitácora persistente.
* **Mecanismo:** Invocación sincrónica o asincrónica a `AuditoriaService` en capa Service.

### RB-12: Prohibición de invención o alucinación de datos
* **Enunciado:** El sistema no inventará diagnósticos clínicos, sugerencias de dosis, dispensaciones, horarios de atención ni cupos que no existan expresamente en la base de datos o en la entrada directa de un profesional acreditado.
* **Mecanismo:** Ausencia de algoritmos generativos clínicos; el triaje orienta en prioridad pero no diagnostica patologías ni prescribe medicamentos.

### RB-13: El Frontend no es frontera de seguridad
* **Enunciado:** Ocultar un botón, formulario o campo en la interfaz gráfica no constituye seguridad. El backend debe rechazar peticiones no autorizadas aunque el cliente web las intente enviar.

### RB-14: Residencia exclusiva de reglas en el Backend
* **Enunciado:** Las validaciones en cliente existen únicamente para mejorar la experiencia de usuario (UX). Las reglas de negocio, autorizaciones y cálculos residen en el backend.

### RB-15: Protección redundante en motor Oracle
* **Enunciado:** Las restricciones críticas de integridad relacional, unicidad, tipos y estados deben estar configuradas a nivel de esquema en Oracle (`CHECK`, `UNIQUE`, `FOREIGN KEY`, `NOT NULL`, triggers), independientemente de las validaciones en Java.

---

### RB-16: Ventana temporal de cancelación de cita por el paciente
* **Enunciado:** El paciente solo puede cancelar una cita programada o confirmada si faltan **al menos 2 horas** para la hora pactada de inicio de la misma (`America/Bogota`). Si faltan menos de 2 horas, la cancelación queda restringida al profesional o al administrador.
* **Mecanismo:** Validación temporal en `CitaService`:
  ```java
  if (Duration.between(Instant.now(), cita.getFechaHoraInicio()).toMinutes() < 120) {
      throw new CancelacionFueraDeTiempoException();
  }
  ```

### RB-17: Máquina de estados determinista para citas
* **Enunciado:** Los estados de una cita médica solo pueden transicionar según el autómata finito definido en ADR-006:
  ```text
  PROGRAMADA  ──> CONFIRMADA | CANCELADA | NO_ASISTIO | REPROGRAMADA
  CONFIRMADA  ──> ATENDIDA   | CANCELADA | NO_ASISTIO | REPROGRAMADA
  ```
  Los estados `ATENDIDA`, `CANCELADA`, `NO_ASISTIO` y `REPROGRAMADA` son **terminales** y no admiten ninguna transición posterior.
* **Mecanismo:** Validación en clase de dominio `EstadoCita` y constraint `CHECK` en Oracle.

### RB-18: Concurrencia de slots mediante índice funcional único
* **Enunciado:** Dos transacciones concurrentes jamás podrán asignar el mismo slot a dos citas activas.
* **Mecanismo:** Implementación a nivel de base de datos en Oracle:
  ```sql
  CREATE UNIQUE INDEX uq_cita_slot_activa ON cita (
    CASE WHEN estado IN ('PROGRAMADA', 'CONFIRMADA') THEN slot_id END
  );
  ```
  Acompañado de la reserva transaccional:
  ```sql
  UPDATE disponibilidad_slot SET estado = 'OCUPADO' WHERE id = :slotId AND estado = 'LIBRE';
  ```
  Si filas afectadas = 0, se lanza `CitaNoDisponibleException`.

### RB-19: Relación asistencial previa o activa para acceso clínico
* **Enunciado:** Un profesional de la salud solo puede acceder a la historia clínica o atenciones de un paciente si existe una **relación asistencial legítima**, definida como:
  1. El paciente tiene una cita `PROGRAMADA` o `CONFIRMADA` futura con dicho profesional, O
  2. El profesional ha brindado una atención clínica al paciente dentro de los últimos 12 meses (parámetro configurable).
* **Mecanismo:** Verificación centralizada en `AccesoClinicoService` antes de despachar información clínica.

### RB-20: Segregación total: Administrador sin acceso clínico
* **Enunciado:** El rol `ADMINISTRADOR` tiene privilegios de gestión operativa (instituciones, sedes, profesionales, especialidades, slots), pero tiene **terminantemente prohibido** el acceso de lectura o escritura a motivos de consulta, notas de evolución, signos vitales, diagnósticos CIE-10, triajes o recetas.
* **Mecanismo:** Los endpoints clínicos rechazan peticiones con rol `ADMINISTRADOR` devolviendo HTTP 403 Forbidden.

### RB-21: Inmutabilidad de atenciones cerradas y enmiendas append-only
* **Enunciado:** Una vez que una atención médica pasa a estado `CERRADA`, su contenido queda fijado de forma inmutable. Cualquier aclaración, corrección o adición debe realizarse creando una **enmienda clínica** enlazada en `ATENCION_ENMIENDA` sin modificar el registro original.
* **Mecanismo:** Trigger en Oracle ATP que bloquea cualquier sentencia `UPDATE` o `DELETE` sobre registros en `ATENCION` con `estado = 'CERRADA'`.

### RB-22: Corte de emergencia determinista en triaje
* **Enunciado:** Si el conjunto de síntomas evaluados en un triaje contiene al menos un síntoma catalogado como alarma (`es_alarma = 1`) o corresponde a Nivel I (reanimación/emergencia inmediata), el sistema activa de manera obligatoria el **Corte de Emergencia**.
* **Efectos:**
  - El resultado es invariablemente `EMERGENCIA`.
  - La interfaz omite cualquier opción de agendamiento de cita ambulatoria.
  - Se instruye al usuario a acudir de inmediato a un servicio de urgencias o comunicarse a la línea nacional **123**.
  - El motor de triaje es puro, determinista y opera sobre `REGLA_TRIAJE` versionada.

### RB-23: Atomicidad transaccional y snapshot en recetas
* **Enunciado:** La creación de una receta médica y el alta de sus ítems asociados debe ejecutarse en una única transacción atómica (`@Transactional`). Adicionalmente, el detalle de receta debe almacenar una copia exacta (*snapshot*) del nombre, presentación y concentración del medicamento al momento de la emisión.
* **Justificación:** Si un medicamento cambia de denominación o es retirado del catálogo maestro posteriormente, la receta histórica debe permanecer intacta e inalterable.

### RB-24: Consentimiento de tratamiento de datos personales obligatorio
* **Enunciado:** El registro de un paciente en el sistema exige la aceptación expresa e informada de la política de tratamiento de datos personales sensibles de salud, conforme a la Ley 1581 de 2012 de Colombia.
* **Mecanismo:** Se persiste en la tabla `CONSENTIMIENTO` el `usuario_id`, fecha/hora de aceptación en `TIMESTAMP WITH TIME ZONE` y la versión exacta del texto aceptado. No es posible crear un paciente sin este consentimiento.

### RB-25: Auditoría insert-only desprovista de contenido clínico
* **Enunciado:** La tabla `AUDITORIA` es de naturaleza estrictamente `insert-only`. Se prohíbe taxativamente almacenar datos clínicos (diagnósticos, síntomas, notas médicas, nombres de medicamentos) o credenciales/secretos en los registros de auditoría.
* **Estructura permitida:** Usuario responsable, tipo de acción, tipo de recurso afectado, identificador público (`public_id`) del recurso, resultado de la operación (éxito/fallo), dirección IP de origen y marca temporal.
