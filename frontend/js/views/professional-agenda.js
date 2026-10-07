/**
 * MediTriaje 2.0 — Agenda del Profesional (professional-agenda.js)
 * Lista cronológica de citas propias del día con acceso directo a "Iniciar atención"
 * (M8.3, HU-06, HU-07). El backend decide toda la autorización (ADR-007).
 */

import { api } from '../api.js';
import { auth } from '../auth.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';
import { showMfaModal } from './mfa-setup-modal.js';
import { showBreakGlassModal } from './break-glass-modal.js';
import { showChangePasswordModal } from './change-password-modal.js';

const ESTADOS = [
  { value: '', label: 'Todos los estados' },
  { value: 'PROGRAMADA', label: 'Programada' },
  { value: 'CONFIRMADA', label: 'Confirmada' },
  { value: 'ATENDIDA', label: 'Atendida' },
  { value: 'CANCELADA', label: 'Cancelada' },
  { value: 'NO_ASISTIO', label: 'No asistió' }
];

/** Fecha de hoy (YYYY-MM-DD) en zona America/Bogota. */
function todayBogota() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date());
}

function formatTime(iso) {
  return new Intl.DateTimeFormat('es-CO', {
    timeZone: 'America/Bogota', hour: '2-digit', minute: '2-digit', hour12: true
  }).format(new Date(iso));
}

function formatLongDate(isoDate) {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Intl.DateTimeFormat('es-CO', {
    timeZone: 'UTC', weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
  }).format(new Date(Date.UTC(y, m - 1, d)));
}

function statusBadge(estado) {
  const map = {
    PROGRAMADA: ['badge--scheduled', 'clock', 'Programada'],
    CONFIRMADA: ['badge--confirmed', 'check', 'Confirmada'],
    ATENDIDA: ['badge--attended', 'activity', 'Atendida'],
    CANCELADA: ['badge--cancelled', 'x', 'Cancelada'],
    NO_ASISTIO: ['badge--cancelled', 'alert-circle', 'No asistió'],
    REPROGRAMADA: ['badge--rescheduled', 'calendar', 'Reprogramada']
  };
  const [cls, icon, label] = map[estado] || ['badge--neutral', 'info', esc(estado)];
  return `<span class="badge ${cls}">${ui.icon(icon, 'icon icon--sm')} ${label}</span>`;
}

