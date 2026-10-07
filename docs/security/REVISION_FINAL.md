# Informe de Revisión Final de Seguridad — MediTriaje 2.0

> **Proyecto:** MediTriaje 2.0  
> **Fecha de Emisión:** 2026-10-06  
> **Fase / Tarea:** T10 — Revisión de Seguridad Final (Plan Post-Auditoría)  
> **Rama Activa:** `docs/revision-seguridad-final`  
> **Clasificación:** Auditoría Técnica y Diagnóstico de Seguridad  
> **Skill Utilizada:** `meditriaje-security-review` (`.opencode/skills/meditriaje-security-review/SKILL.md`)  
> **Normativa y Estándares de Referencia:** OWASP Top 10 (2021), OWASP ASVS v4.0, RFC 6238 (TOTP), RFC 5869 (HKDF/HMAC), Ley 1581 de 2012 (Protección de Datos Personales Colombia), Resolución 1995 de 1999 y Resolución 839 de 2017 (Historia Clínica Electrónica).  
> **Regla de Ejecución:** **Cero modificaciones unilaterales en código de aplicación.** Todo hallazgo se reporta con archivo, línea exacta, vector de impacto y propuesta de mitigación para consideración y aprobación previa de Juan.

---

## 1. Resumen Ejecutivo y Alcance de la Revisión

### 1.1 Objetivo del Análisis
El presente informe documenta los resultados de la auditoría de seguridad integral realizada sobre el repositorio de **MediTriaje 2.0**, cerrando la tarea **T10** del Plan Post-Auditoría. La revisión evaluó el diseño defensivo, la robustez criptográfica, el control de acceso basado en roles y pertenencia, la inmutabilidad de los datos clínicos y la resistencia frente a vectores de ataque modernos.

El análisis abarcó tanto el núcleo funcional consolidado en el MVP (Fase 1) como la superficie expandida introducida por los módulos avanzados de la Fase 2 (F2.1 a F2.8), los cuales incorporaron factores de autenticación adicionales, interoperabilidad en emergencias, dispensación farmacéutica y herramientas analíticas.

### 1.2 Superficie de Ataque y Módulos Auditados
La inspección cubrió los siguientes frentes críticos:

1. **Autenticación, Sesiones y Criptografía (Fase 1 y F2.1):**
   - Hashing de contraseñas con **Argon2id v5.8** (`m=65536, t=3, p=1`).
   - Autenticación multifactor **TOTP** (RFC 6238) y 8 códigos de respaldo uniuso hasheados en base de datos.
   - Recuperación de contraseñas vía **OTP de 6 dígitos** (15 minutos, máximo 3 intentos, revocación masiva de sesiones concurrentes tras restablecimiento).
   - Ciclo de vida de tokens: JWT firmados con **HMAC-SHA256** (15 min) y Refresh Tokens opacos rotativos hasheados con **SHA-256** en BD, con detección y castigo ante reuso.
   - Resistencia ante enumeración de usuarios y protección contra fuerza bruta con bloqueo temporal (15 min tras 5 fallos).

2. **Transporte, Cookies y Cabeceras HTTP (M8.5 y T8):**
   - Cookies `HttpOnly; Secure; SameSite=Strict` sin almacenamiento de tokens en `localStorage` o `sessionStorage`.
   - Protección dual contra CSRF: restricción `SameSite=Strict` y filtro `CsrfHeaderFilter` (`X-Requested-With: XMLHttpRequest`).
   - Cabeceras HTTP de seguridad: `Content-Security-Policy (CSP)`, `Referrer-Policy`, `Permissions-Policy`, `X-Frame-Options`, `X-Content-Type-Options`.
   - Configuración de CORS con listas blancas de orígenes exactos (sin comodines `*`).

