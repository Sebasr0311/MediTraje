/**
 * MediTriaje 2.0 — Generador y Gestión de Código QR de Emergencia (patient-emergency-qr.js)
 * Permite al paciente generar un token temporal criptográfico (15 min, max 3 lecturas),
 * con PIN opcional y selección de alcance clínico (ADR-010, §5.17, §5.18).
 */

import { api } from '../api.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

let activeCountdownInterval = null;

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
      options.second = '2-digit';
      options.hour12 = true;
    }
    return new Intl.DateTimeFormat('es-CO', options).format(d);
  } catch {
    return isoString;
  }
}

/**
 * Vista principal de Código QR de Emergencia para el Paciente
 * @param {HTMLElement} container
 */
export async function patientEmergencyQrView(container) {
  // Limpiar temporizador previo si existía
  if (activeCountdownInterval) {
    clearInterval(activeCountdownInterval);
    activeCountdownInterval = null;
  }

  container.innerHTML = `
    <div class="sg-section" style="padding-top: var(--space-4); padding-bottom: var(--space-12);">
      <!-- Navegación y encabezado -->
      <div class="flex items-center gap-2 mb-4">
        <a href="#/patient/dashboard" class="btn btn-ghost btn--sm" aria-label="Volver al panel del paciente">
          ${ui.icon('chevron-left', 'icon icon--sm')}
          <span>Volver al panel</span>
        </a>
      </div>

      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <h1 class="text-3xl font-bold mb-1 flex items-center gap-3">
            <span style="color: var(--danger); display: flex;">${ui.icon('shield', 'icon icon--lg')}</span>
            <span>Código QR y Resumen de Emergencia</span>
          </h1>
          <p class="text-muted text-sm" style="max-width: 70ch;">
            Genera un acceso temporal seguro para paramédicos y personal de urgencias en caso de emergencia médica o traslado.
          </p>
        </div>
      </div>

      <!-- Alerta informativa de seguridad (ADR-010) -->
      <div class="card mb-8" style="border-left: 4px solid var(--primary); background-color: var(--surface);">
        <div class="card-body flex gap-4 items-start">
          <div style="color: var(--primary); flex-shrink: 0; padding-top: 2px;">
            ${ui.icon('shield', 'icon icon--md')}
          </div>
          <div class="text-sm">
            <h2 class="font-semibold text-base mb-1" style="color: var(--text);">Privacidad y Seguridad de tu Resumen Clínico</h2>
            <ul style="margin: 0; padding-left: var(--space-4); color: var(--text-muted); line-height: 1.6;">
              <li><strong>Cero datos en el código:</strong> El código QR jamás contiene datos clínicos ni de identificación; únicamente transporta una URL web con un token cifrado de 256 bits.</li>
              <li><strong>Vigencia estricta:</strong> Cada código es válido durante exactamente <strong>15 minutos</strong> desde su generación.</li>
              <li><strong>Límite de lecturas:</strong> Permite un máximo de <strong>3 consultas</strong>; tras la tercera lectura o al vencer el tiempo, queda completamente inactivo.</li>
              <li><strong>PIN opcional:</strong> Puedes asignar un PIN de 4 dígitos para impedir lecturas no autorizadas en caso de escaneos accidentales.</li>
            </ul>
          </div>
        </div>
      </div>

      <!-- Contenedor del QR Activo (se oculta si no hay ninguno recién generado o activo) -->
      <div id="activeQrSection" class="mb-8" style="display: none;"></div>

      <div class="grid grid-cols-1 grid-cols-2-lg gap-8 mb-8">
        <!-- Formulario Generador -->
        <div class="card">
          <div class="card-header">
            <h2 class="card-title text-xl flex items-center gap-2">
              ${ui.icon('plus', 'icon icon--sm text-primary')}
              <span>Generar Nuevo Código QR</span>
            </h2>
            <p class="card-subtitle text-sm text-muted">Configura el alcance de datos clínicos que deseas compartir</p>
          </div>
          <div class="card-body">
            <form id="formGenerarQr" novalidate>
              <fieldset style="border: none; padding: 0; margin: 0 0 var(--space-5) 0;">
                <legend class="font-semibold text-sm mb-3" style="color: var(--text);">Alcance de información clínica incluida:</legend>
                
                <div class="flex flex-col gap-3">
                  <label class="flex items-center gap-3 cursor-pointer">
                    <input type="checkbox" id="chkAlergias" name="incluirAlergias" checked style="width: 18px; height: 18px; accent-color: var(--primary);" />
                    <div>
                      <span class="font-medium text-sm">Alergias e hipersensibilidades</span>
                      <p class="text-xs text-muted" style="margin: 0;">Sustancias registradas, severidad y tipo de reacción adversa.</p>
                    </div>
                  </label>

                  <label class="flex items-center gap-3 cursor-pointer">
                    <input type="checkbox" id="chkMedicamentos" name="incluirMedicamentos" checked style="width: 18px; height: 18px; accent-color: var(--primary);" />
                    <div>
                      <span class="font-medium text-sm">Medicamentos activos prescritos</span>
                      <p class="text-xs text-muted" style="margin: 0;">Prescripciones médicas con vigencia activa en tus recetas.</p>
                    </div>
                  </label>

                  <label class="flex items-center gap-3 cursor-pointer">
                    <input type="checkbox" id="chkAtenciones" name="incluirAtenciones" checked style="width: 18px; height: 18px; accent-color: var(--primary);" />
                    <div>
                      <span class="font-medium text-sm">Atenciones recientes y diagnósticos CIE-10</span>
                      <p class="text-xs text-muted" style="margin: 0;">Últimas 5 consultas clínicas con diagnósticos y especialidad.</p>
                    </div>
                  </label>

                  <label class="flex items-center gap-3 cursor-pointer">
                    <input type="checkbox" id="chkContacto" name="incluirContacto" checked style="width: 18px; height: 18px; accent-color: var(--primary);" />
                    <div>
                      <span class="font-medium text-sm">Datos de contacto de emergencia</span>
                      <p class="text-xs text-muted" style="margin: 0;">Teléfono y correo electrónico para comunicación inmediata.</p>
                    </div>
                  </label>
                </div>
              </fieldset>

              <!-- PIN opcional de 4 dígitos -->
              <div class="mb-6">
                <label for="qrPinInput" class="form-label font-semibold text-sm mb-1 block">
                  PIN numérico de seguridad de 4 dígitos (opcional):
                </label>
                <div style="max-width: 200px;">
                  <input
                    type="password"
                    id="qrPinInput"
                    name="pin"
                    class="form-input text-center font-mono text-lg tracking-widest"
                    maxlength="4"
                    pattern="[0-9]{4}"
                    placeholder="••••"
                    inputmode="numeric"
                    autocomplete="off"
                  />
                </div>
                <p class="text-xs text-muted mt-1">
                  Si defines un PIN, los paramédicos deberán digitarlo al escanear. Déjalo en blanco si prefieres acceso inmediato sin clave (ideal ante riesgo de pérdida de conciencia).
                </p>
              </div>

              <button type="submit" id="btnSubmitGenerar" class="btn btn-primary btn--lg w-full">
                ${ui.icon('shield', 'icon icon--md')}
                <span>Generar Código QR de Emergencia</span>
              </button>
            </form>
          </div>
        </div>

        <!-- Instrucciones y Recomendaciones -->
        <div class="card" style="background-color: var(--surface-2);">
          <div class="card-header">
            <h2 class="card-title text-xl flex items-center gap-2">
              ${ui.icon('info', 'icon icon--sm text-primary')}
              <span>¿Cómo utilizar este código?</span>
            </h2>
          </div>
          <div class="card-body text-sm flex flex-col gap-4 text-muted">
            <div class="flex items-start gap-3">
              <span class="badge badge--scheduled font-bold" style="border-radius: var(--radius-full); width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; padding: 0;">1</span>
              <div>
                <strong style="color: var(--text);">Genera el código antes de salir o trasladarte:</strong>
                <p style="margin: 2px 0 0 0;">Configura el alcance y presiona generar. Tendrás 15 minutos de disponibilidad activa.</p>
              </div>
            </div>

            <div class="flex items-start gap-3">
              <span class="badge badge--scheduled font-bold" style="border-radius: var(--radius-full); width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; padding: 0;">2</span>
              <div>
                <strong style="color: var(--text);">Muéstralo en la pantalla de tu móvil o imprímelo:</strong>
                <p style="margin: 2px 0 0 0;">Cualquier cámara de smartphone o lector de ambulancia podrá escanearlo directamente sin instalar ninguna app.</p>
              </div>
            </div>

            <div class="flex items-start gap-3">
              <span class="badge badge--scheduled font-bold" style="border-radius: var(--radius-full); width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; padding: 0;">3</span>
              <div>
                <strong style="color: var(--text);">Auditoría inmutable de cada consulta:</strong>
                <p style="margin: 2px 0 0 0;">Cada vez que un médico o paramédico abre tu resumen, el sistema descuenta una lectura y registra la hora e IP de origen para tu tranquilidad.</p>
              </div>
            </div>

            <div class="flex items-start gap-3">
              <span class="badge badge--danger font-bold" style="border-radius: var(--radius-full); width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; padding: 0;">4</span>
              <div>
                <strong style="color: var(--text);">Revocación instantánea:</strong>
                <p style="margin: 2px 0 0 0;">Si la emergencia fue superada o el código ya no se requiere, puedes revocarlo con un solo clic y quedará invalidado de inmediato.</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Tabla Historial de Accesos Generados -->
      <div class="card">
        <div class="card-header flex items-center justify-between">
          <h2 class="card-title text-xl flex items-center gap-2">
            ${ui.icon('clock', 'icon icon--sm text-primary')}
            <span>Historial de Códigos QR Generados</span>
          </h2>
          <button id="btnRecargarHistorial" class="btn btn-ghost btn--sm" title="Refrescar lista">
            <span>Actualizar</span>
          </button>
        </div>
        <div class="card-body" id="historialQrContainer">
          <div class="skeleton skeleton-card" style="height: 120px;"></div>
        </div>
      </div>
    </div>
  `;

  // Cargar el historial de tokens
  await cargarHistorial(container);

  // Manejar el envío del formulario
  const form = container.querySelector('#formGenerarQr');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btnSubmit = form.querySelector('#btnSubmitGenerar');
    const pinVal = form.qrPinInput.value.trim();

    if (pinVal && !/^[0-9]{4}$/.test(pinVal)) {
      ui.showToast('El PIN debe contener exactamente 4 dígitos numéricos.', 'danger');
      form.qrPinInput.focus();
      return;
    }

    const payload = {
      pin: pinVal || null,
      incluirAlergias: form.chkAlergias.checked,
      incluirMedicamentos: form.chkMedicamentos.checked,
      incluirAtenciones: form.chkAtenciones.checked,
      incluirContacto: form.chkContacto.checked
    };

    try {
      btnSubmit.disabled = true;
      btnSubmit.innerHTML = `<span>Generando código seguro...</span>`;

      const response = await api.post('/patients/me/emergency-qr', payload);
      ui.showToast('Código QR de emergencia generado exitosamente.', 'success');

      // Limpiar input PIN por seguridad
      form.qrPinInput.value = '';

      // Renderizar el QR Activo
      renderQrActivo(container, response);

      // Recargar el historial
      await cargarHistorial(container);

      // Scroll suave a la sección del QR
      document.getElementById('activeQrSection')?.scrollIntoView({ behavior: 'smooth' });
    } catch (err) {
      ui.showToast(err.message || 'Error al generar código QR de emergencia.', 'danger');
    } finally {
      btnSubmit.disabled = false;
      btnSubmit.innerHTML = `
        ${ui.icon('shield', 'icon icon--md')}
        <span>Generar Código QR de Emergencia</span>
      `;
    }
  });

  // Botón recargar historial
  container.querySelector('#btnRecargarHistorial')?.addEventListener('click', () => {
    cargarHistorial(container);
  });
}

