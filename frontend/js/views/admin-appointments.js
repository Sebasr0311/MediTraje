/**
 * MediTriaje 2.0 — Calendario Semanal y Supervisión de Citas Médicas (admin-appointments.js)
 * Motor Time-Grid continuo con resolución temporal real, cálculo de duración proporcional,
 * resolución matemática de citas superpuestas (clustering & lane greedy assignment),
 * indicador dinámico de hora actual (Now Indicator), métricas KPI en tiempo real,
 * filtros combinables (estado, médico, especialidad, paciente) y exportación a Excel (UTF-8 BOM).
 * REGLA ESTRICTA: El rol administrativo no accede a historias clínicas ni datos clínicos privados (ADR-007).
 */

import { api } from '../api.js';
import { ui, esc } from '../ui.js';

/** Altura base en píxeles por cada hora en la cuadrícula */
const PX_PER_HOUR = 70;
const PX_PER_MIN = PX_PER_HOUR / 60;

/** Fecha de hoy en formato YYYY-MM-DD en zona America/Bogota. */
function todayBogota() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date());
}

/** Descompone un instante ISO en fecha y minutos del día en zona America/Bogota. */
function getBogotaDateParts(isoString) {
  if (!isoString) {
    return { dateStr: '', hours: 0, minutes: 0, totalMinutes: 0, timeStr: '—' };
  }
  const d = new Date(isoString);
  const dateStr = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(d);
  const timeStr = new Intl.DateTimeFormat('es-CO', {
    timeZone: 'America/Bogota',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true
  }).format(d);
  const time24Str = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'America/Bogota',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(d);
  const [h, m] = time24Str.split(':').map(Number);
  return {
    dateStr,
    hours: h,
    minutes: m,
    totalMinutes: h * 60 + m,
    timeStr
  };
}

/** Retorna la hora actual en minutos en Bogotá y su representación formateada. */
function getCurrentBogotaTime() {
  const now = new Date();
  const timeStr = new Intl.DateTimeFormat('es-CO', {
    timeZone: 'America/Bogota',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true
  }).format(now);
  const time24Str = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'America/Bogota',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(now);
  const [h, m] = time24Str.split(':').map(Number);
  return {
    hours: h,
    minutes: m,
    totalMinutes: h * 60 + m,
    timeStr
  };
}

/** Formatea hora legible en zona America/Bogota. */
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

/** Formatea fecha corta en zona America/Bogota (ej: 06 oct). */
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

/** Formatea fecha larga en zona America/Bogota (ej: martes, 6 de octubre de 2026). */
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

/** Suma o resta días a una fecha ISO YYYY-MM-DD. */
function addDays(isoDate, days) {
  const [y, m, d] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + days));
  return date.toISOString().slice(0, 10);
}

/** Obtiene el lunes y domingo de la semana a la que pertenece la fecha ISO. */
function getWeekRange(isoDate) {
  const [y, m, d] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  const day = date.getUTCDay(); // 0=Domingo, 1=Lunes, ...
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
  const today = todayBogota();
  for (let i = 0; i < 7; i++) {
    const dStr = addDays(monday, i);
    days.push({
      dateStr: dStr,
      shortName: nombres[i],
      isToday: dStr === today
    });
  }
  return days;
}

/** Metadatos visuales y semánticos por estado de cita (icono + texto + clases). */
function getStatusMeta(estado) {
  const est = (estado || '').toUpperCase();
  switch (est) {
    case 'PROGRAMADA':
      return { label: 'Programada', iconText: '◷', iconName: 'clock', badgeClass: 'badge--scheduled' };
    case 'CONFIRMADA':
      return { label: 'Confirmada', iconText: '✓', iconName: 'check', badgeClass: 'badge--confirmed' };
    case 'ATENDIDA':
      return { label: 'Atendida', iconText: '●', iconName: 'activity', badgeClass: 'badge--attended' };
    case 'CANCELADA':
      return { label: 'Cancelada', iconText: '✕', iconName: 'x', badgeClass: 'badge--cancelled' };
    case 'NO_ASISTIO':
      return { label: 'No asistió', iconText: '!', iconName: 'alert-circle', badgeClass: 'badge--cancelled' };
    case 'REPROGRAMADA':
      return { label: 'Reprogramada', iconText: '⟳', iconName: 'calendar', badgeClass: 'badge--rescheduled' };
    default:
      return { label: estado || 'Estado', iconText: '•', iconName: 'circle', badgeClass: 'badge--neutral' };
  }
}

/** Badge visual para el estado de una cita médica en vistas o modales. */
function citaStatusBadge(estado) {
  const meta = getStatusMeta(estado);
  return `<span class="badge ${meta.badgeClass}">${ui.icon(meta.iconName, 'icon icon--xs')} ${esc(meta.label)}</span>`;
}

/** Calcula el rango de horas operativo del centro (07:00 a 19:00 o dinámico si hay citas más temprano/tarde). */
function computeOperatingHours(citas) {
  let minH = 7;
  let maxH = 19;
  citas.forEach(c => {
    if (c.fechaHoraInicio) {
      const parts = getBogotaDateParts(c.fechaHoraInicio);
      if (parts.hours < minH) minH = Math.max(0, parts.hours);
    }
    if (c.fechaHoraFin) {
      const parts = getBogotaDateParts(c.fechaHoraFin);
      const endH = parts.minutes > 0 ? parts.hours + 1 : parts.hours;
      if (endH > maxH) maxH = Math.min(24, endH);
    }
  });
  return { startHour: minH, endHour: maxH };
}

