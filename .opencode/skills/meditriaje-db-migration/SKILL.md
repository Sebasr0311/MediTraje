---
name: meditriaje-db-migration
description: Úsala siempre que crees o modifiques migraciones Flyway para Oracle en MediTriaje (tablas, índices, triggers, catálogos). Define convenciones de nombres, IDs, estados, fechas, auditoría e inmutabilidad clínica.
---

# Migraciones Oracle — MediTriaje

Antes de empezar, lee `docs/database/MODELO_RELACIONAL.md` y `docs/DECISIONES.md` (ADR-003, 004, 005, 006, 008). La migración debe reflejar el modelo aprobado; si no coincide, detente y avisa.

## Reglas
1. Archivo: `database/migrations/V###__verbo_objeto.sql` (número consecutivo). **Nunca edites una migración ya aplicada**: crea una nueva.
2. Una migración = una responsabilidad (p. ej., "crear_citas"), no un volcado.
3. Nombres en MAYÚSCULAS_CON_GUION_BAJO, singulares, ≤ 30 caracteres.
4. PK técnica: `ID NUMBER GENERATED ALWAYS AS IDENTITY`. Columna `PUBLIC_ID VARCHAR2(36) NOT NULL` con `UNIQUE`; es lo único expuesto por la API.
5. Fechas con `TIMESTAMP WITH TIME ZONE`; nacimiento con `DATE`. Toda tabla lleva `CREATED_AT` (y `UPDATED_AT` si es mutable).
6. Estados con `CHECK` y nombre explícito. Constraints nombradas: `PK_`, `FK_<hija>_<padre>`, `UQ_`, `CK_`, `IX_`.
7. Indexa las FK y solo los índices justificados en el modelo.
8. Tablas clínicas: sin `ON DELETE CASCADE`, sin borrado físico; atenciones cerradas protegidas por trigger (ADR-008).
9. Añade `COMMENT ON TABLE` y `COMMENT ON COLUMN` en lo no obvio.
10. Catálogos de referencia (roles, estados) van en migraciones; **datos de prueba van en `database/seeds/`** y solo para dev, siempre ficticios.

## Al terminar
- Prueba que la migración aplica en una BD limpia (Testcontainers).
- Actualiza `docs/database/MODELO_RELACIONAL.md` si cambió algo.
- Si una constraint protege una regla de negocio, añade una prueba que demuestre que la BD rechaza el dato inválido.
