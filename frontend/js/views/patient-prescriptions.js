/**
 * MediTriaje 2.0 — Vista de Recetas Médicas del Paciente (patient-prescriptions.js)
 * Listado de fórmulas médicas digitales, cálculo de vigencia, snapshots inmutables
 * de medicamentos y opción de impresión/guardado en PDF (M8.2c, HU-08, ADR-008).
 */

import { api } from '../api.js';
import { auth } from '../auth.js';
import { router } from '../router.js';
import { ui } from '../ui.js';

let prescriptionsState = {
  recetas: [],
  page: 0,
  size: 20,
  totalPages: 1,
  totalElements: 0
};

/**
 * Formatea una fecha ISO en formato colombiano
 */
function formatPrescriptionDate(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    }).format(d);
  } catch {
    return isoString;
  }
}

/**
 * Calcula el estado de vigencia de una receta
 * @param {string} createdAt Fecha de emisión
 * @param {number} vigenciaDias Días de vigencia
 * @returns {{ isVigente: boolean, fechaExpiracion: string, diasRestantes: number }}
 */
function checkPrescriptionValidity(createdAt, vigenciaDias = 30) {
  try {
    const createdDate = new Date(createdAt);
    const expireTime = createdDate.getTime() + (vigenciaDias * 24 * 60 * 60 * 1000);
    const now = Date.now();
    const diffMillis = expireTime - now;
    const diffDays = Math.ceil(diffMillis / (1000 * 60 * 60 * 24));

    const expDate = new Date(expireTime);
    const expFormatted = new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'short',
      year: 'numeric'
    }).format(expDate);

    return {
      isVigente: diffMillis > 0,
      fechaExpiracion: expFormatted,
      diasRestantes: diffDays
    };
  } catch {
    return { isVigente: false, fechaExpiracion: '—', diasRestantes: 0 };
  }
}

/**
 * Vista principal de Recetas Médicas del Paciente (#/patient/prescriptions)
 * @param {HTMLElement} container Contenedor DOM
 */
