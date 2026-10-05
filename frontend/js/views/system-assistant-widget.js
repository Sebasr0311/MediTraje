/**
 * MediTriaje 2.0 — Widget Flotante del Asistente del Sistema (system-assistant-widget.js)
 * F2.6, RF-27, ADR-018, DOCUMENTO_MAESTRO §5.19.
 * Orientación interactiva, navegación asistida y corte infalible de emergencias.
 * REGLA ESTRICTA: CERO diagnósticos ni prescripción médica.
 */

import { api } from '../api.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';
import { auth } from '../auth.js';

let widgetInitialized = false;

/**
 * Obtiene la configuración de bienvenida, chips sugeridos y contexto según el rol activo.
 */
function getAssistantRoleConfig() {
  if (!auth.isAuthenticated) {
    return {
      rol: 'GUEST',
      subtitulo: 'Información y Registro',
      bienvenidaHtml: `
        <p class="m-0 mb-2">
          👋 <strong>¡Hola!</strong> Te damos la bienvenida a <strong>MediTriaje 2.0</strong>.
        </p>
        <p class="m-0 text-xs text-muted">
          Soy tu asistente informativo. Puedo contarte cómo funciona nuestra plataforma de <strong>triaje clínico</strong>,
          los <strong>5 niveles de prioridad</strong> o cómo <strong>crear tu cuenta</strong>. Si deseas agendar una cita o realizar tu triaje oficial, deberás iniciar sesión o registrarte.
        </p>
      `,
      chips: [
        { label: '🩺 ¿Qué es MediTriaje?', query: '¿Qué es MediTriaje y cómo funciona?' },
        { label: '🏷️ 5 niveles de triaje', query: '¿Cuáles son los 5 niveles de prioridad del triaje?' },
        { label: '📅 ¿Cómo agendar cita?', query: '¿Cómo agendar una cita médica?' },
        { label: '📝 ¿Cómo me registro?', query: '¿Cómo me registro como paciente?' },
        { label: '⚠️ Emergencia médica', query: 'Tengo un dolor muy fuerte en el pecho', danger: true }
      ]
    };
  }

  if (auth.isAdmin) {
    return {
      rol: 'ROLE_ADMINISTRADOR',
      subtitulo: 'Consola de Administración',
      bienvenidaHtml: `
        <p class="m-0 mb-2">
          👋 <strong>¡Hola, Administrador!</strong>
        </p>
        <p class="m-0 text-xs text-muted">
          Soy tu asistente de gestión operativa. Puedo ayudarte a consultar <strong>reportes y métricas</strong>,
          gestionar <strong>profesionales de la salud</strong> bajo la Ley 1164, habilitar <strong>sedes e instituciones</strong>,
          generar <strong>slots de disponibilidad</strong> o revisar la <strong>auditoría</strong> del sistema.
        </p>
      `,
      chips: [
        { label: '📊 Reportes operativos', query: '¿Cómo consultar los reportes operativos y métricas?' },
        { label: '👨‍⚕️ Alta de profesionales', query: '¿Cómo registrar un profesional de la salud según la ley?' },
        { label: '⏰ Generar turnos de citas', query: '¿Cómo generar slots de disponibilidad para citas?' },
        { label: '🏥 Sedes e IPS', query: '¿Cómo gestionar sedes e instituciones hospitalarias?' },
        { label: '🔒 Registro de auditoría', query: '¿Cómo revisar el registro de auditoría del sistema?' }
      ]
    };
  }

  if (auth.isProfesional) {
    return {
      rol: 'ROLE_PROFESIONAL',
      subtitulo: 'Asistencia Asistencial',
      bienvenidaHtml: `
        <p class="m-0 mb-2">
          👋 <strong>¡Hola, Doctor(a)!</strong>
        </p>
        <p class="m-0 text-xs text-muted">
          Soy tu asistente clínico. Puedo orientarte en la consulta de tu <strong>agenda médica diaria</strong>,
          registro de <strong>atenciones con diagnóstico CIE-10</strong>, emisión de <strong>recetas electrónicas</strong> y
          el protocolo de emergencia asistencial <strong>Break-Glass</strong>.
        </p>
      `,
      chips: [
        { label: '📅 Mi agenda de citas', query: '¿Cómo consultar mi agenda de citas de hoy?' },
        { label: '📋 Registro de atención', query: '¿Cómo registrar una atención médica y diagnósticos CIE-10?' },
        { label: '💊 Prescribir receta', query: '¿Cómo prescribir medicamentos y emitir una receta médica?' },
        { label: '🚨 Acceso Break-Glass', query: '¿Cómo funciona el protocolo de acceso de emergencia Break-Glass?' },
        { label: '📜 Historia inmutable', query: '¿Cuáles son las reglas de inmutabilidad de la historia clínica?' }
      ]
    };
  }

  if (auth.isFarmaceutico) {
    return {
      rol: 'ROLE_FARMACEUTICO',
      subtitulo: 'Ventanilla de Farmacia',
      bienvenidaHtml: `
        <p class="m-0 mb-2">
          👋 <strong>¡Hola!</strong> Asistente de farmacia.
        </p>
        <p class="m-0 text-xs text-muted">
          Puedo guiarte en la <strong>búsqueda de recetas</strong> mediante código alfanumérico, verificación de vigencia y registro de <strong>dispensaciones de medicamentos</strong>.
        </p>
      `,
      chips: [
        { label: '💊 Dispensar receta', query: '¿Cómo buscar y dispensar una receta médica?' },
        { label: '🔍 Saldos pendientes', query: '¿Cómo consultar el saldo de medicamentos pendientes?' }
      ]
    };
  }

  // Rol por defecto: PACIENTE
  return {
    rol: 'ROLE_PACIENTE',
    subtitulo: 'Orientación al Paciente 24/7',
    bienvenidaHtml: `
      <p class="m-0 mb-2">
        👋 <strong>¡Hola!</strong> Soy tu asistente de orientación en <strong>MediTriaje 2.0</strong>.
      </p>
      <p class="m-0 text-xs text-muted">
        Puedo guiarte en cómo realizar tu <strong>triaje de síntomas</strong>, agendar o cancelar <strong>citas médicas</strong>,
        reclamar tus <strong>medicamentos en farmacia</strong> o generar tu <strong>código QR de emergencia</strong>.
      </p>
    `,
    chips: [
      { label: '🩺 ¿Cómo hacer mi triaje?', query: '¿Qué es el triaje y cómo funciona?' },
      { label: '📅 ¿Cómo agendar mi cita?', query: '¿Cómo agendar una cita médica?' },
      { label: '💊 ¿Cómo reclamo mis recetas?', query: '¿Cómo reclamo mis medicamentos en farmacia?' },
      { label: '📲 QR de emergencia', query: '¿Cómo funciona el código QR de emergencia?' },
      { label: '⚠️ Emergencia médica', query: 'Tengo un dolor muy fuerte en el pecho', danger: true }
    ]
  };
}

