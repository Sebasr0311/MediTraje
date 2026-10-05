/**
 * MediTriaje 2.0 — Utilidades UI y Componentes Reutilizables (ui.js)
 * Sistema de Toasts, Modales de Confirmación, Estados de Carga, Vacío y Error.
 */

// Diccionario de SVGs inline estilo Lucide estándar (sin CDN ni dependencias)
const ICONS = {
  check: '<path d="M20 6L9 17l-5-5"/>',
  'alert-circle': '<circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>',
  'alert-triangle': '<path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>',
  info: '<circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>',
  x: '<line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>',
  user: '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
  calendar: '<rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>',
  clock: '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
  'file-text': '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>',
  pill: '<path d="m10.5 20.5 10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7Z"/><path d="m8.5 8.5 7 7"/>',
  activity: '<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>',
  phone: '<path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>',
  'chevron-right': '<polyline points="9 18 15 12 9 6"/>',
  'chevron-left': '<polyline points="15 18 9 12 15 6"/>',
  'chevron-down': '<polyline points="6 9 12 15 18 9"/>',
  search: '<circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>',
  sun: '<circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>',
  moon: '<path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>',
  'log-out': '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>',
  hospital: '<path d="M12 6v4"/><path d="M14 14h-4"/><path d="M14 18h-4"/><path d="M14 8h-4"/><path d="M18 12h-4"/><path d="M6 12h4"/><rect width="16" height="20" x="4" y="2" rx="2"/><path d="M10 22v-4h4v4"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
  plus: '<line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>',
  'arrow-left': '<line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/>',
  'arrow-right': '<line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/>',
  sparkles: '<path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/>',
  lock: '<rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>',
  'help-circle': '<circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/>',
  copy: '<rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/>',
  printer: '<polyline points="6 9 6 2 18 2 18 9"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect width="12" height="8" x="6" y="14"/>'
};

/**
 * Escapa texto de usuario antes de insertarlo en innerHTML (previene XSS almacenado).
 */
