# Matriz de brechas del plan maestro — MediTriaje

**Candidato:** `develop` · `f2be4449e7e6fece9f17a3ed21f31974ea4c327c`.
**Fecha:** 2026-10-09, America/Bogota. Referencia: backlog completo del ZIP, matriz RF-001..027 y prompt pegado.
**Alcance:** contraste y backlog; no se activó SDD ni se aprobaron arquitecturas/DDL. [Estado y evidencias](ESTADO_VERIFICADO.md).

## 1. Cómo leer la matriz

- **Implementado:** requisito completo observable en código inspeccionado; ejecución y despliegue todavía se clasifican aparte.
- **Parcial:** hay una base concreta, pero faltan campos, reglas, estados, seguridad o el resto del flujo.
- **Ausente:** no se localizó implementación de ese requisito en fuentes versionadas inspeccionadas.
- **Verificación pendiente:** no es honesto marcar la tarea satisfecha sin pruebas/operación/aceptación fresca.
- Ningún estado de esta matriz significa certificación clínica o cumplimiento jurídico.

**38 tareas:** 13 parciales; 4 con verificación pendiente; 21 ausentes; **0 completas** conforme a su DoD integral.
**27 RF:** 10 parciales; 15 ausentes; 2 con verificación pendiente; **0 aceptados integralmente**.
El inventario heredado se cuenta por separado: 14 capacidades, 11 implementadas y 3 parciales en código. No sumar ambos conjuntos ni convertirlos en un porcentaje.

### Claves de evidencia

Las referencias Java sin prefijo corresponden a `backend/src/main/java/com/meditriaje/`.
`V002/V003/.../V017` corresponden a los scripts completos de `database/migrations/` del inventario Git (el nombre exacto está en Estado §4 y listado de migraciones).

**N01 — comprobación de ausencia:** CodeGraph por dominio + inventario `git ls-files` de controladores, modelos/repositorios, vistas y DDL + búsqueda en `backend/src/main`, `database/migrations`, `frontend/js` de ENFERMERIA, EPISODIO_ATENCION, INGRESO_URGENCIA, IDENTIDAD_PROVISIONAL, VALORACION_TRIAJE, ASIGNACION_ASISTENCIAL, CAMA_HOSPITALARIA, OCUPACION_CAMA, MOVIMIENTO_PACIENTE, PROCEDIMIENTO_EPISODIO, AFILIACION_ADMINISTRATIVA, LOTE_IMPORTACION, MultipartFile, ALERTA_OPERATIVA y rutas/operaciones reschedule/reprogramar. No se localizaron implementaciones funcionales; solo badges/texto legacy de reprogramación. Se contrastó inventario completo de rutas y tablas para no depender exclusivamente de nombres tentativos. Una ausencia no tiene línea propia; se indica el alcance buscado y la base relacionada. No se afirma inexistencia en ramas no auditadas o despliegues de SHA desconocido.

## 2. Cobertura completa del backlog B/U/H/A/C/O/Q