3. **Módulos de Expansión de Superficie (Fase 2):**
   - **Resumen QR de Emergencia (F2.2, ADR-010):** Tokens opacos de 256 bits (32 bytes `SecureRandom`), almacenamiento con hash SHA-256, protección con PIN opcional (Argon2id), límite de 3 lecturas, expiración a 15 minutos, revocación inmediata por el titular y prevención de enumeración pública.
   - **Acceso Excepcional Break-Glass (F2.5, ADR-017):** Acceso reservado a `ROLE_PROFESIONAL` (vedado a pacientes y administradores), justificación de emergencia $\ge 20$ caracteres, expiración automática a 24 horas, modalidad **estricta de solo lectura** (imposibilidad de crear atenciones ordinarias sin cita previa) y auditoría inmutable reforzada.
   - **Farmacia y Dispensación (F2.4, ADR-016):** Autorización exclusiva `ROLE_FARMACEUTICO`, verificación de vigencia de receta médica, control matemático de saldos acumulados por ítem, prevención estricta de sobre-dispensación y aislamiento total frente a otros roles.
   - **Asistente Virtual del Sistema (F2.6, ADR-018):** Motor determinista con corte infalible ante palabras clave de emergencia médica vital (remisión inmediata al 123/urgencias), cero emisión de diagnósticos o prescripción de fármacos, y cero acceso a historiales clínicos o datos individuales de pacientes.
   - **Visor de Auditoría y Reportes Administrativos (F2.7, ADR-019):** Restricción exclusiva a `ROLE_ADMINISTRADOR`, proyección de metadatos técnicos con cero contenido clínico (ADR-007), y análisis de riesgo por inyección de fórmulas CSV (DDE) en la exportación de reportes.
   - **Notificaciones y Correo Transaccional (F2.8, ADR-015, T3):** Transporte seguro sobre HTTPS (`brevo-api`), sanitización rigurosa de trazas y logs (`[REDACTED_API_KEY]`), enmascaramiento de PII (`u***@domain.com`), ausencia absoluta de terminología médica en plantillas y respuesta neutra ante fallos en endpoints de recuperación.

4. **Base de Datos y Separación de Privilegios (ADR-008, ADR-012):**
   - Coherencia entre usuario dueño `MEDITRIAJE_OWNER` (DDL de migraciones) y usuario de aplicación `MEDITRIAJE_APP` (DML en runtime).
   - Privilegios de mínimos privilegios en Oracle ATP: **cero privilegios `DELETE`** en tablas clínicas (`ATENCION`, `SIGNO_VITAL`, `ALERGIA`, `RECETA`, `RECETA_DETALLE`, `CONSENTIMIENTO`).
   - Cero privilegios `UPDATE` y `DELETE` en la tabla `AUDITORIA`.
   - Triggers PL/SQL de inmutabilidad en el motor de base de datos (`ORA-20001` a `ORA-20009`).

5. **Dependencias y Librerías Externas:**
   - Ecosistema Spring Boot 3.3.4 (Java 21), Oracle JDBC 23.5, JJWT 0.12.6, BouncyCastle 1.78.1. Ausencia de vulnerabilidades conocidas en dependencias directas.

---

## 2. Matriz Consolidada de Hallazgos Priorizados

A continuación se detallan los hallazgos identificados durante la revisión. Conforme a las reglas del proyecto, **ninguno ha sido corregido en código**, quedando a disposición de Juan para evaluar su prioridad e incorporación:

| ID | Severidad | Componente / Archivo | Línea | Título del Hallazgo |
|---|---|---|---|---|
| **SEC-001** | **Medio** | `frontend/js/views/admin-reports.js` | 463 | Inyección de Fórmulas CSV (CSV Formula Injection / DDE) en exportación operativa. |
| **SEC-002** | **Medio** | `backend/src/main/java/com/meditriaje/service/EmergencySummaryService.java` | 137–142 | Ausencia de contador y bloqueo ante intentos fallidos de PIN en acceso QR de emergencia. |
| **SEC-003** | **Bajo** | `backend/src/main/java/com/meditriaje/config/SecurityConfig.java` | 99 | Uso de comodín amplio `/actuator/**` en reglas públicas de autorización. |
| **SEC-004** | **Bajo** | `backend/src/main/java/com/meditriaje/config/SecurityConfig.java` / `application-prod.yml` | 67–78 | Falta de configuración explícita de cabecera HSTS detrás de proxy inverso en producción. |
| **SEC-005** | **Bajo** | `backend/src/main/java/com/meditriaje/config/SecurityConfig.java` | 69 | Directiva `'unsafe-inline'` en `style-src` dentro de la Política de Seguridad de Contenido (CSP). |
| **SEC-006** | **Informativo** | `backend/pom.xml` | 54–57, 61–76 | Mantenimiento y vigilancia continua de dependencias de seguridad y criptografía. |

---

### Detalle de Hallazgos y Propuestas de Mitigación

