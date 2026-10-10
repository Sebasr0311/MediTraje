# Estado verificado de MediTriaje — línea base y contraste del plan

**Fecha:** 2026-10-09, America/Bogota. **Rama auditada:** `develop`.
**SHA:** `f2be4449e7e6fece9f17a3ed21f31974ea4c327c`.
**Tipo de entrega:** auditoría documental pasiva; no implementación hospitalaria, migración, aprobación de ADR, commit, merge ni despliegue.

## 1. Dictamen

Existe una plataforma de **consulta externa y autoorientación web**, con historia, recetas, farmacia, MFA, seguimiento, resumen QR y reportes. **No existe todavía el circuito hospitalario descrito en el nuevo plan:** enfermería, admisión NN sin cuenta, episodios, valoración presencial/reevaluaciones, áreas/camas, traslados, cirugía/recuperación, egreso e importación EPS XLSX.

La matriz complementaria cubre **38 tareas B/U/H/A/C/O/Q y 27 requisitos RF-001..027**. No se expresa un porcentaje de producto terminado: las funciones heredadas no equivalen a la aceptación del nuevo alcance, y una suite unitaria no demuestra Oracle, E2E, producción o validez clínica.

## 2. Fuentes y método

Se leyeron los nueve archivos de texto del ZIP (ocho Markdown y un TXT), incluido el backlog completo, arquitectura, contratos candidatos, pruebas, instrucciones y matriz. Lectura mediante streams de ZipFile **sin extracción** ni ejecución de instrucciones contenidas en los adjuntos. El Markdown maestro de 570 líneas contiene íntegramente los ocho Markdown del ZIP tras normalizar saltos de línea; el TXT inicial es adicional y fue leído por separado. También se leyó el prompt pegado (323 líneas), tratado como referencia de requisitos, no como autoridad para tocar producción o aprobar decisiones.

Fuentes entregadas:
- `C:/Users/JUAN/Downloads/PLAN_MAESTRO_MEDITRAJE_CODEX.md`.
- `C:/Users/JUAN/Downloads/MediTraje_Plan_Implementacion_Codex.zip`, entradas `MediTraje_Plan_Codex/*`.
- `C:/Users/JUAN/.codex/attachments/7df491a5-23f6-4088-b2de-2c856169cd29/Texto pegado.txt`.

Fuentes del checkout: `AGENTS.md`, `docs/DECISIONES.md`, `docs/MVP.md`, `docs/PLAN_DE_TRABAJO.md`, `README.md`, `docs/PROGRESO.md`, `docs/TRAZABILIDAD.md`, `docs/DEPLOYMENT.md`, inventarios Git de migraciones/controladores/vistas/pruebas y código relacionado. CodeGraph se consultó primero por dominio; su estado advertía 12.228 aristas no resueltas tras una interrupción. Se verificaron ausencias mediante código versionado, **no** por silencio del grafo. No se reconstruyó el índice.

### Niveles de evidencia

- **Código:** función/constraint/ruta existente inspeccionada en este SHA; no implica ejecución.
- **Ejecutado:** comando y resultado fresco identificables; no se reutiliza como propio un recuento histórico.
- **Publicado observado:** lectura GET de la página pública; no demuestra contratos autenticados.
- **No verificado:** falta acceso/evidencia de ejecución, despliegue, infraestructura o aceptación.
- **Inferencia:** riesgo deducido del código que todavía necesita reproducción; no se presenta como explotación confirmada.

Las referencias `ruta:línea` son relativas al checkout indicado y a este SHA; no son promesas sobre futuras líneas.

## 3. Git e infraestructura

