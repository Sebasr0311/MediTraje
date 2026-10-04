# GUION_DEMO.md — Guion de Demostración del MVP de MediTriaje 2.0

> **Objetivo:** Demostrar de principio a fin el flujo completo del MVP según lo establecido en [`docs/MVP.md` §8](docs/MVP.md#8-guion-de-demo), utilizando datos 100% ficticios en un entorno local o de demostración.

---

## 0. Prerrequisitos de Ejecución

1. **Backend en ejecución:**
   ```powershell
   cd backend
   mvn spring-boot:run
   ```
   *Verificar salud:* `GET http://localhost:8080/api/v1/ping` debe responder `{"status":"UP"}`.

2. **Frontend en ejecución:**
   ```powershell
   # Desde la raíz del repositorio, usando cualquier servidor estático:
   npx serve frontend -l 5500
   # O en Python:
   python -m http.server 5500 --directory frontend
   ```
   Abrir navegador en: `http://localhost:5500` (o `http://127.0.0.1:5500`).

3. **Cuentas Preconfiguradas (Semillas de Desarrollo):**
   * **Administrador:** `admin@meditriaje.com` / `Admin12345*`
   * **Médico 1 (Medicina General):** `carlos.mendoza@meditriaje.com` / `Temporal12345*`
   * **Médico 2 (Pediatría):** `laura.gomez@meditriaje.com` / `Temporal12345*`

---

## Paso 1: Registro del Paciente y Consentimiento Informado

1. En el navegador, ingresar a `http://localhost:5500/#/register`.
2. **Paso 1 del Formulario (Datos Personales):**
   * **Tipo de Documento:** Cédula de Ciudadanía (`CC`)
   * **Número de Documento:** `1098765432`
   * **Nombres:** `Juan Camilo`
   * **Apellidos:** `Pérez Gómez`
   * **Fecha de Nacimiento:** `1995-06-15`
   * **Teléfono:** `3109876543`
   * Clic en **"Siguiente paso"**.
3. **Paso 2 del Formulario (Credenciales y Consentimiento):**
   * **Correo electrónico:** `juan.perez@ejemplo.com`
   * **Contraseña:** `Paciente12345*`
   * **Confirmar contraseña:** `Paciente12345*`
   * Clic en el enlace *"Leer términos completos del Consentimiento Informado"* para desplegar el modal interactivo de la Política de Tratamiento de Datos (Ley 1581 de 2012, Versión 1.0).
   * Marcar la casilla de verificación: `[x] He leído y acepto expresamente la Política de Tratamiento de Datos Personales y Datos Sensibles de Salud`.
   * Clic en **"Crear mi cuenta"**.
4. **Resultado esperado:**
   * Notificación Toast verde: *"¡Registro exitoso! Tu cuenta ha sido creada."*
   * Redirección automática al Dashboard del Paciente (`#/patient/dashboard`).
   * La base de datos persiste el registro del paciente y almacena el consentimiento inmutable con versión `v1.0` en la tabla `CONSENTIMIENTO`.

---

## Paso 2: Triaje Clínico No Urgente y Agendamiento de Cita

1. En el menú superior o en la tarjeta destacada del dashboard, hacer clic en **"Iniciar Triaje Clínico"** (`#/patient/triage`).
2. **Paso 1 de Triaje (Selección de Síntomas):**
   * En el buscador de síntomas, escribir `garganta` o seleccionar en la categoría General:
     * Clic en el chip **"Dolor de garganta / odinofagia"**.
   * Clic en **"Siguiente: Intensidad y duración"**.
3. **Paso 2 de Triaje (Severidad):**
   * **Escala de Intensidad (0 a 10):** Seleccionar `4` (Moderado).
   * **Duración en horas:** Seleccionar preset `48 horas` (o digitar `48`).
   * **Observaciones:** `Molestia al tragar desde hace dos días.`
   * Clic en **"Evaluar Triaje"**.
4. **Resultado del Triaje:**
   * Se presenta la pantalla de resultado determinista:
     * **Nivel de Prioridad:** `Nivel IV` (Consulta Prioritaria / No Urgente).
     * **Badge:** Verde/Ámbar con icono de estetoscopio.
     * **Ruta Sugerida:** `CITA_PRESENCIAL` en Medicina General.
     * **Aviso Legal:** *"Esta orientación es un prototipo, no sustituye la valoración de un profesional de la salud."*
5. **Agendamiento vinculado:**
   * Clic en el botón **"Agendar cita para este triaje"**.
   * El sistema redirige a la vista de disponibilidad (`#/patient/booking`) con el triaje preseleccionado.
   * Seleccionar la especialidad **"Medicina General"** y la sede **"Sede Centro Valledupar"**.
   * En la cuadrícula de horarios agrupados por día, seleccionar un slot libre del **Dr. Carlos Alberto Mendoza Vega**.
   * Verificar en el resumen: Médico, Especialidad, Sede, Fecha y Hora.
   * Clic en **"Confirmar Reserva de Cita"**.
6. **Resultado esperado:**
   * Modal o pantalla de éxito con el código de confirmación de reserva y estado `PROGRAMADA`.

---

## Paso 3: Triaje con Síntoma de Alarma (Corte de Emergencia Infalible)

1. Regresar a la sección de Triaje Clínico (`#/patient/triage`).
2. En la lista de síntomas, buscar y seleccionar un síntoma de alarma:
   * **"Dolor torácico opresivo"** (con badge rojo `Alarma`).
3. Clic en **"Siguiente: Intensidad y duración"**.
4. Digitar intensidad `7` y duración `1` hora.
5. Clic en **"Evaluar Triaje"**.
6. **Resultado de Emergencia Infalible (ADR-009):**
   * La interfaz despliega inmediatamente el banner crítico de emergencia `.alert--emergency`:
     * **Nivel:** `NIVEL I — EMERGENCIA VITAL`.
     * **Instrucción:** *"Llama de inmediato al 123 o acude al servicio de urgencias más cercano."*
     * **Botón Prominente:** Enlace telefónico directo `tel:123` (*"Llamar al 123"*).
     * **Aislamiento de Citas:** **CERO opciones o botones para agendar citas**.
     * En el backend se audita de forma inmutable el evento `TRIAJE_EMERGENCIA`.

---

## Paso 4: Atención Clínica y Emisión de Receta por el Médico

1. Cerrar sesión del paciente (clic en *"Cerrar sesión"* en la barra de navegación).
2. Ingresar a `http://localhost:5500/#/login` con credenciales de médico:
   * **Correo:** `carlos.mendoza@meditriaje.com`
   * **Contraseña:** `Temporal12345*`
   * *(Si el sistema solicita cambio de contraseña temporal, ingresar la nueva clave ej. `Medico2026*`).*
3. El enrutador redirige a la **Agenda del Profesional** (`#/professional/agenda`):
   * Se visualiza la cita agendada por el paciente `Juan Camilo Pérez Gómez` en estado `PROGRAMADA`.
4. Clic en el botón **"Iniciar Atención"**:
   * El backend registra la atención en estado `ABIERTA` y la cita pasa a `CONFIRMADA`.
5. **Diligenciamiento de la Historia Clínica:**
   * **Motivo de Consulta:** `Paciente consulta por odinofagia de 48 horas de evolución tras cuadro viral leve.`
   * **Evolución Médica:** `Orofaringe congestiva sin placas exudativas. Murmullo vesicular conservado. Sin signos de dificultad respiratoria.`
   * **Signos Vitales:**
     * *Presión Sistólica:* `120` mmHg / *Presión Diastólica:* `80` mmHg *(validación de coherencia sistólica > diastólica).*
     * *Frecuencia Cardíaca:* `72` lpm
     * *Frecuencia Respiratoria:* `16` rpm
     * *Temperatura:* `36.8` °C
     * *Saturación O2:* `98` %
   * **Diagnóstico CIE-10:** Escribir en el buscador `faringitis` y seleccionar:
     * `J02.9` — *Faringitis aguda, no especificada*.
   * **Indicaciones Médicas:** `Hidratación oral abundante, reposo relativo por 3 días y analgesia según receta.`
6. **Cierre Irreversible de la Atención:**
   * Clic en **"Cerrar Atención Médica"**.
   * Se presenta el modal de confirmación advirtiendo que el cierre es **definitivo e inmutable**.
   * Confirmar el cierre. La cita pasa a `ATENDIDA` y los triggers PL/SQL en Oracle ATP protegen la atención contra modificaciones.
7. **Emisión de Receta Médica:**
   * En la pantalla de atención cerrada, hacer clic en **"Emitir Receta Médica"** (`#/professional/prescription/:id`).
   * En el catálogo de medicamentos, buscar y añadir:
     * Medicamento 1: `Amoxicilina 500 mg cápsulas` — Dosis: `1 cápsula cada 8 horas` — Duración: `7 días` — Cantidad: `21`.
     * Medicamento 2: `Acetaminofén 500 mg tabletas` — Dosis: `1 tableta cada 6 horas en caso de dolor o fiebre` — Duración: `3 días` — Cantidad: `12`.
   * Clic en **"Emitir y Firmar Receta"**.
   * Resultado: Receta médica generada exitosamente con snapshots congelados de los fármacos.

---

## Paso 5: Paciente Consulta su Historia Clínica y Recetas

1. Cerrar sesión del médico y volver a iniciar sesión con el paciente:
   * **Correo:** `juan.perez@ejemplo.com` / `Paciente12345*`.
2. En el Dashboard del Paciente:
   * Se visualiza la cita recién completada en estado `ATENDIDA`.
3. Navegar a **"Mi Historia Clínica"** (`#/patient/history`):
   * Se muestra la línea de tiempo cronológica con la atención del Dr. Carlos Mendoza.
   * Se aprecian el diagnóstico formal `J02.9`, los signos vitales validados y la evolución médica.
4. Navegar a **"Mis Recetas"** (`#/patient/prescriptions`):
   * Se muestra la receta con su código público UUID, fecha de vigencia (30 días), profesional emisor y el detalle íntegro de la prescripción (Amoxicilina y Acetaminofén con sus dosis).

---

## Paso 6: Verificación de Aislamiento de Seguridad (Acceso Cruzado Denegado)

### 6.1 Intento de Paciente A accediendo a Paciente B
1. Abrir la consola de desarrollador del navegador (F12) o una pestaña de pruebas HTTP (o `docs/api/M6.http`).
2. Con la sesión del paciente `Juan Camilo Pérez` activa, intentar consultar una cita o historia médica de otro paciente UUID:
   ```http
   GET /api/v1/attentions/00000000-0000-0000-0000-000000000001
   ```
3. **Respuesta recibida:** `403 Forbidden` (`ACCESO_NO_AUTORIZADO`) o `404 Not Found`. El paciente jamás ve registros ajenos.

### 6.2 Intento de Administrador accediendo a contenido clínico (ADR-007)
1. Iniciar sesión con la cuenta de administrador: `admin@meditriaje.com` / `Admin12345*`.
2. Acceder al panel de administración (`#/admin/dashboard`). Comprobar que solo existen pestañas de Instituciones, Sedes, Especialidades, Profesionales y Slots. **Cero contenido clínico**.
3. Intentar realizar una petición directa a cualquier endpoint clínico desde la consola:
   ```javascript
   await fetch('/api/v1/patients/me/history', { headers: { 'X-Requested-With': 'XMLHttpRequest' } });
   ```
4. **Respuesta recibida:** `403 Forbidden` (*"El personal administrativo no tiene acceso a historias ni contenido clínico"*).

---

## Paso 7: Inspección de la Bitácora de Auditoría Inmutable (ADR-011)

1. En la base de datos (o mediante consulta SQL Developer / test de repositorio), consultar la tabla `AUDITORIA`:
   ```sql
   SELECT ID, ACCION, TIPO_RECURSO, RECURSO_PUBLIC_ID, RESULTADO, IP_ORIGEN, CREATED_AT 
   FROM AUDITORIA 
   ORDER BY CREATED_AT DESC;
   ```
2. **Evidencias verificables:**
   * Se registraron ordenadamente:
     - `REGISTRO_PACIENTE`
     - `LOGIN_EXITOSO` (paciente)
     - `RESERVA_CITA`
     - `TRIAJE_EMERGENCIA`
     - `CREACION_ATENCION`
     - `CIERRE_ATENCION`
     - `CREACION_RECETA`
     - `CONSULTA_HISTORIA`
   * **Cero exposición clínica:** En ninguna columna ni registro existen síntomas, nombres de medicamentos ni diagnósticos médicos.
   * **Inmutabilidad:** Si se intenta ejecutar `DELETE FROM AUDITORIA WHERE ID = 1`, Oracle ATP bloquea la operación con `ORA-20000: Registro inmutable`.
