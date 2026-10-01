---
name: meditriaje-security-review
description: Úsala al cerrar una fase o al tocar autenticación, autorización, historia clínica, recetas o auditoría en MediTriaje. Checklist de revisión de seguridad con informe de hallazgos.
---

# Revisión de seguridad — MediTriaje

Revisa el código y entrega un informe en `docs/security/REVISION_<fase>.md`. **No corrijas nada sin aprobación del usuario.** Prioriza hallazgos: Crítico / Alto / Medio / Bajo, con archivo, línea y propuesta.

## Checklist
**Autenticación**
- [ ] Contraseñas con Argon2id (o BCrypt 12); nunca en logs.
- [ ] Cookies `HttpOnly; Secure; SameSite=Strict`; nada de tokens en localStorage.
- [ ] Bloqueo tras fallos; error de login genérico; refresh rotativo y revocable.

**Autorización**
- [ ] Cada endpoint tiene regla explícita de rol y de pertenencia al recurso.
- [ ] Paciente A no ve datos de B manipulando IDs (probado).
- [ ] Admin sin acceso a contenido clínico (probado).
- [ ] Profesional solo con relación asistencial (ADR-007).

**Datos**
- [ ] SQL parametrizado en todas partes.
- [ ] Atenciones cerradas inmutables; sin DELETE sobre tablas clínicas.
- [ ] Respuestas sin campos innecesarios ni IDs internos.

**Operación**
- [ ] Sin secretos, wallet ni `.env` en Git (`git grep` de contraseñas, tokens, `jdbc:`).
- [ ] Logs sin datos personales/clínicos; sin stack traces al cliente.
- [ ] CORS explícito; CSRF activo; cabeceras de seguridad.
- [ ] Auditoría de los eventos de HU-11.
- [ ] Dependencias sin vulnerabilidades conocidas.

**Triaje**
- [ ] Todo síntoma de alarma produce emergencia.
- [ ] Ningún texto afirma diagnóstico ni recomienda medicamentos.
