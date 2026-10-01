# MediTriaje 2.0 — Casos de Uso del Sistema (MVP)

> Documento de especificación funcional para la versión 0.1 (MVP).
> Basado estrictamente en `docs/MVP.md`, `docs/DECISIONES.md` (ADR-001 a ADR-013) y `docs/requirements/DOCUMENTO_MAESTRO.md`.

---

## 1. Actores del Sistema

| Actor | Descripción | Responsabilidades principales |
|---|---|---|
| **Paciente** | Usuario receptor de los servicios de salud orientados por la plataforma. | Registro, aceptación de consentimiento, triaje preliminar, consulta y reserva de citas, consulta de historia y recetas personales. |
| **Profesional de la Salud** | Médico u odontólogo debidamente registrado con especialidad asignada. | Consulta de su propia agenda de atención, registro y cierre de atenciones clínicas, emisión de enmiendas y recetas médicas asociadas. |
| **Administrador** | Personal institucional con perfil de gestión operativa. | Parametrización de instituciones, sedes, especialidades, profesionales y generación de disponibilidad horaria (slots). No tiene acceso a información clínica sensible. |
| **Sistema** | Componente automatizado que ejecuta reglas deterministas, validaciones transaccionales y auditoría. | Ejecución del motor de triaje, control de concurrencia en slots, bloqueo por intentos fallidos de autenticación y persistencia de trazas de auditoría. |

---

## 2. Mapa de Casos de Uso vs. Historias de Usuario

| Historia de Usuario | Caso de Uso | Título |
|---|---|---|
| **HU-01** | **CU-01** | Registro e Inicio de Sesión de Paciente |
| **HU-02** | **CU-02** | Realización de Triaje y Determinación de Ruta |
| **HU-03** | **CU-03** | Consulta de Disponibilidad de Citas |
| **HU-04** | **CU-04** | Agendamiento de Cita Médica con Control de Concurrencia |
| **HU-05** | **CU-05** | Cancelación de Cita Médica |
| **HU-06** | **CU-06** | Consulta de Agenda del Profesional |
| **HU-07** | **CU-07** | Registro, Enmienda y Cierre de Atención Médica |
| **HU-08** | **CU-08** | Emisión Atómica de Receta Médica |
| **HU-09** | **CU-09** | Consulta de Historia Clínica y Recetas por el Paciente |
| **HU-10** | **CU-10** | Administración de Oferta Asistencial y Catálogos |
| **HU-11** | **CU-11** | Registro Centralizado de Auditoría |

---

## 3. Especificación Detallada de Casos de Uso

### CU-01: Registro e Inicio de Sesión de Paciente
* **Actor Principal:** Paciente.
* **Precondiciones:** El paciente no debe estar autenticado.
* **Reglas Asociadas:** RB-02, RB-03, RB-11, RB-24 (Consentimiento Ley 1581), ADR-002, ADR-013.

#### Flujo Principal (Registro):
1. El paciente accede a la opción de registro.
2. El sistema solicita datos de identidad (tipo y número de documento: CC, TI, RC, CE, PA), nombres y apellidos completos, fecha de nacimiento, correo electrónico y contraseña (mínimo 10 caracteres).
3. El sistema presenta el texto íntegro del Consentimiento de Tratamiento de Datos Personales (Ley 1581 de 2012) con su versión vigente.
4. El paciente diligencia el formulario, marca explícitamente la casilla de consentimiento y envía la solicitud.
5. El sistema valida en una sola transacción atómica:
   - Formato válido y longitud de contraseña (≥ 10 caracteres).
   - Unicidad del correo electrónico y número de documento.
   - Presencia y aceptación del consentimiento.
6. El sistema genera el hash de la contraseña mediante Argon2id, crea el registro en `USUARIO` (rol `PACIENTE`), en `PACIENTE` y en `CONSENTIMIENTO` (con timestamp y versión del texto).
7. El sistema registra el evento en auditoría (`REGISTRO_PACIENTE_EXITOSO`) y retorna confirmación.

#### Flujo Principal (Inicio de Sesión):
1. El usuario introduce correo electrónico y contraseña.
2. El sistema verifica que la cuenta no se encuentre bloqueada por intentos fallidos.
3. El sistema valida las credenciales contrastando el hash Argon2id.
4. El sistema reinicia el contador de intentos fallidos.
5. El sistema emite dos tokens seguros en cookies `HttpOnly; Secure; SameSite=Strict`:
   - Access token JWT con expiración corta (15 minutos).
   - Refresh token opaco con rotación de 7 días, almacenado hasheado en base de datos.
