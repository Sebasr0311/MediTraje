/**
 * MediTriaje 2.0 — Visor Público de Resumen de Salud de Emergencia (emergency-summary-view.js)
 * Permite la consulta prehospitalaria protegida mediante token criptográfico y PIN opcional.
 * Apto para paramédicos y personal de urgencias médicas (ADR-010, §5.17, §5.18).
 */

import { api } from '../api.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

/**
 * Formatea una fecha ISO a hora y fecha de Bogotá
 * @param {string} isoString
 * @param {boolean} includeTime
 * @returns {string}
 */
function formatDate(isoString, includeTime = true) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    const options = {
      timeZone: 'America/Bogota',
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    };
    if (includeTime) {
      options.hour = '2-digit';
      options.minute = '2-digit';
      options.hour12 = true;
    }
    return new Intl.DateTimeFormat('es-CO', options).format(d);
  } catch {
    return isoString;
  }
}

/**
 * Vista del Visor de Resumen de Emergencia
 * @param {HTMLElement} container
 * @param {Object} routeData
 */
export async function emergencySummaryView(container, { params }) {
  const token = params?.token;

  if (!token) {
    renderError(container, 'Identificador de acceso no proporcionado.');
    return;
  }

  // 1. Mostrar skeleton de verificación
  container.innerHTML = `
    <div class="sg-section" style="max-width: 900px; margin: 0 auto; padding-top: var(--space-6); padding-bottom: var(--space-12);">
      <div class="card p-6 text-center">
        <div class="skeleton skeleton-title" style="width: 60%; height: 32px; margin: 0 auto 16px auto;"></div>
        <div class="skeleton skeleton-text" style="width: 80%; height: 16px; margin: 0 auto 8px auto;"></div>
        <div class="skeleton skeleton-card" style="height: 140px; margin-top: 24px;"></div>
      </div>
    </div>
  `;

  // 2. Verificar estado preliminar del token
  try {
    const verificacion = await api.get(`/emergency-summary/${encodeURIComponent(token)}/check`);

    if (!verificacion.valido) {
      renderAccesoInvalido(container, verificacion);
      return;
    }

    if (verificacion.requierePin) {
      // Solicitar PIN de 4 dígitos
      renderFormularioPin(container, token);
    } else {
      // Consultar directamente sin PIN
      await consultarYCargarResumen(container, token, null);
    }
  } catch (err) {
    renderError(container, err.message || 'No fue posible validar el código QR de emergencia.');
  }
}

/**
 * Renderiza formulario de ingreso de PIN de 4 dígitos
 * @param {HTMLElement} container
 * @param {string} token
 */
