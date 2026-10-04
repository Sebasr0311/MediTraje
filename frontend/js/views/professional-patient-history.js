/**
 * MediTriaje 2.0 — Vista Profesional de Historia Clínica del Paciente (professional-patient-history.js)
 * Consulta de historia clínica inmutable por profesionales asistenciales (HU-07, HU-09, ADR-007, ADR-017).
 * Integra verificación de relación asistencial activa y soporte de corte de emergencia Break-Glass.
 */

import { api } from '../api.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';
import { showBreakGlassModal } from './break-glass-modal.js';

function formatDateTimeBogota(isoString) {
  if (!isoString) return '—';
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    }).format(new Date(isoString));
  } catch {
    return isoString;
  }
}

const VITALS_LABELS = {
  presionSistolica: 'P. Sistólica (mmHg)',
  presionDiastolica: 'P. Diastólica (mmHg)',
  frecuenciaCardiaca: 'Frec. Cardíaca (lpm)',
  frecuenciaRespiratoria: 'Frec. Respiratoria (rpm)',
  temperatura: 'Temperatura (°C)',
  saturacionOxigeno: 'Sat. O₂ (%)',
  pesoKg: 'Peso (kg)',
  tallaCm: 'Talla (cm)'
};

/**
 * Vista de Consulta de Historia Clínica para Profesionales (#/professional/patient-history/:patientPublicId)
 */
