# Informe de Revisión Final de Seguridad y Endurecimiento — Fase M8 (M8.5)

**Proyecto:** MediTriaje 2.0  
**Fecha de Revisión:** 2026-10-03  
**Auditor:** Senior Security Architect & Systems MVP  
**Alcance:** Revisión integral de seguridad de backend, frontend, base de datos y arquitectura de despliegue previa al cierre del MVP.  
**Estado:** APROBADO POR JUAN Y APLICADO (2026-10-03).

---

## 1. Resumen Ejecutivo

En cumplimiento de la tarea **M8.5 (Endurecimiento)** de `docs/PLAN_DE_TRABAJO.md`, se llevó a cabo una auditoría exhaustiva de seguridad sobre la totalidad del código fuente, configuración de despliegue, dependencias y protocolos de comunicación de **MediTriaje 2.0**.

El estado general de la plataforma es **altamente robusto**, diseñado desde el día cero bajo principios de *Security by Design*, arquitectura de mínimo privilegio (ADR-012), separación estricta de dominios clínicos vs. administrativos (ADR-007), hashing resistente con Argon2id, inmutabilidad respaldada por triggers en Oracle ATP y transporte de credenciales exclusivamente en cookies `HttpOnly; SameSite=Strict`.

Este informe consolida la matriz de controles evaluados y detalla los **hallazgos priorizados** junto con sus recomendaciones técnicas y compensatorias para aprobación del Tech Lead (Juan).

---

## 2. Matriz de Evaluación de Controles de Seguridad

| Control / Dimensión | Estado | Evidencia y Mecanismo de Verificación |
|---|---|---|
| **Ausencia de Secretos en Git** | **CONFORME** | Escaneo con `git grep -i "secret"` y `git grep -i "password"` limpio en código y recursos. Variables `${DB_PASSWORD}`, `${FLYWAY_PASSWORD}`, `${JWT_SECRET}` gestionadas 100% por variables de entorno. Archivos wallet en `.gitignore`. |
| **Protección Criptográfica de Contraseñas** | **CONFORME** | Argon2id v5.8 (`m=65536, t=3, p=1`, BouncyCastle). Longitud mínima (≥10 caracteres), contraseñas temporales de alta entropía (14 caracteres alfanuméricos y símbolos) y cambio obligatorio en primer acceso (`DEBE_CAMBIAR_PASSWORD`). |
| **Manejo de Sesiones y Tokens** | **CONFORME** | Access Token JWT firmado HMAC-SHA256 (15 min de vida). Refresh Token opaco rotativo de alta entropía (dos UUIDv4 concatenados) almacenado hasheado con SHA-256 en BD. Detección automática de reuso con revocación masiva preventiva. |
| **Almacenamiento de Tokens en Cliente** | **CONFORME** | CERO almacenamiento de tokens en `localStorage` o `sessionStorage`. Cookies transmitidas con directivas `HttpOnly`, `Path=/`, `SameSite=Strict`. |
| **Defensa contra CSRF** | **CONFORME** | Mitigación dual: cookies `SameSite=Strict` combinadas con filtro `CsrfHeaderFilter` que exige `X-Requested-With` o `X-CSRF-Protection` en todas las operaciones mutantes (`POST`, `PUT`, `PATCH`, `DELETE`). |
| **Protección contra Fuerza Bruta en Autenticación** | **CONFORME** | Bloqueo temporal automático por 15 minutos tras 5 intentos fallidos consecutivos en `AuthService`. Mensaje de error genérico unificado (`"Credenciales invalidas."`) que neutraliza ataques de enumeración de cuentas. |
| **Aislamiento de Datos Clínicos (ADR-007)** | **CONFORME** | Personal administrativo con acceso clínico vedado incondicionalmente (`403 Forbidden` en historia, triaje y recetas). Relación asistencial activa requerida para profesionales. Pacientes aislados entre sí (`autenticado != autorizado`). |
| **Inmutabilidad y Auditoría en Base de Datos** | **CONFORME** | Triggers PL/SQL en Oracle ATP (`TR_ATENCION_INMUTABILIDAD`, `TR_SIGNO_VITAL_INMUTABILIDAD`, `TR_ENMIENDA_INMUTABILIDAD`, `TR_RECETA_INMUTABILIDAD`, `TR_AUDITORIA_INMUTABILIDAD`). Usuario `MEDITRIAJE_APP` con privilegios mínimos (ADR-012). |
| **Privacidad en Logs y Respuestas de Error** | **CONFORME** | Cero datos clínicos, contraseñas o tokens en bitácoras (`logback`). `GlobalExceptionHandler` captura todas las excepciones retornando DTO `ApiError` estandarizado sin trazas de pila (*stack traces*) al cliente. |
| **Sanitización contra Cross-Site Scripting (XSS)** | **CONFORME** | En frontend, toda interpolación dinámica en plantillas HTML utiliza la función `esc(value)` en `frontend/js/ui.js` para escapar `&`, `<`, `>`, `"`, `'`. |
| **Configuración de CORS** | **CONFORME** | `CorsConfig` inyecta orígenes explícitos desde `CORS_ORIGINS`, limitando métodos y cabeceras estrictamente necesarios, con `AllowCredentials: true`. |
| **Dependencias y Librerías** | **CONFORME** | Spring Boot 3.3.4 (línea LTS activa), JJWT 0.12.6, OIDC/BouncyCastle 1.78.1, Oracle JDBC 23.5. Cero librerías obsoletas o vulnerabilidades críticas conocidas en classpath. |

