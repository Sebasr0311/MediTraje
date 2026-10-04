/**
 * MediTraje 2.0 — Vista de Historia Clínica del Paciente (patient-history.js)
 * Línea de tiempo cronológica de atenciones cerradas inmutables, diagnósticos CIE-10,
 * signos vitales y enmiendas clínicas append-only (M8.2c, HU-09, ADR-007, ADR-008).
 */

import { api } from '../api.js';
import { auth } from '../auth.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

let historyState = {
  atenciones: [],
  page: 0,
  size: 20,
  totalPages: 1,
  totalElements: 0
};

/**
 * Formatea una fecha ISO en formato colombiano
 */
function formatClinicalDate(isoString) {
  if (!isoString) return '—';
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
 * Vista principal de Historia Clínica del Paciente (#/patient/history)
 * @param {HTMLElement} container Contenedor principal del DOM
 */
export async function patientHistoryView(container) {
  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Encabezado con acción de impresión -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <div class="flex items-center gap-2 mb-1">
            <a href="#/patient/dashboard" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver al panel" title="Volver al panel">
              ${ui.icon('arrow-left')}
            </a>
            <h1 class="text-2xl font-bold m-0">Mi Historia Clínica</h1>
          </div>
          <p class="text-sm text-muted m-0">Registro cronológico inmutable de atenciones y parámetros clínicos</p>
        </div>

        <div class="flex items-center gap-2">
          <button type="button" class="btn btn-secondary btn--sm" id="btnPrintHistory" title="Imprimir o guardar como PDF">
            ${ui.icon('file-text')}
            <span>Imprimir historia</span>
          </button>
        </div>
      </div>

      <!-- Contenedor dinámico del historial -->
      <div id="historyTimelineContainer">
        <div class="skeleton skeleton-card" style="height: 180px; margin-bottom: var(--space-4);"></div>
        <div class="skeleton skeleton-card" style="height: 180px;"></div>
      </div>
    </div>
  `;

  // Listener para botón de imprimir
  container.querySelector('#btnPrintHistory')?.addEventListener('click', () => {
    window.print();
  });

  await loadClinicalHistory(container);
}

/**
 * Consulta la historia clínica paginada desde la API
 */
async function loadClinicalHistory(container) {
  const timelineContainer = container.querySelector('#historyTimelineContainer');
  ui.renderLoading(timelineContainer, 'Cargando tu historia clínica...');

  try {
    const res = await api.get('/patients/me/history', {
      page: historyState.page,
      size: historyState.size
    });

    historyState.atenciones = res?.content || [];
    historyState.totalPages = res?.totalPages || 1;
    historyState.totalElements = res?.totalElements || 0;

    renderTimeline(container);
  } catch (err) {
    ui.renderError(timelineContainer, {
      title: 'No fue posible cargar tu historia clínica',
      message: err.message || 'Ocurrió un problema al consultar tus registros clínicos.',
      onRetry: () => loadClinicalHistory(container)
    });
  }
}

/**
 * Renderiza la línea de tiempo vertical de atenciones
 */
function renderTimeline(container) {
  const timelineContainer = container.querySelector('#historyTimelineContainer');
  if (!timelineContainer) return;

  if (historyState.atenciones.length === 0) {
    ui.renderEmpty(timelineContainer, {
      icon: 'file-text',
      title: 'Aún no tienes atenciones registradas',
      description: 'Cuando asistas a una consulta médica con nuestros profesionales, tu valoración clínica e indicaciones aparecerán aquí de forma inmutable.',
      actionText: 'Iniciar triaje o consulta',
      onAction: () => router.navigate('/patient/triage')
    });
    return;
  }

  timelineContainer.innerHTML = `
    <div class="mb-4 flex items-center justify-between">
      <span class="text-xs text-muted font-bold uppercase tracking-wider">
        ${historyState.totalElements} atención(es) clínica(s) registrada(s)
      </span>
      <span class="badge badge--confirmed text-xs">
        ${ui.icon('shield', 'icon icon--sm')} Registros Clínicos Inmutables
      </span>
    </div>

    <div class="timeline">
      ${historyState.atenciones.map((atencion, idx) => {
        const signos = atencion.signosVitales;
        const enmiendas = atencion.enmiendas || [];
        const hasEnmiendas = enmiendas.length > 0;

        return `
          <div class="timeline-item" id="atencion-${atencion.publicId}">
            <div class="timeline-marker"></div>

            <div class="card p-6" style="border-top: 4px solid var(--primary);">
              <!-- Encabezado de la atención -->
              <div class="flex flex-wrap items-center justify-between gap-3 pb-3 border-b mb-4">
                <div>
                  <span class="badge badge--scheduled text-xs font-semibold mb-1">
                    Atención #${historyState.totalElements - idx}
                  </span>
                  <h2 class="text-lg font-bold m-0 capitalize">
                    ${formatClinicalDate(atencion.fechaCierre || atencion.createdAt)}
                  </h2>
                </div>

                <div class="text-right">
                  <span class="text-sm font-semibold block">${atencion.profesionalNombre || 'Profesional Médico'}</span>
                  <span class="badge badge--neutral text-xs">${atencion.especialidadNombre || 'Medicina General'}</span>
                </div>
              </div>

              <!-- Diagnóstico Principal CIE-10 -->
              <div class="mb-5 p-3" style="background-color: var(--teal-50); border-radius: var(--radius-md); border-left: 4px solid var(--primary);">
                <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">Diagnóstico Principal (CIE-10):</span>
                <p class="text-md font-bold m-0 text-primary">
                  ${atencion.diagnosticoCodigo ? `${atencion.diagnosticoCodigo} — ` : ''}${atencion.diagnosticoDescripcion || 'Diagnóstico Clínico'}
                </p>
              </div>

              <!-- Motivo y Evolución Clínica -->
              <div class="grid grid-cols-1 grid-cols-2-md gap-4 mb-5">
                <div>
                  <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">Motivo de Consulta:</span>
                  <p class="text-sm m-0" style="color: var(--text);">
                    ${esc(atencion.motivoConsulta) || 'Consulta médica programada.'}
                  </p>
                </div>

                <div>
                  <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">Evolución Clínica:</span>
                  <p class="text-sm m-0" style="color: var(--text);">
                    ${esc(atencion.evolucion) || 'Paciente valorado en consulta externa.'}
                  </p>
                </div>
              </div>

              <!-- Signos Vitales Registrados -->
              ${signos ? `
                <div class="mb-5">
                  <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-2">Parámetros Fisiológicos (Signos Vitales):</span>
                  <div class="vitals-grid">
                    ${signos.presionSistolica && signos.presionDiastolica ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Presión Arterial</span>
                        <span class="vital-card-val">${signos.presionSistolica}/${signos.presionDiastolica} <small style="font-size: 11px;">mmHg</small></span>
                      </div>
                    ` : ''}

                    ${signos.frecuenciaCardiaca ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Frec. Cardíaca</span>
                        <span class="vital-card-val">${signos.frecuenciaCardiaca} <small style="font-size: 11px;">lpm</small></span>
                      </div>
                    ` : ''}

                    ${signos.frecuenciaRespiratoria ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Frec. Respiratoria</span>
                        <span class="vital-card-val">${signos.frecuenciaRespiratoria} <small style="font-size: 11px;">rpm</small></span>
                      </div>
                    ` : ''}

                    ${signos.temperatura ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Temperatura</span>
                        <span class="vital-card-val">${signos.temperatura} <small style="font-size: 11px;">°C</small></span>
                      </div>
                    ` : ''}

                    ${signos.saturacionOxigeno ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Sat. Oxígeno</span>
                        <span class="vital-card-val">${signos.saturacionOxigeno}%</span>
                      </div>
                    ` : ''}

                    ${signos.pesoKg ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Peso</span>
                        <span class="vital-card-val">${signos.pesoKg} <small style="font-size: 11px;">kg</small></span>
                      </div>
                    ` : ''}

                    ${signos.tallaCm ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Talla</span>
                        <span class="vital-card-val">${signos.tallaCm} <small style="font-size: 11px;">cm</small></span>
                      </div>
                    ` : ''}
                  </div>
                </div>
              ` : ''}

              <!-- Indicaciones y Recomendaciones del Médico -->
              ${atencion.indicaciones ? `
                <div class="p-4 mb-4" style="background-color: var(--surface-2); border-radius: var(--radius-md);">
                  <strong class="text-xs font-bold uppercase tracking-wider text-muted block mb-1">
                    Indicaciones Terapéuticas:
                  </strong>
                  <p class="text-sm m-0" style="color: var(--text); line-height: var(--leading-relaxed);">
                    ${esc(atencion.indicaciones)}
                  </p>
                </div>
              ` : ''}

              <!-- Enmiendas Clínicas (Append-Only) -->
              ${hasEnmiendas ? `
                <div class="mt-5 pt-4 border-t">
                  <div class="flex items-center gap-2 mb-3">
                    ${ui.icon('alert-triangle', 'icon icon--sm text-warning')}
                    <h3 class="text-sm font-bold text-warning uppercase tracking-wider m-0">
                      Aclaraciones y Enmiendas Médicas (${enmiendas.length})
                    </h3>
                  </div>

                  <div class="flex flex-col gap-3">
                    ${enmiendas.map(enm => `
                      <div class="p-3" style="background-color: var(--warning-bg); border-left: 3px solid var(--warning); border-radius: var(--radius-sm); font-size: var(--text-xs);">
                        <div class="flex justify-between items-center mb-1">
                          <strong style="color: var(--on-warning-bg);">${enm.profesionalNombre || 'Médico Autor'}</strong>
                          <span class="text-muted">${formatClinicalDate(enm.fechaEnmienda)}</span>
                        </div>
                        <p class="m-0 mb-1" style="color: var(--on-warning-bg);">${esc(enm.contenido)}</p>
                        ${enm.motivo ? `
                          <span class="block text-muted italic">Motivo de aclaración: ${esc(enm.motivo)}</span>
                        ` : ''}
                      </div>
                    `).join('')}
                  </div>
                </div>
              ` : ''}

            </div>
          </div>
        `;
      }).join('')}
    </div>
  `;
}
