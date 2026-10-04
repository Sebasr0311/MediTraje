/**
 * MediTriaje 2.0 — Reportes Operativos Administrativos (admin-reports.js)
 * F2.6, RF-30, ADR-007, ADR-018.
 * Analítica hospitalaria agregada: citas, triajes, farmacia y break-glass.
 * REGLA ESTRICTA: CERO exposición de historias clínicas o datos personales de pacientes.
 */

import { api } from '../api.js';
import { ui, esc } from '../ui.js';

/** Fecha de hoy en formato YYYY-MM-DD en zona America/Bogota. */
function todayBogota() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date());
}

/** Resta días a una fecha ISO YYYY-MM-DD. */
function subtractDays(isoDate, days) {
  const d = new Date(isoDate + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() - days);
  return d.toISOString().slice(0, 10);
}

/**
 * Renderiza la vista de reportes operativos en el contenedor dado.
 */
export async function renderReports(container) {
  const hoy = todayBogota();
  let fechaDesde = subtractDays(hoy, 30);
  let fechaHasta = hoy;

  container.innerHTML = `
    <div class="reports-view">
      <!-- Encabezado de la vista -->
      <div class="card mb-6">
        <div class="flex justify-between items-center flex-wrap gap-4">
          <div>
            <h2 class="text-xl font-bold mb-1 flex items-center gap-2">
              ${ui.icon('bar-chart', 'icon icon--md text-primary')}
              <span>Indicadores y Reportes Operativos</span>
            </h2>
            <p class="text-sm text-muted m-0">
              Analítica hospitalaria agregada para evaluación de demanda, triajes, farmacia y accesos de emergencia.
            </p>
          </div>
          <div class="flex items-center gap-3">
            <button type="button" id="btnExportarCsv" class="btn btn-secondary btn--sm" title="Descargar reporte operativo en formato CSV">
              ${ui.icon('download', 'icon icon--sm')}
              <span>Exportar CSV</span>
            </button>
            <div class="badge badge--neutral flex items-center gap-1">
              ${ui.icon('shield', 'icon icon--xs')}
              <span>ADR-007: Cero acceso a datos clínicos</span>
            </div>
          </div>
        </div>

        <!-- Barra de Filtros y Rango de Fechas -->
        <div class="mt-6 pt-4 border-t flex flex-wrap items-center justify-between gap-4">
          <form id="reportsFilterForm" class="flex flex-wrap items-center gap-3">
            <div class="flex items-center gap-2">
              <label for="repDesde" class="text-xs font-semibold text-muted">Desde:</label>
              <input type="date" id="repDesde" class="input input--sm" value="${fechaDesde}" max="${hoy}">
            </div>
            <div class="flex items-center gap-2">
              <label for="repHasta" class="text-xs font-semibold text-muted">Hasta:</label>
              <input type="date" id="repHasta" class="input input--sm" value="${fechaHasta}" max="${hoy}">
            </div>
            <button type="submit" id="btnFiltrarReportes" class="btn btn-primary btn--sm">
              ${ui.icon('refresh-cw', 'icon icon--sm')}
              <span>Actualizar</span>
            </button>
          </form>

          <!-- Atajos rápidos de rango -->
          <div class="flex flex-wrap items-center gap-2">
            <button type="button" class="btn btn-ghost btn--sm btn-rango" data-dias="0">Hoy</button>
            <button type="button" class="btn btn-ghost btn--sm btn-rango" data-dias="7">7 días</button>
            <button type="button" class="btn btn-secondary btn--sm btn-rango is-selected" data-dias="30">30 días</button>
            <button type="button" class="btn btn-ghost btn--sm btn-rango" data-dias="all">Histórico</button>
          </div>
        </div>
      </div>

      <!-- Contenedor dinámico de métricas -->
      <div id="reportsDataContainer" aria-live="polite">
        <div class="p-8 text-center text-muted">
          <span class="spinner spinner--lg mb-3"></span>
          <p>Cargando indicadores operativos...</p>
        </div>
      </div>
    </div>
  `;

  let datosReporteActual = null;
  const dataContainer = container.querySelector('#reportsDataContainer');
  const filterForm = container.querySelector('#reportsFilterForm');
  const desdeInput = container.querySelector('#repDesde');
  const hastaInput = container.querySelector('#repHasta');
  const rangoButtons = container.querySelectorAll('.btn-rango');
  const exportBtn = container.querySelector('#btnExportarCsv');

  exportBtn?.addEventListener('click', () => {
    if (!datosReporteActual) {
      ui.showToast('Espere a que carguen los datos antes de exportar.', 'warning');
      return;
    }
    exportarReporteCsv(datosReporteActual, fechaDesde, fechaHasta);
    ui.showToast('Reporte CSV generado y descargado correctamente.', 'success');
  });

  async function cargarMetricas() {
    ui.renderLoading(dataContainer, 'Calculando métricas agregadas...');
    try {
      const params = {};
      if (fechaDesde) params.desde = fechaDesde;
      if (fechaHasta) params.hasta = fechaHasta;

      const data = await api.get('/api/v1/admin/reports/operational', params);
      datosReporteActual = data;
      renderMetricas(dataContainer, data);
    } catch (err) {
      ui.renderError(dataContainer, {
        title: 'Error al consultar reportes',
        message: err.message || 'No fue posible cargar las métricas operativas del sistema.',
        retryText: 'Reintentar',
        onRetry: () => cargarMetricas()
      });
    }
  }

  filterForm.addEventListener('submit', (e) => {
    e.preventDefault();
    fechaDesde = desdeInput.value || null;
    fechaHasta = hastaInput.value || null;
    if (fechaDesde && fechaHasta && fechaDesde > fechaHasta) {
      ui.showToast('La fecha desde no puede ser posterior a la fecha hasta.', 'error');
      return;
    }
    rangoButtons.forEach(b => b.classList.remove('is-selected', 'btn-secondary'));
    cargarMetricas();
  });

  rangoButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      rangoButtons.forEach(b => {
        b.classList.remove('is-selected', 'btn-secondary');
        b.classList.add('btn-ghost');
      });
      btn.classList.add('is-selected', 'btn-secondary');
      btn.classList.remove('btn-ghost');

      const dias = btn.dataset.dias;
      if (dias === 'all') {
        fechaDesde = null;
        fechaHasta = null;
        desdeInput.value = '';
        hastaInput.value = '';
      } else {
        const numDias = parseInt(dias, 10);
        fechaHasta = hoy;
        fechaDesde = numDias === 0 ? hoy : subtractDays(hoy, numDias);
        desdeInput.value = fechaDesde;
        hastaInput.value = fechaHasta;
      }
      cargarMetricas();
    });
  });

  await cargarMetricas();
}

