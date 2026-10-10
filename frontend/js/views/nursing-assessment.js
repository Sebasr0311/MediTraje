/**
 * MediTriaje 2.0 — Valoración Clínica de Triaje y Reevaluación (nursing-assessment.js)
 * Clasificación presencial I a V (Resolución 5596/2015 MinSalud Colombia),
 * registro de signos vitales, escala de Glasgow y registro inmutable append-only (U04, ADR-027).
 */

import { emergencyApi, api } from '../api.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

function formatTriageBadge(nivel) {
  switch (nivel) {
    case 'I':
      return `<span class="badge" style="background-color: var(--danger, #dc2626); color: #fff; font-weight: 700; padding: 4px 10px; border-radius: 9999px;">🔴 Nivel I — Reanimación Inmediata</span>`;
    case 'II':
      return `<span class="badge" style="background-color: #ea580c; color: #fff; font-weight: 700; padding: 4px 10px; border-radius: 9999px;">🟠 Nivel II — Emergencia (< 30 min)</span>`;
    case 'III':
      return `<span class="badge" style="background-color: #ca8a04; color: #fff; font-weight: 700; padding: 4px 10px; border-radius: 9999px;">🟡 Nivel III — Urgencia (< 120 min)</span>`;
    case 'IV':
      return `<span class="badge" style="background-color: #16a34a; color: #fff; font-weight: 700; padding: 4px 10px; border-radius: 9999px;">🟢 Nivel IV — Prioritaria (< 180 min)</span>`;
    case 'V':
      return `<span class="badge" style="background-color: #2563eb; color: #fff; font-weight: 700; padding: 4px 10px; border-radius: 9999px;">🔵 Nivel V — No Urgente (< 240 min)</span>`;
    default:
      return `<span class="badge badge--scheduled">Pendiente</span>`;
  }
}

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

