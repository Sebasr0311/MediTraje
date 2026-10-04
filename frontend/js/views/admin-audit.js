/**
 * MediTriaje 2.0 — Visor de Auditoría de Seguridad (admin-audit.js)
 * F2.7, RF-26, RNF-11, ADR-007, ADR-011, ADR-019.
 * Supervisión técnica e inmutable de la bitácora de eventos del sistema.
 * REGLA ESTRICTA: Cero contenido clínico ni diagnósticos individuales (ADR-007).
 */

import { api } from '../api.js';
import { ui, esc } from '../ui.js';

/** Fecha de hoy en formato YYYY-MM-DD en zona America/Bogota. */
function todayBogota() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date());
}

/** Formatea fecha y hora en zona America/Bogota. */
function formatDateTimeBogota(isoString) {
  if (!isoString) return '—';
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: true
    }).format(new Date(isoString));
  } catch {
    return isoString;
  }
}

/** Badge según el resultado del evento auditado. */
function resultadoBadge(resultado) {
  const res = (resultado || '').toUpperCase();
  if (res === 'EXITO') {
    return `<span class="badge badge--confirmed flex items-center gap-1">${ui.icon('check', 'icon icon--xs')} ÉXITO</span>`;
  }
  if (res === 'FALLO') {
    return `<span class="badge badge--cancelled flex items-center gap-1">${ui.icon('x', 'icon icon--xs')} FALLO</span>`;
  }
  if (res === 'BLOQUEADO') {
    return `<span class="badge badge--scheduled flex items-center gap-1">${ui.icon('shield-alert', 'icon icon--xs')} BLOQUEADO</span>`;
  }
  return `<span class="badge badge--neutral">${esc(resultado)}</span>`;
}

/** Trunca identificadores largos conservando prefijo legible. */
function truncateText(text, maxLen = 14) {
  if (!text) return '—';
  if (text.length <= maxLen) return text;
  return text.substring(0, maxLen) + '…';
}

/**
 * Renderiza la pantalla del Visor de Auditoría de Seguridad.
 */
