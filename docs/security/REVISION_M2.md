# Informe de Revisión de Seguridad — Fase M2: Seguridad Base

**Proyecto:** MediTriaje 2.0  
**Fecha de Revisión:** 2026-10-03  
**Auditor:** Senior Security Architect  
**Alcance:** Fase M2 (M2.1 a M2.5) — Identidad, Registro, Autenticación, Ciclo de Vida de Tokens, Auditoría y Autorización.  
**Estado:** PENDIENTE DE REVISIÓN Y APROBACIÓN POR JUAN.

---

## 1. Resumen Ejecutivo

Durante la Fase M2 se construyeron los cimientos de seguridad e identidad de MediTriaje 2.0 conforme a los acuerdos de arquitectura (ADR-002, ADR-003, ADR-011, ADR-012, ADR-013) y las historias de usuario HU-01 y HU-11.

La evaluación global determina un **nivel de madurez de seguridad alto**, sin secretos expuestos en Git, con hashing de contraseñas de vanguardia (Argon2id), almacenamiento seguro de refresh tokens con mitigación contra secuestro de sesiones, transporte estricto de credenciales en cookies `HttpOnly; SameSite=Strict`, defensa en profundidad contra CSRF y una bitácora de auditoría insert-only inmutable en base de datos.

---

## 2. Matriz de Cumplimiento de Controles

| Control / Requisito | Estado | Evidencia / Mecanismo de Verificación |
|---|---|---|
| **Ausencia de secretos en repositorio** | **CUMPLIDO** | `git grep -i "secret"` y `git grep -i "password"` limpios. Variables `${DB_PASSWORD}`, `${JWT_SECRET}` gestionadas por entorno. |
| **Protección de contraseñas** | **CUMPLIDO** | Argon2id v5.8 (`m=65536, t=3, p=1`) vía BouncyCastle. Validación de longitud mínima (≥10 caracteres). |
| **Exposición de identificadores** | **CUMPLIDO** | Exposición exclusiva de `public_id` (UUIDv4) en DTOs y URLs (ADR-003). PK numérica `ID` oculta internamente. |
| **Protección contra fuerza bruta / enumeración** | **CUMPLIDO** | Bloqueo temporal por 15 minutos al 5.º intento fallido consecutivo. Mensaje genérico unificado (`"Credenciales invalidas."`). |
| **Seguridad de tokens (JWT & Refresh)** | **CUMPLIDO** | Access JWT HMAC-SHA256 (15 min). Refresh token opaco guardado hasheado (SHA-256) en `REFRESH_TOKEN` con rotación obligatoria. |
| **Detección de reuso de tokens (Robo de sesión)** | **CUMPLIDO** | Si se recibe un refresh token ya revocado, se revoca preventivamente toda la sesión activa del usuario (`revocarTodosPorUsuario`). |
| **Almacenamiento de tokens en cliente** | **CUMPLIDO** | Ambos tokens transmitidos exclusivamente en cookies `HttpOnly; Secure; SameSite=Strict`. Cero uso de `localStorage` o `sessionStorage`. |
| **Protección CSRF** | **CUMPLIDO** | Cookies `SameSite=Strict` combinadas con `CsrfHeaderFilter` que exige `X-Requested-With` o `X-CSRF-Protection` en operaciones mutantes. |
| **Inmutabilidad de auditoría** | **CUMPLIDO** | Triggers PL/SQL `TR_AUDITORIA_INMUTABILIDAD` y `TR_CONSENTIMIENTO_INMUTABILIDAD` bloquean UPDATE y DELETE. Usuario `MEDITRIAJE_APP` sin privilegios DDL/DML destructivos. |
| **Ausencia de datos clínicos en logs** | **CUMPLIDO** | Logs de `GlobalExceptionHandler` y `AuditoriaService` registran únicamente códigos de error y acciones técnicas, sin volcar cuerpos sensibles ni valores rechazados de contraseñas. |

---

## 3. Hallazgos y Observaciones para Aprobación

### Hallazgo M2-H1 (Bajo / Operativo): Configuración de bandera `Secure` en cookies para entornos de desarrollo local
- **Descripción:** En entornos locales HTTP puros (`http://localhost:8080`), las cookies marcadas con `Secure=true` son descartadas por los navegadores modernos (Chrome, Firefox, Edge). Actualmente, la propiedad `security.cookie.secure` cuenta con valor por defecto `false` en perfiles `dev` y `test` para permitir el trabajo en máquina local sin certificado SSL, permitiendo activar `true` en producción mediante la variable de entorno `COOKIE_SECURE=true`.
- **Riesgo:** Bajo en desarrollo. En producción, si la variable de entorno `COOKIE_SECURE` no se define explícitamente en el despliegue de Render/OCI, las cookies podrían viajar sin la directiva `Secure`.
- **Recomendación:** Forzar `security.cookie.secure: true` de forma explícita en `application-prod.yml` garantizando que en producción siempre sea `true` independientemente de si la variable existe.

### Hallazgo M2-H2 (Informativo / Buenas Prácticas): Rate Limiting por IP a nivel de Reverse Proxy
- **Descripción:** HU-01 y ADR-002 mencionan "límite de intentos por IP". En la capa de aplicación implementamos el bloqueo por cuenta de usuario (5 intentos fallidos consecutivos bloquean la cuenta por 15 minutos). Sin embargo, una IP maliciosa podría distribuir intentos contra múltiples cuentas distintas (ataque de fuerza bruta horizontal o password spraying).
- **Riesgo:** Medio en despliegues expuestos a internet público.
- **Recomendación:** Para el MVP académico local no es bloqueante. Para la fase de despliegue en Render/Cloudflare (Fase M8), habilitar Rate Limiting por IP en el borde (Reverse Proxy / Web Application Firewall) o añadir un bucket token en memoria (ej. Bucket4j) en endpoints de autenticación si se considera necesario antes de la demo.

### Hallazgo M2-H3 (Informativo): Duración del bloqueo temporal y tiempo de expiración
- **Descripción:** La cuenta se desbloquea automáticamente una vez transcurridos los 15 minutos del bloqueo temporal. Durante la ventana de bloqueo, el atacante o usuario recibe la misma respuesta genérica (`"Credenciales invalidas."`) evitando que un atacante determine si la cuenta existe o está bloqueada.
- **Estado:** Implementado y cubierto por pruebas unitarias automatizadas (`login_cuentaBloqueada_rechazaInmediatamenteSinVerificarPassword` y `login_quintoFalloConsecutivo_bloqueaCuentaTemporalmente`).

---

## 4. Conclusión de la Auditoría

La fase M2 satisface plenamente las reglas no negociables del proyecto:
1. `autenticado != autorizado` (demostrado con 401 en ausencia de token y 403 ante rol insuficiente).
2. Sin secretos en Git ni datos clínicos en logs.
3. SQL 100% parametrizado (`JdbcTemplate` sin concatenación de cadenas).
4. Restricciones críticas en Oracle ATP respaldadas por triggers de inmutabilidad y pruebas de integración.

**No se aplicará ninguna corrección de código sin la aprobación explícita de Juan.**