function renderFormularioPin(container, token) {
  container.innerHTML = `
    <div class="sg-section" style="max-width: 520px; margin: 0 auto; padding-top: var(--space-8); padding-bottom: var(--space-12);">
      <div class="card" style="border-top: 4px solid var(--primary); box-shadow: var(--shadow-lg);">
        <div class="card-header text-center pb-2">
          <div style="background-color: var(--teal-50); color: var(--primary); width: 56px; height: 56px; border-radius: var(--radius-full); display: flex; align-items: center; justify-content: center; margin: 0 auto var(--space-3) auto;">
            ${ui.icon('shield', 'icon icon--lg')}
          </div>
          <h1 class="card-title text-2xl font-bold">Código Protegido con PIN</h1>
          <p class="card-subtitle text-sm text-muted mt-1">
            El paciente configuró un PIN numérico de seguridad para autorizar la lectura de su resumen clínico de emergencia.
          </p>
        </div>

        <div class="card-body">
          <div id="pinErrorAlert" class="mb-4" style="display: none;"></div>

          <form id="formPinEmergencia" novalidate>
            <div class="mb-6 text-center">
              <label for="inputPin" class="form-label font-semibold text-sm mb-2 block">
                Digita el PIN de 4 dígitos proporcionado:
              </label>
              <div style="max-width: 220px; margin: 0 auto;">
                <input
                  type="password"
                  id="inputPin"
                  name="pin"
                  class="form-input text-center font-mono text-2xl tracking-widest"
                  maxlength="4"
                  pattern="[0-9]{4}"
                  placeholder="••••"
                  inputmode="numeric"
                  required
                  autofocus
                  autocomplete="off"
                  style="letter-spacing: 0.4em; padding: 10px;"
                />
              </div>
              <p class="text-xs text-muted mt-2">
                Consulta al paciente o a su acompañante para obtener el código de desbloqueo.
              </p>
            </div>

            <button type="submit" id="btnSubmitPin" class="btn btn-primary btn--lg w-full">
              ${ui.icon('check', 'icon icon--md')}
              <span>Consultar Resumen Clínico</span>
            </button>
          </form>

          <div class="mt-6 pt-4 text-center border-t text-xs text-muted">
            <p style="margin: 0;">
              Por seguridad, cada intento de consulta queda registrado en los registros inmutables de auditoría del sistema de salud.
            </p>
          </div>
        </div>
      </div>
    </div>
  `;

  const form = container.querySelector('#formPinEmergencia');
  const inputPin = container.querySelector('#inputPin');
  const pinErrorAlert = container.querySelector('#pinErrorAlert');
  const btnSubmit = container.querySelector('#btnSubmitPin');

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const pin = inputPin.value.trim();

    if (!pin || !/^[0-9]{4}$/.test(pin)) {
      mostrarErrorPin('Por favor ingresa un PIN numérico válido de 4 dígitos.');
      inputPin.focus();
      return;
    }

    try {
      btnSubmit.disabled = true;
      btnSubmit.innerHTML = `<span>Validando credenciales...</span>`;
      pinErrorAlert.style.display = 'none';

      await consultarYCargarResumen(container, token, pin);
    } catch (err) {
      btnSubmit.disabled = false;
      btnSubmit.innerHTML = `
        ${ui.icon('check', 'icon icon--md')}
        <span>Consultar Resumen Clínico</span>
      `;
      mostrarErrorPin(err.message || 'El PIN ingresado es incorrecto.');
      inputPin.value = '';
      inputPin.focus();
    }
  });

  function mostrarErrorPin(mensaje) {
    pinErrorAlert.style.display = 'block';
    pinErrorAlert.innerHTML = `
      <div class="card p-3 flex items-center gap-2" style="background-color: var(--danger-bg); border-color: var(--danger); color: var(--danger);">
        ${ui.icon('alert-circle', 'icon icon--sm')}
        <span class="text-sm font-medium">${esc(mensaje)}</span>
      </div>
    `;
  }
}

/**
 * Llama a la API para resolver el resumen clínico y renderizarlo
 * @param {HTMLElement} container
 * @param {string} token
 * @param {string|null} pin
 */
async function consultarYCargarResumen(container, token, pin) {
  const response = await api.post(`/emergency-summary/${encodeURIComponent(token)}`, { pin });
  renderResumenClinico(container, response);
}

/**
 * Renderiza el resumen clínico completo con diseño sobrio y de alta legibilidad
 * @param {HTMLElement} container
 * @param {Object} data ResumenSaludResponse
 */