export async function nursingAssessmentView(container, { params }) {
  const episodePublicId = params?.id || params?.episodioId;

  if (!episodePublicId) {
    ui.renderEmpty(container, {
      title: 'Episodio no especificado',
      description: 'No se suministró un identificador de episodio de urgencias válido.',
      actionText: 'Volver a la cola',
      actionUrl: '#/nursing/dashboard'
    });
    return;
  }

  container.innerHTML = `
    <div style="max-width: 900px; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Navegación -->
      <div class="mb-4">
        <a href="#/nursing/dashboard" class="btn btn-ghost btn--sm flex items-center gap-1" style="width: fit-content;">
          ${ui.icon('arrow-left', 'icon icon--sm')}
          <span>Volver a la Cola de Urgencias</span>
        </a>
      </div>

      <!-- Resumen del Paciente y Episodio -->
      <div class="card p-6 mb-6" id="episodeSummaryCard">
        <div class="text-center p-4 text-muted">Cargando información del paciente y episodio...</div>
      </div>

      <!-- Historial de Valoraciones Previas (Inmutable) -->
      <div class="card p-6 mb-6">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-bold m-0 flex items-center gap-2">
            ${ui.icon('clock', 'icon icon--sm')}
            <span>Historial Clínico de Triaje (Inmutable)</span>
          </h2>
          <span class="text-xs text-muted">Resolución 5596 MinSalud · Registro append-only</span>
        </div>
        <div id="triageHistoryContainer">
          <p class="text-sm text-muted">Consultando valoraciones...</p>
        </div>
      </div>

      <!-- Formulario de Nueva Valoración o Reevaluación -->
      <div class="card p-6">
        <div class="mb-4">
          <h2 class="text-xl font-bold m-0" id="formAssessmentTitle">Registro de Valoración de Triaje</h2>
          <p class="text-sm text-muted m-0">Evaluación médica y de enfermería de riesgo vital conforme a criterios clínicos estandarizados</p>
        </div>

        <form id="triageAssessmentForm" novalidate>
          
          <!-- Clasificación de Nivel -->
          <div class="form-group mb-6">
            <label class="form-label font-bold text-base">Nivel de Triaje Asignado <span class="text-danger">*</span></label>
            <div class="grid grid-cols-1 md:grid-cols-5 gap-2 mt-2">
              <label class="card p-3 text-center cursor-pointer border hover:border-danger transition-all select-triage-level" data-level="I">
                <input type="radio" name="rdoNivel" value="I" class="sr-only" required>
                <div class="text-lg font-bold text-danger">Nivel I</div>
                <div class="text-xs font-semibold">Reanimación</div>
                <div class="text-xs text-muted mt-1">Inmediato</div>
              </label>

              <label class="card p-3 text-center cursor-pointer border hover:border-orange-500 transition-all select-triage-level" data-level="II">
                <input type="radio" name="rdoNivel" value="II" class="sr-only">
                <div class="text-lg font-bold text-orange-600">Nivel II</div>
                <div class="text-xs font-semibold">Emergencia</div>
                <div class="text-xs text-muted mt-1">&le; 30 min</div>
              </label>

              <label class="card p-3 text-center cursor-pointer border hover:border-yellow-600 transition-all select-triage-level" data-level="III">
                <input type="radio" name="rdoNivel" value="III" class="sr-only">
                <div class="text-lg font-bold text-yellow-600">Nivel III</div>
                <div class="text-xs font-semibold">Urgencia</div>
                <div class="text-xs text-muted mt-1">&le; 120 min</div>
              </label>

              <label class="card p-3 text-center cursor-pointer border hover:border-green-600 transition-all select-triage-level" data-level="IV">
                <input type="radio" name="rdoNivel" value="IV" class="sr-only">
                <div class="text-lg font-bold text-green-600">Nivel IV</div>
                <div class="text-xs font-semibold">Prioritaria</div>
                <div class="text-xs text-muted mt-1">&le; 180 min</div>
              </label>

              <label class="card p-3 text-center cursor-pointer border hover:border-blue-600 transition-all select-triage-level" data-level="V">
                <input type="radio" name="rdoNivel" value="V" class="sr-only">
                <div class="text-lg font-bold text-blue-600">Nivel V</div>
                <div class="text-xs font-semibold">No Urgente</div>
                <div class="text-xs text-muted mt-1">&le; 240 min</div>
              </label>
            </div>
          </div>

          <!-- Signos Vitales -->
          <div class="card p-4 mb-6" style="background-color: var(--card-bg, #f8fafc); border: 1px solid var(--border);">
            <div class="font-bold text-sm mb-3">Signos Vitales y Parámetros Clínicos</div>
            <div class="grid grid-cols-2 md:grid-cols-3 gap-4">
              
              <div class="form-group">
                <label for="txtPA" class="form-label text-xs font-semibold">Presión Arterial (PA)</label>
                <input type="text" id="txtPA" class="form-input" placeholder="Ej. 120/80">
              </div>

              <div class="form-group">
                <label for="txtFC" class="form-label text-xs font-semibold">Frecuencia Cardíaca (FC)</label>
                <input type="number" id="txtFC" class="form-input" min="20" max="250" placeholder="lpm (ej. 78)">
              </div>

              <div class="form-group">
                <label for="txtFR" class="form-label text-xs font-semibold">Frecuencia Respiratoria (FR)</label>
                <input type="number" id="txtFR" class="form-input" min="5" max="80" placeholder="rpm (ej. 18)">
              </div>

              <div class="form-group">
                <label for="txtSO2" class="form-label text-xs font-semibold">Saturación de O2 (SpO2 %)</label>
                <input type="number" id="txtSO2" class="form-input" min="40" max="100" placeholder="% (ej. 98)">
              </div>

              <div class="form-group">
                <label for="txtTemp" class="form-label text-xs font-semibold">Temperatura (°C)</label>
                <input type="number" step="0.1" id="txtTemp" class="form-input" min="30" max="45" placeholder="°C (ej. 36.8)">
              </div>

              <div class="form-group">
                <label for="txtGlasgow" class="form-label text-xs font-semibold">Escala de Glasgow (3-15)</label>
                <input type="number" id="txtGlasgow" class="form-input" min="3" max="15" placeholder="Puntos (3-15)">
              </div>

            </div>
          </div>

          <!-- Motivo y Hallazgos -->
          <div class="form-group mb-4">
            <label for="txtMotivo" class="form-label font-semibold">Motivo de Consulta Clínico <span class="text-danger">*</span></label>
            <textarea id="txtMotivo" class="form-input" rows="2" required placeholder="Motivo relatado por el paciente o acompañante..."></textarea>
          </div>

          <div class="form-group mb-4">
            <label for="txtHallazgos" class="form-label font-semibold">Hallazgos Clínicos y Exploración Inmediata</label>
            <textarea id="txtHallazgos" class="form-input" rows="3" placeholder="Estado general, vía aérea, ventilación, signos de shock o focalización..."></textarea>
          </div>

          <!-- Reevaluación (Si ya existe valoración previa) -->
          <div id="bloqueReevaluacion" style="display: none;" class="card p-4 mb-4 border-warning bg-amber-50">
            <div class="flex items-center gap-2 text-amber-800 font-bold text-sm mb-2">
              ${ui.icon('alert-circle', 'icon icon--sm')}
              <span>Esta valoración constituye una REEVALUACIÓN clínica</span>
            </div>
            <div class="form-group m-0">
              <label for="txtMotivoReevaluacion" class="form-label text-xs font-bold text-amber-900">Motivo del Cambio o Reevaluación <span class="text-danger">*</span></label>
              <textarea id="txtMotivoReevaluacion" class="form-input" rows="2" placeholder="Describa el cambio clínico (ej. agravamiento de dolor torácico, disminución de saturación, etc.)"></textarea>
            </div>
          </div>

          <!-- Acciones -->
          <div class="flex items-center justify-end gap-3 mt-6">
            <a href="#/nursing/dashboard" class="btn btn-ghost">Cancelar</a>
            <button type="submit" id="btnSubmitAssessment" class="btn btn-primary flex items-center gap-2">
              ${ui.icon('check', 'icon icon--sm')}
              <span>Guardar Clasificación de Triaje</span>
            </button>
          </div>

        </form>
      </div>

    </div>
  `;

  let episodioActual = null;
  let valoracionesPrevias = [];

  // Cargar Detalle y Valoraciones
  try {
    episodioActual = await emergencyApi.obtenerDetalleEpisodio(episodePublicId);
    renderEpisodeSummary(episodioActual);
    document.getElementById('txtMotivo').value = episodioActual.motivoConsulta || '';

    valoracionesPrevias = await emergencyApi.listarHistorialTriaje(episodePublicId);
    renderHistory(valoracionesPrevias);

    if (valoracionesPrevias && valoracionesPrevias.length > 0) {
      document.getElementById('bloqueReevaluacion').style.display = 'block';
      document.getElementById('formAssessmentTitle').textContent = `Reevaluación Clínica de Urgencias (Versión ${valoracionesPrevias.length + 1})`;
    }
  } catch (err) {
    ui.showToast('Error cargando información: ' + err.message, 'error');
  }

  // Selección visual de radio button para niveles
  const levelCards = container.querySelectorAll('.select-triage-level');
  levelCards.forEach(card => {
    card.addEventListener('click', () => {
      levelCards.forEach(c => c.style.borderColor = 'var(--border)');
      card.style.borderColor = 'var(--primary)';
      const radio = card.querySelector('input[type="radio"]');
      if (radio) radio.checked = true;
    });
  });

  // Envío del formulario
  const form = document.getElementById('triageAssessmentForm');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const radioNivel = form.querySelector('input[name="rdoNivel"]:checked');
    if (!radioNivel) {
      ui.showToast('Debe seleccionar un nivel de triaje (I al V).', 'warning');
      return;
    }

    const nivel = radioNivel.value;
    const motivoConsulta = document.getElementById('txtMotivo').value.trim();
    const hallazgosClinicos = document.getElementById('txtHallazgos').value.trim();
    const presionArterial = document.getElementById('txtPA').value.trim();
    const fc = document.getElementById('txtFC').value ? parseInt(document.getElementById('txtFC').value, 10) : null;
    const fr = document.getElementById('txtFR').value ? parseInt(document.getElementById('txtFR').value, 10) : null;
    const so2 = document.getElementById('txtSO2').value ? parseInt(document.getElementById('txtSO2').value, 10) : null;
    const temp = document.getElementById('txtTemp').value ? parseFloat(document.getElementById('txtTemp').value) : null;
    const glasgow = document.getElementById('txtGlasgow').value ? parseInt(document.getElementById('txtGlasgow').value, 10) : null;

    const esReevaluacion = valoracionesPrevias.length > 0;
    const motivoReevaluacion = esReevaluacion ? document.getElementById('txtMotivoReevaluacion').value.trim() : null;

    if (esReevaluacion && !motivoReevaluacion) {
      ui.showToast('El motivo de la reevaluación es obligatorio.', 'warning');
      return;
    }

    const payload = {
      nivel,
      motivoConsulta,
      hallazgosClinicos: hallazgosClinicos || null,
      presionArterial: presionArterial || null,
      frecuenciaCardiaca: fc,
      frecuenciaRespiratoria: fr,
      saturacionOxigeno: so2,
      temperatura: temp,
      escalaGlasgow: glasgow,
      esReevaluacion,
      motivoReevaluacion
    };

    const btnSubmit = document.getElementById('btnSubmitAssessment');
    btnSubmit.disabled = true;
    btnSubmit.innerHTML = `Guardando triaje...`;

    try {
      await emergencyApi.registrarValoracionTriaje(episodePublicId, payload);
      ui.showToast(`Clasificación Triaje Nivel ${nivel} registrada con éxito.`, 'success');
      router.navigate('#/nursing/dashboard');
    } catch (err) {
      btnSubmit.disabled = false;
      btnSubmit.innerHTML = `${ui.icon('check', 'icon icon--sm')} <span>Guardar Clasificación de Triaje</span>`;
      ui.showToast('Error al registrar triaje: ' + (err.message || 'Error de servidor'), 'error');
    }
  });
}

