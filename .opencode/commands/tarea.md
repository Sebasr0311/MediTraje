---
description: Inicia una tarea del plan de trabajo (ejemplo: /tarea M2.3)
---

Lee AGENTS.md, docs/DECISIONES.md, docs/MVP.md y docs/PROGRESO.md.

Voy a trabajar la tarea **$ARGUMENTS**.

1. Busca la tarea $ARGUMENTS en docs/PLAN_DE_TRABAJO.md y úsala como especificación (qué, por qué, prompt y verificación).
2. Si la tarea toca BD, usa la skill `meditriaje-db-migration`. Si crea endpoints, `meditriaje-api-endpoint`. Si toca frontend, `meditriaje-ui-design`.
3. ANTES de escribir código: dame un plan corto (archivos que crearás o modificarás, riesgos, preguntas abiertas) y espera mi OK.
4. Tras mi OK: implementa SOLO esta tarea, ejecuta las pruebas, actualiza docs/PROGRESO.md y propón el mensaje de commit.
5. Si hay una contradicción o una decisión no definida, detente y pregúntame. No avances a otras tareas.
