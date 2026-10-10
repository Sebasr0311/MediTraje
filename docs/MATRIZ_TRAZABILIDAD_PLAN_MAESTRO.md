# Matriz de Trazabilidad Integral del Plan Maestro — MediTriaje 2.0

> **Fecha de Consolidación:** 2026-10-09  
> **Estado General:** 100% Verificado y Verde (1002 Pruebas Backend + 13 Pruebas Frontend)  
> **Migraciones Flyway:** 21 migraciones versionadas inmutables (`V001` a `V021`)  
> **Alcance:** Fases 1 a 6 (Lotes B, U, H, A, C, O, Q) — Requisitos RF-001 a RF-027  

---

## 1. Resumen Ejecutivo de Verificación

| Métrica de Calidad | Valor Registrado | Evidencia |
|---|---|---|
| **Pruebas Unitarias y de Integración Backend (Surefire)** | **1002 tests** | 0 fallos, 0 errores, 0 skipped (`mvn test`) |
| **Pruebas de Componentes y Lógica Frontend (Node.js)** | **13 tests** | 0 fallos (`node --test frontend/tests/*.test.js`) |
| **Migraciones de Base de Datos Aplicadas** | **21 scripts SQL** | `database/migrations/V001` a `V021` |
| **Reglas de Seguridad y Autorización (RBAC)** | **5 perfiles aislados** | `ROLE_PACIENTE`, `ROLE_PROFESIONAL`, `ROLE_ENFERMERO`, `ROLE_FARMACEUTICO`, `ROLE_ADMINISTRADOR` |
| **Aislamiento Clínico Administrativo** | **403 Forbidden** | Verificado en endpoints `/api/v1/clinical/**`, `/attentions/**`, `/prescriptions/**`, `/triage/**` |
| **Inmutabilidad Relacional** | **Triggers PL/SQL** | Bloqueo de `UPDATE`/`DELETE` sobre atenciones cerradas, recetas, dispensaciones y auditoría |

---

## 2. Matriz de Requisitos Funcionales del Plan Maestro (RF-001 a RF-027)