| ID | Entregable | Estado | Qué existe / qué no demuestra | Evidencia en este SHA | Próxima condición ejecutable |
|---|---|---|---|---|---|
| B01 | Inventario/SHA/deploy | Parcial | Git y fuentes inspeccionados; deploy SHA/DB no verificados | ESTADO_VERIFICADO §3–4 | SHA backend/frontend y esquema aplicado sin secretos |
| B02 | Regresión reproducible | Verificación pendiente | Baseline Node fresco disponible; Maven seguro incompleto; Oracle/E2E no acreditados | ESTADO_VERIFICADO §8 | Reporte completo en entorno no productivo |
| B03 | Seguridad/operación | Parcial | Controles en código; S01 confirmado, S02 inferido; backup sin ensayo | config/SecurityConfig.java:52-110; docs/DEPLOYMENT.md:138-145 | ACL, pruebas límites transaccionales, operación staging |
| B04 | Decisiones/aval profesional | Ausente | No decisiones hospitalarias aprobadas; propuesta ADR-021 colisiona | docs/DECISIONES.md:284-303; AGENTS.md:18 | Definir expediente sin cuenta, enfermería y estados; aval externo |
| U01 | RBAC enfermería | Ausente | Solo cuatro roles y cuatro familias de rutas | V002:156-158; V013:20; frontend/js/app.js:156-196; N01 | Rol, alta personal, sede, menú, pruebas multirol/denegación |
| U02 | Admisión sin cuenta/identidad/EPS | Ausente | No episodio ni admisión; PACIENTE obliga cuenta y documento | V003:11-17; N01 | Llegada mínima, hora servidor, idempotencia; sin cuentas ficticias |
| U03 | Identidad provisional/reconciliación | Ausente | No identidad provisional ni vínculo tardío | V003:11-26; N01 | Código opaco, colisiones, revisión humana y rollback de vinculación |
| U04 | Valoración presencial/reevaluación | Ausente | Triaje existente es autoorientación del paciente | controller/TriajeController.java:41-51; triage/MotorTriajeBasadoEnReglas.java:26,86; N01 | PENDIENTE separado de I–V; actor habilitado; append-only |
| U05 | Cola clínica por sede/tiempos | Ausente | No llegada/valoración sobre la que construir cola | N01; repository/ReporteRepository.java:147 | Prioridad confirmada, orden determinista, paginación y dos sesiones |
| U06 | Equipo asistencial/atención sin cita | Parcial | Relación de cita/historia/break-glass existe; no asignación a episodio | service/AccesoClinicoService.java:169-180; service/ClinicalAttentionService.java:133-165; V008:37; N01 | Equipo por episodio y atención sin CITA manteniendo consulta externa |
| U07 | Trazabilidad/etiqueta urgente | Ausente | QR de resumen no identifica ubicación/episodio | service/EmergencySummaryService.java:114-118,215-274; N01 | Etiqueta mínima, impresión controlada, revocación operativa |
| H01 | Áreas/unidades/camas | Parcial | Institución/sede existe; jerarquía hospitalaria no | controller/admin/AdminSiteController.java:32-96; V004:33; N01 | Jerarquía sin ciclos, códigos por sede, impedir inactivar ocupado |
| H02 | Ocupación cama concurrente | Ausente | No cama/intervalo abierto ni constraint de ocupación | N01 | Un ocupante por cama; rollback; 20 solicitudes Oracle |
| H03 | Movimientos longitudinales | Ausente | No ledger origen/destino ni ubicación actual | N01 | Movimiento y ocupación atómicos, reconstrucción y permisos |
| H04 | Procedimiento/cirugía/recuperación | Ausente | No modelo/endpoints/vistas del procedimiento hospitalario | N01 | Estados básicos autorizados, no agenda quirúrgica integral |
| H05 | Egreso/referencia/cierre episodio | Ausente | Cerrar atención de cita no egresa episodio ni libera cama | service/ClinicalAttentionService.java:240-257; N01 | Cierre una vez, responsable, destino, cama a limpieza |
| H06 | Centro de control hospitalario | Parcial | Hay agregados de citas/triaje/farmacia, no hospital | repository/ReporteRepository.java:32,147,232,326; N01 | Capacidad por área/sede, reconexión y mínimo clínico según rol |
| A01 | Fuente/modelo EPS | Ausente | No asegurador ni afiliación administrativa localizados | N01; V004:17-52 | Separar EPS de IPS, fuente/fecha/estado desconocido |
| A02 | XLSX/plantilla/preview | Ausente | No upload MultipartFile/importación en backend/vistas auditados | N01 | Límites y OOXML/zip-bomb/fórmulas/fechas/filas duplicadas |
| A03 | Lote preview/commit | Ausente | No staging/lote/token/hash de importación | N01 | Política ATOMIC_ALL/VALID_ROWS explícita; commit idempotente |
| A04 | Conciliación/novedades afiliación | Ausente | Registro actual crea cuenta; no expediente administrativo | V003:11-26; N01 | No crear credencial al importar; conflictos con revisión humana |
| A05 | Consulta afiliación sin bloquear urgencia | Ausente | No estado de aseguramiento ni ingreso urgente | N01 | Fuente/fecha y permiso; urgencia independiente de EPS |
| A06 | Auditoría/retención archivos | Ausente | Auditoría general no prueba gestión de originales/lotes | V002:128-145; N01 | Finalidad, descarga restringida, retención y errores sin PII |
| C01 | Confirmar/reprogramar | Parcial | Reserva/cancelación/no-show activos; CONFIRMADA/REPROGRAMADA reservados | model/CitaStateMachine.java:9-34; controller/AppointmentController.java:43-92; docs/TRAZABILIDAD.md:78-79 | ADR conciliado con D2/D5, reprogramación atómica y rollback |
| C02 | Agenda/ausencias/feriados | Parcial | Disponibilidad, slots y agenda existen; no calendario completo | controller/admin/AdminSlotController.java:34-91; controller/ProfessionalAgendaController.java:22-42 | Ausencias/feriados y reservas afectadas; horarios históricos |
| C03 | Expediente con/sin cuenta/menores | Ausente | PACIENTE vinculado obligatoriamente a USUARIO | V003:11-26 | Vinculación verificada y representación legal; no reclamar por cédula sola |
| C04 | Notificaciones durables | Parcial | Brevo y registro FALLIDO existen; envío síncrono y sin outbox acreditada | service/AppointmentService.java:287-290; service/AppointmentNotificationService.java:183-217 | Entrega postcommit, reintentos durables, deduplicación y pruebas de fallo |
| O01 | Capacidad/esperas multi-sede | Parcial | Reporte actual tiene sedes/citas, no tiempos de episodio/camas | repository/ReporteRepository.java:103-119,147; N01 | Definiciones KPI y fixtures hospitalarios SQL reproducibles |
| O02 | Demora/saturación/ack alertas | Ausente | No ALERTA_OPERATIVA ni reconocimiento/silenciamiento auditado | N01 | Configuración operativa no médica; permisos/reloj/desconexión |
| O03 | QR operativo | Ausente | Solo QR de resumen clínico temporal público | service/EmergencyQrService.java:90-110; service/EmergencySummaryService.java:118-285; N01 | Backend autenticado, vista mínima, ubicación, reimpresión/revocación |
| O04 | Accesibilidad/red lenta | Parcial | UI con estados y cliente central; acreditación WCAG/red pendiente | frontend/js/api.js:78-85,119-135; frontend/js/views/landing-view.js:587 | Teclado/screenreader/móvil/dos navegadores; sin offline PHI implícito |
| O05 | Interoperabilidad futura documentada | Ausente | No diseño hospitalario interoperable ni convenio externo verificado | docs/DECISIONES.md:143; N01 | Diseño y catálogos a confirmar; no prometer ADRES/RETHUS/FHIR activos |
| Q01 | Suite total/regresión | Verificación pendiente | Suites presentes, no ejecución integral fresca | backend/pom.xml:175-203; .github/workflows/ci.yml:52-72 | Unitarias+Oracle+E2E, skip explícitos y reportes por SHA |
| Q02 | Seguridad completa | Parcial | Auth/ACL/CSRF/auditoría y escaneos CI; hospital/uploads ausentes | config/SecurityConfig.java:52-110; .github/workflows/ci.yml:10-35; N01 | S01/S02, IDOR/sede, cargas/QR, logs y dependencias |
| Q03 | Carga/recuperación | Verificación pendiente | Sin carga hospitalaria ni backup restaurado en staging acreditados | docs/DEPLOYMENT.md:138-145; N01 | p50/p95/p99 y concurrencia sintética con SLO acordado |
| Q04 | Calidad por rol/móvil/teclado | Verificación pendiente | Solo tres E2E Chromium escritorio presentes | e2e/playwright.config.js:6-9,36-40 | Cinco perfiles, dos navegadores/móvil, hora Bogotá y red |
| Q05 | Release/rollback | Parcial | Guía publicada; no nueva release/feature flags/restore probado | docs/DEPLOYMENT.md:112-145; .github/workflows/e2e.yml:94-102 | Migración staging, backup y reversión código forward-only aprobados |
| Q06 | Artefactos finales/aceptación | Parcial | Docs heredados amplios pero stale; matriz nueva sin firmas | README.md:3-8; docs/TRAZABILIDAD.md:11-16; docs/DECISIONES.md:284 | Manual nuevos perfiles, API/ERD/RBAC, evidencias y firma alcance |

