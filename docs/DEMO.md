# Guion de Demostración en Vivo — MediTriaje 2.0 (10 Minutos)

> **Propósito:** Guía de ejecución para presentar la plataforma MediTriaje 2.0 ante evaluadores o comité académico en un recorrido fluido, profesional y cronometrado de **10 minutos**, demostrando la integridad de datos, seguridad estricta, validaciones de norma colombiana y trazabilidad asistencial inmutable.

---

## 1. Cuentas de Demostración Preconfiguradas (`@demo.meditriaje.test`)

Todas las cuentas operan con la contraseña definida en la variable de entorno `DEMO_PASSWORD` (≥ 12 caracteres).

| Rol | Correo Demostrativo | Qué Demostrar / Propósito |
| :--- | :--- | :--- |
| **Administrador** | `admin@demo.meditriaje.test` | Gestión de infraestructura (institución, sedes, especialidades), alta de médicos, generación de turnos, métricas y visor de auditoría de seguridad. |
| **Médico General 1** | `dra.gomez@demo.meditriaje.test` | Consulta de agenda, panel de alergias previas del paciente, atención médica con signos vitales, evolución, CIE-10, cierre inmutable, prescripción de recetas y seguimiento post-atención. |
| **Médico General 2** | `dr.rodriguez@demo.meditriaje.test` | Atención médica de control de hipertensión, registro de enmienda clínica inmutable (ADR-008) y registro de inasistencia (*no-show*). |
| **Médico Internista** | `dra.restrepo@demo.meditriaje.test` | Agenda de medicina interna y validación de relación asistencial. |
| **Farmacéutico** | `farmacia@demo.meditriaje.test` | Ventanilla de farmacia, validación de vigencia de receta y dispensación con control de saldos y lote INVIMA. |
| **Paciente 1** | `laura.morales@demo.meditriaje.test` | Paciente con historia clínica consolidada: triaje leve previo, atención cerrada, receta prescrita y dispensada, y generación de QR de salud de emergencia. |
| **Paciente 2** | `santiago.moreno@demo.meditriaje.test` | Paciente con registro de triaje de emergencia vital (Nivel I). |
| **Paciente 3** | `valentina.castro@demo.meditriaje.test` | Paciente con diagnóstico de hipertensión y enmienda médica en su historia. |
| **Paciente 4** | `mateo.herrera@demo.meditriaje.test` | Paciente con alergia autorreportada activa (polen y gramíneas). |
| **Paciente 5** | `camila.pena@demo.meditriaje.test` | Paciente con historial de alergia inactivada por prueba negativa. |

---

## 2. Cronograma del Recorrido de 10 Minutos

```
00:00 ─── Min 1: Presentación & Registro con Consentimiento (Norma Colombiana)
01:30 ─── Min 2: Triaje No Urgente & Agendamiento Asistencial
03:00 ─── Min 3: Triaje con Alarma — Corte de Emergencia Vital (123)
04:00 ─── Min 5: Consulta Médica: Alergias, Signos Vitales, Cierre & Receta
06:30 ─── Min 6: Portal del Paciente: Historia Inmutable & Receta Electrónica
07:30 ─── Min 7: Demostración de Seguridad: IDOR & Bloqueo de Acceso Administrativo (403)
08:30 ─── Min 8: Código QR de Emergencia con PIN & Lectura Paramédica
09:15 ─── Min 9: Ventanilla Farmacéutica & Auditoría Inmutable de Seguridad
10:00 ─── Min 10: Conclusiones & Cierre
```

---

## 3. Guion Paso a Paso

### Minuto 0:00 – 01:30 · Paso 1: Registro del Paciente con Consentimiento y Validación Colombiana
1. Abrir navegador en `#/register`.
2. **Explicar:** "MediTriaje 2.0 valida en tiempo real los documentos de identidad colombianos (CC, TI, RC, CE, PA), la coherencia etaria y el número celular según la normatividad nacional."
3. Probar validación reactiva:
   - Seleccionar **Cédula de Ciudadanía (CC)** e ingresar una fecha de menor de edad (ej. año 2015).
   - Mostrar cómo la interfaz bloquea el avance y advierte: *"CC requiere al menos 18 años de edad"*.
   - Corregir fecha a mayor de edad, ingresar celular válido de 10 dígitos iniciando en `3`.
