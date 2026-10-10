# Informe de Escaneo de Secretos y Configuración Segura (T1)

**Fecha:** 2026-10-06  
**Herramienta:** Gitleaks v8.30.1 + Reglas Heurísticas Regex de Proyecto  
**Alcance:** Historial completo de Git (`--all`, 134 commits analizados, ramas activas y etiquetas)  
**Clasificación:** Confidencial / Auditoría de Seguridad Interna  

---

## 1. Resumen Ejecutivo

En cumplimiento de la Tarea T1 del Plan Post-Auditoría de MediTriaje 2.0, se realizó un escaneo exhaustivo y automatizado de la totalidad del repositorio Git para detectar posibles credenciales, claves de API, tokens o archivos sensibles versionados.

- **Fugas detectadas por Gitleaks oficial:** 0 hallazgos activos.
- **Archivos restringidos en el árbol o historial:** 0 (`cwallet.sso`, `ewallet.p12`, `tnsnames.ora`, `sqlnet.ora`, `*.jks`, `.env`, `target/`, `*.log` no están ni estuvieron versionados).
- **Contraseñas de ejemplo:** Se sanitizaron las cadenas de documentación en `docs/database/DATABASE.md` sustituyéndolas por `<password_owner>` y `<password_app>`.
- **Fail-fast en perfil prod:** Verificado con la prueba automatizada `ProdSecretsFailFastTest`. El contexto de producción rechaza el arranque inmediato ante la ausencia de cualquiera de las variables obligatorias: `JWT_SECRET`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `CORS_ORIGINS`.

---

## 2. Detalle de Reglas y Patrones Evaluados

| Regla / Patrón | Descripción | Coincidencias en Historial | Estado |
|---|---|---|---|
| `brevo-api-key` | Prefijo de clave API Brevo (`xkeysib-`) | 0 | Limpio |
| `brevo-smtp-key` | Prefijo de clave SMTP Brevo (`xsmtpsib-`) | 0 | Limpio |
| `jwt-token-leak` | Patrón de token JWT firmado (`eyJ...`) | 0 | Limpio |
| `private-key` | Cabeceras de clave privada PEM / PKCS#8 | 0 | Limpio |
| `jdbc-credentials` | Cadenas de conexión JDBC con usuario/contraseña embebida en plano | 0 | Limpio |
| `prohibited-files` | Wallets de Oracle, certificados JKS, archivos `.env`, directorios `target/` o logs | 0 | Limpio |
| `sample-passwords` | Contraseñas de ejemplo en documentación (`docs/database/DATABASE.md`) | 2 referencias corregidas | Subsanado |

*Nota de privacidad: Conforme a la regla de no divulgación, este informe no imprime ni almacena valores confidenciales ni tokens bajo ninguna circunstancia.*

---

## 3. Registro de Hallazgos y Correcciones Aplicadas

| Commit | Archivo | Línea | Tipo de Regla | Severidad | Acción Aplicada |
|---|---|---|---|---|---|
| Working Tree (T1) | `docs/database/DATABASE.md` | 36 | `sample-password-docs` | Baja | Reemplazado por `<password_owner>` |
| Working Tree (T1) | `docs/database/DATABASE.md` | 46 | `sample-password-docs` | Baja | Reemplazado por `<password_app>` |

---

## 4. Endurecimiento de Configuración (Fail-Fast en Producción)

1. **Sin valores por defecto en producción:**  
   En `application-prod.yml`, las variables de entorno de infraestructura crítica no poseen valores por defecto. Si el contenedor o runtime arranca sin ellas, Spring Boot aborta la inicialización de inmediato (`IllegalArgumentException: Could not resolve placeholder`):
   - `DB_URL`
   - `DB_USER`
   - `DB_PASSWORD`
   - `CORS_ORIGINS`
   - `JWT_SECRET`
2. **Aislamiento de comodines de desarrollo:**  
   Los valores simulados de conveniencia (`dev-secret-key-...`, URLs locales de CORS) residen exclusivamente en `application-dev.yml` y `application-test.yml`.
3. **Endurecimiento de `.gitignore`:**  
   Se confirmaron y reforzaron las exclusiones para wallets de Oracle (`*wallet*`, `*.sso`, `*.p12`), llaves privadas (`*.jks`, `*.pem`, `*.key`), archivos de entorno (`.env`, `.env.*`), ejecutables y compilados (`target/`, `*.jar`), y volcados de logs (`*.log`, `logs/`).

---

## 5. Propuesta de Integración de Gitleaks en CI (para T9)

Para garantizar que ningún secreto sea introducido en futuras contribuciones, se propone integrar Gitleaks en GitHub Actions (`.github/workflows/ci.yml`) mediante el action oficial:

```yaml
name: CI

on: [push, pull_request]

jobs:
  security-scan:
    name: Gitleaks Secret Detection
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Run Gitleaks
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GITLEAKS_ARGS: "detect --verbose --redact"
```

El pipeline fallará automáticamente si se detecta cualquier intento de commit con credenciales o claves no redactadas.

---

## 6. Acción Humana Requerida (Juan)

> **ATENCIÓN:** Conforme al principio de defensa en profundidad y regla D8 (no reescribir historial de Git):  
> Si alguna contraseña del ATP de Oracle (usuarios `MEDITRIAJE_OWNER` o `MEDITRIAJE_APP`) o credencial de proveedor externo (Brevo) llegó a compartirse fuera del entorno local seguro, **debe ser rotada de inmediato en la consola de Oracle Cloud / Brevo**.
