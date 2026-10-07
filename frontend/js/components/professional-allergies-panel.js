/**
 * MediTriaje 2.0 — Panel Asistencial de Alergias (professional-allergies-panel.js)
 * Visualización, registro e inactivación de alergias en atención clínica y prescripción (D3, T4).
 */

import { api } from '../api.js';
import { ui, esc } from '../ui.js';

function formatFecha(iso) {
  if (!iso) return '—';
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'short',
      year: 'numeric'
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

function severidadBadge(severidad) {
  switch (severidad) {
    case 'GRAVE':
      return `<span class="badge badge--cancelled font-semibold">Grave</span>`;
    case 'MODERADA':
      return `<span class="badge badge--rescheduled font-semibold">Moderada</span>`;
    case 'LEVE':
      return `<span class="badge badge--scheduled">Leve</span>`;
    default:
      return `<span class="badge">${esc(severidad || '')}</span>`;
  }
}

/**
 * Renderiza el panel de alergias de un paciente en un elemento contenedor del DOM.
 * @param {HTMLElement} targetEl Contenedor donde se insertará el panel
 * @param {Object} options Opciones del panel: { patientPublicId, atencionPublicId, onUpdate }
 */
export async function renderProfessionalAllergiesPanel(targetEl, { patientPublicId, atencionPublicId, onUpdate } = {}) {
  if (!targetEl || !patientPublicId) return;

  targetEl.innerHTML = `<div class="p-3 text-center text-muted text-xs">Cargando alergias e hipersensibilidades...</div>`;

  try {
    const alergias = await api.get(`/clinical/patients/${patientPublicId}/allergies`, { includeInactive: true });
    renderContent(targetEl, alergias, { patientPublicId, atencionPublicId, onUpdate });
  } catch (err) {
    targetEl.innerHTML = `
      <div class="alert alert--danger p-3 text-xs mb-3">
        <span>No se pudieron cargar las alergias: ${esc(err.message)}</span>
      </div>`;
  }
}

function renderContent(targetEl, alergias, { patientPublicId, atencionPublicId, onUpdate }) {
  const activas = alergias.filter(a => a.estado === 'ACTIVA');
  const inactivas = alergias.filter(a => a.estado === 'INACTIVA');

  targetEl.innerHTML = `
    <div class="card mb-4" style="border: 1px solid var(--border); box-shadow: var(--shadow-sm);">
      <div class="card-header flex flex-wrap items-center justify-between gap-2 py-3 px-4" style="background: var(--surface-2);">
        <div class="flex items-center gap-2">
          ${ui.icon('alert-circle', 'icon icon--sm text-danger')}
          <h3 class="card-title text-base font-bold m-0">Alergias e Hipersensibilidades</h3>
          <span class="badge ${activas.length > 0 ? 'badge--danger' : 'badge--confirmed'} text-xs">
            ${activas.length} ${activas.length === 1 ? 'activa' : 'activas'}
          </span>
        </div>
        <button type="button" class="btn btn-secondary btn--sm btn-add-allergy">
          ${ui.icon('plus', 'icon icon--sm')}
          <span>Registrar alergia</span>
        </button>
      </div>

      <div class="card-body p-4">
        ${activas.length === 0 ? `
          <div class="alert alert--info p-3 mb-0" role="status">
            ${ui.icon('info', 'icon alert-icon')}
            <div class="alert-content">
              <p class="m-0 text-sm font-medium">Sin alergias registradas (esto no confirma que no tenga)</p>
            </div>
          </div>
        ` : `
          <div class="flex flex-col gap-2">
            ${activas.map(a => `
              <div class="p-3 flex flex-wrap items-center justify-between gap-2" style="background: var(--surface); border: 1px solid var(--border); border-left: 4px solid ${a.severidad === 'GRAVE' ? 'var(--danger)' : 'var(--warning)'}; border-radius: var(--radius-md);">
                <div style="flex: 1; min-width: 220px;">
                  <div class="flex flex-wrap items-center gap-2 mb-1">
                    <strong class="text-sm font-bold text-danger">${esc(a.sustancia)}</strong>
                    ${severidadBadge(a.severidad)}
                    ${a.autorreportada ? `<span class="badge badge--scheduled text-xs">Autorreportada</span>` : `<span class="badge badge--confirmed text-xs">Diagnóstico médico</span>`}
                  </div>
                  ${a.reaccion ? `<div class="text-xs text-muted"><strong>Reacción:</strong> ${esc(a.reaccion)}</div>` : ''}
                </div>
                <div>
                  <button type="button" class="btn btn-ghost btn--sm btn-deactivate-allergy text-xs" data-id="${esc(a.publicId)}" data-sustancia="${esc(a.sustancia)}">
                    <span>Inactivar</span>
                  </button>
                </div>
              </div>
            `).join('')}
          </div>
        `}

        ${inactivas.length > 0 ? `
          <details class="mt-3 text-xs text-muted">
            <summary style="cursor: pointer; font-weight: 500;">Ver historial de alergias inactivadas (${inactivas.length})</summary>
            <div class="mt-2 flex flex-col gap-2">
              ${inactivas.map(a => `
                <div class="p-2" style="background: var(--surface-2); border-radius: var(--radius-sm);">
                  <div class="flex items-center gap-2">
                    <span class="font-medium line-through">${esc(a.sustancia)}</span>
                    <span class="badge text-xs">Inactiva</span>
                    ${a.autorreportada ? `<span class="badge badge--scheduled text-xs">Autorreportada</span>` : ''}
                  </div>
                  ${a.motivoInactivacion ? `<div class="text-xs mt-1"><strong>Motivo:</strong> ${esc(a.motivoInactivacion)}</div>` : ''}
                  <div class="text-xs text-muted">Inactivada: ${formatFecha(a.fechaInactivacion)}</div>
                </div>
              `).join('')}
            </div>
          </details>
        ` : ''}
      </div>
    </div>
  `;

  // Listener para agregar alergia
  targetEl.querySelector('.btn-add-allergy')?.addEventListener('click', () => {
    mostrarModalRegistrarAlergia(targetEl, { patientPublicId, atencionPublicId, onUpdate });
  });

  // Listener para inactivar
  targetEl.querySelectorAll('.btn-deactivate-allergy').forEach(btn => {
    btn.addEventListener('click', () => {
      mostrarModalInactivarAlergia(targetEl, btn.dataset.id, btn.dataset.sustancia, { patientPublicId, atencionPublicId, onUpdate });
    });
  });
}

function mostrarModalRegistrarAlergia(targetEl, { patientPublicId, atencionPublicId, onUpdate }) {
  const modalHtml = `
    <form id="formClinicoAlergia" class="flex flex-col gap-3">
      <div class="form-group m-0">
        <label for="f-modal-sustancia" class="form-label text-xs">Sustancia o Medicamento *</label>
        <input type="text" id="f-modal-sustancia" class="form-input" placeholder="Ej. Penicilina, Dipirona, Latex" minlength="2" maxlength="100" required autofocus>
      </div>
      <div class="form-group m-0">
        <label for="f-modal-severidad" class="form-label text-xs">Severidad clínica *</label>
        <select id="f-modal-severidad" class="form-select" required>
          <option value="LEVE">Leve (eritema local, prurito leve sin compromiso sistémico)</option>
          <option value="MODERADA" selected>Moderada (urticaria generalizada, angioedema sin disnea)</option>
          <option value="GRAVE">Grave (anafilaxia, hipotensión, compromiso de vía aérea)</option>
        </select>
      </div>
      <div class="form-group m-0">
        <label for="f-modal-reaccion" class="form-label text-xs">Reacción observada / reportada (opcional)</label>
        <textarea id="f-modal-reaccion" class="form-input" rows="2" maxlength="200" placeholder="Ej. Broncoespasmo agudo, edema facial"></textarea>
      </div>
      <p id="f-modal-error" class="text-xs text-danger m-0" role="alert" style="display:none;"></p>
    </form>
  `;

  ui.showModal({
    title: 'Registrar Alergia / Hipersensibilidad',
    body: modalHtml,
    confirmText: 'Registrar alergia',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      const sustancia = document.getElementById('f-modal-sustancia')?.value.trim();
      const severidad = document.getElementById('f-modal-severidad')?.value;
      const reaccion = document.getElementById('f-modal-reaccion')?.value.trim();
      const errBox = document.getElementById('f-modal-error');

      if (!sustancia || sustancia.length < 2) {
        if (errBox) { errBox.textContent = 'La sustancia debe tener al menos 2 caracteres.'; errBox.style.display = 'block'; }
        return false;
      }

      try {
        await api.post(`/clinical/patients/${patientPublicId}/allergies`, {
          sustancia,
          severidad,
          reaccion: reaccion || null,
          atencionPublicId: atencionPublicId || null
        });
        ui.showToast('Alergia clínica registrada correctamente.', 'success');
        await renderProfessionalAllergiesPanel(targetEl, { patientPublicId, atencionPublicId, onUpdate });
        if (typeof onUpdate === 'function') onUpdate();
        return true;
      } catch (err) {
        if (errBox) { errBox.textContent = err.message; errBox.style.display = 'block'; }
        return false;
      }
    }
  });
}

