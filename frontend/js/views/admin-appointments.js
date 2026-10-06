/**
 * MediTriaje 2.0 — Calendario Semanal y Exportación de Citas en Panel Admin (admin-appointments.js)
 * Visualización dinámica en tiempo real por semana y día, inspección de detalles de cita
 * y exportación a plantillas compatibles con Microsoft Excel (BOM UTF-8) (ADR-003, ADR-006, RF-26).
 * REGLA ESTRICTA: El rol administrativo no accede a historias clínicas ni datos clínicos privados (ADR-007).
 */

import { api } from '../api.js';
import { ui, esc } from '../ui.js';

/** Fecha de hoy en formato YYYY-MM-DD en zona America/Bogota. */
function todayBogota() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date());
}

/** Formatea hora en zona America/Bogota. */
function formatTimeBogota(isoString) {
  if (!isoString) return '—';
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    }).format(new Date(isoString));
  } catch {
    return isoString;
  }
}

/** Formatea fecha corta en zona America/Bogota. */
function formatDateShortBogota(isoString) {
  if (!isoString) return '—';
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'short'
    }).format(new Date(isoString));
  } catch {
    return isoString;
  }
}

/** Formatea fecha larga en zona America/Bogota. */
function formatDateLongBogota(isoString) {
  if (!isoString) return '—';
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    }).format(new Date(isoString));
  } catch {
    return isoString;
  }
}

/** Suma o resta días a una fecha ISO YYYY-MM-DD en UTC. */
function addDays(isoDate, days) {
  const [y, m, d] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + days));
  return date.toISOString().slice(0, 10);
}

/** Obtiene el lunes y domingo de la semana a la que pertenece la fecha ISO. */
function getWeekRange(isoDate) {
  const [y, m, d] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  // getUTCDay: 0=Domingo, 1=Lunes, ..., 6=Sábado
  const day = date.getUTCDay();
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = addDays(isoDate, diffToMonday);
  const sunday = addDays(monday, 6);
  return { monday, sunday };
}

/** Genera la lista de los 7 días de la semana (Lunes a Domingo) para la fecha ISO. */
function getWeekDays(isoDate) {
  const { monday } = getWeekRange(isoDate);
  const days = [];
  const nombres = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
  for (let i = 0; i < 7; i++) {
    const dStr = addDays(monday, i);
    days.push({
      dateStr: dStr,
      shortName: nombres[i],
      isToday: dStr === todayBogota()
    });
  }
  return days;
}

/** Badge visual para el estado de una cita médica. */
function citaStatusBadge(estado) {
  const est = (estado || '').toUpperCase();
  if (est === 'PROGRAMADA') {
    return `<span class="badge badge--scheduled">${ui.icon('clock', 'icon icon--xs')} Programada</span>`;
  }
  if (est === 'CONFIRMADA') {
    return `<span class="badge badge--confirmed">${ui.icon('check', 'icon icon--xs')} Confirmada</span>`;
  }
  if (est === 'ATENDIDA') {
    return `<span class="badge badge--attended">${ui.icon('activity', 'icon icon--xs')} Atendida</span>`;
  }
  if (est === 'CANCELADA') {
    return `<span class="badge badge--cancelled">${ui.icon('x', 'icon icon--xs')} Cancelada</span>`;
  }
  if (est === 'NO_ASISTIO') {
    return `<span class="badge badge--cancelled">${ui.icon('alert-circle', 'icon icon--xs')} No asistió</span>`;
  }
  if (est === 'REPROGRAMADA') {
    return `<span class="badge badge--rescheduled">${ui.icon('calendar', 'icon icon--xs')} Reprogramada</span>`;
  }
  return `<span class="badge badge--neutral">${esc(estado)}</span>`;
}

/**
 * Renderiza la sección completa del Calendario Semanal y Exportación de Citas en el panel Admin.
 */
