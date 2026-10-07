# SECURITY.md — Política y Modelo Integral de Seguridad

> **Proyecto:** MediTriaje 2.0  
> **Fecha de Emisión:** 2026-10-03  
> **Clasificación:** Documento Técnico de Seguridad y Cumplimiento Normativo (Contexto Colombia, Ley 1581 de 2012)

---

## 1. Principios Rectores de Seguridad

MediTriaje 2.0 procesa información médica y datos sensibles de pacientes. Por ello, la arquitectura fue concebida desde el día cero bajo cuatro postulados cardinales:

1. **Security by Design y Defense in Depth:** La seguridad no es un parche posterior ni depende de ocultar botones en la interfaz de usuario. Cada capa (red, HTTP, filtros, servicios, SQL y base de datos) valida de manera independiente la autenticidad, autorización e integridad.
2. **Principio de Mínimo Privilegio (Least Privilege, ADR-012):** La cuenta de base de datos de runtime (`MEDITRIAJE_APP`) carece de privilegios DDL y tiene expresamente denegado el permiso `DELETE` en tablas clínicas.
3. **Aislamiento Estricto de Roles (`autenticado != autorizado`, ADR-007):** El rol `ROLE_ADMINISTRADOR` gestiona la infraestructura y la oferta médica, pero tiene **vedado de forma incondicional el acceso a historias clínicas, triajes y recetas** (`403 Forbidden`). Un paciente jamás puede consultar datos de otro paciente, incluso conociendo su identificador.
4. **Inmutabilidad Clínica Respaldada por el Motor de Base de Datos (ADR-008):** Una vez cerrada una atención médica o emitida una receta, su contenido no se puede alterar ni eliminar (ni por API ni por SQL directo). Las correcciones se realizan exclusivamente mediante enmiendas sucesivas anexadas (*append-only*).

---

## 2. Autenticación y Gestión Criptográfica

| Dimensión | Mecanismo Implementado | Justificación y Norma |
|---|---|---|
| **Hashing de Contraseñas** | **Argon2id v5.8** (`m=65536, t=3, p=1`) vía BouncyCastle / Spring Security. | Algoritmo resistente a ataques masivos por GPU/ASIC. Recomendado formalmente por OWASP. Longitud mínima de 10 caracteres. |
| **Contraseñas Temporales** | Criptográficamente seguras con `SecureRandom` (14 caracteres alfanuméricos con símbolos). | Exigencia obligatoria de cambio de clave en el primer acceso (`DEBE_CAMBIAR_PASSWORD = 1`). |
| **Protección contra Fuerza Bruta** | Bloqueo temporal automático por 15 minutos tras 5 fallos consecutivos. | Respuesta unificada genérica (*"Credenciales invalidas."*) que neutraliza la enumeración de cuentas. |
| **Access Token** | JWT firmado con **HMAC-SHA256**, vida útil de 15 minutos. Claims reducidos (`sub=publicId`, `roles`). | Minimiza la ventana de exposición en caso de intercepción accidental. |
| **Refresh Token** | Token opaco de alta entropía (doble UUIDv4) almacenado **hasheado con SHA-256** en BD. Vida útil de 7 días. | Rotación obligatoria en cada uso. **Detección de reuso:** si se presenta un token ya rotado, se revocan preventivamente todas las sesiones activas del usuario. |

---

## 3. Transporte y Manejo de Sesiones en Cliente

### Cero Exposición en `localStorage` o `sessionStorage` (ADR-002)
Los navegadores almacenan los tokens de sesión en **cookies de cabecera** configuradas por el servidor:
```http
Set-Cookie: access_token=<jwt>; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=900
Set-Cookie: refresh_token=<token>; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict; Max-Age=604800
```
- **`HttpOnly`:** El código JavaScript del cliente no tiene acceso a leer las cookies, eliminando el riesgo de robo de tokens ante cualquier potencial vulnerabilidad XSS.
- **`Secure`:** Transmisión restringida a túneles cifrados TLS/HTTPS (forzada en producción en `application-prod.yml`).
- **`SameSite=Strict`:** El navegador nunca adjunta estas cookies en solicitudes originadas desde sitios web externos.

---

## 4. Defensas contra CSRF y XSS