4. En el paso de credenciales, hacer clic en el enlace para abrir el modal del **Consentimiento Informado** (Ley 1581 de 2012 / Res. 1995 de 1999).
5. Aceptar el consentimiento y completar el registro.

### Minuto 01:30 – 03:00 · Paso 2: Triaje Leve y Agendamiento Vinculado
1. Con la sesión del paciente abierta, navegar a `#/patient/triage`.
2. Seleccionar el síntoma **"Dolor de garganta / odinofagia"** (o *"Cefalea"*), intensidad 4/10, duración 24 horas.
3. Presionar **"Evaluar Triaje"**.
4. **Destacar:**
   - Resultado determinista: **Nivel IV (Prioritario / No urgente)**, ruta sugerida `CITA_PRESENCIAL`.
   - Aviso institucional visible: *"Prototipo académico. Orienta, no diagnostica ni reemplaza la valoración de un profesional de la salud."*
5. Hacer clic en **"Agendar cita médica"**:
   - Se abre el selector de turnos con el triaje preseleccionado.
   - Escoger un turno libre de la **Dra. María Paula Gómez** y confirmar reserva.
   - Mostrar el código de confirmación de cita en estado `PROGRAMADA`.

### Minuto 03:00 – 04:00 · Paso 3: Triaje con Síntoma de Alarma (Corte de Emergencia)
1. Iniciar un nuevo triaje (`#/patient/triage`).
2. Seleccionar un síntoma de alarma: **"Dolor torácico opresivo"** (badge rojo de alarma).
3. Presionar **"Evaluar Triaje"**.
4. **Destacar regla de prototipo infalible:**
   - La pantalla cambia inmediatamente al banner rojo de alerta máxima: **Nivel I — Emergencia Vital**.
   - Mensaje imperativo: *"Llama de inmediato al 123 o acude a urgencias."*
   - Botón directo de llamada `tel:123`.
   - **Cero botones o posibilidades de agendar cita** (aislamiento clínico estricto).

### Minuto 04:00 – 06:30 · Paso 4: Consulta Médica (Atención, Alergias, Cierre y Receta)
1. Cerrar sesión e iniciar sesión como médico: `dra.gomez@demo.meditriaje.test`.
2. Entrar a la **Agenda del Profesional** (`#/professional/agenda`):
   - Mostrar el listado del día y seleccionar la cita recién reservada por el paciente.
3. Presionar **"Iniciar Atención"**:
   - Se despliega el panel de **Alergias Previas** (ADR-007, T4): *"Sin alergias registradas (esto no confirma que no tenga)"*.
   - Registrar una alergia: sustancia *"Penicilina"*, reacción *"Anafilaxia"*, severidad *"GRAVE"*.
4. Diligenciar evolución médica:
   - Motivo de consulta y evolución clínica.
   - Signos vitales: Presión arterial `120/80`, FC `72`, SatO2 `98%`, Temp `36.5`.
   - Diagnóstico CIE-10: buscar y seleccionar `R51 - Cefalea`.
5. Presionar **"Cerrar Atención Médica"**:
   - Explicar la inmutabilidad: la atención se consolida y la cita pasa a `ATENDIDA`. Triggers Oracle impiden modificaciones directas.
6. En la sección de prescripción, presionar **"Emitir Receta Médica"**:
   - Seleccionar medicamentos del catálogo (ej. *Acetaminofén 500 mg*, *Ibuprofeno 400 mg*).
   - Generar la receta con 30 días de vigencia y snapshot farmacéutico inmutable congelado en base de datos.

