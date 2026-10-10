/**
 * MediTriaje 2.0 — Panel y Cola Priorizada de Urgencias (nursing-dashboard.js)
 * Interfaz asistencial para personal de enfermería (ROLE_ENFERMERIA).
 * Monitorización en tiempo real de pacientes en espera, niveles I a V,
 * tiempos de espera normativos (Resolución 5596 de 2015), pacientes NN y asignaciones.
 */

import { emergencyApi } from '../api.js';
import { auth } from '../auth.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

let dashboardState = {
  sedes: [],
  selectedSedePublicId: '',
  filterEstado: '',
  queue: [],
  loading: false,
  timerInterval: null
};

function formatTriageBadge(nivel) {
  switch (nivel) {
    case 'I':
      return `<span class="badge" style="background-color: var(--danger, #dc2626); color: #fff; font-weight: 700; padding: 4px 10px; border-radius: 9999px;">🔴 Nivel I — Reanimación</span>`;
    case 'II':
      return `<span class="badge" style="background-color: #ea580c; color: #fff; font-weight: 700; padding: 4px 10px; border-radius: 9999px;">🟠 Nivel II — Emergencia</span>`;
    case 'III':
      return `<span class="badge" style="background-color: #ca8a04; color: #fff; font-weight: 700; padding: 4px 10px; border-radius: 9999px;">🟡 Nivel III — Urgencia</span>`;
    case 'IV':
      return `<span class="badge" style="background-color: #16a34a; color: #fff; font-weight: 700; padding: 4px 10px; border-radius: 9999px;">🟢 Nivel IV — Prioritaria</span>`;
    case 'V':
      return `<span class="badge" style="background-color: #2563eb; color: #fff; font-weight: 700; padding: 4px 10px; border-radius: 9999px;">🔵 Nivel V — No Urgente</span>`;
    default:
      return `<span class="badge badge--scheduled" style="font-weight: 600; padding: 4px 10px; border-radius: 9999px;">⚪ Pendiente Triaje</span>`;
  }
}

function formatWaitTime(minutos) {
  if (minutos == null || minutos < 0) return '0 min';
  if (minutos < 60) return `${minutos} min`;
  const horas = Math.floor(minutos / 60);
  const rest = minutos % 60;
  return `${horas}h ${rest}m`;
}

function getWaitAlertClass(nivel, minutos) {
  if (minutos == null) return '';
  if (nivel === 'I') return 'text-danger font-bold';
  if (nivel === 'II' && minutos > 30) return 'text-danger font-bold';
  if (nivel === 'III' && minutos > 120) return 'text-warning font-bold';
  if (nivel === 'IV' && minutos > 180) return 'text-warning font-semibold';
  return 'text-muted';
}