/**
 * Inicializa el widget flotante del asistente global en el DOM si aún no existe.
 */
export function initSystemAssistantWidget() {
  if (widgetInitialized || document.getElementById('assistant-fab')) {
    return;
  }
  widgetInitialized = true;

  // Inyectar contenedor del widget
  const host = document.createElement('aside');
  host.id = 'assistant-widget-host';
  host.setAttribute('aria-label', 'Asistente de orientación de MediTriaje');
  host.innerHTML = `
    <!-- Botón flotante accesible (FAB) -->
    <button type="button" id="assistant-fab" class="assistant-fab" aria-expanded="false" aria-controls="assistant-panel" aria-label="Abrir asistente de MediTriaje" title="Asistente de orientación y ayuda">
      <span class="assistant-fab-icon">
        ${ui.icon('message-circle', 'icon icon--md')}
      </span>
      <span class="assistant-fab-badge" aria-hidden="true">Ayuda</span>
    </button>

    <!-- Panel de Chat del Asistente -->
    <section id="assistant-panel" class="assistant-panel" aria-hidden="true" role="dialog" aria-modal="false" aria-labelledby="assistant-panel-title">
      <!-- Cabecera -->
      <header class="assistant-panel-header">
        <div class="flex items-center gap-2">
          <div class="assistant-avatar">
            ${ui.icon('activity', 'icon icon--sm')}
          </div>
          <div>
            <h2 id="assistant-panel-title" class="text-sm font-bold m-0 text-white">Asistente MediTriaje</h2>
            <span id="assistant-panel-subtitle" class="text-xs text-teal-100 flex items-center gap-1">
              <span class="status-dot"></span> Orientación 24/7
            </span>
          </div>
        </div>
        <button type="button" id="assistant-close-btn" class="assistant-close-btn" aria-label="Cerrar panel de asistente">
          ${ui.icon('x', 'icon icon--sm')}
        </button>
      </header>

      <!-- Mensajes del Chat -->
      <div id="assistant-messages" class="assistant-messages" role="log" aria-live="polite">
        <!-- Renderizado dinámico según el rol -->
      </div>

      <!-- Pie y Formulario de Entrada -->
      <footer class="assistant-panel-footer">
        <form id="assistant-form" class="assistant-input-bar">
          <input type="text" id="assistant-input" class="assistant-input" placeholder="Escribe tu consulta aquí..." autocomplete="off" maxlength="500" required aria-label="Escribe tu pregunta para el asistente">
          <button type="submit" id="assistant-send-btn" class="assistant-send-btn" aria-label="Enviar mensaje">
            ${ui.icon('send', 'icon icon--sm')}
          </button>
        </form>
        <p class="assistant-disclaimer m-0">
          Orientación operativa. No sustituye la valoración médica profesional.
        </p>
      </footer>
    </section>
  `;

  document.body.appendChild(host);

  // Inyectar estilos dedicados del widget (cumpliendo con tokens.css)
  inyectarEstilosWidget();

  // Controladores de eventos
  const fab = host.querySelector('#assistant-fab');
  const panel = host.querySelector('#assistant-panel');
  const closeBtn = host.querySelector('#assistant-close-btn');
  const form = host.querySelector('#assistant-form');
  const input = host.querySelector('#assistant-input');
  const messagesBox = host.querySelector('#assistant-messages');

  function renderizarPantallaInicial() {
    const config = getAssistantRoleConfig();
    const subEl = host.querySelector('#assistant-panel-subtitle');
    if (subEl) {
      subEl.innerHTML = `<span class="status-dot"></span> ${esc(config.subtitulo)}`;
    }

    messagesBox.innerHTML = `
      <!-- Mensaje de bienvenida inicial -->
      <div class="assistant-msg assistant-msg--bot">
        <div class="assistant-msg-bubble">
          ${config.bienvenidaHtml}
        </div>
        <span class="assistant-msg-time">Ahora</span>
      </div>

      <!-- Atajos rápidos iniciales -->
      <div class="assistant-shortcuts-box mb-3">
        <span class="text-xs font-semibold text-muted mb-2 block">Consultas sugeridas:</span>
        <div class="assistant-chips">
          ${config.chips.map(chip => `
            <button type="button" class="assistant-chip ${chip.danger ? 'assistant-chip--danger' : ''}" data-query="${esc(chip.query)}">${esc(chip.label)}</button>
          `).join('')}
        </div>
      </div>
    `;

    // Conectar listeners de chips
    messagesBox.querySelectorAll('.assistant-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        const q = chip.dataset.query;
        if (q) {
          input.value = q;
          enviarConsulta(q);
        }
      });
    });
  }

  // Renderizar la pantalla inicial con el rol actual
  renderizarPantallaInicial();

  // Escuchar cambios de autenticación para regenerar el asistente según el nuevo rol
  auth.addEventListener('auth:change', () => {
    renderizarPantallaInicial();
  });

  function abrirPanel() {
    panel.classList.add('is-open');
    panel.setAttribute('aria-hidden', 'false');
    fab.setAttribute('aria-expanded', 'true');
    input.focus();
  }

  function cerrarPanel() {
    panel.classList.remove('is-open');
    panel.setAttribute('aria-hidden', 'true');
    fab.setAttribute('aria-expanded', 'false');
    fab.focus();
  }

  fab.addEventListener('click', () => {
    if (panel.classList.contains('is-open')) {
      cerrarPanel();
    } else {
      abrirPanel();
    }
  });

  closeBtn.addEventListener('click', cerrarPanel);

  // Envío del formulario
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const texto = input.value.trim();
    if (!texto) return;
    enviarConsulta(texto);
  });

  async function enviarConsulta(consultaTexto) {
    input.value = '';
    // 1. Agregar burbuja del usuario
    agregarMensajeUsuario(consultaTexto);

    // 2. Indicador de tipeo
    const loaderId = agregarIndicadorTipeo();
    scrollAlFinal();

    const config = getAssistantRoleConfig();

    try {
      const resp = await api.post('/assistant/chat', {
        mensaje: consultaTexto,
        contexto: config.rol
      });
      removerIndicadorTipeo(loaderId);
      agregarMensajeBot(resp);
    } catch (err) {
      removerIndicadorTipeo(loaderId);
      agregarMensajeBot({
        respuesta: 'Lo siento, no pude procesar tu mensaje en este momento. ' + (err.message || 'Por favor intenta nuevamente.'),
        esEmergencia: false,
        categoria: 'ERROR',
        sugerencias: [],
        avisoLegal: null
      });
    }

    scrollAlFinal();
  }

  function agregarMensajeUsuario(texto) {
    const el = document.createElement('div');
    el.className = 'assistant-msg assistant-msg--user';
    el.innerHTML = `
      <div class="assistant-msg-bubble">
        <p class="m-0">${esc(texto)}</p>
      </div>
      <span class="assistant-msg-time">Tú</span>
    `;
    messagesBox.appendChild(el);
  }

  function agregarIndicadorTipeo() {
    const id = 'loader-' + Date.now();
    const el = document.createElement('div');
    el.id = id;
    el.className = 'assistant-msg assistant-msg--bot';
    el.innerHTML = `
      <div class="assistant-msg-bubble assistant-typing">
        <span></span><span></span><span></span>
      </div>
    `;
    messagesBox.appendChild(el);
    return id;
  }

  function removerIndicadorTipeo(id) {
    const el = document.getElementById(id);
    if (el) el.remove();
  }

  function agregarMensajeBot(resp) {
    const el = document.createElement('div');
    el.className = 'assistant-msg assistant-msg--bot';

    const esEmergencia = resp.esEmergencia;
    const bubbleCls = esEmergencia ? 'assistant-msg-bubble assistant-msg-bubble--emergency' : 'assistant-msg-bubble';

    let sugerenciasHtml = '';
    if (resp.sugerencias && resp.sugerencias.length > 0) {
      sugerenciasHtml = `
        <div class="assistant-actions mt-3 flex flex-wrap gap-2">
          ${resp.sugerencias.map(sug => {
            const isTel = sug.rutaSpa && sug.rutaSpa.startsWith('tel:');
            const btnCls = isTel || esEmergencia ? 'btn btn-danger btn--xs' : 'btn btn-secondary btn--xs';
            return `
              <button type="button" class="${btnCls} assistant-action-btn" data-href="${esc(sug.rutaSpa)}">
                ${sug.icono ? ui.icon(sug.icono, 'icon icon--xs') : ''}
                <span>${esc(sug.etiqueta)}</span>
              </button>
            `;
          }).join('')}
        </div>
      `;
    }

    let legalHtml = '';
    if (resp.avisoLegal) {
      legalHtml = `<div class="assistant-legal-tag mt-2">${esc(resp.avisoLegal)}</div>`;
    }

    el.innerHTML = `
      <div class="${bubbleCls}">
        ${esEmergencia ? '<div class="emergency-badge mb-2">🚨 ATENCIÓN INMEDIATA 123</div>' : ''}
        <p class="m-0 leading-relaxed">${esc(resp.respuesta)}</p>
        ${sugerenciasHtml}
        ${legalHtml}
      </div>
      <span class="assistant-msg-time">MediTriaje</span>
    `;

    // Conectar botones de navegación SPA
    el.querySelectorAll('.assistant-action-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const href = btn.dataset.href;
        if (!href) return;
        if (href.startsWith('tel:')) {
          window.location.href = href;
        } else if (href.startsWith('#')) {
          cerrarPanel();
          router.navigate(href.replace(/^#/, ''));
        }
      });
    });

    messagesBox.appendChild(el);
  }

  function scrollAlFinal() {
    messagesBox.scrollTop = messagesBox.scrollHeight;
  }
}

