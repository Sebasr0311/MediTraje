/**
 * MediTriaje 2.0 — Asignación y Traslados Intrahospitalarios (hospital-bed-management.js)
 * Asignación de cama inicial y traslados longitudinales entre pabellones y unidades (H01, H02, H03).
 */

import { hospitalApi, emergencyApi } from '../api.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

function formatDateTime(iso) {
  if (!iso) return '—';
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export async function hospitalBedManagementView(container, { params }) {
  const episodePublicId = params?.id || params?.episodioId;

  if (!episodePublicId) {
    ui.renderEmpty(container, {
      title: 'Episodio no especificado',
      description: 'Identificador de episodio hospitalario no encontrado.',
      actionText: 'Volver al Centro de Control',
      actionUrl: '#/hospital/census'
    });
    return;
  }

  container.innerHTML = `
    <div style="max-width: 850px; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Navegación -->
      <div class="mb-4">
        <a href="#/hospital/census" class="btn btn-ghost btn--sm flex items-center gap-1" style="width: fit-content;">
          ${ui.icon('arrow-left', 'icon icon--sm')}
          <span>Volver al Centro de Control</span>
        </a>
      </div>

      <!-- Resumen del Paciente y Episodio -->
      <div class="card p-6 mb-6" id="bedEpSummaryCard">
        <p class="text-sm text-muted">Cargando información del paciente...</p>
      </div>

      <!-- Historial de Movimientos y Traslados Longitudinales -->
      <div class="card p-6 mb-6">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-base font-bold m-0 flex items-center gap-2">
            ${ui.icon('activity', 'icon icon--sm')}
            <span>Timeline de Movimientos y Traslados Intrahospitalarios</span>
          </h2>
          <span class="text-xs text-muted">Ledger inmutable (H03)</span>
        </div>
        <div id="movementsTimelineContainer">
          <p class="text-sm text-muted">Cargando traslados...</p>
        </div>
      </div>

      <!-- Formulario de Asignación / Traslado -->
      <div class="card p-6">
        <h2 class="text-lg font-bold mb-4" id="formBedTitle">Asignación de Cama Hospitalaria</h2>

        <form id="bedActionForm" novalidate>
          
          <div class="form-group mb-4">
            <label for="selCamaDestino" class="form-label font-semibold">Seleccionar Cama Disponible <span class="text-danger">*</span></label>
            <select id="selCamaDestino" class="form-select" required>
              <option value="">Cargando camas disponibles...</option>
            </select>
          </div>

          <div class="form-group mb-6">
            <label for="txtMotivoCama" class="form-label font-semibold">Motivo Asistencial / Indicación de Ubicación <span class="text-danger">*</span></label>
            <textarea id="txtMotivoCama" class="form-input" rows="3" required placeholder="Justificación médica del ingreso a sala o traslado..."></textarea>
          </div>

          <div class="flex items-center justify-end gap-3">
            <a href="#/hospital/census" class="btn btn-ghost">Cancelar</a>
            <button type="submit" id="btnSubmitBedAction" class="btn btn-primary flex items-center gap-2">
              ${ui.icon('check', 'icon icon--sm')}
              <span id="btnSubmitBedActionText">Asignar Cama</span>
            </button>
          </div>

        </form>
      </div>

    </div>
  `;

  let episodio = null;
  let camasDisponibles = [];
  let movimientos = [];
  let tieneCamaAsignada = false;

  try {
    episodio = await emergencyApi.obtenerDetalleEpisodio(episodePublicId);
    renderSummary(episodio);

    movimientos = await hospitalApi.listarMovimientos(episodePublicId);
    renderMovements(movimientos);

    tieneCamaAsignada = movimientos && movimientos.length > 0;
    if (tieneCamaAsignada) {
      document.getElementById('formBedTitle').textContent = 'Traslado Intrahospitalario de Paciente';
      document.getElementById('btnSubmitBedActionText').textContent = 'Confirmar Traslado';
    }

    // Cargar camas disponibles de la sede del episodio
    const todasCamas = await hospitalApi.listarCamas(episodio.sedePublicId);
    camasDisponibles = (todasCamas || []).filter(c => c.estado === 'DISPONIBLE');

    const selCama = document.getElementById('selCamaDestino');
    if (camasDisponibles.length > 0) {
      selCama.innerHTML = `<option value="">Seleccione una cama...</option>` + camasDisponibles.map(c => `
        <option value="${esc(c.publicId)}">${esc(c.codigo)} — ${esc(c.areaNombre)} (${esc(c.habitacionCodigo)})</option>
      `).join('');
    } else {
      selCama.innerHTML = `<option value="">No hay camas disponibles en esta sede</option>`;
    }
  } catch (err) {
    ui.showToast('Error cargando información: ' + err.message, 'error');
  }

  // Envío del formulario
  const form = document.getElementById('bedActionForm');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const camaId = document.getElementById('selCamaDestino').value;
    const motivo = document.getElementById('txtMotivoCama').value.trim();

    if (!camaId) {
      ui.showToast('Debe seleccionar una cama disponible.', 'warning');
      return;
    }

    if (!motivo) {
      ui.showToast('El motivo es obligatorio.', 'warning');
      return;
    }

    const btn = document.getElementById('btnSubmitBedAction');
    btn.disabled = true;

    try {
      if (tieneCamaAsignada) {
        // Traslado
        await hospitalApi.trasladarPaciente(episodePublicId, {
          camaDestinoPublicId: camaId,
          motivoTraslado: motivo
        });
        ui.showToast('Traslado intrahospitalario registrado con éxito.', 'success');
      } else {
        // Asignación inicial
        await hospitalApi.asignarCama(episodePublicId, {
          camaPublicId: camaId,
          motivoAsignacion: motivo
        });
        ui.showToast('Cama asignada exitosamente.', 'success');
      }
      router.navigate('#/hospital/census');
    } catch (err) {
      btn.disabled = false;
      ui.showToast('Error en asignación/traslado: ' + (err.message || 'Error de servidor'), 'error');
    }
  });
}

