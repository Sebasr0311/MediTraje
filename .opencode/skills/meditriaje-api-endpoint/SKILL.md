---
name: meditriaje-api-endpoint
description: Úsala al crear o modificar un endpoint REST de MediTriaje. Define el recorrido Controller → Service → Repository, DTOs, errores, autorización, auditoría, pruebas y archivo .http.
---

# Endpoint REST — receta de MediTriaje

Lee la historia (HU) en `docs/MVP.md` y sus criterios de aceptación antes de empezar.

## Pasos
1. **DTOs** de entrada y salida con validaciones (`jakarta.validation`). Nunca expongas entidades ni IDs internos; usa `publicId`.
2. **Controller**: solo HTTP (ruta `/api/v1/...`, status correcto, `@PreAuthorize` por rol). Sin lógica de negocio ni SQL.
3. **Service**: caso de uso y reglas. `@Transactional` si hay más de una escritura (todo o nada).
4. **Autorización por recurso**: si el endpoint toca datos de una persona, verifica pertenencia o relación asistencial (`AccesoClinicoService`). Autenticado ≠ autorizado.
5. **Repository**: SQL parametrizado, sin concatenar strings.
6. **Errores**: lanza excepciones de dominio (`RecursoNoEncontrado`, `AccesoNoAutorizado`, `CitaNoDisponible`...). El `@RestControllerAdvice` las convierte en `ApiError`. Nunca stack traces.
7. **Auditoría**: si es una operación sensible (ver HU-11), llama a `AuditoriaService` sin contenido clínico.
8. **Logs**: sin datos personales ni clínicos.
9. **Paginación** en listados (`page`, `size` con máximo).

## Pruebas mínimas
- Camino feliz.
- Sin autenticar → 401. Rol equivocado → 403.
- Recurso de otra persona → 403/404.
- Entrada inválida → 400 con `ApiError`.
- Regla de negocio violada (estado inválido, no disponible, etc.).

## Entregables
- Peticiones de ejemplo en `docs/api/M?.http`.
- `PROGRESO.md` actualizado.
