/**
 * MediTriaje 2.0 — Gestión de Alergias del Paciente (patient-allergies.js)
 * Declaración de alergias autorreportadas e historial con trazabilidad clínica (D3, T4).
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
      return `<span class="badge badge--cancelled font-semibold">Severidad Grave</span>`;
    case 'MODERADA':
      return `<span class="badge badge--rescheduled font-semibold">Severidad Moderada</span>`;
    case 'LEVE':
      return `<span class="badge badge--scheduled">Severidad Leve</span>`;
    default:
      return `<span class="badge">${esc(severidad || '')}</span>`;
  }
}

export async function patientAllergiesView(container) {
  ui.renderLoading(container, 'Cargando tus alergias e hipersensibilidades...');

  try {
    const alergias = await api.get('/patients/me/allergies');
    renderView(container, alergias);
  } catch (err) {
    ui.renderError(container, {
      title: 'No fue posible cargar tus alergias',
      message: err.message,
      onRetry: () => patientAllergiesView(container)
    });
  }
}

function renderView(container, alergias) {
  const activas = alergias.filter(a => a.estado === 'ACTIVA');
  const inactivas = alergias.filter(a => a.estado === 'INACTIVA');

  container.innerHTML = `
    <div class="sg-section" style="max-width: 52rem; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-6">
        <div>
          <div class="flex items-center gap-2 mb-1">
            <a href="#/patient/dashboard" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver al panel">${ui.icon('arrow-left')}</a>
            <h1 class="text-2xl font-bold m-0">Mis Alergias e Hipersensibilidades</h1>
          </div>
          <p class="text-sm text-muted m-0">Informa a tu equipo de salud sobre reacciones a medicamentos o sustancias.</p>
        </div>
        <button type="button" id="btnNuevaAlergia" class="btn btn-primary btn--sm">
          ${ui.icon('plus', 'icon icon--sm')}
          <span>Declarar alergia</span>
        </button>
      </div>

      <div class="alert alert--info mb-6" role="note">
        ${ui.icon('info', 'icon alert-icon')}
        <div class="alert-content">
          <p class="text-xs m-0">
            <strong>Transparencia clínica:</strong> Las alergias que declares quedan marcadas como <em>autorreportadas</em> hasta que sean valoradas en consulta médica. Las alergias registradas por el personal de salud solo pueden ser modificadas por un profesional.
          </p>
        </div>
      </div>

      <!-- Alergias Activas -->
      <div class="card mb-6">
        <div class="card-header flex items-center justify-between">
          <h2 class="card-title text-lg flex items-center gap-2">
            ${ui.icon('activity', 'icon icon--sm text-primary')}
            <span>Alergias Activas (${activas.length})</span>
          </h2>
        </div>
        <div class="card-body">
          ${activas.length === 0 ? `
            <div class="p-4 text-center text-muted text-sm bg-surface-2 rounded-md">
              Sin alergias registradas (esto no confirma que no tenga). Puedes declarar una con el botón superior.
            </div>
          ` : `
            <div class="flex flex-col gap-3">
              ${activas.map(a => `
                <div class="card p-4 flex flex-wrap items-start justify-between gap-3" style="border-left: 4px solid ${a.severidad === 'GRAVE' ? 'var(--danger)' : 'var(--primary)'};">
                  <div style="flex: 1; min-width: 240px;">
                    <div class="flex flex-wrap items-center gap-2 mb-1">
                      <strong class="text-base font-bold">${esc(a.sustancia)}</strong>
                      ${severidadBadge(a.severidad)}
                      ${a.autorreportada ? '<span class="badge badge--scheduled text-xs">Autorreportada</span>' : '<span class="badge badge--confirmed text-xs">Diagnóstico médico</span>'}
                    </div>
                    ${a.reaccion ? `<p class="text-sm text-muted m-0 mb-1"><strong>Reacción:</strong> ${esc(a.reaccion)}</p>` : ''}
                    <p class="text-xs text-muted m-0">Registrada el ${formatFecha(a.createdAt)}</p>
                  </div>
                  <div>
                    ${a.autorreportada ? `
                      <button type="button" class="btn btn-secondary btn--sm btn-inactivar" data-id="${esc(a.publicId)}" data-sustancia="${esc(a.sustancia)}">
                        <span>Inactivar</span>
                      </button>
                    ` : `
                      <span class="text-xs text-muted italic" title="Gestionada por personal asistencial">Médica</span>
                    `}
                  </div>
                </div>
              `).join('')}
            </div>
          `}
        </div>
      </div>

      <!-- Alergias Inactivas -->
      ${inactivas.length > 0 ? `
        <details class="card mb-6">
          <summary class="card-title text-base font-semibold" style="cursor: pointer; padding: var(--space-4);">
            Historial de alergias inactivadas (${inactivas.length})
          </summary>
          <div class="card-body">
            <div class="flex flex-col gap-3">
              ${inactivas.map(a => `
                <div class="card p-3 opacity-75" style="background: var(--surface-2);">
                  <div class="flex flex-wrap items-center gap-2 mb-1">
                    <span class="font-bold text-sm line-through">${esc(a.sustancia)}</span>
                    <span class="badge text-xs">Inactiva</span>
                    ${a.autorreportada ? '<span class="badge badge--scheduled text-xs">Autorreportada</span>' : ''}
                  </div>
                  ${a.motivoInactivacion ? `<p class="text-xs text-muted m-0"><strong>Motivo inactivación:</strong> ${esc(a.motivoInactivacion)}</p>` : ''}
                  <p class="text-xs text-muted m-0 mt-1">Inactivada el ${formatFecha(a.fechaInactivacion)}</p>
                </div>
              `).join('')}
            </div>
          </div>
        </details>
      ` : ''}
    </div>
  `;

  // Event listener para declarar alergia
  container.querySelector('#btnNuevaAlergia')?.addEventListener('click', () => {
    mostrarModalDeclararAlergia(container);
  });

  // Event listener para inactivar
  container.querySelectorAll('.btn-inactivar').forEach(btn => {
    btn.addEventListener('click', () => {
      const publicId = btn.dataset.id;
      const sustancia = btn.dataset.sustancia;
      mostrarModalInactivarAlergia(container, publicId, sustancia);
    });
  });
}

function mostrarModalDeclararAlergia(container) {
  const modalHtml = `
    <form id="formDeclararAlergia" class="flex flex-col gap-3">
      <div class="form-group m-0">
        <label for="modal-sustancia" class="form-label text-xs">Sustancia o Medicamento *</label>
        <input type="text" id="modal-sustancia" class="form-input" placeholder="Ej. Penicilina, Ibuprofeno, Mariscos" minlength="2" maxlength="100" required autofocus>
      </div>
      <div class="form-group m-0">
        <label for="modal-severidad" class="form-label text-xs">Severidad percibida *</label>
        <select id="modal-severidad" class="form-select" required>
          <option value="LEVE">Leve (erupción leve, molestia digestiva leve)</option>
          <option value="MODERADA" selected>Moderada (urticaria extensa, hinchazón moderada)</option>
          <option value="GRAVE">Grave (dificultad respiratoria, anafilaxia previa)</option>
        </select>
      </div>
      <div class="form-group m-0">
        <label for="modal-reaccion" class="form-label text-xs">Reacción o síntomas experimentados (opcional)</label>
        <textarea id="modal-reaccion" class="form-input" rows="2" maxlength="200" placeholder="Ej. Salpullido en la piel, inflamación de labios"></textarea>
      </div>
      <p id="modalError" class="text-xs text-danger m-0" role="alert" style="display:none;"></p>
    </form>
  `;

  ui.showModal({
    title: 'Declarar alergia autorreportada',
    body: modalHtml,
    confirmText: 'Guardar alergia',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      const form = document.getElementById('formDeclararAlergia');
      const sustancia = document.getElementById('modal-sustancia')?.value.trim();
      const severidad = document.getElementById('modal-severidad')?.value;
      const reaccion = document.getElementById('modal-reaccion')?.value.trim();
      const errBox = document.getElementById('modalError');

      if (!sustancia || sustancia.length < 2) {
        if (errBox) { errBox.textContent = 'La sustancia debe tener al menos 2 caracteres.'; errBox.style.display = 'block'; }
        return false;
      }

      try {
        await api.post('/patients/me/allergies', {
          sustancia,
          severidad,
          reaccion: reaccion || null
        });
        ui.showToast('Alergia declarada correctamente.', 'success');
        patientAllergiesView(container);
        return true;
      } catch (err) {
        if (errBox) { errBox.textContent = err.message; errBox.style.display = 'block'; }
        return false;
      }
    }
  });
}

function mostrarModalInactivarAlergia(container, publicId, sustancia) {
  const modalHtml = `
    <form id="formInactivarAlergia" class="flex flex-col gap-3">
      <p class="text-sm text-muted m-0">
        Indica el motivo por el cual deseas marcar como inactiva la alergia a <strong>${esc(sustancia)}</strong>.
        Por trazabilidad clínica, el registro no se borra, sino que se inactiva.
      </p>
      <div class="form-group m-0">
        <label for="modal-motivo" class="form-label text-xs">Motivo de inactivación *</label>
        <textarea id="modal-motivo" class="form-input" rows="3" minlength="5" maxlength="500" placeholder="Ej. Prueba de alergia posterior negativa, diagnóstico corregido" required autofocus></textarea>
      </div>
      <p id="modalInactError" class="text-xs text-danger m-0" role="alert" style="display:none;"></p>
    </form>
  `;

  ui.showModal({
    title: 'Inactivar alergia autorreportada',
    body: modalHtml,
    confirmText: 'Inactivar',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      const motivo = document.getElementById('modal-motivo')?.value.trim();
      const errBox = document.getElementById('modalInactError');

      if (!motivo || motivo.length < 5) {
        if (errBox) { errBox.textContent = 'El motivo de inactivación debe tener al menos 5 caracteres.'; errBox.style.display = 'block'; }
        return false;
      }

      try {
        await api.patch(`/patients/me/allergies/${publicId}/deactivate`, { motivo });
        ui.showToast('Alergia inactivada correctamente.', 'info');
        patientAllergiesView(container);
        return true;
      } catch (err) {
        if (errBox) { errBox.textContent = err.message; errBox.style.display = 'block'; }
        return false;
      }
    }
  });
}
