/**
 * MediTriaje 2.0 — Centro de Mando Analítico Hospitalario y Alertas (operational-dashboard.js)
 * KPIs de capacidad, tiempos Res 5596/2015, reconocimiento de alertas y QR de cabecera (O01, O02, O03).
 */

import { operationalApi, emergencyApi } from '../api.js';

export async function renderOperationalDashboard(container) {
  container.innerHTML = `
    <div class="view-header" style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1rem;">
      <div>
        <div class="breadcrumb">Hospital &gt; Analítica &gt; Centro de Mando Operativo</div>
        <h1 class="view-title">Centro de Mando y Analítica Hospitalaria</h1>
        <p class="view-subtitle">Monitoreo en tiempo real de capacidad instalada, tiempos de espera y saturación hospitalaria en Valledupar.</p>
      </div>
      <div>
        <label for="select-sede-op" style="font-weight: 600; font-size: 0.85rem; display: block; margin-bottom: 0.25rem;">Sede Hospitalaria:</label>
        <select id="select-sede-op" class="form-control" style="min-width: 220px;">
          <option value="">Cargando sedes...</option>
        </select>
      </div>
    </div>

    <!-- Indicador de Alerta Crítica Global -->
    <div id="banner-alerta-critica" class="alert alert-danger" style="display: none; margin-top: 1rem; border-left: 4px solid #c62828;">
      <strong>⚠️ ALERTA DE SATURACIÓN OPERATIVA:</strong>
      <span id="texto-alerta-critica">Existen demoras asistenciales o saturación de camas activas en esta sede.</span>
    </div>

    <!-- Tarjetas de KPIs Principales -->
    <div class="grid grid-4" style="gap: 1rem; margin-top: 1.5rem;">
      <div class="card" style="text-align: center; border-top: 4px solid var(--color-primary);">
        <div style="font-size: 0.85rem; color: var(--color-text-secondary); margin-bottom: 0.25rem;">Ocupación de Camas</div>
        <div id="kpi-tasa-ocupacion" style="font-size: 2rem; font-weight: 700;">0%</div>
        <div id="kpi-desglose-camas" style="font-size: 0.8rem; color: var(--color-text-secondary); margin-top: 0.25rem;">0 de 0 camas ocupadas</div>
      </div>

      <div class="card" style="text-align: center; border-top: 4px solid #f57c00;">
        <div style="font-size: 0.85rem; color: var(--color-text-secondary); margin-bottom: 0.25rem;">Pacientes Urgencias</div>
        <div id="kpi-pacientes-urgencias" style="font-size: 2rem; font-weight: 700;">0</div>
        <div style="font-size: 0.8rem; color: var(--color-text-secondary); margin-top: 0.25rem;">En valoración / espera</div>
      </div>

      <div class="card" style="text-align: center; border-top: 4px solid #c62828;">
        <div style="font-size: 0.85rem; color: var(--color-text-secondary); margin-bottom: 0.25rem;">Alertas Activas</div>
        <div id="kpi-alertas-activas" style="font-size: 2rem; font-weight: 700;">0</div>
        <div style="font-size: 0.8rem; color: var(--color-text-secondary); margin-top: 0.25rem;">Demoras y umbrales</div>
      </div>

      <div class="card" style="text-align: center; border-top: 4px solid #2e7d32;">
        <div style="font-size: 0.85rem; color: var(--color-text-secondary); margin-bottom: 0.25rem;">Camas Disponibles</div>
        <div id="kpi-camas-disponibles" style="font-size: 2rem; font-weight: 700;">0</div>
        <div style="font-size: 0.8rem; color: var(--color-text-secondary); margin-top: 0.25rem;">Listas para asignación</div>
      </div>
    </div>

    <!-- Tiempos de Espera por Nivel de Triaje (Resolución 5596/2015) -->
    <div class="card" style="margin-top: 1.5rem;">
      <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 1rem;">
        Tiempos de Espera por Clasificación de Triaje (Resolución 5596/2015)
      </h2>
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; text-align: center;">
        <div style="background: #ffebee; padding: 1rem; border-radius: var(--radius-md); border-top: 3px solid #d32f2f;">
          <div style="font-weight: 700; color: #d32f2f;">Triaje I — Resucitación</div>
          <div id="tiempo-t1" style="font-size: 1.5rem; font-weight: 700; margin: 0.5rem 0;">0 min</div>
          <small style="color: var(--color-text-secondary);">Estándar: 0 min (Inmediato)</small>
        </div>

        <div style="background: #fff3e0; padding: 1rem; border-radius: var(--radius-md); border-top: 3px solid #e65100;">
          <div style="font-weight: 700; color: #e65100;">Triaje II — Emergencia</div>
          <div id="tiempo-t2" style="font-size: 1.5rem; font-weight: 700; margin: 0.5rem 0;">0 min</div>
          <small style="color: var(--color-text-secondary);">Estándar: máx. 30 min</small>
        </div>

        <div style="background: #fffde7; padding: 1rem; border-radius: var(--radius-md); border-top: 3px solid #fbc02d;">
          <div style="font-weight: 700; color: #f57f17;">Triaje III — Urgencia</div>
          <div id="tiempo-t3" style="font-size: 1.5rem; font-weight: 700; margin: 0.5rem 0;">0 min</div>
          <small style="color: var(--color-text-secondary);">Estándar: máx. 45 min</small>
        </div>

        <div style="background: #e8f5e9; padding: 1rem; border-radius: var(--radius-md); border-top: 3px solid #2e7d32;">
          <div style="font-weight: 700; color: #2e7d32;">Triaje IV — Prioritario</div>
          <div id="tiempo-t4" style="font-size: 1.5rem; font-weight: 700; margin: 0.5rem 0;">0 min</div>
          <small style="color: var(--color-text-secondary);">Consulta asistencial</small>
        </div>

        <div style="background: #e1f5fe; padding: 1rem; border-radius: var(--radius-md); border-top: 3px solid #0288d1;">
          <div style="font-weight: 700; color: #0288d1;">Triaje V — No Urgente</div>
          <div id="tiempo-t5" style="font-size: 1.5rem; font-weight: 700; margin: 0.5rem 0;">0 min</div>
          <small style="color: var(--color-text-secondary);">Ambulatorio / Cita</small>
        </div>
      </div>
    </div>

    <!-- Gestión y Reconocimiento de Alertas de Saturación (O02) -->
    <div class="card" style="margin-top: 1.5rem;">
      <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 1rem;">Alertas Operativas Hospitalarias</h2>
      <div style="overflow-x: auto;">
        <table class="table" style="width: 100%; text-align: left;">
          <thead>
            <tr>
              <th>Fecha/Hora</th>
              <th>Severidad</th>
              <th>Tipo de Alerta</th>
              <th>Mensaje / Diagnóstico Operativo</th>
              <th>Estado</th>
              <th>Acción</th>
            </tr>
          </thead>
          <tbody id="tbody-alertas">
            <tr>
              <td colspan="6" style="text-align: center; color: var(--color-text-secondary); padding: 1.5rem;">
                No hay alertas activas en esta sede. Operación dentro de parámetros normales.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Módulo de Seguimiento y Manilla QR Intrahospitalaria (O03) -->
    <div class="card" style="margin-top: 1.5rem;">
      <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 0.5rem;">Seguimiento Intrahospitalario por Código QR Seguro</h2>
      <p style="color: var(--color-text-secondary); font-size: 0.9rem; margin-bottom: 1rem;">
        Consulte la localización segura de un paciente mediante el token de su manilla o cabecera (sin exposición de diagnóstico ni PHI).
      </p>

      <form id="form-scan-qr" style="display: flex; gap: 0.5rem; max-width: 500px; margin-bottom: 1rem;">
        <input type="text" id="input-token-qr" class="form-control" placeholder="Ej: QROP-7A1B2C3D4E" required />
        <button type="submit" class="btn btn-primary">Verificar Localización</button>
      </form>

      <div id="resultado-qr-track" style="display: none; background: var(--color-bg-secondary); padding: 1rem; border-radius: var(--radius-md); max-width: 500px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
          <strong id="qr-id-prov" style="font-size: 1.1rem;">-</strong>
          <span id="qr-badge-estado" class="badge badge-success">ACTIVO</span>
        </div>
        <div>Ubicación: <strong id="qr-ubicacion">-</strong></div>
        <div>Nivel de Triaje: <span id="qr-nivel-triaje" class="badge">-</span></div>
        <div style="margin-top: 0.5rem; font-size: 0.8rem; color: var(--color-text-secondary);">
          Token auditado: <span id="qr-token-val">-</span>
        </div>
      </div>
    </div>
  `;

  const selectSede = container.querySelector('#select-sede-op');
  const bannerAlerta = container.querySelector('#banner-alerta-critica');
  const textoAlerta = container.querySelector('#texto-alerta-critica');

  const kpiOcupacion = container.querySelector('#kpi-tasa-ocupacion');
  const kpiDesglose = container.querySelector('#kpi-desglose-camas');
  const kpiUrgencias = container.querySelector('#kpi-pacientes-urgencias');
  const kpiAlertas = container.querySelector('#kpi-alertas-activas');
  const kpiDisponibles = container.querySelector('#kpi-camas-disponibles');

  const t1 = container.querySelector('#tiempo-t1');
  const t2 = container.querySelector('#tiempo-t2');
  const t3 = container.querySelector('#tiempo-t3');
  const t4 = container.querySelector('#tiempo-t4');
  const t5 = container.querySelector('#tiempo-t5');

  const tbodyAlertas = container.querySelector('#tbody-alertas');

  // Cargar sedes disponibles
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
      cargarDatosDashboard(selectSede.value);
    } else {
      selectSede.innerHTML = '<option value="">Sede Principal</option>';
      cargarDatosDashboard(null);
    }
  } catch (err) {
    selectSede.innerHTML = '<option value="">Sede Principal</option>';
    cargarDatosDashboard(null);
  }

  selectSede.addEventListener('change', () => {
    cargarDatosDashboard(selectSede.value);
  });

  async function cargarDatosDashboard(sedePubId) {
    try {
      const data = await operationalApi.obtenerDashboard(sedePubId);

      kpiOcupacion.textContent = `${data.tasaOcupacionPorcentaje}%`;
      kpiDesglose.textContent = `${data.camasOcupadas} ocupadas de ${data.totalCamas} camas`;
      kpiUrgencias.textContent = data.episodiosUrgenciasActivos;
      kpiAlertas.textContent = data.alertasActivas ? data.alertasActivas.length : 0;
      kpiDisponibles.textContent = data.camasDisponibles;

      const tiempos = data.tiemposPromedioEsperaMinutos || {};
      t1.textContent = `${tiempos['I'] || 0} min`;
      t2.textContent = `${tiempos['II'] || 0} min`;
      t3.textContent = `${tiempos['III'] || 0} min`;
      t4.textContent = `${tiempos['IV'] || 0} min`;
      t5.textContent = `${tiempos['V'] || 0} min`;

      // Renderizar Alertas
      if (data.alertasActivas && data.alertasActivas.length > 0) {
        bannerAlerta.style.display = 'block';
        textoAlerta.textContent = `Atención: ${data.alertasActivas.length} alerta(s) de saturación operativa requieren intervención en esta sede.`;
        tbodyAlertas.innerHTML = '';

        data.alertasActivas.forEach(a => {
          const tr = document.createElement('tr');
          const esCritica = a.nivelSeveridad === 'CRITICA';
          tr.innerHTML = `
            <td>${new Date(a.creadoAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</td>
            <td><span class="badge ${esCritica ? 'badge-danger' : 'badge-warning'}">${a.nivelSeveridad}</span></td>
            <td><strong>${a.tipoAlerta}</strong></td>
            <td>${a.mensaje}</td>
            <td><span class="badge badge-secondary">${a.estado}</span></td>
            <td>
              <button class="btn btn-sm btn-outline-primary btn-ack-alerta" data-id="${a.publicId}">
                Reconocer (ACK)
              </button>
            </td>
          `;
          tbodyAlertas.appendChild(tr);
        });

        // Eventos de reconocimiento auditado
        tbodyAlertas.querySelectorAll('.btn-ack-alerta').forEach(btn => {
          btn.addEventListener('click', async () => {
            const alertaId = btn.getAttribute('data-id');
            const motivo = prompt('Ingrese el motivo o acción tomada para reconocer esta alerta (auditado):');
            if (motivo && motivo.trim().length > 0) {
              try {
                await operationalApi.reconocerAlerta(alertaId, motivo.trim());
                alert('Alerta reconocida con éxito y registrada en auditoría.');
                cargarDatosDashboard(selectSede.value);
              } catch (err) {
                alert(err.message || 'Error al reconocer alerta.');
              }
            }
          });
        });
      } else {
        bannerAlerta.style.display = 'none';
        tbodyAlertas.innerHTML = `
          <tr>
            <td colspan="6" style="text-align: center; color: var(--color-text-secondary); padding: 1.5rem;">
              No hay alertas activas en esta sede. Operación dentro de parámetros normales.
            </td>
          </tr>
        `;
      }
    } catch (err) {
      console.error('Error cargando dashboard operativo:', err);
    }
  }

  // Escaneo / Consulta de QR
  const formScan = container.querySelector('#form-scan-qr');
  const resScan = container.querySelector('#resultado-qr-track');
  const qrIdProv = container.querySelector('#qr-id-prov');
  const qrUbicacion = container.querySelector('#qr-ubicacion');
  const qrNivel = container.querySelector('#qr-nivel-triaje');
  const qrTokenVal = container.querySelector('#qr-token-val');

  formScan.addEventListener('submit', async (e) => {
    e.preventDefault();
    const token = container.querySelector('#input-token-qr').value.trim();
    if (!token) return;

    try {
      const qrData = await operationalApi.consultarTrackingQr(token);
      resScan.style.display = 'block';
      qrIdProv.textContent = qrData.codigoIdentidadProvisional || 'Paciente Identificado';
      qrUbicacion.textContent = qrData.ubicacionActual || 'Ubicación no reportada';
      qrNivel.textContent = `Triaje ${qrData.nivelTriaje || 'Pendiente'}`;
      qrTokenVal.textContent = qrData.tokenQr;
    } catch (err) {
      alert(err.message || 'Token QR no encontrado o inválido.');
    }
  });
}