/**
 * Renderiza las tarjetas y tablas analíticas con los datos agregados.
 */
function renderMetricas(target, data) {
  const citas = data.citas || {};
  const triaje = data.triaje || {};
  const farmacia = data.farmacia || {};
  const breakGlass = data.breakGlass || {};

  target.innerHTML = `
    <!-- 1. Tarjetas KPI de Resumen Global -->
    <div class="grid grid-4 gap-4 mb-6">
      <!-- KPI Citas -->
      <div class="card p-4">
        <div class="flex justify-between items-start mb-2">
          <span class="text-xs font-semibold text-muted uppercase">Total Citas</span>
          <div class="p-2 rounded bg-teal-50 text-primary">
            ${ui.icon('calendar', 'icon icon--sm')}
          </div>
        </div>
        <div class="text-3xl font-extrabold mb-1">${citas.totalCitas ?? 0}</div>
        <div class="text-xs flex items-center justify-between text-muted">
          <span>Cumplimiento: <strong class="text-success">${citas.tasaCumplimiento ?? 0}%</strong></span>
          <span>Cancelación: <strong class="text-danger">${citas.tasaCancelacion ?? 0}%</strong></span>
        </div>
      </div>

      <!-- KPI Triajes -->
      <div class="card p-4">
        <div class="flex justify-between items-start mb-2">
          <span class="text-xs font-semibold text-muted uppercase">Evaluaciones Triaje</span>
          <div class="p-2 rounded" style="background-color: var(--triage-1-bg); color: var(--triage-1-fg);">
            ${ui.icon('activity', 'icon icon--sm')}
          </div>
        </div>
        <div class="text-3xl font-extrabold mb-1">${triaje.totalTriajes ?? 0}</div>
        <div class="text-xs flex items-center justify-between text-muted">
          <span>Emergencias (Nivel I): <strong class="text-danger">${triaje.emergencias ?? 0}</strong></span>
          <span>Tasa: <strong>${triaje.tasaEmergencia ?? 0}%</strong></span>
        </div>
      </div>

      <!-- KPI Farmacia -->
      <div class="card p-4">
        <div class="flex justify-between items-start mb-2">
          <span class="text-xs font-semibold text-muted uppercase">Recetas Emitidas</span>
          <div class="p-2 rounded bg-info-bg text-info">
            ${ui.icon('pill', 'icon icon--sm')}
          </div>
        </div>
        <div class="text-3xl font-extrabold mb-1">${farmacia.totalRecetas ?? 0}</div>
        <div class="text-xs flex items-center justify-between text-muted">
          <span>Fármacos entregados: <strong>${farmacia.unidadesDispensadas ?? 0}</strong> un.</span>
          <span>Completas: <strong class="text-success">${farmacia.dispensadasTotal ?? 0}</strong></span>
        </div>
      </div>

      <!-- KPI Break-Glass -->
      <div class="card p-4">
        <div class="flex justify-between items-start mb-2">
          <span class="text-xs font-semibold text-muted uppercase">Acceso Emergencia (BG)</span>
          <div class="p-2 rounded bg-warning-bg text-warning">
            ${ui.icon('alert-triangle', 'icon icon--sm')}
          </div>
        </div>
        <div class="text-3xl font-extrabold mb-1">${breakGlass.totalActivaciones ?? 0}</div>
        <div class="text-xs flex items-center justify-between text-muted">
          <span>Ventanas activas (24h): <strong class="text-warning">${breakGlass.activacionesActivas ?? 0}</strong></span>
          <span>ADR-017 / 100% auditado</span>
        </div>
      </div>
    </div>

    <!-- 2. Sección Detallada: Citas Médicas y Triaje Clínico -->
    <div class="grid grid-2 gap-6 mb-6">
      <!-- Desglose de Citas por Estado -->
      <div class="card">
        <div class="card-header pb-3 border-b mb-4 flex justify-between items-center">
          <h3 class="text-base font-bold m-0 flex items-center gap-2">
            ${ui.icon('clock', 'icon icon--sm text-primary')}
            <span>Estados de Citas</span>
          </h3>
          <span class="text-xs text-muted">Total: ${citas.totalCitas ?? 0}</span>
        </div>
        <div class="flex flex-col gap-3">
          ${renderProgressBar('Atendidas', citas.atendidas ?? 0, citas.totalCitas, 'var(--success)')}
          ${renderProgressBar('Programadas', citas.programadas ?? 0, citas.totalCitas, 'var(--info)')}
          ${renderProgressBar('Confirmadas', citas.confirmadas ?? 0, citas.totalCitas, 'var(--teal-600)')}
          ${renderProgressBar('Canceladas', citas.canceladas ?? 0, citas.totalCitas, 'var(--danger)')}
          ${renderProgressBar('No Asistió', citas.noAsistio ?? 0, citas.totalCitas, 'var(--warning)')}
          ${renderProgressBar('Reprogramadas', citas.reprogramadas ?? 0, citas.totalCitas, 'var(--slate-500)')}
        </div>
      </div>

      <!-- Desglose de Triajes por Nivel de Prioridad -->
      <div class="card">
        <div class="card-header pb-3 border-b mb-4 flex justify-between items-center">
          <h3 class="text-base font-bold m-0 flex items-center gap-2">
            ${ui.icon('activity', 'icon icon--sm text-danger')}
            <span>Distribución de Triaje (Niveles I–V)</span>
          </h3>
          <span class="text-xs text-muted">Total: ${triaje.totalTriajes ?? 0}</span>
        </div>
        <div class="flex flex-col gap-3">
          ${renderProgressBar('Nivel I — Resucitación / Emergencia', triaje.nivel1 ?? 0, triaje.totalTriajes, 'var(--triage-1-bd)')}
          ${renderProgressBar('Nivel II — Emergencia / Prioritario', triaje.nivel2 ?? 0, triaje.totalTriajes, 'var(--triage-2-bd)')}
          ${renderProgressBar('Nivel III — Urgencia / Cita Presencial', triaje.nivel3 ?? 0, triaje.totalTriajes, 'var(--triage-3-bd)')}
          ${renderProgressBar('Nivel IV — Prioridad Menor / Telemedicina', triaje.nivel4 ?? 0, triaje.totalTriajes, 'var(--triage-4-bd)')}
          ${renderProgressBar('Nivel V — No Urgente / Consulta Externa', triaje.nivel5 ?? 0, triaje.totalTriajes, 'var(--triage-5-bd)')}
        </div>
      </div>
    </div>

    <!-- 3. Sección: Demanda por Especialidad y Sedes Hospitalarias -->
    <div class="grid grid-2 gap-6 mb-6">
      <!-- Citas por Especialidad -->
      <div class="card">
        <div class="card-header pb-3 border-b mb-4 flex justify-between items-center">
          <h3 class="text-base font-bold m-0 flex items-center gap-2">
            ${ui.icon('briefcase', 'icon icon--sm text-primary')}
            <span>Demanda por Especialidad</span>
          </h3>
          <span class="text-xs text-muted">Distribución</span>
        </div>
        ${renderDistributionList(citas.porEspecialidad)}
      </div>

      <!-- Citas por Sede -->
      <div class="card">
        <div class="card-header pb-3 border-b mb-4 flex justify-between items-center">
          <h3 class="text-base font-bold m-0 flex items-center gap-2">
            ${ui.icon('map-pin', 'icon icon--sm text-primary')}
            <span>Ocupación por Sede Hospitalaria</span>
          </h3>
          <span class="text-xs text-muted">Distribución</span>
        </div>
        ${renderDistributionList(citas.porSede)}
      </div>
    </div>

    <!-- 4. Sección: Farmacia y Auditoría Break-Glass -->
    <div class="grid grid-2 gap-6">
      <!-- Estados de Dispensación -->
      <div class="card">
        <div class="card-header pb-3 border-b mb-4 flex justify-between items-center">
          <h3 class="text-base font-bold m-0 flex items-center gap-2">
            ${ui.icon('package', 'icon icon--sm text-info')}
            <span>Dispensación de Medicamentos</span>
          </h3>
          <span class="text-xs text-muted">Recetas: ${farmacia.totalRecetas ?? 0}</span>
        </div>
        <div class="flex flex-col gap-3">
          ${renderProgressBar('Dispensada Total (Completas)', farmacia.dispensadasTotal ?? 0, farmacia.totalRecetas, 'var(--success)')}
          ${renderProgressBar('Dispensada Parcial', farmacia.dispensadasParcial ?? 0, farmacia.totalRecetas, 'var(--warning)')}
          ${renderProgressBar('Pendiente por Dispensar', farmacia.recetasPendientes ?? 0, farmacia.totalRecetas, 'var(--info)')}
        </div>
        <div class="mt-4 pt-3 border-t text-xs text-muted flex justify-between">
          <span>Unidades entregadas en ventanilla:</span>
          <strong class="text-base font-bold text-primary">${farmacia.unidadesDispensadas ?? 0}</strong>
        </div>
      </div>

      <!-- Auditoría de Activaciones Break-Glass por Especialidad -->
      <div class="card">
        <div class="card-header pb-3 border-b mb-4 flex justify-between items-center">
          <h3 class="text-base font-bold m-0 flex items-center gap-2">
            ${ui.icon('shield-alert', 'icon icon--sm text-warning')}
            <span>Break-Glass por Especialidad</span>
          </h3>
          <span class="text-xs text-muted">Comité Asistencial</span>
        </div>
        ${renderDistributionList(breakGlass.porEspecialidad, 'No se registran accesos Break-Glass en este rango.')}
        <div class="mt-4 pt-3 border-t text-xs text-muted">
          ${ui.icon('info', 'icon icon--xs mr-1')}
          <span>Todas las activaciones cuentan con justificación clínica firmada y expiración en 24h.</span>
        </div>
      </div>
    </div>
  `;
}