### Defensa Dual contra CSRF (Cross-Site Request Forgery)
1. **Barrera de Navegador:** `SameSite=Strict` impide el envío de cookies en contextos cruzados.
2. **Barrera de Servidor (`CsrfHeaderFilter`):** Todas las operaciones mutantes (`POST`, `PUT`, `PATCH`, `DELETE`) exigen la cabecera:
   ```http
   X-Requested-With: XMLHttpRequest
   ```
   Las peticiones entre sitios (*cross-site*) disparadas por formularios HTML tradicionales no pueden inyectar esta cabecera sin pasar por una pre-petición CORS (preflight), la cual es bloqueada por el servidor si el origen no está explícitamente en la lista blanca `CORS_ORIGINS`.

### Sanitización contra XSS (Cross-Site Scripting)
En el frontend Vanilla, toda interpolación de texto dinámico proveniente del usuario o de la base de datos se escapa mediante la función centralizada `esc(str)` en `frontend/js/ui.js`:
```javascript
export function esc(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
```

---

## 5. Cabeceras HTTP de Seguridad (Fase M8.5)

Configuradas explícitamente en `SecurityConfig.java`:

```http
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none';
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
```

* **Anti-Clickjacking:** `frame-ancestors 'none'` y `X-Frame-Options: DENY` impiden que la aplicación sea embebida dentro de un `<iframe>` malicioso.
* **Restricción de Recursos:** CSP restringe la carga de scripts y conexiones exclusivamente al propio origen (`'self'`). Sin dependencias de CDNs de terceros.
* **Privacidad:** `Permissions-Policy` deshabilita sensores de hardware y geolocalización no requeridos.

---

## 6. Aislamiento Clínico y Relación Asistencial (ADR-007)

El acceso a la historia clínica se rige por la matriz de autorización verificada en `AccesoClinicoService`:

```text
Solicitud de Acceso a Historia Clínica / Receta
  ├── ¿Rol ADMINISTRADOR? ─────────────> 403 Forbidden (Acceso denegado a personal administrativo)
  ├── ¿Rol PACIENTE?
  │     ├── ¿Es su propio expediente? ──> 200 OK (Consulta permitida)
  │     └── ¿Es de otro paciente? ──────> 403 Forbidden (Aislamiento absoluto)
  └── ¿Rol PROFESIONAL?
        ├── ¿Es el autor del registro? ─> 200 OK
        └── ¿Tiene cita activa/atendida
            con el paciente? ────────────> 200 OK (Relación asistencial vigente)
            └── Si NO tiene relación ───> 403 Forbidden (Acceso no autorizado)
```

---

## 7. Inmutabilidad en Base de Datos (ADR-008)

El motor de base de datos Oracle ATP rechaza físicamente cualquier intento de alteración sobre registros clínicos consolidados:

* **Triggers de Bloqueo:** `TR_ATENCION_INMUTABILIDAD`, `TR_SIGNO_VITAL_INMUTABILIDAD`, `TR_ENMIENDA_INMUTABILIDAD`, `TR_RECETA_INMUTABILIDAD` y `TR_RECETA_DETALLE_INMUTABILIDAD`.
* **Excepciones PL/SQL:** Lanzan errores en el rango `ORA-20001` a `ORA-20009` cancelando la transacción.
* **Enmiendas Médicas:** Si un médico requiere corregir una evolución o aclarar un dato, debe emitir una enmienda (`POST /api/v1/attentions/{id}/amendments`), la cual queda fechada, firmada y anexada cronológicamente sin alterar el registro original.

---

## 8. Trazabilidad y Auditoría Inmutable (ADR-011)

Cada evento sensible se persiste en la tabla `AUDITORIA` mediante `AuditoriaService`:
* **Eventos Auditados:** `LOGIN_EXITOSO`, `LOGIN_FALLIDO`, `LOGOUT`, `REGISTRO_PACIENTE`, `RESERVA_CITA`, `CANCELACION_CITA`, `TRIAJE_REALIZADO`, `TRIAJE_EMERGENCIA`, `CREACION_ATENCION`, `CIERRE_ATENCION`, `ENMIENDA_ATENCION`, `CONSULTA_HISTORIA`, `CREACION_RECETA`, `CAMBIO_ADMINISTRATIVO`.
* **Privacidad Estricta en Auditoría:** La auditoría registra únicamente metadatos (`usuario_id`, `accion`, `tipo_recurso`, `recurso_public_id`, `resultado`, `ip_origen`, `created_at`). **Nunca se almacenan síntomas, diagnósticos, nombres de medicamentos ni contraseñas en los registros de auditoría ni en los logs de aplicación**.
* **Inmutabilidad del Log:** La tabla `AUDITORIA` tiene trigger `TR_AUDITORIA_INMUTABILIDAD` que bloquea cualquier sentencia `UPDATE` o `DELETE`.