### Minuto 06:30 – 07:30 · Paso 5: Portal del Paciente (Historia Clínica y Receta)
1. Cerrar sesión e ingresar nuevamente con el paciente `laura.morales@demo.meditriaje.test`.
2. Ir a **"Mi Historia Clínica"** (`#/patient/history`):
   - Mostrar la atención médica cerrada, diagnóstico CIE-10, signos vitales y datos del profesional responsable.
3. Ir a **"Mis Recetas"** (`#/patient/prescriptions`):
   - Ver la receta electrónica emitida con sus medicamentos, posología y estado de vigencia.

### Minuto 07:30 – 08:30 · Paso 6: Prueba de Seguridad en Vivo (Mitigación IDOR y Bloqueo Administrativo)
1. **Intento de Acceso Cruzado (IDOR):**
   - Mostrar cómo, si el paciente intenta acceder por URL o API al triaje o historia de otro paciente (`/api/v1/triage/{otroId}`), el backend responde tajantemente `403 Forbidden` (`AccesoNoAutorizadoException`).
2. **Principio de Mínimo Privilegio para Administradores:**
   - Iniciar sesión como `admin@demo.meditriaje.test`.
   - Intentar acceder a `/api/v1/attentions` o `/api/v1/prescriptions`.
   - El sistema arroja **403 Forbidden**. El administrador gestiona agendas, infraestructura y usuarios, pero **jamás tiene acceso a datos clínicos de pacientes** (ADR-007).

### Minuto 08:30 – 09:15 · Paso 7: Código QR Temporal de Salud para Paramédicos
1. En el portal del paciente (`#/patient/dashboard`), hacer clic en **"Generar QR de Emergencia"**.
2. Fijar un PIN de seguridad de 4 dígitos (ej. `1234`).
3. El sistema genera un QR con token criptográfico firmado temporal (expiración a 15 minutos).
4. Abrir una ventana de incógnito y acceder a la URL pública del QR:
   - Solicita el PIN de 4 dígitos.
   - Tras ingresar el PIN, despliega el **Resumen Clínico de Emergencia**: tipo de sangre, alergias activas con origen profesional/paciente, diagnósticos relevantes y medicamentos vigentes.
   - Mostrar el aviso legal y advertencia de uso exclusivo para urgencias.

### Minuto 09:15 – 10:00 · Paso 8: Farmacia, Visor de Auditoría y Cierre
1. Iniciar sesión como `farmacia@demo.meditriaje.test`:
   - Mostrar cómo el farmacéutico valida la receta emitida y registra la entrega con lote INVIMA y control de saldos.
2. Iniciar sesión como `admin@demo.meditriaje.test` e ingresar al **Visor de Auditoría de Seguridad**:
   - Observar el registro inmutable de cada evento (`REGISTRO_PACIENTE`, `TRIAJE_REALIZADO`, `RESERVA_CITA`, `CIERRE_ATENCION`, `EMISION_RECETA`, `DISPENSACION_RECETA`).
   - Resaltar que los logs y eventos de auditoría **no exponen datos clínicos (PHI) ni contraseñas**.
3. **Cierre:** "MediTriaje 2.0 combina arquitectura limpia, inmutabilidad relacional en Oracle ATP y estricto cumplimiento normativo en salud."

---

## 4. Plan de Contingencia (Plan B)

Si durante la demostración en vivo se presenta una falla de red externa, latencia imprevista en Render Free o suspensión del servicio gratuito de Oracle Cloud ATP:

1. **Revisión previa obligatoria (15 minutos antes):**
   - Acceder a la consola de Oracle Cloud y verificar que la base de datos `MEDITRIAJE` esté en estado **Available** (Verde).
   - Realizar una petición a `GET https://tu-backend.onrender.com/api/v1/ping` para despertar el contenedor de Render y mitigar el *cold start*.
2. **Video de Respaldo Pre-grabado:**
   - Disponer de un video en alta definición (1080p, 10 minutos de duración) cubriendo el recorrido exacto de este guion.
   - En caso de indisponibilidad técnica, reproducir el video explicando en vivo la arquitectura y decisiones técnicas.