## 3. Trazabilidad RF-001..027 del ZIP

Cada fila remite a la tarea anterior para migración, endpoint, pantalla, permiso y evidencia. En requisitos ausentes, esos artefactos también están **ausentes**, no se inventan URLs, migraciones o tests como si existieran.

| Requisito | Condición | Estado | Tareas | Brecha observable |
|---|---|---|---|---|
| RF-001 | Experiencias de perfiles/farmacia | Parcial | U01 | Cuatro roles heredados, enfermería ausente |
| RF-002 | Ingreso sin documento/cuenta | Ausente | U02 | Modelo obliga cuenta y documento |
| RF-003 | Código provisional opaco | Ausente | U03 | Identidad provisional ausente |
| RF-004 | Identificación tardía conserva historia | Ausente | U03 | Reconciliación ausente |
| RF-005 | Triaje presencial I–V/PENDIENTE | Ausente | U04 | No valoración humana; motor web distinto |
| RF-006 | Reevaluaciones append-only | Ausente | U04 | No valoraciones de episodio |
| RF-007 | Cola profesional por sede | Ausente | U05 | Cola ausente |
| RF-008 | Asignar médico/limitar relación | Parcial | U06 | ACL heredada existe; no equipo por episodio y S01 |
| RF-009 | Urgencia sin EPS | Ausente | U02/A05 | No admisión urgente |
| RF-010 | Áreas/camas | Parcial | H01 | Sedes sí; áreas/camas no |
| RF-011 | Ocupación única concurrente | Ausente | H02 | No constraint/modelo de camas |
| RF-012 | Timeline movimientos | Ausente | H03 | No movimientos hospitalarios |
| RF-013 | Cirugía/recuperación | Ausente | H04 | No procedimientos/localización |
| RF-014 | Egreso/liberación cama | Ausente | H05 | Cierre consulta no es egreso hospitalario |
| RF-015 | Ocupación tiempo real sin PHI admin | Parcial | H06 | Agregados ambulatorios no hospitalarios |
| RF-016 | EPS XLSX preview/commit | Ausente | A02/A03 | No importación |
| RF-017 | Conciliación/idempotencia afiliación | Ausente | A04 | No afiliación |
| RF-018 | Fuente/fecha aseguramiento | Ausente | A01/A05 | No modelo aseguramiento |
| RF-019 | Reprogramación atómica | Parcial | C01 | Infraestructura citas sí; transición/reschedule no |
| RF-020 | Preservar clínica/receta/farmacia/QR | Verificación pendiente | C/Q01 | Código existe; regresión total fresca sin completar |
| RF-021 | Fallo correo no pierde transacción | Parcial | C04 | Captura de fallo sí; durabilidad/postcommit y Oracle pendiente |
| RF-022 | QR operativo sin PHI | Ausente | O03 | QR clínico público no cumple QR ubicación |
| RF-023 | Accesibilidad/red lenta | Parcial | O04 | Patrones UI sí; validación manual y cola refresh pendiente |
| RF-024 | Seguridad/CSRF/IDOR/logs/uploads | Parcial | Q02 | Controles base sí; S01 y uploads ausentes |
| RF-025 | CI/Oracle/E2E/staging | Parcial | Q01/Q04 | Workflows sí; pruebas completas/staging no acreditados |
| RF-026 | Backup restaurable/rollback | Verificación pendiente | Q03/Q05 | Procedimiento documentado, ensayo no verificado |
| RF-027 | Release documentada/aceptada | Parcial | Q06 | Release heredada/docs no aceptación hospitalaria |

