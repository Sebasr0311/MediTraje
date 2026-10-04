/**
 * MediTriaje 2.0 — Emisión de Receta Médica (professional-prescription.js)
 * Búsqueda en catálogo de medicamentos, armado de ítems y emisión atómica
 * sobre una atención cerrada (M8.3, HU-08, ADR-008). El sistema no receta por su cuenta.
 */

import { api } from '../api.js';
import { ui, esc } from '../ui.js';

export async function professionalPrescriptionView(container, { params }) {
  const atencionId = params.atencionId;
  const items = []; // { med, dosis, frecuencia, duracionDias, cantidad, indicaciones }

  container.innerHTML = `
    <div style="max-width: 56rem; margin: 0 auto; padding-bottom: var(--space-12);">
      <div class="flex items-center gap-2 mb-1">
        <a href="#/professional/attention/${esc(atencionId)}" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver a la atención">${ui.icon('arrow-left')}</a>
        <h1 class="text-2xl font-bold m-0">Nueva receta médica</h1>
      </div>
      <p class="text-sm text-muted mb-6">La prescripción es decisión exclusiva del profesional. Una vez emitida, la receta es inmutable.</p>

      <div id="rxBody">
        <div class="card mb-4">
          <div class="card-header"><h2 class="card-title text-lg">1. Buscar medicamento</h2></div>
          <div class="card-body">
            <label for="medSearch" class="form-label">Nombre comercial, principio activo o código</label>
            <input type="search" id="medSearch" class="form-input mb-3" autocomplete="off" placeholder="Ej. acetaminofén">
            <div id="medResults" class="flex flex-col gap-1" style="max-height: 14rem; overflow-y: auto;"></div>
          </div>
        </div>

        <div class="card mb-4">
          <div class="card-header"><h2 class="card-title text-lg">2. Medicamentos de la receta (<span id="itemCount">0</span>)</h2></div>
          <div class="card-body">
            <div id="itemsList"><p class="text-sm text-muted m-0">Aún no has agregado medicamentos.</p></div>
          </div>
        </div>

        <div class="card mb-6">
          <div class="card-header"><h2 class="card-title text-lg">3. Vigencia</h2></div>
          <div class="card-body">
            <div class="form-group m-0" style="max-width: 14rem;">
              <label for="vigencia" class="form-label">Días de vigencia (1–365)</label>
              <input type="number" id="vigencia" class="form-input" min="1" max="365" value="30">
            </div>
          </div>
        </div>

        <p id="rxError" class="text-sm text-danger" role="alert"></p>
        <div class="flex justify-end">
          <button type="button" id="btnEmit" class="btn btn-primary btn--lg">${ui.icon('check')}<span>Emitir receta</span></button>
        </div>
      </div>
    </div>
  `;

  const $ = (s) => container.querySelector(s);
  let timer;

  $('#medSearch').addEventListener('input', (e) => {
    clearTimeout(timer);
    const q = e.target.value.trim();
    timer = setTimeout(() => searchMeds(q), 250);
  });

  async function searchMeds(q) {
    const box = $('#medResults');
    if (q.length < 2) { box.innerHTML = ''; return; }
    try {
      const res = await api.get('/catalogs/medications', { q, page: 0, size: 10 });
      const list = res?.content || [];
      box.innerHTML = list.length === 0
        ? '<span class="text-sm text-muted">Sin resultados.</span>'
        : list.map(m => `
            <button type="button" class="btn btn-secondary btn--sm med-opt" data-id="${esc(m.publicId)}" style="justify-content:flex-start; text-align:left;">
              <strong>${esc(m.nombreComercial)}</strong>&nbsp;· ${esc(m.principioActivo)} ${esc(m.concentracion)} · ${esc(m.presentacion)}
            </button>`).join('');
      box.querySelectorAll('.med-opt').forEach(b => b.addEventListener('click', () => {
        const med = list.find(m => m.publicId === b.dataset.id);
        if (items.some(i => i.med.publicId === med.publicId)) {
          ui.showToast('Ese medicamento ya está en la receta.', 'warning');
          return;
        }
        if (items.length >= 20) {
          ui.showToast('Máximo 20 medicamentos por receta.', 'warning');
          return;
        }
        items.push({ med, dosis: '', frecuencia: '', duracionDias: '', cantidad: '', indicaciones: '' });
        renderItems();
      }));
    } catch (err) {
      box.innerHTML = `<span class="text-sm text-danger">${esc(err.message)}</span>`;
    }
  }

  function renderItems() {
    $('#itemCount').textContent = items.length;
    const list = $('#itemsList');
    if (items.length === 0) {
      list.innerHTML = '<p class="text-sm text-muted m-0">Aún no has agregado medicamentos.</p>';
      return;
    }
    list.innerHTML = items.map((it, i) => `
      <div class="p-4 mb-3" style="border: 1px solid var(--border); border-radius: var(--radius-md);" data-i="${i}">
        <div class="flex items-start justify-between gap-2 mb-3">
          <div>
            <strong>${esc(it.med.nombreComercial)}</strong>
            <span class="text-xs text-muted block">${esc(it.med.principioActivo)} · ${esc(it.med.concentracion)} · ${esc(it.med.presentacion)}</span>
          </div>
          <button type="button" class="btn btn-ghost btn--sm btn-remove" aria-label="Quitar ${esc(it.med.nombreComercial)}">${ui.icon('x')}</button>
        </div>
        <div class="grid grid-cols-1 grid-cols-2-md gap-3">
          <div class="form-group m-0"><label class="form-label text-xs">Dosis *</label><input class="form-input f-dosis" maxlength="100" value="${esc(it.dosis)}"></div>
          <div class="form-group m-0"><label class="form-label text-xs">Frecuencia *</label><input class="form-input f-frecuencia" maxlength="100" value="${esc(it.frecuencia)}"></div>
          <div class="form-group m-0"><label class="form-label text-xs">Duración (días) *</label><input type="number" min="1" class="form-input f-duracion" value="${esc(it.duracionDias)}"></div>
          <div class="form-group m-0"><label class="form-label text-xs">Cantidad (unidades) *</label><input type="number" min="1" class="form-input f-cantidad" value="${esc(it.cantidad)}"></div>
        </div>
        <div class="form-group m-0 mt-3"><label class="form-label text-xs">Indicaciones</label><input class="form-input f-indic" maxlength="300" value="${esc(it.indicaciones)}"></div>
      </div>`).join('');

    list.querySelectorAll('[data-i]').forEach(row => {
      const it = items[Number(row.dataset.i)];
      row.querySelector('.f-dosis').addEventListener('input', e => { it.dosis = e.target.value; });
      row.querySelector('.f-frecuencia').addEventListener('input', e => { it.frecuencia = e.target.value; });
      row.querySelector('.f-duracion').addEventListener('input', e => { it.duracionDias = e.target.value; });
      row.querySelector('.f-cantidad').addEventListener('input', e => { it.cantidad = e.target.value; });
      row.querySelector('.f-indic').addEventListener('input', e => { it.indicaciones = e.target.value; });
      row.querySelector('.btn-remove').addEventListener('click', () => {
        items.splice(Number(row.dataset.i), 1);
        renderItems();
      });
    });
  }

  $('#btnEmit').addEventListener('click', () => {
    const err = $('#rxError');
    err.textContent = '';

    if (items.length === 0) { err.textContent = 'Agrega al menos un medicamento.'; return; }
    for (const it of items) {
      const dur = Number(it.duracionDias), cant = Number(it.cantidad);
      if (!it.dosis.trim() || !it.frecuencia.trim() || !(dur >= 1) || !(cant >= 1)) {
        err.textContent = `Completa dosis, frecuencia, duración y cantidad de ${it.med.nombreComercial}.`;
        return;
      }
    }
    const vigencia = Number($('#vigencia').value);
    if (!Number.isInteger(vigencia) || vigencia < 1 || vigencia > 365) {
      err.textContent = 'La vigencia debe ser un entero entre 1 y 365 días.';
      return;
    }

    ui.showModal({
      title: '¿Emitir esta receta?',
      message: `<p class="m-0">Se emitirá una receta con <strong>${items.length}</strong> medicamento(s) y vigencia de <strong>${vigencia}</strong> días. No podrá modificarse después.</p>`,
      confirmText: 'Sí, emitir receta',
      cancelText: 'Revisar',
      onConfirm: async () => {
        const btn = $('#btnEmit');
        ui.setButtonLoading(btn, true);
        try {
          const receta = await api.post('/prescriptions', {
            atencionPublicId: atencionId,
            vigenciaDias: vigencia,
            detalles: items.map(it => ({
              medicamentoPublicId: it.med.publicId,
              dosis: it.dosis.trim(),
              frecuencia: it.frecuencia.trim(),
              duracionDias: Number(it.duracionDias),
              cantidad: Number(it.cantidad),
              indicaciones: it.indicaciones.trim() || null
            }))
          });
          renderSuccess(receta);
        } catch (ex) {
          ui.setButtonLoading(btn, false);
          err.textContent = ex.message || 'No fue posible emitir la receta.';
        }
      }
    });
  });

  function renderSuccess(receta) {
    $('#rxBody').innerHTML = `
      <div class="text-center" style="padding: var(--space-8) 0;">
        <div style="width: 72px; height: 72px; border-radius: 50%; background: var(--success-bg); color: var(--success); display: inline-flex; align-items: center; justify-content: center; margin-bottom: var(--space-4);">
          ${ui.icon('check', 'icon icon--lg')}
        </div>
        <h2 class="text-2xl font-bold mb-2">Receta emitida</h2>
        <p class="text-muted mx-auto">Código <span class="font-mono">${esc(receta.publicId.slice(0, 8))}</span> · ${receta.detalles.length} medicamento(s) · vigencia ${receta.vigenciaDias} días.</p>
        <div class="flex flex-wrap justify-center gap-3 mt-6">
          <a href="#/professional/attention/${esc(atencionId)}" class="btn btn-secondary">Volver a la atención</a>
          <a href="#/professional/agenda" class="btn btn-primary">Ir a la agenda</a>
        </div>
      </div>`;
  }
}