---

## 3. Hallazgos Priorizados y Propuestas de Endurecimiento

A continuación se presentan los hallazgos técnicos ordenados por nivel de criticidad para revisión de Juan antes de proceder con su implementación:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ CLASIFICACIÓN DE HALLAZGOS                                                             │
│ • MEDIO (1): Cabeceras HTTP de seguridad explícitas (CSP, Referrer, Permissions)       │
│ • MEDIO (2): Bandera Secure en cookies para el perfil de producción                    │
│ • BAJO  (1): Nivel de detalle de Actuator Health en producción                         │
│ • INFORMATIVO (1): Rate Limiting distribuido por IP en borde de red                     │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### Hallazgo M8.5-H1 (Criticidad: MEDIA — Configuración de Cabeceras HTTP de Seguridad)

- **Descripción:**  
  Actualmente, `SecurityConfig.java` delega las cabeceras HTTP a los valores por defecto de Spring Security (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 0`). Sin embargo, no se definen de forma explícita las siguientes cabeceras recomendadas por OWASP para aplicaciones web que manejan datos sensibles de salud:
  1. `Content-Security-Policy (CSP)`: Falta restringir las fuentes de scripts, estilos, conexiones y fuentes a `'self'`, impidiendo inyecciones de recursos externos.
  2. `Referrer-Policy`: Falta fijar `strict-origin-when-cross-origin` o `no-referrer` para evitar que URLs con identificadores viajen en cabeceras Referer.
  3. `Permissions-Policy`: Falta deshabilitar APIs de navegador no utilizadas por el frontend (cámara, micrófono, geolocalización: `camera=(), microphone=(), geolocation=()`).

- **Impacto potencial:**  
  Riesgo moderado de ataques de inyección o exfiltración en caso de inclusión de scripts maliciosos de terceros.

- **Recomendación / Propuesta de Mitigación:**  
  Configurar explícitamente en el `SecurityFilterChain` de `SecurityConfig.java`:
  ```java
  http.headers(headers -> headers
      .contentSecurityPolicy(csp -> csp
          .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none';")
      )
      .referrerPolicy(referrer -> referrer
          .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
      )
      .permissionsPolicy(permissions -> permissions
          .policy("camera=(), microphone=(), geolocation=()")
      )
      .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
  );
  ```

---

### Hallazgo M8.5-H2 (Criticidad: MEDIA — Bandera `Secure` en Cookies para Perfil `prod`)

- **Descripción:**  
  En `AuthController.java`, la bandera `secure` de las cookies `access_token` y `refresh_token` se evalúa con `@Value("${security.cookie.secure:false}")`.  
  En `application-dev.yml` y `application.yml` el valor por defecto es `false` (necesario para permitir desarrollo local sobre `http://localhost:8080` sin SSL). Sin embargo, en `application-prod.yml` no está fijado `security.cookie.secure: true` de forma mandatoria; depende de que el operador defina la variable `COOKIE_SECURE=true` en el entorno de Render u OCI.

- **Impacto potencial:**  
  Si un despliegue en producción omite la variable de entorno `COOKIE_SECURE`, las cookies de sesión podrían transmitirse sin el flag `Secure`, permitiendo que viajen sobre conexiones no cifradas en redes intermedias.

- **Recomendación / Propuesta de Mitigación:**  
  Añadir en `backend/src/main/resources/application-prod.yml`:
  ```yaml
  security:
    cookie:
      secure: true  # Forzado en producción independientemente de variables externas
  ```

---

### Hallazgo M8.5-H3 (Criticidad: BAJA — Detalle de Actuator Health en Producción)

- **Descripción:**  
  En `application.yml`, se encuentra configurado:
  ```yaml
  management:
    endpoint:
      health:
        show-details: always
  ```
  Esto hace que `GET /actuator/health` devuelva los detalles internos de la base de datos (versión de motor, validación de conexión, pool Hikari). En `SecurityConfig.java`, `/actuator/**` está permitido sin autenticación (`permitAll()`).

- **Impacto potencial:**  
  En producción, un atacante no autenticado que consulte `/actuator/health` puede obtener información técnica de la base de datos (aunque no credenciales directas).

- **Recomendación / Propuesta de Mitigación:**  
  En `application-prod.yml`, sobreescribir la configuración para que los detalles no se expongan públicamente:
  ```yaml
  management:
    endpoint:
      health:
        show-details: when-authorized
  ```
  O limitar `/actuator/health` público a `{ "status": "UP" }` sin metadata interna del pool.

---

### Hallazgo M8.5-H4 (Criticidad: INFORMATIVA — Límite de Intentos por IP en el Borde)

- **Descripción:**  
  La aplicación cuenta con una defensa excelente contra ataques de fuerza bruta vertical dirigidos a una cuenta específica (5 fallos = 15 minutos de bloqueo en `USUARIO`). No obstante, un atacante distribuido podría realizar ataques de *password spraying* (probar una contraseña común contra cientos de cuentas desde una misma IP).

- **Impacto potencial:**  
  Riesgo bajo en entorno académico/demo; riesgo medio en un entorno corporativo real de producción masiva.

- **Recomendación / Propuesta de Mitigación:**  
  Para el despliegue del MVP en Render / Cloudflare, habilitar el Web Application Firewall (WAF) o regla de Rate Limiting por IP en el Reverse Proxy (ej. máximo 20 peticiones por minuto a `/api/v1/auth/login`). A nivel de código de aplicación, no se requiere introducir librerías adicionales que añadan complejidad o dependencias no deseadas para el MVP.

---

## 4. Estado de Implementación y Verificación

Los hallazgos aprobados por Juan fueron implementados y verificados:

1. **Endurecimiento de Cabeceras HTTP (M8.5-H1) [APLICADO]:**
   - Configurado en `SecurityConfig.java` con bloque explícito para Content-Security-Policy, Referrer-Policy (`strict-origin-when-cross-origin`), Permissions-Policy y FrameOptions (`deny`).
   - Verificado con test automatizado en `PingControllerTest.shouldIncludeSecurityHeaders`.
2. **Forzar `security.cookie.secure: true` en Producción (M8.5-H2) [APLICADO]:**
   - Actualizado en `backend/src/main/resources/application-prod.yml`.
3. **Restringir Detalles de Actuator en Producción (M8.5-H3) [APLICADO]:**
   - Configurado `management.endpoint.health.show-details: when-authorized` en `backend/src/main/resources/application-prod.yml`.
4. **Verificación de Pruebas de Regresión:**
   - Suite completa ejecutada con éxito (`501 tests run, 0 failures, 0 errors, 0 skipped`).