## 4. Escenarios de prueba exigidos y evidencia faltante

| Grupo | IDs del plan | Cobertura actual / prueba siguiente |
|---|---|---|
| Seguridad | SEC01..SEC05 | SEC01 episodio ausente; pruebas heredadas de otro paciente/admin/break-glass presentes, no prueban enfermería/equipo/sede. SEC04 afectado por S01 de lectura triaje; SEC05 necesita ventana/justificación con persistencia real |
| Urgencias | URG01..URG07 | Todos requieren episodio/identidad/valoración nuevos; no cubierta admisión NN, menores sin cuenta, identificación tardía, PENDIENTE, reevaluación o doble clic |
| Hospital | HOSP01..HOSP04 | Ausentes camas/traslados/egreso; pruebas Oracle necesarias, no extrapolar concurrencia de slots |
| Afiliaciones | AFF01..AFF06 | Ausente XLSX/lotes/afiliación; probar preview, errores fila, zip-bomb/fórmulas, RBAC, reimportación y acceso urgente sin EPS |
| Citas | CITA01..CITA03 | Reserva concurrente y bloqueo de cancelación con atención tienen código/tests heredados; CITA02 reprogramación no existe. Reejecutar Oracle y no contar mocks como DB |
| Operación | OPS01..OPS02 | Agregados ambulatorios y captura de fallos email existentes; fixtures hospitalarios y caída/recuperación completa no acreditados |
| QR | QR01 | Token clínico no equivale a pulsera operativa. Probar falsificación/copia, permisos, revocación y riesgo S02 |
| Regresión | REG01 | Node8 ejecutado fresco; baseline backend integral y Oracle/Playwright no acreditados. Preservar MFA/recetas/farmacia/historia |

