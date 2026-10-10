/**
 * MediTriaje 2.0 — Centro de Control Hospitalario y Censo de Camas (hospital-census.js)
 * Monitorización en tiempo real de capacidad hospitalaria, ocupación de camas,
 * disponibilidad por pabellón y desinfección (Fase H, H06, ADR-024).
 */

import { hospitalApi, emergencyApi } from '../api.js';
import { auth } from '../auth.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

let censusState = {
  sedes: [],
  selectedSedePublicId: '',
  censusData: null,
  beds: [],
  filterEstado: '',
  filterArea: ''
};

function formatBedBadge(estado) {
  switch (estado) {
    case 'DISPONIBLE':
      return `<span class="badge" style="background-color: var(--teal-600, #059669); color: #fff; font-weight: 600;">Disponible</span>`;
    case 'OCUPADA':
      return `<span class="badge" style="background-color: var(--danger, #dc2626); color: #fff; font-weight: 600;">Ocupada</span>`;
    case 'LIMPIEZA':
      return `<span class="badge" style="background-color: #d97706; color: #fff; font-weight: 600;">En Limpieza</span>`;
    case 'MANTENIMIENTO':
      return `<span class="badge" style="background-color: #64748b; color: #fff; font-weight: 600;">Mantenimiento</span>`;
    default:
      return `<span class="badge badge--dark">${esc(estado)}</span>`;
  }
}