6. El sistema audita el evento (`LOGIN_EXITOSO`) y redirige al dashboard del rol correspondiente.

#### Flujos Alternos y Excepciones:
* **1a. Correo ya registrado:** El sistema aborta la transacción y devuelve error genérico de validación para evitar enumeración de usuarios.
* **1b. Falta de consentimiento:** El sistema rechaza el registro si la casilla obligatoria no fue seleccionada.
* **2a. Credenciales incorrectas:**
  - El sistema incrementa el contador de intentos fallidos del usuario/IP.
  - Devuelve error genérico "Credenciales inválidas" (sin revelar si el correo existe o no).
  - Si el contador alcanza 5 intentos consecutivos, bloquea temporalmente la cuenta por 15 minutos y audita `LOGIN_BLOQUEO`.
* **3a. Cierre de sesión (Logout):**
  - El usuario solicita salir del sistema.
  - El sistema revoca el refresh token en base de datos, limpia las cookies de sesión y audita `LOGOUT_EXITOSO`.

---

### CU-02: Realización de Triaje y Determinación de Ruta
* **Actor Principal:** Paciente.
* **Precondiciones:** Paciente autenticado con sesión activa.
* **Reglas Asociadas:** RB-08, RB-12, RB-22 (Corte de emergencia), ADR-009.

#### Flujo Principal:
1. El paciente accede al módulo de triaje y visualiza el aviso legal: *"Este triaje es una herramienta de orientación y no sustituye la valoración médica profesional presencial"*.
2. El paciente selecciona los síntomas presentes desde el catálogo semilla, especificando duración (horas/días) e intensidad percibida (escala 0 a 10).
3. El paciente confirma y envía el formulario.
4. El sistema evalúa determinísticamente las reglas activas de prototipo en `REGLA_TRIAJE` contra los síntomas ingresados.
5. Si ningún síntoma activa corte de emergencia:
   - El sistema clasifica la prioridad en nivel I a V (alineado a Res. 5596/2015).
   - Determina la ruta asistencial sugerida (Atención prioritaria, Cita presencial, Cita remota o Consulta programada).
   - Persiste el resultado en `TRIAJE` y `TRIAJE_SINTOMA`, guardando la versión de reglas utilizada.
6. El sistema presenta al paciente la tarjeta de resultado con nivel, icono, color normativo, texto explicativo sereno y botón para buscar citas disponibles con el `triaje_id` preasociado.
7. El sistema audita la generación del triaje (`TRIAJE_GENERADO`).

#### Flujos Alternos y Excepciones (Corte de Emergencia):
* **4a. Detección de síntoma de alarma:**
  - Si al menos un síntoma seleccionado tiene la bandera `es_alarma = 1` o cumple condición crítica de emergencia (Nivel I):
  - El sistema activa de inmediato el **Corte de Emergencia**.
  - **No ofrece agendamiento de cita** ni sugiere esperar turno ambulatorio.
  - Despliega la pantalla de emergencia con fondo rojo de advertencia, instrucción clara de acudir al centro asistencial más cercano y botón directo para llamar al **123**.
  - Persiste el evento en `TRIAJE` con resultado `EMERGENCIA`.
  - Audita el evento de alarma con máxima prioridad (`TRIAJE_CORTE_EMERGENCIA`).

---

### CU-03: Consulta de Disponibilidad de Citas
* **Actor Principal:** Paciente, Administrador.
* **Precondiciones:** Usuario autenticado.
* **Reglas Asociadas:** RB-04, RB-12, ADR-006.

#### Flujo Principal:
1. El usuario accede al buscador de disponibilidad.
2. Opcionalmente ingresa filtros: especialidad médica, rango de fechas, sede de atención y modalidad (presencial / telemedicina).
3. El sistema consulta la tabla `DISPONIBILIDAD_SLOT` aplicando las siguientes condiciones estrictas:
   - `estado = 'LIBRE'`.
   - `fecha_hora_inicio > CURRENT_TIMESTAMP` (slots futuros).
   - Coincidencia con los filtros suministrados.
4. El sistema retorna la lista paginada de horarios disponibles agrupados por día y profesional.
5. El sistema no genera slots virtuales ni inventa disponibilidades no creadas previamente por administración.