export function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export const ui = {
  /**
   * Retorna una etiqueta SVG con el icono solicitado.
   */
  icon(name, className = 'icon') {
    const content = ICONS[name] || ICONS.info;
    return `<svg class="${className}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${content}</svg>`;
  },

  /**
   * Muestra una notificación Toast accesible.
   */
  showToast(message, type = 'info', duration = 4000) {
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.className = 'toast-container';
      container.setAttribute('role', 'region');
      container.setAttribute('aria-label', 'Notificaciones del sistema');
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast--${type}`;
    toast.setAttribute('role', 'status');

    toast.innerHTML = `
      <span class="toast-message">${message}</span>
      <button type="button" class="toast-close" aria-label="Cerrar notificacion">
        ${this.icon('x', 'icon icon--sm')}
      </button>
    `;

    toast.querySelector('.toast-close').addEventListener('click', () => {
      toast.remove();
    });

    container.appendChild(toast);

    if (duration > 0) {
      setTimeout(() => {
        if (toast.parentElement) {
          toast.remove();
        }
      }, duration);
    }
  },

  /**
   * Muestra un modal accesible de confirmación para acciones irreversibles.
   */
  showModal({
    title = '¿Confirmar acción?',
    message = '¿Estás seguro de continuar con esta operación?',
    confirmText = 'Confirmar',
    cancelText = 'Cancelar',
    isDanger = false,
    onConfirm = () => {},
    onCancel = () => {}
  }) {
    let container = document.getElementById('modal-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'modal-container';
      document.body.appendChild(container);
    }

    const backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop is-open';
    backdrop.setAttribute('role', 'dialog');
    backdrop.setAttribute('aria-modal', 'true');
    backdrop.setAttribute('aria-labelledby', 'modal-dialog-title');

    backdrop.innerHTML = `
      <div class="modal">
        <header class="modal-header">
          <h3 id="modal-dialog-title" class="modal-title">${title}</h3>
          <button type="button" class="btn btn-ghost btn--sm btn--icon-only btn-close-modal" aria-label="Cerrar modal">
            ${this.icon('x')}
          </button>
        </header>
        <div class="modal-body">
          ${typeof message === 'string' && message.trim().startsWith('<') ? message : `<p>${message}</p>`}
        </div>
        <footer class="modal-footer">
          ${cancelText ? `<button type="button" class="btn btn-secondary btn-cancel-modal">${cancelText}</button>` : ''}
          <button type="button" class="btn ${isDanger ? 'btn-danger' : 'btn-primary'} btn-confirm-modal">${confirmText}</button>
        </footer>
      </div>
    `;

    const closeModal = () => {
      document.removeEventListener('keydown', handleKeyDown);
      backdrop.remove();
    };

    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        closeModal();
        onCancel();
      }
    };

    backdrop.querySelector('.btn-close-modal')?.addEventListener('click', () => {
      closeModal();
      onCancel();
    });

    backdrop.querySelector('.btn-cancel-modal')?.addEventListener('click', () => {
      closeModal();
      onCancel();
    });

    backdrop.querySelector('.btn-confirm-modal')?.addEventListener('click', async () => {
      closeModal();
      await onConfirm();
    });

    backdrop.addEventListener('click', (e) => {
      if (e.target === backdrop) {
        closeModal();
        onCancel();
      }
    });

    document.addEventListener('keydown', handleKeyDown);
    container.appendChild(backdrop);

    // Foco accesible al botón de confirmación o volver
    backdrop.querySelector(isDanger ? '.btn-cancel-modal' : '.btn-confirm-modal').focus();
  },

  /**
   * Renderiza un estado de carga con spinner y mensaje.
   */
  renderLoading(container, text = 'Cargando información...') {
    if (!container) return;
    container.innerHTML = `
      <div class="flex flex-col items-center justify-center p-8 gap-4 text-center">
        <div class="skeleton skeleton-circle" style="width: 3rem; height: 3rem; border-radius: 50%;"></div>
        <span class="text-sm text-muted font-medium">${text}</span>
      </div>
    `;
  },

  /**
   * Renderiza skeletons de carga según la estructura deseada.
   */
  renderSkeleton(container, type = 'card') {
    if (!container) return;
    if (type === 'card') {
      container.innerHTML = `
        <div class="card mb-4">
          <div class="skeleton skeleton-title" style="width: 40%;"></div>
          <div class="skeleton skeleton-text" style="width: 80%;"></div>
          <div class="skeleton skeleton-text" style="width: 60%;"></div>
        </div>
      `;
    } else if (type === 'table') {
      container.innerHTML = `
        <div class="table-container p-4">
          <div class="skeleton skeleton-title mb-4" style="width: 30%;"></div>
          <div class="skeleton skeleton-text mb-2"></div>
          <div class="skeleton skeleton-text mb-2"></div>
          <div class="skeleton skeleton-text"></div>
        </div>
      `;
    }
  },

  /**
   * Renderiza un estado vacío amigable con siguiente paso sugerido.
   */
  renderEmpty(container, {
    icon = 'calendar',
    title = 'No hay elementos para mostrar',
    description = 'No se encontraron registros en esta seccion.',
    actionText = null,
    onAction = null
  }) {
    if (!container) return;
    container.innerHTML = `
      <div class="empty-state">
        <div class="empty-state-icon">
          ${this.icon(icon, 'icon icon--lg')}
        </div>
        <h3 class="empty-state-title">${title}</h3>
        <p class="empty-state-desc">${description}</p>
        ${actionText ? `<button type="button" class="btn btn-primary btn--sm empty-btn-action">${actionText}</button>` : ''}
      </div>
    `;

    if (actionText && onAction) {
      container.querySelector('.empty-btn-action').addEventListener('click', onAction);
    }
  },

  /**
   * Renderiza un mensaje de error con botón de reintento.
   */
  renderError(container, {
    title = 'No fue posible cargar la información',
    message = 'Ocurrio un problema al comunicarse con el servidor.',
    onRetry = null
  }) {
    if (!container) return;
    container.innerHTML = `
      <div class="alert alert--danger">
        ${this.icon('alert-circle', 'icon alert-icon')}
        <div class="alert-content">
          <div class="alert-title">${title}</div>
          <p class="mb-3">${message}</p>
          ${onRetry ? `<button type="button" class="btn btn-secondary btn--sm btn-retry">Reintentar</button>` : ''}
        </div>
      </div>
    `;

    if (onRetry) {
      container.querySelector('.btn-retry').addEventListener('click', onRetry);
    }
  },

  /**
   * Alterna la clase de carga en un botón.
   */
  setButtonLoading(button, isLoading) {
    if (!button) return;
    if (isLoading) {
      button.classList.add('btn--loading');
      button.setAttribute('aria-disabled', 'true');
      button.disabled = true;
    } else {
      button.classList.remove('btn--loading');
      button.removeAttribute('aria-disabled');
      button.disabled = false;
    }
  }
};