export async function hospitalCensusView(container) {
  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Encabezado del Centro de Control -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <div class="flex items-center gap-2 mb-1">
            <div class="empty-state-icon" style="margin: 0; width: 40px; height: 40px; background-color: var(--indigo-50, #eef2ff); color: var(--indigo-600, #4f46e5);">
              ${ui.icon('grid', 'icon icon--md')}
            </div>
            <h1 class="text-2xl font-bold m-0">Centro de Control Hospitalario</h1>
          </div>
          <p class="text-sm text-muted m-0">Censo de camas en tiempo real, tasas de ocupación por pabellón y gestión de capacidad instalada</p>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <div class="form-group m-0" style="min-width: 240px;">
            <select id="censusSedeSelect" class="form-select form-input--sm" aria-label="Seleccionar sede hospitalaria">
              <option value="">Cargando sedes...</option>
            </select>
          </div>

          <button type="button" id="btnRefreshCensus" class="btn btn-ghost btn--sm flex items-center gap-1">
            ${ui.icon('refresh-cw', 'icon icon--sm')}
            <span>Actualizar</span>
          </button>
        </div>
      </div>

      <!-- Tarjetas de Métricas de Capacidad (KPIs) -->
      <div class="grid grid-cols-2 md:grid-cols-6 gap-3 mb-6" id="censusKpiGrid">
        <div class="card p-3 text-center">
          <div class="text-xs text-muted font-bold uppercase">Total Camas</div>
          <div class="text-2xl font-bold mt-1" id="kpiTotalCamas">0</div>
        </div>

        <div class="card p-3 text-center" style="border-top: 3px solid var(--danger, #dc2626);">
          <div class="text-xs text-muted font-bold uppercase">Ocupadas</div>
          <div class="text-2xl font-bold mt-1 text-danger" id="kpiOcupadas">0</div>
        </div>

        <div class="card p-3 text-center" style="border-top: 3px solid #059669;">
          <div class="text-xs text-muted font-bold uppercase">Disponibles</div>
          <div class="text-2xl font-bold mt-1 text-success" id="kpiDisponibles">0</div>
        </div>

        <div class="card p-3 text-center" style="border-top: 3px solid #d97706;">
          <div class="text-xs text-muted font-bold uppercase">Limpieza</div>
          <div class="text-2xl font-bold mt-1" style="color: #d97706;" id="kpiLimpieza">0</div>
        </div>

        <div class="card p-3 text-center" style="border-top: 3px solid #64748b;">
          <div class="text-xs text-muted font-bold uppercase">Mantenimiento</div>
          <div class="text-2xl font-bold mt-1 text-muted" id="kpiMantenimiento">0</div>
        </div>

        <div class="card p-3 text-center" style="border-top: 3px solid var(--primary);">
          <div class="text-xs text-muted font-bold uppercase">% Ocupación</div>
          <div class="text-2xl font-bold mt-1" style="color: var(--primary);" id="kpiTasaOcupacion">0%</div>
        </div>
      </div>

      <!-- Pabellones y Áreas Hospitalarias -->
      <div class="card p-4 mb-6">
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-base font-bold m-0">Ocupación por Áreas y Pabellones</h2>
          <span class="text-xs text-muted">Capacidad por servicio asistencial</span>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-4 gap-3" id="areaSummaryGrid">
          <p class="text-sm text-muted">Consultando áreas...</p>
        </div>
      </div>

      <!-- Barra de Filtros del Mapa de Camas -->
      <div class="card p-4 mb-6">
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div class="flex flex-wrap items-center gap-3">
            <div class="flex items-center gap-2">
              <span class="text-xs font-semibold">Estado:</span>
              <select id="filterBedEstado" class="form-select form-input--sm" style="width: auto;">
                <option value="">Todos los estados</option>
                <option value="DISPONIBLE">Disponibles</option>
                <option value="OCUPADA">Ocupadas</option>
                <option value="LIMPIEZA">En Limpieza</option>
                <option value="MANTENIMIENTO">Mantenimiento</option>
              </select>
            </div>

            <div class="flex items-center gap-2">
              <span class="text-xs font-semibold">Área:</span>
              <select id="filterBedArea" class="form-select form-input--sm" style="width: auto;">
                <option value="">Todas las áreas</option>
              </select>
            </div>
          </div>

          <div class="text-xs text-muted" id="lblBedCount">Mostrando 0 camas</div>
        </div>
      </div>

      <!-- Grid Visual de Camas -->
      <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4" id="bedCardsGrid">
        <div class="p-8 text-center text-muted col-span-full">Cargando inventario de camas...</div>
      </div>

    </div>
  `;

  await loadSedes();

  const sedeSelect = document.getElementById('censusSedeSelect');
  sedeSelect.addEventListener('change', async (e) => {
    censusState.selectedSedePublicId = e.target.value;
    await loadData();
  });

  document.getElementById('btnRefreshCensus').addEventListener('click', async () => {
    await loadData();
    ui.showToast('Censo hospitalario actualizado.', 'info');
  });

  document.getElementById('filterBedEstado').addEventListener('change', (e) => {
    censusState.filterEstado = e.target.value;
    renderBeds();
  });

  document.getElementById('filterBedArea').addEventListener('change', (e) => {
    censusState.filterArea = e.target.value;
    renderBeds();
  });
}

async function loadSedes() {
  const sedeSelect = document.getElementById('censusSedeSelect');
  try {
    const sedes = await emergencyApi.listarSedes();
    censusState.sedes = sedes || [];
    if (sedes && sedes.length > 0) {
      censusState.selectedSedePublicId = sedes[0].publicId;
      sedeSelect.innerHTML = sedes.map(s => `
        <option value="${esc(s.publicId)}">${esc(s.nombre)} (${esc(s.ciudad || 'Valledupar')})</option>
      `).join('');
      await loadData();
    }
  } catch (err) {
    ui.showToast('Error cargando sedes: ' + err.message, 'error');
  }
}

async function loadData() {
  if (!censusState.selectedSedePublicId) return;

  try {
    const [census, beds] = await Promise.all([
      hospitalApi.obtenerCenso(censusState.selectedSedePublicId),
      hospitalApi.listarCamas(censusState.selectedSedePublicId)
    ]);

    censusState.censusData = census;
    censusState.beds = beds || [];

    updateKpis(census);
    renderAreas(census.areas);
    updateAreaFilter(census.areas);
    renderBeds();
  } catch (err) {
    ui.showToast('Error al obtener censo hospitalario: ' + err.message, 'error');
  }
}

function updateKpis(c) {
  document.getElementById('kpiTotalCamas').textContent = c.totalCamas;
  document.getElementById('kpiOcupadas').textContent = c.ocupadas;
  document.getElementById('kpiDisponibles').textContent = c.disponibles;
  document.getElementById('kpiLimpieza').textContent = c.enLimpieza;
  document.getElementById('kpiMantenimiento').textContent = c.enMantenimiento;
  document.getElementById('kpiTasaOcupacion').textContent = `${c.tasaOcupacionPorcentaje}%`;
}

function renderAreas(areas) {
  const container = document.getElementById('areaSummaryGrid');
  if (!areas || areas.length === 0) {
    container.innerHTML = `<p class="text-sm text-muted m-0">No hay áreas configuradas para esta sede.</p>`;
    return;
  }

  container.innerHTML = areas.map(a => {
    const pct = a.totalCamas > 0 ? Math.round((a.ocupadas / a.totalCamas) * 100) : 0;
    return `
      <div class="card p-3 bg-slate-50 border">
        <div class="font-bold text-sm truncate">${esc(a.areaNombre)}</div>
        <div class="text-xs text-muted font-mono">${esc(a.areaCodigo)} · ${esc(a.tipoArea)}</div>
        
        <div class="flex items-center justify-between mt-2 text-xs">
          <span>Ocupación: <strong>${a.ocupadas}/${a.totalCamas}</strong></span>
          <span class="font-bold ${pct > 85 ? 'text-danger' : 'text-primary'}">${pct}%</span>
        </div>

        <div style="background-color: var(--border, #e2e8f0); height: 6px; border-radius: 9999px; margin-top: 6px; overflow: hidden;">
          <div style="background-color: ${pct > 85 ? 'var(--danger, #dc2626)' : 'var(--primary, #0d9488)'}; width: ${pct}%; height: 100%;"></div>
        </div>
      </div>
    `;
  }).join('');
}

function updateAreaFilter(areas) {
  const sel = document.getElementById('filterBedArea');
  sel.innerHTML = `<option value="">Todas las áreas</option>` + (areas || []).map(a => `
    <option value="${esc(a.areaNombre)}">${esc(a.areaNombre)}</option>
  `).join('');
}

function renderBeds() {
  const grid = document.getElementById('bedCardsGrid');
  const countLbl = document.getElementById('lblBedCount');

  let list = censusState.beds;
  if (censusState.filterEstado) {
    list = list.filter(b => b.estado === censusState.filterEstado);
  }
  if (censusState.filterArea) {
    list = list.filter(b => b.areaNombre === censusState.filterArea);
  }

  countLbl.textContent = `Mostrando ${list.length} de ${censusState.beds.length} camas`;

  if (list.length === 0) {
    grid.innerHTML = `<div class="p-8 text-center text-muted col-span-full">No se encontraron camas con los filtros seleccionados.</div>`;
    return;
  }

  grid.innerHTML = list.map(b => {
    const isOcupada = b.estado === 'OCUPADA';
    const isLimpieza = b.estado === 'LIMPIEZA';

    let cardBorder = 'border-slate-200';
    if (isOcupada) cardBorder = 'border-danger';
    else if (b.estado === 'DISPONIBLE') cardBorder = 'border-emerald-500';
    else if (isLimpieza) cardBorder = 'border-amber-500';

    return `
      <div class="card p-4 border flex flex-col justify-between ${cardBorder}">
        <div>
          <div class="flex items-center justify-between mb-2">
            <span class="font-bold text-base font-mono">${esc(b.codigo)}</span>
            ${formatBedBadge(b.estado)}
          </div>

          <div class="text-xs text-muted mb-1">
            <span class="font-semibold">Área:</span> ${esc(b.areaNombre)}
          </div>
          <div class="text-xs text-muted mb-3">
            <span class="font-semibold">Habitación:</span> ${esc(b.habitacionCodigo)}
          </div>

          ${isOcupada ? `
            <div class="p-2 rounded bg-red-50 border border-red-200 mb-2">
              <div class="text-xs font-bold text-red-900 truncate">
                ${esc(b.pacienteNombre || 'Paciente Asignado')}
              </div>
              <div class="text-xs text-red-700 font-mono truncate">
                Episodio: ${esc(b.episodioPublicId || '—')}
              </div>
            </div>
          ` : ''}
        </div>

        <div class="mt-3 pt-2 border-t flex items-center justify-end gap-1">
          ${isLimpieza ? `
            <button type="button" class="btn btn-xs btn-primary btn-bed-action" data-id="${esc(b.publicId)}" data-status="DISPONIBLE" title="Liberar cama a Disponible tras desinfección">
              ${ui.icon('check', 'icon icon--xs')}
              <span>Marcar Disponible</span>
            </button>
          ` : ''}

          ${b.estado === 'DISPONIBLE' ? `
            <button type="button" class="btn btn-xs btn-ghost btn-bed-action" data-id="${esc(b.publicId)}" data-status="MANTENIMIENTO" title="Poner en mantenimiento">
              <span>Mantenimiento</span>
            </button>
          ` : ''}

          ${b.estado === 'MANTENIMIENTO' ? `
            <button type="button" class="btn btn-xs btn-ghost btn-bed-action" data-id="${esc(b.publicId)}" data-status="DISPONIBLE" title="Habilitar cama">
              <span>Habilitar</span>
            </button>
          ` : ''}
        </div>
      </div>
    `;
  }).join('');

  // Acciones de cambio de estado de cama
  grid.querySelectorAll('.btn-bed-action').forEach(btn => {
    btn.addEventListener('click', async () => {
      const camaId = btn.dataset.id;
      const status = btn.dataset.status;

      try {
        await hospitalApi.cambiarEstadoCama(camaId, status);
        ui.showToast(`Estado de cama actualizado a ${status}.`, 'success');
        await loadData();
      } catch (err) {
        ui.showToast('Error cambiando estado: ' + err.message, 'error');
      }
    });
  });
}