#### Flujos Alternos:
* **3a. Sin disponibilidad para los filtros seleccionados:** El sistema muestra un estado vacío comprensible indicando que no hay cupos libres y sugiriendo ampliar el rango de fechas o cambiar de sede.

---

### CU-04: Agendamiento de Cita Médica con Control de Concurrencia
* **Actor Principal:** Paciente.
* **Precondiciones:** Paciente autenticado; slot identificado en estado `LIBRE`.
* **Reglas Asociadas:** RB-04, RB-05, RB-14, RB-15, RB-18 (Índice funcional y bloqueo), ADR-003, ADR-006.

#### Flujo Principal:
1. El paciente selecciona un slot disponible (`public_id` del slot) y opcionalmente asocia el código de su triaje previo (`triaje_id`).
2. El paciente confirma los datos de la reserva en el resumen previo.
3. El sistema inicia una transacción de base de datos en Oracle:
   - Ejecuta actualización con bloqueo: `UPDATE DISPONIBILIDAD_SLOT SET estado = 'OCUPADO' WHERE id = :slotId AND estado = 'LIBRE'`.
   - Verifica que el número de filas afectadas sea exactamente 1.
   - Crea el registro en `CITA` con estado inicial `PROGRAMADA`, asignando el `paciente_id` del token de sesión, fecha de registro y `triaje_id` (validando que pertenezca al mismo paciente).
4. La base de datos valida automáticamente el índice único funcional `uq_cita_slot_activa`.
5. El sistema confirma la transacción (*commit*).
6. El sistema audita la cita creada (`CITA_AGENDADA`).
7. El sistema retorna al paciente el identificador público de la cita (`public_id`), fecha, hora, profesional y sede.

#### Flujos Alternos y Excepciones (Concurrencia):
* **3a. Conflicto concurrente (Doble reserva simultánea):**
  - Si otro paciente reservó el slot milisegundos antes, la sentencia `UPDATE` afecta 0 filas o el índice funcional rechaza la inserción.
  - El sistema ejecuta *rollback* inmediato y lanza la excepción de dominio `CitaNoDisponible`.
  - La API responde código HTTP 409 Conflict con mensaje amigable: *"El horario seleccionado acaba de ser reservado por otro usuario. Por favor selecciona otro horario."*
  - Se audita el intento fallido por contención (`CITA_CONFLICTO_CONCURRENCIA`).

---

### CU-05: Cancelación de Cita Médica
* **Actor Principal:** Paciente (o Profesional / Administrador).
* **Precondiciones:** Cita existente en estado `PROGRAMADA` o `CONFIRMADA`.
* **Reglas Asociadas:** RB-01, RB-03, RB-16 (Ventana de 2 horas), RB-17 (Máquina de estados), ADR-006.

#### Flujo Principal:
1. El paciente visualiza su listado de citas y solicita cancelar una de ellas.
2. El sistema verifica autorización: el `paciente_id` de la cita debe coincidir con el usuario en sesión (si es paciente).
3. El sistema valida la regla de tiempo: la fecha de inicio del slot debe estar a **más de 2 horas** del instante actual (`America/Bogota`).
4. El sistema inicia transacción:
   - Cambia el estado de `CITA` a `CANCELADA` (transición válida permitida).
   - Actualiza el `DISPONIBILIDAD_SLOT` asociado devolviendo su estado a `LIBRE`.
   - Registra motivo de cancelación y fecha.
5. El sistema confirma la transacción y audita `CITA_CANCELADA`.
6. Informa al paciente que la cita ha sido liberada con éxito.

#### Flujos Alternos y Excepciones:
* **2a. Intento de cancelar cita ajena:** El sistema deniega la operación con 403/404 y audita intento de acceso no autorizado.
* **3a. Cancelación tardía (< 2 horas antes):** El sistema rechaza la solicitud del paciente con error de negocio `CancelacionFueraDeTiempo` ("Las cancelaciones por parte del paciente deben realizarse con al menos 2 horas de anticipación"). La cita permanece inalterada.

---

### CU-06: Consulta de Agenda del Profesional
* **Actor Principal:** Profesional de la Salud.
* **Precondiciones:** Profesional autenticado con rol `PROFESIONAL`.
* **Reglas Asociadas:** RB-01, RB-02, RB-03, ADR-007.