/**
 * Renderiza el código QR activo recién generado con cuenta regresiva y botón de revocación
 * @param {HTMLElement} rootContainer
 * @param {Object} qrData
 */
function renderQrActivo(rootContainer, qrData) {
  const activeSection = rootContainer.querySelector('#activeQrSection');
  if (!activeSection) return;

  if (activeCountdownInterval) {
    clearInterval(activeCountdownInterval);
    activeCountdownInterval = null;
  }

  activeSection.style.display = 'block';

  // Construir URL completa accesible desde el navegador
  const fullUrl = qrData.qrUrl?.startsWith('http')
    ? qrData.qrUrl
    : `${window.location.origin}${window.location.pathname}#/emergency-summary/${qrData.token}`;

  const pinNotice = qrData.requierePin
    ? `<span class="badge badge--scheduled">Protegido con PIN de 4 dígitos</span>`
    : `<span class="badge badge--confirmed">Acceso directo (sin PIN)</span>`;

  activeSection.innerHTML = `
    <div class="card" style="border: 2px solid var(--primary); background: linear-gradient(180deg, var(--surface) 0%, var(--surface-2) 100%);">
      <div class="card-header flex flex-wrap items-center justify-between gap-4">
        <div class="flex items-center gap-3">
          <div style="background-color: var(--teal-50); color: var(--primary); padding: 8px; border-radius: var(--radius-md); display: flex;">
            ${ui.icon('shield', 'icon icon--md')}
          </div>
          <div>
            <h3 class="card-title text-xl font-bold">Código QR de Emergencia Activo</h3>
            <p class="card-subtitle text-xs text-muted">Válido para paramédicos y personal de urgencias</p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          ${pinNotice}
          <div id="countdownBadge" class="badge badge--danger text-sm font-bold flex items-center gap-1">
            ${ui.icon('clock', 'icon icon--sm')}
            <span id="countdownText">15:00 restante(s)</span>
          </div>
        </div>
      </div>

      <div class="card-body">
        <div class="grid grid-cols-1 grid-cols-2-md gap-8 items-center">
          <!-- Contenedor del QR Visual -->
          <div class="flex flex-col items-center justify-center p-4 bg-white rounded-lg shadow-sm" style="border: 1px solid var(--border); max-width: 280px; margin: 0 auto; width: 100%;">
            <div id="qrCanvasTarget" style="display: flex; justify-content: center; align-items: center; width: 220px; height: 220px;"></div>
            <p class="text-xs text-muted text-center mt-3 font-mono font-medium" style="color: #475569;">
              Escanear con cámara de smartphone
            </p>
          </div>

          <!-- Detalles del acceso y acciones -->
          <div class="flex flex-col gap-4">
            <div>
              <span class="text-xs font-semibold text-muted uppercase tracking-wider block mb-1">Dirección Web de Emergencia:</span>
              <div class="flex items-center gap-2">
                <input
                  type="text"
                  id="directUrlInput"
                  class="form-input text-xs font-mono"
                  readonly
                  value="${esc(fullUrl)}"
                  style="background-color: var(--surface);"
                />
                <button type="button" id="btnCopyUrl" class="btn btn-secondary btn--sm" title="Copiar enlace">
                  ${ui.icon('check', 'icon icon--sm')}
                  <span id="copyBtnText">Copiar</span>
                </button>
              </div>
            </div>

            <div class="text-sm text-muted">
              <div><strong>Lecturas disponibles:</strong> Máximo ${qrData.maxAccesos} consultas.</div>
              <div><strong>Expira a las:</strong> ${formatDate(qrData.expiraAt)}</div>
              <div><strong>Alcance:</strong> 
                ${qrData.incluirAlergias ? 'Alergias · ' : ''}
                ${qrData.incluirMedicamentos ? 'Medicamentos · ' : ''}
                ${qrData.incluirAtenciones ? 'Atenciones · ' : ''}
                ${qrData.incluirContacto ? 'Contacto' : ''}
              </div>
            </div>

            <div class="pt-2 flex flex-wrap gap-3">
              <a href="${esc(fullUrl)}" target="_blank" rel="noopener" class="btn btn-secondary btn--sm">
                <span>Abrir vista previa del resumen</span>
              </a>
              <button type="button" id="btnRevocarActivo" class="btn btn-danger btn--sm" data-id="${esc(qrData.publicId)}">
                ${ui.icon('x', 'icon icon--sm')}
                <span>Revocar este código de inmediato</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;

  // Renderizar el QR con la librería local
  const canvasTarget = activeSection.querySelector('#qrCanvasTarget');
  if (canvasTarget && typeof window.QRCode !== 'undefined') {
    canvasTarget.innerHTML = '';
    try {
      new window.QRCode(canvasTarget, {
        text: fullUrl,
        width: 220,
        height: 220,
        colorDark: '#0f172a',
        colorLight: '#ffffff',
        correctLevel: window.QRCode.CorrectLevel.M
      });
    } catch (err) {
      console.error('Error renderizando QRCode:', err);
      canvasTarget.innerHTML = `<p class="text-xs text-danger text-center">No fue posible renderizar el gráfico QR.</p>`;
    }
  } else if (canvasTarget) {
    canvasTarget.innerHTML = `<p class="text-xs text-muted text-center">Generando enlace seguro...</p>`;
  }

  // Copiar enlace al portapapeles
  const btnCopy = activeSection.querySelector('#btnCopyUrl');
  const copyBtnText = activeSection.querySelector('#copyBtnText');
  btnCopy?.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(fullUrl);
      copyBtnText.textContent = '¡Copiado!';
      ui.showToast('Enlace copiado al portapapeles.', 'info');
      setTimeout(() => {
        if (copyBtnText) copyBtnText.textContent = 'Copiar';
      }, 2500);
    } catch {
      ui.showToast('Selecciona y copia manualmente la URL.', 'info');
    }
  });

  // Temporizador regresivo dinámico
  const countdownText = activeSection.querySelector('#countdownText');
  const countdownBadge = activeSection.querySelector('#countdownBadge');
  const expiraMs = new Date(qrData.expiraAt).getTime();

  const updateCountdown = () => {
    const ahora = Date.now();
    const difSec = Math.max(0, Math.floor((expiraMs - ahora) / 1000));
    if (difSec <= 0) {
      if (countdownText) countdownText.textContent = 'EXPIRADO (00:00)';
      if (countdownBadge) {
        countdownBadge.className = 'badge badge--rescheduled text-sm font-bold flex items-center gap-1';
      }
      clearInterval(activeCountdownInterval);
      activeCountdownInterval = null;
      return;
    }
    const mins = Math.floor(difSec / 60);
    const secs = difSec % 60;
    const formatted = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
    if (countdownText) {
      countdownText.textContent = `${formatted} restante(s)`;
    }
  };

  updateCountdown();
  activeCountdownInterval = setInterval(updateCountdown, 1000);

  // Botón Revocar Código
  const btnRevocar = activeSection.querySelector('#btnRevocarActivo');
  btnRevocar?.addEventListener('click', () => {
    const publicId = btnRevocar.getAttribute('data-id');
    confirmarRevocacion(rootContainer, publicId);
  });
}

/**
 * Consulta y carga el historial de accesos QR del paciente
 * @param {HTMLElement} rootContainer
 */
async function cargarHistorial(rootContainer) {
  const container = rootContainer.querySelector('#historialQrContainer');
  if (!container) return;

  try {
    const accesos = await api.get('/patients/me/emergency-qr');
    if (!accesos || accesos.length === 0) {
      container.innerHTML = `
        <div class="empty-state text-center py-6">
          <p class="text-sm text-muted">Aún no has generado ningún código QR de emergencia.</p>
        </div>
      `;
      return;
    }

    container.innerHTML = `
      <div class="table-container" style="overflow-x: auto;">
        <table class="table" style="width: 100%;">
          <thead>
            <tr>
              <th>Fecha de Creación</th>
              <th>Estado</th>
              <th>Lecturas</th>
              <th>PIN</th>
              <th>Alcance Autorizado</th>
              <th>Vencimiento</th>
              <th style="text-align: right;">Acciones</th>
            </tr>
          </thead>
          <tbody>
            ${accesos.map(a => {
              let badgeClass = 'badge--confirmed';
              if (a.estado === 'EXPIRADO') badgeClass = 'badge--scheduled';
              if (a.estado === 'AGOTADO') badgeClass = 'badge--rescheduled';
              if (a.estado === 'REVOCADO') badgeClass = 'badge--danger';

              const alcanceTags = [];
              if (a.incluirAlergias) alcanceTags.push('Alergias');
              if (a.incluirMedicamentos) alcanceTags.push('Medicamentos');
              if (a.incluirAtenciones) alcanceTags.push('Atenciones');
              if (a.incluirContacto) alcanceTags.push('Contacto');

              const puedeRevocar = a.estado === 'ACTIVO';

              return `
                <tr>
                  <td class="font-medium text-xs">${formatDate(a.createdAt)}</td>
                  <td><span class="badge ${badgeClass}">${esc(a.estado)}</span></td>
                  <td class="text-xs font-mono">${a.accesosRealizados} / ${a.maxAccesos}</td>
                  <td class="text-xs">${a.requierePin ? 'Sí (4 dígitos)' : 'No (Libre)'}</td>
                  <td class="text-xs text-muted">${alcanceTags.join(', ') || 'Básico'}</td>
                  <td class="text-xs text-muted">${formatDate(a.expiraAt)}</td>
                  <td style="text-align: right;">
                    ${puedeRevocar ? `
                      <button type="button" class="btn btn-ghost btn--sm text-danger btnRevocarFila" data-id="${esc(a.publicId)}" title="Revocar acceso">
                        ${ui.icon('x', 'icon icon--sm')}
                        <span>Revocar</span>
                      </button>
                    ` : `<span class="text-xs text-muted">—</span>`}
                  </td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
    `;

    // Asignar listeners a botones de revocación en la tabla
    container.querySelectorAll('.btnRevocarFila').forEach(btn => {
      btn.addEventListener('click', () => {
        const publicId = btn.getAttribute('data-id');
        confirmarRevocacion(rootContainer, publicId);
      });
    });
  } catch (err) {
    container.innerHTML = `
      <div class="empty-state text-center py-4">
        <p class="text-sm text-danger">No se pudo cargar el historial de códigos QR: ${esc(err.message)}</p>
      </div>
    `;
  }
}

/**
 * Solicita confirmación antes de revocar un token temporal
 * @param {HTMLElement} rootContainer
 * @param {string} publicId
 */
function confirmarRevocacion(rootContainer, publicId) {
  ui.showModal({
    title: 'Revocar Código de Emergencia',
    message: '¿Estás seguro de que deseas revocar este código QR? Cualquier paramédico o médico que intente escanearlo recibirá un aviso de acceso inválido y no podrá consultar tus datos.',
    confirmText: 'Sí, revocar ahora',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      try {
        await api.patch(`/patients/me/emergency-qr/${publicId}/revoke`);
        ui.showToast('Acceso QR revocado exitosamente.', 'info');

        // Si el QR activo correspondía a este publicId, ocultar o refrescar
        const activeSection = rootContainer.querySelector('#activeQrSection');
        if (activeSection) {
          activeSection.style.display = 'none';
          if (activeCountdownInterval) {
            clearInterval(activeCountdownInterval);
            activeCountdownInterval = null;
          }
        }

        // Recargar historial
        await cargarHistorial(rootContainer);
      } catch (err) {
        ui.showToast(err.message || 'Error al revocar acceso QR.', 'danger');
      }
    }
  });
}
