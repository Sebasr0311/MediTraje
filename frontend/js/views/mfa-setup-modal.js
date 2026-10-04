/**
 * MediTriaje 2.0 — Modal de Enrolamiento y Configuración MFA TOTP (mfa-setup-modal.js)
 * Módulo para enrolar profesionales y administradores en autenticación multifactor RFC 6238 (ADR-014, F2.1.4, F2.1.5).
 */

import { auth } from '../auth.js';
import { ui, esc } from '../ui.js';

/**
 * Abre el diálogo modal accesible de configuración de MFA.
 */
export async function showMfaModal() {
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
  backdrop.setAttribute('aria-labelledby', 'mfaModalTitle');

  // Estado inicial de carga
  backdrop.innerHTML = `
    <div class="modal" style="max-width: 36rem;">
      <header class="modal-header">
        <h3 id="mfaModalTitle" class="modal-title flex items-center gap-2">
          ${ui.icon('shield', 'icon text-primary')}
          <span>Autenticación en Dos Pasos (MFA)</span>
        </h3>
        <button type="button" class="btn btn-ghost btn--sm btn--icon-only btn-close-mfa" aria-label="Cerrar modal">
          ${ui.icon('x')}
        </button>
      </header>
      <div class="modal-body text-center" style="padding: var(--space-8) 0;" id="mfaModalBody">
        <div class="empty-state-icon" style="margin: 0 auto var(--space-4) auto; background-color: var(--teal-50); color: var(--primary);">
          ${ui.icon('activity', 'icon icon--lg')}
        </div>
        <p class="text-sm text-muted">Generando secreto criptográfico seguro...</p>
      </div>
    </div>
  `;

  modalContainer.appendChild(backdrop);

  const closeModal = () => {
    document.removeEventListener('keydown', handleKeyDown);
    backdrop.remove();
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') {
      closeModal();
    }
  };

  document.addEventListener('keydown', handleKeyDown);
  backdrop.querySelector('.btn-close-mfa')?.addEventListener('click', closeModal);

  // Solicitar secreto al backend
  let setupData = null;
  try {
    setupData = await auth.setupMfa();
  } catch (err) {
    const modalBody = backdrop.querySelector('#mfaModalBody');
    modalBody.innerHTML = `
      <div class="alert alert--danger text-left">
        ${ui.icon('alert-circle', 'icon alert-icon')}
        <div class="alert-content">
          <p class="m-0">No fue posible iniciar la configuración de MFA. ${esc(err.message || 'Error de servidor.')}</p>
        </div>
      </div>
      <div class="mt-4 text-center">
        <button type="button" class="btn btn-secondary btn-close-mfa-err">Cerrar</button>
      </div>
    `;
    modalBody.querySelector('.btn-close-mfa-err')?.addEventListener('click', closeModal);
    return;
  }

  // Renderizar Paso 1: Escaneo y Verificación
  renderStepSetup(backdrop, setupData, closeModal);
}

/**
 * Renderiza el paso de configuración con el secreto y formulario de verificación.
 */