Las tres especificaciones E2E actuales son triaje→cita, atención→receta y aislamiento admin (`e2e/tests/01-patient-triage-appointment.spec.js:19`, `02-professional-attention-recipe.spec.js:19`, `03-admin-security-isolation.spec.js:18`). No equivalen a los escenarios hospitalarios.

## 5. Permisos actuales frente a política candidata

| Operación | Evidencia actual | Diferencia relevante |
|---|---|---|
| Administrar catálogos/personal | @PreAuthorize ADMIN en controladores admin | Enfermería/personales por sede aún no modelados |
| Triaje web POST | Solo ROLE_PACIENTE, TriajeController:42 | No autoriza valoración presencial |
| Triaje GET | isAuthenticated, service comprueba roles | Profesional sin relación permitido (S01); falta usar ACL central |
| Historia clínica | ROLE_PROFESIONAL / dueño paciente + AccesoClinicoService | Relación por citas/historia/break-glass, no equipo por episodio |
| Dispensar | ROLE_FARMACEUTICO, V013 y módulo farmacia | Conservar rol; no convertir administración en farmacia |
| Alta urgente/valoración/traslado/egreso | Ausente | Matriz candidata requiere decidir habilitación, sede y mínimo necesario |
| EPS/preview/commit | Ausente | Permiso administrativo específico y finalidad antes de uploads |
| QR resumen | Público con token/PIN opcional | QR operativo requiere autenticación y autorización asistencial, política distinta |

La política negativa propuesta en ZIP es **candidata**, no reemplaza permisos actuales sin decisión. Acumular rol admin+profesional plantea una regla explícita: AccesoClinicoService prioriza denegación admin en 151-154; diseñar multirol deliberadamente, no relajarlo para comodidad.

## 6. Secuencia de implementación propuesta, sin aprobación implícita

