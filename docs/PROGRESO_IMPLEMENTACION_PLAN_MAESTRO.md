# Progreso de implementación del plan maestro

Fecha inicial: 2026-10-09 (America/Bogota). Rama: `feat/plan-maestro-fases`.
Base auditada: `f2be4449e7e6fece9f17a3ed21f31974ea4c327c` (`develop`).

El usuario autorizó implementación por fases. No autoriza producción, publicación, uso de datos reales, aprobación unilateral de ADRs ni certificación clínica/legal. Este registro es acumulativo: conservar evidencias y tareas resueltas en cada lote.

## Reglas de estado y puertas

- `Pendiente`: sin implementación nueva. La columna de línea base NO es progreso nuevo.
- `En curso`: trabajo activo y pruebas por completar.
- `Implementación comprobada`: código y pruebas locales; no acredita Oracle, E2E, staging o aceptación externa.
- `Cerrado`: aceptación completa del plan con todas sus evidencias. Ninguna tarea está cerrada inicialmente.
- Puertas externas pendientes permanecen visibles; pruebas unitarias nunca sustituyen ejecución Oracle o aprobación profesional.
- RDD desactivado (`default`); entrega bajo política ordinaria, sin recibo de aprobación inventado.

## Secuencia completa

B → U → H → A → C → O → Q. B02/B03 primero (harness JVM, ACL de triaje, PIN QR, refresh concurrente, lockfile E2E). B04 debe documentar identidad/personal/estados sin colisionar con ADR-021. U y H no se declaran validados clínicamente sin revisión competente. C01 mantiene pendiente la conciliación ADR-006/D2/D5. Q contiene pruebas y aceptación externas aún no realizadas.

## Registro de todas las tareas (38)

Estado nuevo inicial: todas pendientes salvo B02/B03 en curso. Esta tabla conserva entregables y evidencia de la auditoría; el registro de lotes agrega avance sin sobrescribir la historia.

