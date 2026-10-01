# MediTriaje 2.0 — Guía de diseño UI/UX

Fuente de verdad visual. Los valores concretos viven en `frontend/css/tokens.css`; este documento explica **por qué** y **cómo usarlos**.

## 1. Dirección de diseño

**Sensación buscada:** calma, confianza, claridad. Es una app de salud: la persona puede llegar preocupada, con prisa o desde un celular modesto.

| Principio | Qué significa en la práctica |
|---|---|
| **Claridad sobre decoración** | Mucho espacio en blanco, una acción primaria por pantalla, jerarquía tipográfica evidente. |
| **Calma** | Paleta teal suave, bordes redondeados, sin rojos salvo donde importa de verdad (emergencia, errores). |
| **Confianza** | Consistencia total (mismos componentes en todo), lenguaje claro y estados siempre visibles. |
| **Ligera y rápida** | Mobile-first, sin frameworks ni CDN, fuentes e iconos locales. Útil con conectividad limitada. |
| **Inclusiva** | Accesibilidad AA desde el inicio, objetivos táctiles grandes, texto legible. |

## 2. Paleta de color

### Marca y neutros
| Rol | Token | Hex | Uso |
|---|---|---|---|
| Primario | `--primary` | `#0F766E` | Botón principal, enlaces de navegación activos, énfasis |
| Primario (hover) | `--primary-hover` | `#115E59` | Hover/pressed del primario |
| Primario suave | `--primary-soft` | `#F0FDFA` | Fondos de resaltado, tarjetas destacadas |
| Fondo | `--bg` | `#F8FAFC` | Fondo de página |
| Superficie | `--surface` | `#FFFFFF` | Tarjetas, formularios, modales |
| Superficie 2 | `--surface-2` | `#F1F5F9` | Filas alternas, inputs deshabilitados |
| Borde | `--border` | `#E2E8F0` | Separadores y bordes de tarjetas |
| Texto | `--text` | `#0F172A` | Texto principal |
| Texto atenuado | `--text-muted` | `#475569` | Ayudas, metadatos |
| Enlace | `--link` | `#1D4ED8` | Enlaces en texto |
| Foco | `--focus` | `#0EA5E9` | Anillo de foco (siempre visible) |

**Proporción recomendada:** ~70 % neutros, ~20 % teal, ~10 % color de estado.

### Estados
| Estado | Texto/icono | Fondo |
|---|---|---|
| Éxito | `#15803D` | `#DCFCE7` |
| Advertencia | `#B45309` | `#FEF3C7` |
| Error | `#B91C1C` | `#FEE2E2` |
| Información | `#1D4ED8` | `#DBEAFE` |

### Niveles de triaje (alineados con la convención de colores de triaje I–V)
| Nivel | Color | Texto | Fondo | Borde |
|---|---|---|---|---|
| I | Rojo | `#991B1B` | `#FEE2E2` | `#DC2626` |
| II | Naranja | `#9A3412` | `#FFEDD5` | `#EA580C` |
| III | Amarillo | `#854D0E` | `#FEF9C3` | `#CA8A04` |
| IV | Verde | `#166534` | `#DCFCE7` | `#16A34A` |
| V | Azul | `#1E40AF` | `#DBEAFE` | `#2563EB` |

**Regla de oro:** el color nunca es el único portador de significado. Cada nivel y estado lleva **icono + texto** ("Nivel II · Muy urgente").

### Tema oscuro
Definido en `tokens.css` (`[data-theme="dark"]`). Es opcional en el MVP: impleméntalo si sobra tiempo, pero **escribe todo el CSS con tokens desde el inicio** y será gratis.

## 3. Tipografía
- **Familia:** Inter, con respaldo del sistema. **Alójala localmente** (`frontend/assets/fonts/Inter-Variable.woff2`, `font-display: swap`): una app de salud no debe hacer peticiones a terceros.
- **Escala:** 12 / 14 / **16 (base)** / 18 / 20 / 24 / 30 / 36 px (tokens `--text-*`).
- **Interlineado:** 1.5 en texto, 1.25 en títulos. Ancho de línea ≤ 65 caracteres.
- **Pesos:** 400 texto, 500 etiquetas, 600 títulos y botones.
- Nunca texto de cuerpo menor a 14 px; en móvil, 16 px en los inputs (evita el zoom automático de iOS).

## 4. Espaciado y forma
- Espaciado base 4 px (`--space-1` … `--space-16`). Entre tarjetas `--space-4`/`--space-6`; dentro de una tarjeta `--space-5`/`--space-6`.
- Radios: botones e inputs `--radius-md` (10), tarjetas `--radius-lg` (14), modales `--radius-xl`.
- Sombras suaves; preferir borde fino `--border` + `--shadow-sm`. Nada de sombras pesadas.
- Iconos: SVG **inline** estilo Lucide (trazo 1.75–2 px, 20–24 px), copiados al repo, sin CDN.

