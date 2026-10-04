/**
 * MediTriaje 2.0 — Ventanilla de Dispensación Farmacéutica (pharmacy-dispensation.js)
 * Interfaz asistencial para regentes de farmacia (ROLE_FARMACEUTICO).
 * Búsqueda de recetas por código de reclamación o documento, control estricto
 * de saldos acumulados, trazabilidad de lote INVIMA y comprobantes inmutables (F2.4, ADR-016).
 */

import { pharmacyApi } from '../api.js';
import { auth } from '../auth.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

let pharmacyState = {
  sedes: [],
  selectedSedePublicId: '',
  searchQuery: '',
  prescriptions: [],
  page: 0,
  size: 10,
  totalPages: 1,
  totalElements: 0,
  selectedReceta: null, // RecetaDispensacionResponse
  lastComprobante: null  // DispensacionResponse
};

/**
 * Formatea una fecha y hora ISO en formato colombiano (America/Bogota)
 */
function formatDateTime(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(d);
  } catch {
    return isoString;
  }
}

/**
 * Formatea fecha solo (sin hora)
 */
function formatDate(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    }).format(d);
  } catch {
    return isoString;
  }
}

/**
 * Retorna badge visual para el estado de dispensación
 */
function renderDispensationBadge(estado) {
  switch (estado) {
    case 'DISPENSADA_TOTAL':
      return `<span class="badge badge--confirmed font-semibold">${ui.icon('check', 'icon icon--sm')} Dispensada Total</span>`;
    case 'DISPENSADA_PARCIAL':
      return `<span class="badge badge--warning font-semibold">${ui.icon('clock', 'icon icon--sm')} Dispensada Parcial</span>`;
    case 'PENDIENTE':
    default:
      return `<span class="badge badge--scheduled font-semibold">${ui.icon('pill', 'icon icon--sm')} Pendiente</span>`;
  }
}

/**
 * Vista principal de la Ventanilla de Farmacia (#/pharmacy/dispensation)
 */