function mostrarModalInactivarAlergia(targetEl, publicId, sustancia, { patientPublicId, atencionPublicId, onUpdate }) {
  const modalHtml = `
    <form id="formClinicoInactivar" class="flex flex-col gap-3">
      <p class="text-sm text-muted m-0">
        Indica el motivo clínico de inactivación para la alergia a <strong>${esc(sustancia)}</strong>.
        Por inmutabilidad legal, el registro no se elimina de la historia clínica.
      </p>
      <div class="form-group m-0">
        <label for="f-inact-motivo" class="form-label text-xs">Motivo de inactivación *</label>
        <textarea id="f-inact-motivo" class="form-input" rows="3" minlength="5" maxlength="500" placeholder="Ej. Prueba de provocación controlada negativa, error de registro previo" required autofocus></textarea>
      </div>
      <p id="f-inact-error" class="text-xs text-danger m-0" role="alert" style="display:none;"></p>
    </form>
  `;

  ui.showModal({
    title: 'Inactivar Registro de Alergia',
    body: modalHtml,
    confirmText: 'Inactivar',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      const motivo = document.getElementById('f-inact-motivo')?.value.trim();
      const errBox = document.getElementById('f-inact-error');

      if (!motivo || motivo.length < 5) {
        if (errBox) { errBox.textContent = 'El motivo de inactivación debe tener al menos 5 caracteres.'; errBox.style.display = 'block'; }
        return false;
      }

      try {
        await api.patch(`/clinical/allergies/${publicId}/deactivate`, { motivo });
        ui.showToast('Alergia inactivada correctamente.', 'info');
        await renderProfessionalAllergiesPanel(targetEl, { patientPublicId, atencionPublicId, onUpdate });
        if (typeof onUpdate === 'function') onUpdate();
        return true;
      } catch (err) {
        if (errBox) { errBox.textContent = err.message; errBox.style.display = 'block'; }
        return false;
      }
    }
  });
}