#### [SEC-001] Inyección de Fórmulas CSV (CSV Formula Injection / DDE) en Reportes Operativos
* **Severidad:** **Medio** (CWE-1236: Improper Neutralization of Formula Elements in a CSV File)
* **Archivo:** `frontend/js/views/admin-reports.js`
* **Línea:** 463
* **Código Actual:**
  ```javascript
  const csvContent = '\uFEFF' + rows.map(r => r.map(c => `"${String(c ?? '').replace(/"/g, '""')}"`).join(',')).join('\r\n');
  ```
* **Descripción Técnica:**
  En la exportación de métricas a CSV para administradores, las celdas se envuelven en comillas dobles y se escapan comillas internas. Sin embargo, si un texto dinámico (por ejemplo, el nombre de una sede hospitalaria o el nombre de una especialidad) inicia con caracteres interpretados como fórmulas por Microsoft Excel o LibreOffice (`=`, `+`, `-`, `@`, `\t`, `\r`), el programa ofimático intentará calcular la fórmula o ejecutar comandos mediante Dynamic Data Exchange (DDE) al abrir el archivo.
* **Impacto y Vector de Ataque:**
  Si un usuario administrativo o mediante configuración introduce un nombre como `=cmd|' /C calc'!A0` o `@SUM(...)` para exfiltrar datos, la máquina del analista que abre el CSV podría verse comprometida.
* **Propuesta de Mitigación:**
  Sanitizar el valor de cada celda antes de serializarlo. Si el primer carácter es uno de los prefijos de fórmula (`^[=+\-@\t\r]`), anteponer una comilla simple (`'`) o un espacio:
  ```javascript
  function sanitizeCsvCell(val) {
    const s = String(val ?? '');
    if (/^[=+\-@\t\r]/.test(s)) {
      return `'${s}`;
    }
    return s;
  }
  ```

---

#### [SEC-002] Ausencia de Bloqueo por Fallos Reiterados de PIN en Acceso QR de Emergencia
* **Severidad:** **Medio** (CWE-307: Improper Restriction of Excessive Authentication Attempts)
* **Archivo:** `backend/src/main/java/com/meditriaje/service/EmergencySummaryService.java`
* **Línea:** 137–142
* **Código Actual:**
  ```java
  if (acceso.requierePin()) {
      String pinProporcionado = (request != null && request.pin() != null) ? request.pin().trim() : "";
      if (pinProporcionado.isBlank() || !passwordEncoder.matches(pinProporcionado, acceso.pinHash())) {
          throw new CredencialesInvalidasException("El PIN de seguridad proporcionado es incorrecto.");
      }
  }
  ```
* **Descripción Técnica:**
  El token QR de 256 bits posee entropía criptográfica insuperable (imposible de enumerar o adivinar por la red). Sin embargo, cuando un paciente configura un PIN opcional (típicamente 4 a 6 dígitos) y el código QR físico es obtenido por un tercero, un fallo en la validación del PIN lanza `CredencialesInvalidasException` sin incrementar ningún contador de intentos fallidos en la tabla `ACCESO_TEMPORAL_QR`. Aunque Argon2id introduce un retardo por cálculo computacional, el token no se invalida automáticamente tras 3 o 5 intentos erróneos de PIN dentro de su ventana de vida de 15 minutos.
* **Impacto:**
  Posibilidad de ataque de fuerza bruta sobre el PIN de 4 dígitos si un atacante posee el token de la URL antes de que expire la ventana de 15 minutos.
* **Propuesta de Mitigación:**
  1. Añadir una columna `INTENTOS_PIN_FALLIDOS NUMBER(2) DEFAULT 0` en `ACCESO_TEMPORAL_QR`.
  2. Incrementar el contador atómicamente ante cada fallo de PIN.
  3. Si alcanza 3 intentos erróneos, marcar el registro como revocado o agotado (`ESTADO = AGOTADO`).

---

#### [SEC-003] Uso de Comodín `/actuator/**` en Reglas Públicas de Spring Security
* **Severidad:** **Bajo** (CWE-200: Exposure of Sensitive Information to an Unauthorized Actor)
* **Archivo:** `backend/src/main/java/com/meditriaje/config/SecurityConfig.java`
* **Línea:** 99
* **Código Actual:**
  ```java
  .requestMatchers(
      "/",
      "/ping",
      "/health",
      ...,
      "/actuator/**"
  ).permitAll()
  ```
* **Descripción Técnica:**
  El filtro de seguridad expone con `permitAll()` cualquier ruta bajo `/actuator/**`. Actualmente, la configuración en `application.yml` restringe la exposición web a `health, info` y `application-prod.yml` condiciona los detalles de salud a `when-authorized`. No obstante, la regla de seguridad HTTP confía en la capa de configuración de Actuator en lugar de aplicar defensa en profundidad. Si en el futuro se habilitaran endpoints adicionales de Actuator (como `env`, `metrics`, `beans`), quedarían expuestos públicamente sin autenticación.
* **Impacto:**
  Riesgo de fuga involuntaria de configuración o métricas operativas ante futuros cambios en `application.yml`.
* **Propuesta de Mitigación:**
  Reemplazar `/actuator/**` por las rutas exactas requeridas para sondas de salud:
  ```java
  "/actuator/health",
  "/actuator/info"
  ```

---

#### [SEC-004] Falta de Configuración Explícita de Cabecera HSTS Detrás de Proxy Inverso
* **Severidad:** **Bajo** (CWE-319: Cleartext Transmission of Sensitive Information)
* **Archivo:** `backend/src/main/java/com/meditriaje/config/SecurityConfig.java` / `backend/src/main/resources/application-prod.yml`
* **Líneas:** 67–78 de `SecurityConfig.java`
* **Descripción Técnica:**
  Spring Security incluye la cabecera `Strict-Transport-Security` únicamente cuando detecta que la solicitud entrante es HTTPS (`request.isSecure() == true`). En entornos PaaS como Render, el proxy inverso o terminador SSL de borde recibe la petición HTTPS y la transfiere al contenedor de Spring Boot mediante HTTP interno con cabeceras `X-Forwarded-Proto: https`. Si `server.forward-headers-strategy` no está configurado como `framework` o `native`, Spring Boot podría considerar la petición como HTTP y no emitir la cabecera HSTS al cliente.
* **Impacto:**
  El navegador del usuario podría no fijar la política HSTS para forzar conexiones HTTPS futuras en caso de navegación directa.
* **Propuesta de Mitigación:**
  1. Agregar en `application-prod.yml`:
     ```yaml
     server:
       forward-headers-strategy: framework
     ```
  2. Configurar explícitamente en `SecurityConfig.java`:
     ```java
     headers.httpStrictTransportSecurity(hsts -> hsts
         .maxAgeInSeconds(31536000)
         .includeSubDomains(true)
         .preload(true)
     );
     ```

---

#### [SEC-005] Directiva `'unsafe-inline'` en `style-src` dentro de la Política de Seguridad (CSP)
* **Severidad:** **Bajo** (CWE-79: Cross-site Scripting - Defense in Depth)
* **Archivo:** `backend/src/main/java/com/meditriaje/config/SecurityConfig.java`
* **Línea:** 69
* **Código Actual:**
  ```java
  headers.contentSecurityPolicy(csp -> csp
      .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none';")
  );
  ```
* **Descripción Técnica:**
  La directiva `style-src` incluye `'unsafe-inline'`, permitiendo bloques `<style>` y atributos `style="..."` embebidos en el DOM. Esto fue configurado para dar soporte a micro-componentes de la UI Vanilla (como barras de progreso y anchos dinámicos). Sin embargo, relaja la política estricta de CSP recomendada para entornos de máxima seguridad médica.
* **Impacto:**
  Superficie teórica para ataques de inyección de estilos (CSS Injection) si existiera una brecha de sanitización previa.
* **Propuesta de Mitigación:**
  A mediano plazo, refactorizar los estilos inline hacia clases utilitarias CSS predefinidas en `tokens.css` y `style.css`, o implementar un generador de nonces criptográficos (`'nonce-...'`) para estilos dinámicos.

---

#### [SEC-006] Vigilancia y Escaneo Periódico de Dependencias Criptográficas
* **Severidad:** **Informativo** (CWE-1104: Use of Unmaintained Third Party Components)
* **Archivo:** `backend/pom.xml`
* **Líneas:** 54–57, 61–76
* **Descripción Técnica:**
  El proyecto utiliza versiones modernas y soportadas de librerías criptográficas: `BouncyCastle: 1.78.1`, `JJWT: 0.12.6`, `Spring Boot: 3.3.4` y `OJDBC: 23.5.0.24.07`. No existen vulnerabilidades conocidas ni avisos de seguridad críticos abiertos en estas versiones.
* **Propuesta de Mitigación:**
  Mantener activos los flujos automáticos de escaneo en GitHub Actions (Dependabot o escaneos periódicos con OWASP Dependency-Check) para alertar sobre parches de seguridad en cuanto se liberen nuevas versiones de Spring Framework o BouncyCastle.

---

## 3. Evaluación Exhaustiva por Dominios de Seguridad

### 3.1 Dominio 1: Autenticación, Credenciales y Gestión de Sesiones
* **Hashing de Contraseñas:** Validado en `SecurityConfig.java:54`. Emplea `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`, configurado con memoria de 64 MB (`m=65536`), 3 iteraciones (`t=3`) y paralelismo de 1 hilo (`p=1`). Supera ampliamente los requerimientos de OWASP para almacenamiento de contraseñas.
* **Protección contra Fuerza Bruta:** Verificado en `AuthService.java:365–371`. Tras 5 intentos fallidos consecutivos, la cuenta pasa a estado bloqueado por 15 minutos (`BLOQUEO_MINUTOS = 15`), impidiendo ataques automatizados de diccionario.
* **Respuesta Genérica:** Verificado en `AuthService.java:369`. Las respuestas ante credenciales erróneas unifican el mensaje (*"Credenciales invalidas."*), evitando que un atacante determine si un correo electrónico se encuentra registrado o no.
* **Protección de Tokens en Reposo:**
  - Los Refresh Tokens no se guardan en texto plano: se almacenan hasheados con SHA-256 (`TokenHashUtil.hash`).
  - Detección de reuso de Refresh Token: implementada en `AuthService.java:490–510`. Si un token ya rotado se presenta nuevamente, el sistema asume compromiso de sesión y revoca de inmediato todas las sesiones activas del usuario.

### 3.2 Dominio 2: Multifactor (MFA/TOTP) y Códigos de Respaldo (RFC 6238)
* **Algoritmo TOTP:** Evaluado en `TotpService.java` y `AuthService.java:854`. Cumple con el estándar RFC 6238 (paso de tiempo de 30 segundos, HMAC-SHA1 de 6 dígitos, clave secreta en Base32).
* **Códigos de Respaldo:** Evaluado en `AuthService.java:870–885`. Se generan 8 códigos criptográficos uniuso mediante `SecureRandom` (formato `XXXX-XXXX`). En la base de datos se almacena exclusivamente el hash SHA-256 de cada código (`MFA_BACKUP_CODE.CODIGO_HASH`). Al ser consumido uno, se elimina o invalida de inmediato en BD (`mfaBackupCodeRepository.consumirCodigo`).
* **Desafío MFA en Login:** En `AuthService.java:377`, si el usuario tiene MFA activo, el endpoint de login emite un `mfaChallengeToken` JWT de corta duración y scope exclusivo (`type=mfa_challenge`), rechazado tajantemente por cualquier endpoint clínico o transaccional ordinario hasta que el segundo factor sea validado con éxito.

### 3.3 Dominio 3: Recuperación de Contraseña por OTP
* **Generación de OTP:** Verificado en `AuthService.java:598–618`. Código numérico de 6 dígitos generado con `SecureRandom`.
* **Almacenamiento Seguro:** Se persiste únicamente el hash SHA-256 del código en la tabla `CODIGO_VERIFICACION`.
* **Expiración e Intentos:** Vigencia estricta de 15 minutos (`ChronoUnit.MINUTES`) y límite innegociable de 3 intentos fallidos. Si se superan los 3 intentos, el código se marca como usado/inútil.
* **Neutralización de Enumeración:** Ante solicitudes en `POST /api/v1/auth/forgot-password`, el servicio retorna una respuesta exitosa unificada idéntica (`SolicitarRecuperacionResponse.defaultResponse()`) tanto si el correo existe, como si no existe o si el envío del mensaje falló.
* **Revocación Masiva:** En `AuthService.java:770`, una vez restablecida la contraseña con éxito, se ejecutan de manera transaccional:
  `refreshTokenRepository.revocarTodosPorUsuario(usuario.id());`
  Esto asegura que cualquier sesión activa en otros dispositivos sea terminada inmediatamente.

### 3.4 Dominio 4: Resumen QR de Emergencia
* **Entropía del Token:** Verificado en `EmergencyQrService.java:97–100`. Emplea un búfer de 32 bytes (256 bits) de aleatoriedad criptográfica (`secureRandom.nextBytes`) codificado en Base64 URL-safe sin padding. Imposible de predecir o colisionar.
* **Almacenamiento con Hash:** La base de datos almacena únicamente el hash SHA-256 del token (`ACCESO_TEMPORAL_QR.TOKEN_HASH`), impidiendo que un volcado de BD permita a un atacante reconstruir los enlaces de acceso.
* **Control de Lecturas y Vigencia:** Configurado con un máximo de 3 accesos permitidos (`MAX_ACCESOS_PERMITIDOS = 3`) y ventana de caducidad de 15 minutos (`VIGENCIA_MINUTOS = 15`). El contador se decrementa atómicamente en BD mediante `registrarAcceso()`.
* **PIN de Protección:** Si el paciente define un PIN, este se almacena hasheado con Argon2id (`passwordEncoder.encode(request.pin())`).
* **Revocación Proactiva:** El titular del expediente puede revocar el acceso en cualquier momento mediante `PATCH /api/v1/patients/me/emergency-qr/{publicId}/revoke`.

### 3.5 Dominio 5: Acceso Excepcional Break-Glass
* **Aislamiento Estricto:** Verificado en `BreakGlassService.java` y `ClinicalBreakGlassController.java`. Restringido mediante `@PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")`. Administradores y pacientes reciben `403 Forbidden`.
* **Justificación de Urgencia:** Requiere un motivo explícito de al menos 20 caracteres (`request.motivo().trim().length() < 20` lanza `DatosInvalidosException`).
* **Ventana Temporal:** Vigencia acotada a 24 horas (`VIGENCIA_HORAS = 24`). Pasada la ventana, la consulta de relación asistencial en `AccesoClinicoService` retorna falso y el acceso caduca automáticamente.
* **Principio de Solo Lectura:** El acceso Break-Glass habilita la consulta del expediente (antecedentes, alergias, recetas previas, atenciones anteriores). **No permite la creación de atenciones ordinarias sin cita previa**, ya que `ClinicalAttentionService.iniciarAtencion` exige obligatoriamente un `citaPublicId` asignado y programado.
* **Auditoría Reforzada:** Registra evento inmutable `ACCESO_BREAK_GLASS` con el identificador del profesional, el paciente y la IP de origen.

### 3.6 Dominio 6: Farmacia y Dispensación
* **Segregación Funcional:** Verificado en `PharmacyDispensationController.java:32`. Control de acceso estricto mediante `@PreAuthorize("hasAuthority('ROLE_FARMACEUTICO')")`. Ningún otro rol puede invocar los endpoints de dispensación.
* **Prevención de Sobre-Dispensación:** Verificado en `DispensationService.java:148–160`. El servicio calcula matemáticamente:
  `saldoDisponible = item.cantidadPrescrita() - yaDispensado`
  Si `det.cantidadEntregada() > saldoDisponible` o `saldoDisponible <= 0`, la transacción aborta con `DatosInvalidosException`.
* **Vigencia de Recetas:** El servicio valida la fecha de emisión contra los días de vigencia estipulados (`receta.vigenciaDias()`). Si la receta venció, la dispensación es rechazada.
* **Auditoría:** Cada entrega registra el evento `DISPENSACION_RECETA` vinculado a la receta y la sede farmacéutica.

### 3.7 Dominio 7: Asistente Virtual y Prevención de Fugas (PHI/PII)
* **Detección Infalible de Emergencias:** Verificado en `AssistantService.java:25–47`. Lista exhaustiva de 22 patrones clínicos vitales (dolor de pecho, dificultad respiratoria, pérdida de conciencia, convulsiones, hemorragias, signos de ACV/infarto). Ante cualquier coincidencia, el asistente interrumpe el flujo y remite a la línea 123 o al servicio de urgencias.
* **Cero Emisión Diagnóstica:** El asistente está programado como guía de navegación operativa y administrativa. No diagnostica ni receta.
* **Cero Acceso a Datos Clínicos:** El motor no realiza consultas a tablas de pacientes, historias clínicas ni recetas individuales. No existe vector de fuga de PHI a través del asistente.
* **Aislamiento Técnico:** Opera de manera local y determinista dentro de la aplicación, sin enviar solicitudes a APIs externas ni modelos de lenguaje de terceros que comprometan la privacidad.

### 3.8 Dominio 8: Visor de Auditoría y Exportación de Datos
* **Segregación Administrativa:** Verificado en `AdminAuditController.java:24`. Restringido a `ROLE_ADMINISTRADOR`.
* **Cumplimiento de ADR-007:** La respuesta `RegistroAuditoriaResponse` contiene exclusivamente metadatos técnicos y de seguridad (`id`, `usuarioEmail`, `accion`, `tipoRecurso`, `recursoPublicId`, `resultado`, `ipOrigen`, `fechaHora`). Cero diagnósticos, síntomas o fármacos expuestos.
* **Riesgo CSV Injection:** Identificado y documentado como hallazgo `SEC-001`.

### 3.9 Dominio 9: Subsistema de Notificaciones y Correos Electrónicos
* **Transporte Seguro:** Implementado en `BrevoApiEmailTransport.java` mediante llamadas HTTPS (puerto 443 estándar) a la API oficial de Brevo (`https://api.brevo.com/v3/smtp/email`).
* **Censura de Secretos en Logs:** Verificado en `EmailUtil.java:36–53`. La clave `BREVO_API_KEY` se sustituye sistemáticamente por `[REDACTED_API_KEY]` y los mensajes de error se truncan a 500 caracteres sin etiquetas HTML.
* **Enmascaramiento de PII:** Verificado en `EmailUtil.enmascararEmail`. Toda dirección de correo registrada en bitácoras se anonimiza (ej. `j***@hospital.com`).
* **Plantillas Asépticas:** Las 5 plantillas institucionales fueron auditadas y verificadas: contienen únicamente referencias operativas (fechas, sedes, nombres de doctores o códigos temporales), sin incluir información clínica, signos vitales ni diagnósticos.

### 3.10 Dominio 10: Inmutabilidad en Base de Datos y Mínimos Privilegios
* **Segregación de Cuentas:**
  - `MEDITRIAJE_OWNER`: Propietario de los esquemas, tablas, índices y secuencias; ejecuta las migraciones Flyway.
  - `MEDITRIAJE_APP`: Usuario runtime de la API web.
* **Privilegios DML Mínimos:**
  - `SELECT, INSERT, UPDATE` sobre tablas de control y sesiones.
  - `SELECT` exclusivo sobre catálogos inmutables (`ROL`, `SINTOMA`, `REGLA_TRIAJE`, `DIAGNOSTICO_CIE10`, `MEDICAMENTO`).
  - `SELECT, INSERT` sin `UPDATE` ni `DELETE` sobre registros clínicos inmutables (`TRIAJE`, `TRIAJE_SINTOMA`, `ATENCION_ENMIENDA`, `RECETA`, `RECETA_DETALLE`, `DISPENSACION`, `DISPENSACION_DETALLE`, `ACCESO_BREAK_GLASS`).
  - **Cero privilegios `DELETE`** sobre toda tabla clínica.
  - **Cero privilegios `UPDATE` o `DELETE`** sobre la tabla `AUDITORIA`.
* **Triggers de Bloqueo:** Implementados en Oracle ATP para forzar la inmutabilidad física incluso ante intentos de modificación directos por SQL.

---

## 4. Lista de Verificación de Cumplimiento (Skill `meditriaje-security-review`)

A continuación se resume el estado de cumplimiento frente al checklist oficial de la skill:

| Categoría | Control / Criterio Evaluado | Estado | Evidencia / Referencia Técnica |
|---|---|:---:|---|
| **Autenticación** | Contraseñas con Argon2id v5.8; nunca en logs. | **CUMPLE** | `SecurityConfig.java:54`, `AuthService.java:369`. |
| **Autenticación** | Cookies `HttpOnly; Secure; SameSite=Strict`; sin tokens en localStorage. | **CUMPLE** | `AuthController.java:71–85`, `ui.js`, `api.js`. |
| **Autenticación** | Bloqueo tras fallos; login genérico; refresh rotativo y revocable. | **CUMPLE** | `AuthService.java:365–510`, `RefreshTokenRepository`. |
| **Autorización** | Regla explícita por endpoint (`@PreAuthorize`) y validación de pertenencia. | **CUMPLE** | `@PreAuthorize` en todos los controllers y servicios. |
| **Autorización** | Paciente A no ve datos de Paciente B manipulando IDs. | **CUMPLE** | Validado en `AccesoClinicoService`, probado en tests de aislamiento. |
| **Autorización** | Admin sin acceso a contenido clínico (`403 Forbidden`). | **CUMPLE** | `AccesoClinicoService:152`, probado exhaustivamente en `AdminClinicalIsolationMetaTest`. |
| **Autorización** | Profesional solo accede con relación asistencial activa (ADR-007). | **CUMPLE** | `AccesoClinicoService:207–239` (cita futura, atención previa o break-glass). |
| **Datos** | SQL 100% parametrizado con `JdbcTemplate`; sin concatenaciones. | **CUMPLE** | Repositorios del proyecto revisados al 100%. |
| **Datos** | Atenciones cerradas inmutables; sin `DELETE` sobre tablas clínicas. | **CUMPLE** | Triggers PL/SQL y restricciones de grants en `MEDITRIAJE_APP`. |
| **Datos** | Respuestas con DTOs seguros, sin IDs internos de base de datos. | **CUMPLE** | Uso exclusivo de `publicId` (UUIDv4) en todas las APIs. |
| **Operación** | Sin secretos ni credenciales en Git; variables de entorno en runtime. | **CUMPLE** | Validado con Gitleaks sobre el historial completo. |
| **Operación** | Logs sin datos personales/clínicos; sin stack traces al cliente. | **CUMPLE** | `GlobalExceptionHandler.java`, `EmailUtil.java`. |
| **Operación** | CORS explícito; CSRF activo; cabeceras HTTP de seguridad. | **CUMPLE** | `CsrfHeaderFilter.java`, `SecurityConfig.java` (hallazgos menores SEC-003 a SEC-005). |
| **Operación** | Auditoría inmutable de eventos sensibles (HU-11, ADR-011). | **CUMPLE** | `AuditoriaService.java`, trigger `TR_AUDITORIA_INMUTABILIDAD`. |
| **Operación** | Dependencias sin vulnerabilidades conocidas. | **CUMPLE** | Spring Boot 3.3.4, JJWT 0.12.6, BouncyCastle 1.78.1. |
| **Triaje** | Todo síntoma de alarma produce corte de emergencia al 123. | **CUMPLE** | `MotorTriajeBasadoEnReglas.java`, pruebas de corte infalible. |
| **Triaje** | Cero diagnósticos afirmados y cero prescripción de fármacos. | **CUMPLE** | Avisos legales visibles y categorización estrictamente orientativa. |

---

## 5. Acciones Humanas Pendientes para Juan

Conforme al Plan Post-Auditoría (§4 y §5), la presente revisión no ejecuta cambios destructivos ni altera ramas de producción. Quedan bajo la decisión de Juan las siguientes acciones:

1. **Revisión de Hallazgos Priorizados:**
   - Determinar si se aprueba la aplicación del parche para `SEC-001` (sanitización de CSV contra inyecciones de fórmulas DDE en `admin-reports.js`).
   - Evaluar si se programa para una siguiente iteración el contador de intentos de PIN en `EmergencySummaryService.java` (`SEC-002`) y el ajuste de Actuator en `SecurityConfig.java` (`SEC-003`).
2. **Gestión de Secretos en Oracle ATP y Plataformas Cloud:**
   - Confirmar el cambio de contraseñas de las cuentas `MEDITRIAJE_OWNER` y `MEDITRIAJE_APP` en la consola de Oracle Cloud Infrastructure (OCI).
   - Verificar la configuración de variables de entorno en Render (`BREVO_API_KEY`, `JWT_SECRET`, `DB_PASSWORD`, `CORS_ORIGINS`).
3. **Flujos de Integración y Despliegue:**
   - Revisar el primer run de los flujos de GitHub Actions (`ci.yml` y `e2e.yml`).
   - Realizar la prueba manual de restauración de backup en Oracle ATP según el procedimiento documentado en `docs/DEPLOYMENT.md`.
4. **Cierre de Ramas y Versiones:**
   - Decidir el momento del merge de `integration/post-auditoria` hacia `develop` o `main`, y la creación del tag de versión correspondiente.

---
*Fin del Informe de Revisión Final de Seguridad — MediTriaje 2.0.*