export async function renderAdminAppointments(container) {
  const state = {
    viewMode: 'week', // 'week' | 'day'
    selectedDate: todayBogota(),
    filtroEstado: '',
    filtroEspecialidad: '',
    citas: [],
    loading: false
  };

  container.innerHTML = `
    <div class="card mb-6">
      <div class="card-body">
        <!-- Barra Superior de Control: Título, Vistas y Exportar -->
        <div class="flex flex-wrap items-center justify-between gap-4 mb-5 pb-4 border-b">
          <div>
            <h2 class="text-xl font-bold m-0 flex items-center gap-2">
              ${ui.icon('calendar', 'icon icon--md text-primary')}
              <span>Calendario de Citas y Supervisión</span>
            </h2>
            <p class="text-xs text-muted m-0">
              Supervisión de agendamiento en tiempo real, navegación semanal/diaria y exportación para reportes
            </p>
          </div>

          <div class="flex flex-wrap items-center gap-2">
            <!-- Selector de Modo de Vista (Semanal vs Diario) -->
            <div class="btn-group" role="group" aria-label="Modo de visualización">
              <button type="button" class="btn btn--sm ${state.viewMode === 'week' ? 'btn-primary' : 'btn-secondary'}" id="btnViewWeek">
                ${ui.icon('calendar')}<span>Semana</span>
              </button>
              <button type="button" class="btn btn--sm ${state.viewMode === 'day' ? 'btn-primary' : 'btn-secondary'}" id="btnViewDay">
                ${ui.icon('clock')}<span>Día</span>
              </button>
            </div>

            <!-- Botón Exportar a Excel -->
            <button type="button" class="btn btn-primary btn--sm" id="btnOpenExportModal">
              ${ui.icon('download')}
              <span>Exportar a Excel</span>
            </button>
          </div>
        </div>

        <!-- Barra de Navegación de Fechas y Filtros -->
        <div class="grid grid-cols-1 grid-cols-3-md gap-4 items-end mb-4">
          <!-- Controles de Navegación Temporal -->
          <div class="flex items-center gap-2">
            <button type="button" class="btn btn-secondary btn--sm btn--icon-only" id="btnPrevDate" title="Período anterior" aria-label="Período anterior">
              ${ui.icon('chevron-left')}
            </button>
            <button type="button" class="btn btn-secondary btn--sm" id="btnTodayDate">
              <span>Hoy</span>
            </button>
            <button type="button" class="btn btn-secondary btn--sm btn--icon-only" id="btnNextDate" title="Período siguiente" aria-label="Período siguiente">
              ${ui.icon('chevron-right')}
            </button>
            <input type="date" id="inputJumpDate" class="form-input text-xs" style="max-width: 140px;" value="${state.selectedDate}">
          </div>

          <!-- Filtro de Estado -->
          <div class="form-group m-0">
            <label for="selectFilterEstado" class="form-label text-xs">Filtrar por estado</label>
            <select id="selectFilterEstado" class="form-select text-xs">
              <option value="">Todos los estados</option>
              <option value="PROGRAMADA">Programadas</option>
              <option value="CONFIRMADA">Confirmadas</option>
              <option value="ATENDIDA">Atendidas</option>
              <option value="CANCELADA">Canceladas</option>
              <option value="NO_ASISTIO">No asistió</option>
            </select>
          </div>

          <!-- Indicador del Período Actual -->
          <div class="text-right">
            <span class="text-xs text-muted block uppercase tracking-wider font-bold">Período Seleccionado:</span>
            <strong class="text-sm text-primary block" id="currentPeriodLabel">—</strong>
          </div>
        </div>

        <!-- Contenedor Principal del Calendario -->
        <div id="calendarViewport" class="mt-4" aria-live="polite"></div>
      </div>
    </div>
  `;

  const viewportEl = container.querySelector('#calendarViewport');
  const periodLabelEl = container.querySelector('#currentPeriodLabel');
  const inputJumpDate = container.querySelector('#inputJumpDate');
  const selectFilterEstado = container.querySelector('#selectFilterEstado');

  // Carga y renderizado
  async function loadAndRender() {
    ui.renderLoading(viewportEl, 'Actualizando citas médicas del período...');

    let desde;
    let hasta;

    if (state.viewMode === 'week') {
      const { monday, sunday } = getWeekRange(state.selectedDate);
      desde = monday;
      hasta = sunday;
      periodLabelEl.textContent = `Semana: ${formatDateShortBogota(monday + 'T12:00:00Z')} – ${formatDateShortBogota(sunday + 'T12:00:00Z')}`;
    } else {
      desde = state.selectedDate;
      hasta = state.selectedDate;
      periodLabelEl.textContent = formatDateLongBogota(state.selectedDate + 'T12:00:00Z');
    }

    inputJumpDate.value = state.selectedDate;

    try {
      const params = {
        desde,
        hasta,
        page: 0,
        size: 500
      };
      if (state.filtroEstado) {
        params.estado = state.filtroEstado;
      }

      const res = await api.get('/admin/appointments', params);
      state.citas = res?.content || [];

      if (state.viewMode === 'week') {
        renderWeekView(state.citas, desde, hasta);
      } else {
        renderDayView(state.citas, state.selectedDate);
      }
    } catch (err) {
      ui.renderError(viewportEl, {
        title: 'Error al consultar las citas',
        message: err.message || 'No fue posible obtener el listado de citas médicas.',
        onRetry: loadAndRender
      });
    }
  }

  // Renderizado de Vista Semanal (Grid de 7 columnas)
  function renderWeekView(citas, monday, sunday) {
    const weekDays = getWeekDays(state.selectedDate);

    // Agrupar citas por fecha en Colombia (YYYY-MM-DD)
    const citasPorDia = new Map();
    weekDays.forEach(d => citasPorDia.set(d.dateStr, []));

    citas.forEach(c => {
      if (c.fechaHoraInicio) {
        try {
          const dStr = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date(c.fechaHoraInicio));
          if (citasPorDia.has(dStr)) {
            citasPorDia.get(dStr).push(c);
          }
        } catch {
          // Ignorar si fecha es inválida
        }
      }
    });

    viewportEl.innerHTML = `
      <div class="calendar-week-grid">
        ${weekDays.map(day => {
          const dayCitas = citasPorDia.get(day.dateStr) || [];
          const [y, m, d] = day.dateStr.split('-');
          return `
            <div class="calendar-day-col ${day.isToday ? 'is-today' : ''}" data-day="${day.dateStr}">
              <div class="calendar-day-header">
                <span class="text-xs uppercase font-bold text-muted block">${day.shortName}</span>
                <span class="text-lg font-bold block ${day.isToday ? 'text-primary' : ''}">${d}</span>
                <span class="badge ${dayCitas.length > 0 ? 'badge--scheduled' : 'badge--neutral'} text-xs" style="font-size: 10px;">
                  ${dayCitas.length} cita(s)
                </span>
              </div>

              <div class="flex-1 flex flex-col gap-2 overflow-y-auto" style="max-height: 480px;">
                ${dayCitas.length === 0 ? `
                  <div class="text-center p-3 text-xs text-muted" style="margin: auto 0;">
                    Sin citas
                  </div>
                ` : dayCitas.map(c => `
                  <div class="calendar-cita-item status-${esc(c.estado)}" data-cita-id="${esc(c.citaPublicId)}">
                    <div class="flex items-center justify-between gap-1 mb-1">
                      <strong class="text-xs text-primary">${formatTimeBogota(c.fechaHoraInicio)}</strong>
                      ${citaStatusBadge(c.estado)}
                    </div>
                    <strong class="block text-xs truncate" title="${esc(c.pacienteNombre)}">
                      ${esc(c.pacienteNombre)}
                    </strong>
                    <span class="text-xs text-muted block truncate" title="Dr(a). ${esc(c.profesionalNombre)}">
                      Dr(a). ${esc(c.profesionalNombre)}
                    </span>
                    <span class="text-xs text-muted block truncate" style="font-size: 10px;">
                      ${esc(c.especialidadNombre)}
                    </span>
                  </div>
                `).join('')}
              </div>
            </div>
          `;
        }).join('')}
      </div>
    `;

    // Asignar clics a cada cita para abrir modal de detalle
    attachCitaClickListeners();
  }

  // Renderizado de Vista Diaria (Lista detallada y cronológica)
  function renderDayView(citas, dateStr) {
    if (citas.length === 0) {
      ui.renderEmpty(viewportEl, {
        icon: 'calendar',
        title: 'Sin citas para este día',
        description: `No hay citas médicas registradas para el ${formatDateLongBogota(dateStr + 'T12:00:00Z')}.`
      });
      return;
    }

    viewportEl.innerHTML = `
      <div class="flex items-center justify-between mb-3 pb-2 border-b">
        <span class="text-xs font-bold uppercase tracking-wider text-muted">
          ${citas.length} cita(s) programada(s) para hoy
        </span>
        <span class="text-xs text-muted">Haz clic en cualquier cita para ver la información completa</span>
      </div>

      <div class="flex flex-col gap-3">
        ${citas.map(c => `
          <div class="card p-4 calendar-cita-item status-${esc(c.estado)}" data-cita-id="${esc(c.citaPublicId)}" style="cursor: pointer;">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div class="flex items-start gap-4">
                <div style="min-width: 6.5rem;">
                  <span class="text-lg font-bold block text-primary">${formatTimeBogota(c.fechaHoraInicio)}</span>
                  <span class="text-xs text-muted">${formatTimeBogota(c.fechaHoraFin)}</span>
                  <span class="badge ${c.modalidad === 'TELEMEDICINA' ? 'badge--rescheduled' : 'badge--neutral'} text-xs mt-1">
                    ${c.modalidad === 'TELEMEDICINA' ? 'Telemedicina' : (esc(c.sedeNombre) || 'Presencial')}
                  </span>
                </div>

                <div>
                  <div class="flex items-center gap-2">
                    <strong class="text-base text-text">${esc(c.pacienteNombre)}</strong>
                    <span class="text-xs text-muted font-mono">(${esc(c.pacienteDocumentoTipo)} ${esc(c.pacienteDocumentoNumero)})</span>
                  </div>
                  <div class="flex flex-wrap items-center gap-2 mt-1">
                    ${citaStatusBadge(c.estado)}
                    <span class="badge badge--neutral text-xs">Dr(a). ${esc(c.profesionalNombre)}</span>
                    <span class="badge badge--neutral text-xs">${esc(c.especialidadNombre)}</span>
                    ${c.triajeNivel ? `<span class="badge badge--scheduled text-xs">Triaje: Nivel ${esc(c.triajeNivel)}</span>` : ''}
                  </div>
                  ${c.motivoConsulta ? `
                    <p class="text-xs text-muted m-0 mt-2"><strong>Motivo:</strong> ${esc(c.motivoConsulta)}</p>
                  ` : ''}
                </div>
              </div>

              <div>
                <button type="button" class="btn btn-secondary btn--sm">
                  ${ui.icon('info')}<span>Ver detalle</span>
                </button>
              </div>
            </div>
          </div>
        `).join('')}
      </div>
    `;

    attachCitaClickListeners();
  }

  // Listener para inspección de citas
  function attachCitaClickListeners() {
    viewportEl.querySelectorAll('.calendar-cita-item').forEach(item => {
      item.addEventListener('click', () => {
        const citaId = item.getAttribute('data-cita-id');
        const cita = state.citas.find(c => c.citaPublicId === citaId);
        if (cita) {
          showAppointmentDetailModal(cita);
        }
      });
    });
  }

  // Modal de Detalle Completo de la Cita
  function showAppointmentDetailModal(c) {
    const isTele = c.modalidad === 'TELEMEDICINA';
    const fechaTexto = formatDateLongBogota(c.fechaHoraInicio);
    const horaInicio = formatTimeBogota(c.fechaHoraInicio);
    const horaFin = formatTimeBogota(c.fechaHoraFin);

    const bodyHtml = `
      <div class="p-4" style="max-height: 75vh; overflow-y: auto;">
        <div class="flex items-center justify-between pb-3 border-b mb-4">
          <div>
            <span class="text-xs text-muted font-bold uppercase tracking-wider block">Identificador de la Cita:</span>
            <span class="font-mono font-bold text-sm text-primary">${esc(c.citaPublicId)}</span>
          </div>
          <div>${citaStatusBadge(c.estado)}</div>
        </div>

        <div class="grid grid-cols-1 grid-cols-2-md gap-4 mb-4">
          <!-- Paciente -->
          <div class="p-3 bg-surface-2 rounded-md">
            <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">
              ${ui.icon('user', 'icon icon--xs')} Datos del Paciente
            </span>
            <strong class="block text-sm text-text">${esc(c.pacienteNombre)}</strong>
            <span class="text-xs text-muted block">Doc: ${esc(c.pacienteDocumentoTipo)} ${esc(c.pacienteDocumentoNumero)}</span>
            <span class="text-xs text-muted block">Tel: ${esc(c.pacienteTelefono || 'No registrado')}</span>
            <span class="text-xs text-muted block truncate">Email: ${esc(c.pacienteEmail || '—')}</span>
          </div>

          <!-- Profesional Asignado -->
          <div class="p-3 bg-surface-2 rounded-md">
            <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">
              ${ui.icon('award', 'icon icon--xs')} Médico Asignado
            </span>
            <strong class="block text-sm text-text">Dr(a). ${esc(c.profesionalNombre)}</strong>
            <span class="text-xs text-muted block">Registro: ${esc(c.registroMedico || '—')}</span>
            <span class="badge badge--neutral text-xs mt-1">${esc(c.especialidadNombre)}</span>
          </div>

          <!-- Fecha y Horario -->
          <div class="p-3 bg-surface-2 rounded-md">
            <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">
              ${ui.icon('calendar', 'icon icon--xs')} Fecha y Horario
            </span>
            <strong class="block text-sm capitalize text-text">${fechaTexto}</strong>
            <span class="text-xs font-semibold text-primary block">${horaInicio} – ${horaFin}</span>
            <span class="badge ${isTele ? 'badge--rescheduled' : 'badge--scheduled'} text-xs mt-1">
              ${isTele ? 'Telemedicina Virtual' : 'Presencial en Sede'}
            </span>
          </div>

          <!-- Sede y Lugar -->
          <div class="p-3 bg-surface-2 rounded-md">
            <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">
              ${ui.icon('map-pin', 'icon icon--xs')} Ubicación de Atención
            </span>
            <strong class="block text-sm text-text">${esc(c.sedeNombre || 'Centro Médico MediTriaje')}</strong>
            <span class="text-xs text-muted block">${esc(c.sedeCiudad || 'Colombia')}</span>
          </div>
        </div>

        <!-- Motivo de Consulta y Triaje Clínico -->
        <div class="p-3 bg-surface-2 rounded-md mb-4">
          <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">
            ${ui.icon('activity', 'icon icon--xs')} Motivo de la Consulta y Triaje
          </span>
          <p class="text-sm m-0 mb-2">
            ${c.motivoConsulta ? esc(c.motivoConsulta) : 'Consulta médica solicitada directamente por el paciente.'}
          </p>
          <div class="flex items-center gap-2">
            ${c.triajeNivel ? `
              <span class="badge badge--scheduled text-xs">Clasificación Triaje: Nivel ${esc(c.triajeNivel)}</span>
            ` : '<span class="badge badge--neutral text-xs">Sin triaje preliminar</span>'}
            ${c.triajePublicId ? `
              <span class="text-xs text-muted font-mono">ID Triaje: ${esc(c.triajePublicId.slice(0, 8))}...</span>
            ` : ''}
          </div>
        </div>

        ${c.motivoCancelacion ? `
          <div class="alert alert--danger mb-4">
            ${ui.icon('alert-triangle', 'icon alert-icon text-danger')}
            <div class="alert-content">
              <strong class="block text-xs">Motivo de Cancelación Registrado:</strong>
              <p class="text-xs m-0">${esc(c.motivoCancelacion)}</p>
            </div>
          </div>
        ` : ''}

        <div class="text-xs text-muted text-right">
          Fecha de solicitud: ${c.createdAt ? new Date(c.createdAt).toLocaleString('es-CO') : '—'}
        </div>
      </div>
    `;

    ui.showModal({
      title: 'Detalle de la Cita Médica',
      message: bodyHtml,
      confirmText: 'Cerrar',
      cancelText: '',
      onConfirm: () => {}
    });
  }

  // Modal para Exportar Citas a Excel (.csv con UTF-8 BOM)
  function showExportModal() {
    const today = todayBogota();
    const { monday, sunday } = getWeekRange(state.selectedDate);

    const exportHtml = `
      <div class="p-4">
        <p class="text-sm text-muted mb-4">
          Descarga un reporte consolidado en archivo compatible nativamente con Microsoft Excel con codificación UTF-8 BOM y delimitadores estándar.
        </p>

        <form id="formExportAppointments" class="flex flex-col gap-4">
          <!-- Rango Predefinido -->
          <div class="form-group m-0">
            <label class="form-label text-xs">Seleccionar rango de exportación</label>
            <div class="grid grid-cols-1 grid-cols-3-md gap-2">
              <button type="button" class="btn btn-secondary btn--sm btn-preset" data-desde="${today}" data-hasta="${today}">
                Solo hoy (${today.slice(5)})
              </button>
              <button type="button" class="btn btn-secondary btn--sm btn-preset" data-desde="${monday}" data-hasta="${sunday}">
                Esta semana (${monday.slice(5)} al ${sunday.slice(5)})
              </button>
              <button type="button" class="btn btn-secondary btn--sm btn-preset" data-desde="${addDays(today, -30)}" data-hasta="${today}">
                Últimos 30 días
              </button>
            </div>
          </div>

          <!-- Rango Personalizado -->
          <div class="grid grid-cols-2 gap-3">
            <div class="form-group m-0">
              <label for="exportFechaDesde" class="form-label text-xs">Fecha Desde</label>
              <input type="date" id="exportFechaDesde" class="form-input text-xs" value="${monday}" required>
            </div>
            <div class="form-group m-0">
              <label for="exportFechaHasta" class="form-label text-xs">Fecha Hasta</label>
              <input type="date" id="exportFechaHasta" class="form-input text-xs" value="${sunday}" required>
            </div>
          </div>

          <!-- Filtro de Estado Opcional -->
          <div class="form-group m-0">
            <label for="exportEstado" class="form-label text-xs">Filtrar por Estado (opcional)</label>
            <select id="exportEstado" class="form-select text-xs">
              <option value="">Todos los estados</option>
              <option value="PROGRAMADA">Solo Programadas</option>
              <option value="CONFIRMADA">Solo Confirmadas</option>
              <option value="ATENDIDA">Solo Atendidas</option>
              <option value="CANCELADA">Solo Canceladas</option>
            </select>
          </div>
        </form>
      </div>
    `;

    ui.showModal({
      title: 'Exportar Citas a Plantilla Excel',
      message: exportHtml,
      confirmText: 'Descargar archivo Excel (.csv)',
      cancelText: 'Cancelar',
      onConfirm: async () => {
        const fDesde = document.getElementById('exportFechaDesde')?.value;
        const fHasta = document.getElementById('exportFechaHasta')?.value;
        const est = document.getElementById('exportEstado')?.value;

        if (!fDesde || !fHasta) {
          ui.showToast('Por favor selecciona las fechas inicial y final para la exportación.', 'warning');
          return;
        }

        try {
          ui.showToast('Generando plantilla de exportación...', 'info');
          const queryParams = new URLSearchParams({
            desde: fDesde,
            hasta: fHasta
          });
          if (est) queryParams.set('estado', est);

          const downloadUrl = `/api/v1/admin/appointments/export?${queryParams.toString()}`;
          
          // Descarga directa con credenciales de cookie JWT
          const response = await fetch(downloadUrl, {
            method: 'GET',
            headers: { 'Accept': 'text/csv' },
            credentials: 'include'
          });

          if (!response.ok) {
            throw new Error(`Error en el servidor al exportar citas (${response.status})`);
          }

          const blob = await response.blob();
          const blobUrl = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.style.display = 'none';
          a.href = blobUrl;
          a.download = `citas_meditriaje_${fDesde.replace(/-/g, '')}_al_${fHasta.replace(/-/g, '')}.csv`;
          document.body.appendChild(a);
          a.click();
          a.remove();
          window.URL.revokeObjectURL(blobUrl);

          ui.showToast('Archivo Excel descargado exitosamente.', 'confirmed');
        } catch (err) {
          ui.showToast(err.message || 'No fue posible exportar las citas médicas.', 'danger');
        }
      }
    });

    // Conectar botones predefinidos dentro del modal
    setTimeout(() => {
      document.querySelectorAll('.btn-preset').forEach(btn => {
        btn.addEventListener('click', () => {
          const dDesde = btn.getAttribute('data-desde');
          const dHasta = btn.getAttribute('data-hasta');
          const inputDesde = document.getElementById('exportFechaDesde');
          const inputHasta = document.getElementById('exportFechaHasta');
          if (inputDesde) inputDesde.value = dDesde;
          if (inputHasta) inputHasta.value = dHasta;
        });
      });
    }, 50);
  }

  // Configuración de Eventos de la Vista
  container.querySelector('#btnViewWeek')?.addEventListener('click', () => {
    state.viewMode = 'week';
    container.querySelector('#btnViewWeek').className = 'btn btn--sm btn-primary';
    container.querySelector('#btnViewDay').className = 'btn btn--sm btn-secondary';
    loadAndRender();
  });

  container.querySelector('#btnViewDay')?.addEventListener('click', () => {
    state.viewMode = 'day';
    container.querySelector('#btnViewDay').className = 'btn btn--sm btn-primary';
    container.querySelector('#btnViewWeek').className = 'btn btn--sm btn-secondary';
    loadAndRender();
  });

  container.querySelector('#btnPrevDate')?.addEventListener('click', () => {
    const delta = state.viewMode === 'week' ? -7 : -1;
    state.selectedDate = addDays(state.selectedDate, delta);
    loadAndRender();
  });

  container.querySelector('#btnNextDate')?.addEventListener('click', () => {
    const delta = state.viewMode === 'week' ? 7 : 1;
    state.selectedDate = addDays(state.selectedDate, delta);
    loadAndRender();
  });

  container.querySelector('#btnTodayDate')?.addEventListener('click', () => {
    state.selectedDate = todayBogota();
    loadAndRender();
  });

  inputJumpDate?.addEventListener('change', () => {
    if (inputJumpDate.value) {
      state.selectedDate = inputJumpDate.value;
      loadAndRender();
    }
  });

  selectFilterEstado?.addEventListener('change', () => {
    state.filtroEstado = selectFilterEstado.value;
    loadAndRender();
  });

  container.querySelector('#btnOpenExportModal')?.addEventListener('click', () => {
    showExportModal();
  });

  // Ejecución inicial
  await loadAndRender();
}
