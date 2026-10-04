/**
 * MediTriaje 2.0 — Formulario de Atención Clínica del Profesional (professional-attention.js)
 * Secciones colapsables (signos vitales, evolución, diagnóstico, indicaciones), cierre irreversible
 * con confirmación explícita y enmiendas append-only (M8.3, HU-07, ADR-008).
 */

import { api } from '../api.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

function formatDateTime(iso) {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('es-CO', {
    timeZone: 'America/Bogota', day: 'numeric', month: 'long', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: true
  }).format(new Date(iso));
}

function tipoBadge(tipo) {
  switch (tipo) {
    case 'CONTROL_MEDICO':
      return `<span class="badge badge--scheduled">${ui.icon('calendar', 'icon icon--sm')} Control Médico</span>`;
    case 'EVOLUCION_SINTOMAS':
      return `<span class="badge badge--confirmed">${ui.icon('activity', 'icon icon--sm')} Evolución de Síntomas</span>`;
    case 'EXAMEN_PENDIENTE':
      return `<span class="badge badge--rescheduled">${ui.icon('file-text', 'icon icon--sm')} Examen Pendiente</span>`;
    case 'ADHERENCIA_TRATAMIENTO':
      return `<span class="badge badge--attended">${ui.icon('pill', 'icon icon--sm')} Adherencia a Tratamiento</span>`;
    default:
      return `<span class="badge badge--scheduled">${esc(tipo || '')}</span>`;
  }
}

function estadoBadge(estado) {
  switch (estado) {
    case 'PENDIENTE':
      return `<span class="badge badge--scheduled">Pendiente</span>`;
    case 'COMPLETADO':
      return `<span class="badge badge--confirmed">${ui.icon('check', 'icon icon--sm')} Completado</span>`;
    case 'CANCELADO':
      return `<span class="badge badge--cancelled">Cancelado</span>`;
    default:
      return `<span class="badge">${esc(estado || '')}</span>`;
  }
}