export async function nursingDashboardView(container) {
  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Encabezado de Urgencias y Enfermería -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <div class="flex items-center gap-2 mb-1">
            <div class="empty-state-icon" style="margin: 0; width: 40px; height: 40px; background-color: var(--teal-50, #f0fdf4); color: var(--primary, #0d9488);">
              ${ui.icon('activity', 'icon icon--md')}
            </div>
            <h1 class="text-2xl font-bold m-0">Centro de Urgencias y Triaje</h1>
          </div>
          <p class="text-sm text-muted m-0">Recepción, clasificación presencial (Resolución 5596), monitoreo de espera y reevaluación clínica</p>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <!-- Selector de Sede -->
          <div class="form-group m-0" style="min-width: 220px;">
            <select id="nursingSedeSelect" class="form-select form-input--sm" aria-label="Seleccionar sede de atención">
              <option value="">Cargando sedes...</option>
            </select>
          </div>

          <a href="#/hospital/census" class="btn btn-secondary btn--sm flex items-center gap-1" title="Ver censo de camas hospitalarias">
            ${ui.icon('hospital', 'icon icon--sm')}
            <span>Censo Camas</span>
          </a>

          <a href="#/affiliations/search" class="btn btn-secondary btn--sm flex items-center gap-1" title="Consultar aseguramiento EPS">
            ${ui.icon('file-text', 'icon icon--sm')}
            <span>Consulta EPS</span>
          </a>

          <a href="#/operational/dashboard" class="btn btn-secondary btn--sm flex items-center gap-1" title="Ver tablero de mando operativo">
            ${ui.icon('bar-chart-2', 'icon icon--sm')}
            <span>Mando</span>
          </a>

          <a href="#/nursing/admission" class="btn btn-primary btn--sm flex items-center gap-2">
            ${ui.icon('plus', 'icon icon--sm')}
            <span>Nueva Admisión</span>
          </a>
        </div>
      </div>

      <!-- Tarjetas de Resumen Operativo -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6" id="kpiCards">
        <div class="card p-4 flex items-center justify-between">
          <div>
            <div class="text-xs text-muted font-bold uppercase tracking-wider">En Espera Total</div>
            <div class="text-2xl font-bold mt-1" id="kpiTotal">0</div>
          </div>
          <div style="color: var(--primary);">${ui.icon('users', 'icon icon--lg')}</div>
        </div>

        <div class="card p-4 flex items-center justify-between" style="border-left: 4px solid var(--danger, #dc2626);">
          <div>
            <div class="text-xs text-muted font-bold uppercase tracking-wider">Nivel I y II Críticos</div>
            <div class="text-2xl font-bold mt-1 text-danger" id="kpiCriticos">0</div>
          </div>
          <div style="color: var(--danger);">${ui.icon('alert-triangle', 'icon icon--lg')}</div>
        </div>

        <div class="card p-4 flex items-center justify-between" style="border-left: 4px solid #ca8a04;">
          <div>
            <div class="text-xs text-muted font-bold uppercase tracking-wider">Pendientes de Triaje</div>
            <div class="text-2xl font-bold mt-1" style="color: #ca8a04;" id="kpiPendientesTriaje">0</div>
          </div>
          <div style="color: #ca8a04;">${ui.icon('clock', 'icon icon--lg')}</div>
        </div>

        <div class="card p-4 flex items-center justify-between" style="border-left: 4px solid #6366f1;">
          <div>
            <div class="text-xs text-muted font-bold uppercase tracking-wider">Identidad Provisional (NN)</div>
            <div class="text-2xl font-bold mt-1 text-indigo-600" id="kpiNN">0</div>
          </div>
          <div style="color: #6366f1;">${ui.icon('help-circle', 'icon icon--lg')}</div>
        </div>
      </div>

      <!-- Barra de Filtros y Estado -->
      <div class="card p-4 mb-6">
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div class="flex items-center gap-2">
            <span class="text-sm font-semibold">Filtrar por estado:</span>
            <select id="filterEstadoSelect" class="form-select form-input--sm" style="width: auto;">
              <option value="">Todos los activos</option>
              <option value="REGISTRADO">Solo Registrados (sin triaje)</option>
              <option value="EN_TRIAJE">En Espera de Atención Médica</option>
              <option value="EN_ATENCION">En Atención Médica</option>
            </select>
          </div>

          <button type="button" id="btnRefreshQueue" class="btn btn-ghost btn--sm flex items-center gap-1">
            ${ui.icon('refresh-cw', 'icon icon--sm')}
            <span>Actualizar Cola</span>
          </button>
        </div>
      </div>

      <!-- Tabla de la Cola Priorizada -->
      <div class="card overflow-hidden">
        <div class="table-responsive">
          <table class="table" id="urgencyQueueTable" aria-label="Cola priorizada de urgencias">
            <thead>
              <tr>
                <th scope="col">Prioridad / Nivel</th>
                <th scope="col">Paciente</th>
                <th scope="col">Motivo de Ingreso</th>
                <th scope="col">Espera</th>
                <th scope="col">Estado</th>
                <th scope="col">Médico Asignado</th>
                <th scope="col" class="text-right">Acciones</th>
              </tr>
            </thead>
            <tbody id="urgencyQueueBody">
              <tr>
                <td colspan="7" class="text-center p-8 text-muted">Cargando cola de atención...</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </div>
  `;

  // Cargar Sedes
  await loadSedes();

  // Escuchadores de eventos
  const sedeSelect = document.getElementById('nursingSedeSelect');
  sedeSelect.addEventListener('change', async (e) => {
    dashboardState.selectedSedePublicId = e.target.value;
    await loadQueue();
  });

  const estadoSelect = document.getElementById('filterEstadoSelect');
  estadoSelect.addEventListener('change', async (e) => {
    dashboardState.filterEstado = e.target.value;
    await loadQueue();
  });

  const refreshBtn = document.getElementById('btnRefreshQueue');
  refreshBtn.addEventListener('click', async () => {
    await loadQueue();
    ui.showToast('Cola actualizada.', 'info');
  });
}

async function loadSedes() {
  const sedeSelect = document.getElementById('nursingSedeSelect');
  try {
    const rawSedes = await emergencyApi.listarSedes();
    const sedes = Array.isArray(rawSedes) ? rawSedes : (rawSedes?.content || []);
    dashboardState.sedes = sedes || [];
    if (sedes && sedes.length > 0) {
      dashboardState.selectedSedePublicId = sedes[0].publicId;
      sedeSelect.innerHTML = sedes.map(s => `
        <option value="${esc(s.publicId)}">${esc(s.nombre)} (${esc(s.ciudad || 'Valledupar')})</option>
      `).join('');
      await loadQueue();
    } else {
      sedeSelect.innerHTML = `<option value="">Sin sedes disponibles</option>`;
    }
  } catch (err) {
    ui.showToast('No fue posible cargar las sedes: ' + (err.message || 'Error de red'), 'error');
  }
}

async function loadQueue() {
  const tbody = document.getElementById('urgencyQueueBody');
  if (!dashboardState.selectedSedePublicId) {
    tbody.innerHTML = `<tr><td colspan="7" class="text-center p-8 text-muted">Seleccione una sede para visualizar la cola.</td></tr>`;
    return;
  }

  tbody.innerHTML = `<tr><td colspan="7" class="text-center p-8 text-muted">Cargando episodios de urgencias...</td></tr>`;

  try {
    const rawQueue = await emergencyApi.listarColaUrgencias(
      dashboardState.selectedSedePublicId,
      dashboardState.filterEstado || null
    );
    dashboardState.queue = Array.isArray(rawQueue) ? rawQueue : (rawQueue?.items || rawQueue?.content || []);
    renderQueue();
    updateKpis();
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="7" class="text-center p-8 text-danger">Error al cargar la cola de urgencias: ${esc(err.message)}</td></tr>`;
  }
}