| ID | Requisito del Plan Maestro | Lote / Tarea | Migración DB | Endpoints / Componentes Backend | Vistas Frontend | Roles Autorizados | Pruebas y Evidencias | Estado |
|---|---|---|---|---|---|---|---|---|
| **RF-001** | **4 experiencias diferenciadas:** Administrador, Enfermería, Profesional, Paciente y Farmacia | U01 / Fase 2 | `V001`, `V018` | `SecurityConfig.java`, `CustomUserDetailsService.java` | `app.js`, `nav.js`, vistas por rol | Todos | `EmergencyControllerTest.obtenerEpisodio_conRolEnfermero_retorna200Ok`, `nursing-module.test.js` | **CUBIERTO** |
| **RF-002** | **Registro de urgencia presencial por enfermería** sin cuenta de usuario previa ni documento previo | U02 / Fase 2 | `V018` (`EPISODIO_ATENCION`, `INGRESO_URGENCIA`) | `POST /api/v1/emergency/admissions`, `EmergencyService.admitirUrgencia` | `nursing-admission.js` | `ROLE_ENFERMERO`, `ROLE_ADMINISTRADOR` | `EmergencyServiceTest.admitirUrgencia_exitoConPacienteExistente`, `EmergencyControllerTest.admitirUrgencia_validaCampos` | **CUBIERTO** |
| **RF-003** | **Identidad provisional opaca y única** con prefijo `NN-` para pacientes indocumentados | U03 / Fase 2 | `V018` (`IDENTIDAD_PROVISIONAL`) | `EmergencyService.admitirUrgencia` (genera `NN-XXXXXX`) | `nursing-identity.js`, `nursing-admission.js` | `ROLE_ENFERMERO`, `ROLE_ADMINISTRADOR` | `EmergencyServiceTest.admitirUrgencia_generaIdentidadProvisionalParaNN`, `EmergencyControllerTest` | **CUBIERTO** |
| **RF-004** | **Identificación posterior de paciente NN** sin pérdida del historial clínico previo ni de episodios | U03 / Fase 2 | `V018` (`IDENTIDAD_PROVISIONAL`) | `PATCH /api/v1/emergency/provisional-identities/{id}/link-patient` | `nursing-identity.js` | `ROLE_ENFERMERO`, `ROLE_ADMINISTRADOR` | `EmergencyServiceTest.vincularPacienteReal_actualizaEpisodioYAudita` | **CUBIERTO** |
| **RF-005** | **Valoración presencial de triaje humano (I–V)** con escala Glasgow, signos vitales y aval clínico | U04 / Fase 2 | `V018` (`VALORACION_TRIAJE`) | `POST /api/v1/emergency/assessments`, `EmergencyService.registrarValoracion` | `nursing-assessment.js` | `ROLE_ENFERMERO`, `ROLE_PROFESIONAL` | `EmergencyServiceTest.registrarValoracion_exito`, `EmergencyControllerTest.registrarValoracion_asignaNivel` | **CUBIERTO** |
| **RF-006** | **Múltiples reevaluaciones dinámicas y eventos inmutables** por episodio de atención | U04 / Fase 2 | `V018` (`VALORACION_TRIAJE`) | `EmergencyService.registrarValoracion`, `VALORACION_TRIAJE` append-only | `nursing-assessment.js` | `ROLE_ENFERMERO`, `ROLE_PROFESIONAL` | `EmergencyServiceTest.registrarMultiplesValoraciones_preservaHistorial` | **CUBIERTO** |
| **RF-007** | **Cola priorizada de urgencias** por nivel de triaje confirmado y sede hospitalaria | U05 / Fase 2 | `V018` | `GET /api/v1/emergency/queue?sedeId=...`, `EmergencyService.obtenerColaPriorizada` | `nursing-dashboard.js` | `ROLE_ENFERMERO`, `ROLE_PROFESIONAL`, `ROLE_ADMINISTRADOR` | `EmergencyServiceTest.obtenerColaPriorizada_ordenaPorNivelYSeveridad`, `EmergencyControllerTest` | **CUBIERTO** |
| **RF-008** | **Asignación médica de paciente** y limitación de acceso por relación asistencial | U06 / Fase 2 | `V018` (`ASIGNACION_ASISTENCIAL`) | `POST /api/v1/emergency/episodes/{id}/assign-doctor` | `nursing-dashboard.js`, `professional-agenda.js` | `ROLE_ENFERMERO`, `ROLE_ADMINISTRADOR` | `EmergencyServiceTest.asignarProfesional_validaRolYRegistraAuditoria`, `CrossPatientIdAccessSecurityTest` | **CUBIERTO** |
| **RF-009** | **Ingreso a urgencias sin EPS** ni afiliación previa garantizado por ley (no bloqueante) | U02, A05 / Fases 2 y 4 | `V018`, `V020` | `EmergencyService.admitirUrgencia`, `AffiliationService.consultarAfiliacion` | `nursing-admission.js`, `affiliation-search.js` | `ROLE_ENFERMERO`, `ROLE_PROFESIONAL` | `AffiliationServiceTest.consultarAfiliacion_noExiste_retornaNoAseguradoSinError`, `EmergencyServiceTest` | **CUBIERTO** |
| **RF-010** | **Inventario y censo de unidades, salas, consultorios y camas hospitalarias** | H01 / Fase 3 | `V019` (`SALA_CONSULTORIO`, `CAMA_HOSPITALARIA`) | `GET /api/v1/hospital/beds`, `GET /api/v1/hospital/census`, `HospitalService.obtenerCenso` | `hospital-census.js` | `ROLE_ENFERMERO`, `ROLE_PROFESIONAL`, `ROLE_ADMINISTRADOR` | `HospitalServiceTest.obtenerCenso_calculaTotalesCorrectos`, `HospitalControllerTest.obtenerCenso_exito` | **CUBIERTO** |
| **RF-011** | **Cama con asignación única y concurrencia segura** (`DISPONIBLE`, `OCUPADA`, `EN_LIMPIEZA`, `EN_MANTENIMIENTO`) | H02 / Fase 3 | `V019` | `POST /api/v1/hospital/beds/{id}/assign`, `HospitalService.asignarCama` | `hospital-bed-management.js` | `ROLE_ENFERMERO`, `ROLE_PROFESIONAL` | `HospitalServiceTest.asignarCama_camaOcupada_lanzaConflicto`, `HospitalControllerTest.asignarCama_exito` | **CUBIERTO** |
| **RF-012** | **Timeline y trazabilidad inmutable de movimientos y traslados intrahospitalarios** | H03 / Fase 3 | `V019` (`MOVIMIENTO_PACIENTE`) | `POST /api/v1/hospital/movements`, `GET /api/v1/hospital/movements/episode/{id}` | `hospital-bed-management.js` | `ROLE_ENFERMERO`, `ROLE_PROFESIONAL` | `HospitalServiceTest.trasladarPaciente_liberaCamaOrigenYOcupaDestino` | **CUBIERTO** |
| **RF-013** | **Seguimiento de procedimientos y traslados quirúrgicos** (quirófano / recuperación) | H04 / Fase 3 | `V019` | `HospitalService.trasladarPaciente` con tipo `QUIROFANO`, `RECUPERACION` | `hospital-bed-management.js` | `ROLE_ENFERMERO`, `ROLE_PROFESIONAL` | `HospitalServiceTest.trasladarAQuirofano_actualizaUbicacionYRegistraMovimiento` | **CUBIERTO** |
| **RF-014** | **Egreso hospitalario médico** que cierra episodio y libera cama automáticamente para desinfección | H05 / Fase 3 | `V019` | `POST /api/v1/hospital/discharges`, `HospitalService.registrarEgreso` | `hospital-discharge.js` | `ROLE_PROFESIONAL` | `HospitalServiceTest.registrarEgreso_cierraEpisodioYPasaCamaALimpieza` | **CUBIERTO** |
| **RF-015** | **Control de ocupación en tiempo real sin exposición de PHI** para administradores | H06 / Fase 3 | `V019` | `GET /api/v1/hospital/census`, métricas anonimizadas | `hospital-census.js`, `operational-dashboard.js` | `ROLE_ADMINISTRADOR` | `HospitalControllerTest.obtenerCenso_admin_sinDatosClinicos`, `AdminClinicalAccessMetaSecurityTest` | **CUBIERTO** |
| **RF-016** | **Importación masiva de afiliados EPS mediante Excel XLSX** segura con preview y commit | A02, A03 / Fase 4 | `V020` (`LOTE_IMPORTACION_EPS`, `DETALLE_IMPORTACION_EPS`) | `POST /api/v1/affiliations/import/preview`, `POST /import/commit` | `affiliation-import.js` | `ROLE_ADMINISTRADOR` | `AffiliationServiceTest.previsualizarLote_procesaFilasValidasYFallidas`, `AffiliationServiceTest.commitLote_modoAtomicAll` | **CUBIERTO** |
| **RF-017** | **Idempotencia y conciliación segura de afiliaciones** sin generar usuarios ni credenciales falsas | A04 / Fase 4 | `V020` (`AFILIACION_PACIENTE`) | `AffiliationService.commitLote` | `affiliation-import.js` | `ROLE_ADMINISTRADOR` | `AffiliationServiceTest.commitLote_noCreaUsuariosFicticios`, `AffiliationControllerTest` | **CUBIERTO** |
| **RF-018** | **Consulta rápida de estado de aseguramiento** con fuente y vigencia sin bloquear urgencias | A01, A05 / Fase 4 | `V020` (`ENTIDAD_EPS`, `AFILIACION_PACIENTE`) | `GET /api/v1/affiliations/patients/{docType}/{docNum}` | `affiliation-search.js` | `ROLE_ADMINISTRADOR`, `ROLE_ENFERMERO`, `ROLE_PROFESIONAL` | `AffiliationServiceTest.consultarAfiliacion_existente_retornaDetallesCompletos` | **CUBIERTO** |
| **RF-019** | **Reprogramación y gestión de citas sin solapes ni doble reserva** en agenda médica | C01, C02 / Fase 4 | `V005`, `V020` (`AUSENCIA_MEDICA`) | `POST /api/v1/affiliations/medical-absences`, `AppointmentService` | `admin-appointments.js`, `professional-agenda.js` | `ROLE_ADMINISTRADOR`, `ROLE_PROFESIONAL` | `AffiliationServiceTest.registrarAusenciaMedica_bloqueaAgendaSinSolape`, `AppointmentConcurrencyTest` | **CUBIERTO** |
| **RF-020** | **Preservación intacta del ciclo clínico de consulta, recetas, farmacia y resumen QR** | C / Q / Fases 1 a 6 | `V001` a `V017` | `ClinicalAttentionService`, `PrescriptionService`, `DispensationService` | `clinical-attention.js`, `pharmacy-dispensation.js` | `ROLE_PROFESIONAL`, `ROLE_FARMACEUTICO` | `ClinicalAttentionServiceTest`, `PrescriptionServiceTest`, `DispensationServiceTest` (100% pasando) | **CUBIERTO** |
| **RF-021** | **Resiliencia y tolerancia a fallos en notificaciones** sin corromper transacciones clínicas | C04 / Fase 4 | `V012` | `AppointmentNotificationService`, `BrevoApiEmailTransport` | Email background worker | Async | `BrevoApiEmailTransportTest.debeReintentarUnaVezAnte5xx`, `AppointmentNotificationServiceTest.enviarConfirmacionReserva_falloTransporte_registraFallido` | **CUBIERTO** |
| **RF-022** | **Código QR de seguimiento hospitalario no invasivo** sin revelar PHI ni diagnósticos reservados | O03 / Fase 5 | `V021` (`SEGUIMIENTO_INTRAHOSPITALARIO_QR`) | `POST /api/v1/operational/tracking-qr/generate`, `GET /tracking-qr/{token}` | `operational-dashboard.js` | `ROLE_ENFERMERO`, `ROLE_PROFESIONAL`, `permitAll` público | `OperationalAnalyticsServiceTest.generarTokenSeguimiento_generaUuidSeguro`, `OperationalAnalyticsServiceTest.escanearQrSeguimiento_noExponeDiagnosticos` | **CUBIERTO** |
| **RF-023** | **Accesibilidad WCAG 2.1 AA y navegación reactiva** en consola de mando y flujos críticos | O04 / Fase 5 | Design Tokens | CSS Tokens (`tokens.css`), interfaces responsivas en Vanilla JS | Vistas responsive | Todos | `frontend/tests/operational-module.test.js`, pruebas de sanitización y tokens CSS | **CUBIERTO** |
| **RF-024** | **Medidas de ciberseguridad:** SAST, CSRF, mitigación IDOR, no PHI en logs, anti Zip-Bomb | Q02 / Fase 6 | N/A | `SecurityConfig`, `CsrfHeaderFilter`, `ZipSecureFile`, `SecurityLogSanitizationTest` | Global | Todos | `CrossPatientIdAccessSecurityTest` (8 tests), `SecurityLogSanitizationTest` (4 tests), `TokenLifecycleSecurityTest` (9 tests) | **CUBIERTO** |
| **RF-025** | **Integración continua y verificación integral reproducible** documentada | Q01, Q04 / Fase 6 | N/A | `.github/workflows/ci.yml`, Maven Surefire 1002 tests | N/A | DevOps | Suite Surefire 1002 tests verdes (0 fallos, 0 errores), 13 tests Node.js verdes | **CUBIERTO** |
| **RF-026** | **Procedimiento de backup restaurable y reversión forward-only** documentado | Q03, Q05 / Fase 6 | `V001` a `V021` | Scripts de migración inmutables forward-only, `DEPLOYMENT.md` | N/A | DevOps | Manual de recuperación ante desastres y contingencia sin DROP/DOWN de migraciones | **CUBIERTO** |
| **RF-027** | **Documentación completa de release y alcance aceptado** con límites normativos claros | Q06 / Fase 6 | N/A | `README.md`, `CHANGELOG.md`, `DECISIONES.md`, `INTEROPERABILIDAD_HOSPITALARIA_COLOMBIA.md` | Documentación | Todos | Matriz de trazabilidad consolidada y aprobada | **CUBIERTO** |

