# Semillas de Prueba Ficticias (database/seeds/)

> [!CAUTION]
> **PROHIBIDO SU USO EN PRODUCCIÓN** (ADR-004, ADR-012).
> Los scripts contenidos en esta carpeta corresponden a datos **100% ficticios** diseñados única y exclusivamente para entornos locales de desarrollo (`dev`), validación funcional y pruebas automatizadas. Nunca deben ejecutarse contra esquemas de producción de Oracle Cloud Infrastructure (OCI).

---

## Contenido de las Semillas (Fase M3)

El script [`dev_seeds_m3.sql`](file:///C:/Users/JUAN/Antigravity%20IDE/MediTraje/MediTraje/database/seeds/dev_seeds_m3.sql) aprovisiona la estructura asistencial base y la oferta inicial para desarrollo:

### 1. Usuario Administrador de Plataforma
- **Email:** `admin@meditriaje.com`
- **Contraseña:** `Admin12345*`
- **Rol:** `ROLE_ADMINISTRADOR`
- **Obligación de cambio de clave:** No (`DEBE_CAMBIAR_PASSWORD = 0`)

### 2. Estructura Asistencial
- **Institución:**
  - `IPS MediSalud Valledupar S.A.S.` (NIT: `900123456-1`)
- **Sedes:**
  - `Sede Centro Valledupar` (`Calle 16 # 12-45`, Valledupar)
  - `Sede Norte Valledupar` (`Carrera 19D # 5-30`, Valledupar)
- **Especialidades:**
  - `Medicina General` (duración turno: 20 min)
  - `Pediatría` (duración turno: 30 min)
  - `Medicina Interna` (duración turno: 30 min)
  - `Odontología` (duración turno: 30 min)

### 3. Profesionales Asistenciales
Todos creados con rol `ROLE_PROFESIONAL` y contraseña temporal inicial:
- **Contraseña temporal común:** `Temporal12345*`
- **Obligación de cambio en primer login:** Sí (`DEBE_CAMBIAR_PASSWORD = 1`)

| Profesional | Registro Médico | Especialidad | Sede Principal | Email |
|---|---|---|---|---|
| Dr. Carlos Alberto Mendoza Vega | `RM-102938` | Medicina General | Sede Centro | `carlos.mendoza@meditriaje.com` |
| Dra. Laura Sofia Gómez Rueda | `RM-203948` | Pediatría | Sede Centro | `laura.gomez@meditriaje.com` |
| Dr. Andrés Felipe Castro Ortiz | `RM-304958` | Medicina Interna | Sede Norte | `andres.castro@meditriaje.com` |
| Dra. Marcela Patricia Morales Díaz | `RM-405968` | Odontología | Sede Norte | `marcela.morales@meditriaje.com` |

### 4. Disponibilidad Horaria (Slots)
- Generados para los próximos 14 días hábiles (lunes a viernes).
- Franjas matutinas y vespertinas en zona horaria obligatoria `America/Bogota` (ADR-005).
- Modalidades: `PRESENCIAL` y `TELEMEDICINA`.
- Estado inicial: `LIBRE`.

---

## Cómo ejecutar las semillas en Entorno Local (DEV)

### Opción A: Vía SQLcl o SQL*Plus
```bash
# Conectado con usuario MEDITRIAJE_APP o MEDITRIAJE_OWNER
sql <usuario>/<password>@<tns_alias> @database/seeds/dev_seeds_m3.sql
```

### Opción B: Vía PowerShell
```powershell
.\database\seeds\cargar_seeds_dev.ps1
```