function renderResumenClinico(container, data) {
  const paciente = data.paciente || {};
  const alergias = data.alergias || [];
  const medicamentos = data.medicamentosActivos || [];
  const atenciones = data.atencionesRecientes || [];

  container.innerHTML = `
    <div class="sg-section" style="max-width: 960px; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Encabezado de Emergencia -->
      <div class="card mb-6" style="border: 2px solid var(--danger); background: linear-gradient(90deg, var(--danger-bg) 0%, var(--surface) 100%);">
        <div class="card-body flex flex-wrap items-center justify-between gap-4 py-4">
          <div class="flex items-center gap-3">
            <div style="background-color: var(--danger); color: #FFFFFF; width: 48px; height: 48px; border-radius: var(--radius-md); display: flex; align-items: center; justify-content: center;">
              ${ui.icon('activity', 'icon icon--lg')}
            </div>
            <div>
              <span class="badge badge--danger mb-1 font-bold">ATENCIÓN PREHOSPITALARIA Y URGENCIAS</span>
              <h1 class="text-2xl font-bold" style="color: var(--text);">Resumen Clínico de Emergencia</h1>
            </div>
          </div>

          <div class="text-right text-xs text-muted">
            <div><strong>Consulta auditada:</strong> ${formatDate(data.generadoAt || new Date().toISOString())}</div>
            <div class="text-success font-medium">Lectura autorizada por el paciente</div>
          </div>
        </div>
      </div>

      <!-- Advertencia Legal Obligatoria (ADR-010, §5.18) -->
      <div class="card mb-6" style="border-left: 4px solid var(--warning); background-color: var(--warning-bg); color: var(--warning-text, #854D0E);">
        <div class="card-body flex items-start gap-3 py-3">
          <div style="flex-shrink: 0; padding-top: 2px;">
            ${ui.icon('alert-triangle', 'icon icon--md')}
          </div>
          <div class="text-xs leading-relaxed font-medium">
            <strong>AVISO CLÍNICO OBLIGATORIO:</strong>
            Prototipo académico. Orienta, no diagnostica ni reemplaza la valoración de un profesional de la salud. ${esc(data.advertenciaLegal || 'Esta información consolidada no sustituye la historia clínica integral de un centro hospitalario ni el criterio de los equipos de urgencias.')}
          </div>
        </div>
      </div>

      <!-- Tarjeta Principal del Paciente -->
      <div class="card mb-6" style="box-shadow: var(--shadow-md);">
        <div class="card-header flex items-center justify-between">
          <h2 class="card-title text-xl flex items-center gap-2">
            ${ui.icon('user', 'icon icon--sm text-primary')}
            <span>Identificación del Paciente</span>
          </h2>
          <span class="badge badge--confirmed">Registro Verificado</span>
        </div>
        <div class="card-body">
          <div class="grid grid-cols-1 grid-cols-3-md gap-4">
            <div>
              <span class="text-xs text-muted block uppercase tracking-wider font-semibold">Nombre Completo:</span>
              <strong class="text-lg font-bold" style="color: var(--text);">${esc(paciente.nombreCompleto || 'No informado')}</strong>
            </div>

            <div>
              <span class="text-xs text-muted block uppercase tracking-wider font-semibold">Documento de Identidad:</span>
              <span class="text-base font-semibold font-mono">${esc(paciente.tipoDocumento)} ${esc(paciente.numeroDocumento)}</span>
            </div>

            <div>
              <span class="text-xs text-muted block uppercase tracking-wider font-semibold">Edad y Nacimiento:</span>
              <span class="text-base font-semibold">${paciente.edad} años</span>
              <span class="text-xs text-muted block">(${formatDate(paciente.fechaNacimiento, false)})</span>
            </div>
          </div>

          <!-- Contacto si fue autorizado -->
          <div class="mt-4 pt-4 border-t flex flex-wrap gap-6 items-center text-sm">
            <div>
              <span class="text-xs text-muted uppercase font-semibold">Teléfono de Contacto:</span>
              ${paciente.telefono ? `
                <a href="tel:${esc(paciente.telefono)}" class="font-bold text-primary flex items-center gap-1 mt-0.5" style="text-decoration: underline;">
                  ${ui.icon('phone', 'icon icon--sm')}
                  <span>${esc(paciente.telefono)}</span>
                </a>
              ` : `<span class="text-muted italic block">No autorizado o sin registro</span>`}
            </div>

            <div>
              <span class="text-xs text-muted uppercase font-semibold">Correo Electrónico:</span>
              <span class="font-medium block text-muted">${esc(paciente.email || 'No autorizado o sin registro')}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Sección de Alergias (Prioridad Crítica en Urgencias) -->
      <div class="card mb-6" style="border-top: 4px solid var(--danger);">
        <div class="card-header flex items-center justify-between">
          <h2 class="card-title text-xl flex items-center gap-2" style="color: var(--danger);">
            ${ui.icon('alert-circle', 'icon icon--sm text-danger')}
            <span>Alergias e Hipersensibilidades Reportadas</span>
          </h2>
          <span class="badge ${alergias.length > 0 ? 'badge--danger' : 'badge--confirmed'}">
            ${alergias.length} ${alergias.length === 1 ? 'alergia' : 'alergias'}
          </span>
        </div>
        <div class="card-body">
          ${alergias.length === 0 ? `
            <div class="p-4 text-center text-muted text-sm bg-surface-2 rounded-md">
              No se registran antecedentes de alergias conocidas o este módulo no fue autorizado en el alcance del código.
            </div>
          ` : `
            <div class="grid grid-cols-1 grid-cols-2-md gap-4">
              ${alergias.map(a => `
                <div class="card p-4" style="background-color: var(--danger-bg); border-color: var(--danger-border, #FECACA);">
                  <div class="flex items-center justify-between mb-2">
                    <strong class="text-base font-bold text-danger">${esc(a.sustancia)}</strong>
                    <div class="flex items-center gap-1">
                      ${a.origen === 'PACIENTE' ? '<span class="badge badge--scheduled text-xs">Autorreportada</span>' : '<span class="badge badge--confirmed text-xs">Diagnóstico</span>'}
                      <span class="badge badge--danger">${esc(a.severidad || 'NO ESPECIFICADA')}</span>
                    </div>
                  </div>
                  <div class="text-xs text-muted">
                    <strong>Reacción adversa:</strong> ${esc(a.reaccion || 'Sin descripción')}
                  </div>
                </div>
              `).join('')}
            </div>
          `}
        </div>
      </div>

      <!-- Sección de Medicamentos Activos -->
      <div class="card mb-6">
        <div class="card-header flex items-center justify-between">
          <h2 class="card-title text-xl flex items-center gap-2">
            ${ui.icon('pill', 'icon icon--sm text-primary')}
            <span>Medicamentos Activos Prescritos</span>
          </h2>
          <span class="badge badge--scheduled">
            ${medicamentos.length} ${medicamentos.length === 1 ? 'medicamento' : 'medicamentos'}
          </span>
        </div>
        <div class="card-body">
          ${medicamentos.length === 0 ? `
            <div class="p-4 text-center text-muted text-sm bg-surface-2 rounded-md">
              Sin prescripciones de medicamentos activos vigentes o no autorizadas en el alcance.
            </div>
          ` : `
            <div class="table-container" style="overflow-x: auto;">
              <table class="table" style="width: 100%;">
                <thead>
                  <tr>
                    <th>Medicamento / Principio Activo</th>
                    <th>Dosis y Frecuencia</th>
                    <th>Presentación / Concentración</th>
                    <th>Duración</th>
                    <th>Indicaciones Clínicas</th>
                  </tr>
                </thead>
                <tbody>
                  ${medicamentos.map(m => `
                    <tr>
                      <td>
                        <strong class="text-sm block" style="color: var(--text);">${esc(m.nombre)}</strong>
                        <span class="text-xs text-muted font-mono">${esc(m.principioActivo)}</span>
                      </td>
                      <td class="text-sm">
                        <div><strong>${esc(m.dosis)}</strong></div>
                        <div class="text-xs text-muted">${esc(m.frecuencia)}</div>
                      </td>
                      <td class="text-xs text-muted">
                        ${esc(m.presentacion || '')} ${esc(m.concentracion || '')}
                      </td>
                      <td class="text-xs font-medium">
                        ${m.duracionDias} días (${m.cantidad} uds)
                      </td>
                      <td class="text-xs text-muted" style="max-width: 250px;">
                        ${esc(m.indicaciones || 'Según orden médica')}
                      </td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          `}
        </div>
      </div>

      <!-- Sección de Atenciones Recientes y Diagnósticos CIE-10 -->
      <div class="card mb-6">
        <div class="card-header flex items-center justify-between">
          <h2 class="card-title text-xl flex items-center gap-2">
            ${ui.icon('file-text', 'icon icon--sm text-primary')}
            <span>Atenciones Médicas Recientes</span>
          </h2>
          <span class="badge badge--scheduled">
            ${atenciones.length} ${atenciones.length === 1 ? 'consulta' : 'consultas'}
          </span>
        </div>
        <div class="card-body">
          ${atenciones.length === 0 ? `
            <div class="p-4 text-center text-muted text-sm bg-surface-2 rounded-md">
              Sin atenciones médicas previas reportadas o no autorizadas en el alcance.
            </div>
          ` : `
            <div class="flex flex-col gap-4">
              ${atenciones.map(at => `
                <div class="card p-4" style="background-color: var(--surface-2); border-left: 4px solid var(--primary);">
                  <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
                    <span class="text-xs text-muted font-semibold">${formatDate(at.fechaAtencion)}</span>
                    <span class="badge badge--confirmed">${esc(at.especialidad || 'Consulta Externa')}</span>
                  </div>

                  <div class="mb-2">
                    <span class="text-xs text-muted block uppercase tracking-wider font-semibold">Diagnóstico Principal (CIE-10):</span>
                    <strong class="text-sm font-semibold" style="color: var(--text);">
                      ${esc(at.diagnosticoCodigo || '')} — ${esc(at.diagnosticoDescripcion || 'Diagnóstico no especificado')}
                    </strong>
                  </div>

                  ${at.motivoConsulta ? `
                    <div class="text-xs text-muted mb-1">
                      <strong>Motivo de consulta:</strong> ${esc(at.motivoConsulta)}
                    </div>
                  ` : ''}

                  ${at.indicaciones ? `
                    <div class="text-xs text-muted">
                      <strong>Indicaciones al egreso:</strong> ${esc(at.indicaciones)}
                    </div>
                  ` : ''}
                </div>
              `).join('')}
            </div>
          `}
        </div>
      </div>

      <!-- Pie de Seguridad y Auditoría -->
      <div class="text-center text-xs text-muted pt-4 border-t">
        <p class="mb-1">
          Este acceso temporal registra un evento inmutable en el repositorio de auditoría institucional (ADR-011, §5.18).
        </p>
        <p style="margin: 0;">
          MediTriaje 2.0 · Plataforma de Triaje, Citas e Historia Clínica
        </p>
      </div>
    </div>
  `;
}

/**
 * Renderiza pantalla de acceso inválido, expirado o agotado
 * @param {HTMLElement} container
 * @param {Object} verificacion
 */
function renderAccesoInvalido(container, verificacion) {
  let badgeText = verificacion.estado || 'INVÁLIDO';
  let badgeClass = 'badge--danger';
  if (verificacion.estado === 'EXPIRADO') badgeClass = 'badge--scheduled';
  if (verificacion.estado === 'AGOTADO') badgeClass = 'badge--rescheduled';

  container.innerHTML = `
    <div class="sg-section" style="max-width: 600px; margin: 0 auto; padding-top: var(--space-8); padding-bottom: var(--space-12);">
      <div class="card p-8 text-center" style="border-top: 4px solid var(--danger); box-shadow: var(--shadow-lg);">
        <div style="background-color: var(--danger-bg); color: var(--danger); width: 64px; height: 64px; border-radius: var(--radius-full); display: flex; align-items: center; justify-content: center; margin: 0 auto var(--space-4) auto;">
          ${ui.icon('alert-triangle', 'icon icon--lg')}
        </div>

        <span class="badge ${badgeClass} mb-2">${esc(badgeText)}</span>
        <h1 class="text-2xl font-bold mb-2">Acceso de Emergencia No Disponible</h1>
        <p class="text-muted text-sm mb-6 leading-relaxed">
          ${esc(verificacion.mensaje || 'El código QR consultado ya no es válido, ha superado su vigencia de 15 minutos o consumió el cupo máximo de 3 lecturas permitidas.')}
        </p>

        <div class="card p-4 text-xs text-muted mb-6 text-left" style="background-color: var(--surface-2);">
          <strong>¿Por qué ocurre esto?</strong>
          <p class="mt-1" style="margin-bottom: 0;">
            Por estricta protección de la privacidad y confidencialidad de los datos de salud del paciente, los códigos de emergencia de MediTriaje 2.0 son efímeros y se auto-destruyen tras su vencimiento o agotamiento.
          </p>
        </div>

        <a href="#/" class="btn btn-primary">
          <span>Ir al inicio de MediTriaje</span>
        </a>
      </div>
    </div>
  `;
}

/**
 * Renderiza error general
 * @param {HTMLElement} container
 * @param {string} mensaje
 */
function renderError(container, mensaje) {
  container.innerHTML = `
    <div class="sg-section" style="max-width: 500px; margin: 0 auto; padding-top: var(--space-8); padding-bottom: var(--space-12);">
      <div class="card p-6 text-center">
        <div style="color: var(--danger); margin-bottom: var(--space-3); display: flex; justify-content: center;">
          ${ui.icon('alert-circle', 'icon icon--lg')}
        </div>
        <h2 class="text-xl font-bold mb-2">Error de Acceso</h2>
        <p class="text-muted text-sm mb-4">${esc(mensaje)}</p>
        <a href="#/" class="btn btn-secondary btn--sm">
          <span>Volver al inicio</span>
        </a>
      </div>
    </div>
  `;
}