/** Convierte un valor de input a número o null. */
function num(value) {
  if (value === undefined || value === null || String(value).trim() === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

const VITALS = [
  { key: 'presionSistolica', label: 'Presión sistólica (mmHg)', min: 40, max: 300, step: 1 },
  { key: 'presionDiastolica', label: 'Presión diastólica (mmHg)', min: 20, max: 200, step: 1 },
  { key: 'frecuenciaCardiaca', label: 'Frec. cardíaca (lpm)', min: 30, max: 250, step: 1 },
  { key: 'frecuenciaRespiratoria', label: 'Frec. respiratoria (rpm)', min: 5, max: 60, step: 1 },
  { key: 'temperatura', label: 'Temperatura (°C)', min: 30, max: 45, step: 0.1 },
  { key: 'saturacionOxigeno', label: 'Saturación O₂ (%)', min: 50, max: 100, step: 1 },
  { key: 'pesoKg', label: 'Peso (kg)', min: 0.5, max: 500, step: 0.1 },
  { key: 'tallaCm', label: 'Talla (cm)', min: 20, max: 260, step: 0.1 }
];

export async function professionalAttentionView(container, { params }) {
  ui.renderLoading(container, 'Cargando atención...');
  try {
    const atencion = await api.get(`/attentions/${params.id}`);
    if (atencion.estado === 'ABIERTA') {
      renderOpenForm(container, atencion);
    } else {
      renderClosed(container, atencion);
    }
  } catch (err) {
    ui.renderError(container, {
      title: 'No fue posible abrir la atención',
      message: err.message,
      onRetry: () => professionalAttentionView(container, { params })
    });
  }
}

function header(atencion, badge) {
  return `
    <div class="flex flex-wrap items-center justify-between gap-3 mb-6">
      <div>
        <div class="flex items-center gap-2 mb-1">
          <a href="#/professional/agenda" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver a la agenda">${ui.icon('arrow-left')}</a>
          <h1 class="text-2xl font-bold m-0">Atención clínica</h1>
        </div>
        <p class="text-sm text-muted m-0">Paciente: <strong>${esc(atencion.pacienteNombre)}</strong> · ${esc(atencion.especialidadNombre || '')}</p>
      </div>
      ${badge}
    </div>`;
}

/* ------------------------------------------------------------------ */
/* Atención ABIERTA: formulario editable                               */
/* ------------------------------------------------------------------ */
function renderOpenForm(container, atencion) {
  const state = { cie10: null };

  container.innerHTML = `
    <div style="max-width: 52rem; margin: 0 auto; padding-bottom: var(--space-12);">
      ${header(atencion, `<span class="badge badge--confirmed">${ui.icon('activity', 'icon icon--sm')} Abierta</span>`)}

      <form id="attentionForm" novalidate>
        <details class="card mb-4" open>
          <summary class="card-title text-lg font-semibold" style="cursor: pointer; padding: var(--space-4);">Signos vitales (opcional)</summary>
          <div class="card-body">
            <div class="vitals-grid" style="grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));">
              ${VITALS.map(v => `
                <div class="form-group m-0">
                  <label for="v-${v.key}" class="form-label text-xs">${v.label}</label>
                  <input type="number" id="v-${v.key}" class="form-input" min="${v.min}" max="${v.max}" step="${v.step}" inputmode="decimal">
                </div>`).join('')}
            </div>
            <p id="vitalsError" class="text-xs text-danger m-0 mt-2" role="alert"></p>
          </div>
        </details>

        <details class="card mb-4" open>
          <summary class="card-title text-lg font-semibold" style="cursor: pointer; padding: var(--space-4);">Motivo y evolución</summary>
          <div class="card-body">
            <div class="form-group">
              <label for="f-motivo" class="form-label">Motivo de consulta *</label>
              <textarea id="f-motivo" class="form-input" rows="2" maxlength="500"></textarea>
            </div>
            <div class="form-group m-0">
              <label for="f-evolucion" class="form-label">Evolución clínica *</label>
              <textarea id="f-evolucion" class="form-input" rows="6" maxlength="4000"></textarea>
              <span id="evolCount" class="text-xs text-muted">0 / 4000</span>
            </div>
          </div>
        </details>

        <details class="card mb-4" open>
          <summary class="card-title text-lg font-semibold" style="cursor: pointer; padding: var(--space-4);">Diagnóstico CIE-10 *</summary>
          <div class="card-body">
            <div class="form-group">
              <label for="f-cie10" class="form-label">Buscar por código o descripción</label>
              <input type="search" id="f-cie10" class="form-input" autocomplete="off" placeholder="Ej. J00, resfriado, hipertensión">
            </div>
            <div id="cie10Results" class="flex flex-col gap-1 mb-3" style="max-height: 14rem; overflow-y: auto;"></div>
            <div id="cie10Selected" class="p-3" style="display:none; background: var(--teal-50); border-left: 4px solid var(--primary); border-radius: var(--radius-md);"></div>
          </div>
        </details>

        <details class="card mb-6" open>
          <summary class="card-title text-lg font-semibold" style="cursor: pointer; padding: var(--space-4);">Indicaciones *</summary>
          <div class="card-body">
            <div class="form-group m-0">
              <label for="f-indicaciones" class="form-label">Indicaciones médicas al paciente</label>
              <textarea id="f-indicaciones" class="form-input" rows="3" maxlength="1000"></textarea>
            </div>
          </div>
        </details>

        <div class="alert alert--warning mb-4" role="note">
          ${ui.icon('alert-triangle', 'icon alert-icon')}
          <div class="alert-content"><p class="m-0 text-sm">Al cerrar la atención, el registro queda <strong>inmutable</strong>. Solo podrá aclararse con enmiendas.</p></div>
        </div>
        <p id="formError" class="text-sm text-danger" role="alert"></p>

        <div class="flex justify-end">
          <button type="submit" id="btnClose" class="btn btn-primary btn--lg">${ui.icon('check')}<span>Cerrar atención</span></button>
        </div>
      </form>
    </div>
  `;

  const $ = (sel) => container.querySelector(sel);
  const evol = $('#f-evolucion');
  evol.addEventListener('input', () => { $('#evolCount').textContent = `${evol.value.length} / 4000`; });

  // Búsqueda CIE-10 con debounce
  let timer;
  $('#f-cie10').addEventListener('input', (e) => {
    clearTimeout(timer);
    const q = e.target.value.trim();
    timer = setTimeout(() => searchCie10(q), 250);
  });

  async function searchCie10(q) {
    const box = $('#cie10Results');
    if (q.length < 2) { box.innerHTML = ''; return; }
    try {
      const list = await api.get('/catalogs/icd10', { q });
      box.innerHTML = list.length === 0
        ? '<span class="text-sm text-muted">Sin resultados.</span>'
        : list.slice(0, 30).map(d => `
            <button type="button" class="btn btn-secondary btn--sm cie-opt" data-code="${esc(d.codigo)}" data-desc="${esc(d.descripcion)}" style="justify-content:flex-start; text-align:left;">
              <strong>${esc(d.codigo)}</strong>&nbsp;— ${esc(d.descripcion)}
            </button>`).join('');
      box.querySelectorAll('.cie-opt').forEach(b => b.addEventListener('click', () => {
        state.cie10 = { codigo: b.dataset.code, descripcion: b.dataset.desc };
        const sel = $('#cie10Selected');
        sel.style.display = 'block';
        sel.innerHTML = `<strong>${esc(state.cie10.codigo)}</strong> — ${esc(state.cie10.descripcion)}`;
        box.innerHTML = '';
      }));
    } catch (err) {
      box.innerHTML = `<span class="text-sm text-danger">${esc(err.message)}</span>`;
    }
  }

  function collectVitals() {
    const out = {};
    let any = false;
    VITALS.forEach(v => {
      const n = num($(`#v-${v.key}`).value);
      if (n !== null) { out[v.key] = n; any = true; }
    });
    return any ? out : null;
  }

  $('#attentionForm').addEventListener('submit', (e) => {
    e.preventDefault();
    $('#vitalsError').textContent = '';
    $('#formError').textContent = '';

    const vitals = collectVitals();
    if (vitals?.presionSistolica && vitals?.presionDiastolica && vitals.presionSistolica <= vitals.presionDiastolica) {
      $('#vitalsError').textContent = 'La presión sistólica debe ser mayor que la diastólica.';
      return;
    }
    for (const v of VITALS) {
      const n = vitals?.[v.key];
      if (n !== undefined && (n < v.min || n > v.max)) {
        $('#vitalsError').textContent = `${v.label}: valor fuera de rango (${v.min}–${v.max}).`;
        return;
      }
    }

    const body = {
      diagnosticoCie10Codigo: state.cie10?.codigo || '',
      motivoConsulta: $('#f-motivo').value.trim(),
      evolucion: evol.value.trim(),
      indicaciones: $('#f-indicaciones').value.trim(),
      signosVitales: vitals
    };
    const missing = [];
    if (!body.motivoConsulta) missing.push('motivo de consulta');
    if (!body.evolucion) missing.push('evolución clínica');
    if (!body.diagnosticoCie10Codigo) missing.push('diagnóstico CIE-10');
    if (!body.indicaciones) missing.push('indicaciones');
    if (missing.length) {
      $('#formError').textContent = `Completa: ${missing.join(', ')}.`;
      return;
    }

    ui.showModal({
      title: '¿Cerrar esta atención?',
      message: `<p>Esta acción es <strong>irreversible</strong>: el registro clínico quedará inmutable y la cita pasará a <em>Atendida</em>. Solo podrás agregar enmiendas.</p>
                <p class="m-0 text-sm text-muted">Diagnóstico: <strong>${esc(state.cie10.codigo)}</strong> — ${esc(state.cie10.descripcion)}</p>`,
      confirmText: 'Sí, cerrar atención',
      cancelText: 'Revisar de nuevo',
      onConfirm: async () => {
        const btn = $('#btnClose');
        ui.setButtonLoading(btn, true);
        try {
          const cerrada = await api.post(`/attentions/${atencion.publicId}/close`, body);
          ui.showToast('Atención cerrada correctamente.', 'success');
          renderClosed(container, cerrada);
          window.scrollTo(0, 0);
        } catch (err) {
          ui.setButtonLoading(btn, false);
          $('#formError').textContent = err.message || 'No fue posible cerrar la atención.';
        }
      }
    });
  });
}

/* ------------------------------------------------------------------ */
/* Atención CERRADA: solo lectura + enmiendas + receta                 */
/* ------------------------------------------------------------------ */
function renderClosed(container, atencion) {
  const s = atencion.signosVitales;
  const enmiendas = atencion.enmiendas || [];

  container.innerHTML = `
    <div style="max-width: 52rem; margin: 0 auto; padding-bottom: var(--space-12);">
      ${header(atencion, `<span class="badge badge--attended">${ui.icon('shield', 'icon icon--sm')} Cerrada · inmutable</span>`)}

      <div class="card p-6 mb-6" style="border-top: 4px solid var(--primary);">
        <p class="text-xs text-muted m-0 mb-3">Cerrada el ${formatDateTime(atencion.fechaCierre)}</p>
        <div class="p-3 mb-4" style="background: var(--teal-50); border-left: 4px solid var(--primary); border-radius: var(--radius-md);">
          <span class="text-xs text-muted font-bold uppercase block">Diagnóstico CIE-10</span>
          <strong class="text-primary">${esc(atencion.diagnosticoCodigo)} — ${esc(atencion.diagnosticoDescripcion)}</strong>
        </div>
        <h2 class="text-sm font-bold uppercase text-muted mb-1">Motivo de consulta</h2>
        <p>${esc(atencion.motivoConsulta)}</p>
        <h2 class="text-sm font-bold uppercase text-muted mb-1">Evolución clínica</h2>
        <p style="white-space: pre-wrap;">${esc(atencion.evolucion)}</p>
        <h2 class="text-sm font-bold uppercase text-muted mb-1">Indicaciones</h2>
        <p class="m-0" style="white-space: pre-wrap;">${esc(atencion.indicaciones)}</p>
        ${s ? `
          <div class="vitals-grid mt-4">
            ${VITALS.filter(v => s[v.key] != null).map(v => `
              <div class="vital-card"><span class="vital-card-label">${v.label}</span><span class="vital-card-val">${esc(s[v.key])}</span></div>`).join('')}
          </div>` : ''}
      </div>

      <div class="flex flex-wrap gap-3 mb-8">
        <a href="#/professional/prescription/${esc(atencion.publicId)}" class="btn btn-primary">${ui.icon('pill')}<span>Crear receta</span></a>
        <a href="#/professional/agenda" class="btn btn-secondary">Volver a la agenda</a>
      </div>

      <!-- Plan de Seguimiento Post-Atención (F2.2.4) -->
      <section class="mb-8" id="followUpSection">
        <h2 class="text-lg font-bold mb-3 flex items-center justify-between">
          <span>Plan de Seguimiento Post-Atención</span>
          <span id="followUpCount" class="text-sm font-normal text-muted"></span>
        </h2>
        <div id="followUpList" class="flex flex-col gap-3 mb-4">
          <div class="skeleton skeleton-card" style="height: 80px;"></div>
        </div>

        <details class="card" id="prescribeFollowUpCard">
          <summary class="card-title text-md font-semibold" style="cursor: pointer; padding: var(--space-4);">Prescribir tarea de seguimiento</summary>
          <div class="card-body">
            <form id="followUpForm" novalidate>
              <div class="grid grid-cols-1 grid-cols-2-md gap-4 mb-3">
                <div class="form-group m-0">
                  <label for="fu-tipo" class="form-label">Tipo de seguimiento *</label>
                  <select id="fu-tipo" class="form-input">
                    <option value="CONTROL_MEDICO">Control Médico</option>
                    <option value="EVOLUCION_SINTOMAS">Evolución de Síntomas</option>
                    <option value="EXAMEN_PENDIENTE">Examen Pendiente</option>
                    <option value="ADHERENCIA_TRATAMIENTO">Adherencia a Tratamiento</option>
                  </select>
                </div>
                <div class="form-group m-0">
                  <label for="fu-fecha" class="form-label">Fecha sugerida de control (opcional)</label>
                  <input type="date" id="fu-fecha" class="form-input" min="${new Date().toISOString().split('T')[0]}">
                </div>
              </div>
              <div class="form-group">
                <label for="fu-indicaciones" class="form-label">Indicaciones para el paciente *</label>
                <textarea id="fu-indicaciones" class="form-input" rows="3" maxlength="1000" placeholder="Ej: Continuar con el antibiótico por 7 días y reportar si persiste la fiebre o el dolor..."></textarea>
                <span class="text-xs text-muted">Máximo 1000 caracteres.</span>
              </div>
              <p id="fu-error" class="text-sm text-danger" role="alert"></p>
              <div class="flex justify-end">
                <button type="submit" id="btnPrescribeFollowUp" class="btn btn-primary">${ui.icon('check')}<span>Prescribir seguimiento</span></button>
              </div>
            </form>
          </div>
        </details>
      </section>

      <h2 class="text-lg font-bold mb-3">Enmiendas (${enmiendas.length})</h2>
      <div id="amendList" class="flex flex-col gap-3 mb-6">
        ${enmiendas.length === 0 ? '<p class="text-sm text-muted m-0">Sin enmiendas.</p>' : enmiendas.map(amendHtml).join('')}
      </div>

      <details class="card">
        <summary class="card-title text-md font-semibold" style="cursor: pointer; padding: var(--space-4);">Agregar enmienda</summary>
        <div class="card-body">
          <form id="amendForm" novalidate>
            <div class="form-group">
              <label for="a-motivo" class="form-label">Motivo *</label>
              <input id="a-motivo" class="form-input" maxlength="255">
            </div>
            <div class="form-group">
              <label for="a-contenido" class="form-label">Contenido aclaratorio *</label>
              <textarea id="a-contenido" class="form-input" rows="4" maxlength="2000"></textarea>
            </div>
            <p id="amendError" class="text-sm text-danger" role="alert"></p>
            <button type="submit" id="btnAmend" class="btn btn-secondary">Registrar enmienda</button>
          </form>
        </div>
      </details>
    </div>
  `;

  // Lógica de seguimientos post-atención (F2.2.4)
  async function loadFollowUps() {
    const listEl = container.querySelector('#followUpList');
    const countEl = container.querySelector('#followUpCount');
    if (!listEl) return;
    try {
      const items = await api.get(`/attentions/${atencion.publicId}/follow-ups`);
      if (countEl) countEl.textContent = `(${items.length})`;
      if (items.length === 0) {
        listEl.innerHTML = '<p class="text-sm text-muted m-0">Sin tareas de seguimiento prescritas para esta atención.</p>';
      } else {
        listEl.innerHTML = items.map(followUpCardHtml).join('');
        listEl.querySelectorAll('.btn-cancel-followup').forEach(btn => {
          btn.addEventListener('click', () => {
            const publicId = btn.dataset.id;
            ui.showModal({
              title: 'Cancelar seguimiento',
              message: '¿Estás seguro de que deseas cancelar esta tarea de seguimiento?',
              confirmText: 'Sí, cancelar seguimiento',
              cancelText: 'Volver',
              onConfirm: async () => {
                try {
                  await api.patch(`/follow-ups/${publicId}/cancel`);
                  ui.showToast('Seguimiento cancelado.', 'info');
                  await loadFollowUps();
                } catch (err) {
                  ui.showToast(err.message || 'Error al cancelar.', 'danger');
                }
              }
            });
          });
        });
      }
    } catch (err) {
      listEl.innerHTML = `<p class="text-sm text-danger m-0">Error al cargar seguimientos: ${esc(err.message)}</p>`;
    }
  }

  const fuForm = container.querySelector('#followUpForm');
  if (fuForm) {
    fuForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const tipo = container.querySelector('#fu-tipo').value;
      const fechaSugeridaControl = container.querySelector('#fu-fecha').value || null;
      const indicaciones = container.querySelector('#fu-indicaciones').value.trim();
      const errEl = container.querySelector('#fu-error');
      errEl.textContent = '';

      if (!indicaciones) {
        errEl.textContent = 'Las indicaciones para el paciente son obligatorias.';
        return;
      }

      const btn = container.querySelector('#btnPrescribeFollowUp');
      ui.setButtonLoading(btn, true);
      try {
        await api.post(`/attentions/${atencion.publicId}/follow-ups`, {
          tipo,
          indicaciones,
          fechaSugeridaControl
        });
        ui.showToast('Plan de seguimiento prescrito correctamente.', 'success');
        fuForm.reset();
        await loadFollowUps();
      } catch (err) {
        errEl.textContent = err.message || 'No fue posible registrar el seguimiento.';
      } finally {
        ui.setButtonLoading(btn, false);
      }
    });
  }

  loadFollowUps();

  const form = container.querySelector('#amendForm');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const motivo = container.querySelector('#a-motivo').value.trim();
    const contenido = container.querySelector('#a-contenido').value.trim();
    const err = container.querySelector('#amendError');
    err.textContent = '';
    if (!motivo || !contenido) { err.textContent = 'Motivo y contenido son obligatorios.'; return; }
    const btn = container.querySelector('#btnAmend');
    ui.setButtonLoading(btn, true);
    try {
      const enm = await api.post(`/attentions/${atencion.publicId}/amendments`, { motivo, contenido });
      const list = container.querySelector('#amendList');
      if (!list.querySelector('.amend-item')) list.innerHTML = '';
      list.insertAdjacentHTML('beforeend', amendHtml(enm));
      form.reset();
      ui.showToast('Enmienda registrada.', 'success');
    } catch (ex) {
      err.textContent = ex.message || 'No fue posible registrar la enmienda.';
    } finally {
      ui.setButtonLoading(btn, false);
    }
  });
}