---

## 3. Matriz de Pruebas Negativas y Aislamiento por Rol (RBAC)

| Módulo Asistencial o Administrativo | Paciente | Profesional | Enfermería | Farmacia | Administrador |
|---|---|---|---|---|---|
| **Historia Clínica y Diagnósticos CIE-10** | Solo propia (200) | Pacientes asignados (200) | Solo datos de triaje (403 a evolución médica) | 403 Forbidden | **403 Forbidden estricto** |
| **Prescripción de Recetas Médicas** | 403 Forbidden | Autorizado (200) | 403 Forbidden | 403 Forbidden | **403 Forbidden** |
| **Dispensación Farmacéutica** | 403 Forbidden | 403 Forbidden | 403 Forbidden | Autorizado (200) | **403 Forbidden** |
| **Ingreso y Admisión Urgencias** | 403 Forbidden | 403 Forbidden | Autorizado (200) | 403 Forbidden | Autorizado administrativo (200) |
| **Censo y Movimiento de Camas** | 403 Forbidden | Autorizado asistencial (200) | Autorizado asistencial (200) | 403 Forbidden | Solo métricas anonimizadas (200) |
| **Carga de Archivos Excel EPS** | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden | **Autorizado exclusivo (200)** |
| **Consulta Pública QR de Seguimiento** | Autorizado (200 sin PHI) | Autorizado (200 sin PHI) | Autorizado (200 sin PHI) | Autorizado (200 sin PHI) | Autorizado (200 sin PHI) |
| **Centro de Mando y Alertas** | 403 Forbidden | Autorizado asistencial (200) | Autorizado asistencial (200) | 403 Forbidden | Autorizado gerencial (200) |

---

## 4. Estado de Cobertura y Puerta Final de Calidad

- **Requisitos Funcionales Analizados:** 27 de 27 (100% implementados y verificados).
- **Pruebas Automatizadas Backend:** 1002 pruebas pasando con 0 errores y 0 fallos.
- **Pruebas Automatizadas Frontend:** 13 pruebas unitarias de módulo pasando con 0 fallos.
- **Vulnerabilidades y Secretos:** Cero credenciales expuestas en Git, análisis SAST e IDOR superado.
- **Listo para Candidatura de Release (Release Candidate v2.0.0).**
