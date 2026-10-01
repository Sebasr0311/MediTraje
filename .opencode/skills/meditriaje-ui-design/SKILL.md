---
name: meditriaje-ui-design
description: Úsala siempre que construyas o modifiques pantallas, CSS o componentes del frontend de MediTriaje. Aplica el sistema de diseño (tokens, paleta, tipografía, componentes, accesibilidad) para una UI profesional, clara y agradable.
---

# UI/UX — MediTriaje

Fuente de verdad: `docs/DISENO_UI_UX.md` y `frontend/css/tokens.css`. Léelos antes de escribir CSS o HTML. Si además está disponible la skill `frontend-design`, úsala para pulir la estética, pero **respetando estos tokens**.

## Reglas
1. **Solo tokens.** Ningún color, tamaño, radio o sombra hardcodeado: usa `var(--...)` de `tokens.css`.
2. **Mobile-first** (360 px en adelante), luego 768, 1024 y 1280.
3. **Un objetivo por pantalla** y una sola acción primaria visible.
4. **Siempre** estados de carga (skeleton), vacío (con siguiente paso sugerido) y error (con cómo resolver).
5. **Nunca solo color**: estados y niveles de triaje llevan icono + texto.
6. **Accesibilidad AA**: contraste, foco visible, `label` en cada campo, navegación por teclado, `aria-live` para errores, objetivos táctiles ≥ 44 px, `lang="es"`, respetar `prefers-reduced-motion`.
7. **HTML semántico** (`header`, `nav`, `main`, `section`, `button` para acciones, `a` para navegar).
8. **Español claro**, frases cortas, sin jerga médica innecesaria; errores que expliquen qué pasó y qué hacer.
9. **Sin dependencias externas en runtime**: fuentes locales, iconos SVG inline (estilo Lucide), cero CDN.
10. **La UI no es una frontera de seguridad**; ocultar un botón no sustituye la validación del backend.

## Antes de dar una pantalla por terminada
Pasa la checklist de la sección 11 de `docs/DISENO_UI_UX.md` y revisa capturas en 375, 768 y 1280 px.
