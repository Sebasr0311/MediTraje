/**
 * MediTriaje 2.0 — Vista de Mis Citas del Paciente (patient-appointments.js)
 * Listado reactivo de citas agendadas, filtros por estado, consulta de detalles
 * y cancelación anticipada con regla de las 2 horas (M8.2c, HU-04, HU-05, ADR-006).
 */

import { api } from '../api.js';
import { auth } from '../auth.js';
import { router } from '../router.js';
import { ui } from '../ui.js';

let appointmentsState = {
  citas: [],
  page: 0,
  size: 20,
  totalPages: 1,
  totalElements: 0,
  filterStatus: 'TODAS' // 'TODAS' | 'ACTIVAS' | 'HISTORICAS'
};

/**
 * Formatea una fecha ISO en formato colombiano
 */
function formatAppointmentDate(isoString) {
  try {
    const d = new Date(isoString);
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    }).format(d);
  } catch {
    return isoString;
  }
}

/**
 * Calcula si la cita puede ser cancelada según la regla de las 2 horas (ADR-006, HU-05)
 * @param {string} fechaHoraInicio
 * @returns {{ canCancel: boolean, hoursRemaining: number, reason: string }}
 */
function checkCancellationAllowed(fechaHoraInicio) {
  try {
    const slotTime = new Date(fechaHoraInicio).getTime();
    const now = Date.now();
    const diffMillis = slotTime - now;
    const diffHours = diffMillis / (1000 * 60 * 60);

    if (diffHours <= 0) {
      return {
        canCancel: false,
        hoursRemaining: 0,
        reason: 'Esta cita ya ha iniciado o transcurrido en el pasado.'
      };
    }

    if (diffHours < 2) {
      return {
        canCancel: false,
        hoursRemaining: diffHours,
        reason: 'La cancelación por parte del paciente solo está permitida hasta 2 horas antes de la cita.'
      };
    }

    return {
      canCancel: true,
      hoursRemaining: diffHours,
      reason: ''
    };
  } catch {
    return { canCancel: false, hoursRemaining: 0, reason: 'Fecha inválida' };
  }
}

/**
 * Mapeo de estado de cita a badge visual accesible
 */
function getStatusBadge(estado) {
  switch (estado) {
    case 'PROGRAMADA':
      return `<span class="badge badge--scheduled">${ui.icon('clock', 'icon icon--sm')} Programada</span>`;
    case 'CONFIRMADA':
      return `<span class="badge badge--confirmed">${ui.icon('check', 'icon icon--sm')} Confirmada</span>`;
    case 'ATENDIDA':
      return `<span class="badge badge--attended">${ui.icon('activity', 'icon icon--sm')} Atendida</span>`;
    case 'CANCELADA':
      return `<span class="badge badge--cancelled">${ui.icon('x', 'icon icon--sm')} Cancelada</span>`;
    case 'REPROGRAMADA':
      return `<span class="badge badge--rescheduled">${ui.icon('calendar', 'icon icon--sm')} Reprogramada</span>`;
    case 'NO_ASISTIO':
      return `<span class="badge badge--cancelled">${ui.icon('alert-circle', 'icon icon--sm')} No Asistió</span>`;
    default:
      return `<span class="badge badge--neutral">${estado}</span>`;
  }
}

/**
 * Vista principal de Mis Citas (#/patient/appointments)
 * @param {HTMLElement} container Contenedor DOM
 */