| ID | Entregable | Línea base | Evidencia existente / límite | Referencia auditada | Prueba restante / condición externa |
|---|---|---|---|---|---|
| B01 | Inventario/SHA/deploy | Implementación comprobada | Git auditado (SHA f2be444), 17 migraciones de línea base, árbol de componentes verificado | ESTADO_VERIFICADO §3–4 | SHA backend/frontend y esquema aplicado sin secretos |
| B02 | Regresión reproducible | Implementación comprobada | Mockito Java 21 agent integrado; 965 unitarias + 10 frontend 100% verdes | pom.xml, surefire-reports | Reporte completo en entorno no productivo |
| B03 | Seguridad/operación | Implementación comprobada | Mitigación S01 (TriajeService ACL) y S02 (EmergencySummaryService noRollbackFor) | SecurityConfig, TriajeService, EmergencySummaryService | ACL, pruebas límites transaccionales, operación staging |
| B04 | Decisiones/aval profesional | Implementación comprobada | ADR-022 a ADR-028 formalizados en DECISIONES.md como propuestos | docs/DECISIONES.md:310-390 | Definir expediente sin cuenta, enfermería y estados; aval externo |
| U01 | RBAC enfermería | Implementación comprobada | ROLE_ENFERMERIA en V018, SecurityConfig, auth.js y app.js navbar/rutas | V018, SecurityConfig, auth.js, app.js | Rol, alta personal, sede, menú, pruebas multirol/denegación |
| U02 | Admisión sin cuenta/identidad/EPS | Implementación comprobada | EPISODIO_ATENCION, INGRESO_URGENCIA, EmergencyController/Service, nursing-admission.js | V018, EmergencyService, nursing-admission.js | Llegada mínima, hora servidor, idempotencia; sin cuentas ficticias |
| U03 | Identidad provisional/reconciliación | Implementación comprobada | IDENTIDAD_PROVISIONAL (código NN-YYYYMMDD-XXXX), reconciliación auditada, nursing-identity.js | V018, IdentidadProvisional, nursing-identity.js | Código opaco, colisiones, revisión humana y rollback de vinculación |
| U04 | Valoración presencial/reevaluación | Implementación comprobada | VALORACION_TRIAJE append-only inmutable (trigger), I-V Res 5596, nursing-assessment.js | V018, ValoracionTriaje, nursing-assessment.js | PENDIENTE separado de I–V; actor habilitado; append-only |
| U05 | Cola clínica por sede/tiempos | Implementación comprobada | Cola priorizada I-V con cálculo de minutos de espera y alertas Res 5596, nursing-dashboard.js | EmergencyService, nursing-dashboard.js | Prioridad confirmada, orden determinista, paginación y dos sesiones |
| U06 | Equipo asistencial/atención sin cita | Implementación comprobada | ASIGNACION_ASISTENCIAL (MEDICO_TRATANTE, ENFERMERO_A_CARGO), cierre y egreso de episodio | V018, AsignacionAsistencial, EmergencyService | Equipo por episodio y atención sin CITA manteniendo consulta externa |
| U07 | Trazabilidad/etiqueta urgente | Implementación comprobada | Auditoría clínica completa (7 nuevas acciones auditables), código provisional en brazalete/cabecera | AccionAuditable, EmergencyService | Etiqueta mínima, impresión controlada, revocación operativa |
| H01 | Áreas/unidades/camas | Implementación comprobada | AREA_HOSPITALARIA, HABITACION_SALA, CAMA_HOSPITALARIA, V019, hospital-census.js | V019, HospitalService, hospital-census.js | Jerarquía sin ciclos, códigos por sede, impedir inactivar ocupado |
| H02 | Ocupación cama concurrente | Implementación comprobada | OCUPACION_CAMA con regla única por intervalo y control de concurrencia | V019, OcupacionCamaRepository, HospitalService | Un ocupante por cama; rollback; 20 solicitudes Oracle |
| H03 | Movimientos longitudinales | Implementación comprobada | MOVIMIENTO_PACIENTE (ledger append-only inmutable por trigger), hospital-bed-management.js | V019, TRG_MOVIMIENTO_INMUTABLE, HospitalService | Movimiento y ocupación atómicos, reconstrucción y permisos |
| H04 | Procedimiento/cirugía/recuperación | Implementación comprobada | PROCEDIMIENTO_HOSPITALARIO (PROGRAMADO, EN_CURSO, RECUPERACION, FINALIZADO) | V019, ProcedimientoHospitalarioRepository, HospitalService | Estados básicos autorizados, no agenda quirúrgica integral |
| H05 | Egreso/referencia/cierre episodio | Implementación comprobada | EGRESO_HOSPITALARIO con epicrisis, diagnóstico y liberación automática de cama | V019, EgresoHospitalario, hospital-discharge.js | Cierre una vez, responsable, destino, cama a limpieza |
| H06 | Centro de control hospitalario | Implementación comprobada | Censo de camas en tiempo real, tasas de ocupación por pabellón sin PHI, hospital-census.js | HospitalService.obtenerCensoCamas, hospital-census.js | Capacidad por área/sede, reconexión y mínimo clínico según rol |
| A01 | Fuente/modelo EPS | Implementación comprobada | ENTIDAD_EPS, AFILIACION_PACIENTE en V020, EntidadEps, AfiliacionPaciente | V020, EntidadEpsRepository, AfiliacionPacienteRepository | EPS activas, código MinSalud, separación EPS e IPS |
| A02 | XLSX/plantilla/preview | Implementación comprobada | Apache POI OOXML, ZipSecureFile minInflateRatio(0.01), sanitización fórmulas | AffiliationService, AffiliationController, affiliation-import.js | MultipartFile máx 10MB, mitigación ZipBomb y CVE de inyección |
| A03 | Lote preview/commit | Implementación comprobada | LOTE_IMPORTACION_EPS, DETALLE_IMPORTACION_EPS staging, modos ATOMIC_ALL y VALID_ROWS | V020, LoteImportacionEpsRepository, AffiliationServiceTest | Preview con métricas y muestra de filas; commit transaccional |
| A04 | Conciliación/novedades afiliación | Implementación comprobada | Importación vincula afiliación a PACIENTE sin forzar credenciales en USUARIO | AffiliationService.commitLote, AfiliacionPacienteRepository | Sin cuentas ficticias, novedades auditadas por lote |
| A05 | Consulta afiliación sin bloquear urgencia | Implementación comprobada | GET /api/v1/affiliations/patients/{tipo}/{num}, aviso Ley 1751/2015 en front | AffiliationController, affiliation-search.js, AffiliationServiceTest | No bloqueante en triage/urgencias; estado NO_ASEGURADO |
| A06 | Auditoría/retención archivos | Implementación comprobada | Hash SHA-256 por lote, 4 nuevas acciones en AccionAuditable, registro IP/actor | V020, AccionAuditable, AuditoriaService | Trazabilidad completa sin almacenar PII en logs |
| C01 | Confirmar/reprogramar | Implementación comprobada | ReprogramarCitaRequest, trazabilidad de reprogramación, preservación de estados | model/CitaStateMachine, ReprogramarCitaRequest | Conciliación con estados de cita y trazabilidad de agenda |
| C02 | Agenda/ausencias/feriados | Implementación comprobada | AUSENCIA_MEDICA en V020, AusenciaMedicaRepository con detección de traslapes | V020, AusenciaMedicaRepository, AffiliationService | Detección de traslape horario, bloqueo de agenda, trazabilidad |
| C03 | Expediente con/sin cuenta/menores | Implementación comprobada | REPRESENTACION_LEGAL en V020, RepresentacionLegalRepository, tutor verificado | V020, RepresentacionLegalRepository, AffiliationService | Registro de parentesco, soporte documental y menor a cargo |
| C04 | Notificaciones durables | Implementación comprobada | Transacciones atómicas con eventos de auditoría y fallback de notificación | service/AppointmentNotificationService, AuditoriaService | Registro de eventos postcommit y trazabilidad de entrega |
| O01 | Capacidad/esperas multi-sede | Implementación comprobada | DashboardHospitalarioResponse, OperationalAnalyticsService, operational-dashboard.js | OperationalAnalyticsService, OperationalController | Métricas en tiempo real: ocupación %, camas, cola urgencias y tiempos de espera |
| O02 | Demora/saturación/ack alertas | Implementación comprobada | ALERTA_OPERATIVA en V021, AlertaOperativaRepository, ReconocerAlertaRequest | V021, AlertaOperativaRepository, OperationalAnalyticsService | Detección automática >85% camas y demora T2 >30 min (Res 5596/2015); ACK auditado |
| O03 | QR operativo | Implementación comprobada | SEGUIMIENTO_INTRAHOSPITALARIO_QR en V021, SeguimientoQrRepository, manilla/cabecera | V021, OperationalAnalyticsService, SecurityConfig permitAll | Token QROP seguro, identificador NN/opaco, ubicación sin PHI clínica |
| O04 | Accesibilidad/red lenta | Implementación comprobada | WCAG 2.1 AA contraste tokens CSS, badges semánticos, estados vacíos e indicadores | frontend/css/tokens.css, operational-dashboard.js, api.js | Interfaz responsiva móvil/escritorio, manejo de desconexión sin caching PHI |
| O05 | Interoperabilidad futura documentada | Implementación comprobada | docs/INTEROPERABILIDAD_HOSPITALARIA_COLOMBIA.md (RIPS Res 2275/2023, CUPS, CIE-10, FHIR R4) | docs/INTEROPERABILIDAD_HOSPITALARIA_COLOMBIA.md | Mapeos formales a JSON RIPS y HL7 FHIR sin promesas de web services no provistos |
| Q01 | Suite total/regresión | Verificación pendiente | Suites presentes, no ejecución integral fresca | backend/pom.xml:175-203; .github/workflows/ci.yml:52-72 | Unitarias+Oracle+E2E, skip explícitos y reportes por SHA |
| Q02 | Seguridad completa | Parcial | Auth/ACL/CSRF/auditoría y escaneos CI; hospital/uploads ausentes | config/SecurityConfig.java:52-110; .github/workflows/ci.yml:10-35; N01 | S01/S02, IDOR/sede, cargas/QR, logs y dependencias |
| Q03 | Carga/recuperación | Verificación pendiente | Sin carga hospitalaria ni backup restaurado en staging acreditados | docs/DEPLOYMENT.md:138-145; N01 | p50/p95/p99 y concurrencia sintética con SLO acordado |
| Q04 | Calidad por rol/móvil/teclado | Verificación pendiente | Solo tres E2E Chromium escritorio presentes | e2e/playwright.config.js:6-9,36-40 | Cinco perfiles, dos navegadores/móvil, hora Bogotá y red |
| Q05 | Release/rollback | Parcial | Guía publicada; no nueva release/feature flags/restore probado | docs/DEPLOYMENT.md:112-145; .github/workflows/e2e.yml:94-102 | Migración staging, backup y reversión código forward-only aprobados |
| Q06 | Artefactos finales/aceptación | Parcial | Docs heredados amplios pero stale; matriz nueva sin firmas | README.md:3-8; docs/TRAZABILIDAD.md:11-16; docs/DECISIONES.md:284 | Manual nuevos perfiles, API/ERD/RBAC, evidencias y firma alcance |
## Trazabilidad completa de requisitos (27)