/**
 * Renderiza una barra de progreso porcentual accesible.
 */
function renderProgressBar(etiqueta, valor, total, colorHex) {
  const tot = total || 0;
  const pct = tot > 0 ? Math.round((valor * 100) / tot) : 0;

  return `
    <div>
      <div class="flex justify-between items-center text-xs mb-1">
        <span class="font-medium text-slate-700">${esc(etiqueta)}</span>
        <span class="text-muted"><strong>${valor}</strong> (${pct}%)</span>
      </div>
      <div style="background-color: var(--slate-100); height: 8px; border-radius: 4px; overflow: hidden;" role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100">
        <div style="background-color: ${colorHex}; width: ${pct}%; height: 100%; transition: width 0.3s ease;"></div>
      </div>
    </div>
  `;
}

/**
 * Renderiza una lista o tabla de items distribuidos porcentualmente.
 */
function renderDistributionList(items, emptyMsg = 'No hay datos registrados en este rango de fechas.') {
  if (!items || items.length === 0) {
    return `<p class="text-xs text-muted my-4 text-center">${esc(emptyMsg)}</p>`;
  }

  return `
    <div class="flex flex-col gap-2">
      ${items.map(it => `
        <div class="flex items-center justify-between p-2 rounded bg-surface-2 text-xs">
          <span class="font-medium text-slate-800 truncate" style="max-width: 60%;">${esc(it.etiqueta)}</span>
          <div class="flex items-center gap-3">
            <span class="badge badge--neutral">${it.cantidad} reg.</span>
            <span class="font-semibold text-muted" style="min-width: 45px; text-align: right;">${it.porcentaje}%</span>
          </div>
        </div>
      `).join('')}
    </div>
  `;
}

