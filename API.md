# API.md — MediTriaje 2.0

> La documentación completa del catálogo y especificación de endpoints REST se encuentra centralizada en:  
> 🔗 **[`docs/api/API.md`](docs/api/API.md)**

Para consultar las secciones específicas:
- [Convenciones Generales (UUIDs, Cookies, CSRF, ApiError)](docs/api/API.md#1-convenciones-y-principios-generales)
- [Autenticación y Cuentas (`/api/v1/auth`)](docs/api/API.md#2-autenticación-y-cuenta-apiv1auth)
- [Pacientes (`/api/v1/patients`)](docs/api/API.md#3-pacientes-apiv1patients)
- [Disponibilidad y Citas (`/api/v1/availability`, `/api/v1/appointments`)](docs/api/API.md#4-disponibilidad-y-citas-apiv1availability-apiv1appointments)
- [Agenda del Profesional (`/api/v1/professionals`)](docs/api/API.md#5-agenda-del-profesional-asistencial-apiv1professionals)
- [Triaje Clínico (`/api/v1/triage`)](docs/api/API.md#6-triaje-clínico-apiv1triage)
- [Atención Médica e Historia Clínica (`/api/v1/attentions`)](docs/api/API.md#7-atención-médica-e-historia-clínica-apiv1attentions)
- [Recetas Médicas y Catálogo (`/api/v1/prescriptions`)](docs/api/API.md#8-recetas-médicas-apiv1prescriptions-apiv1catalogsmedications)
- [Administración del Sistema (`/api/v1/admin/*`)](docs/api/API.md#9-administración-del-sistema-apiv1admin)
- [Monitoreo y Diagnóstico (Ping y Actuator)](docs/api/API.md#10-monitoreo-y-diagnóstico)

Además, puedes revisar y ejecutar las colecciones HTTP interactivas por módulo:
- [`docs/api/M2.http`](docs/api/M2.http) — Autenticación y roles.
- [`docs/api/M3.http`](docs/api/M3.http) — Administración y catálogos.
- [`docs/api/M4.http`](docs/api/M4.http) — Disponibilidad y citas.
- [`docs/api/M5.http`](docs/api/M5.http) — Triaje y corte de emergencia.
- [`docs/api/M6.http`](docs/api/M6.http) — Atención clínica e historia médica.
- [`docs/api/M7.http`](docs/api/M7.http) — Recetas y medicamentos.