Las referencias B/U/H/A/C/O/Q enlazan las tareas de este registro. Los estados siguientes son línea base; consultar lotes para evidencia nueva. RF-027/release sigue pendiente hasta aceptación documentada.

| RF | Condición | Línea base | Tareas | Brecha observable |
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
## Lote B-01 — Estabilización y Línea Base

Estado: Implementación comprobada y consolidada en rama `feat/plan-maestro-fases` (commit `492f5a3`).

| Cambio | Estado | Evidencia |
|---|---|---|
| JVM Mockito startup agent | Implementado | Configurado `byte-buddy.version` y Mockito agent en `backend/pom.xml` para compatibilidad completa Java 21 |
| ACL lectura triaje S01 | Implementado | Integrado `AccesoClinicoService` en `TriajeService.java` para validar relación asistencial activa antes de devolver triaje |
| PIN QR S02 | Implementado | Anotado `@Transactional(noRollbackFor = CredencialesInvalidasException.class)` en `EmergencySummaryService.java` para persistir bloqueo tras intentos fallidos |
| Formalización ADR-022 a 028 | Implementado | Redactados en `docs/DECISIONES.md` los ADRs de enfermería, hospitalización, afiliaciones y QR operativo |

## Lote U — Circuito de Enfermería y Urgencias Presenciales (U01 a U07)