function renderEpisodeSummary(ep) {
  const container = document.getElementById('episodeSummaryCard');
  const isNN = ep.esIdentidadProvisional;

  container.innerHTML = `
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <div class="flex items-center gap-2">
          ${isNN ? `<span class="badge badge--warning font-bold">PROVISIONAL NN</span>` : ''}
          <h1 class="text-xl font-bold m-0">${esc(ep.pacienteNombre || 'Paciente No Identificado')}</h1>
        </div>
        <div class="text-xs text-muted mt-1 font-mono">
          Episodio: ${esc(ep.episodioPublicId)} · Sede: ${esc(ep.sedeNombre || 'Valledupar')}
        </div>
        ${isNN ? `<div class="text-xs text-indigo-700 font-bold mt-1">Código Identificador: ${esc(ep.codigoProvisional)}</div>` : ''}
      </div>

      <div class="text-right">
        <div class="text-xs text-muted">Estado Actual</div>
        <div class="badge ${ep.estado === 'EN_ATENCION' ? 'badge--in-progress' : 'badge--scheduled'} text-sm mt-1">
          ${esc(ep.estado)}
        </div>
      </div>
    </div>
  `;
}

function renderHistory(lista) {
  const container = document.getElementById('triageHistoryContainer');
  if (!lista || lista.length === 0) {
    container.innerHTML = `<p class="text-sm text-muted m-0">No se registran valoraciones clínicas previas para este episodio.</p>`;
    return;
  }

  container.innerHTML = `
    <div class="flex flex-col gap-3">
      ${lista.map(v => `
        <div class="card p-3 border" style="background-color: var(--surface-subtle, #f8fafc);">
          <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
            <div class="flex items-center gap-2">
              <span class="badge badge--dark text-xs font-bold">Versión ${v.version}</span>
              ${formatTriageBadge(v.nivel)}
              ${v.esReevaluacion ? `<span class="badge badge--warning text-xs">Reevaluación</span>` : ''}
            </div>
            <div class="text-xs text-muted font-mono">
              ${formatDateTime(v.creadoAt)} · Por: ${esc(v.evaluadorNombre || 'Profesional')}
            </div>
          </div>

          <div class="text-sm mb-1">
            <span class="font-semibold">Motivo:</span> ${esc(v.motivoConsulta)}
          </div>

          ${v.hallazgosClinicos ? `
            <div class="text-xs text-muted mb-2">
              <span class="font-semibold">Hallazgos:</span> ${esc(v.hallazgosClinicos)}
            </div>
          ` : ''}

          <div class="grid grid-cols-2 md:grid-cols-6 gap-2 text-xs font-mono p-2 rounded bg-white border">
            <div>PA: <strong>${esc(v.presionArterial || '—')}</strong></div>
            <div>FC: <strong>${v.frecuenciaCardiaca ? v.frecuenciaCardiaca + ' lpm' : '—'}</strong></div>
            <div>FR: <strong>${v.frecuenciaRespiratoria ? v.frecuenciaRespiratoria + ' rpm' : '—'}</strong></div>
            <div>SpO2: <strong>${v.saturacionOxigeno ? v.saturacionOxigeno + '%' : '—'}</strong></div>
            <div>Temp: <strong>${v.temperatura ? v.temperatura + '°C' : '—'}</strong></div>
            <div>Glasgow: <strong>${v.escalaGlasgow ? v.escalaGlasgow + '/15' : '—'}</strong></div>
          </div>

          ${v.motivoReevaluacion ? `
            <div class="text-xs text-amber-800 mt-2 font-medium">
              Motivo reevaluación: ${esc(v.motivoReevaluacion)}
            </div>
          ` : ''}
        </div>
      `).join('')}
    </div>
  `;
}