export async function renderAudit(container) {
  let page = 0;
  const size = 15;
  const hoy = todayBogota();
  let fechaDesde = '';
  let fechaHasta = '';
  let accionFiltro = '';
  let resultadoFiltro = '';

  container.innerHTML = `
    <div class="audit-view">
      <!-- Encabezado de la bitácora -->
      <div class="card mb-6">
        <div class="flex justify-between items-center flex-wrap gap-4">
          <div>
            <h2 class="text-xl font-bold mb-1 flex items-center gap-2">
              ${ui.icon('shield', 'icon icon--md text-primary')}
              <span>Bitácora de Auditoría de Seguridad</span>
            </h2>
            <p class="text-sm text-muted m-0">
              Supervisión técnica e inmutable de eventos, autenticaciones y operaciones del sistema (RF-26, RNF-11, ADR-019).
            </p>
          </div>
          <div class="badge badge--neutral flex items-center gap-1">
            ${ui.icon('lock', 'icon icon--xs')}
            <span>ADR-007: Cero acceso a datos clínicos</span>
          </div>
        </div>

        <!-- Filtros de búsqueda -->
        <div class="mt-6 pt-4 border-t">
          <form id="auditFilterForm" class="grid grid-cols-1 grid-cols-2-md grid-cols-5-lg gap-3 items-end">
            <div class="form-group m-0">
              <label for="auditDesde" class="form-label text-xs">Desde:</label>
              <input type="date" id="auditDesde" class="form-input form-input--sm" max="${hoy}">
            </div>
            <div class="form-group m-0">
              <label for="auditHasta" class="form-label text-xs">Hasta:</label>
              <input type="date" id="auditHasta" class="form-input form-input--sm" max="${hoy}">
            </div>
            <div class="form-group m-0">
              <label for="auditAccion" class="form-label text-xs">Acción:</label>
              <select id="auditAccion" class="form-select form-select--sm">
                <option value="">Todas las acciones</option>
                <optgroup label="Autenticación y Sesión">
                  <option value="LOGIN_EXITOSO">LOGIN_EXITOSO</option>
                  <option value="LOGIN_FALLIDO">LOGIN_FALLIDO</option>
                  <option value="LOGOUT">LOGOUT</option>
                  <option value="CAMBIO_PASSWORD">CAMBIO_PASSWORD</option>
                  <option value="SOLICITUD_RESET_PASSWORD">SOLICITUD_RESET_PASSWORD</option>
                  <option value="RESET_PASSWORD_EXITOSO">RESET_PASSWORD_EXITOSO</option>
                  <option value="MFA_ACTIVADO">MFA_ACTIVADO</option>
                  <option value="MFA_DESACTIVADO">MFA_DESACTIVADO</option>
                </optgroup>
                <optgroup label="Administración e Infraestructura">
                  <option value="REGISTRO_PACIENTE">REGISTRO_PACIENTE</option>
                  <option value="ALTA_PROFESIONAL">ALTA_PROFESIONAL</option>
                  <option value="CAMBIO_ADMINISTRATIVO">CAMBIO_ADMINISTRATIVO</option>
                </optgroup>
                <optgroup label="Citas y Triaje">
                  <option value="RESERVA_CITA">RESERVA_CITA</option>
                  <option value="CONFIRMACION_CITA">CONFIRMACION_CITA</option>
                  <option value="CANCELACION_CITA">CANCELACION_CITA</option>
                  <option value="REPROGRAMACION_CITA">REPROGRAMACION_CITA</option>
                  <option value="TRIAJE_REALIZADO">TRIAJE_REALIZADO</option>
                  <option value="TRIAJE_EMERGENCIA">TRIAJE_EMERGENCIA</option>
                </optgroup>
                <optgroup label="Atención Clínica y Farmacia">
                  <option value="CREACION_ATENCION">CREACION_ATENCION</option>
                  <option value="ENMIENDA_ATENCION">ENMIENDA_ATENCION</option>
                  <option value="ACCESO_HISTORIA_CLINICA">ACCESO_HISTORIA_CLINICA</option>
                  <option value="CREACION_RECETA">CREACION_RECETA</option>
                  <option value="DISPENSACION_PARCIAL">DISPENSACION_PARCIAL</option>
                  <option value="DISPENSACION_TOTAL">DISPENSACION_TOTAL</option>
                  <option value="ACCESO_EMERGENCIA_BREAK_GLASS">ACCESO_EMERGENCIA_BREAK_GLASS</option>
                </optgroup>
              </select>
            </div>
            <div class="form-group m-0">
              <label for="auditResultado" class="form-label text-xs">Resultado:</label>
              <select id="auditResultado" class="form-select form-select--sm">
                <option value="">Todos los resultados</option>
                <option value="EXITO">ÉXITO</option>
                <option value="FALLO">FALLO</option>
                <option value="BLOQUEADO">BLOQUEADO</option>
              </select>
            </div>
            <div class="flex items-center gap-2">
              <button type="submit" id="btnFiltrarAudit" class="btn btn-primary btn--sm flex-1">
                ${ui.icon('search', 'icon icon--sm')}
                <span>Filtrar</span>
              </button>
              <button type="button" id="btnLimpiarAudit" class="btn btn-secondary btn--sm" title="Limpiar filtros">
                ${ui.icon('x', 'icon icon--sm')}
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- Contenedor dinámico de resultados -->
      <div id="auditTableContainer" aria-live="polite"></div>
    </div>
  `;

  const filterForm = container.querySelector('#auditFilterForm');
  const desdeInput = container.querySelector('#auditDesde');
  const hastaInput = container.querySelector('#auditHasta');
  const accionSelect = container.querySelector('#auditAccion');
  const resultadoSelect = container.querySelector('#auditResultado');
  const btnLimpiar = container.querySelector('#btnLimpiarAudit');
  const tableContainer = container.querySelector('#auditTableContainer');

  async function cargarEventos() {
    ui.renderLoading(tableContainer, 'Consultando bitácora inmutable de auditoría...');
    try {
      const params = {
        page,
        size
      };
      if (fechaDesde) params.desde = fechaDesde;
      if (fechaHasta) params.hasta = fechaHasta;
      if (accionFiltro) params.accion = accionFiltro;
      if (resultadoFiltro) params.resultado = resultadoFiltro;

      const data = await api.get('/api/v1/admin/audit', params);
      renderTable(data);
    } catch (err) {
      ui.renderError(tableContainer, {
        title: 'Error al consultar la bitácora',
        message: err.message || 'No fue posible acceder a los registros de auditoría.',
        retryText: 'Reintentar',
        onRetry: () => cargarEventos()
      });
    }
  }

  function renderTable(data) {
    const list = data?.content || [];
    const total = data?.totalElements || 0;
    const totalPages = Math.max(1, data?.totalPages || 1);

    if (list.length === 0) {
      ui.renderEmpty(tableContainer, {
        icon: 'shield',
        title: 'Sin eventos de auditoría',
        description: 'No se encontraron eventos en la bitácora con los criterios de búsqueda especificados.'
      });
      return;
    }

    tableContainer.innerHTML = `
      <div class="card p-0 mb-4 overflow-hidden">
        <div class="p-3 border-b bg-surface-2 flex justify-between items-center text-xs">
          <span class="font-medium text-slate-700">Eventos de Bitácora Registrados</span>
          <span class="text-muted">Total: <strong>${total}</strong> registros</span>
        </div>
        <div class="table-container m-0">
          <table class="table table--stacked">
            <thead>
              <tr>
                <th style="width: 70px;">ID</th>
                <th>Fecha / Hora (Bogotá)</th>
                <th>Usuario</th>
                <th>Acción</th>
                <th>Tipo Recurso</th>
                <th>ID Recurso</th>
                <th>Resultado</th>
                <th>IP Origen</th>
              </tr>
            </thead>
            <tbody>
              ${list.map(r => `
                <tr>
                  <td data-label="ID"><span class="font-mono text-xs text-muted">#${r.id}</span></td>
                  <td data-label="Fecha / Hora"><span class="text-xs whitespace-nowrap">${formatDateTimeBogota(r.fechaHora)}</span></td>
                  <td data-label="Usuario">
                    <span class="text-xs font-semibold text-slate-800" title="${esc(r.usuarioEmail || '')}">
                      ${esc(r.usuarioEmail || 'Sistema / Anónimo')}
                    </span>
                  </td>
                  <td data-label="Acción">
                    <span class="badge badge--neutral text-xs font-mono">${esc(r.accion)}</span>
                  </td>
                  <td data-label="Tipo Recurso">
                    <span class="text-xs text-muted font-medium">${esc(r.tipoRecurso || '—')}</span>
                  </td>
                  <td data-label="ID Recurso">
                    ${r.recursoPublicId ? `
                      <span class="font-mono text-xs text-muted" title="${esc(r.recursoPublicId)}">
                        ${esc(truncateText(r.recursoPublicId, 12))}
                      </span>
                    ` : '<span class="text-muted text-xs">—</span>'}
                  </td>
                  <td data-label="Resultado">${resultadoBadge(r.resultado)}</td>
                  <td data-label="IP Origen"><span class="font-mono text-xs text-muted">${esc(r.ipOrigen || '—')}</span></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      </div>

      <!-- Barra de Paginación Accesible -->
      <div class="flex items-center justify-between gap-3 flex-wrap">
        <button type="button" id="btnAuditPrev" class="btn btn-secondary btn--sm" ${!data?.hasPrevious ? 'disabled' : ''}>
          ${ui.icon('arrow-left', 'icon icon--xs')}
          <span>Anterior</span>
        </button>
        <span class="text-xs text-muted">
          Página <strong>${page + 1}</strong> de <strong>${totalPages}</strong> (${total} eventos en total)
        </span>
        <button type="button" id="btnAuditNext" class="btn btn-secondary btn--sm" ${!data?.hasNext ? 'disabled' : ''}>
          <span>Siguiente</span>
          ${ui.icon('arrow-right', 'icon icon--xs')}
        </button>
      </div>
    `;

    tableContainer.querySelector('#btnAuditPrev')?.addEventListener('click', () => {
      if (page > 0) {
        page--;
        cargarEventos();
      }
    });

    tableContainer.querySelector('#btnAuditNext')?.addEventListener('click', () => {
      if (data?.hasNext) {
        page++;
        cargarEventos();
      }
    });
  }

  filterForm.addEventListener('submit', (e) => {
    e.preventDefault();
    fechaDesde = desdeInput.value || '';
    fechaHasta = hastaInput.value || '';
    if (fechaDesde && fechaHasta && fechaDesde > fechaHasta) {
      ui.showToast('La fecha desde no puede ser posterior a la fecha hasta.', 'error');
      return;
    }
    accionFiltro = accionSelect.value || '';
    resultadoFiltro = resultadoSelect.value || '';
    page = 0;
    cargarEventos();
  });

  btnLimpiar.addEventListener('click', () => {
    filterForm.reset();
    fechaDesde = '';
    fechaHasta = '';
    accionFiltro = '';
    resultadoFiltro = '';
    page = 0;
    cargarEventos();
  });

  await cargarEventos();
}