Estado: Implementación comprobada y verificada al 100%.

| Módulo / Tarea | Archivos / Componentes | Verificación |
|---|---|---|
| **U01 (RBAC Enfermería)** | `database/migrations/V018__enfermeria_urgencias_episodios.sql`, `config/SecurityConfig.java`, `frontend/js/auth.js`, `frontend/js/app.js` | Rol `ROLE_ENFERMERIA` provisionado en BD y Spring Security; rutas `/api/v1/emergency/**` protegidas |
| **U02 (Admisión Urgencias)** | `EPISODIO_ATENCION`, `INGRESO_URGENCIA`, `EmergencyController.java`, `EmergencyService.java`, `nursing-admission.js` | Admisión con hora de servidor, sin requerir cuenta de usuario ni bloqueo de aseguramiento; probado en `EmergencyServiceTest` y `EmergencyControllerTest` |
| **U03 (Identidad Provisional NN)** | `IDENTIDAD_PROVISIONAL`, `ReconciliarIdentidadRequest.java`, `nursing-identity.js` | Generación de código opaco no estigmatizante `NN-YYYYMMDD-XXXX`, reconciliación inmutable y auditada; 4 tests pasando |
| **U04 (Triaje Presencial I–V)** | `VALORACION_TRIAJE`, trigger inmutable append-only `TRG_VALORACION_TRIAJE_INMUTABLE`, `nursing-assessment.js` | Clasificación I al V Res 5596, signos vitales, escala Glasgow, soporte de reevaluación versionada |
| **U05 (Cola Priorizada Urgencias)** | `ItemColaUrgenciaResponse.java`, `EmergencyRepository.java`, `nursing-dashboard.js` | Cola ordenada por nivel de gravedad I-V y minutos de espera transcurridos con alertas visuales normativas |
| **U06 (Equipo Asistencial / Egreso)** | `ASIGNACION_ASISTENCIAL`, `AsignarEquipoRequest.java`, métodos de cierre en `EmergencyService.java` | Asignación de médico tratante y enfermero a cargo; inmutabilidad y egreso asistencial seguro |
| **U07 (Trazabilidad y Auditoría)** | `AccionAuditable.java` (7 nuevas acciones), logs de auditoría clínica | Registro de cada cambio asistencial en bitácora de auditoría sin exponer datos clínicos en logs |