/**
 * Algoritmo de resolución matemática de superposición de citas (Time-Grid Interval Packing).
 * Agrupa citas colisionantes en clusters continuos y asigna carriles horizontales proporcionales (lanes).
 */
function layoutDayEvents(citasDelDia, startHour) {
  if (!citasDelDia || citasDelDia.length === 0) return [];

  const dayStartMin = startHour * 60;

  // 1. Calcular minutos de inicio, fin y duración real
  const items = citasDelDia.map(c => {
    const startParts = getBogotaDateParts(c.fechaHoraInicio);
    const startMin = startParts.totalMinutes;
    let endMin;
    if (c.fechaHoraFin) {
      const endParts = getBogotaDateParts(c.fechaHoraFin);
      endMin = endParts.totalMinutes;
      if (endMin <= startMin) endMin = startMin + 30;
    } else {
      endMin = startMin + 30; // 30 min por defecto
    }
    const durationMin = endMin - startMin;
    const topPx = (startMin - dayStartMin) * PX_PER_MIN;
    const heightPx = Math.max(32, durationMin * PX_PER_MIN);

    return {
      cita: c,
      startMin,
      endMin,
      durationMin,
      topPx,
      heightPx
    };
  });

  // 2. Ordenar por hora de inicio ascendente, y luego por mayor duración
  items.sort((a, b) => a.startMin - b.startMin || b.durationMin - a.durationMin);

  // 3. Crear clusters conexos de citas que se superponen
  const clusters = [];
  let currentCluster = [];
  let clusterMaxEnd = -1;

  for (const item of items) {
    if (currentCluster.length === 0) {
      currentCluster.push(item);
      clusterMaxEnd = item.endMin;
    } else if (item.startMin < clusterMaxEnd) {
      currentCluster.push(item);
      if (item.endMin > clusterMaxEnd) {
        clusterMaxEnd = item.endMin;
      }
    } else {
      clusters.push(currentCluster);
      currentCluster = [item];
      clusterMaxEnd = item.endMin;
    }
  }
  if (currentCluster.length > 0) {
    clusters.push(currentCluster);
  }

  // 4. Asignar carriles (lanes) dentro de cada cluster usando greedy column packing
  const result = [];
  for (const cluster of clusters) {
    const laneEndTimes = [];
    for (const item of cluster) {
      let placedLane = -1;
      for (let l = 0; l < laneEndTimes.length; l++) {
        if (laneEndTimes[l] <= item.startMin) {
          placedLane = l;
          laneEndTimes[l] = item.endMin;
          break;
        }
      }
      if (placedLane === -1) {
        placedLane = laneEndTimes.length;
        laneEndTimes.push(item.endMin);
      }
      item.lane = placedLane;
    }

    const totalLanes = laneEndTimes.length;
    for (const item of cluster) {
      const widthPct = 100 / totalLanes;
      const leftPct = item.lane * widthPct;
      item.leftStyle = `${leftPct}%`;
      item.widthStyle = totalLanes > 1 ? `calc(${widthPct}% - 3px)` : 'calc(100% - 3px)';
      result.push(item);
    }
  }

  return result;
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
    filtroProfesional: '',
    busquedaPaciente: '',
    citas: [],
    specialties: [],
    professionals: [],
    activeKpiFilter: '',
    timerNowId: null
  };

  // Cargar catálogos iniciales para los filtros (especialidades y profesionales)
  try {
    const [resSpec, resProf] = await Promise.all([
      api.get('/admin/specialties', { size: 100, estado: 'ACTIVO' }).catch(() => ({ content: [] })),
      api.get('/admin/professionals', { size: 100, estado: 'ACTIVO' }).catch(() => ({ content: [] }))
    ]);
    state.specialties = resSpec?.content || [];
    state.professionals = resProf?.content || [];
  } catch {
    // Si falla catálogo, los filtros permanecen básicos
  }

  container.innerHTML = `
    <div class="card mb-6">
      <div class="card-body">
        <!-- Barra Superior de Control: Título, Vistas y Exportar -->
        <div class="flex flex-wrap items-center justify-between gap-4 mb-4 pb-4 border-b">
          <div>
            <h2 class="text-xl font-bold m-0 flex items-center gap-2">
              ${ui.icon('calendar', 'icon icon--md text-primary')}
              <span>Calendario de Citas y Supervisión</span>
            </h2>
            <p class="text-xs text-muted m-0">
              Supervisión de agenda en cuadrícula horaria en tiempo real, control de turnos y reporte analítico
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

        <!-- Barra de Métricas Rápidas (KPIs) de la Semana / Día -->
        <div class="calendar-kpi-bar" id="calendarKpiBar" aria-label="Métricas del período"></div>

        <!-- Barra de Navegación Temporal y Filtros Combinables -->
        <div class="calendar-filters-row">
          <!-- Navegación Temporal -->
          <div class="flex items-center gap-1.5 flex-wrap">
            <button type="button" class="btn btn-secondary btn--sm btn--icon-only" id="btnPrevDate" title="Período anterior" aria-label="Período anterior">
              ${ui.icon('chevron-left')}
            </button>
            <button type="button" class="btn btn-secondary btn--sm" id="btnTodayDate">
              <span>Hoy</span>
            </button>
            <button type="button" class="btn btn-secondary btn--sm btn--icon-only" id="btnNextDate" title="Período siguiente" aria-label="Período siguiente">
              ${ui.icon('chevron-right')}
            </button>
            <input type="date" id="inputJumpDate" class="form-input text-xs" style="max-width: 140px; padding: 4px 8px;" value="${state.selectedDate}">
          </div>

          <!-- Indicador del Período Actual -->
          <div class="flex items-center gap-2">
            <span class="badge badge--scheduled text-xs font-bold" id="currentPeriodLabel" style="font-size: 11px; padding: 4px 10px;">—</span>
          </div>

          <!-- Filtros Combinables: Estado, Profesional, Especialidad y Búsqueda -->
          <div class="flex items-center gap-2 flex-wrap flex-1 justify-end">
            <!-- Filtro Estado -->
            <select id="selectFilterEstado" class="form-select text-xs" style="max-width: 150px;">
              <option value="">Todos los estados</option>
              <option value="PROGRAMADA">Programadas</option>
              <option value="CONFIRMADA">Confirmadas</option>
              <option value="ATENDIDA">Atendidas</option>
              <option value="CANCELADA">Canceladas</option>
              <option value="NO_ASISTIO">No asistió</option>
              <option value="REPROGRAMADA">Reprogramadas</option>
            </select>

            <!-- Filtro Especialidad -->
            <select id="selectFilterEspecialidad" class="form-select text-xs" style="max-width: 160px;">
              <option value="">Todas las especialidades</option>
              ${state.specialties.map(s => `
                <option value="${esc(s.publicId)}">${esc(s.nombre)}</option>
              `).join('')}
            </select>

            <!-- Filtro Profesional -->
            <select id="selectFilterProfesional" class="form-select text-xs" style="max-width: 170px;">
              <option value="">Todos los médicos</option>
              ${state.professionals.map(p => `
                <option value="${esc(p.publicId)}">Dr(a). ${esc(p.nombres)} ${esc(p.apellidos)}</option>
              `).join('')}
            </select>

            <!-- Búsqueda reactiva de paciente -->
            <div class="relative" style="min-width: 170px; max-width: 220px;">
              <input type="search" id="inputSearchPaciente" class="form-input text-xs w-full" placeholder="Buscar paciente / doc...">
            </div>
          </div>
        </div>

        <!-- Contenedor Principal del Calendario (Viewport Time-Grid) -->
        <div id="calendarViewport" class="mt-2" aria-live="polite"></div>
      </div>
    </div>
  `;

  const viewportEl = container.querySelector('#calendarViewport');
  const periodLabelEl = container.querySelector('#currentPeriodLabel');
  const inputJumpDate = container.querySelector('#inputJumpDate');
  const selectFilterEstado = container.querySelector('#selectFilterEstado');
  const selectFilterEspecialidad = container.querySelector('#selectFilterEspecialidad');
  const selectFilterProfesional = container.querySelector('#selectFilterProfesional');
  const inputSearchPaciente = container.querySelector('#inputSearchPaciente');
  const kpiBarEl = container.querySelector('#calendarKpiBar');

  // Actualización de métricas en la barra KPI
  function updateKpiBar(citas) {
    const total = citas.length;
    const programadas = citas.filter(c => c.estado === 'PROGRAMADA').length;
    const confirmadas = citas.filter(c => c.estado === 'CONFIRMADA').length;
    const atendidas = citas.filter(c => c.estado === 'ATENDIDA').length;
    const canceladas = citas.filter(c => c.estado === 'CANCELADA').length;
    const noAsistio = citas.filter(c => c.estado === 'NO_ASISTIO').length;

    kpiBarEl.innerHTML = `
      <div class="calendar-kpi-card ${state.filtroEstado === '' ? 'is-active' : ''}" data-status="">
        <span class="calendar-kpi-label">${ui.icon('calendar', 'icon icon--xs')} Total Citas</span>
        <span class="calendar-kpi-value text-primary">${total}</span>
      </div>
      <div class="calendar-kpi-card ${state.filtroEstado === 'PROGRAMADA' ? 'is-active' : ''}" data-status="PROGRAMADA">
        <span class="calendar-kpi-label">${ui.icon('clock', 'icon icon--xs')} Programadas</span>
        <span class="calendar-kpi-value">${programadas}</span>
      </div>
      <div class="calendar-kpi-card ${state.filtroEstado === 'CONFIRMADA' ? 'is-active' : ''}" data-status="CONFIRMADA">
        <span class="calendar-kpi-label">${ui.icon('check', 'icon icon--xs text-success')} Confirmadas</span>
        <span class="calendar-kpi-value text-success">${confirmadas}</span>
      </div>
      <div class="calendar-kpi-card ${state.filtroEstado === 'ATENDIDA' ? 'is-active' : ''}" data-status="ATENDIDA">
        <span class="calendar-kpi-label">${ui.icon('activity', 'icon icon--xs text-info')} Atendidas</span>
        <span class="calendar-kpi-value text-info">${atendidas}</span>
      </div>
      <div class="calendar-kpi-card ${state.filtroEstado === 'CANCELADA' ? 'is-active' : ''}" data-status="CANCELADA">
        <span class="calendar-kpi-label">${ui.icon('x', 'icon icon--xs text-danger')} Canceladas</span>
        <span class="calendar-kpi-value text-danger">${canceladas}</span>
      </div>
      <div class="calendar-kpi-card ${state.filtroEstado === 'NO_ASISTIO' ? 'is-active' : ''}" data-status="NO_ASISTIO">
        <span class="calendar-kpi-label">${ui.icon('alert-circle', 'icon icon--xs text-warning')} No asistió</span>
        <span class="calendar-kpi-value text-warning">${noAsistio}</span>
      </div>
    `;

    // Conectar clic en KPI para filtrar rápidamente
    kpiBarEl.querySelectorAll('.calendar-kpi-card').forEach(card => {
      card.addEventListener('click', () => {
        const targetStatus = card.getAttribute('data-status');
        selectFilterEstado.value = targetStatus;
        state.filtroEstado = targetStatus;
        loadAndRender();
      });
    });
  }

  // Filtrado de citas en memoria por búsqueda de paciente
  function getFilteredCitas() {
    let list = state.citas;
    if (state.busquedaPaciente.trim()) {
      const q = state.busquedaPaciente.trim().toLowerCase();
      list = list.filter(c =>
        (c.pacienteNombre && c.pacienteNombre.toLowerCase().includes(q)) ||
        (c.pacienteDocumentoNumero && c.pacienteDocumentoNumero.includes(q))
      );
    }
    return list;
  }

  // Carga y renderizado
  async function loadAndRender() {
    ui.renderLoading(viewportEl, 'Actualizando cuadrícula de citas médicas...');

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
      if (state.filtroEstado) params.estado = state.filtroEstado;
      if (state.filtroEspecialidad) params.especialidadPublicId = state.filtroEspecialidad;
      if (state.filtroProfesional) params.profesionalPublicId = state.filtroProfesional;

      const res = await api.get('/admin/appointments', params);
      state.citas = res?.content || [];

      updateKpiBar(state.citas);
      renderCurrentView();
    } catch (err) {
      ui.renderError(viewportEl, {
        title: 'Error al consultar las citas',
        message: err.message || 'No fue posible obtener el listado de citas médicas.',
        onRetry: loadAndRender
      });
    }
  }

  // Despacho de vista actual (Semana o Día)
  function renderCurrentView() {
    const filteredCitas = getFilteredCitas();

    if (state.viewMode === 'week') {
      const { monday, sunday } = getWeekRange(state.selectedDate);
      renderWeekTimeGrid(filteredCitas, monday, sunday);
    } else {
      renderDayTimeGrid(filteredCitas, state.selectedDate);
    }
  }

  // Renderizado de la Vista Semanal con Cuadrícula Horaria (Time-Grid)
  function renderWeekTimeGrid(citas, monday, sunday) {
    const weekDays = getWeekDays(state.selectedDate);
    const { startHour, endHour } = computeOperatingHours(citas);
    const totalHours = endHour - startHour;
    const totalGridHeight = totalHours * PX_PER_HOUR;

    // Agrupar citas por día en Colombia
    const citasPorDia = new Map();
    weekDays.forEach(d => citasPorDia.set(d.dateStr, []));

    citas.forEach(c => {
      if (c.fechaHoraInicio) {
        const parts = getBogotaDateParts(c.fechaHoraInicio);
        if (citasPorDia.has(parts.dateStr)) {
          citasPorDia.get(parts.dateStr).push(c);
        }
      }
    });

    // Generar horas para el eje lateral (Gutter)
    const hoursList = [];
    for (let h = startHour; h <= endHour; h++) {
      const formattedHour = `${String(h).padStart(2, '0')}:00`;
      const topPx = (h - startHour) * PX_PER_HOUR;
      hoursList.push({ hour: h, label: formattedHour, topPx });
    }

    const todayStr = todayBogota();

    viewportEl.innerHTML = `
      <!-- Selector rápido de días para móviles y tablets -->
      <div class="calendar-day-tabs-mobile" role="tablist" aria-label="Días de la semana">
        ${weekDays.map(day => {
          const dayCitas = citasPorDia.get(day.dateStr) || [];
          const [, , d] = day.dateStr.split('-');
          return `
            <button 
              type="button" 
              class="calendar-day-tab-btn ${day.isToday ? 'is-today' : ''} ${day.dateStr === state.selectedDate ? 'is-active' : ''}" 
              data-target-day="${day.dateStr}"
              role="tab"
              aria-selected="${day.dateStr === state.selectedDate}"
            >
              <span class="uppercase font-bold" style="font-size: 10px;">${day.shortName}</span>
              <span class="text-sm font-bold">${d}</span>
              <span class="badge ${dayCitas.length > 0 ? 'badge--scheduled' : 'badge--neutral'}" style="font-size: 9px; padding: 0 4px; margin-top: 2px;">
                ${dayCitas.length}
              </span>
            </button>
          `;
        }).join('')}
      </div>

      <!-- Cuadrícula Horaria Semanal Continua (Time-Grid) -->
      <div class="time-grid-wrapper" id="timeGridWrapper">
        <!-- Encabezado de Columnas (Sticky Header) -->
        <div class="time-grid-header" id="timeGridHeader">
          <div class="time-grid-header-corner" title="Eje Horario">
            ${ui.icon('clock', 'icon icon--xs')}
          </div>
          ${weekDays.map(day => {
            const dayCitas = citasPorDia.get(day.dateStr) || [];
            const [, , d] = day.dateStr.split('-');
            return `
              <div class="time-grid-header-day ${day.isToday ? 'is-today' : ''}" data-day="${day.dateStr}">
                <span class="day-name block">${day.shortName}</span>
                <span class="day-num block">${d}</span>
                <span class="badge day-badge ${dayCitas.length > 0 ? (day.isToday ? 'badge--confirmed' : 'badge--scheduled') : 'badge--neutral'}">
                  ${dayCitas.length} ${dayCitas.length === 1 ? 'cita' : 'citas'}
                </span>
              </div>
            `;
          }).join('')}
        </div>

        <!-- Área Scrollable con Cuadrícula y Citas -->
        <div class="time-grid-scroll-area" id="timeGridScrollArea">
          <div class="time-grid-body" style="height: ${totalGridHeight}px;">
            <!-- Eje lateral de horas (Gutter) -->
            <div class="time-grid-gutter" style="height: ${totalGridHeight}px;">
              ${hoursList.map(item => `
                <span class="time-grid-gutter-hour" style="top: ${item.topPx}px;">${item.label}</span>
              `).join('')}
            </div>

            <!-- 7 Columnas de Días -->
            ${weekDays.map(day => {
              const dayCitas = citasPorDia.get(day.dateStr) || [];
              const layoutEvents = layoutDayEvents(dayCitas, startHour);

              return `
                <div class="time-grid-day-column ${day.isToday ? 'is-today' : ''}" data-day="${day.dateStr}" style="height: ${totalGridHeight}px;">
                  <!-- Líneas horizontales de guía horaria -->
                  ${hoursList.map(item => `
                    <div class="time-grid-line-hour" style="top: ${item.topPx}px;"></div>
                    ${item.hour < endHour ? `
                      <div class="time-grid-line-half" style="top: ${item.topPx + (PX_PER_HOUR / 2)}px;"></div>
                    ` : ''}
                  `).join('')}

                  <!-- Indicador dinámico de hora actual ("Ahora") si corresponde al día de hoy -->
                  <div class="now-indicator-slot" data-day="${day.dateStr}"></div>

                  <!-- Bloques de Citas Médicas con Posición y Altura Proporcional -->
                  ${layoutEvents.map(item => {
                    const c = item.cita;
                    const meta = getStatusMeta(c.estado);
                    const horaInicio = formatTimeBogota(c.fechaHoraInicio);
                    const horaFin = formatTimeBogota(c.fechaHoraFin);

                    return `
                      <div 
                        class="calendar-event-block status-${esc(c.estado)}" 
                        data-cita-id="${esc(c.citaPublicId || c.publicId)}"
                        style="top: ${item.topPx}px; height: ${item.heightPx}px; left: ${item.leftStyle}; width: ${item.widthStyle};"
                        tabindex="0"
                        role="button"
                        aria-label="Cita de ${esc(c.pacienteNombre)} de ${horaInicio} a ${horaFin}, estado ${meta.label}"
                      >
                        <div class="event-header">
                          <span class="event-time">${horaInicio} - ${horaFin}</span>
                          <span class="event-badge">${meta.iconText} ${meta.label}</span>
                        </div>
                        <span class="event-patient" title="${esc(c.pacienteNombre)}">
                          ${esc(c.pacienteNombre)}
                        </span>
                        ${item.heightPx >= 48 ? `
                          <span class="event-doctor" title="Dr(a). ${esc(c.profesionalNombre)}">
                            Dr(a). ${esc(c.profesionalNombre)}
                          </span>
                        ` : ''}
                        ${item.heightPx >= 66 ? `
                          <span class="event-service">
                            ${esc(c.especialidadNombre)}
                          </span>
                        ` : ''}
                      </div>
                    `;
                  }).join('')}
                </div>
              `;
            }).join('')}
          </div>
        </div>
      </div>
    `;

    // Conectar indicador de "Ahora"
    attachNowIndicator(startHour, endHour);

    // Conectar clics a citas para abrir modal de detalle
    attachCitaClickListeners();

    // Conectar tabs de días en móvil para scroll horizontal suave
    viewportEl.querySelectorAll('.calendar-day-tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const targetDay = btn.getAttribute('data-target-day');
        viewportEl.querySelectorAll('.calendar-day-tab-btn').forEach(b => {
          b.classList.remove('is-active');
          b.setAttribute('aria-selected', 'false');
        });
        btn.classList.add('is-active');
        btn.setAttribute('aria-selected', 'true');
        const targetCol = viewportEl.querySelector(`.time-grid-day-column[data-day="${targetDay}"]`);
        if (targetCol) {
          targetCol.scrollIntoView({ behavior: 'smooth', inline: 'start', block: 'nearest' });
        }
      });
    });

    // Auto-scroll inicial a la hora de las primeras citas o a la hora actual
    setTimeout(() => {
      const scrollArea = viewportEl.querySelector('#timeGridScrollArea');
      if (scrollArea) {
        const currentBogota = getCurrentBogotaTime();
        let targetHour = currentBogota.hours;
        if (targetHour < startHour || targetHour > endHour) {
          targetHour = 8; // default 08:00 AM
        }
        const scrollTarget = Math.max(0, (targetHour - startHour - 1) * PX_PER_HOUR);
        scrollArea.scrollTop = scrollTarget;
      }

      // En dispositivos móviles, auto-scroll horizontal a la columna del día actual
      const todayCol = viewportEl.querySelector('.time-grid-day-column.is-today');
      if (todayCol && window.innerWidth <= 768) {
        todayCol.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
      }
    }, 60);
  }

  // Renderizado de la Vista Diaria con Cuadrícula Ampliada (Time-Grid Día)
  function renderDayTimeGrid(citas, dateStr) {
    const { startHour, endHour } = computeOperatingHours(citas);
    const totalHours = endHour - startHour;
    const totalGridHeight = totalHours * PX_PER_HOUR;
    const isToday = dateStr === todayBogota();

    // Filtrar citas del día seleccionado
    const dayCitas = citas.filter(c => {
      if (!c.fechaHoraInicio) return false;
      const parts = getBogotaDateParts(c.fechaHoraInicio);
      return parts.dateStr === dateStr;
    });

    const layoutEvents = layoutDayEvents(dayCitas, startHour);

    const hoursList = [];
    for (let h = startHour; h <= endHour; h++) {
      const formattedHour = `${String(h).padStart(2, '0')}:00`;
      const topPx = (h - startHour) * PX_PER_HOUR;
      hoursList.push({ hour: h, label: formattedHour, topPx });
    }

    viewportEl.innerHTML = `
      <div class="time-grid-wrapper" id="timeGridWrapper">
        <!-- Encabezado Diario -->
        <div class="time-grid-header is-day-view">
          <div class="time-grid-header-corner" title="Eje Horario">
            ${ui.icon('clock', 'icon icon--xs')}
          </div>
          <div class="time-grid-header-day ${isToday ? 'is-today' : ''}" data-day="${dateStr}">
            <span class="day-name block">${formatDateLongBogota(dateStr + 'T12:00:00Z')}</span>
            <span class="badge day-badge ${dayCitas.length > 0 ? 'badge--confirmed' : 'badge--neutral'} mt-1">
              ${dayCitas.length} ${dayCitas.length === 1 ? 'cita programada' : 'citas programadas'}
            </span>
          </div>
        </div>

        <!-- Área con Scroll Vertical -->
        <div class="time-grid-scroll-area" id="timeGridScrollArea">
          <div class="time-grid-body is-day-view" style="height: ${totalGridHeight}px;">
            <!-- Eje lateral de horas (Gutter) -->
            <div class="time-grid-gutter" style="height: ${totalGridHeight}px;">
              ${hoursList.map(item => `
                <span class="time-grid-gutter-hour" style="top: ${item.topPx}px;">${item.label}</span>
              `).join('')}
            </div>

            <!-- Columna única del Día con mayor espacio -->
            <div class="time-grid-day-column ${isToday ? 'is-today' : ''}" data-day="${dateStr}" style="height: ${totalGridHeight}px;">
              <!-- Líneas guía horarias -->
              ${hoursList.map(item => `
                <div class="time-grid-line-hour" style="top: ${item.topPx}px;"></div>
                ${item.hour < endHour ? `
                  <div class="time-grid-line-half" style="top: ${item.topPx + (PX_PER_HOUR / 2)}px;"></div>
                ` : ''}
              `).join('')}

              <!-- Indicador "Ahora" en vista día -->
              <div class="now-indicator-slot" data-day="${dateStr}"></div>

              ${dayCitas.length === 0 ? `
                <div class="flex items-center justify-center h-full p-6 text-center text-muted text-sm">
                  <div>
                    ${ui.icon('calendar', 'icon icon--lg text-muted mb-2')}
                    <p class="m-0">No hay citas médicas registradas para este día.</p>
                  </div>
                </div>
              ` : layoutEvents.map(item => {
                const c = item.cita;
                const meta = getStatusMeta(c.estado);
                const horaInicio = formatTimeBogota(c.fechaHoraInicio);
                const horaFin = formatTimeBogota(c.fechaHoraFin);

                return `
                  <div 
                    class="calendar-event-block status-${esc(c.estado)}" 
                    data-cita-id="${esc(c.citaPublicId || c.publicId)}"
                    style="top: ${item.topPx}px; height: ${item.heightPx}px; left: ${item.leftStyle}; width: ${item.widthStyle};"
                    tabindex="0"
                    role="button"
                    aria-label="Cita de ${esc(c.pacienteNombre)} de ${horaInicio} a ${horaFin}, estado ${meta.label}"
                  >
                    <div class="event-header">
                      <div class="flex items-center gap-2">
                        <span class="event-time">${horaInicio} - ${horaFin}</span>
                        <span class="badge ${c.modalidad === 'TELEMEDICINA' ? 'badge--rescheduled' : 'badge--neutral'}" style="font-size: 9px; padding: 0 4px;">
                          ${c.modalidad === 'TELEMEDICINA' ? 'Telemedicina' : (esc(c.sedeNombre) || 'Presencial')}
                        </span>
                      </div>
                      <span class="event-badge">${meta.iconText} ${meta.label}</span>
                    </div>

                    <div class="flex items-center justify-between gap-2 mt-1">
                      <strong class="event-patient text-sm">
                        ${esc(c.pacienteNombre)}
                      </strong>
                      <span class="text-xs text-muted font-mono">
                        ${esc(c.pacienteDocumentoTipo)} ${esc(c.pacienteDocumentoNumero)}
                      </span>
                    </div>

                    <div class="flex items-center gap-2 text-xs text-muted mt-0.5">
                      <span>Dr(a). ${esc(c.profesionalNombre)}</span>
                      <span>•</span>
                      <span>${esc(c.especialidadNombre)}</span>
                      ${c.triajeNivel ? `
                        <span class="badge badge--scheduled" style="font-size: 9px; padding: 0 4px;">Triaje Nivel ${esc(c.triajeNivel)}</span>
                      ` : ''}
                    </div>

                    ${c.motivoConsulta && item.heightPx >= 75 ? `
                      <p class="text-xs text-muted truncate m-0 mt-1" style="font-size: 11px;">
                        <strong>Motivo:</strong> ${esc(c.motivoConsulta)}
                      </p>
                    ` : ''}
                  </div>
                `;
              }).join('')}
            </div>
          </div>
        </div>
      </div>
    `;

    attachNowIndicator(startHour, endHour);
    attachCitaClickListeners();
  }

  // Indicador horizontal dinámico de hora actual ("Ahora")
  function attachNowIndicator(startHour, endHour) {
    if (state.timerNowId) {
      clearInterval(state.timerNowId);
      state.timerNowId = null;
    }

    const todayStr = todayBogota();

    function updateIndicator() {
      const nowInfo = getCurrentBogotaTime();
      const slot = viewportEl.querySelector(`.now-indicator-slot[data-day="${todayStr}"]`);
      if (!slot) return;

      if (nowInfo.hours >= startHour && nowInfo.hours <= endHour) {
        const topPx = (nowInfo.totalMinutes - startHour * 60) * PX_PER_MIN;
        slot.innerHTML = `
          <div class="calendar-now-indicator" style="top: ${topPx}px;" title="Hora actual en Bogotá: ${nowInfo.timeStr}">
            <div class="calendar-now-badge">● AHORA ${nowInfo.timeStr}</div>
            <div class="calendar-now-line"></div>
          </div>
        `;
      } else {
        slot.innerHTML = '';
      }
    }

    updateIndicator();
    state.timerNowId = setInterval(updateIndicator, 60000);
  }

  // Listener para abrir el modal de detalle completo
  function attachCitaClickListeners() {
    viewportEl.querySelectorAll('.calendar-event-block').forEach(item => {
      item.addEventListener('click', () => {
        const citaId = item.getAttribute('data-cita-id');
        const cita = state.citas.find(c => (c.citaPublicId || c.publicId) === citaId);
        if (cita) {
          showAppointmentDetailModal(cita);
        }
      });
      item.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          const citaId = item.getAttribute('data-cita-id');
          const cita = state.citas.find(c => (c.citaPublicId || c.publicId) === citaId);
          if (cita) {
            showAppointmentDetailModal(cita);
          }
        }
      });
    });
  }

  // Modal de Detalle Completo de la Cita Médica
  function showAppointmentDetailModal(c) {
    const isTele = c.modalidad === 'TELEMEDICINA';
    const fechaTexto = formatDateLongBogota(c.fechaHoraInicio);
    const horaInicio = formatTimeBogota(c.fechaHoraInicio);
    const horaFin = formatTimeBogota(c.fechaHoraFin);
    let duracionMin = 30;
    if (c.fechaHoraInicio && c.fechaHoraFin) {
      duracionMin = Math.max(15, Math.round((new Date(c.fechaHoraFin) - new Date(c.fechaHoraInicio)) / 60000));
    }

    const bodyHtml = `
      <div class="p-4" style="max-height: 78vh; overflow-y: auto;">
        <!-- Cabecera de Identificación y Estado -->
        <div class="flex items-center justify-between pb-3 border-b mb-4">
          <div>
            <span class="text-xs text-muted font-bold uppercase tracking-wider block">Identificador de la Cita</span>
            <span class="font-mono font-bold text-sm text-primary">${esc(c.citaPublicId || c.publicId)}</span>
          </div>
          <div>${citaStatusBadge(c.estado)}</div>
        </div>

        <!-- Tarjetas de Información del Paciente y Médico -->
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
              ${ui.icon('award', 'icon icon--xs')} Profesional Asignado
            </span>
            <strong class="block text-sm text-text">Dr(a). ${esc(c.profesionalNombre)}</strong>
            <span class="text-xs text-muted block">Registro Médico: ${esc(c.registroMedico || '—')}</span>
            <span class="badge badge--neutral text-xs mt-1">${esc(c.especialidadNombre)}</span>
          </div>

          <!-- Fecha, Horario y Duración -->
          <div class="p-3 bg-surface-2 rounded-md">
            <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">
              ${ui.icon('calendar', 'icon icon--xs')} Horario y Duración
            </span>
            <strong class="block text-sm capitalize text-text">${fechaTexto}</strong>
            <span class="text-xs font-semibold text-primary block mt-0.5">
              ${horaInicio} – ${horaFin} (${duracionMin} minutos)
            </span>
            <span class="badge ${isTele ? 'badge--rescheduled' : 'badge--scheduled'} text-xs mt-1">
              ${isTele ? 'Telemedicina Virtual' : 'Presencial en Sede'}
            </span>
          </div>

          <!-- Ubicación de Atención -->
          <div class="p-3 bg-surface-2 rounded-md">
            <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">
              ${ui.icon('map-pin', 'icon icon--xs')} Sede y Consultorio
            </span>
            <strong class="block text-sm text-text">${esc(c.sedeNombre || 'Centro Médico MediTriaje')}</strong>
            <span class="text-xs text-muted block">${esc(c.sedeCiudad || 'Colombia')}</span>
          </div>
        </div>

        <!-- Motivo de Consulta y Clasificación de Triaje -->
        <div class="p-3 bg-surface-2 rounded-md mb-4">
          <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">
            ${ui.icon('activity', 'icon icon--xs')} Motivo de Consulta y Triaje Clínico
          </span>
          <p class="text-sm m-0 mb-2">
            ${c.motivoConsulta ? esc(c.motivoConsulta) : 'Consulta médica programada por el paciente.'}
          </p>
          <div class="flex items-center gap-2">
            ${c.triajeNivel ? `
              <span class="badge badge--scheduled text-xs">Clasificación Triaje: Nivel ${esc(c.triajeNivel)}</span>
            ` : '<span class="badge badge--neutral text-xs">Sin triaje preliminar</span>'}
            ${c.triajePublicId ? `
              <span class="text-xs text-muted font-mono">Ref: ${esc(c.triajePublicId.slice(0, 8))}...</span>
            ` : ''}
          </div>
        </div>

        <!-- Alerta de Cancelación si aplica -->
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
          Fecha de registro: ${c.createdAt ? new Date(c.createdAt).toLocaleString('es-CO') : '—'}
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

  // Modal para Exportar Citas a Plantilla Excel (.csv con UTF-8 BOM)
  function showExportModal() {
    const today = todayBogota();
    const { monday, sunday } = getWeekRange(state.selectedDate);

    const exportHtml = `
      <div class="p-4">
        <p class="text-sm text-muted mb-4">
          Descarga un reporte consolidado en archivo compatible nativamente con Microsoft Excel con codificación UTF-8 BOM y delimitadores estándar.
        </p>

        <form id="formExportAppointments" class="flex flex-col gap-4">
          <!-- Botones de Rango Rápido -->
          <div class="form-group m-0">
            <label class="form-label text-xs mb-1">Rangos Rápidos</label>
            <div class="flex flex-wrap gap-2">
              <button type="button" class="btn btn-secondary btn--sm btn-preset flex-1" style="min-width: 100px;" data-desde="${today}" data-hasta="${today}">
                Solo hoy
              </button>
              <button type="button" class="btn btn-secondary btn--sm btn-preset flex-1" style="min-width: 110px;" data-desde="${monday}" data-hasta="${sunday}">
                Esta semana
              </button>
              <button type="button" class="btn btn-secondary btn--sm btn-preset flex-1" style="min-width: 120px;" data-desde="${addDays(today, -30)}" data-hasta="${today}">
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
              <option value="NO_ASISTIO">Solo No Asistió</option>
            </select>
          </div>

          <!-- Filtro de Especialidad Opcional -->
          <div class="form-group m-0">
            <label for="exportEspecialidad" class="form-label text-xs">Especialidad (opcional)</label>
            <select id="exportEspecialidad" class="form-select text-xs">
              <option value="">Todas las especialidades</option>
              ${state.specialties.map(s => `
                <option value="${esc(s.publicId)}">${esc(s.nombre)}</option>
              `).join('')}
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
        const esp = document.getElementById('exportEspecialidad')?.value;

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
          if (esp) queryParams.set('especialidadPublicId', esp);

          const downloadUrl = `/api/v1/admin/appointments/export?${queryParams.toString()}`;
          
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

  selectFilterEspecialidad?.addEventListener('change', () => {
    state.filtroEspecialidad = selectFilterEspecialidad.value;
    loadAndRender();
  });

  selectFilterProfesional?.addEventListener('change', () => {
    state.filtroProfesional = selectFilterProfesional.value;
    loadAndRender();
  });

  inputSearchPaciente?.addEventListener('input', () => {
    state.busquedaPaciente = inputSearchPaciente.value;
    renderCurrentView();
  });

  container.querySelector('#btnOpenExportModal')?.addEventListener('click', () => {
    showExportModal();
  });

  // Ejecución inicial
  await loadAndRender();
}