export async function professionalAgendaView(container) {
  const state = { fecha: todayBogota(), estado: '' };
  const debeCambiarPass = Boolean(auth.user?.debeCambiarPassword);

  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-bottom: var(--space-12);">
      ${debeCambiarPass ? `
        <div class="alert alert--warning mb-6 flex flex-wrap items-center justify-between gap-3" role="alert">
          <div class="flex items-center gap-3">
            ${ui.icon('alert-triangle', 'icon alert-icon text-warning')}
            <div>
              <strong class="block">Contraseña temporal activa</strong>
              <span class="text-sm">Por tu seguridad institucional, debes actualizar la contraseña temporal entregada por el administrador.</span>
            </div>
          </div>
          <button type="button" id="btnBannerCambiarPass" class="btn btn-warning btn--sm">
            ${ui.icon('lock')}
            <span>Actualizar contraseña ahora</span>
          </button>
        </div>
      ` : ''}

      <div class="mb-6 flex justify-between items-center flex-wrap gap-4">
        <div>
          <h1 class="text-2xl font-bold mb-1">Agenda del día</h1>
          <p class="text-sm text-muted m-0">Tus citas asignadas en orden cronológico</p>
        </div>
        <div class="flex items-center gap-2">
          <button type="button" id="btnCambiarPassword" class="btn btn-secondary btn--sm" title="Actualizar contraseña de acceso">
            ${ui.icon('lock', 'icon icon--sm')}
            <span>Contraseña</span>
          </button>
          <button type="button" id="btnBreakGlass" class="btn btn-secondary btn--sm" title="Activar acceso clínico de emergencia ante urgencias vitales (ADR-017)">
            ${ui.icon('alert-triangle', 'icon icon--sm text-danger')}
            <span>Acceso Emergencia</span>
          </button>
          <button type="button" id="btnConfigurarMfa" class="btn btn-secondary btn--sm">
            ${ui.icon('shield', 'icon icon--sm')}
            <span>Seguridad MFA</span>
          </button>
        </div>
      </div>

      <div class="card mb-6">
        <div class="card-body">
          <form id="agendaFilters" class="grid grid-cols-1 grid-cols-3-md gap-4 items-end">
            <div class="form-group m-0">
              <label for="agendaDate" class="form-label text-xs">Fecha</label>
              <input type="date" id="agendaDate" class="form-input" value="${state.fecha}">
            </div>
            <div class="form-group m-0">
              <label for="agendaStatus" class="form-label text-xs">Estado</label>
              <select id="agendaStatus" class="form-select">
                ${ESTADOS.map(e => `<option value="${e.value}">${e.label}</option>`).join('')}
              </select>
            </div>
            <div class="flex gap-2">
              <button type="submit" class="btn btn-primary w-full">${ui.icon('search')}<span>Consultar</span></button>
              <button type="button" id="btnToday" class="btn btn-secondary">Hoy</button>
            </div>
          </form>
        </div>
      </div>

      <div id="agendaList" aria-live="polite"></div>
    </div>
  `;

  const listEl = container.querySelector('#agendaList');
  const dateEl = container.querySelector('#agendaDate');
  const statusEl = container.querySelector('#agendaStatus');

  async function load() {
    ui.renderLoading(listEl, 'Consultando tu agenda...');
    try {
      const res = await api.get('/professionals/me/agenda', {
        fecha: state.fecha || undefined,
        estado: state.estado || undefined,
        page: 0,
        size: 100
      });
      render(res?.content || []);
    } catch (err) {
      ui.renderError(listEl, {
        title: 'No fue posible cargar la agenda',
        message: err.message,
        onRetry: load
      });
    }
  }

  function render(citas) {
    if (citas.length === 0) {
      ui.renderEmpty(listEl, {
        icon: 'calendar',
        title: 'Sin citas para este filtro',
        description: state.fecha ? `No tienes citas el ${formatLongDate(state.fecha)}.` : 'No tienes citas con este filtro.'
      });
      return;
    }

    listEl.innerHTML = `
      <p class="text-xs text-muted font-bold uppercase tracking-wider mb-3">
        ${citas.length} cita(s)${state.fecha ? ' · ' + formatLongDate(state.fecha) : ''}
      </p>
      <div class="flex flex-col gap-3">
        ${citas.map(c => {
          const canStart = c.estado === 'PROGRAMADA' || c.estado === 'CONFIRMADA';
          const yaIniciada = c.fechaHoraInicio ? (new Date(c.fechaHoraInicio).getTime() <= Date.now()) : false;
          const canNoShow = c.estado === 'PROGRAMADA' && yaIniciada && !c.tieneAtencion;
          const fechaCita = c.fechaHoraInicio ? formatLongDate(new Date(c.fechaHoraInicio).toISOString().slice(0, 10)) : '';
          const horaInicio = formatTime(c.fechaHoraInicio);
          const horaFin = c.fechaHoraFin ? formatTime(c.fechaHoraFin) : '';
          const isTele = c.modalidad === 'TELEMEDICINA';

          return `
            <div class="card p-4" style="border-left: 4px solid ${canStart ? 'var(--primary)' : 'var(--border)'};">
              <div class="flex flex-wrap items-center justify-between gap-3">
                <div class="flex items-start gap-4">
                  <!-- Horario y Día -->
                  <div style="min-width: 7.5rem;">
                    <span class="text-lg font-bold block text-primary">${horaInicio} ${horaFin ? `<span class="text-xs text-muted font-normal block">– ${horaFin}</span>` : ''}</span>
                    <span class="text-xs text-muted font-medium block capitalize mt-0.5">${fechaCita}</span>
                    <span class="badge ${isTele ? 'badge--rescheduled' : 'badge--neutral'} text-xs mt-1" style="font-size: 11px;">
                      ${isTele ? 'Telemedicina' : (esc(c.sedeNombre) || 'Presencial')}
                    </span>
                  </div>

                  <!-- Paciente y Detalles -->
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center gap-2">
                      ${ui.icon('user', 'icon icon--sm text-primary')}
                      <span class="font-bold text-base block text-text">${esc(c.pacienteNombre)}</span>
                    </div>
                    <div class="flex flex-wrap items-center gap-2 mt-1.5">
                      ${statusBadge(c.estado)}
                      <span class="badge badge--neutral text-xs">${esc(c.especialidadNombre || 'Medicina General')}</span>
                      ${c.triajeNivel ? `
                        <span class="badge badge--triage-${esc(c.triajeNivel.toLowerCase())} text-xs font-bold">
                          ${ui.icon('activity', 'icon icon--xs')} Triaje Nivel ${esc(c.triajeNivel)}
                        </span>
                      ` : (c.triajePublicId ? `
                        <span class="badge badge--scheduled text-xs">
                          ${ui.icon('activity', 'icon icon--xs')} Triaje vinculado
                        </span>
                      ` : '')}
                    </div>

                    <!-- Razón de la consulta / Triaje Clínico -->
                    <div class="mt-2 p-2.5" style="background-color: var(--surface-2); border-radius: var(--radius-sm); border-left: 3px solid var(--primary); max-width: 600px;">
                      <div class="text-xs text-muted font-bold uppercase tracking-wider mb-1 flex items-center gap-1.5">
                        ${ui.icon('info', 'icon icon--xs text-primary')}
                        <span>Razón de la consulta:</span>
                      </div>
                      <p class="text-xs text-text m-0 font-medium leading-relaxed">
                        ${esc(c.motivoConsulta) || (c.triajePublicId ? 'Valoración médica asistencial según triaje clínico' : 'Consulta médica programada')}
                      </p>
                    </div>
                  </div>
                </div>

                <!-- Acciones -->
                <div class="flex items-center gap-2">
                  <a href="#/professional/patient-history/${esc(c.pacientePublicId)}" class="btn btn-secondary btn--sm" title="Consultar historia clínica del paciente">
                    ${ui.icon('file-text', 'icon icon--sm')}<span>Historial</span>
                  </a>
                  ${canNoShow ? `
                    <button type="button" class="btn btn-secondary btn--sm btn-no-show" data-cita="${esc(c.publicId)}" style="color: var(--danger); border-color: var(--danger-bg);" title="Registrar que el paciente no se presentó a su cita médica">
                      ${ui.icon('alert-circle', 'icon icon--sm')}<span>No asistió</span>
                    </button>` : ''}
                  ${canStart ? `
                    <button type="button" class="btn btn-primary btn-start" data-cita="${esc(c.publicId)}">
                      ${ui.icon('activity')}<span>Iniciar atención</span>
                    </button>` : ''}
                </div>
              </div>
            </div>`;
        }).join('')}
      </div>
    `;

    listEl.querySelectorAll('.btn-no-show').forEach(btn => {
      btn.addEventListener('click', () => {
        const citaId = btn.dataset.cita;
        ui.showModal({
          title: 'Registrar Inasistencia del Paciente',
          message: `
            <div class="alert alert--warning mb-3">
              ${ui.icon('alert-triangle', 'icon alert-icon text-warning')}
              <div class="alert-content">
                <strong class="block text-sm">¿Confirmar que el paciente no asistió?</strong>
                <p class="text-xs m-0">Esta acción cambiará de forma definitiva el estado de la cita a <strong>NO ASISTIÓ</strong>.</p>
              </div>
            </div>
            <p class="text-sm text-muted mb-0">
              El turno asignado no será liberado dado que la hora de inicio ya transcurrió. Esta acción queda registrada en la bitácora inmutable de auditoría.
            </p>
          `,
          confirmText: 'Confirmar No Asistió',
          cancelText: 'Cancelar',
          onConfirm: async () => {
            ui.setButtonLoading(btn, true);
            try {
              await api.patch(`/appointments/${citaId}/no-show`);
              ui.showToast('Inasistencia registrada exitosamente.', 'success');
              await load();
            } catch (err) {
              ui.setButtonLoading(btn, false);
              ui.showToast(err.message || 'No fue posible registrar la inasistencia.', 'danger');
            }
          }
        });
      });
    });

    listEl.querySelectorAll('.btn-start').forEach(btn => {
      btn.addEventListener('click', async () => {
        ui.setButtonLoading(btn, true);
        try {
          const atencion = await api.post('/attentions', { citaPublicId: btn.dataset.cita });
          router.navigate(`/professional/attention/${atencion.publicId}`);
        } catch (err) {
          ui.setButtonLoading(btn, false);
          ui.showToast(err.message || 'No fue posible iniciar la atención.', 'danger');
        }
      });
    });
  }

  container.querySelector('#agendaFilters').addEventListener('submit', (e) => {
    e.preventDefault();
    state.fecha = dateEl.value;
    state.estado = statusEl.value;
    load();
  });
  container.querySelector('#btnToday').addEventListener('click', () => {
    dateEl.value = state.fecha = todayBogota();
    load();
  });
  container.querySelector('#btnBreakGlass')?.addEventListener('click', () => {
    showBreakGlassModal();
  });
  container.querySelector('#btnConfigurarMfa')?.addEventListener('click', () => {
    showMfaModal();
  });
  container.querySelector('#btnCambiarPassword')?.addEventListener('click', () => {
    showChangePasswordModal({ isMandatory: false });
  });
  container.querySelector('#btnBannerCambiarPass')?.addEventListener('click', () => {
    showChangePasswordModal({ isMandatory: true });
  });

  await load();
}