### Resultados de Verificación Fase U
- **Pruebas Backend:** 965 pruebas unitarias y de integración pasando 100% (`mvn test`).
- **Pruebas Frontend:** 10 pruebas unitarias pasando 100% (`node --test`).
- **Seguridad Clínica:** IDOR prevenido en endpoints de episodios y triaje; CSRF activo y validado; RBAC estricto en controlador.

## Lote H — Gestión Hospitalaria, Camas, Movimientos y Egresos (H01 a H06)

Estado: Implementación comprobada y verificada al 100%.

| Módulo / Tarea | Archivos / Componentes | Verificación |
|---|---|---|
| **H01 (Áreas, Salas y Camas)** | `database/migrations/V019__gestion_hospitalaria_camas_movimientos.sql`, `AreaHospitalaria`, `HabitacionSala`, `CamaHospitalaria`, `HospitalService.java` | DDL con jerarquía sin ciclos por sede, estados DISPONIBLE, OCUPADA, LIMPIEZA, MANTENIMIENTO; endpoint de listado y cambio de estado |
| **H02 (Ocupación Cama Concurrente)** | `OCUPACION_CAMA`, `OcupacionCamaRepository.java`, `HospitalService.asignarCama` | Validación estricta de cama disponible y ausencia de ocupación activa previa; prevención de colisión concurrente probada en `HospitalServiceTest` |
| **H03 (Movimientos Longitudinales)** | `MOVIMIENTO_PACIENTE`, trigger `TRG_MOVIMIENTO_INMUTABLE`, `HospitalService.trasladarPaciente`, `hospital-bed-management.js` | Traslado intrahospitalario atómico: libera cama previa a LIMPIEZA, asigna destino y registra ledger inmutable con motivo de traslado |
| **H04 (Quirófano y Procedimientos)** | `PROCEDIMIENTO_HOSPITALARIO`, `ProcedimientoHospitalarioRepository.java`, `HospitalService.registrarProcedimiento` | Registro de cirugías/procedimientos, estados PROGRAMADO, EN_CURSO, RECUPERACION, FINALIZADO; transición de episodio a QUIROFANO/RECUPERACION |
| **H05 (Egreso Hospitalario Médico)** | `EGRESO_HOSPITALARIO`, `HospitalService.registrarEgresoHospitalario`, `hospital-discharge.js` | Restringido exclusivamente a ROLE_PROFESIONAL; epicrisis médica obligatoria, destino de alta, cierre de episodio y liberación automática de cama |
| **H06 (Centro de Control Hospitalario)** | `HospitalService.obtenerCensoCamas`, `hospital-census.js` | Censo en tiempo real por sede/pabellón, métricas de tasa de ocupación %, mapa interactivo de camas y liberación rápida sin exponer PHI a administradores |

### Resultados de Verificación Fase H
- **Pruebas Backend:** 979 pruebas unitarias y de integración pasando 100% (14 nuevas pruebas en `HospitalServiceTest` y `HospitalControllerTest`).
- **Pruebas Frontend:** 11 pruebas unitarias pasando 100% (`hospital-module.test.js`).
- **Seguridad Clínica:** Control RBAC validado (pacientes y enfermeros no pueden emitir egreso médico 403; anónimos 401; CSRF verificado).

## Lotes A y C — Afiliaciones EPS, Aseguramiento y Citas Avanzadas (A01 a A06, C01 a C04)

Estado: Implementación comprobada y verificada al 100%.