/**
 * Inyecta las reglas CSS necesarias para el widget flotante,
 * utilizando variables nativas de tokens.css.
 */
function inyectarEstilosWidget() {
  if (document.getElementById('assistant-widget-styles')) return;

  const style = document.createElement('style');
  style.id = 'assistant-widget-styles';
  style.textContent = `
    .assistant-fab {
      position: fixed;
      bottom: calc(var(--space-6) + 60px);
      right: var(--space-6);
      z-index: 1000;
      width: 56px;
      height: 56px;
      border-radius: var(--radius-full);
      background-color: var(--primary);
      color: var(--on-primary);
      border: 2px solid var(--surface);
      box-shadow: var(--shadow-lg);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: transform var(--duration-fast) var(--ease), background-color var(--duration-fast) var(--ease);
    }
    .assistant-fab:hover {
      transform: scale(1.06);
      background-color: var(--primary-hover);
    }
    .assistant-fab-badge {
      font-size: 9px;
      font-weight: var(--weight-bold);
      line-height: 1;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .assistant-panel {
      position: fixed;
      bottom: calc(var(--space-6) + 120px);
      right: var(--space-6);
      z-index: 1001;
      width: 380px;
      max-width: calc(100vw - 32px);
      height: 520px;
      max-height: calc(100vh - 140px);
      background-color: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius-lg);
      box-shadow: var(--shadow-xl);
      display: flex;
      flex-direction: column;
      overflow: hidden;
      opacity: 0;
      transform: translateY(20px) scale(0.96);
      pointer-events: none;
      transition: opacity var(--duration-normal) var(--ease), transform var(--duration-normal) var(--ease);
    }
    .assistant-panel.is-open {
      opacity: 1;
      transform: translateY(0) scale(1);
      pointer-events: auto;
    }
    .assistant-panel-header {
      background-color: var(--primary);
      color: var(--on-primary);
      padding: var(--space-3) var(--space-4);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .assistant-avatar {
      width: 32px;
      height: 32px;
      border-radius: var(--radius-full);
      background-color: rgba(255,255,255,0.2);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .status-dot {
      display: inline-block;
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: #2DD4BF;
    }
    .assistant-close-btn {
      background: transparent;
      border: none;
      color: var(--on-primary);
      cursor: pointer;
      padding: var(--space-1);
      border-radius: var(--radius-sm);
    }
    .assistant-messages {
      flex: 1;
      overflow-y: auto;
      padding: var(--space-4);
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      background-color: var(--slate-50);
    }
    .assistant-msg {
      display: flex;
      flex-direction: column;
      max-width: 86%;
    }
    .assistant-msg--bot {
      align-self: flex-start;
    }
    .assistant-msg--user {
      align-self: flex-end;
    }
    .assistant-msg-bubble {
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius-md);
      font-size: var(--text-sm);
      line-height: 1.45;
      background-color: var(--surface);
      color: var(--text);
      border: 1px solid var(--border);
      box-shadow: var(--shadow-sm);
    }
    .assistant-msg--user .assistant-msg-bubble {
      background-color: var(--primary);
      color: var(--on-primary);
      border-color: var(--primary);
      border-bottom-right-radius: 2px;
    }
    .assistant-msg-bubble--emergency {
      border: 2px solid var(--danger);
      background-color: var(--danger-bg);
      color: var(--on-danger-bg);
    }
    .emergency-badge {
      display: inline-block;
      font-size: 11px;
      font-weight: var(--weight-bold);
      color: var(--danger);
      background: #FFFFFF;
      padding: 2px 6px;
      border-radius: var(--radius-sm);
      border: 1px solid var(--danger);
    }
    .assistant-msg-time {
      font-size: 10px;
      color: var(--slate-400);
      margin-top: 2px;
      padding: 0 4px;
    }
    .assistant-shortcuts-box {
      background: var(--surface);
      border: 1px dashed var(--border);
      border-radius: var(--radius-md);
      padding: var(--space-3);
    }
    .assistant-chips {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .assistant-chip {
      background-color: var(--slate-100);
      color: var(--slate-800);
      border: 1px solid var(--slate-200);
      border-radius: var(--radius-sm);
      padding: 6px 8px;
      font-size: 11px;
      text-align: left;
      cursor: pointer;
      transition: background-color var(--duration-fast);
    }
    .assistant-chip:hover {
      background-color: var(--teal-50);
      border-color: var(--teal-300);
      color: var(--primary);
    }
    .assistant-chip--danger {
      border-color: #FCA5A5;
      color: var(--danger);
    }
    .assistant-chip--danger:hover {
      background-color: #FEE2E2;
      border-color: var(--danger);
      color: var(--danger);
    }
    .assistant-panel-footer {
      padding: var(--space-3);
      border-top: 1px solid var(--border);
      background-color: var(--surface);
    }
    .assistant-input-bar {
      display: flex;
      gap: var(--space-2);
      align-items: center;
    }
    .assistant-input {
      flex: 1;
      height: 38px;
      border: 1px solid var(--border);
      border-radius: var(--radius-md);
      padding: 0 var(--space-3);
      font-size: var(--text-sm);
      background-color: var(--surface);
      color: var(--text);
    }
    .assistant-input:focus {
      outline: 2px solid var(--focus);
    }
    .assistant-send-btn {
      width: 38px;
      height: 38px;
      border-radius: var(--radius-md);
      background-color: var(--primary);
      color: var(--on-primary);
      border: none;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
    }
    .assistant-disclaimer {
      font-size: 10px;
      color: var(--slate-500);
      text-align: center;
      margin-top: 6px;
    }
    .assistant-legal-tag {
      font-size: 10px;
      color: var(--slate-500);
      font-style: italic;
      border-top: 1px dotted var(--border);
      padding-top: 4px;
    }
    .assistant-typing span {
      display: inline-block;
      width: 6px;
      height: 6px;
      margin: 0 2px;
      background: var(--slate-400);
      border-radius: 50%;
      animation: typing 1.4s infinite ease-in-out both;
    }
    .assistant-typing span:nth-child(1) { animation-delay: -0.32s; }
    .assistant-typing span:nth-child(2) { animation-delay: -0.16s; }
    @keyframes typing {
      0%, 80%, 100% { transform: scale(0); }
      40% { transform: scale(1); }
    }
    @media (max-width: 640px) {
      .assistant-fab {
        bottom: calc(var(--space-4) + 60px);
        right: var(--space-4);
      }
      .assistant-panel {
        right: var(--space-4);
        left: var(--space-4);
        width: auto;
        bottom: calc(var(--space-4) + 120px);
      }
    }
  `;
  document.head.appendChild(style);
}
