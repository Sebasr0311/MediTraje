/**
 * MediTriaje 2.0 — Modal de Cambio de Contraseña (change-password-modal.js)
 * Permite cambiar la contraseña temporal asignada en el alta médica o voluntaria (M3.3, F2.1.3).
 */

import { auth } from '../auth.js';
import { ui, esc } from '../ui.js';

/**
 * Muestra el modal accesible para cambio de contraseña.
 * @param {Object} options
 * @param {boolean} options.isMandatory Si es un cambio obligatorio por contraseña temporal
 */
export function showChangePasswordModal({ isMandatory = false } = {}) {
  let modalContainer = document.getElementById('modal-container');
  if (!modalContainer) {
    modalContainer = document.createElement('div');
    modalContainer.id = 'modal-container';
    document.body.appendChild(modalContainer);
  }

  const backdrop = document.createElement('div');
  backdrop.className = 'modal-backdrop is-open';
  backdrop.setAttribute('role', 'dialog');
  backdrop.setAttribute('aria-modal', 'true');
  backdrop.setAttribute('aria-labelledby', 'changePasswordTitle');

  backdrop.innerHTML = `
    <div class="modal" style="max-width: 32rem;">
      <header class="modal-header">
        <h3 id="changePasswordTitle" class="modal-title flex items-center gap-2">
          ${ui.icon('lock', 'icon text-primary')}
          <span>${isMandatory ? 'Actualizar Contraseña Temporal' : 'Cambiar Contraseña'}</span>
        </h3>
        ${!isMandatory ? `
          <button type="button" class="btn btn-ghost btn--sm btn--icon-only btn-close-modal" aria-label="Cerrar modal">
            ${ui.icon('x')}
          </button>
        ` : ''}
      </header>

      <form id="formChangePassword" novalidate>
        <div class="modal-body">
          ${isMandatory ? `
            <div class="alert alert--info mb-4" role="note">
              ${ui.icon('shield', 'icon alert-icon')}
              <div class="alert-content">
                <div class="alert-title">Primer acceso detectado</div>
                <div class="text-sm">Por seguridad institucional, debes cambiar tu contraseña temporal antes de continuar.</div>
              </div>
            </div>
          ` : `
            <p class="text-sm text-muted mb-4">
              Ingresa tu contraseña actual y define una nueva clave segura de al menos 10 caracteres.
            </p>
          `}

          <div id="changePassAlert" aria-live="polite"></div>

          <div class="form-group mb-3">
            <label for="currentPassword" class="form-label text-sm">
              Contraseña actual o temporal <span class="required" aria-hidden="true">*</span>
            </label>
            <input 
              type="password" 
              id="currentPassword" 
              class="form-input" 
              placeholder="••••••••••••" 
              required
              autocomplete="current-password"
            >
          </div>

          <div class="form-group mb-3">
            <label for="newPassword" class="form-label text-sm">
              Nueva contraseña <span class="required" aria-hidden="true">*</span>
            </label>
            <input 
              type="password" 
              id="newPassword" 
              class="form-input" 
              placeholder="Mínimo 10 caracteres" 
              required
              minlength="10"
              autocomplete="new-password"
            >
            <span class="text-xs text-muted block mt-1">Usa al menos 10 caracteres combinando letras, números y símbolos.</span>
          </div>

          <div class="form-group mb-4">
            <label for="confirmNewPassword" class="form-label text-sm">
              Confirmar nueva contraseña <span class="required" aria-hidden="true">*</span>
            </label>
            <input 
              type="password" 
              id="confirmNewPassword" 
              class="form-input" 
              placeholder="Repite la nueva contraseña" 
              required
              autocomplete="new-password"
            >
          </div>
        </div>

        <footer class="modal-footer flex justify-between gap-3">
          ${!isMandatory ? `
            <button type="button" class="btn btn-secondary btn-cancel-modal">
              <span>Cancelar</span>
            </button>
          ` : `
            <span class="text-xs text-muted">MediTriaje Seguridad</span>
          `}
          <button type="submit" id="btnSubmitChangePass" class="btn btn-primary">
            ${ui.icon('check')}
            <span>Guardar nueva contraseña</span>
          </button>
        </footer>
      </form>
    </div>
  `;

  modalContainer.appendChild(backdrop);

  const closeModal = () => {
    document.removeEventListener('keydown', handleKeyDown);
    backdrop.remove();
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Escape' && !isMandatory) {
      closeModal();
    }
  };

  document.addEventListener('keydown', handleKeyDown);

  backdrop.querySelectorAll('.btn-close-modal, .btn-cancel-modal').forEach(btn => {
    btn.addEventListener('click', closeModal);
  });

  const form = backdrop.querySelector('#formChangePassword');
  const alertContainer = backdrop.querySelector('#changePassAlert');
  const btnSubmit = backdrop.querySelector('#btnSubmitChangePass');
  const currentPassEl = backdrop.querySelector('#currentPassword');
  const newPassEl = backdrop.querySelector('#newPassword');
  const confirmPassEl = backdrop.querySelector('#confirmNewPassword');

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    alertContainer.innerHTML = '';

    const currentPass = currentPassEl.value;
    const newPass = newPassEl.value;
    const confirmPass = confirmPassEl.value;

    if (!currentPass) {
      alertContainer.innerHTML = `<div class="alert alert--danger mb-3">Por favor ingresa tu contraseña actual.</div>`;
      currentPassEl.focus();
      return;
    }

    if (!newPass || newPass.length < 10) {
      alertContainer.innerHTML = `<div class="alert alert--danger mb-3">La nueva contraseña debe tener al menos 10 caracteres.</div>`;
      newPassEl.focus();
      return;
    }

    if (newPass === currentPass) {
      alertContainer.innerHTML = `<div class="alert alert--danger mb-3">La nueva contraseña debe ser diferente a la actual.</div>`;
      newPassEl.focus();
      return;
    }

    if (newPass !== confirmPass) {
      alertContainer.innerHTML = `<div class="alert alert--danger mb-3">Las contraseñas nuevas no coinciden.</div>`;
      confirmPassEl.focus();
      return;
    }

    ui.setButtonLoading(btnSubmit, true);

    try {
      await auth.changePassword(currentPass, newPass);
      ui.showToast('Contraseña actualizada exitosamente.', 'success');
      closeModal();
    } catch (err) {
      alertContainer.innerHTML = `<div class="alert alert--danger mb-3">${esc(err.message || 'No fue posible actualizar la contraseña.')}</div>`;
    } finally {
      ui.setButtonLoading(btnSubmit, false);
    }
  });
}