export async function patientPrescriptionsView(container) {
  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Encabezado con acción global de impresión -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <div class="flex items-center gap-2 mb-1">
            <a href="#/patient/dashboard" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver al panel" title="Volver al panel">
              ${ui.icon('arrow-left')}
            </a>
            <h1 class="text-2xl font-bold m-0">Mis Recetas Médicas</h1>
          </div>
          <p class="text-sm text-muted m-0">Prescripciones farmacológicas digitales emitidas con respaldo legal inmutable</p>
        </div>

        <button type="button" class="btn btn-secondary btn--sm" id="btnPrintPrescriptions" title="Imprimir fórmulas">
          ${ui.icon('file-text')}
          <span>Imprimir recetas</span>
        </button>
      </div>

      <!-- Contenedor dinámico de recetas -->
      <div id="prescriptionsListContainer">
        <div class="skeleton skeleton-card" style="height: 180px; margin-bottom: var(--space-4);"></div>
        <div class="skeleton skeleton-card" style="height: 180px;"></div>
      </div>
    </div>
  `;

  container.querySelector('#btnPrintPrescriptions')?.addEventListener('click', () => {
    window.print();
  });

  await loadPrescriptions(container);
}

/**
 * Consulta las recetas médicas desde la API
 */
async function loadPrescriptions(container) {
  const listContainer = container.querySelector('#prescriptionsListContainer');
  ui.renderLoading(listContainer, 'Cargando tus recetas médicas...');

  try {
    const res = await api.get('/patients/me/prescriptions', {
      page: prescriptionsState.page,
      size: prescriptionsState.size
    });

    prescriptionsState.recetas = res?.content || [];
    prescriptionsState.totalPages = res?.totalPages || 1;
    prescriptionsState.totalElements = res?.totalElements || 0;

    renderPrescriptionsList(container);
  } catch (err) {
    ui.renderError(listContainer, {
      title: 'No fue posible cargar tus recetas',
      message: err.message || 'Ocurrió un error al obtener las recetas médicas.',
      onRetry: () => loadPrescriptions(container)
    });
  }
}

/**
 * Renderiza la lista de recetas
 */
function renderPrescriptionsList(container) {
  const listContainer = container.querySelector('#prescriptionsListContainer');
  if (!listContainer) return;

  if (prescriptionsState.recetas.length === 0) {
    ui.renderEmpty(listContainer, {
      icon: 'pill',
      title: 'No tienes recetas médicas emitidas',
      description: 'Cuando un médico te formule medicamentos durante una consulta, tus prescripciones aparecerán aquí con sus indicaciones completas.',
      actionText: 'Ver mis citas',
      onAction: () => router.navigate('/patient/appointments')
    });
    return;
  }

  listContainer.innerHTML = `
    <div class="mb-4 flex items-center justify-between">
      <span class="text-xs text-muted font-bold uppercase tracking-wider">
        ${prescriptionsState.totalElements} receta(s) médica(s) registrada(s)
      </span>
      <span class="badge badge--confirmed text-xs">
        ${ui.icon('shield', 'icon icon--sm')} Fórmulas Farmacológicas Inmutables
      </span>
    </div>

    <div class="flex flex-col gap-6">
      ${prescriptionsState.recetas.map(receta => {
        const vigenciaInfo = checkPrescriptionValidity(receta.createdAt, receta.vigenciaDias);
        const detalles = receta.detalles || [];

        return `
          <div class="card p-6" id="receta-${receta.publicId}" style="border-top: 4px solid ${vigenciaInfo.isVigente ? 'var(--primary)' : 'var(--border)'};">
            
            <!-- Encabezado de la receta -->
            <div class="flex flex-wrap items-center justify-between gap-3 pb-3 border-b mb-4">
              <div>
                <div class="flex items-center gap-2 mb-1">
                  <span class="badge ${vigenciaInfo.isVigente ? 'badge--confirmed' : 'badge--neutral'} text-xs font-bold">
                    ${vigenciaInfo.isVigente ? 'Vigente' : 'Vencida'}
                  </span>
                  <span class="text-xs text-muted font-mono">Código: ${receta.publicId.slice(0, 8)}</span>
                </div>
                <h2 class="text-lg font-bold m-0">Receta Médica Digital</h2>
              </div>

              <div class="text-right">
                <span class="text-sm font-semibold block">${receta.profesionalNombre || 'Profesional Médico'}</span>
                <span class="badge badge--neutral text-xs">${receta.especialidadNombre || 'Medicina General'}</span>
              </div>
            </div>

            <!-- Metadatos de Fecha y Vigencia -->
            <div class="grid grid-cols-1 grid-cols-3-md gap-4 mb-5 p-3" style="background-color: var(--surface-2); border-radius: var(--radius-md); font-size: var(--text-xs);">
              <div>
                <strong class="text-muted block">Fecha de Emisión:</strong>
                <span class="font-medium text-text">${formatPrescriptionDate(receta.createdAt)}</span>
              </div>

              <div>
                <strong class="text-muted block">Vigencia Total:</strong>
                <span class="font-medium text-text">${receta.vigenciaDias} días</span>
              </div>

              <div>
                <strong class="text-muted block">Vencimiento:</strong>
                <span class="font-medium text-text">${vigenciaInfo.fechaExpiracion}</span>
                ${vigenciaInfo.isVigente ? `
                  <span class="text-success font-semibold ml-1">(${vigenciaInfo.diasRestantes} días restantes)</span>
                ` : ''}
              </div>
            </div>

            <!-- Tabla de Medicamentos Prescritos -->
            <div class="mb-4">
              <h3 class="text-xs font-bold uppercase tracking-wider text-muted mb-2">
                Medicamentos Prescritos (${detalles.length}):
              </h3>

              <div class="table-container">
                <table class="table">
                  <thead>
                    <tr>
                      <th>Medicamento</th>
                      <th>Concentración / Presentación</th>
                      <th>Dosis y Frecuencia</th>
                      <th>Duración / Cantidad</th>
                      <th>Indicaciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${detalles.map(item => `
                      <tr>
                        <td>
                          <strong class="block">${item.nombreComercial}</strong>
                          <span class="text-xs text-muted">Principio activo: ${item.principioActivo}</span>
                        </td>
                        <td>
                          <span class="badge badge--neutral">${item.concentracion}</span>
                          <span class="text-xs block text-muted mt-1">${item.presentacion}</span>
                        </td>
                        <td>
                          <strong>${item.dosis}</strong>
                          <span class="text-xs block text-muted">${item.frecuencia}</span>
                        </td>
                        <td>
                          <span class="font-medium">${item.duracionDias} días</span>
                          <span class="text-xs block text-muted">${item.cantidad} unidad(es)</span>
                        </td>
                        <td class="text-xs" style="max-width: 200px;">
                          ${item.indicaciones || 'Tomar según indicación médica.'}
                        </td>
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Pie de Receta -->
            <div class="flex flex-wrap items-center justify-between gap-3 pt-3 border-t">
              <span class="text-xs text-muted">
                Fórmula médica electrónica verificada. Válida para reclamación y dispensación.
              </span>

              <button type="button" class="btn btn-secondary btn--sm btn-print-single" data-receta-id="${receta.publicId}">
                ${ui.icon('file-text', 'icon icon--sm')}
                <span>Imprimir esta receta</span>
              </button>
            </div>

          </div>
        `;
      }).join('')}
    </div>
  `;

  // Asignar listeners de impresión individual
  listContainer.querySelectorAll('.btn-print-single').forEach(btn => {
    btn.addEventListener('click', () => {
      window.print();
    });
  });
}