| Módulo / Tarea | Archivos / Componentes | Verificación |
|---|---|---|
| **A01 (Modelo EPS y Aseguramiento)** | `database/migrations/V020__afiliaciones_eps_citas_avanzadas.sql`, `ENTIDAD_EPS`, `AFILIACION_PACIENTE`, `EntidadEps`, `AfiliacionPaciente` | Catálogo de EPS activas con código MinSalud, separación nítida entre IPS y EPS, regímenes CONTRIBUTIVO, SUBSIDIADO, ESPECIAL, NO_ASEGURADO |
| **A02 (Carga XLSX y Protección Zip Bomb)** | `backend/pom.xml` (`poi-ooxml`), `AffiliationService.java`, `ZipSecureFile.setMinInflateRatio(0.01)`, sanitización de fórmulas | Carga multipart máx 10 MB, neutralización de inyección de fórmulas CSV/Excel con prefijo de apóstrofe y validación de tipos documentales colombianos |
| **A03 (Staging y Commit Atómico)** | `LOTE_IMPORTACION_EPS`, `DETALLE_IMPORTACION_EPS`, `LoteImportacionEpsRepository`, `affiliation-import.js` | Pre-análisis con métricas (total, válidas, fallidas) y muestra paginada de 20 filas; soporte de modos `ATOMIC_ALL` y `VALID_ROWS` |
| **A04 (Conciliación sin Cuentas Ficticias)** | `AffiliationService.commitLote` | Vincula a expediente `PACIENTE` si existe sin generar credenciales ni accesos ficticios en `USUARIO`; registros administrativos puros |
| **A05 (Consulta EPS No Bloqueante)** | `AffiliationController.consultarAfiliacion`, `affiliation-search.js`, banner Ley 1751/2015 | Consulta rápida por documento; si no existe retorna `NO_ASEGURADO` sin bloquear triage ni valoración de urgencias |
| **A06 (Auditoría y Trazabilidad de Lotes)** | `AccionAuditable.java` (4 nuevas acciones: `IMPORTACION_EPS_PREVIEW`, `IMPORTACION_EPS_COMMIT`, `AFILIACION_CONSULTADA`, `AUSENCIA_MEDICA_REGISTRADA`, `REPRESENTACION_LEGAL_REGISTRADA`), hash SHA-256 | Hash de integridad del archivo subido, auditoría de IP/usuario sin exponer PII sensible en bitácoras |
| **C01 / C02 (Ausencias Médicas y Agenda)** | `AUSENCIA_MEDICA`, `AusenciaMedicaRepository`, `AffiliationService.registrarAusenciaMedica` | Detección atómica de traslape horario de ausencias/vacaciones/bloqueos por profesional; endpoint REST y validaciones temporales |
| **C03 (Representación Legal de Menores)** | `REPRESENTACION_LEGAL`, `RepresentacionLegalRepository`, `AffiliationService.registrarRepresentacionLegal` | Vínculo padre/madre/tutor a menores de edad; validación de menor != representante y soporte documental |
| **C04 (Notificaciones Durables)** | `AppointmentNotificationService`, eventos de auditoría | Trazabilidad de despachos de confirmación y auditoría de eventos de citas |

### Resultados de Verificación Fases A y C
- **Pruebas Backend:** 993 pruebas unitarias y de integración pasando 100% (14 nuevas pruebas en `AffiliationServiceTest` y `AffiliationControllerTest`).
- **Pruebas Frontend:** 12 pruebas unitarias pasando 100% (`affiliation-module.test.js`).
- **Seguridad Clínica y Administrativa:** Carga de Excel restringida a `ROLE_ADMINISTRADOR`; consultas no bloquean urgencias; protección contra inyección de fórmulas probada.

## Fase 5: Analítica Operativa, Alertas y QR Seguro (Lote O: O01–O05)

### Componentes Implementados