function renderSummary(ep) {
  const card = document.getElementById('bedEpSummaryCard');
  card.innerHTML = `
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-xl font-bold m-0">${esc(ep.pacienteNombre || 'Paciente No Identificado')}</h1>
        <div class="text-xs text-muted mt-1 font-mono">
          Episodio: ${esc(ep.episodioPublicId)} · Sede: ${esc(ep.sedeNombre || 'Valledupar')}
        </div>
      </div>
      <span class="badge ${ep.estado === 'HOSPITALIZADO' ? 'badge--in-progress' : 'badge--scheduled'} font-bold">
        ${esc(ep.estado)}
      </span>
    </div>
  `;
}

function renderMovements(list) {
  const container = document.getElementById('movementsTimelineContainer');
  if (!list || list.length === 0) {
    container.innerHTML = `<p class="text-sm text-muted m-0">No se registran movimientos ni camas previas en este episodio.</p>`;
    return;
  }

  container.innerHTML = `
    <div class="flex flex-col gap-3">
      ${list.map((m, idx) => `
        <div class="card p-3 border bg-slate-50 flex items-start justify-between">
          <div>
            <div class="flex items-center gap-2 mb-1">
              <span class="badge badge--dark text-xs">#${idx + 1}</span>
              <span class="text-xs text-muted">${formatDateTime(m.fechaMovimiento)}</span>
            </div>
            <div class="font-bold text-sm">
              ${m.camaOrigenCodigo ? esc(m.camaOrigenCodigo) + ' (' + esc(m.areaOrigenNombre) + ')' : 'Admisión'}
              &rarr;
              ${esc(m.camaDestinoCodigo || '—')} (${esc(m.areaDestinoNombre || '—')})
            </div>
            <div class="text-xs text-muted mt-1">
              <strong>Motivo:</strong> ${esc(m.motivoTraslado)}
            </div>
          </div>
          <span class="text-xs text-muted font-mono">${esc(m.registradoPorNombre || '')}</span>
        </div>
      `).join('')}
    </div>
  `;
}