#### Flujo Principal:
1. El profesional accede al módulo de su agenda diaria.
2. El sistema recupera el identificador interno del profesional a partir de su sesión de usuario.
3. El sistema consulta las citas vinculadas a dicho profesional para la fecha actual o rango solicitado:
   - Solo devuelve citas asignadas a su identificador.
   - Incluye estado (`PROGRAMADA`, `CONFIRMADA`, `ATENDIDA`, etc.), hora de inicio/fin, nombre del paciente y motivo/triaje.
4. El profesional visualiza la lista cronológica con opciones para iniciar la atención de pacientes confirmados.

#### Flujos Alternos:
* **2a. Profesional intenta consultar agenda de un colega:** La API no provee filtros por otros profesionales; cualquier parámetro forzado produce un 403 Forbidden.

---

### CU-07: Registro, Enmienda y Cierre de Atención Médica
* **Actor Principal:** Profesional de la Salud.
* **Precondiciones:** Profesional autenticado con relación asistencial activa con el paciente (ADR-007).
* **Reglas Asociadas:** RB-06, RB-08, RB-19 (Relación asistencial), RB-20, RB-21 (Inmutabilidad), ADR-007, ADR-008.

#### Flujo Principal (Creación y Cierre de Atención):
1. El profesional selecciona al paciente citado e inicia la atención.
2. El sistema valida la **relación asistencial** mediante `AccesoClinicoService` (cita activa confirmada o atención previa propia en ≤ 12 meses).
3. El profesional ingresa:
   - Motivo de consulta y evolución médica.
   - Signos vitales (tensión arterial, frecuencia cardíaca, temperatura, saturación O2).
   - Diagnóstico principal (código CIE-10 del catálogo institucional reducido).
   - Plan e indicaciones de manejo.
4. El profesional solicita el cierre de la atención.
5. El sistema solicita confirmación explícita advirtiendo que el cierre es **definitivo e irreversible**.
6. En una transacción atómica:
   - Se crea el registro en `ATENCION` con estado `CERRADA`, fecha/hora de cierre y firma digitalizada/usuario del profesional.
   - Se persisten los signos vitales en `SIGNO_VITAL`.
   - Se actualiza el estado de la `CITA` a `ATENDIDA` (estado terminal).
7. La base de datos activa el trigger protector: a partir de este instante, cualquier intento de `UPDATE` o `DELETE` sobre esta atención es abortado a nivel de motor Oracle.
8. El sistema audita `ATENCION_CERRADA` (sin incluir los datos clínicos en la auditoría) y notifica finalización.

#### Flujos Alternos (Enmienda Clínica):
* **4a. Necesidad de corregir o ampliar una atención ya cerrada:**
  - El profesional responsable accede a la atención cerrada y redacta una **enmienda**.
  - Proporciona: texto de la enmienda, motivo de la corrección y firma.
  - El sistema crea un registro en `ATENCION_ENMIENDA` vinculado a la atención original (append-only), registrando autor, fecha y contenido.
  - La atención original permanece 100% inalterada.
  - Se audita `ATENCION_ENMIENDA_CREADA`.

#### Excepciones:
* **2a. Falta de relación asistencial:** El sistema rechaza con HTTP 403 Forbidden y audita `ACCESO_CLINICO_DENEGADO`.
* **6a. Intento de modificación directa:** Si se envía una solicitud para alterar una atención cerrada, el backend y el trigger de Oracle devuelven error de violación de integridad (`RegistroClinicoInmutable`).

---

### CU-08: Emisión Atómica de Receta Médica
* **Actor Principal:** Profesional de la Salud.
* **Precondiciones:** Atención médica abierta o en proceso de cierre por el mismo profesional.
* **Reglas Asociadas:** RB-07, RB-08, RB-12, RB-23 (Atomicidad y snapshot), ADR-008.

#### Flujo Principal:
1. El profesional decide prescribir medicamentos asociados a la atención.
2. Selecciona los medicamentos del catálogo (`MEDICAMENTO`) e ingresa posología, vía de administración, frecuencia y duración (en texto libre profesional; el sistema no inventa dosis).
3. El profesional envía la receta con uno o más ítems.
4. El sistema ejecuta una transacción `ALL-OR-NOTHING`:
   - Crea la cabecera en `RECETA` asociada al `paciente_id`, `profesional_id` y `atencion_id`.
   - Por cada medicamento, crea el registro en `RECETA_DETALLE` copiando un **snapshot inmutable** del nombre comercial, principio activo y presentación exacta en ese momento (para evitar alteraciones históricas si el catálogo cambia).
