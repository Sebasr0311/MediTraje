/**
 * MediTriaje 2.0 — Centro de Mando Analítico Hospitalario y Alertas (operational-dashboard.js)
 * KPIs de capacidad, Resolución 5596/2015 MinSalud Colombia, reconocimiento auditado de alertas y QR (O01, O02, O03).
 */

import { operationalApi, emergencyApi } from '../api.js';
import { ui, esc } from '../ui.js';

let refreshIntervalId = null;

/**
 * Formatea fechas/horas al huso horario oficial de Colombia (America/Bogota).
 */
function formatTime(isoString) {
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

export async function renderOperationalDashboard(container) {
  // Limpiar cualquier intervalo de auto-refresco previo si existiese
  if (refreshIntervalId) {
    clearInterval(refreshIntervalId);
    refreshIntervalId = null;
  }

  container.innerHTML = `
    <!-- Cabecera del Centro de Mando -->
    <header class="operational-header">
      <div>
        <nav class="breadcrumb" aria-label="Ruta de navegación">
          <span>Hospital</span>
          <span class="breadcrumb-separator" aria-hidden="true">&gt;</span>
          <span>Analítica Operativa</span>
          <span class="breadcrumb-separator" aria-hidden="true">&gt;</span>
          <span class="breadcrumb-current" aria-current="page">Centro de Mando</span>
        </nav>
        <div class="flex items-center gap-3" style="margin-top: var(--space-1);">
          <h1 class="view-title" style="margin: 0;">Centro de Mando y Analítica Hospitalaria</h1>
          <span class="operational-live-badge" title="Monitoreo activo en tiempo real">
            <span class="operational-live-dot" aria-hidden="true"></span>
            <span>En vivo</span>
          </span>
        </div>
        <p class="view-subtitle" style="margin-top: var(--space-1);">
          Supervisión integral de ocupación de camas, tiempos de espera según Resolución 5596/2015 y gestión de alertas en Valledupar, Cesar.
        </p>
      </div>

      <!-- Controles de Sede y Refresco -->
      <div class="operational-header-actions">
        <div>
          <label for="select-sede-op" class="font-medium text-xs text-muted" style="display: block; margin-bottom: 4px;">
            Sede Hospitalaria:
          </label>
          <select id="select-sede-op" class="form-control" style="min-width: 230px;" aria-label="Seleccionar sede hospitalaria">
            <option value="">Cargando sedes...</option>
          </select>
        </div>

        <div style="display: flex; gap: var(--space-2); align-self: flex-end;">
          <button type="button" 
                  id="btnToggleAutoRefresh" 
                  class="btn btn-secondary btn--sm" 
                  title="Alternar actualización automática cada 30 segundos"
                  aria-pressed="true">
            ${ui.icon('clock', 'icon icon--sm')}
            <span id="txtAutoRefreshStatus">Auto: ON (30s)</span>
          </button>

          <button type="button" 
                  id="btnManualRefresh" 
                  class="btn btn-primary btn--sm" 
                  title="Actualizar métricas ahora">
            ${ui.icon('refresh', 'icon icon--sm')}
            <span>Actualizar</span>
          </button>
        </div>
      </div>
    </header>

    <!-- Indicador de Alerta Crítica Global de Saturación -->
    <section id="banner-alerta-critica" 
             class="operational-banner-alert" 
             role="alert" 
             aria-live="assertive" 
             style="display: none;">
      <div class="operational-banner-content">
        <span aria-hidden="true" style="font-size: 1.5rem;">⚠️</span>
        <div>
          <strong style="font-weight: var(--weight-bold); font-size: var(--text-sm);">ALERTA DE SATURACIÓN OPERATIVA EN CURSO:</strong>
          <div id="texto-alerta-critica" style="font-size: var(--text-sm); margin-top: 2px;">
            Existen demoras asistenciales o saturación de camas activas en esta sede.
          </div>
        </div>
      </div>
      <a href="#seccion-alertas-operativas" class="btn btn-danger btn--sm" style="white-space: nowrap;">
        Gestionar Alertas
      </a>
    </section>

    <!-- Tarjetas de KPIs Principales -->
    <section aria-label="Métricas e indicadores hospitalarios en tiempo real" class="operational-kpi-grid">
      <!-- KPI 1: Ocupación de Camas -->
      <article class="operational-kpi-card accent-primary">
        <div>
          <div class="operational-kpi-top">
            <span class="operational-kpi-title">Ocupación Hospitalaria</span>
            <span class="operational-kpi-icon" aria-hidden="true">${ui.icon('layers')}</span>
          </div>
          <div id="kpi-tasa-ocupacion" class="operational-kpi-value">0%</div>
          <div class="operational-progress" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">
            <div id="kpi-progress-bar" class="operational-progress-bar is-normal" style="width: 0%;"></div>
          </div>
          <div id="kpi-desglose-camas" class="operational-kpi-subtitle">0 de 0 camas ocupadas</div>
        </div>

        <div class="operational-pill-group" id="kpi-pills-camas" aria-label="Desglose por estado de camas">
          <span class="operational-pill" title="Disponibles">🟢 Libres: <strong id="kpi-camas-libres-val">0</strong></span>
          <span class="operational-pill" title="En proceso de desinfección">🟡 Limpieza: <strong id="kpi-camas-limpieza-val">0</strong></span>
          <span class="operational-pill" title="En mantenimiento">🔧 Mant.: <strong id="kpi-camas-mant-val">0</strong></span>
        </div>
      </article>

      <!-- KPI 2: Pacientes en Urgencias -->
      <article class="operational-kpi-card accent-warning">
        <div>
          <div class="operational-kpi-top">
            <span class="operational-kpi-title">Pacientes en Urgencias</span>
            <span class="operational-kpi-icon" aria-hidden="true">${ui.icon('users')}</span>
          </div>
          <div id="kpi-pacientes-urgencias" class="operational-kpi-value">0</div>
          <div class="operational-kpi-subtitle">En cola activa de admisión y triaje</div>
        </div>

        <div class="operational-pill-group" id="kpi-pills-triaje" aria-label="Pacientes por clasificación de triaje">
          <span class="operational-pill" style="border-left: 3px solid var(--triage-1-bd);">T1: <strong id="kpi-cnt-t1">0</strong></span>
          <span class="operational-pill" style="border-left: 3px solid var(--triage-2-bd);">T2: <strong id="kpi-cnt-t2">0</strong></span>
          <span class="operational-pill" style="border-left: 3px solid var(--triage-3-bd);">T3: <strong id="kpi-cnt-t3">0</strong></span>
          <span class="operational-pill" style="border-left: 3px solid var(--triage-4-bd);">T4: <strong id="kpi-cnt-t4">0</strong></span>
          <span class="operational-pill" style="border-left: 3px solid var(--triage-5-bd);">T5: <strong id="kpi-cnt-t5">0</strong></span>
        </div>
      </article>

      <!-- KPI 3: Alertas Activas -->
      <article class="operational-kpi-card accent-danger">
        <div>
          <div class="operational-kpi-top">
            <span class="operational-kpi-title">Alertas de Saturación</span>
            <span class="operational-kpi-icon" aria-hidden="true">${ui.icon('alert-triangle')}</span>
          </div>
          <div id="kpi-alertas-activas" class="operational-kpi-value">0</div>
          <div class="operational-kpi-subtitle">Umbrales clínicos y demoras de triaje</div>
        </div>

        <div class="operational-pill-group">
          <span id="kpi-alerta-status-pill" class="badge badge--confirmed">
            ${ui.icon('check', 'icon icon--sm')} Operación Normal
          </span>
        </div>
      </article>

      <!-- KPI 4: Camas Libres Disponibles -->
      <article class="operational-kpi-card accent-success">
        <div>
          <div class="operational-kpi-top">
            <span class="operational-kpi-title">Camas Disponibles</span>
            <span class="operational-kpi-icon" aria-hidden="true">${ui.icon('shield-check')}</span>
          </div>
          <div id="kpi-camas-disponibles" class="operational-kpi-value">0</div>
          <div class="operational-kpi-subtitle">Listas para asignación inmediata</div>
        </div>

        <div class="operational-pill-group">
          <span class="operational-pill" id="kpi-total-camas-pill">Capacidad: 0 instaladas</span>
        </div>
      </article>
    </section>

    <!-- Tiempos de Espera por Nivel de Triaje (Resolución 5596/2015 del MinSalud Colombia) -->
    <section class="card" style="margin-bottom: var(--space-6);" aria-labelledby="title-tiempos-triaje">
      <div class="flex items-center justify-between" style="margin-bottom: var(--space-3); flex-wrap: wrap; gap: var(--space-2);">
        <div>
          <h2 id="title-tiempos-triaje" style="font-size: var(--text-lg); font-weight: var(--weight-bold); margin: 0;">
            Tiempos Promedio de Espera por Nivel de Triaje
          </h2>
          <p class="text-xs text-muted" style="margin: 2px 0 0 0;">
            Estándares de oportunidad nacional obligatorios conforme a la Resolución 5596 de 2015 del Ministerio de Salud.
          </p>
        </div>
        <span class="badge badge--scheduled">Norma Colombia 2015</span>
      </div>

      <div class="operational-triage-grid">
        <!-- Triaje I -->
        <article class="operational-triage-card triage-1">
          <div>
            <div class="operational-triage-header">
              <span class="operational-triage-name">Nivel I — Resucitación</span>
              <span class="operational-triage-badge" style="background: var(--triage-1-bd); color: #FFFFFF;">I</span>
            </div>
            <div id="tiempo-t1" class="operational-triage-time">0 min</div>
            <div class="operational-triage-meta">Estándar: Inmediato (0 min)</div>
          </div>
          <div class="text-xs" style="margin-top: var(--space-2); opacity: 0.9;">
            En espera: <strong id="cnt-wait-t1">0 pacientes</strong>
          </div>
        </article>

        <!-- Triaje II -->
        <article class="operational-triage-card triage-2">
          <div>
            <div class="operational-triage-header">
              <span class="operational-triage-name">Nivel II — Emergencia</span>
              <span class="operational-triage-badge" style="background: var(--triage-2-bd); color: #FFFFFF;">II</span>
            </div>
            <div id="tiempo-t2" class="operational-triage-time">0 min</div>
            <div class="operational-triage-meta">Estándar: Máx. 30 minutos</div>
          </div>
          <div id="warn-t2" class="operational-triage-warning" style="display: none;">
            ⚠️ Excede norma (+0 min)
          </div>
          <div class="text-xs" style="margin-top: var(--space-2); opacity: 0.9;">
            En espera: <strong id="cnt-wait-t2">0 pacientes</strong>
          </div>
        </article>

        <!-- Triaje III -->
        <article class="operational-triage-card triage-3">
          <div>
            <div class="operational-triage-header">
              <span class="operational-triage-name">Nivel III — Urgencia</span>
              <span class="operational-triage-badge" style="background: var(--triage-3-bd); color: #FFFFFF;">III</span>
            </div>
            <div id="tiempo-t3" class="operational-triage-time">0 min</div>
            <div class="operational-triage-meta">Estándar: Máx. 45 minutos</div>
          </div>
          <div id="warn-t3" class="operational-triage-warning" style="display: none;">
            ⚠️ Excede norma (+0 min)
          </div>
          <div class="text-xs" style="margin-top: var(--space-2); opacity: 0.9;">
            En espera: <strong id="cnt-wait-t3">0 pacientes</strong>
          </div>
        </article>

        <!-- Triaje IV -->
        <article class="operational-triage-card triage-4">
          <div>
            <div class="operational-triage-header">
              <span class="operational-triage-name">Nivel IV — Prioritario</span>
              <span class="operational-triage-badge" style="background: var(--triage-4-bd); color: #FFFFFF;">IV</span>
            </div>
            <div id="tiempo-t4" class="operational-triage-time">0 min</div>
            <div class="operational-triage-meta">Estándar: Consulta prioritaria</div>
          </div>
          <div class="text-xs" style="margin-top: var(--space-2); opacity: 0.9;">
            En espera: <strong id="cnt-wait-t4">0 pacientes</strong>
          </div>
        </article>

        <!-- Triaje V -->
        <article class="operational-triage-card triage-5">
          <div>
            <div class="operational-triage-header">
              <span class="operational-triage-name">Nivel V — No Urgente</span>
              <span class="operational-triage-badge" style="background: var(--triage-5-bd); color: #FFFFFF;">V</span>
            </div>
            <div id="tiempo-t5" class="operational-triage-time">0 min</div>
            <div class="operational-triage-meta">Estándar: Atención ambulatoria</div>
          </div>
          <div class="text-xs" style="margin-top: var(--space-2); opacity: 0.9;">
            En espera: <strong id="cnt-wait-t5">0 pacientes</strong>
          </div>
        </article>
      </div>
    </section>

    <!-- Gestión y Reconocimiento de Alertas de Saturación (O02) -->
    <section id="seccion-alertas-operativas" class="card" style="margin-bottom: var(--space-6);" aria-labelledby="title-alertas-ops">
      <div class="flex items-center justify-between" style="margin-bottom: var(--space-3); flex-wrap: wrap; gap: var(--space-2);">
        <div>
          <h2 id="title-alertas-ops" style="font-size: var(--text-lg); font-weight: var(--weight-bold); margin: 0;">
            Alertas Operativas y Saturación Hospitalaria
          </h2>
          <p class="text-xs text-muted" style="margin: 2px 0 0 0;">
            Eventos asistenciales que sobrepasan la capacidad instalada o tiempos máximos de triaje.
          </p>
        </div>
        <span id="badge-total-alertas" class="badge badge--confirmed">0 alertas activas</span>
      </div>

      <div class="table-container">
        <table class="table" style="width: 100%; text-align: left;">
          <thead>
            <tr>
              <th scope="col">Hora</th>
              <th scope="col">Severidad</th>
              <th scope="col">Tipo de Alerta</th>
              <th scope="col">Diagnóstico Operativo / Mensaje</th>
              <th scope="col">Estado</th>
              <th scope="col" style="text-align: right;">Acción</th>
            </tr>
          </thead>
          <tbody id="tbody-alertas">
            <tr>
              <td colspan="6" style="text-align: center; color: var(--text-muted); padding: var(--space-6);">
                Cargando estado de alertas...
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- Módulo de Seguimiento y Manilla QR Intrahospitalaria (O03) -->
    <section class="card" aria-labelledby="title-qr-tracking">
      <div class="flex items-center gap-2" style="margin-bottom: var(--space-2);">
        <span class="text-primary" aria-hidden="true">${ui.icon('qr-code')}</span>
        <h2 id="title-qr-tracking" style="font-size: var(--text-lg); font-weight: var(--weight-bold); margin: 0;">
          Seguimiento Intrahospitalario por Código QR Seguro
        </h2>
      </div>
      <p class="text-xs text-muted" style="margin-bottom: var(--space-4); max-width: 650px;">
        Consulte la localización segura de un paciente mediante el token de su manilla o cabecera clínica. 
        Garantiza estricta confidencialidad sin exponer diagnósticos ni datos sensibles (cumplimiento Ley 1581 / Res. 1995 de 1999).
      </p>

      <form id="form-scan-qr" style="display: flex; gap: var(--space-2); max-width: 520px; flex-wrap: wrap;">
        <input type="text" 
               id="input-token-qr" 
               class="form-control" 
               placeholder="Ej: QROP-7A1B2C3D4E" 
               aria-label="Token de manilla o cabecera hospitalaria" 
               required 
               style="flex: 1; min-width: 200px;" />
        <button type="submit" class="btn btn-primary" id="btnConsultarQr">
          ${ui.icon('search', 'icon icon--sm')}
          <span>Localizar Paciente</span>
        </button>
      </form>

      <!-- Tarjeta de Resultados del Token QR -->
      <div id="resultado-qr-track" class="operational-qr-card" style="display: none;" aria-live="polite">
        <div class="operational-qr-header">
          <div class="flex items-center gap-2">
            <span class="text-primary" aria-hidden="true">${ui.icon('user')}</span>
            <strong id="qr-id-prov" style="font-size: var(--text-md);">-</strong>
          </div>
          <span id="qr-badge-estado" class="badge badge--confirmed">ACTIVO</span>
        </div>

        <div class="operational-qr-grid">
          <div>
            <div class="text-xs text-muted">Ubicación Asistencial:</div>
            <strong id="qr-ubicacion" style="color: var(--text);">-</strong>
          </div>
          <div>
            <div class="text-xs text-muted">Nivel de Triaje:</div>
            <span id="qr-nivel-triaje" class="badge">-</span>
          </div>
          <div>
            <div class="text-xs text-muted">Sede:</div>
            <span id="qr-sede-val" style="font-size: var(--text-xs);">-</span>
          </div>
          <div>
            <div class="text-xs text-muted">Token Auditado:</div>
            <div class="flex items-center gap-1" style="margin-top: 2px;">
              <code id="qr-token-val" class="font-mono text-xs" style="background: var(--surface); padding: 2px 4px; border-radius: var(--radius-sm); border: 1px solid var(--border);">-</code>
              <button type="button" id="btnCopiarTokenQr" class="btn btn-ghost btn--sm btn--icon-only" title="Copiar token" aria-label="Copiar token">
                ${ui.icon('copy', 'icon icon--sm')}
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  `;

  // Referencias al DOM
  const selectSede = container.querySelector('#select-sede-op');
  const bannerAlerta = container.querySelector('#banner-alerta-critica');
  const textoAlerta = container.querySelector('#texto-alerta-critica');
  const btnToggleAutoRefresh = container.querySelector('#btnToggleAutoRefresh');
  const txtAutoRefreshStatus = container.querySelector('#txtAutoRefreshStatus');
  const btnManualRefresh = container.querySelector('#btnManualRefresh');

  const kpiOcupacion = container.querySelector('#kpi-tasa-ocupacion');
  const kpiProgressBar = container.querySelector('#kpi-progress-bar');
  const kpiDesglose = container.querySelector('#kpi-desglose-camas');
  const kpiLibresVal = container.querySelector('#kpi-camas-libres-val');
  const kpiLimpiezaVal = container.querySelector('#kpi-camas-limpieza-val');
  const kpiMantVal = container.querySelector('#kpi-camas-mant-val');

  const kpiUrgencias = container.querySelector('#kpi-pacientes-urgencias');
  const kpiCntT1 = container.querySelector('#kpi-cnt-t1');
  const kpiCntT2 = container.querySelector('#kpi-cnt-t2');
  const kpiCntT3 = container.querySelector('#kpi-cnt-t3');
  const kpiCntT4 = container.querySelector('#kpi-cnt-t4');
  const kpiCntT5 = container.querySelector('#kpi-cnt-t5');

  const kpiAlertas = container.querySelector('#kpi-alertas-activas');
  const kpiAlertaStatusPill = container.querySelector('#kpi-alerta-status-pill');
  const badgeTotalAlertas = container.querySelector('#badge-total-alertas');

  const kpiDisponibles = container.querySelector('#kpi-camas-disponibles');
  const kpiTotalCamasPill = container.querySelector('#kpi-total-camas-pill');

  const t1 = container.querySelector('#tiempo-t1');
  const t2 = container.querySelector('#tiempo-t2');
  const t3 = container.querySelector('#tiempo-t3');
  const t4 = container.querySelector('#tiempo-t4');
  const t5 = container.querySelector('#tiempo-t5');

  const cntWaitT1 = container.querySelector('#cnt-wait-t1');
  const cntWaitT2 = container.querySelector('#cnt-wait-t2');
  const cntWaitT3 = container.querySelector('#cnt-wait-t3');
  const cntWaitT4 = container.querySelector('#cnt-wait-t4');
  const cntWaitT5 = container.querySelector('#cnt-wait-t5');

  const warnT2 = container.querySelector('#warn-t2');
  const warnT3 = container.querySelector('#warn-t3');

  const tbodyAlertas = container.querySelector('#tbody-alertas');

  let isAutoRefreshActive = true;

  // Cargar sedes activas
  try {
    const rawSedes = await emergencyApi.listarSedes();
    const sedes = Array.isArray(rawSedes) ? rawSedes : (rawSedes?.content || []);
    selectSede.innerHTML = '';
    if (sedes && sedes.length > 0) {
      sedes.forEach(s => {
        const opt = document.createElement('option');
        opt.value = s.publicId;
        opt.textContent = s.nombre;
        selectSede.appendChild(opt);
      });
      await cargarDatosDashboard(selectSede.value);
    } else {
      selectSede.innerHTML = '<option value="">Hospital Rosario Pumarejo de López</option>';
      await cargarDatosDashboard(null);
    }
  } catch (err) {
    selectSede.innerHTML = '<option value="">Hospital Rosario Pumarejo de López</option>';
    await cargarDatosDashboard(null);
  }

  // Cambio de sede
  selectSede.addEventListener('change', () => {
    cargarDatosDashboard(selectSede.value);
  });

  // Botón manual de refresco
  btnManualRefresh.addEventListener('click', async () => {
    btnManualRefresh.disabled = true;
    btnManualRefresh.innerHTML = `${ui.icon('refresh', 'icon icon--sm icon--spin')} <span>Actualizando...</span>`;
    await cargarDatosDashboard(selectSede.value);
    btnManualRefresh.disabled = false;
    btnManualRefresh.innerHTML = `${ui.icon('refresh', 'icon icon--sm')} <span>Actualizar</span>`;
    ui.showToast('Métricas operativas actualizadas.', 'info', 2000);
  });

  // Alternar auto-refresco
  btnToggleAutoRefresh.addEventListener('click', () => {
    isAutoRefreshActive = !isAutoRefreshActive;
    btnToggleAutoRefresh.setAttribute('aria-pressed', isAutoRefreshActive ? 'true' : 'false');
    if (isAutoRefreshActive) {
      btnToggleAutoRefresh.classList.remove('btn-ghost');
      btnToggleAutoRefresh.classList.add('btn-secondary');
      txtAutoRefreshStatus.textContent = 'Auto: ON (30s)';
      iniciarAutoRefresh();
      ui.showToast('Auto-refresco activado (cada 30s).', 'info', 2000);
    } else {
      btnToggleAutoRefresh.classList.remove('btn-secondary');
      btnToggleAutoRefresh.classList.add('btn-ghost');
      txtAutoRefreshStatus.textContent = 'Auto: OFF';
      detenerAutoRefresh();
      ui.showToast('Auto-refresco pausado.', 'info', 2000);
    }
  });

  function iniciarAutoRefresh() {
    detenerAutoRefresh();
    refreshIntervalId = setInterval(() => {
      // Solo refrescar si la vista actual sigue siendo el dashboard operativo
      if (window.location.hash.startsWith('#/operational/dashboard')) {
        cargarDatosDashboard(selectSede.value, true);
      } else {
        detenerAutoRefresh();
      }
    }, 30000);
  }

  function detenerAutoRefresh() {
    if (refreshIntervalId) {
      clearInterval(refreshIntervalId);
      refreshIntervalId = null;
    }
  }

  // Iniciar auto-refresco por defecto
  iniciarAutoRefresh();

  /**
   * Carga y renderiza datos del dashboard desde el backend.
   */
  async function cargarDatosDashboard(sedePubId, isBackground = false) {
    try {
      const data = await operationalApi.obtenerDashboard(sedePubId);

      // 1. KPI Ocupación
      const pct = Math.round(data.tasaOcupacionPorcentaje || 0);
      kpiOcupacion.textContent = `${pct}%`;
      kpiProgressBar.style.width = `${Math.min(pct, 100)}%`;
      kpiProgressBar.className = 'operational-progress-bar';
      if (pct >= 85) {
        kpiProgressBar.classList.add('is-danger');
      } else if (pct >= 70) {
        kpiProgressBar.classList.add('is-warning');
      } else {
        kpiProgressBar.classList.add('is-normal');
      }

      kpiDesglose.textContent = `${data.camasOcupadas || 0} ocupadas de ${data.totalCamas || 0} camas`;
      kpiLibresVal.textContent = data.camasDisponibles || 0;
      kpiLimpiezaVal.textContent = data.camasLimpieza || 0;
      kpiMantVal.textContent = data.camasMantenimiento || 0;

      // 2. KPI Urgencias
      kpiUrgencias.textContent = data.episodiosUrgenciasActivos || 0;
      const porTriaje = data.episodiosPorNivelTriaje || {};
      kpiCntT1.textContent = porTriaje['I'] || 0;
      kpiCntT2.textContent = porTriaje['II'] || 0;
      kpiCntT3.textContent = porTriaje['III'] || 0;
      kpiCntT4.textContent = porTriaje['IV'] || 0;
      kpiCntT5.textContent = porTriaje['V'] || 0;

      // 3. KPI Alertas
      const totalAlertas = (data.alertasActivas || []).length;
      kpiAlertas.textContent = totalAlertas;
      if (totalAlertas > 0) {
        kpiAlertaStatusPill.className = 'badge badge--cancelled';
        kpiAlertaStatusPill.innerHTML = `${ui.icon('alert-triangle', 'icon icon--sm')} ${totalAlertas} Requiere(n) Intervención`;
        badgeTotalAlertas.className = 'badge badge--cancelled';
        badgeTotalAlertas.textContent = `${totalAlertas} alerta(s) activa(s)`;
      } else {
        kpiAlertaStatusPill.className = 'badge badge--confirmed';
        kpiAlertaStatusPill.innerHTML = `${ui.icon('check', 'icon icon--sm')} Operación Normal`;
        badgeTotalAlertas.className = 'badge badge--confirmed';
        badgeTotalAlertas.textContent = '0 alertas activas';
      }

      // 4. KPI Camas Libres
      kpiDisponibles.textContent = data.camasDisponibles || 0;
      kpiTotalCamasPill.textContent = `Capacidad: ${data.totalCamas || 0} instaladas`;

      // 5. Tiempos Promedio Res 5596/2015
      const tiempos = data.tiemposPromedioEsperaMinutos || {};
      const t1Val = Math.round(tiempos['I'] || 0);
      const t2Val = Math.round(tiempos['II'] || 0);
      const t3Val = Math.round(tiempos['III'] || 0);
      const t4Val = Math.round(tiempos['IV'] || 0);
      const t5Val = Math.round(tiempos['V'] || 0);

      t1.textContent = `${t1Val} min`;
      t2.textContent = `${t2Val} min`;
      t3.textContent = `${t3Val} min`;
      t4.textContent = `${t4Val} min`;
      t5.textContent = `${t5Val} min`;

      cntWaitT1.textContent = `${porTriaje['I'] || 0} paciente(s)`;
      cntWaitT2.textContent = `${porTriaje['II'] || 0} paciente(s)`;
      cntWaitT3.textContent = `${porTriaje['III'] || 0} paciente(s)`;
      cntWaitT4.textContent = `${porTriaje['IV'] || 0} paciente(s)`;
      cntWaitT5.textContent = `${porTriaje['V'] || 0} paciente(s)`;

      // Comparación contra estándares legales obligatorios
      if (t2Val > 30) {
        warnT2.style.display = 'inline-flex';
        warnT2.textContent = `⚠️ Excede norma (+${t2Val - 30} min)`;
      } else {
        warnT2.style.display = 'none';
      }

      if (t3Val > 45) {
        warnT3.style.display = 'inline-flex';
        warnT3.textContent = `⚠️ Excede norma (+${t3Val - 45} min)`;
      } else {
        warnT3.style.display = 'none';
      }

      // 6. Alertas Activas
      if (totalAlertas > 0) {
        bannerAlerta.style.display = 'flex';
        textoAlerta.textContent = `Atención: Existen ${totalAlertas} alerta(s) de saturación que requieren intervención inmediata en esta sede.`;
        tbodyAlertas.innerHTML = '';

        data.alertasActivas.forEach(a => {
          const tr = document.createElement('tr');
          const esCritica = a.nivelSeveridad === 'CRITICA';
          const severidadBadgeClass = esCritica ? 'badge--cancelled' : 'badge--rescheduled';

          tr.innerHTML = `
            <td>
              <div class="font-medium text-sm">${formatTime(a.creadoAt)}</div>
            </td>
            <td>
              <span class="badge ${severidadBadgeClass}">
                ${esCritica ? ui.icon('alert-triangle', 'icon icon--sm') : ui.icon('alert-circle', 'icon icon--sm')}
                ${esc(a.nivelSeveridad)}
              </span>
            </td>
            <td><strong>${esc(a.tipoAlerta)}</strong></td>
            <td style="max-width: 380px;">${esc(a.mensaje)}</td>
            <td><span class="badge badge--scheduled">${esc(a.estado)}</span></td>
            <td style="text-align: right;">
              <button type="button" 
                      class="btn btn-secondary btn--sm btn-ack-alerta" 
                      data-id="${esc(a.publicId)}" 
                      data-tipo="${esc(a.tipoAlerta)}">
                Reconocer (ACK)
              </button>
            </td>
          `;
          tbodyAlertas.appendChild(tr);
        });

        // Configurar acciones de reconocimiento (ACK auditado) con modal accesible
        tbodyAlertas.querySelectorAll('.btn-ack-alerta').forEach(btn => {
          btn.addEventListener('click', () => {
            const alertaId = btn.getAttribute('data-id');
            const tipo = btn.getAttribute('data-tipo');
            mostrarModalReconocerAlerta(alertaId, tipo);
          });
        });
      } else {
        bannerAlerta.style.display = 'none';
        tbodyAlertas.innerHTML = `
          <tr>
            <td colspan="6" style="text-align: center; color: var(--text-muted); padding: var(--space-6);">
              <div class="flex items-center justify-center gap-2" style="color: var(--success); font-weight: var(--weight-medium);">
                ${ui.icon('check-circle', 'icon')}
                <span>No hay alertas activas en esta sede. La operación hospitalaria transcurre con normalidad.</span>
              </div>
            </td>
          </tr>
        `;
      }
    } catch (err) {
      if (!isBackground) {
        console.error('Error al cargar datos del Centro de Mando:', err);
        ui.showToast('Error de comunicación con el servicio de analítica.', 'error');
      }
    }
  }

  /**
   * Despliega un modal accesible para el reconocimiento auditado de alertas.
   */
  function mostrarModalReconocerAlerta(alertaId, tipoAlerta) {
    const modalContent = `
      <div>
        <p style="margin-bottom: var(--space-3); font-size: var(--text-sm);">
          Está por reconocer la alerta operativa <strong>${esc(tipoAlerta)}</strong>. 
          Esta acción quedará registrada en el log inmutable de auditoría con su usuario y dirección IP.
        </p>
        <div class="form-group">
          <label for="txtMotivoAckModal" class="form-label required">Motivo o acción de mitigación tomada:</label>
          <textarea id="txtMotivoAckModal" 
                    class="form-control" 
                    rows="3" 
                    placeholder="Ejemplo: Se activaron 4 camas de expansión en observación y se reforzó el triaje con personal médico adicional." 
                    required></textarea>
        </div>
      </div>
    `;

    ui.showModal({
      title: 'Reconocimiento de Alerta Operativa',
      message: modalContent,
      confirmText: 'Confirmar Reconocimiento (ACK)',
      cancelText: 'Cancelar',
      onConfirm: async () => {
        const textarea = document.getElementById('txtMotivoAckModal');
        const motivo = textarea ? textarea.value.trim() : '';
        if (!motivo) {
          ui.showToast('Debe ingresar el motivo de reconocimiento para el registro de auditoría.', 'error');
          return;
        }

        try {
          await operationalApi.reconocerAlerta(alertaId, motivo);
          ui.showToast('Alerta reconocida con éxito y asentada en auditoría.', 'success');
          await cargarDatosDashboard(selectSede.value);
        } catch (err) {
          ui.showToast(err.message || 'Error al reconocer la alerta.', 'error');
        }
      }
    });
  }

  // Módulo de Consulta de QR de Manilla / Cabecera (O03)
  const formScan = container.querySelector('#form-scan-qr');
  const resScan = container.querySelector('#resultado-qr-track');
  const qrIdProv = container.querySelector('#qr-id-prov');
  const qrUbicacion = container.querySelector('#qr-ubicacion');
  const qrNivel = container.querySelector('#qr-nivel-triaje');
  const qrSedeVal = container.querySelector('#qr-sede-val');
  const qrTokenVal = container.querySelector('#qr-token-val');
  const btnCopiarToken = container.querySelector('#btnCopiarTokenQr');

  formScan.addEventListener('submit', async (e) => {
    e.preventDefault();
    const token = container.querySelector('#input-token-qr').value.trim();
    if (!token) return;

    try {
      const qrData = await operationalApi.consultarTrackingQr(token);
      resScan.style.display = 'block';
      qrIdProv.textContent = qrData.codigoIdentidadProvisional || 'Paciente Asistencial';
      qrUbicacion.textContent = qrData.ubicacionActual || 'Ubicación intrahospitalaria pendiente';
      
      const nivel = qrData.nivelTriaje || 'I';
      qrNivel.textContent = `Triaje ${nivel}`;
      qrNivel.className = `badge badge--scheduled`;
      if (nivel === 'I') qrNivel.style.borderLeft = '3px solid var(--triage-1-bd)';
      else if (nivel === 'II') qrNivel.style.borderLeft = '3px solid var(--triage-2-bd)';
      else if (nivel === 'III') qrNivel.style.borderLeft = '3px solid var(--triage-3-bd)';

      qrSedeVal.textContent = qrData.sedeNombre || selectSede.options[selectSede.selectedIndex]?.text || 'Sede Principal';
      qrTokenVal.textContent = qrData.tokenQr;

      ui.showToast('Localización intrahospitalaria verificada con éxito.', 'success', 2500);
    } catch (err) {
      resScan.style.display = 'none';
      ui.showToast(err.message || 'Token QR no encontrado o inválido.', 'error');
    }
  });

  // Copiar token de manilla al portapapeles
  btnCopiarToken?.addEventListener('click', async () => {
    const val = qrTokenVal.textContent;
    if (val && val !== '-') {
      try {
        await navigator.clipboard.writeText(val);
        ui.showToast('Token copiado al portapapeles.', 'info', 2000);
      } catch {
        ui.showToast(val, 'info', 3000);
      }
    }
  });
}