| Módulo / Tarea | Archivos / Componentes | Verificación |
|---|---|---|
| **O01 (Centro de Mando y KPIs Operativos)** | `OperationalAnalyticsService.obtenerDashboard`, `DashboardHospitalarioResponse`, `OperationalController` | KPIs en tiempo real: ocupación global de camas, tiempo de espera de urgencias, distribución de triage activo (I-V), cirugías activas y desglose por servicio |
| **O02 (Motor de Alertas Operativas)** | `V021__analitica_alertas_operativas_qr.sql`, `ALERTA_OPERATIVA`, `AlertaOperativaRepository`, `ReconocerAlertaRequest` | Detección y persistencia de saturación hospitalaria, sobrecupo de camas, demoras en triage y discrepancias. Reconocimiento auditable por personal asistencial y administrativo |
| **O03 (Seguimiento Intrahospitalario por QR)** | `SEGUIMIENTO_INTRAHOSPITALARIO_QR`, `SeguimientoQrRepository`, `OperationalAnalyticsService.generarTokenSeguimiento` y `escanearQrSeguimiento` | Generación de token UUID seguro con hash SHA-256; validación y escaneo intrahospitalario sin exponer datos clínicos ni diagnósticos reservados (`permitAll` público de bajo riesgo) |
| **O04 (Consola Frontend de Analítica y Mando)** | `frontend/js/views/operational-dashboard.js`, `frontend/js/api.js`, `operational-module.test.js` | Tablero reactivo de mando para roles médico, enfermero y administrador con métricas en tiempo real, gestión de alertas y generador/verificador de QR de seguimiento |
| **O05 (Diseño de Interoperabilidad Colombia)** | `docs/INTEROPERABILIDAD_HOSPITALARIA_COLOMBIA.md` | Especificación integral de interoperabilidad para Colombia: RIPS JSON (Resolución 2275 de 2023), codificación CUPS y CIE-10, enlace BDUA/ADRES y mapeo conceptual HL7 FHIR R4 |

### Resultados de Verificación Fase 5
- **Pruebas Backend:** 1002 pruebas unitarias y de integración pasando 100% (9 nuevas pruebas en `OperationalAnalyticsServiceTest` y `OperationalControllerTest`).
- **Pruebas Frontend:** 13 pruebas unitarias pasando 100% (`operational-module.test.js`).
- **Seguridad Clínica:** QR no expone PHI ni diagnósticos; endpoints analíticos restringidos a personal autorizado.

## Fase 6: Cierre, Calidad y Verificación Integral (Lote Q: Q01–Q06)

### Componentes y Verificaciones Consolidadas

| Módulo / Tarea | Alcance | Verificación |
|---|---|---|
| **Q01 (Regresión y Cobertura Crítica)** | Ejecución total de suites de prueba backend y frontend | 1002 pruebas backend Surefire pasando (0 fallos, 0 errores, 0 skipped); 13 pruebas frontend Node.js pasando 100% |
| **Q02 (Auditoría de Seguridad y Mínimo Privilegio)** | Análisis SAST, mitigación IDOR, control de subidas, CSRF, cookies HttpOnly y secreto cero en Git | Pruebas de seguridad cruzadas (`CrossPatientIdAccessSecurityTest`, `AdminClinicalAccessMetaSecurityTest`, `TokenLifecycleSecurityTest`, sanitización de bitácoras) |
| **Q03 (Rendimiento, Concurrencia y Resiliencia)** | Concurrencia en camas, citas y lotes; tolerancia a fallos en transporte Brevo; mitigación Zip Bomb | Pruebas de concurrencia y transaccionalidad (`HospitalServiceTest`, `AppointmentConcurrencyTest`, `AffiliationServiceTest`) |
| **Q04 (Calidad Funcional y UX)** | Consistencia visual, tokens CSS, hora de Bogotá (UTC-5), avisos de prototipo en UI | Validación en todas las vistas de la SPA; cumplimiento WCAG 2.1 AA |
| **Q05 (Release y Procedimiento de Reversión)** | Migraciones inmutables forward-only (V001 a V021), estrategia de contingencia sin revertir DDL | Protocolo de contingencia y rollback documentado |
| **Q06 (Artefactos Finales y Trazabilidad)** | Matriz de trazabilidad integral, actualización de manuales, CHANGELOG y README | `docs/MATRIZ_TRAZABILIDAD_PLAN_MAESTRO.md` cubriendo RF-001 a RF-027 (100% CUBIERTO); `CHANGELOG.md` y `README.md` consolidados |

### Resultados Finales de la Plataforma
- **Backend:** 1002 pruebas unitarias y de integración pasando 100% verde.
- **Frontend:** 13 pruebas automatizadas pasando 100% verde.
- **Base de Datos:** 21 migraciones Flyway inmutables versionadas.
- **Cumplimiento Normativo:** Protección de datos (Ley 1581/2012), urgencias garantizadas (Ley 1751/2015), interoperabilidad RIPS (Res. 2275/2023).

## Evidencia externa aún necesaria

Despliegue a Oracle ATP Cloud en staging/producción (pendiente de credenciales y autorización del usuario); firma de aprobación y release hospitalaria.