export async function patientAppointmentsView(container) {
  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Encabezado con acción rápida -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <div class="flex items-center gap-2 mb-1">
            <a href="#/patient/dashboard" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver al panel" title="Volver al panel">
              ${ui.icon('arrow-left')}
            </a>
            <h1 class="text-2xl font-bold m-0">Mis Citas Médicas</h1>
          </div>
          <p class="text-sm text-muted m-0">Revisa tus citas programadas, historial y cancelaciones</p>
        </div>

        <a href="#/patient/book" class="btn btn-primary" id="btnBookNewAppointment">
          ${ui.icon('plus')}
          <span>Agendar nueva cita</span>
        </a>
      </div>

      <!-- Filtros rápidos por estado de cita -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6 pb-3 border-b">
        <div class="chip-group" id="appointmentStatusFilters">
          <button type="button" class="chip ${appointmentsState.filterStatus === 'TODAS' ? 'is-selected' : ''}" data-status="TODAS">
            Todas
          </button>
          <button type="button" class="chip ${appointmentsState.filterStatus === 'ACTIVAS' ? 'is-selected' : ''}" data-status="ACTIVAS">
            Próximas y Activas
          </button>
          <button type="button" class="chip ${appointmentsState.filterStatus === 'HISTORICAS' ? 'is-selected' : ''}" data-status="HISTORICAS">
            Finalizadas y Canceladas
          </button>
        </div>

        <span id="appointmentCountBadge" class="text-xs text-muted font-medium"></span>
      </div>

      <!-- Contenedor dinámico de listado -->
      <div id="appointmentsListContainer">
        <div class="skeleton skeleton-card" style="height: 140px; margin-bottom: var(--space-4);"></div>
        <div class="skeleton skeleton-card" style="height: 140px;"></div>
      </div>
    </div>
  `;

  setupFilterListeners(container);
  await loadAppointments(container);
}

/**
 * Consulta las citas del paciente desde la API
 */
async function loadAppointments(container) {
  const listContainer = container.querySelector('#appointmentsListContainer');
  const countBadge = container.querySelector('#appointmentCountBadge');
  ui.renderLoading(listContainer, 'Cargando tus citas médicas...');

  try {
    const res = await api.get('/patients/me/appointments', {
      page: appointmentsState.page,
      size: appointmentsState.size
    });

    appointmentsState.citas = res?.content || [];
    appointmentsState.totalPages = res?.totalPages || 1;
    appointmentsState.totalElements = res?.totalElements || 0;

    if (countBadge) {
      countBadge.textContent = `${appointmentsState.totalElements} cita(s) en total`;
    }

    renderAppointmentsList(container);
  } catch (err) {
    ui.renderError(listContainer, {
      title: 'No fue posible cargar tus citas',
      message: err.message || 'Ocurrió un error al obtener la lista de citas.',
      onRetry: () => loadAppointments(container)
    });
  }
}

/**
 * Configura los botones de filtro de estado
 */
function setupFilterListeners(container) {
  container.querySelectorAll('#appointmentStatusFilters button').forEach(btn => {
    btn.addEventListener('click', () => {
      container.querySelectorAll('#appointmentStatusFilters button').forEach(b => b.classList.remove('is-selected'));
      btn.classList.add('is-selected');
      appointmentsState.filterStatus = btn.getAttribute('data-status');
      renderAppointmentsList(container);
    });
  });
}

/**
 * Renderiza la lista filtrada de citas
 */
function renderAppointmentsList(container) {
  const listContainer = container.querySelector('#appointmentsListContainer');
  if (!listContainer) return;

  // Aplicar filtro de estado
  let filtered = appointmentsState.citas;
  if (appointmentsState.filterStatus === 'ACTIVAS') {
    filtered = filtered.filter(c => c.estado === 'PROGRAMADA' || c.estado === 'CONFIRMADA');
  } else if (appointmentsState.filterStatus === 'HISTORICAS') {
    filtered = filtered.filter(c => ['ATENDIDA', 'CANCELADA', 'NO_ASISTIO', 'REPROGRAMADA'].includes(c.estado));
  }

  if (filtered.length === 0) {
    ui.renderEmpty(listContainer, {
      icon: 'calendar',
      title: appointmentsState.filterStatus === 'ACTIVAS' ? 'No tienes citas activas próximas' : 'No se encontraron citas',
      description: 'Aún no registras citas con el filtro seleccionado. Puedes agendar una consulta médica en cualquier momento.',
      actionText: 'Agendar cita médica',
      onAction: () => router.navigate('/patient/book')
    });
    return;
  }

  listContainer.innerHTML = `
    <div class="flex flex-col gap-4">
      ${filtered.map(cita => {
        const isTele = cita.modalidad === 'TELEMEDICINA';
        const cancelCheck = checkCancellationAllowed(cita.fechaHoraInicio);
        const isActive = cita.estado === 'PROGRAMADA' || cita.estado === 'CONFIRMADA';

        return `
          <div class="card p-5" id="card-cita-${cita.publicId}" style="border-left: 4px solid ${isActive ? 'var(--primary)' : 'var(--border)'};">
            <div class="flex flex-wrap items-center justify-between gap-3 pb-3 border-b mb-3">
              <div class="flex items-center gap-2">
                ${getStatusBadge(cita.estado)}
                <span class="badge ${isTele ? 'badge--rescheduled' : 'badge--neutral'} text-xs">
                  ${isTele ? 'Telemedicina' : 'Presencial'}
                </span>
                ${cita.triajePublicId ? `
                  <span class="badge badge--scheduled text-xs" title="Cita vinculada a triaje clínico">
                    ${ui.icon('activity', 'icon icon--sm')} Triaje
                  </span>
                ` : ''}
              </div>
              <span class="text-xs text-muted font-mono">ID: ${cita.publicId.slice(0, 8)}</span>
            </div>

            <div class="grid grid-cols-1 grid-cols-3-md gap-4 items-center">
              <!-- Fecha y Hora -->
              <div>
                <span class="text-xs text-muted font-bold uppercase tracking-wider block">Fecha y Hora:</span>
                <p class="text-md font-bold m-0 text-text capitalize">
                  ${formatAppointmentDate(cita.fechaHoraInicio)}
                </p>
              </div>

              <!-- Profesional y Especialidad -->
              <div>
                <span class="text-xs text-muted font-bold uppercase tracking-wider block">Profesional:</span>
                <p class="text-md font-semibold m-0">${cita.profesionalNombre || 'Profesional Asistencial'}</p>
                <span class="badge badge--neutral text-xs">${cita.especialidadNombre || 'Medicina'}</span>
              </div>

              <!-- Sede y Ubicación -->
              <div>
                <span class="text-xs text-muted font-bold uppercase tracking-wider block">Lugar:</span>
                <p class="text-sm font-semibold m-0">${cita.sedeNombre || 'Centro Médico'}</p>
                <p class="text-xs text-muted m-0">${cita.sedeDireccion || 'Consultorio asignado'}</p>
              </div>
            </div>

            ${cita.estado === 'CANCELADA' && cita.motivoCancelacion ? `
              <div class="alert alert--neutral mt-4 p-3" style="font-size: var(--text-xs);">
                <strong class="text-muted block">Motivo de cancelación:</strong>
                <span>${cita.motivoCancelacion}</span>
              </div>
            ` : ''}

            <!-- Zona de Acciones y Cancelación -->
            ${isActive ? `
              <div class="flex flex-wrap items-center justify-between gap-3 mt-4 pt-3 border-t">
                ${cancelCheck.canCancel ? `
                  <span class="text-xs text-muted">
                    Puedes cancelar esta cita hasta 2 horas antes del inicio programado.
                  </span>
                  <button 
                    type="button" 
                    class="btn btn-secondary btn--sm btn-cancel-appointment" 
                    data-cita-id="${cita.publicId}"
                    style="color: var(--danger); border-color: var(--danger-bg);"
                  >
                    ${ui.icon('x', 'icon icon--sm')}
                    <span>Cancelar cita</span>
                  </button>
                ` : `
                  <div class="flex items-center gap-2 text-xs text-muted" role="note">
                    ${ui.icon('info', 'icon icon--sm')}
                    <span>${cancelCheck.reason}</span>
                  </div>
                  <button type="button" class="btn btn-secondary btn--sm" disabled title="${cancelCheck.reason}">
                    <span>Cancelar cita</span>
                  </button>
                `}
              </div>
            ` : ''}
          </div>
        `;
      }).join('')}
    </div>
  `;

  // Asignar listeners de cancelación
  listContainer.querySelectorAll('.btn-cancel-appointment').forEach(btn => {
    btn.addEventListener('click', () => {
      const citaId = btn.getAttribute('data-cita-id');
      const cita = appointmentsState.citas.find(c => c.publicId === citaId);
      if (!cita) return;

      promptCancellation(container, cita);
    });
  });
}

/**
 * Muestra el modal de confirmación de cancelación con captura de motivo opcional
 */
function promptCancellation(container, cita) {
  const fechaTexto = formatAppointmentDate(cita.fechaHoraInicio);

  const modalHtml = `
    <div>
      <p class="mb-4">
        ¿Estás seguro de que deseas cancelar tu cita del <strong class="capitalize">${fechaTexto}</strong> con <strong>${cita.profesionalNombre}</strong>?
      </p>
      <div class="form-group mb-2">
        <label for="cancelReasonInput" class="form-label text-xs">Motivo de la cancelación (opcional):</label>
        <textarea 
          id="cancelReasonInput" 
          class="form-input" 
          rows="2" 
          maxlength="255" 
          placeholder="Ej. Inconveniente personal, viaje, mejoría de los síntomas..."
        ></textarea>
      </div>
      <p class="text-xs text-muted m-0">
        Al confirmar, el turno quedará liberado en la agenda para otro paciente.
      </p>
    </div>
  `;

  ui.showModal({
    title: '¿Confirmar cancelación de cita?',
    message: modalHtml,
    confirmText: 'Sí, cancelar cita',
    cancelText: 'Volver',
    isDanger: true,
    onConfirm: async () => {
      const motivo = document.getElementById('cancelReasonInput')?.value?.trim() || null;
      await executeCancellation(container, cita.publicId, motivo);
    }
  });
}

/**
 * Ejecuta la cancelación de la cita contra el backend PATCH /api/v1/appointments/{id}/cancel
 */
async function executeCancellation(container, citaPublicId, motivo) {
  try {
    const updatedCita = await api.patch(`/appointments/${citaPublicId}/cancel`, {
      motivo: motivo || undefined
    });

    ui.showToast('Tu cita médica ha sido cancelada correctamente.', 'info');

    // Actualizar el estado de la cita en el listado local sin recargar todo
    const index = appointmentsState.citas.findIndex(c => c.publicId === citaPublicId);
    if (index !== -1) {
      appointmentsState.citas[index] = {
        ...appointmentsState.citas[index],
        estado: 'CANCELADA',
        motivoCancelacion: motivo
      };
      renderAppointmentsList(container);
    }
  } catch (err) {
    ui.showToast(err.message || 'No fue posible cancelar la cita médica.', 'danger');
  }
}