## 5. Componentes

**Botones**
- Primario (relleno teal), Secundario (borde + fondo blanco), Terciario/ghost (solo texto), Peligro (rojo, solo acciones destructivas).
- Altura mínima 44 px, texto en verbo ("Agendar cita", no "Aceptar"). Solo **un** primario por vista. Estado de carga con spinner y botón deshabilitado.

**Campos de formulario**
- Etiqueta siempre visible **arriba** (nunca solo placeholder), texto de ayuda debajo, error en rojo con icono y texto claro, vinculado con `aria-describedby`.
- Validación al salir del campo y al enviar; no regañar mientras se escribe.
- Tipos y `autocomplete` correctos (`email`, `tel`, `bday`, etc.).

**Tarjetas** — contenedor base de todo: `--surface`, borde `--border`, radio `--radius-lg`. Variante "destacada" con `--primary-soft`.

**Badges de estado**
- Cita: PROGRAMADA (info), CONFIRMADA (éxito), ATENDIDA (neutro), CANCELADA/NO_ASISTIÓ (error/advertencia), REPROGRAMADA (advertencia). Siempre con icono y texto en español legible.
- Nivel de triaje: píldora con los colores de la sección 2.

**Navegación**
- Escritorio: barra lateral (profesional/admin) o barra superior (paciente).
- Móvil: barra inferior con 4–5 destinos para el paciente (Inicio, Triaje, Citas, Historia, Perfil). Elemento activo claramente marcado.
- Migas de pan solo en administración.

**Tablas** (admin/profesional): en móvil se transforman en lista de tarjetas. Encabezados fijos, orden y paginación.

**Feedback**
- *Toast* para éxito (se cierra solo, `role="status"`).
- Error de formulario en línea + resumen arriba (`role="alert"`).
- *Modal de confirmación* para acciones irreversibles (cerrar atención, cancelar cita) que diga **qué pasará**, no solo "¿Seguro?".
- *Skeletons* en carga, no pantallas en blanco.
- *Estados vacíos* con ilustración sencilla (SVG), una frase y el siguiente paso ("Aún no tienes citas · Buscar disponibilidad").

## 6. Pantallas clave

**Login / Registro** — tarjeta centrada (`--container-narrow`), logo, formulario corto. Registro en 2 pasos (cuenta → datos personales) con el consentimiento de datos como paso explícito, con enlace al texto completo y casilla **no premarcada**.

**Dashboard del paciente** — saludo breve, tarjeta destacada "Próxima cita", acceso grande "Necesito atención" (inicia el triaje), luego últimas atenciones y recetas. Máximo 4–5 tarjetas.

**Triaje (asistente por pasos)**
- Una pregunta por pantalla, barra de progreso ("Paso 2 de 5"), botones grandes, "Atrás" siempre disponible, ancho estrecho.
- Selector de síntomas con búsqueda y chips grandes seleccionables; intensidad con escala 0–10 táctil.
- Texto de apoyo sereno al inicio: "Responde con calma. Esto orienta, no reemplaza a un profesional de la salud."

**Resultado del triaje** — nivel con color + icono + texto, explicación en lenguaje simple, ruta sugerida y **un** botón primario ("Ver horarios disponibles"). Aviso permanente de que no es un diagnóstico.

**Pantalla de EMERGENCIA** (síntomas de alarma)
- Fondo `--triage-1-bg`, borde rojo, icono grande, título "Busca atención de urgencias ahora".
- Botón enorme **"Llamar al 123"** (`<a href="tel:123">`) y texto de ir a urgencias.
- **Sin** botón de agendar, sin distracciones, sin animaciones.

**Disponibilidad y reserva (≤ 3 pasos)** — filtros arriba (especialidad, fecha, sede); resultados agrupados por día con **chips de hora** táctiles; confirmación en un resumen claro antes de reservar; pantalla de éxito con "Agregar al calendario" opcional.

**Historia clínica** — línea de tiempo vertical (fecha, profesional, motivo), detalle expandible, enmiendas visibles y marcadas, solo lectura. Lenguaje de lista, no de documento denso.

**Agenda del profesional** — vista del día en lista cronológica, estado por colores + texto, acceso directo a "Iniciar atención". Formulario de atención en secciones colapsables (signos vitales, evolución, diagnóstico, indicaciones, receta) con guardado visible y confirmación al cerrar.

**Administración** — tablas con filtros y acciones por fila; el generador de slots con vista previa de lo que se creará antes de confirmar.