/**
 * Exporta las métricas operativas agregadas a un archivo plano CSV (RF-30, ADR-019).
 * Incluye BOM UTF-8 para compatibilidad nativa con Microsoft Excel.
 */
function exportarReporteCsv(data, fechaDesde, fechaHasta) {
  const rows = [];
  rows.push(['REPORTE OPERATIVO AGREGADO — MEDITRIAJE 2.0']);
  rows.push(['Rango Desde', fechaDesde || 'Inicio Historico']);
  rows.push(['Rango Hasta', fechaHasta || 'Actualidad']);
  rows.push(['Fecha de Generacion (Bogota)', new Intl.DateTimeFormat('es-CO', {
    timeZone: 'America/Bogota',
    dateStyle: 'full',
    timeStyle: 'medium'
  }).format(new Date())]);
  rows.push(['Politica de Privacidad', 'ADR-007: Cero contenido clinico ni identificadores individuales']);
  rows.push([]);

  rows.push(['SECCION', 'INDICADOR', 'CANTIDAD', 'METRICA']);
  const citas = data.citas || {};
  rows.push(['CITAS', 'Total Citas Agendadas', citas.totalCitas ?? 0, '100%']);
  rows.push(['CITAS', 'Citas Atendidas', citas.atendidas ?? 0, `${citas.tasaCumplimiento ?? 0}%`]);
  rows.push(['CITAS', 'Citas Programadas', citas.programadas ?? 0, '']);
  rows.push(['CITAS', 'Citas Confirmadas', citas.confirmadas ?? 0, '']);
  rows.push(['CITAS', 'Citas Canceladas', citas.canceladas ?? 0, `${citas.tasaCancelacion ?? 0}%`]);
  rows.push(['CITAS', 'No Asistio', citas.noAsistio ?? 0, '']);
  rows.push(['CITAS', 'Reprogramadas', citas.reprogramadas ?? 0, '']);
  rows.push([]);

  const triaje = data.triaje || {};
  rows.push(['TRIAJE', 'Total Triajes Evaluados', triaje.totalTriajes ?? 0, '100%']);
  rows.push(['TRIAJE', 'Nivel I — Resucitacion / Emergencia', triaje.nivel1 ?? 0, `${triaje.tasaEmergencia ?? 0}%`]);
  rows.push(['TRIAJE', 'Nivel II — Emergencia / Prioritario', triaje.nivel2 ?? 0, '']);
  rows.push(['TRIAJE', 'Nivel III — Urgencia / Cita Presencial', triaje.nivel3 ?? 0, '']);
  rows.push(['TRIAJE', 'Nivel IV — Prioridad Menor / Telemedicina', triaje.nivel4 ?? 0, '']);
  rows.push(['TRIAJE', 'Nivel V — No Urgente / Consulta Externa', triaje.nivel5 ?? 0, '']);
  rows.push([]);

  const farmacia = data.farmacia || {};
  rows.push(['FARMACIA', 'Total Recetas Emitidas', farmacia.totalRecetas ?? 0, '']);
  rows.push(['FARMACIA', 'Dispensadas Total (Completas)', farmacia.dispensadasTotal ?? 0, '']);
  rows.push(['FARMACIA', 'Dispensadas Parcial', farmacia.dispensadasParcial ?? 0, '']);
  rows.push(['FARMACIA', 'Pendientes por Dispensar', farmacia.recetasPendientes ?? 0, '']);
  rows.push(['FARMACIA', 'Unidades de Farmacos Entregadas', farmacia.unidadesDispensadas ?? 0, '']);
  rows.push([]);

  const breakGlass = data.breakGlass || {};
  rows.push(['BREAK_GLASS', 'Total Activaciones Emergencia', breakGlass.totalActivaciones ?? 0, '']);
  rows.push(['BREAK_GLASS', 'Ventanas Activas (24h)', breakGlass.activacionesActivas ?? 0, '']);
  rows.push([]);

  rows.push(['CITAS POR ESPECIALIDAD', 'Especialidad', 'Cantidad', 'Porcentaje']);
  (citas.porEspecialidad || []).forEach(it => {
    rows.push(['CITAS POR ESPECIALIDAD', it.etiqueta, it.cantidad, `${it.porcentaje}%`]);
  });
  rows.push([]);

  rows.push(['CITAS POR SEDE', 'Sede Hospitalaria', 'Cantidad', 'Porcentaje']);
  (citas.porSede || []).forEach(it => {
    rows.push(['CITAS POR SEDE', it.etiqueta, it.cantidad, `${it.porcentaje}%`]);
  });
  rows.push([]);

  rows.push(['BREAK_GLASS POR ESPECIALIDAD', 'Especialidad Solicitante', 'Cantidad', 'Porcentaje']);
  (breakGlass.porEspecialidad || []).forEach(it => {
    rows.push(['BREAK_GLASS POR ESPECIALIDAD', it.etiqueta, it.cantidad, `${it.porcentaje}%`]);
  });

  const csvContent = '\uFEFF' + rows.map(r => r.map(c => `"${String(c ?? '').replace(/"/g, '""')}"`).join(',')).join('\r\n');
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.setAttribute('href', url);
  const fDesde = (fechaDesde || 'historico').replace(/[^0-9a-zA-Z_-]/g, '');
  const fHasta = (fechaHasta || 'actual').replace(/[^0-9a-zA-Z_-]/g, '');
  link.setAttribute('download', `reporte_operativo_${fDesde}_${fHasta}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