function updateKpis() {
  const queue = dashboardState.queue;
  const total = queue.length;
  const criticos = queue.filter(q => q.nivelTriaje === 'I' || q.nivelTriaje === 'II').length;
  const pendientes = queue.filter(q => !q.nivelTriaje || q.nivelTriaje === 'PENDIENTE_VALORACION' || q.estado === 'REGISTRADO' || q.estadoEpisodio === 'REGISTRADO').length;
  const nn = queue.filter(q => q.codigoProvisional != null || q.esIdentidadProvisional || q.esNN).length;

  document.getElementById('kpiTotal').textContent = total;
  document.getElementById('kpiCriticos').textContent = criticos;
  document.getElementById('kpiPendientesTriaje').textContent = pendientes;
  document.getElementById('kpiNN').textContent = nn;
}

function renderQueue() {
  const tbody = document.getElementById('urgencyQueueBody');
  const queue = dashboardState.queue;

  if (queue.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" class="text-center p-12 text-muted">
          ${ui.icon('check-circle', 'icon icon--lg mb-2', { style: 'color: var(--primary);' })}
          <p class="font-medium m-0">No hay pacientes en cola de urgencias para los filtros seleccionados.</p>
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = queue.map(item => {
    const isNN = item.codigoProvisional != null || item.esIdentidadProvisional || item.esNN;
    const waitClass = getWaitAlertClass(item.nivelTriaje, item.minutosEspera);
    const epId = item.episodioPublicId || item.episodioId;
    const nombre = item.pacienteNombre || item.identificadorVisible || 'Paciente No Identificado';
    const motivo = item.motivoConsulta || item.motivoLlegada || item.motivoIngreso || '—';
    const estado = item.estado || item.estadoEpisodio || 'REGISTRADO';
    const medico = item.medicoAsignado || item.profesionalTratanteNombre || 'Sin asignar';

    return `
      <tr>
        <td>${formatTriageBadge(item.nivelTriaje)}</td>
        <td>
          <div class="font-bold flex items-center gap-1">
            ${isNN ? `<span class="badge badge--warning text-xs">NN</span>` : ''}
            <span>${esc(nombre)}</span>
          </div>
          ${isNN ? `<div class="text-xs text-muted font-mono">${esc(item.codigoProvisional)}</div>` : ''}
        </td>
        <td style="max-width: 250px;">
          <div class="text-sm truncate" title="${esc(motivo)}">${esc(motivo)}</div>
        </td>
        <td>
          <span class="${waitClass}">${formatWaitTime(item.minutosEspera)}</span>
        </td>
        <td>
          <span class="badge ${estado === 'EN_ATENCION' ? 'badge--in-progress' : 'badge--scheduled'}">
            ${esc(estado)}
          </span>
        </td>
        <td>
          <span class="text-sm text-muted">${esc(medico)}</span>
        </td>
        <td class="text-right">
          <div class="flex items-center justify-end gap-1">
            <a href="#/nursing/assessment/${esc(epId)}" class="btn btn-ghost btn--xs" title="Valorar o reevaluar triaje">
              ${ui.icon('clipboard', 'icon icon--sm')}
              <span>Triaje</span>
            </a>

            ${isNN ? `
              <a href="#/nursing/identity/${esc(epId)}" class="btn btn-ghost btn--xs" title="Reconciliar identidad NN">
                ${ui.icon('user-check', 'icon icon--sm')}
                <span>Identidad</span>
              </a>
            ` : ''}

            <button type="button" class="btn btn-ghost btn--xs btn-close-episode" data-id="${esc(epId)}" title="Egresar o cerrar episodio">
              ${ui.icon('log-out', 'icon icon--sm')}
            </button>
          </div>
        </td>
      </tr>
    `;
  }).join('');

  // Botones de cierre de episodio
  tbody.querySelectorAll('.btn-close-episode').forEach(btn => {
    btn.addEventListener('click', () => {
      const epId = btn.dataset.id;
      ui.showModal({
        title: 'Egresar Paciente de Urgencias',
        message: '¿Confirma el cierre y egreso asistencial de este episodio de atención?',
        confirmText: 'Sí, egresar',
        cancelText: 'Cancelar',
        onConfirm: async () => {
          try {
            await emergencyApi.cerrarEpisodio(epId);
            ui.showToast('Episodio de urgencias egresado correctamente.', 'success');
            await loadQueue();
          } catch (err) {
            ui.showToast('Error al egresar episodio: ' + (err.message || 'Error de servidor'), 'error');
          }
        }
      });
    });
  });
}