## 7. Microcopy (voz y tono)
- Español claro, cercano y respetuoso; frases cortas. Decide el trato y mantenlo: **tú** en la app de paciente, tono neutro-formal en profesional y administración.
- Evita jerga médica y mensajes técnicos. Mal: "Error 409". Bien: "Ese horario acaba de ser tomado. Elige otro, por favor."
- Los errores explican qué pasó y qué hacer. Los éxitos confirman lo concreto ("Tu cita quedó para el jueves 8 de octubre, 8:30 a. m.").
- Nunca prometer resultados clínicos ni afirmar diagnósticos.
- Fechas en formato local ("jueves 8 de octubre", hora de Colombia) y legibles.

## 8. Accesibilidad (WCAG 2.2 AA)
- Contraste ≥ 4.5:1 en texto, ≥ 3:1 en componentes e iconos (los tokens ya cumplen).
- Todo operable con teclado; orden de foco lógico; foco **siempre** visible.
- Etiquetas de formulario, `alt` en imágenes, `aria-live` en errores y toasts, `lang="es"`.
- Objetivos táctiles ≥ 44 × 44 px; espacio entre ellos.
- Respetar `prefers-reduced-motion`. No usar solo color para informar.
- Funcional con zoom al 200 % y texto agrandado.

## 9. Responsive y rendimiento
- Mobile-first. Puntos de quiebre: 360 (base), 768, 1024, 1280.
- Una columna en móvil; 2 columnas (contenido + lateral) desde 1024.
- Peso objetivo: página inicial liviana, sin librerías; imágenes en SVG o WebP; fuentes locales con subconjunto latino.
- Probar en un celular real con conexión lenta.

## 10. Cómo trabajarlo con la CLI

**Orden:** primero el sistema de diseño, luego las pantallas. Así todo queda consistente.

**M8.0 — Sistema de diseño (hazlo antes de M8.1)**
```
Usa la skill meditriaje-ui-design y la skill frontend-design si está disponible. Lee docs/DISENO_UI_UX.md y frontend/css/tokens.css (no cambies los tokens sin avisarme).
Crea frontend/css/base.css y frontend/css/components.css con: botones (4 variantes + estado de carga), campos de formulario (label, ayuda, error), tarjetas, badges de cita y de triaje, alertas, toast, modal de confirmación, skeleton, estado vacío, navegación (superior, lateral, inferior móvil) y tabla responsive. Solo var(--...) de tokens; mobile-first; accesible.
Crea frontend/styleguide.html que muestre TODOS los componentes y estados, en tema claro y oscuro. Íconos SVG inline estilo Lucide en frontend/assets/icons/. Sin CDN ni fuentes externas.
```
Después revisa `styleguide.html` tú mismo en el navegador y en el celular **antes de seguir**. Ajustar aquí es barato; ajustar con 20 pantallas hechas, no.

**Cada pantalla (M8.2–M8.4)**
```
Usa meditriaje-ui-design. Implementa la pantalla {nombre} según la sección 6 de docs/DISENO_UI_UX.md reutilizando SOLO componentes de components.css. Incluye estados de carga, vacío y error. Verifica con la checklist de la sección 11.
```
**Revisión visual (por pantalla o por flujo):** `/revisar-ui frontend/pages/<pantalla>.html` (usa Playwright para capturas a 375, 768 y 1280 px).

**Si el resultado se ve "genérico":** pide ajustes concretos, no vagos. Ejemplo: "Aumenta el espacio entre secciones a `--space-8`, reduce el peso de los títulos de tarjeta a 600 y deja un solo botón primario" funciona mejor que "hazlo más bonito".

## 11. Checklist de revisión de cada pantalla
**Visual**
- [ ] Una sola acción primaria clara.
- [ ] Jerarquía evidente (título → contenido → acción).
- [ ] Espaciado consistente (solo `--space-*`); mucho aire.
- [ ] Solo colores de tokens; teal en ≤ ~20 % de la pantalla.
- [ ] Mismos componentes que el resto de la app.

**Usabilidad**
- [ ] Se entiende qué hacer en 5 segundos.
- [ ] Estados de carga, vacío y error presentes y útiles.
- [ ] Acciones destructivas con confirmación que explica la consecuencia.
- [ ] Textos claros y en el tono definido; fechas en formato local.
- [ ] Formularios: etiquetas visibles, errores en línea, `autocomplete` correcto.

**Accesibilidad**
- [ ] Todo funciona con teclado; foco visible.
- [ ] Contraste AA; el color no es el único indicador.
- [ ] Objetivos táctiles ≥ 44 px.
- [ ] `lang="es"`, landmarks (`header`, `nav`, `main`), `aria-live` donde corresponde.

**Técnico**
- [ ] Se ve bien a 375, 768 y 1280 px; sin scroll horizontal.
- [ ] Sin dependencias externas ni valores hardcodeados.
- [ ] Sin datos sensibles en la URL, `localStorage` o consola.
- [ ] La seguridad no depende de ocultar elementos.