function renderStepSetup(backdrop, setupData, closeModal) {
  const modalBody = backdrop.querySelector('#mfaModalBody');
  const formattedSecret = setupData.secret ? setupData.secret.match(/.{1,4}/g)?.join(' ') : setupData.secret;

  modalBody.innerHTML = `
    <div class="text-left">
      <p class="text-sm text-muted mb-4">
        Protege tu cuenta agregando una capa extra de seguridad. Cada vez que inicies sesión, 
        se te solicitará un código generado por tu aplicación autenticadora.
      </p>

      <div class="card mb-4" style="background-color: var(--surface-2); border-left: 4px solid var(--primary); padding: var(--space-4);">
        <h4 class="text-sm font-bold mb-2 flex items-center gap-2">
          ${ui.icon('info', 'icon icon--sm text-primary')}
          <span>Paso 1: Agrega tu cuenta en la app autenticadora</span>
        </h4>
        <p class="text-xs text-muted mb-3">
          Abre <strong>Google Authenticator</strong>, <strong>Microsoft Authenticator</strong> o <strong>Authy</strong> 
          y agrega una cuenta ingresando la siguiente clave secreta:
        </p>
        
        <div class="flex items-center gap-2 flex-wrap mb-2">
          <code id="mfaSecretCode" style="font-size: 1.125rem; font-weight: 700; letter-spacing: 0.15em; background: var(--surface); padding: 8px 12px; border-radius: var(--radius-md); border: 1px dashed var(--border); flex: 1 1 auto; text-align: center;">
            ${esc(formattedSecret)}
          </code>
          <button type="button" id="btnCopySecret" class="btn btn-secondary btn--sm" title="Copiar clave">
            ${ui.icon('file-text', 'icon icon--sm')}
            <span>Copiar</span>
          </button>
        </div>

        ${setupData.qrUri ? `
          <div class="mt-2 text-xs">
            <a href="${esc(setupData.qrUri)}" class="text-primary font-medium" style="word-break: break-all;">
              Abrir enlace otpauth directamente en la app
            </a>
          </div>
        ` : ''}
      </div>

      <form id="formMfaVerify" novalidate>
        <div class="form-group mb-4">
          <label for="mfaVerificationCode" class="form-label font-bold">
            Paso 2: Ingresa el código de 6 dígitos de tu aplicación
          </label>
          <input 
            type="text" 
            id="mfaVerificationCode" 
            name="codigo"
            class="form-input text-center" 
            placeholder="123456" 
            maxlength="6"
            inputmode="numeric"
            pattern="[0-9]{6}"
            required 
            autocomplete="one-time-code"
            style="font-family: monospace; font-size: 1.5rem; letter-spacing: 0.25em; max-width: 200px; margin: 0 auto; display: block;"
          >
          <span class="form-error text-center mt-1" id="mfaVerifyError" style="display: none;" role="alert"></span>
        </div>

        <div id="mfaVerifyAlert" class="mb-4"></div>

        <div class="flex justify-end gap-3 mt-6">
          <button type="button" class="btn btn-secondary btn-cancel-mfa">Cancelar</button>
          <button type="submit" id="btnSubmitVerifyMfa" class="btn btn-primary">
            ${ui.icon('shield', 'icon icon--sm')}
            <span>Activar MFA</span>
          </button>
        </div>
      </form>
    </div>
  `;

  // Copiar secreto
  backdrop.querySelector('#btnCopySecret')?.addEventListener('click', () => {
    navigator.clipboard.writeText(setupData.secret).then(() => {
      ui.showToast('Clave secreta copiada al portapapeles.', 'success', 2500);
    }).catch(() => {
      ui.showToast('No se pudo copiar automáticamente. Cópiala manualmente.', 'warning');
    });
  });

  backdrop.querySelector('.btn-cancel-mfa')?.addEventListener('click', closeModal);

  // Manejo del formulario de verificación
  const form = backdrop.querySelector('#formMfaVerify');
  const codeInput = backdrop.querySelector('#mfaVerificationCode');
  const codeError = backdrop.querySelector('#mfaVerifyError');
  const alertContainer = backdrop.querySelector('#mfaVerifyAlert');
  const btnSubmit = backdrop.querySelector('#btnSubmitVerifyMfa');

  // Autofoco en el input
  setTimeout(() => codeInput?.focus(), 150);

  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    codeError.style.display = 'none';
    codeError.textContent = '';
    alertContainer.innerHTML = '';
    codeInput.classList.remove('has-error');

    const codigo = codeInput.value.trim();
    if (!codigo || !/^[0-9]{6}$/.test(codigo)) {
      codeInput.classList.add('has-error');
      codeError.textContent = 'Ingresa los 6 dígitos numéricos.';
      codeError.style.display = 'block';
      codeInput.focus();
      return;
    }

    ui.setButtonLoading(btnSubmit, true);

    try {
      const result = await auth.verifyMfa(codigo);
      ui.showToast('MFA activado con éxito.', 'success');
      renderStepBackupCodes(backdrop, result, closeModal);
    } catch (err) {
      ui.setButtonLoading(btnSubmit, false);
      codeInput.classList.add('has-error');
      alertContainer.innerHTML = `
        <div class="alert alert--danger">
          ${ui.icon('alert-circle', 'icon alert-icon')}
          <div class="alert-content">
            <p class="m-0">${esc(err.message || 'Código incorrecto o expirado. Verifica la hora de tu dispositivo.')}</p>
          </div>
        </div>
      `;
      codeInput.select();
    }
  });
}

/**
 * Renderiza el paso final de confirmación y entrega de códigos de respaldo uniuso.
 */
function renderStepBackupCodes(backdrop, verifyResult, closeModal) {
  const modalBody = backdrop.querySelector('#mfaModalBody');
  const backupCodes = verifyResult.backupCodes || [];

  modalBody.innerHTML = `
    <div class="text-left">
      <div class="alert alert--success mb-4">
        ${ui.icon('check', 'icon alert-icon')}
        <div class="alert-content">
          <h4 class="font-bold text-sm m-0">¡Autenticación en Dos Pasos Activada!</h4>
          <p class="text-xs m-0 mt-1">Tu cuenta ahora está protegida por MFA TOTP (RFC 6238).</p>
        </div>
      </div>

      <div class="mb-4">
        <h4 class="text-sm font-bold mb-1 flex items-center gap-2">
          ${ui.icon('shield', 'icon icon--sm text-warning')}
          <span>Códigos de Respaldo de Emergencia</span>
        </h4>
        <p class="text-xs text-muted mb-3">
          Guarda estos códigos en un lugar seguro. Si pierdes acceso a tu aplicación autenticadora, 
          podrás usar cada código <strong>una sola vez</strong> para ingresar:
        </p>

        <div class="grid grid-cols-2 gap-2 p-3" style="background-color: var(--surface-2); border-radius: var(--radius-md); border: 1px solid var(--border);">
          ${backupCodes.map(code => `
            <div class="text-center font-mono font-bold text-sm p-2" style="background: var(--surface); border-radius: var(--radius-sm); border: 1px solid var(--border); letter-spacing: 0.1em;">
              ${esc(code)}
            </div>
          `).join('')}
        </div>
      </div>

      <div class="flex justify-between items-center flex-wrap gap-3 mt-6 pt-3 border-top">
        <button type="button" id="btnCopyAllCodes" class="btn btn-secondary">
          ${ui.icon('file-text', 'icon icon--sm')}
          <span>Copiar códigos</span>
        </button>
        <button type="button" id="btnFinishMfa" class="btn btn-primary">
          <span>He guardado mis códigos</span>
        </button>
      </div>
    </div>
  `;

  backdrop.querySelector('#btnCopyAllCodes')?.addEventListener('click', () => {
    const textToCopy = `MEDITRIAJE 2.0 - CODIGOS DE RESPALDO MFA\n======================================\n` +
      backupCodes.join('\n') +
      `\n\nCada codigo es de un solo uso. Guardalos en un lugar seguro.`;
    navigator.clipboard.writeText(textToCopy).then(() => {
      ui.showToast('Códigos de respaldo copiados al portapapeles.', 'success');
    }).catch(() => {
      ui.showToast('No se pudieron copiar automáticamente.', 'warning');
    });
  });

  backdrop.querySelector('#btnFinishMfa')?.addEventListener('click', closeModal);
}