function followUpCardHtml(item) {
  const isPending = item.estado === 'PENDIENTE';
  const isCompleted = item.estado === 'COMPLETADO';
  return `
    <div class="card p-4" style="border-left: 4px solid ${isPending ? 'var(--primary)' : (isCompleted ? 'var(--success)' : 'var(--neutral-300)')};">
      <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
        <div class="flex items-center gap-2">
          ${tipoBadge(item.tipo)}
          ${estadoBadge(item.estado)}
        </div>
        <span class="text-xs text-muted">Prescrito el ${formatDateTime(item.createdAt)}</span>
      </div>

      ${item.fechaSugeridaControl ? `
        <div class="text-xs text-primary font-medium mb-2">
          ${ui.icon('calendar', 'icon icon--sm')} Fecha sugerida de control: <strong>${esc(item.fechaSugeridaControl)}</strong>
        </div>
      ` : ''}

      <div class="p-3 mb-2" style="background: var(--surface-2); border-radius: var(--radius-md);">
        <span class="text-xs font-bold text-muted uppercase block mb-1">Indicaciones al paciente</span>
        <p class="text-sm m-0" style="white-space: pre-wrap;">${esc(item.indicaciones)}</p>
      </div>

      ${item.reportePaciente ? `
        <div class="p-3 mt-2" style="background: var(--teal-50); border-left: 3px solid var(--primary); border-radius: var(--radius-sm);">
          <div class="flex items-center justify-between gap-2 mb-1">
            <span class="text-xs font-bold text-primary uppercase">Reporte de evolución del paciente</span>
            <span class="text-xs text-muted">${formatDateTime(item.fechaRespuestaPaciente)}</span>
          </div>
          <p class="text-sm m-0" style="white-space: pre-wrap; color: var(--on-teal-50);">${esc(item.reportePaciente)}</p>
        </div>
      ` : ''}

      ${isPending ? `
        <div class="flex justify-end pt-2">
          <button type="button" class="btn btn-ghost btn--sm text-danger btn-cancel-followup" data-id="${esc(item.publicId)}">
            ${ui.icon('x', 'icon icon--sm')}
            <span>Cancelar seguimiento</span>
          </button>
        </div>
      ` : ''}
    </div>
  `;
}

function amendHtml(enm) {
  return `
    <div class="amend-item p-3" style="background: var(--warning-bg); border-left: 3px solid var(--warning); border-radius: var(--radius-sm); font-size: var(--text-sm);">
      <div class="flex justify-between gap-2 mb-1">
        <strong style="color: var(--on-warning-bg);">${esc(enm.profesionalNombre)}</strong>
        <span class="text-xs text-muted">${formatDateTime(enm.fechaEnmienda)}</span>
      </div>
      <p class="m-0 mb-1" style="color: var(--on-warning-bg);">${esc(enm.contenido)}</p>
      <span class="text-xs text-muted">Motivo: ${esc(enm.motivo)}</span>
    </div>`;
}