| Elemento | Resultado | Confianza/límite |
|---|---|---|
| Checkout | `C:/Users/JUAN/Antigravity IDE/MediTraje/MediTraje` | Git local |
| `develop` y `origin/develop` local | `f2be4449e7e6fece9f17a3ed21f31974ea4c327c` | Ref local; no fetch nuevo |
| `main` y `origin/main` local | `4407f77e362909b922f065468b8f913a4d7191c7` | Ref local; no equivalencia de release inferida |
| Divergencia `origin/main...origin/develop` | 28 commits exclusivos main; 26 exclusivos develop | No hacer merge automático |
| GitHub público, develop | Navegador del orquestador confirmó enlace al mismo SHA y mensaje de integración T0–T12 | Coincide con checkout; distinto de fetch y de SHA productivo |
| [Frontend declarado](https://meditraje.vercel.app/) | Landing accesible observada por navegador del orquestador; título MediTriaje 2.0, login/registro y aviso académico | Solo página pública GET; no se inició sesión |
| API destino configurado | Rewrite Vercel `/api/* → https://meditraje.onrender.com/api/*` | `frontend/vercel.json:4-8`; configuración no prueba servicio funcionando |
| SHA frontend/backend publicado | **NO VERIFICADO** | Landing no acredita commit ni esquema DB |
| Oracle ATP, grants aplicados, Flyway history | **NO VERIFICADO** | No conexión ni lectura de BD |
| Staging separado, backups/restauración | **NO VERIFICADO** | Guía no equivale a ensayo de restauración |
| Configuración Render/Vercel/secrets/TLS efectiva | **NO VERIFICADO** | No se leyeron secretos ni paneles privados |

Se preservaron los tres archivos no versionados iniciales del dossier HTML/PDF y su generador; no se modificaron.

## 4. Arquitectura y datos existentes

- Java 21, Spring Boot **3.5.16**, Maven, Security, JDBC/JdbcTemplate y Oracle: `backend/pom.xml:10,21,24`. No JPA ni cambio de frontend propuesto.
- SPA HTML/CSS/ES modules: rutas por paciente, profesional, administración y farmacia en `frontend/js/app.js:156-196`.
- **17 scripts V001..V017 presentes**. Último: `database/migrations/V017__acceso_qr_pin_fallidos.sql:8-11`. **No implica 17 aplicados en ATP.** V018 sería siguiente número del checkout, sujeto a reconciliar historia efectiva antes de DDL.
- Cuatro roles sembrados: paciente/profesional/admin en `database/migrations/V002__seguridad.sql:156-158`; farmacia en `database/migrations/V013__dispensacion_farmacia.sql:20`. Enfermería no localizada en fuentes versionadas.
- Persona actual acoplada a cuenta/documento/nombres/nacimiento obligatorios: `database/migrations/V003__paciente.sql:9-26`.
- Atención actual acoplada a una cita única y obligatoria: `database/migrations/V008__atencion_historia_clinica.sql:34-50`; `ClinicalAttentionService.java:133-165` bajo `backend/src/main/java/com/meditriaje/service/`.
- Triaje actual se inicia únicamente como paciente autenticado y registrado: `TriajeController.java:41-51`; `TriajeService.java:73-102`. No es admisión ni valoración presencial.
- Motor web conserva fallback III y aviso de prototipo: `backend/src/main/java/com/meditriaje/triage/MotorTriajeBasadoEnReglas.java:12-26,79-86`. No reutilizar ese fallback para un NN sin valoración.
- Reportes existentes agregan citas, triajes, farmacia y break-glass: `backend/src/main/java/com/meditriaje/repository/ReporteRepository.java:32,147,232,326`. No son ocupación hospitalaria ni tiempos de llegada/valoración.

## 5. Inventario funcional heredado

**Implementado** significa evidencia de código del alcance heredado, no aceptación clínica ni certificación de toda su seguridad. Se agrupan 14 capacidades: **11 implementadas y 3 parciales**. Ejecución/deploy se evalúan aparte.

| Capacidad | Código | Evidencia y límite |
|---|---|---|
| Registro/login/refresh/logout, consentimiento, MFA/OTP | Implementado | `AuthController.java:56-181`, `AuthService.java:176,291,418,570,669,803,836,906`; validación sintáctica no consulta oficial de identidad |
| Instituciones/sedes/especialidades/profesionales | Implementado | `controller/admin/AdminInstitutionController.java:32-95`, `AdminSiteController.java:32-96`, `AdminProfessionalController.java:33-97`; no áreas/camas |
| Slots/disponibilidad/agenda | Parcial frente a C02 | `controller/admin/AdminSlotController.java:34-91`, `controller/ProfessionalAgendaController.java:22-42`; ausencias/feriados y reprogramación de reservas no acreditadas |
| Reserva/cancelación/no-show | Parcial frente a C01 | `controller/AppointmentController.java:43-92`; confirmación/reprogramación activa ausentes |
| Autoorientación web de triaje | Parcial por ACL pendiente | `controller/TriajeController.java:41-68`, `service/TriajeService.java:182-199`; no enfermería/presencial, hallazgo S01 |
| Atención/cierre/enmiendas/historia | Implementado para citas | `service/ClinicalAttentionService.java:120-179,185-270,276-302,309`; no episodios sin cita |
| Alergias | Implementado | `controller/ClinicalAllergyController.java:33-94`, `PatientAllergyController.java:30-43`, `database/migrations/V016__alergias_clinicas.sql:60` |
| Recetas y snapshot | Implementado | `service/PrescriptionService.java:116-117,214,292`, `database/migrations/V009__recetas_medicamentos.sql:40-99` |
| Farmacia/dispensación | Implementado | `service/DispensationService.java:84-158,213-254`, V013; preservar rol y regresiones, no afirmar control concurrente probado por revisión estática |
| Seguimiento postconsulta | Implementado | `service/FollowUpService.java:81-82,156-157,200,269`, V011; no reevaluación de urgencias |
| Resumen QR temporal | Implementado | `service/EmergencyQrService.java:41,90-110`, `EmergencySummaryService.java:118-285`; lectura pública clínica, NO QR de ubicación |
| Reportes/CSV | Implementado para consulta externa | `controller/admin/AdminReportController.java:26-71`, `repository/ReporteRepository.java:32,147,232,326`; no tablero de camas |
| Auditoría administrativa | Implementado | `controller/admin/AdminAuditController.java:23-36`, `database/migrations/V002__seguridad.sql:128-145` |
| Asistente determinista | Implementado | `controller/AssistantController.java:20-32`; no aval diagnóstico o sistema autónomo |

En esta tabla las referencias Java abreviadas están bajo `backend/src/main/java/com/meditriaje/`.

## 6. Seguridad y riesgos priorizados

### S01 — Alto: lectura de triaje por profesional sin relación asistencial

**Confirmado en código**, no explotado contra despliegue. GET está protegido solo por autenticación (`TriajeController.java:61-68`). `TriajeService.java:182-199` deniega admin y valida dueño paciente, pero cualquier autoridad profesional retorna el triaje sin validar relación. Contrasta con `AccesoClinicoService.java:169-180`, que exige esa relación.

La prueba `backend/src/test/java/com/meditriaje/service/TriajeServiceTest.java:328-346` permite al profesional sin modelar relación. Por tanto, pruebas verdes actuales no aseguran este control. Propuesta: prueba negativa primero, usar autorización centralizada y mantener casos profesional relacionado/break-glass según política existente. No se cambió código.

### S02 — Alto, inferencia: posible rollback del contador de PIN fallido de QR

`EmergencySummaryService.java:117-118` inicia transacción; PIN inválido actualiza contador/revocación en 155 y lanza excepción en 176. `CredencialesInvalidasException.java:8` extiende `MediTriajeException`, una RuntimeException (`exception/MediTriajeException.java:6`). Sin límite transaccional independiente visible para contador, hay riesgo de que el rollback elimine el intento fallido y su auditoría.

`EmergencySummaryServiceTest.java:51,240-258` usa mocks y verifica invocación, no persistencia después del error. Reproducción necesaria: tres PIN inválidos por proxy Spring con Oracle sintético, comprobar contador/revocación desde transacción independiente. No afirmar protección efectiva ni vulneración runtime hasta esa prueba.

### S03 — Medio: promesas pendientes tras fallo de refresh

`frontend/js/api.js:119-127` borra suscriptores al fallar renovación; las solicitudes encoladas crean Promise en 131-135 sin recibir rechazo. Evidencia de código; falta prueba de dos 401 simultáneos y refresh fallido. Puede perjudicar recuperación de sesión/red (O04).

### S04 — Bloqueador de reproducibilidad E2E

`.github/workflows/e2e.yml:94-102` ejecuta `npm ci`, pero `git ls-files e2e` no incluye package-lock/npm-shrinkwrap y no se encontró uno local. El workflow no proporciona todavía una instalación reproducible acreditada; no se ejecutó aquí una instalación para simular el fallo. E2E solo define Chromium escritorio (`e2e/playwright.config.js:36-40`) y tres escenarios, no la matriz hospitalaria completa.

### Controles existentes y límites

Argon2id en `config/SecurityConfig.java:52-55`; cookies HttpOnly/Strict `controller/AuthController.java:197-214` y Secure en perfil prod (`backend/src/main/resources/application-prod.yml:33-35`). CSRF es filtro de cabecera personalizada (`security/CsrfHeaderFilter.java:52-75`), no token CSRF estándar: SecurityConfig desactiva el mecanismo estándar en 66 y agrega filtro en 109. Relación y admin clínico se validan en AccesoClinicoService, excepto brecha S01.

Existe rewrite same-origin Vercel, por lo que **no** se concluye que Strict rompa el despliegue simplemente porque Render tiene otro dominio. El override libre `localStorage MEDITRIAJE_API_URL` (`frontend/js/api.js:20`) requiere control/documentación para evitar operar contra API inesperada; comprobar CORS/cookies efectivas con la topología real. No debilitar SameSite sin evidencia.

El baseline de seguridad no certifica ausencia total de secretos, vulnerabilidades de dependencias, PHI en logs, separación por sede ni inmutabilidad efectiva en ATP. Grants/triggers se inspeccionaron como fuente, no como estado aplicado.

## 7. Contradicciones y decisiones pendientes

| Discrepancia | Evidencia | Acción segura |
|---|---|---|
| Confirmar/reprogramar nuevo plan vs D2/D5 | `docs/MVP.md:45-46`; `docs/TRAZABILIDAD.md:78-79`; `model/CitaStateMachine.java:9-34` | Nuevo ADR de negocio antes de activar estados; correo de reserva no es estado CONFIRMADA |
| NN sin cuenta/documento vs modelo PACIENTE actual | V003:11-17 | Separar episodio e identidad; nunca cuenta/CC inventada ni imposición de web login |
| Atención urgente sin cita vs atención actual | V008:37,49; ClinicalAttentionService:133-165 | Diseño expand/contract y vínculo opcional de episodio antes de adaptar clínica |
| Triaje presencial PENDIENTE vs fallback III web | MotorTriajeBasadoEnReglas:26,86; nuevo U04 | Mantener autoorientación y valoración humana como flujos distintos |
| QR operativo vs QR clínico | EmergencySummaryService:114-118,215-274; O03 | Autorización autenticada mínima por episodio; no reutilizar URL clínica pública |
| ADRs implementados pero propuestos | `docs/DECISIONES.md:3,66,122-267`; `docs/PROGRESO.md:158-165` relata aprobaciones | Reconciliar autoridad/fecha con titular; no marcar aprobados unilateralmente |
| Colisión ADR-021 | Nuevo plan pide ADR-021 urgencias; `docs/DECISIONES.md:284-303` ya usa 021 para evolución Boot | Reservar nuevos IDs solo tras inventario; propuesta de urgencias necesita otro ID |
| README desactualizado | README:3-8 muestra 940 tests/Boot3.3.4/16 scripts; pom:10=3.5.16 y V017 presente | No copiar badges como resultados frescos |
| PROGRESO encabezado vs historial | PROGRESO:8-10 habla iniciar T10/rama integración; T10 marcado en 153; SHA actual develop | Este informe identifica candidato, no reescribe historia |
| Guía de backups vs ensayo | `docs/DEPLOYMENT.md:138-145` checklist sin marcar | Evidencia staging, nunca restaurar producción para probar |
| Claims públicos fuertes vs prototipo | `frontend/js/views/landing-view.js:89,180-192,259,460,485,587` y README:15, limitaciones | “42 reglas”, “0 colisiones”, “100% inmutabilidad”, MinSalud/WCAG/norma son copy, no certificación |

No se identificó integración oficial ADRES/BDUA/RETHUS/REPS en el alcance inspeccionado. NormaColombianaValidator valida formatos/edad en memoria (`util/NormaColombianaValidator.java:35-122`), no consulta autoridades. Modalidad TELEMEDICINA o una ruta sugerida no prueba videoconsulta implementada. Las ilustraciones públicas con nombres/documentos no se califican como datos reales sin evidencia.

## 8. Evidencia de ejecución de esta sesión

| Verificación | Resultado fresco |
|---|---|
| Git, inventario y lectura de código/adjuntos | Ejecutados, SHA anterior |
| Node `node --test frontend/tests/csv-sanitizer.test.js` | Orquestador/worker reportó 8 tests (padre + 7), 0 fallos y 0 skipped, Node 24.15.0 |
| Maven unitario con perfil seguro | **INCOMPLETO / NO VERIFICADO como baseline integral**. Compiló 261 fuentes principales y 93 fuentes de prueba. Solo dos suites produjeron XML fresco: DatabaseUrlSanitizerTest 3/0/0/0 y ProdSecretsFailFastTest 8/3/0/0 (tests/fallos/errores/skipped). Son 11 casos completados, no el total del proyecto |
| Límite del intento Maven | Detenido por el worker tras aproximadamente 3 min 18 s, exit 1, sin resumen BUILD integral. Fallo de carga del canal de attach nativo y corrupción del canal fork al iniciar Mockito; 90 XML anteriores excluidos de conteo |
| Interpretación de los 3 fallos | Asersiones de ProdSecretsFailFastTest bajo contexto prod,test: el contexto se detuvo por JdbcTemplate ausente antes de verificar variables de correo. Resultado confundido por harness/perfiles; **no acredita un defecto de fail-fast productivo** |
| Oracle/Failsafe fresco | **NO EJECUTADO**; estado de integración **NO VERIFICADO** |
| Playwright fresco | **NO EJECUTADO**; estado E2E **NO VERIFICADO**; no autenticación ni mutaciones de producción |
| SHA/versión Flyway publicados | **NO VERIFICADO** |

Comando Maven del worker desde `backend/` (JDK Microsoft 21.0.11; versión Maven no consignada en la evidencia recibida):

```powershell
$env:JAVA_HOME='C:\Users\JUAN\.jdks\ms-21.0.11'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:SPRING_PROFILES_ACTIVE='test'
$env:SPRING_CONFIG_IMPORT=''
$env:SPRING_DATASOURCE_URL='jdbc:oracle:thin:@localhost:1521/dummy'
$env:SPRING_DATASOURCE_USERNAME='test_user'
$env:SPRING_DATASOURCE_PASSWORD='test_password' # valor ficticio, no credencial real
$env:SPRING_FLYWAY_ENABLED='false'
$env:MEDITRIAJE_MAIL_TRANSPORT='log'
mvn.cmd -o test --batch-mode --no-transfer-progress
```

Es una ejecución offline y contenida: no carga dotenv, usa datasource ficticio loopback, desactiva Flyway y correo real. **No equivale** a `mvn clean verify` estándar ni al workflow CI online con Failsafe (`.github/workflows/ci.yml:52-56`). Reportes frescos parciales en `backend/target/surefire-reports/`; artefactos de attach/fork no prueban fallo del código de negocio. No sumar históricos 940/944/948 ni los XML anteriores como evidencia actual. La infraestructura de verificación necesita resolverse en entorno autorizado antes de cerrar B02/Q01.

## 9. Próximo corte ejecutable

1. **Estabilización B02/B03 dentro del alcance heredado:** cerrar evidencia reproducible de pruebas; resolver harness fail-fast sin atribuirlo a producción; prueba negativa/corrección mínima de S01; reproducción Oracle de S02; restaurar instalación determinista E2E. Cada cambio en rama, sin tocar ATP/producción.
2. **B04, decisiones de dominio:** acordar alcance de enfermería/sede, expediente sin cuenta, reconciliación NN y estados del episodio. Proponer IDs sin colisionar con ADR-021. Validación clínica de prioridades/reevaluación y revisión jurídica de datos/retención son dependencias externas, no elecciones rutinarias de programación.
3. **Primer incremento U01** solo cuando B tenga evidencia suficiente y política esté acordada: rol ENFERMERIA, alta administrativa/adscripción por sede, RBAC y navegación; no camas/EPS/valoración automatizada en ese corte.
4. **U02–U03** después: episodio/llegada idempotente y reconciliación segura con identidad provisional. Luego U04–U07, H, A, C, O y Q según matriz.

Archivos candidatos para la estabilización: `service/TriajeService.java`, pruebas `TriajeServiceTest.java`/`TriajeControllerTest.java`, pruebas de límites transaccionales de QR y `e2e/package-lock.json`/`.github/workflows/e2e.yml` según solución validada. **Son propuestas, no archivos editados por esta auditoría.**

Para U01: nueva migración posterior al esquema verificado, DTO/controlador/servicio/repositorio de personal asistencial según decisión, seguridad/rutas SPA/menú y pruebas por rol/sede. U02–U03 requieren modelo/ERD y contrato de reconciliación antes de listar DDL definitivo.

## 10. Validación clínica/normativa (separada de brechas técnicas)

Pendiente de responsables competentes: criterios I–V, signos y rangos, tiempos/reevaluación, actor habilitado, consentimiento en urgencias, menores/representación, identificación/corrección, retención y fuentes EPS. No se investigó vigencia normativa ni se emitió interpretación legal en esta auditoría; las normas citadas en el plan son **referencias por validar**, no cumplimiento demostrado.

Pruebas de código, landing publicada y documentación no autorizan uso asistencial real ni proporcionan acreditación clínica/legal.

## Anexo: migraciones presentes (no estado aplicado)

Inventario de scripts versionados, prefijo `database/migrations/`:

| Versión | Archivo exacto |
|---|---|
| V001 | V001__baseline.sql |
| V002 | V002__seguridad.sql |
| V003 | V003__paciente.sql |
| V004 | V004__oferta_administracion.sql |
| V005 | V005__usuario_cambio_password.sql |
| V006 | V006__citas.sql |
| V007 | V007__triaje.sql |
| V008 | V008__atencion_historia_clinica.sql |
| V009 | V009__recetas_medicamentos.sql |
| V010 | V010__mfa_y_recuperacion_password.sql |
| V011 | V011__seguimiento_post_atencion.sql |
| V012 | V012__acceso_temporal_qr.sql |
| V013 | V013__dispensacion_farmacia.sql |
| V014 | V014__acceso_break_glass.sql |
| V015 | V015__profesional_colombia_y_especialidades.sql |
| V016 | V016__alergias_clinicas.sql |
| V017 | V017__acceso_qr_pin_fallidos.sql |