---

## 9. Matriz de Mitigación OWASP Top 10 (2021)

| Riesgo OWASP | Mitigación Técnica en MediTriaje 2.0 |
|---|---|
| **A01: Broken Access Control** | `@PreAuthorize` por método, `AccesoClinicoService`, aislamiento paciente-paciente, bloqueo a administradores, IDs opacos UUIDv4. |
| **A02: Cryptographic Failures** | Argon2id v5.8, JWT firmado con HMAC-SHA256, Refresh tokens hasheados con SHA-256, cookies `Secure; HttpOnly`. |
| **A03: Injection** | SQL 100% parametrizado con `JdbcTemplate` (cero concatenación de cadenas). HTML escapado con `esc()`. |
| **A04: Insecure Design** | Separación DTO/Entidad, control de concurrencia atómico en slots, triaje determinista con corte de emergencia. |
| **A05: Security Misconfiguration** | Cabeceras CSP, Referrer y Permissions explícitas. Actuator restringido en prod. Cero stack traces en respuestas de error. |
| **A06: Vulnerable Components** | Spring Boot 3.3.4 (LTS), OJDBC 23.5, JJWT 0.12.6, BouncyCastle 1.78.1. Sin dependencias en desuso. |
| **A07: Identification & Auth Failures** | Bloqueo temporal por 5 intentos fallidos, rotación y detección de reuso de tokens, contraseñas temporales forzadas a cambio. |
| **A08: Software & Data Integrity** | Inmutabilidad de registros clínicos en BD (triggers PL/SQL), migraciones Flyway inmutables validadas por checksum. |
| **A09: Security Logging & Monitoring** | Tabla de auditoría inmutable, sin datos sensibles en logs, correlación con `traceId` en cada `ApiError`. |
| **A10: Server-Side Request Forgery (SSRF)** | La aplicación no realiza peticiones HTTP salientes basadas en URLs suministradas por el usuario. |

---

## 10. Seguridad en Notificaciones y Correos Electrónicos (ADR-015, T3)

MediTriaje 2.0 implementa una política rigurosa de protección de datos personales (PII) y de salud (PHI) en su subsistema de correos electrónicos transaccionales:

1. **Ausencia Absoluta de Contenido Clínico en Correos:** Ninguna de las 5 plantillas HTML institucionales (`bienvenida-credenciales.html`, `cancelacion-cita.html`, `confirmacion-cita.html`, `recuperacion-password.html`, `resumen-atencion-seguimiento.html`) contiene diagnósticos, códigos CIE-10, prescripción de medicamentos, signos vitales ni notas de evolución médica. Toda información asistencial detallada reside exclusivamente tras las barreras de autenticación y autorización del portal web.
2. **Transporte Seguro HTTPS y Mitigación de Bloqueo SMTP:** En producción, el transporte oficial opera mediante la **API HTTP de Brevo** sobre TLS (puerto 443 estándar), eliminando la dependencia de puertos SMTP salientes (25, 465, 587) bloqueados por defecto en entornos PaaS como Render.
3. **Enmascaramiento de PII y Censura de Secretos en Logs:** Toda traza de log que registre un intento de envío enmascara la dirección de correo (`p***@ejemplo.com`). Los mensajes de error retornados por la API de Brevo son procesados por `EmailUtil.sanitizarMensajeError`, sustituyendo cualquier ocurrencia de la clave `BREVO_API_KEY` por `[REDACTED_API_KEY]`, eliminando fragmentos HTML y truncando a un máximo de 500 caracteres.
4. **Protección contra Enumeración de Cuentas en Recuperación de Clave:** Ante solicitudes en `POST /api/v1/auth/forgot-password`, el endpoint retorna un HTTP 200 genérico e idéntico tanto si el usuario existe, como si no existe o si el despacho de correo falló. Cualquier fallo operativo es auditado internamente bajo el evento `EMAIL_FALLIDO` (con resultado `FALLO`) sin comprometer la respuesta externa hacia el cliente.
5. **Visibilidad Operativa sin Silenciamiento:** Los fallos de transporte (códigos 400 por remitente no verificado, 401 por credenciales inválidas, 429 por cuota o 5xx por problemas de red) se capturan y persisten explícitamente en `RECORDATORIO_CITA` con `ESTADO_ENVIO = 'FALLIDO'` y detalle del error, garantizando que el personal operativo detecte de inmediato cualquier degradación del canal de mensajería.