1. **B02/B03 estabilización:** cerrar baseline seguro y harness; prueba negativa S01 y reparación mínima autorizada dentro del alcance existente; reproducir S02 con Oracle sintético; instalación E2E determinista. Sin restore/DDL real.
2. **B04 decisiones:** modelo de personal y sede, expediente sin cuenta, NN/reconciliación, relación por episodio, máquina de estados, retención/importación. Colisión de ADR-021 y contradicción D2/D5 deben resolverse antes de cambiar esos contratos.
3. **U01 primer corte:** RBAC enfermería, alta/adscripción administrativa y menú + pruebas 401/403/usuario inactivo/multirol. Puerta: baseline y política acordadas.
4. **U02–U03:** episodio/llegada idempotente sin datos civiles requeridos; identidad provisional opaca y vinculación auditada. Puerta: decisión de identidad; nunca cuenta web/CC ficticia.
5. **U04–U07:** valoración profesional, PENDIENTE explícito, reevaluación/eventos, cola/equipo y etiqueta. Puerta: criterios clínicos/actor habilitado aprobados; no convertir orientación web en diagnóstico.
6. **H01–H06:** jerarquía/ocupación/movimientos/procedimiento básico/egreso/tablero; constraint de cama y transacciones demostradas con Oracle. Puerta: episodio y decisiones de capacidad.
7. **A01–A06:** EPS administrativa + XLSX seguro + staging/idempotencia/reconciliación; urgencia independiente del aseguramiento. Puerta: fuente/finalidad/retención y estrategia de errores de lote.
8. **C01–C04:** confirmar/reprogramar solo tras ADR compatible D2/D5; agenda completa, expediente/cuenta y notificaciones durables. Puerta: no perder cita antigua si falla reprogramación.
9. **O01–O05:** KPI/alertas/QR operativo/accesibilidad y diseño interoperabilidad, sin conexiones oficiales imaginadas ni offline PHI implícito.
10. **Q01–Q06:** suite completa, security/performance y restauración staging, aceptación/release humana. Ningún deploy/merge automático.

### Diseño candidato del primer incremento U01–U03

**No es un ADR aprobado ni DDL definitivo.** Mantener capas/JDBC/Oracle/Flyway/SPA; reutilizar USUARIO y SEDE. Un registro urgente debe poder existir sin PACIENTE definitivo y sin USUARIO paciente; el código provisional identifica el episodio, no finge documento civil. Reconciliar después mediante evento/referencia auditada y transacción que impida doble vínculo, conservando origen de los registros.

Decisiones indispensables: enfermería como subtipo de PROFESIONAL o personal asistencial separado (reutilizar tabla ahorra estructura pero puede ampliar permisos médicos; tabla separada evita ese acoplamiento pero agrega mapeo/ACL); paciente sin cuenta o identidad ligada inicialmente solo al episodio; quién confirma/revisa rectificación y cómo resolver duplicados. Estas alternativas son de dominio/seguridad, no elecciones silenciosas del auditor.

Superficies candidatas: migración posterior al esquema verificado; DTOs de admisión/reconciliación; service/repository/controller de episodio/personal; AccesoClinicoService extendido por equipo **después** de aprobar política; rutas/vistas enfermería; pruebas negativas, idempotencia, concurrencia de vínculo y Oracle. Contratos propuestos del ZIP (`/api/v1/emergency/admissions`, episodios/identity-reconcile) no existen actualmente y deben fijarse al aprobar diseño.

## 7. Criterio para cerrar B y avanzar

- Candidato/SHA conocido y baseline **completo** con fallos/skipped y artefactos.
- Oracle se prueba realmente o se mantiene explícito NO VERIFICADO; una suite unitaria no elimina ese requisito.
- S01 resuelto/probado y S02 reproducido/clasificado; estado de riesgo documentado.
- ADRs de alcance y estados reconciliados sin aprobarlos unilateralmente.
- Evidencia de staging independiente antes de cualquier escritura; restauración comprobada antes de DDL productivo.
- Revisor clínico/jurídico valida su dominio: tests técnicos no lo sustituyen.

Este documento conserva las brechas, no afirma “100% completado” ni cambia el plan maestro, estados aprobados o datos existentes.

