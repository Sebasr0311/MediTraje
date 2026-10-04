/**
 * MediTriaje 2.0 — Vista de Recetas Médicas del Paciente (patient-prescriptions.js)
 * Listado de fórmulas médicas digitales, código de reclamación alfanumérico,
 * cálculo de vigencia, saldos de dispensación y trazabilidad farmacéutica (M8.2c, HU-08, F2.4, ADR-016).
 */

import { api, patientApi } from '../api.js';
import { auth } from '../auth.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

let prescriptionsState = {
  recetas: [],
  page: 0,
  size: 20,
  totalPages: 1,
  totalElements: 0,
  dispensationDetails: new Map() // recetaPublicId -> RecetaDispensacionResponse
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
 * Formatea una fecha y hora ISO en formato colombiano
 */
function formatDateTime(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
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
 * Retorna el badge visual para el estado de dispensación
 */
function renderDispensationBadge(estado) {
  switch (estado) {
    case 'DISPENSADA_TOTAL':
      return `<span class="badge badge--confirmed font-semibold">${ui.icon('check', 'icon icon--sm')} Totalmente Entregada</span>`;
    case 'DISPENSADA_PARCIAL':
      return `<span class="badge badge--warning font-semibold">${ui.icon('clock', 'icon icon--sm')} Entrega Parcial</span>`;
    case 'PENDIENTE':
    default:
      return `<span class="badge badge--scheduled font-semibold">${ui.icon('pill', 'icon icon--sm')} Pendiente de Entrega</span>`;
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
          <p class="text-sm text-muted m-0">Prescripciones farmacológicas digitales con código de reclamación y seguimiento de entregas</p>
        </div>

        <button type="button" class="btn btn-secondary btn--sm" id="btnPrintPrescriptions" title="Imprimir fórmulas">
          ${ui.icon('printer')}
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
      description: 'Cuando un médico te formule medicamentos durante una consulta, tus prescripciones aparecerán aquí con su código de reclamación en farmacia.',
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
        const codigoReclamacion = "REC-" + receta.publicId.replace(/-/g, "").substring(0, 8).toUpperCase();

        return `
          <div class="card p-6" id="receta-${receta.publicId}" style="border-top: 4px solid ${vigenciaInfo.isVigente ? 'var(--primary)' : 'var(--border)'};">
            
            <!-- Encabezado de la receta -->
            <div class="flex flex-wrap items-center justify-between gap-3 pb-3 border-b mb-4">
              <div>
                <div class="flex items-center gap-2 mb-1">
                  <span class="badge ${vigenciaInfo.isVigente ? 'badge--confirmed' : 'badge--neutral'} text-xs font-bold">
                    ${vigenciaInfo.isVigente ? 'Vigente' : 'Vencida'}
                  </span>
                  <span class="text-xs text-muted font-mono">UUID: ${receta.publicId.slice(0, 8)}</span>
                </div>
                <h2 class="text-lg font-bold m-0">Receta Médica Digital</h2>
              </div>

              <div class="text-right">
                <span class="text-sm font-semibold block">${receta.profesionalNombre || 'Profesional Médico'}</span>
                <span class="badge badge--neutral text-xs">${receta.especialidadNombre || 'Medicina General'}</span>
              </div>
            </div>

            <!-- Código de Reclamación Farmacéutica (ADR-016) -->
            <div class="card p-4 mb-5" style="background-color: var(--teal-50); border: 2px dashed var(--primary); border-radius: var(--radius-md);">
              <div class="flex flex-wrap items-center justify-between gap-3">
                <div class="flex items-center gap-3">
                  <div class="empty-state-icon" style="margin: 0; width: 44px; height: 44px; background-color: var(--primary); color: #fff;">
                    ${ui.icon('pill', 'icon icon--md')}
                  </div>
                  <div>
                    <span class="text-xs font-bold uppercase tracking-wider text-muted block">Código para Reclamar en Farmacia</span>
                    <strong class="font-mono text-2xl text-primary font-bold tracking-wider">${codigoReclamacion}</strong>
                  </div>
                </div>

                <div class="flex items-center gap-2">
                  <button type="button" class="btn btn-secondary btn--sm btn-copy-claim" data-claim="${codigoReclamacion}" title="Copiar código para ventanilla">
                    ${ui.icon('copy', 'icon icon--sm')}
                    <span>Copiar código</span>
                  </button>
                  <button type="button" class="btn btn-primary btn--sm btn-view-dispensation" data-receta-id="${receta.publicId}">
                    ${ui.icon('activity', 'icon icon--sm')}
                    <span>Estado de Entrega</span>
                  </button>
                </div>
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
                ` : '<span class="text-danger font-semibold ml-1">(Expirada)</span>'}
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
                          <strong>${esc(item.dosis)}</strong>
                          <span class="text-xs block text-muted">${esc(item.frecuencia)}</span>
                        </td>
                        <td>
                          <span class="font-medium">${item.duracionDias} días</span>
                          <span class="text-xs block text-muted">${item.cantidad} unidad(es)</span>
                        </td>
                        <td class="text-xs" style="max-width: 200px;">
                          ${esc(item.indicaciones) || 'Tomar según indicación médica.'}
                        </td>
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Panel Expandible de Estado de Dispensación Farmacéutica (ADR-016) -->
            <div id="dispensation-box-${receta.publicId}" class="mt-4 pt-4 border-t" style="display: none;">
              <div class="skeleton skeleton-card" style="height: 100px;"></div>
            </div>

            <!-- Pie de Receta -->
            <div class="flex flex-wrap items-center justify-between gap-3 pt-3 border-t">
              <span class="text-xs text-muted">
                Fórmula médica electrónica verificada. Válida para reclamación y dispensación farmacéutica.
              </span>

              <button type="button" class="btn btn-secondary btn--sm btn-print-single" data-receta-id="${receta.publicId}">
                ${ui.icon('printer', 'icon icon--sm')}
                <span>Imprimir esta receta</span>
              </button>
            </div>

          </div>
        `;
      }).join('')}
    </div>
  `;

  // Listener para botones de copiado de código de reclamación
  listContainer.querySelectorAll('.btn-copy-claim').forEach(btn => {
    btn.addEventListener('click', async () => {
      const claimCode = btn.dataset.claim;
      try {
        await navigator.clipboard.writeText(claimCode);
        ui.showToast(`Código ${claimCode} copiado al portapapeles.`, 'success');
      } catch {
        // Fallback accesible
        const temp = document.createElement('input');
        temp.value = claimCode;
        document.body.appendChild(temp);
        temp.select();
        document.execCommand('copy');
        temp.remove();
        ui.showToast(`Código ${claimCode} copiado al portapapeles.`, 'success');
      }
    });
  });

  // Listener para consultar / expandir estado de dispensación
  listContainer.querySelectorAll('.btn-view-dispensation').forEach(btn => {
    btn.addEventListener('click', async () => {
      const recetaId = btn.dataset.recetaId;
      const box = listContainer.querySelector(`#dispensation-box-${recetaId}`);
      if (!box) return;

      if (box.style.display === 'block') {
        box.style.display = 'none';
        btn.classList.remove('btn-secondary');
        btn.classList.add('btn-primary');
        return;
      }

      box.style.display = 'block';
      btn.classList.remove('btn-primary');
      btn.classList.add('btn-secondary');

      await renderDispensationDetails(box, recetaId);
    });
  });

  // Asignar listeners de impresión individual
  listContainer.querySelectorAll('.btn-print-single').forEach(btn => {
    btn.addEventListener('click', () => {
      window.print();
    });
  });
}

/**
 * Consulta y renderiza el desglose de saldos y entregas previas de una receta
 */
async function renderDispensationDetails(boxElement, recetaPublicId) {
  ui.renderLoading(boxElement, 'Consultando estado de entregas en farmacia...');

  try {
    const dispData = await patientApi.consultarDispensacionReceta(recetaPublicId);
    prescriptionsState.dispensationDetails.set(recetaPublicId, dispData);

    const items = dispData.items || [];
    const entregas = dispData.entregasPrevias || [];

    boxElement.innerHTML = `
      <div class="card p-4" style="background-color: var(--surface-1); border-left: 4px solid var(--primary);">
        <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
          <div class="flex items-center gap-2">
            <h3 class="text-sm font-bold uppercase tracking-wider m-0">Trazabilidad de Farmacia</h3>
            ${renderDispensationBadge(dispData.estadoDispensacion)}
          </div>
          <span class="text-xs text-muted">
            Código: <strong class="font-mono text-primary">${dispData.codigoReclamacion}</strong>
          </span>
        </div>

        <!-- Tabla de saldos por medicamento -->
        <h4 class="text-xs font-bold text-muted uppercase tracking-wider mb-2">Saldos de Medicamentos:</h4>
        <div class="table-container mb-4">
          <table class="table text-xs">
            <thead>
              <tr>
                <th>Medicamento</th>
                <th class="text-center">Prescrito</th>
                <th class="text-center">Entregado</th>
                <th class="text-center">Saldo Pendiente</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              ${items.map(item => `
                <tr>
                  <td>
                    <strong>${esc(item.nombreComercial)}</strong>
                    <span class="text-muted block">${esc(item.presentacion)} — ${esc(item.concentracion)}</span>
                  </td>
                  <td class="text-center font-medium">${item.cantidadPrescrita}</td>
                  <td class="text-center font-medium text-success">${item.cantidadDispensada}</td>
                  <td class="text-center font-bold ${item.saldoPendiente > 0 ? 'text-primary' : 'text-muted'}">
                    ${item.saldoPendiente}
                  </td>
                  <td>${renderDispensationBadge(item.estado)}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>

        <!-- Historial de entregas realizadas -->
        <h4 class="text-xs font-bold text-muted uppercase tracking-wider mb-2">Entregas Realizadas (${entregas.length}):</h4>
        ${entregas.length === 0 ? `
          <p class="text-xs text-muted m-0 p-3" style="background-color: var(--surface-2); border-radius: var(--radius-sm);">
            No se han registrado entregas todavía para esta receta. Preséntate en ventanilla con tu código de reclamación.
          </p>
        ` : `
          <div class="flex flex-col gap-3">
            ${entregas.map(ent => `
              <div class="p-3" style="background-color: var(--surface-2); border-radius: var(--radius-sm); border: 1px solid var(--border);">
                <div class="flex flex-wrap items-center justify-between gap-2 text-xs mb-2">
                  <span><strong>Fecha:</strong> ${formatDateTime(ent.createdAt)}</span>
                  <span><strong>Sede:</strong> ${esc(ent.sedeNombre)}</span>
                  <span class="text-muted"><strong>Dispensador:</strong> ${esc(ent.dispensadorNombre)}</span>
                </div>
                ${ent.observaciones ? `
                  <p class="text-xs text-muted mb-2"><strong>Observaciones:</strong> ${esc(ent.observaciones)}</p>
                ` : ''}
                <div class="table-container">
                  <table class="table text-xs" style="margin: 0;">
                    <thead>
                      <tr>
                        <th>Medicamento</th>
                        <th>Lote INVIMA</th>
                        <th>Vencimiento Lote</th>
                        <th class="text-center">Cantidad Entregada</th>
                      </tr>
                    </thead>
                    <tbody>
                      ${(ent.detalles || []).map(d => `
                        <tr>
                          <td><strong>${esc(d.nombreComercial)}</strong> (${esc(d.principioActivo)})</td>
                          <td><span class="badge badge--neutral font-mono text-xs">${esc(d.lote) || 'N/A'}</span></td>
                          <td>${d.fechaVencimientoLote || 'N/A'}</td>
                          <td class="text-center font-bold text-success">${d.cantidadEntregada}</td>
                        </tr>
                      `).join('')}
                    </tbody>
                  </table>
                </div>
              </div>
            `).join('')}
          </div>
        `}
      </div>
    `;
  } catch (err) {
    ui.renderError(boxElement, {
      title: 'No fue posible obtener el estado de dispensación',
      message: err.message || 'Error al consultar datos de farmacia.',
      onRetry: () => renderDispensationDetails(boxElement, recetaPublicId)
    });
  }
}