5. Si todos los detalles son válidos, se confirma la transacción.
6. El sistema audita la emisión (`RECETA_EMITIDA`) y retorna el identificador público de la receta.

#### Flujos Alternos y Excepciones:
* **4a. Falla en la validación de algún medicamento o posología:** El sistema hace *rollback* completo de la receta y sus detalles; no quedan recetas huérfanas o parciales.

---

### CU-09: Consulta de Historia Clínica y Recetas por el Paciente
* **Actor Principal:** Paciente.
* **Precondiciones:** Paciente autenticado con rol `PACIENTE`.
* **Reglas Asociadas:** RB-01, RB-02, RB-03, RB-08, ADR-003, ADR-007.

#### Flujo Principal:
1. El paciente ingresa a la sección "Mi Historia Clínica" o "Mis Recetas".
2. El sistema extrae el `paciente_id` exclusivamente del token de seguridad en la sesión activa.
3. El sistema consulta las atenciones cerradas, diagnósticos CIE-10, enmiendas asociadas y recetas históricas pertenecientes a dicho paciente.
4. El sistema presenta la información en una línea de tiempo clara y accesible (solo lectura).
5. Las respuestas de la API contienen únicamente `public_id` (UUID); nunca se exponen PKs internas de base de datos.
6. El sistema audita el acceso del paciente a su historia (`CONSULTA_HISTORIA_PACIENTE`).

#### Flujos Alternos y Excepciones (Aislamiento de Paciente):
* **2a. Parámetros alterados en URL / ID spoofing:** Si un paciente manipula la URL para solicitar la historia o receta de otro paciente (`/api/v1/patients/{otro_id}/history`), el sistema rechaza la petición con 403 Forbidden o 404 Not Found, registrando alerta en auditoría (`ACCESO_CRUZADO_DENEGADO`).

---

### CU-10: Administración de Oferta Asistencial y Catálogos
* **Actor Principal:** Administrador.
* **Precondiciones:** Usuario autenticado con rol `ADMINISTRADOR`.
* **Reglas Asociadas:** RB-02, RB-03, RB-20 (Admin sin acceso clínico), ADR-006, ADR-007.

#### Flujo Principal:
1. El administrador accede al panel de gestión operativa.
2. Gestiona instituciones prestadoras, sedes de servicio, especialidades y alta de profesionales médicos (con generación de usuario y contraseña temporal).
3. Configura el **Generador de Slots**:
   - Selecciona profesional, sede, rango de fechas, horario de inicio y fin, y duración del turno (por defecto 20 minutos).
4. El sistema calcula los intervalos de tiempo en zona horaria `America/Bogota` verificando que no existan solapes previos para ese profesional.
5. El sistema inserta los registros en `DISPONIBILIDAD_SLOT` con estado inicial `LIBRE`.
6. El sistema audita la generación masiva de disponibilidad (`SLOTS_GENERADOS_ADMIN`).

#### Excepciones:
* **1a. Intento del administrador de consultar expedientes clínicos:** Si el administrador invoca endpoints de atenciones, notas médicas o recetas, el backend rechaza categóricamente con HTTP 403 Forbidden. La administración no tiene privilegios clínicos.

---

### CU-11: Registro Centralizado de Auditoría
* **Actor Principal:** Sistema.
* **Precondiciones:** Ocurrencia de cualquier evento relevante en la plataforma.
* **Reglas Asociadas:** RB-11, RB-25, ADR-002, ADR-011.

#### Flujo Principal:
1. Se dispara un evento auditable (autenticación, cierre de atención, emisión de receta, cancelación de cita, corte de emergencia, cambio administrativo).
2. El servicio de auditoría recibe los metadatos:
   - Identificador de usuario (o anónimo en login fallido).
   - Acción tipificada (Enum).
   - Tipo de recurso y su identificador (`public_id`).
   - Resultado (ÉXITO / FALLO / BLOQUEADO).
   - Dirección IP y timestamp en zona horaria `America/Bogota`.
3. El sistema inserta el registro en la tabla `AUDITORIA` mediante una operación **insert-only**.
4. **Prohibición absoluta:** En ningún caso se persisten diagnósticos, medicamentos, notas médicas, datos personales sensibles ni secretos/tokens.
5. El usuario de base de datos de la aplicación no posee privilegios de `UPDATE` ni `DELETE` sobre la tabla de auditoría.