export async function professionalPatientHistoryView(container, { params }) {
  const patientPublicId = params?.patientPublicId;

  if (!patientPublicId) {
    ui.renderError(container, {
      title: 'Identificador no especificado',
      message: 'No se suministró un identificador válido de paciente.',
      actionText: 'Volver a la agenda',
      onAction: () => router.navigate('/professional/agenda')
    });
    return;
  }

  container.innerHTML = `
    <div style="max-width: 56rem; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      <!-- Encabezado -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <div class="flex items-center gap-2 mb-1">
            <button type="button" id="btnBackAgenda" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver a la agenda" title="Volver a la agenda">
              ${ui.icon('arrow-left')}
            </button>
            <h1 class="text-2xl font-bold m-0">Historia Clínica del Paciente</h1>
          </div>
          <p class="text-sm text-muted m-0">Identificador paciente: <code class="text-xs">${esc(patientPublicId)}</code></p>
        </div>

        <div class="flex items-center gap-2">
          <button type="button" class="btn btn-secondary btn--sm" id="btnPrintHistory" title="Imprimir o guardar como PDF">
            ${ui.icon('printer')}
            <span>Imprimir</span>
          </button>
          <button type="button" class="btn btn-danger btn--sm" id="btnTriggerBreakGlass" title="Activar o renovar acceso de emergencia">
            ${ui.icon('alert-triangle')}
            <span>Acceso Emergencia</span>
          </button>
        </div>
      </div>

      <!-- Alerta de Break-Glass (si aplica) -->
      <div id="breakGlassBannerContainer"></div>

      <!-- Contenedor dinámico del historial -->
      <div id="patientHistoryContainer">
        <div class="skeleton skeleton-card" style="height: 180px; margin-bottom: var(--space-4);"></div>
        <div class="skeleton skeleton-card" style="height: 180px;"></div>
      </div>
    </div>
  `;

  container.querySelector('#btnBackAgenda')?.addEventListener('click', () => {
    if (window.history.length > 1) {
      window.history.back();
    } else {
      router.navigate('/professional/agenda');
    }
  });

  container.querySelector('#btnPrintHistory')?.addEventListener('click', () => {
    window.print();
  });

  container.querySelector('#btnTriggerBreakGlass')?.addEventListener('click', () => {
    showBreakGlassModal({
      pacientePublicId: patientPublicId,
      onActivated: () => loadHistory()
    });
  });

  async function checkActiveBreakGlass() {
    try {
      const activeList = await api.get('/clinical/break-glass/active');
      if (Array.isArray(activeList)) {
        return activeList.find(item => item.pacientePublicId === patientPublicId) || null;
      }
    } catch {
      // Ignorar error de consulta de activos
    }
    return null;
  }

  async function loadHistory() {
    const listContainer = container.querySelector('#patientHistoryContainer');
    const bannerContainer = container.querySelector('#breakGlassBannerContainer');
    ui.renderLoading(listContainer, 'Consultando historial clínico del paciente...');

    // 1. Verificar si hay Break-Glass activo para este paciente
    const activeBg = await checkActiveBreakGlass();
    if (activeBg) {
      bannerContainer.innerHTML = `
        <div class="alert alert--danger mb-6" role="alert" style="background-color: var(--danger-bg); border-left: 4px solid var(--danger); padding: var(--space-4);">
          <div class="flex items-start gap-3">
            <span class="text-danger" style="margin-top: 2px;">${ui.icon('alert-triangle', 'icon icon--md')}</span>
            <div class="w-full">
              <div class="flex flex-wrap justify-between items-center gap-2">
                <strong class="text-sm font-bold" style="color: var(--on-danger-bg);">ACCESO CLÍNICO DE EMERGENCIA ACTIVO (ADR-017)</strong>
                <span class="badge badge--cancelled text-xs font-semibold">Vigente hasta: ${formatDateTimeBogota(activeBg.fechaExpiracion)}</span>
              </div>
              <p class="text-xs m-0 mt-2" style="color: var(--on-danger-bg);">
                <strong>Motivo registrado:</strong> ${esc(activeBg.motivo)}
              </p>
              <span class="text-xs text-muted block mt-1">Este acceso excepcional se encuentra registrado inmutablemente en la bitácora de auditoría.</span>
            </div>
          </div>
        </div>
      `;
    } else {
      bannerContainer.innerHTML = '';
    }

    // 2. Consultar historial clínico
    try {
      const res = await api.get(`/clinical/patients/${patientPublicId}/history`, {
        page: 0,
        size: 50
      });

      const atenciones = res?.content || [];
      renderHistoryContent(listContainer, atenciones, activeBg);
    } catch (err) {
      // Si el error es 403 (falta de relación asistencial), ofrecer activación directa de Break-Glass
      if (err.status === 403 || String(err.message || '').includes('relacion asistencial')) {
        renderUnauthorizedPrompt(listContainer);
      } else {
        ui.renderError(listContainer, {
          title: 'No fue posible cargar el historial clínico',
          message: err.message,
          actionText: 'Reintentar',
          onAction: loadHistory
        });
      }
    }
  }

  function renderUnauthorizedPrompt(targetEl) {
    targetEl.innerHTML = `
      <div class="card p-8 text-center" style="border: 2px dashed var(--danger); background: var(--surface);">
        <div class="empty-state-icon" style="margin: 0 auto var(--space-4) auto; background-color: var(--danger-bg); color: var(--danger);">
          ${ui.icon('shield', 'icon icon--lg')}
        </div>
        <h2 class="text-xl font-bold mb-2 text-danger">Acceso Restringido — Sin Relación Asistencial Activa</h2>
        <p class="text-muted mb-6" style="max-width: 48ch; margin-left: auto; margin-right: auto;">
          No tienes una cita programada ni una atención previa con este paciente en los últimos 12 meses (ADR-007).
          <br><br>
          Si estás atendiendo una <strong>urgencia vital o emergencia médica</strong>, puedes activar el
          <strong>Acceso Clínico de Emergencia (Break-Glass, ADR-017)</strong> registrando la debida justificación médica.
        </p>
        <button type="button" class="btn btn-danger btn--lg" id="btnUnlockPrompt">
          ${ui.icon('alert-triangle')}
          <span>Activar Acceso de Emergencia (Break-Glass)</span>
        </button>
      </div>
    `;

    targetEl.querySelector('#btnUnlockPrompt')?.addEventListener('click', () => {
      showBreakGlassModal({
        pacientePublicId: patientPublicId,
        onActivated: () => loadHistory()
      });
    });
  }

  function renderHistoryContent(targetEl, atenciones, activeBg) {
    if (atenciones.length === 0) {
      ui.renderEmpty(targetEl, {
        icon: 'file-text',
        title: 'Sin atenciones previas registradas',
        description: 'El paciente no registra atenciones médicas cerradas en la plataforma.',
        actionText: 'Volver a la agenda',
        onAction: () => router.navigate('/professional/agenda')
      });
      return;
    }

    targetEl.innerHTML = `
      <div class="flex items-center justify-between mb-4">
        <span class="text-xs text-muted font-bold uppercase tracking-wider">
          ${atenciones.length} atención(es) registrada(s)
        </span>
        ${!activeBg ? `
          <span class="badge badge--confirmed text-xs">
            ${ui.icon('check', 'icon icon--sm')} Relación Asistencial Activa
          </span>
        ` : ''}
      </div>

      <div class="flex flex-col gap-6">
        ${atenciones.map(atn => renderAtencionCard(atn)).join('')}
      </div>
    `;
  }

  function renderAtencionCard(atn) {
    const s = atn.signosVitales;
    const enmiendas = atn.enmiendas || [];

    const vitalsItems = s ? Object.keys(VITALS_LABELS)
      .filter(k => s[k] != null)
      .map(k => `
        <div class="vital-card">
          <span class="vital-card-label">${VITALS_LABELS[k]}</span>
          <span class="vital-card-val">${esc(s[k])}</span>
        </div>
      `).join('') : '';

    return `
      <div class="card p-6" style="border-top: 4px solid var(--primary); box-shadow: var(--shadow-sm);">
        <!-- Encabezado de la atención -->
        <div class="flex flex-wrap items-center justify-between gap-2 mb-4 pb-3" style="border-bottom: 1px solid var(--border);">
          <div>
            <span class="text-xs text-muted block mb-1">Atención cerrada el ${formatDateTimeBogota(atn.fechaCierre)}</span>
            <div class="flex items-center gap-2">
              <strong class="text-md">${esc(atn.profesionalNombre || 'Profesional tratante')}</strong>
              <span class="badge badge--neutral text-xs">${esc(atn.especialidadNombre || 'Medicina')}</span>
            </div>
          </div>
          <span class="badge badge--attended">
            ${ui.icon('shield', 'icon icon--sm')} Inmutable
          </span>
        </div>

        <!-- Diagnóstico CIE-10 -->
        <div class="p-3 mb-4" style="background: var(--teal-50); border-left: 4px solid var(--primary); border-radius: var(--radius-md);">
          <span class="text-xs text-muted font-bold uppercase block">Diagnóstico CIE-10</span>
          <strong class="text-primary">${esc(atn.diagnosticoCodigo)} — ${esc(atn.diagnosticoDescripcion)}</strong>
        </div>

        <!-- Motivo y Evolución -->
        <div class="mb-4">
          <h3 class="text-xs font-bold uppercase text-muted mb-1">Motivo de consulta</h3>
          <p class="text-sm m-0 mb-3">${esc(atn.motivoConsulta)}</p>

          <h3 class="text-xs font-bold uppercase text-muted mb-1">Evolución clínica</h3>
          <p class="text-sm m-0" style="white-space: pre-wrap;">${esc(atn.evolucion)}</p>
        </div>

        <!-- Signos vitales -->
        ${vitalsItems ? `
          <div class="mb-4">
            <h3 class="text-xs font-bold uppercase text-muted mb-2">Signos vitales</h3>
            <div class="vitals-grid">
              ${vitalsItems}
            </div>
          </div>
        ` : ''}

        <!-- Indicaciones -->
        <div class="mb-4">
          <h3 class="text-xs font-bold uppercase text-muted mb-1">Indicaciones médicas</h3>
          <p class="text-sm m-0" style="white-space: pre-wrap;">${esc(atn.indicaciones)}</p>
        </div>

        <!-- Enmiendas inmutables -->
        ${enmiendas.length > 0 ? `
          <div class="mt-4 pt-3" style="border-top: 1px dashed var(--border);">
            <h3 class="text-xs font-bold uppercase text-muted mb-2">Enmiendas clínicas aclaratorias (${enmiendas.length})</h3>
            <div class="flex flex-col gap-2">
              ${enmiendas.map(enm => `
                <div class="p-3 card" style="background: var(--surface-2); border-left: 3px solid var(--warning);">
                  <div class="flex justify-between items-center text-xs text-muted mb-1">
                    <span>${formatDateTimeBogota(enm.createdAt)}</span>
                    <span class="font-semibold text-warning">Aclaración</span>
                  </div>
                  <strong class="text-xs block mb-1">Motivo: ${esc(enm.motivo)}</strong>
                  <p class="text-xs m-0" style="white-space: pre-wrap;">${esc(enm.contenido)}</p>
                </div>
              `).join('')}
            </div>
          </div>
        ` : ''}
      </div>
    `;
  }

  await loadHistory();
}
