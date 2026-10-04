/**
 * MediTriaje 2.0 — Modal de Justificación Legal y Ética Break-Glass (break-glass-modal.js)
 * Acceso clínico de emergencia excepcional ante urgencia vital o incapacidad (ADR-017, F2.5.5).
 * Valida motivo obligatorio (20-500 chars), declaración juramentada y auditoría reforzada.
 */

import { api } from '../api.js';
import { ui, esc } from '../ui.js';
import { router } from '../router.js';

function formatDateTimeBogota(isoString) {
  if (!isoString) return '—';
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    }).format(new Date(isoString));
  } catch {
    return isoString;
  }
}

/**
 * Abre el diálogo modal accesible de activación de Break-Glass.
 * @param {Object} options
 * @param {string} [options.pacientePublicId] ID público del paciente si ya se conoce
 * @param {string} [options.pacienteNombre] Nombre opcional para visualización
 * @param {Function} [options.onActivated] Callback invocado tras la activación exitosa
 */
export async function showBreakGlassModal({ pacientePublicId = '', pacienteNombre = '', onActivated = null } = {}) {
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
  backdrop.setAttribute('aria-labelledby', 'breakGlassModalTitle');

  backdrop.innerHTML = `
    <div class="modal" style="max-width: 40rem;">
      <header class="modal-header" style="border-bottom: 2px solid var(--danger);">
        <h3 id="breakGlassModalTitle" class="modal-title flex items-center gap-2 text-danger">
          ${ui.icon('alert-triangle', 'icon text-danger')}
          <span>Acceso Clínico de Emergencia (Break-Glass)</span>
        </h3>
        <button type="button" class="btn btn-ghost btn--sm btn--icon-only btn-close-bg" aria-label="Cerrar modal">
          ${ui.icon('x')}
        </button>
      </header>

      <div class="modal-body" style="padding: var(--space-4) var(--space-6);">
        <!-- Advertencia Legal y Ética (ADR-017) -->
        <div class="alert alert--danger mb-4" role="alert" style="background-color: var(--danger-bg); border-left: 4px solid var(--danger); padding: var(--space-3) var(--space-4);">
          <div class="flex gap-2">
            <span class="text-danger">${ui.icon('alert-triangle', 'icon icon--sm')}</span>
            <div>
              <strong class="text-sm block" style="color: var(--on-danger-bg);">ADVERTENCIA LEGAL Y ÉTICA OBLIGATORIA (ADR-017)</strong>
              <p class="text-xs m-0 mt-1" style="color: var(--on-danger-bg); line-height: 1.4;">
                Este mecanismo desbloquea de forma inmediata la historia clínica sin mediar cita ni relación previa,
                <strong>exclusivamente ante situaciones de urgencia vital o incapacidad del paciente</strong>.
                Todo acceso queda registrado permanentemente de forma inmutable en la bitácora de auditoría y será
                supervisado por el comité asistencial.
              </p>
            </div>
          </div>
        </div>

        <!-- Lista de accesos activos vigentes -->
        <div id="bgActiveListContainer" class="mb-4" style="display: none;">
          <h4 class="text-xs font-bold uppercase text-muted mb-2">Tus accesos de emergencia vigentes (24h)</h4>
          <div id="bgActiveList" class="flex flex-col gap-2" style="max-height: 8rem; overflow-y: auto;"></div>
        </div>

        <form id="breakGlassForm" novalidate>
          <div class="form-group mb-3">
            <label for="bgPacienteId" class="form-label text-xs font-semibold">Identificador Público del Paciente (UUID) *</label>
            <input type="text" id="bgPacienteId" class="form-input" value="${esc(pacientePublicId)}"
                   placeholder="Ej: 3fa85f64-5717-4562-b3fc-2c963f66afa6"
                   ${pacientePublicId ? 'readonly' : 'required'}>
            ${pacienteNombre ? `<span class="text-xs text-muted mt-1 block">Paciente: <strong>${esc(pacienteNombre)}</strong></span>` : ''}
          </div>

          <div class="form-group mb-3">
            <div class="flex justify-between items-center mb-1">
              <label for="bgMotivo" class="form-label text-xs font-semibold m-0">Justificación médica de urgencia *</label>
              <span id="bgCharCount" class="text-xs text-muted">0 / 500 (mínimo 20)</span>
            </div>
            <textarea id="bgMotivo" class="form-input" rows="4" maxlength="500"
                      placeholder="Describa con precisión la situación clínica, sospecha diagnóstica o motivo de urgencia vital que justifica el acceso inmediato a la historia clínica..."></textarea>
            <span class="text-xs text-muted block mt-1">Mínimo 20 caracteres detallando el criterio médico.</span>
          </div>

          <div class="form-group mb-4">
            <label class="flex items-start gap-2 text-xs" style="cursor: pointer; user-select: none;">
              <input type="checkbox" id="bgJuramento" style="margin-top: 2px;">
              <span>Declaro bajo gravedad de juramento que la consulta responde a una <strong>necesidad médica urgente</strong> y asumo la responsabilidad ética y legal del acceso excepcional (Ley 23 de 1981 / Res. 1995 de 1999).</span>
            </label>
          </div>

          <p id="bgError" class="text-xs text-danger mb-3" role="alert" style="display: none;"></p>

          <div class="flex justify-end gap-3 pt-2" style="border-top: 1px solid var(--border);">
            <button type="button" class="btn btn-secondary btn-close-bg">Cancelar</button>
            <button type="submit" id="btnSubmitBg" class="btn btn-danger" disabled>
              ${ui.icon('shield')}
              <span>Desbloquear acceso (24h)</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  `;

  modalContainer.appendChild(backdrop);

  const $ = (sel) => backdrop.querySelector(sel);
  const closeModal = () => {
    document.removeEventListener('keydown', handleKeyDown);
    backdrop.remove();
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') closeModal();
  };
  document.addEventListener('keydown', handleKeyDown);

  backdrop.querySelectorAll('.btn-close-bg').forEach(b => b.addEventListener('click', closeModal));

  const pacienteInput = $('#bgPacienteId');
  const motivoArea = $('#bgMotivo');
  const charCount = $('#bgCharCount');
  const juramentoCheck = $('#bgJuramento');
  const submitBtn = $('#btnSubmitBg');
  const errorEl = $('#bgError');

  // Consulta de accesos activos para el profesional
  api.get('/clinical/break-glass/active')
    .then(activeItems => {
      if (Array.isArray(activeItems) && activeItems.length > 0) {
        const containerActive = $('#bgActiveListContainer');
        const listActive = $('#bgActiveList');
        containerActive.style.display = 'block';
        listActive.innerHTML = activeItems.map(item => `
          <div class="p-2 card flex justify-between items-center text-xs" style="background: var(--surface-2); border-left: 3px solid var(--danger);">
            <div>
              <span class="font-bold block">${esc(item.pacienteNombre)}</span>
              <span class="text-muted">Vence: ${formatDateTimeBogota(item.fechaExpiracion)}</span>
            </div>
            <button type="button" class="btn btn-secondary btn--sm btn-go-history" data-id="${esc(item.pacientePublicId)}">
              ${ui.icon('file-text', 'icon icon--sm')} <span>Ver historia</span>
            </button>
          </div>
        `).join('');

        listActive.querySelectorAll('.btn-go-history').forEach(btn => {
          btn.addEventListener('click', () => {
            closeModal();
            router.navigate(`/professional/patient-history/${btn.dataset.id}`);
          });
        });
      }
    })
    .catch(() => {
      // Ignorar fallo de carga de activos para no bloquear el modal principal
    });

  function validateForm() {
    const len = motivoArea.value.trim().length;
    charCount.textContent = `${len} / 500 (mínimo 20)`;
    if (len < 20) {
      charCount.style.color = 'var(--danger)';
    } else {
      charCount.style.color = 'var(--success)';
    }

    const validId = pacienteInput.value.trim().length > 0;
    const validMotivo = len >= 20 && len <= 500;
    const checked = juramentoCheck.checked;

    submitBtn.disabled = !(validId && validMotivo && checked);
  }

  motivoArea.addEventListener('input', validateForm);
  pacienteInput.addEventListener('input', validateForm);
  juramentoCheck.addEventListener('change', validateForm);

  $('#breakGlassForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    errorEl.style.display = 'none';
    errorEl.textContent = '';

    const pId = pacienteInput.value.trim();
    const mot = motivoArea.value.trim();

    if (!pId) {
      errorEl.textContent = 'El identificador del paciente es obligatorio.';
      errorEl.style.display = 'block';
      return;
    }
    if (mot.length < 20) {
      errorEl.textContent = 'La justificación médica debe tener al menos 20 caracteres.';
      errorEl.style.display = 'block';
      return;
    }
    if (!juramentoCheck.checked) {
      errorEl.textContent = 'Debe aceptar la declaración juramentada de responsabilidad ética.';
      errorEl.style.display = 'block';
      return;
    }

    ui.setButtonLoading(submitBtn, true);
    try {
      const res = await api.post('/clinical/break-glass', {
        pacientePublicId: pId,
        motivo: mot
      });

      ui.showToast('Acceso clínico de emergencia activado exitosamente (24 horas).', 'success');
      closeModal();

      if (typeof onActivated === 'function') {
        onActivated(res);
      } else {
        router.navigate(`/professional/patient-history/${pId}`);
      }
    } catch (err) {
      ui.setButtonLoading(submitBtn, false);
      errorEl.textContent = err.message || 'Error al solicitar el acceso clínico de emergencia.';
      errorEl.style.display = 'block';
    }
  });

  // Foco inicial
  if (!pacientePublicId) {
    pacienteInput.focus();
  } else {
    motivoArea.focus();
  }
}