export async function pharmacyDispensationView(container) {
  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Encabezado de la Ventanilla -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <div class="flex items-center gap-2 mb-1">
            <div class="empty-state-icon" style="margin: 0; width: 40px; height: 40px; background-color: var(--teal-50); color: var(--primary);">
              ${ui.icon('pill', 'icon icon--md')}
            </div>
            <h1 class="text-2xl font-bold m-0">Ventanilla de Farmacia</h1>
          </div>
          <p class="text-sm text-muted m-0">Dispensación y entrega controlada de medicamentos con trazabilidad de lote y control de saldos</p>
        </div>

        <!-- Selector de Sede Farmacéutica -->
        <div class="form-group m-0" style="min-width: 260px;">
          <label for="pharmacySiteSelect" class="form-label text-xs font-bold uppercase tracking-wider text-muted">
            Sede de Entrega <span class="text-danger">*</span>
          </label>
          <select id="pharmacySiteSelect" class="form-select text-sm">
            <option value="">Cargando sedes...</option>
          </select>
        </div>
      </div>

      <!-- Área de Búsqueda de Recetas -->
      <div class="card p-5 mb-6" style="background-color: var(--surface-1);">
        <form id="pharmacySearchForm" class="flex flex-wrap items-end gap-3">
          <div class="form-group m-0" style="flex: 1; min-width: 280px;">
            <label for="pharmacySearchInput" class="form-label text-xs font-bold uppercase tracking-wider text-muted">
              Código de Reclamación o Documento del Paciente
            </label>
            <div class="input-with-icon" style="position: relative;">
              <input
                type="text"
                id="pharmacySearchInput"
                class="form-input text-base"
                placeholder="Ej: REC-A1B2C3D4 o número de cédula (ej: 10203040)"
                autocomplete="off"
              />
            </div>
          </div>

          <button type="submit" class="btn btn-primary" id="btnSearchPrescriptions">
            ${ui.icon('search', 'icon icon--sm')}
            <span>Buscar Receta</span>
          </button>

          <button type="button" class="btn btn-secondary" id="btnResetSearch" title="Limpiar búsqueda">
            ${ui.icon('x', 'icon icon--sm')}
            <span>Limpiar</span>
          </button>
        </form>
      </div>

      <!-- Contenedor Principal de Resultados / Estación de Trabajo -->
      <div id="pharmacyMainContent">
        <div class="card p-8 text-center" style="background-color: var(--surface-2); border: 2px dashed var(--border);">
          <div class="empty-state-icon" style="margin: 0 auto var(--space-3); width: 48px; height: 48px; color: var(--text-muted);">
            ${ui.icon('search', 'icon icon--lg')}
          </div>
          <h3 class="text-base font-semibold mb-1">Ingresa el código o documento para iniciar</h3>
          <p class="text-xs text-muted m-0">El paciente presenta su código de reclamación alfanumérico generado en su portal.</p>
        </div>
      </div>

    </div>
  `;

  // Cargar sedes activas
  await loadSedes(container);

  // Listeners de búsqueda
  const form = container.querySelector('#pharmacySearchForm');
  const searchInput = container.querySelector('#pharmacySearchInput');
  const resetBtn = container.querySelector('#btnResetSearch');

  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    pharmacyState.searchQuery = searchInput.value.trim();
    pharmacyState.page = 0;
    await executeSearch(container);
  });

  resetBtn?.addEventListener('click', () => {
    searchInput.value = '';
    pharmacyState.searchQuery = '';
    pharmacyState.selectedReceta = null;
    pharmacyState.prescriptions = [];
    renderInitialState(container);
  });
}

/**
 * Carga las sedes activas en el selector
 */
async function loadSedes(container) {
  const siteSelect = container.querySelector('#pharmacySiteSelect');
  if (!siteSelect) return;

  try {
    const sedes = await pharmacyApi.listarSedes();
    pharmacyState.sedes = sedes || [];

    if (pharmacyState.sedes.length === 0) {
      siteSelect.innerHTML = '<option value="">Sin sedes disponibles</option>';
      return;
    }

    siteSelect.innerHTML = pharmacyState.sedes.map((s, idx) => `
      <option value="${s.publicId}" ${idx === 0 ? 'selected' : ''}>
        ${esc(s.nombre)} (${esc(s.ciudad)})
      </option>
    `).join('');

    pharmacyState.selectedSedePublicId = siteSelect.value;

    siteSelect.addEventListener('change', () => {
      pharmacyState.selectedSedePublicId = siteSelect.value;
    });
  } catch (err) {
    siteSelect.innerHTML = '<option value="">Error cargando sedes</option>';
    ui.showToast('No fue posible cargar el listado de sedes.', 'danger');
  }
}

/**
 * Renderiza el estado vacío inicial
 */
function renderInitialState(container) {
  const mainContent = container.querySelector('#pharmacyMainContent');
  if (!mainContent) return;

  mainContent.innerHTML = `
    <div class="card p-8 text-center" style="background-color: var(--surface-2); border: 2px dashed var(--border);">
      <div class="empty-state-icon" style="margin: 0 auto var(--space-3); width: 48px; height: 48px; color: var(--text-muted);">
        ${ui.icon('search', 'icon icon--lg')}
      </div>
      <h3 class="text-base font-semibold mb-1">Ingresa el código o documento para iniciar</h3>
      <p class="text-xs text-muted m-0">El paciente presenta su código de reclamación alfanumérico generado en su portal.</p>
    </div>
  `;
}

/**
 * Ejecuta la búsqueda de recetas en la API
 */
async function executeSearch(container) {
  const mainContent = container.querySelector('#pharmacyMainContent');
  if (!mainContent) return;

  ui.renderLoading(mainContent, 'Consultando prescripciones médicas...');

  try {
    const res = await pharmacyApi.buscarRecetas(pharmacyState.searchQuery, pharmacyState.page, pharmacyState.size);
    pharmacyState.prescriptions = res?.content || [];
    pharmacyState.totalPages = res?.totalPages || 1;
    pharmacyState.totalElements = res?.totalElements || 0;

    if (pharmacyState.prescriptions.length === 0) {
      ui.renderEmpty(mainContent, {
        icon: 'file-text',
        title: 'No se encontraron recetas médicas',
        description: `No hay prescripciones asociadas al criterio "${pharmacyState.searchQuery}". Verifica el código o documento.`,
        actionText: 'Nueva búsqueda',
        onAction: () => {
          container.querySelector('#pharmacySearchInput')?.focus();
        }
      });
      return;
    }

    // Si hay exactamente 1 coincidencia directa, abrir de inmediato la estación de trabajo
    if (pharmacyState.prescriptions.length === 1 && pharmacyState.searchQuery.length >= 6) {
      await loadAndDisplayWorkspace(pharmacyState.prescriptions[0].recetaPublicId, container);
      return;
    }

    renderSearchResults(container);
  } catch (err) {
    ui.renderError(mainContent, {
      title: 'Error en la búsqueda de recetas',
      message: err.message || 'No fue posible completar la consulta en el servidor.',
      onRetry: () => executeSearch(container)
    });
  }
}

/**
 * Renderiza la lista de resultados de búsqueda
 */
function renderSearchResults(container) {
  const mainContent = container.querySelector('#pharmacyMainContent');
  if (!mainContent) return;

  mainContent.innerHTML = `
    <div class="mb-4 flex items-center justify-between">
      <span class="text-xs text-muted font-bold uppercase tracking-wider">
        ${pharmacyState.totalElements} receta(s) encontrada(s)
      </span>
      <span class="badge badge--neutral text-xs">Ventanilla Activa</span>
    </div>

    <div class="table-container card">
      <table class="table">
        <thead>
          <tr>
            <th>Código Reclamación</th>
            <th>Paciente</th>
            <th>Médico / Especialidad</th>
            <th>Emisión / Vencimiento</th>
            <th>Vigencia</th>
            <th>Estado Entrega</th>
            <th class="text-right">Acción</th>
          </tr>
        </thead>
        <tbody>
          ${pharmacyState.prescriptions.map(r => `
            <tr>
              <td>
                <strong class="font-mono text-primary font-bold">${r.codigoReclamacion}</strong>
              </td>
              <td>
                <strong>${esc(r.pacienteNombre)}</strong>
                <span class="text-xs text-muted block font-mono">${esc(r.pacienteDocumento)}</span>
              </td>
              <td>
                <span class="font-medium text-xs block">${esc(r.profesionalNombre)}</span>
                <span class="badge badge--neutral text-xs">${esc(r.especialidadNombre)}</span>
              </td>
              <td class="text-xs">
                <span>${formatDate(r.fechaEmision)}</span>
                <span class="text-muted block">Hasta: ${formatDate(r.fechaVencimiento)}</span>
              </td>
              <td>
                <span class="badge ${r.vencida ? 'badge--neutral' : 'badge--confirmed'} text-xs font-bold">
                  ${r.vencida ? 'Vencida' : 'Vigente'}
                </span>
              </td>
              <td>${renderDispensationBadge(r.estadoDispensacion)}</td>
              <td class="text-right">
                <button type="button" class="btn btn-primary btn--sm btn-select-receta" data-receta-id="${r.recetaPublicId}">
                  ${ui.icon('pill', 'icon icon--sm')}
                  <span>Atender</span>
                </button>
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;

  mainContent.querySelectorAll('.btn-select-receta').forEach(btn => {
    btn.addEventListener('click', async () => {
      const recetaId = btn.dataset.recetaId;
      await loadAndDisplayWorkspace(recetaId, container);
    });
  });
}

/**
 * Carga el detalle completo de la receta seleccionada e inicia la estación de trabajo
 */
async function loadAndDisplayWorkspace(recetaPublicId, container) {
  const mainContent = container.querySelector('#pharmacyMainContent');
  if (!mainContent) return;

  ui.renderLoading(mainContent, 'Cargando saldos y prescripción médica...');

  try {
    const receta = await pharmacyApi.consultarReceta(recetaPublicId);
    pharmacyState.selectedReceta = receta;
    renderDispensaryWorkspace(container);
  } catch (err) {
    ui.renderError(mainContent, {
      title: 'No fue posible cargar la receta',
      message: err.message || 'Error al obtener los detalles de la prescripción.',
      onRetry: () => loadAndDisplayWorkspace(recetaPublicId, container)
    });
  }
}

/**
 * Renderiza la estación de trabajo para dispensar la receta
 */
function renderDispensaryWorkspace(container) {
  const mainContent = container.querySelector('#pharmacyMainContent');
  if (!mainContent || !pharmacyState.selectedReceta) return;

  const r = pharmacyState.selectedReceta;
  const items = r.items || [];
  const entregas = r.entregasPrevias || [];
  const puedeDispensar = !r.vencida && r.estadoDispensacion !== 'DISPENSADA_TOTAL';

  mainContent.innerHTML = `
    <!-- Barra superior de navegación de la estación -->
    <div class="flex items-center justify-between gap-4 mb-4">
      <button type="button" class="btn btn-ghost btn--sm" id="btnBackToResults">
        ${ui.icon('arrow-left', 'icon icon--sm')}
        <span>Volver a la lista</span>
      </button>

      <span class="text-xs text-muted font-mono">Receta: ${r.recetaPublicId}</span>
    </div>

    <!-- Banner de Alerta Sanitaria si está vencida o completada -->
    ${r.vencida ? `
      <div class="card p-4 mb-5" style="background-color: var(--danger-bg); border-left: 4px solid var(--danger);">
        <div class="flex items-center gap-3">
          <div style="color: var(--danger);">${ui.icon('alert-triangle', 'icon icon--lg')}</div>
          <div>
            <strong class="text-sm font-bold block" style="color: var(--danger);">RECETA MÉDICA VENCIDA</strong>
            <p class="text-xs text-muted m-0">
              La vigencia de ${r.vigenciaDias} días expiró el ${formatDate(r.fechaVencimiento)}. Por normatividad sanitaria no se permite la entrega de medicamentos.
            </p>
          </div>
        </div>
      </div>
    ` : ''}

    ${r.estadoDispensacion === 'DISPENSADA_TOTAL' ? `
      <div class="card p-4 mb-5" style="background-color: var(--teal-50); border-left: 4px solid var(--primary);">
        <div class="flex items-center gap-3">
          <div class="text-primary">${ui.icon('check', 'icon icon--lg')}</div>
          <div>
            <strong class="text-sm font-bold block text-primary">RECETA COMPLETAMENTE DISPENSADA</strong>
            <p class="text-xs text-muted m-0">Todos los medicamentos prescritos han sido entregados en su totalidad. Saldo pendiente: 0.</p>
          </div>
        </div>
      </div>
    ` : ''}

    <!-- Ficha Informativa del Paciente y Prescriptor -->
    <div class="card p-5 mb-6" style="border-top: 4px solid var(--primary);">
      <div class="flex flex-wrap items-center justify-between gap-4 pb-3 border-b mb-4">
        <div>
          <span class="text-xs font-bold uppercase tracking-wider text-muted block">Código de Reclamación</span>
          <strong class="font-mono text-2xl text-primary font-bold tracking-wider">${r.codigoReclamacion}</strong>
        </div>

        <div class="text-right">
          <span class="text-xs text-muted block">Estado General de Dispensación</span>
          ${renderDispensationBadge(r.estadoDispensacion)}
        </div>
      </div>

      <div class="grid grid-cols-1 grid-cols-3-md gap-4 text-xs">
        <div>
          <strong class="text-muted block">Paciente:</strong>
          <span class="font-semibold text-text text-sm">${esc(r.pacienteNombre)}</span>
          <span class="text-muted block font-mono">${esc(r.pacienteDocumento)}</span>
        </div>

        <div>
          <strong class="text-muted block">Médico Prescriptor:</strong>
          <span class="font-semibold text-text text-sm">${esc(r.profesionalNombre)}</span>
          <span class="badge badge--neutral text-xs mt-1">${esc(r.especialidadNombre)}</span>
        </div>

        <div>
          <strong class="text-muted block">Vigencia de la Receta:</strong>
          <span>Emisión: <strong>${formatDate(r.fechaEmision)}</strong></span>
          <span class="block">Vence: <strong>${formatDate(r.fechaVencimiento)}</strong> (${r.vigenciaDias} días)</span>
        </div>
      </div>
    </div>

    <!-- Formulario de Entrega y Control de Saldos -->
    <div class="card p-5 mb-6">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div>
          <h2 class="text-base font-bold m-0">Medicamentos y Control de Saldos</h2>
          <p class="text-xs text-muted m-0">Ingresa la cantidad a entregar, el número de lote INVIMA y la fecha de vencimiento</p>
        </div>
        <span class="badge badge--scheduled text-xs">Trazabilidad Sanitaria Obligatoria</span>
      </div>

      <div class="table-container mb-4">
        <table class="table text-xs">
          <thead>
            <tr>
              <th style="min-width: 180px;">Medicamento</th>
              <th class="text-center">Prescrito</th>
              <th class="text-center">Dispensado</th>
              <th class="text-center">Saldo</th>
              <th>Estado</th>
              ${puedeDispensar ? `
                <th style="width: 110px;" class="text-center">A Entregar</th>
                <th style="min-width: 140px;">Lote INVIMA</th>
                <th style="min-width: 140px;">Vence Lote</th>
              ` : ''}
            </tr>
          </thead>
          <tbody>
            ${items.map((item, idx) => `
              <tr class="item-row" data-med-id="${item.medicamentoPublicId}" data-saldo="${item.saldoPendiente}">
                <td>
                  <strong class="block">${esc(item.nombreComercial)}</strong>
                  <span class="text-muted block">${esc(item.presentacion)} — ${esc(item.concentracion)}</span>
                  <span class="text-xs text-muted block">Dosis: ${esc(item.dosis)} (${esc(item.frecuencia)})</span>
                </td>
                <td class="text-center font-medium">${item.cantidadPrescrita}</td>
                <td class="text-center font-medium text-success">${item.cantidadDispensada}</td>
                <td class="text-center font-bold ${item.saldoPendiente > 0 ? 'text-primary' : 'text-muted'}">
                  ${item.saldoPendiente}
                </td>
                <td>${renderDispensationBadge(item.estado)}</td>
                ${puedeDispensar ? `
                  <td>
                    ${item.saldoPendiente > 0 ? `
                      <input
                        type="number"
                        class="form-input text-center font-bold input-cantidad"
                        min="0"
                        max="${item.saldoPendiente}"
                        value="${item.saldoPendiente}"
                        data-med-id="${item.medicamentoPublicId}"
                        style="padding: 4px 8px;"
                      />
                    ` : `
                      <span class="text-muted text-center block">—</span>
                    `}
                  </td>
                  <td>
                    ${item.saldoPendiente > 0 ? `
                      <input
                        type="text"
                        class="form-input font-mono input-lote"
                        placeholder="Ej: LOTE-8942A"
                        maxlength="50"
                        data-med-id="${item.medicamentoPublicId}"
                        style="padding: 4px 8px;"
                      />
                    ` : `
                      <span class="text-muted block">—</span>
                    `}
                  </td>
                  <td>
                    ${item.saldoPendiente > 0 ? `
                      <input
                        type="date"
                        class="form-input input-vencimiento"
                        data-med-id="${item.medicamentoPublicId}"
                        style="padding: 4px 8px;"
                      />
                    ` : `
                      <span class="text-muted block">—</span>
                    `}
                  </td>
                ` : ''}
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>

      ${puedeDispensar ? `
        <!-- Observaciones de Dispensación -->
        <div class="form-group mb-5">
          <label for="dispenseObservations" class="form-label text-xs font-bold uppercase tracking-wider text-muted">
            Observaciones Sanitarias o de Entrega (Opcional)
          </label>
          <textarea
            id="dispenseObservations"
            class="form-textarea text-xs"
            rows="2"
            maxlength="500"
            placeholder="Indicaciones adicionales de entrega, condiciones especiales de conservación o notas farmacéuticas..."
          ></textarea>
        </div>

        <!-- Botones de Acción -->
        <div class="flex flex-wrap items-center justify-end gap-3 pt-3 border-t">
          <button type="button" class="btn btn-secondary" id="btnCancelDispense">
            Cancelar
          </button>

          <button type="button" class="btn btn-primary" id="btnConfirmDispense">
            ${ui.icon('check', 'icon icon--sm')}
            <span>Confirmar y Registrar Entrega</span>
          </button>
        </div>
      ` : ''}
    </div>

    <!-- Historial de Entregas Previas (Inmutables) -->
    <div class="card p-5 mb-6">
      <h3 class="text-base font-bold mb-1">Historial de Entregas Farmacéuticas (${entregas.length})</h3>
      <p class="text-xs text-muted mb-4">Registro inmutable de dispensaciones realizadas en ventanilla para esta receta</p>

      ${entregas.length === 0 ? `
        <p class="text-xs text-muted m-0 p-4 text-center" style="background-color: var(--surface-2); border-radius: var(--radius-sm);">
          No se registran entregas previas para esta prescripción médica.
        </p>
      ` : `
        <div class="flex flex-col gap-4">
          ${entregas.map(ent => `
            <div class="p-4" style="background-color: var(--surface-2); border-radius: var(--radius-md); border: 1px solid var(--border);">
              <div class="flex flex-wrap items-center justify-between gap-3 text-xs pb-2 border-b mb-3">
                <div>
                  <strong class="text-sm block">Comprobante #${ent.publicId.slice(0, 8)}</strong>
                  <span class="text-muted">Fecha: ${formatDateTime(ent.createdAt)}</span>
                </div>

                <div class="text-right">
                  <span class="badge badge--neutral text-xs">${esc(ent.sedeNombre)}</span>
                  <span class="text-muted block text-xs mt-1">Dispensado por: <strong>${esc(ent.dispensadorNombre)}</strong></span>
                </div>
              </div>

              ${ent.observaciones ? `
                <p class="text-xs text-muted mb-3"><strong>Observaciones:</strong> ${esc(ent.observaciones)}</p>
              ` : ''}

              <div class="table-container">
                <table class="table text-xs" style="margin: 0;">
                  <thead>
                    <tr>
                      <th>Medicamento</th>
                      <th>Lote INVIMA</th>
                      <th>Vencimiento Lote</th>
                      <th class="text-center">Cantidad Entregada</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${(ent.detalles || []).map(d => `
                      <tr>
                        <td><strong>${esc(d.nombreComercial)}</strong> (${esc(d.principioActivo)})</td>
                        <td><span class="badge badge--neutral font-mono text-xs">${esc(d.lote) || 'N/A'}</span></td>
                        <td>${d.fechaVencimientoLote || 'N/A'}</td>
                        <td class="text-center font-bold text-success">${d.cantidadEntregada}</td>
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              </div>
            </div>
          `).join('')}
        </div>
      `}
    </div>
  `;

  // Listeners
  mainContent.querySelector('#btnBackToResults')?.addEventListener('click', () => {
    if (pharmacyState.prescriptions.length > 1) {
      renderSearchResults(container);
    } else {
      renderInitialState(container);
    }
  });

  mainContent.querySelector('#btnCancelDispense')?.addEventListener('click', () => {
    renderSearchResults(container);
  });

  mainContent.querySelector('#btnConfirmDispense')?.addEventListener('click', async () => {
    await processDispensation(container);
  });
}

/**
 * Valida y envía la solicitud de dispensación
 */
async function processDispensation(container) {
  const r = pharmacyState.selectedReceta;
  if (!r) return;

  const siteSelect = container.querySelector('#pharmacySiteSelect');
  const sedePublicId = siteSelect?.value || pharmacyState.selectedSedePublicId;

  if (!sedePublicId) {
    ui.showToast('Por favor selecciona una sede de entrega en el selector superior.', 'warning');
    siteSelect?.focus();
    return;
  }

  // Recolectar cantidades de entrega
  const rows = container.querySelectorAll('.item-row');
  const detalles = [];
  let totalUnidades = 0;

  for (const row of rows) {
    const medId = row.dataset.medId;
    const saldo = parseInt(row.dataset.saldo, 10) || 0;
    const qtyInput = row.querySelector('.input-cantidad');
    const loteInput = row.querySelector('.input-lote');
    const vencInput = row.querySelector('.input-vencimiento');

    if (!qtyInput) continue;

    const cantidad = parseInt(qtyInput.value, 10) || 0;
    if (cantidad <= 0) continue;

    if (cantidad > saldo) {
      ui.showToast(`La cantidad ingresada (${cantidad}) supera el saldo pendiente (${saldo}) para uno de los medicamentos.`, 'danger');
      qtyInput.focus();
      return;
    }

    const lote = loteInput ? loteInput.value.trim() : null;
    const fechaVenc = vencInput && vencInput.value ? vencInput.value : null;

    detalles.push({
      medicamentoPublicId: medId,
      cantidadEntregada: cantidad,
      lote: lote || null,
      fechaVencimientoLote: fechaVenc || null
    });

    totalUnidades += cantidad;
  }

  if (detalles.length === 0) {
    ui.showToast('Debes ingresar una cantidad a entregar mayor a 0 para al menos un medicamento.', 'warning');
    return;
  }

  const obsInput = container.querySelector('#dispenseObservations');
  const observaciones = obsInput ? obsInput.value.trim() : null;

  // Confirmación modal
  ui.showModal({
    title: 'Confirmar Dispensación Farmacéutica',
    message: `¿Estás seguro de registrar la entrega de <strong>${totalUnidades} unidad(es)</strong> correspondientes a ${detalles.length} medicamento(s)?<br><br><small class="text-muted">Esta acción es inmutable y quedará registrada en el sistema de trazabilidad legal.</small>`,
    confirmText: 'Sí, registrar entrega',
    cancelText: 'Revisar',
    onConfirm: async () => {
      const mainContent = container.querySelector('#pharmacyMainContent');
      ui.renderLoading(mainContent, 'Registrando dispensación inmutable en base de datos...');

      try {
        const payload = {
          recetaPublicId: r.recetaPublicId,
          sedePublicId: sedePublicId,
          observaciones: observaciones || null,
          detalles: detalles
        };

        const result = await pharmacyApi.registrarDispensacion(payload);
        pharmacyState.lastComprobante = result;

        ui.showToast('Dispensación registrada exitosamente.', 'success');
        renderComprobante(container, result);
      } catch (err) {
        ui.renderError(mainContent, {
          title: 'Error al registrar la dispensación',
          message: err.message || 'Ocurrió un error al procesar la entrega de medicamentos.',
          onRetry: () => loadAndDisplayWorkspace(r.recetaPublicId, container)
        });
      }
    }
  });
}

/**
 * Renderiza el comprobante de entrega tras una dispensación exitosa
 */
function renderComprobante(container, comprobante) {
  const mainContent = container.querySelector('#pharmacyMainContent');
  if (!mainContent) return;

  const r = pharmacyState.selectedReceta;

  mainContent.innerHTML = `
    <div class="card p-8 mb-6" style="background-color: var(--surface-1); border-top: 6px solid var(--primary); max-width: 720px; margin: 0 auto;">
      
      <!-- Encabezado del comprobante -->
      <div class="flex items-center justify-between pb-4 border-b mb-5">
        <div>
          <span class="badge badge--confirmed text-xs font-bold mb-1">
            ${ui.icon('check', 'icon icon--sm')} Entrega Exitosa
          </span>
          <h2 class="text-xl font-bold m-0">Comprobante de Dispensación Farmacéutica</h2>
          <span class="text-xs text-muted font-mono">No. ${comprobante.publicId}</span>
        </div>

        <button type="button" class="btn btn-secondary btn--sm" id="btnPrintReceipt">
          ${ui.icon('printer', 'icon icon--sm')}
          <span>Imprimir Comprobante</span>
        </button>
      </div>

      <!-- Datos generales de la entrega -->
      <div class="grid grid-cols-1 grid-cols-2-md gap-4 mb-6 text-xs p-4" style="background-color: var(--surface-2); border-radius: var(--radius-md);">
        <div>
          <strong class="text-muted block">Código de Reclamación:</strong>
          <span class="font-mono text-base font-bold text-primary">${r?.codigoReclamacion || '—'}</span>
        </div>

        <div>
          <strong class="text-muted block">Fecha y Hora de Entrega:</strong>
          <span class="font-medium text-text">${formatDateTime(comprobante.createdAt)}</span>
        </div>

        <div>
          <strong class="text-muted block">Paciente:</strong>
          <span class="font-medium text-text">${esc(r?.pacienteNombre || '—')}</span>
          <span class="text-muted font-mono block">${esc(r?.pacienteDocumento || '')}</span>
        </div>

        <div>
          <strong class="text-muted block">Sede y Regente Farmacéutico:</strong>
          <span class="font-medium text-text">${esc(comprobante.sedeNombre)}</span>
          <span class="text-muted block">Dispensado por: ${esc(comprobante.dispensadorNombre)}</span>
        </div>
      </div>

      <!-- Tabla de medicamentos entregados -->
      <h3 class="text-xs font-bold uppercase tracking-wider text-muted mb-2">Detalle de Medicamentos Entregados:</h3>
      <div class="table-container mb-6">
        <table class="table text-xs">
          <thead>
            <tr>
              <th>Medicamento</th>
              <th>Lote INVIMA</th>
              <th>Vencimiento Lote</th>
              <th class="text-center">Cantidad Entregada</th>
            </tr>
          </thead>
          <tbody>
            ${(comprobante.detalles || []).map(d => `
              <tr>
                <td>
                  <strong>${esc(d.nombreComercial)}</strong>
                  <span class="text-muted block text-xs">${esc(d.presentacion)} — ${esc(d.concentracion)}</span>
                </td>
                <td><span class="badge badge--neutral font-mono text-xs">${esc(d.lote) || 'N/A'}</span></td>
                <td>${d.fechaVencimientoLote || 'N/A'}</td>
                <td class="text-center font-bold text-success text-sm">${d.cantidadEntregada}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>

      ${comprobante.observaciones ? `
        <div class="mb-6 p-3" style="background-color: var(--surface-2); border-radius: var(--radius-sm); font-size: var(--text-xs);">
          <strong class="text-muted block">Observaciones Registradas:</strong>
          <p class="m-0">${esc(comprobante.observaciones)}</p>
        </div>
      ` : ''}

      <!-- Acciones de cierre -->
      <div class="flex flex-wrap items-center justify-between gap-3 pt-4 border-t">
        <button type="button" class="btn btn-secondary" id="btnNewDispensation">
          ${ui.icon('search', 'icon icon--sm')}
          <span>Nueva Dispensación</span>
        </button>

        <button type="button" class="btn btn-primary" id="btnViewPrescriptionUpdated">
          ${ui.icon('pill', 'icon icon--sm')}
          <span>Ver Saldos Actualizados</span>
        </button>
      </div>

    </div>
  `;

  mainContent.querySelector('#btnPrintReceipt')?.addEventListener('click', () => {
    window.print();
  });

  mainContent.querySelector('#btnNewDispensation')?.addEventListener('click', () => {
    pharmacyState.selectedReceta = null;
    pharmacyState.prescriptions = [];
    pharmacyState.searchQuery = '';
    const searchInput = container.querySelector('#pharmacySearchInput');
    if (searchInput) searchInput.value = '';
    renderInitialState(container);
  });

  mainContent.querySelector('#btnViewPrescriptionUpdated')?.addEventListener('click', async () => {
    await loadAndDisplayWorkspace(comprobante.recetaPublicId, container);
  });
}
