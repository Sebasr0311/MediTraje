/**
 * MediTriaje 2.0 — Vistas de Autenticación (auth-views.js)
 * Manejo de Login con toggle de contraseña y Registro de Paciente en 2 pasos accesibles (M8.2a).
 */

import { auth } from '../auth.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

/**
 * Renderiza la vista de Inicio de Sesión
 * @param {HTMLElement} container Contenedor principal
 * @param {Object} context Contexto de la ruta (incluyendo queryParams)
 */
export function loginView(container, { queryParams } = {}) {
  container.innerHTML = `
    <div class="container-narrow" style="padding-top: var(--space-8); padding-bottom: var(--space-8);">
      <div class="card" style="padding: var(--space-8);">
        <div class="text-center mb-6">
          <div class="empty-state-icon" style="margin: 0 auto var(--space-3) auto; background-color: var(--teal-50); color: var(--primary);">
            ${ui.icon('shield', 'icon icon--lg')}
          </div>
          <h1 class="text-2xl font-bold mb-1">Iniciar Sesión</h1>
          <p class="text-sm text-muted">Ingresa tus credenciales para acceder a MediTriaje</p>
        </div>

        <form id="formLogin" novalidate>
          <div id="loginAlertContainer" aria-live="polite"></div>

          <div class="form-group mb-4">
            <label for="loginEmail" class="form-label">
              Correo electrónico <span class="required" aria-hidden="true">*</span>
            </label>
            <input 
              type="email" 
              id="loginEmail" 
              name="email"
              class="form-input" 
              placeholder="ejemplo@correo.com" 
              required 
              autocomplete="email"
              aria-required="true"
            >
            <span class="form-error" id="emailError" style="display: none;" role="alert"></span>
          </div>

          <div class="form-group mb-4">
            <label for="loginPassword" class="form-label">
              Contraseña <span class="required" aria-hidden="true">*</span>
            </label>
            <div style="position: relative; display: flex; align-items: center;">
              <input 
                type="password" 
                id="loginPassword" 
                name="password"
                class="form-input" 
                placeholder="••••••••••" 
                required 
                autocomplete="current-password"
                aria-required="true"
                style="padding-right: 48px;"
              >
              <button 
                type="button" 
                id="btnTogglePassword" 
                class="btn btn-ghost btn--sm" 
                style="position: absolute; right: 4px; padding: 6px 10px; color: var(--text-muted);" 
                aria-label="Mostrar contraseña"
                title="Mostrar u ocultar contraseña"
              >
                ${ui.icon('search', 'icon icon--sm')}
              </button>
            </div>
            <span class="form-error" id="passwordError" style="display: none;" role="alert"></span>
          </div>

          <div class="flex justify-end mb-4">
            <a href="#/forgot-password" class="text-xs text-primary font-semibold hover:underline">¿Olvidaste tu contraseña?</a>
          </div>

          <button type="submit" id="btnLoginSubmit" class="btn btn-primary w-full mt-2">
            <span>Ingresar</span>
          </button>
        </form>

        <div class="text-center mt-6 pt-4 border-top">
          <p class="text-sm text-muted">
            ¿Eres paciente nuevo?
            <a href="#/register" class="font-semibold text-primary" style="margin-left: 4px;">Regístrate aquí</a>
          </p>
        </div>
      </div>
    </div>
  `;

  // Toggle de visibilidad de contraseña
  const passwordInput = document.getElementById('loginPassword');
  const toggleBtn = document.getElementById('btnTogglePassword');
  let isPasswordVisible = false;

  toggleBtn.addEventListener('click', () => {
    isPasswordVisible = !isPasswordVisible;
    passwordInput.type = isPasswordVisible ? 'text' : 'password';
    toggleBtn.setAttribute('aria-label', isPasswordVisible ? 'Ocultar contraseña' : 'Mostrar contraseña');
    toggleBtn.innerHTML = ui.icon(isPasswordVisible ? 'x' : 'search', 'icon icon--sm');
  });

  // Manejo del envío del formulario
  const form = document.getElementById('formLogin');
  const btnSubmit = document.getElementById('btnLoginSubmit');
  const alertBox = document.getElementById('loginAlertContainer');
  const emailInput = document.getElementById('loginEmail');
  const emailError = document.getElementById('emailError');
  const passwordError = document.getElementById('passwordError');

  const clearErrors = () => {
    emailInput.classList.remove('has-error');
    emailInput.removeAttribute('aria-invalid');
    emailInput.removeAttribute('aria-describedby');
    emailError.style.display = 'none';
    emailError.textContent = '';

    passwordInput.classList.remove('has-error');
    passwordInput.removeAttribute('aria-invalid');
    passwordInput.removeAttribute('aria-describedby');
    passwordError.style.display = 'none';
    passwordError.textContent = '';
    alertBox.innerHTML = '';
  };

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearErrors();

    const email = emailInput.value.trim();
    const password = passwordInput.value;
    let hasClientError = false;

    if (!email) {
      emailInput.classList.add('has-error');
      emailInput.setAttribute('aria-invalid', 'true');
      emailInput.setAttribute('aria-describedby', 'emailError');
      emailError.textContent = 'Por favor ingresa tu correo electrónico.';
      emailError.style.display = 'block';
      hasClientError = true;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      emailInput.classList.add('has-error');
      emailInput.setAttribute('aria-invalid', 'true');
      emailInput.setAttribute('aria-describedby', 'emailError');
      emailError.textContent = 'Formato de correo no válido.';
      emailError.style.display = 'block';
      hasClientError = true;
    }

    if (!password) {
      passwordInput.classList.add('has-error');
      passwordInput.setAttribute('aria-invalid', 'true');
      passwordInput.setAttribute('aria-describedby', 'passwordError');
      passwordError.textContent = 'Por favor ingresa tu contraseña.';
      passwordError.style.display = 'block';
      hasClientError = true;
    }

    if (hasClientError) {
      (emailInput.classList.contains('has-error') ? emailInput : passwordInput).focus();
      return;
    }

    ui.setButtonLoading(btnSubmit, true);

    try {
      const session = await auth.login(email, password);

      if (session && session.mfaRequerido) {
        ui.setButtonLoading(btnSubmit, false);
        renderMfaChallengeStep(container, session.mfaChallengeToken, email, queryParams);
        return;
      }

      ui.showToast('Bienvenido a MediTriaje.', 'success');

      const redirect = queryParams?.get('redirect');
      if (redirect) {
        router.navigate(decodeURIComponent(redirect));
      } else {
        router.redirectToHome();
      }
    } catch (err) {
      let errorMsg = 'Correo electrónico o contraseña incorrectos.';
      let isWarning = false;

      // Detectar cuenta bloqueada (código 423 o mensaje de bloqueo de 15 minutos)
      if (err.status === 423 || (err.message && err.message.toLowerCase().includes('bloquead'))) {
        errorMsg = 'Cuenta bloqueada temporalmente por 15 minutos debido a 5 intentos fallidos consecutivos. Por favor espera antes de volver a intentar.';
        isWarning = true;
      } else if (err.message && err.status !== 401) {
        errorMsg = err.message;
      }

      alertBox.innerHTML = `
        <div class="alert ${isWarning ? 'alert--warning' : 'alert--danger'} mb-4" role="alert">
          ${ui.icon(isWarning ? 'alert-triangle' : 'alert-circle', 'icon alert-icon')}
          <div class="alert-content">
            <div class="alert-title">${isWarning ? 'Cuenta bloqueada' : 'No fue posible ingresar'}</div>
            <div>${errorMsg}</div>
          </div>
        </div>
      `;
    } finally {
      ui.setButtonLoading(btnSubmit, false);
    }
  });
}

/**
 * Renderiza el paso de desafío MFA dentro de la pantalla de Login (ADR-014, F2.1.4, F2.1.5).
 */
function renderMfaChallengeStep(container, challengeToken, email, queryParams) {
  container.innerHTML = `
    <div class="container-narrow" style="padding-top: var(--space-8); padding-bottom: var(--space-8);">
      <div class="card" style="padding: var(--space-8);">
        <div class="text-center mb-6">
          <div class="empty-state-icon" style="margin: 0 auto var(--space-3) auto; background-color: var(--teal-50); color: var(--primary);">
            ${ui.icon('shield', 'icon icon--lg')}
          </div>
          <h1 class="text-2xl font-bold mb-1">Verificación en Dos Pasos</h1>
          <p class="text-sm text-muted">Ingresa el código para <strong>${esc(email)}</strong></p>
        </div>

        <form id="formMfaChallenge" novalidate>
          <div id="mfaAlertContainer" aria-live="polite"></div>

          <div class="form-group mb-4 text-center">
            <label for="mfaChallengeCode" class="form-label font-bold mb-2">
              Código de autenticación o de respaldo <span class="required" aria-hidden="true">*</span>
            </label>
            <input 
              type="text" 
              id="mfaChallengeCode" 
              name="codigo"
              class="form-input text-center" 
              placeholder="123456 o 1234-5678" 
              required 
              autocomplete="one-time-code"
              aria-required="true"
              style="font-family: monospace; font-size: 1.5rem; letter-spacing: 0.2em; max-width: 260px; margin: 0 auto; display: block;"
            >
            <span class="form-error text-center mt-1" id="mfaChallengeError" style="display: none;" role="alert"></span>
            <p class="text-xs text-muted mt-2">
              Ingresa el código de 6 dígitos de tu app autenticadora o uno de tus códigos de respaldo uniuso.
            </p>
          </div>

          <button type="submit" id="btnSubmitMfaChallenge" class="btn btn-primary w-full mt-4">
            ${ui.icon('shield', 'icon icon--sm')}
            <span>Verificar e Ingresar</span>
          </button>

          <button type="button" id="btnCancelMfa" class="btn btn-ghost w-full mt-2 text-muted">
            <span>Cancelar y volver al inicio</span>
          </button>
        </form>
      </div>
    </div>
  `;

  const mfaForm = container.querySelector('#formMfaChallenge');
  const mfaInput = container.querySelector('#mfaChallengeCode');
  const mfaError = container.querySelector('#mfaChallengeError');
  const mfaAlert = container.querySelector('#mfaAlertContainer');
  const btnSubmit = container.querySelector('#btnSubmitMfaChallenge');
  const btnCancel = container.querySelector('#btnCancelMfa');

  setTimeout(() => mfaInput?.focus(), 150);

  btnCancel?.addEventListener('click', () => {
    loginView(container, { queryParams });
  });

  mfaForm?.addEventListener('submit', async (e) => {
    e.preventDefault();
    mfaError.style.display = 'none';
    mfaError.textContent = '';
    mfaAlert.innerHTML = '';
    mfaInput.classList.remove('has-error');

    const codigo = mfaInput.value.trim();
    if (!codigo) {
      mfaInput.classList.add('has-error');
      mfaError.textContent = 'Por favor ingresa tu código de verificación.';
      mfaError.style.display = 'block';
      mfaInput.focus();
      return;
    }

    ui.setButtonLoading(btnSubmit, true);

    try {
      await auth.authenticateMfa(challengeToken, codigo);
      ui.showToast('Bienvenido a MediTriaje.', 'success');

      const redirect = queryParams?.get('redirect');
      if (redirect) {
        router.navigate(decodeURIComponent(redirect));
      } else {
        router.redirectToHome();
      }
    } catch (err) {
      ui.setButtonLoading(btnSubmit, false);
      mfaInput.classList.add('has-error');
      mfaAlert.innerHTML = `
        <div class="alert alert--danger mb-4">
          ${ui.icon('alert-circle', 'icon alert-icon')}
          <div class="alert-content">
            <p class="m-0">${esc(err.message || 'Código de verificación incorrecto o expirado.')}</p>
          </div>
        </div>
      `;
      mfaInput.select();
    }
  });
}

/**
 * Texto oficial del consentimiento informado v1.0 (Ley 1581 de 2012)
 */
export const TEXTO_CONSENTIMIENTO_V1 = `
En cumplimiento de la Ley Estatutaria 1581 de 2012 de Protección de Datos Personales y el Decreto 1377 de 2013 de la República de Colombia, autorizo de manera libre, previa, expresa e informada a MediTriaje 2.0 para recolectar, almacenar, usar y tratar mis datos personales y de salud (categoría sensible).

Finalidades asistenciales y clínicas:
1. Valoración y clasificación de riesgo mediante el sistema automatizado de triaje clínico.
2. Agendamiento, gestión, confirmación y recordatorio de citas médicas con profesionales habilitados.
3. Registro de atenciones médicas y apertura de historia clínica electrónica inmutable con trazabilidad auditable.
4. Generación, expedición y consulta de recetas médicas electrónicas con trazabilidad farmacológica.

Derechos del titular:
Conocer, actualizar, rectificar y suprimir mis datos personales cuando proceda legalmente, así como revocar la presente autorización frente a tratamientos no obligatorios por mandato legal asistencial.
`;

/**
 * Renderiza la vista de Registro de Paciente en 2 pasos
 * @param {HTMLElement} container Contenedor principal
 */
export function registerView(container) {
  container.innerHTML = `
    <div class="container-narrow" style="padding-top: var(--space-6); padding-bottom: var(--space-8);">
      <div class="card" style="padding: var(--space-8);">
        <div class="text-center mb-6">
          <span class="badge badge--scheduled mb-2">Nuevo Paciente</span>
          <h1 class="text-2xl font-bold mb-1">Crear Cuenta</h1>
          <p class="text-sm text-muted">Accede a triaje médico, citas prioritarias e historia clínica</p>
        </div>

        <!-- Indicador de pasos -->
        <div class="flex items-center justify-between mb-6" style="border-bottom: 1px solid var(--border); padding-bottom: var(--space-4);">
          <div id="stepTab1" class="flex items-center gap-2 font-medium" style="color: var(--primary);">
            <span class="badge badge--confirmed" style="border-radius: var(--radius-full); width: 24px; height: 24px; display: inline-flex; align-items: center; justify-content: center; padding: 0;">1</span>
            <span>Datos Personales</span>
          </div>
          <div style="height: 2px; flex: 1; margin: 0 var(--space-3); background-color: var(--border);"></div>
          <div id="stepTab2" class="flex items-center gap-2 font-medium text-muted">
            <span class="badge badge--neutral" style="border-radius: var(--radius-full); width: 24px; height: 24px; display: inline-flex; align-items: center; justify-content: center; padding: 0;">2</span>
            <span>Cuenta y Consentimiento</span>
          </div>
        </div>

        <form id="formRegister" novalidate>
          <div id="registerAlertContainer" aria-live="polite"></div>

          <!-- PASO 1: Datos Personales -->
          <div id="step1Container">
            <div class="grid grid-cols-1 grid-cols-2-md gap-4">
              <div class="form-group mb-4">
                <label for="regTipoDoc" class="form-label">
                  Tipo de documento <span class="required" aria-hidden="true">*</span>
                </label>
                <select id="regTipoDoc" class="form-select" required aria-required="true">
                  <option value="CC" selected>Cédula de Ciudadanía (CC)</option>
                  <option value="TI">Tarjeta de Identidad (TI)</option>
                  <option value="CE">Cédula de Extranjería (CE)</option>
                  <option value="PA">Pasaporte (PA)</option>
                </select>
              </div>

              <div class="form-group mb-4">
                <label for="regNumDoc" class="form-label">
                  Número de documento <span class="required" aria-hidden="true">*</span>
                </label>
                <input 
                  type="text" 
                  id="regNumDoc" 
                  class="form-input" 
                  placeholder="Ej. 1098765432" 
                  required 
                  aria-required="true"
                >
                <span class="form-error" id="numDocError" style="display: none;" role="alert"></span>
              </div>
            </div>

            <div class="grid grid-cols-1 grid-cols-2-md gap-4">
              <div class="form-group mb-4">
                <label for="regNombres" class="form-label">
                  Nombres <span class="required" aria-hidden="true">*</span>
                </label>
                <input 
                  type="text" 
                  id="regNombres" 
                  class="form-input" 
                  placeholder="Ej. Carlos" 
                  required 
                  autocomplete="given-name"
                  aria-required="true"
                >
                <span class="form-error" id="nombresError" style="display: none;" role="alert"></span>
              </div>

              <div class="form-group mb-4">
                <label for="regApellidos" class="form-label">
                  Apellidos <span class="required" aria-hidden="true">*</span>
                </label>
                <input 
                  type="text" 
                  id="regApellidos" 
                  class="form-input" 
                  placeholder="Ej. Pérez Gómez" 
                  required 
                  autocomplete="family-name"
                  aria-required="true"
                >
                <span class="form-error" id="apellidosError" style="display: none;" role="alert"></span>
              </div>
            </div>

            <div class="grid grid-cols-1 grid-cols-2-md gap-4">
              <div class="form-group mb-4">
                <label for="regFechaNac" class="form-label">
                  Fecha de nacimiento <span class="required" aria-hidden="true">*</span>
                </label>
                <input 
                  type="date" 
                  id="regFechaNac" 
                  class="form-input" 
                  required 
                  autocomplete="bday"
                  aria-required="true"
                >
                <span class="form-error" id="fechaNacError" style="display: none;" role="alert"></span>
              </div>

              <div class="form-group mb-4">
                <label for="regTelefono" class="form-label">Teléfono móvil</label>
                <input 
                  type="tel" 
                  id="regTelefono" 
                  class="form-input" 
                  placeholder="3001234567" 
                  autocomplete="tel"
                >
                <span class="form-error" id="telefonoError" style="display: none;" role="alert"></span>
              </div>
            </div>

            <button type="button" id="btnNextStep" class="btn btn-primary w-full mt-3">
              <span>Continuar al Paso 2</span>
              ${ui.icon('arrow-right', 'icon icon--sm')}
            </button>
          </div>

          <!-- PASO 2: Cuenta y Consentimiento -->
          <div id="step2Container" style="display: none;">
            <div class="form-group mb-4">
              <label for="regEmail" class="form-label">
                Correo electrónico <span class="required" aria-hidden="true">*</span>
              </label>
              <input 
                type="email" 
                id="regEmail" 
                class="form-input" 
                placeholder="ejemplo@correo.com" 
                required 
                autocomplete="email"
                aria-required="true"
              >
              <span class="form-error" id="regEmailError" style="display: none;" role="alert"></span>
            </div>

            <div class="form-group mb-4">
              <label for="regPassword" class="form-label">
                Contraseña <span class="required" aria-hidden="true">*</span>
              </label>
              <div style="position: relative; display: flex; align-items: center;">
                <input 
                  type="password" 
                  id="regPassword" 
                  class="form-input" 
                  placeholder="Mínimo 10 caracteres" 
                  required 
                  autocomplete="new-password"
                  aria-required="true"
                  style="padding-right: 48px;"
                >
                <button 
                  type="button" 
                  id="btnToggleRegPassword" 
                  class="btn btn-ghost btn--sm" 
                  style="position: absolute; right: 4px; padding: 6px 10px; color: var(--text-muted);" 
                  aria-label="Mostrar contraseña"
                  title="Mostrar u ocultar contraseña"
                >
                  ${ui.icon('search', 'icon icon--sm')}
                </button>
              </div>
              <span class="form-help">Debe incluir al menos 10 caracteres.</span>
              <span class="form-error" id="regPasswordError" style="display: none;" role="alert"></span>
            </div>

            <div class="form-group mb-4">
              <label for="regPasswordConfirm" class="form-label">
                Confirmar contraseña <span class="required" aria-hidden="true">*</span>
              </label>
              <input 
                type="password" 
                id="regPasswordConfirm" 
                class="form-input" 
                placeholder="Repite tu contraseña" 
                required 
                autocomplete="new-password"
                aria-required="true"
              >
              <span class="form-error" id="regPasswordConfirmError" style="display: none;" role="alert"></span>
            </div>

            <!-- Consentimiento informado explícito (Ley 1581) -->
            <div class="card mb-4" style="background-color: var(--primary-soft); border-color: var(--teal-200); padding: var(--space-4);">
              <div class="form-check">
                <input type="checkbox" id="regConsentimiento" required aria-required="true">
                <label for="regConsentimiento" class="form-check-label text-sm" style="line-height: 1.5;">
                  Acepto el tratamiento de datos de salud y los términos asistenciales bajo la <strong>Ley 1581 de 2012</strong> (versión v1.0).
                </label>
              </div>
              <div class="mt-2 text-right">
                <button type="button" id="btnViewConsentModal" class="btn btn-ghost btn--sm text-primary font-medium" style="padding: 2px 6px;">
                  ${ui.icon('file-text', 'icon icon--sm')}
                  <span>Leer consentimiento completo</span>
                </button>
              </div>
              <span class="form-error" id="consentimientoError" style="display: none; margin-top: 4px;" role="alert"></span>
            </div>

            <div class="flex gap-3">
              <button type="button" id="btnPrevStep" class="btn btn-secondary flex-1">
                ${ui.icon('arrow-left', 'icon icon--sm')}
                <span>Volver</span>
              </button>
              <button type="submit" id="btnRegisterSubmit" class="btn btn-primary flex-2">
                <span>Crear mi cuenta</span>
              </button>
            </div>
          </div>
        </form>

        <div class="text-center mt-6 pt-4 border-top">
          <p class="text-sm text-muted">
            ¿Ya tienes una cuenta?
            <a href="#/login" class="font-semibold text-primary" style="margin-left: 4px;">Inicia sesión</a>
          </p>
        </div>
      </div>
    </div>
  `;

  // Variables de control de pasos
  const step1 = document.getElementById('step1Container');
  const step2 = document.getElementById('step2Container');
  const stepTab1 = document.getElementById('stepTab1');
  const stepTab2 = document.getElementById('stepTab2');
  const btnNext = document.getElementById('btnNextStep');
  const btnPrev = document.getElementById('btnPrevStep');
  const form = document.getElementById('formRegister');
  const btnSubmit = document.getElementById('btnRegisterSubmit');
  const alertBox = document.getElementById('registerAlertContainer');

  // Campos paso 1
  const tipoDoc = document.getElementById('regTipoDoc');
  const numDoc = document.getElementById('regNumDoc');
  const nombres = document.getElementById('regNombres');
  const apellidos = document.getElementById('regApellidos');
  const fechaNac = document.getElementById('regFechaNac');
  const telefono = document.getElementById('regTelefono');

  const numDocError = document.getElementById('numDocError');
  const nombresError = document.getElementById('nombresError');
  const apellidosError = document.getElementById('apellidosError');
  const fechaNacError = document.getElementById('fechaNacError');
  const telefonoError = document.getElementById('telefonoError');

  // Campos paso 2
  const email = document.getElementById('regEmail');
  const password = document.getElementById('regPassword');
  const passwordConfirm = document.getElementById('regPasswordConfirm');
  const consentimiento = document.getElementById('regConsentimiento');

  const regEmailError = document.getElementById('regEmailError');
  const regPasswordError = document.getElementById('regPasswordError');
  const regPasswordConfirmError = document.getElementById('regPasswordConfirmError');
  const consentimientoError = document.getElementById('consentimientoError');

  // Modal para ver consentimiento completo
  document.getElementById('btnViewConsentModal').addEventListener('click', () => {
    ui.showModal({
      title: 'Consentimiento Informado (v1.0)',
      message: `
        <div style="max-height: 260px; overflow-y: auto; text-align: left; font-size: var(--text-sm); line-height: 1.6; white-space: pre-line; padding: var(--space-2); background: var(--surface-2); border-radius: var(--radius-md);">
          ${TEXTO_CONSENTIMIENTO_V1.trim()}
        </div>
      `,
      confirmText: 'Entendido y cerrar',
      cancelText: ''
    });
  });

  // Toggle de visibilidad de contraseña en registro
  const toggleRegPass = document.getElementById('btnToggleRegPassword');
  let isRegPassVisible = false;
  toggleRegPass.addEventListener('click', () => {
    isRegPassVisible = !isRegPassVisible;
    password.type = isRegPassVisible ? 'text' : 'password';
    toggleRegPass.setAttribute('aria-label', isRegPassVisible ? 'Ocultar contraseña' : 'Mostrar contraseña');
    toggleRegPass.innerHTML = ui.icon(isRegPassVisible ? 'x' : 'search', 'icon icon--sm');
  });

  // Validaciones del Paso 1
  function validateStep1() {
    let isValid = true;
    alertBox.innerHTML = '';

    // Número de documento
    numDoc.classList.remove('has-error');
    numDoc.removeAttribute('aria-invalid');
    numDocError.style.display = 'none';
    if (!numDoc.value.trim()) {
      numDoc.classList.add('has-error');
      numDoc.setAttribute('aria-invalid', 'true');
      numDoc.setAttribute('aria-describedby', 'numDocError');
      numDocError.textContent = 'El número de documento es obligatorio.';
      numDocError.style.display = 'block';
      isValid = false;
    } else if (numDoc.value.trim().length < 5) {
      numDoc.classList.add('has-error');
      numDoc.setAttribute('aria-invalid', 'true');
      numDoc.setAttribute('aria-describedby', 'numDocError');
      numDocError.textContent = 'El documento debe tener al menos 5 caracteres.';
      numDocError.style.display = 'block';
      isValid = false;
    }

    // Nombres
    nombres.classList.remove('has-error');
    nombres.removeAttribute('aria-invalid');
    nombresError.style.display = 'none';
    if (!nombres.value.trim()) {
      nombres.classList.add('has-error');
      nombres.setAttribute('aria-invalid', 'true');
      nombres.setAttribute('aria-describedby', 'nombresError');
      nombresError.textContent = 'Los nombres son obligatorios.';
      nombresError.style.display = 'block';
      isValid = false;
    }

    // Apellidos
    apellidos.classList.remove('has-error');
    apellidos.removeAttribute('aria-invalid');
    apellidosError.style.display = 'none';
    if (!apellidos.value.trim()) {
      apellidos.classList.add('has-error');
      apellidos.setAttribute('aria-invalid', 'true');
      apellidos.setAttribute('aria-describedby', 'apellidosError');
      apellidosError.textContent = 'Los apellidos son obligatorios.';
      apellidosError.style.display = 'block';
      isValid = false;
    }

    // Fecha de nacimiento
    fechaNac.classList.remove('has-error');
    fechaNac.removeAttribute('aria-invalid');
    fechaNacError.style.display = 'none';
    if (!fechaNac.value) {
      fechaNac.classList.add('has-error');
      fechaNac.setAttribute('aria-invalid', 'true');
      fechaNac.setAttribute('aria-describedby', 'fechaNacError');
      fechaNacError.textContent = 'La fecha de nacimiento es obligatoria.';
      fechaNacError.style.display = 'block';
      isValid = false;
    } else {
      const selected = new Date(fechaNac.value);
      const today = new Date();
      if (selected >= today) {
        fechaNac.classList.add('has-error');
        fechaNac.setAttribute('aria-invalid', 'true');
        fechaNac.setAttribute('aria-describedby', 'fechaNacError');
        fechaNacError.textContent = 'La fecha de nacimiento no puede ser futura.';
        fechaNacError.style.display = 'block';
        isValid = false;
      }
    }

    return isValid;
  }

  // Navegación Paso 1 -> Paso 2
  btnNext.addEventListener('click', () => {
    if (!validateStep1()) return;

    step1.style.display = 'none';
    step2.style.display = 'block';

    stepTab1.classList.replace('text-primary', 'text-muted');
    stepTab1.querySelector('.badge').className = 'badge badge--neutral';
    stepTab2.classList.replace('text-muted', 'text-primary');
    stepTab2.querySelector('.badge').className = 'badge badge--confirmed';

    email.focus();
  });

  // Navegación Paso 2 -> Paso 1
  btnPrev.addEventListener('click', () => {
    step2.style.display = 'none';
    step1.style.display = 'block';

    stepTab2.classList.replace('text-primary', 'text-muted');
    stepTab2.querySelector('.badge').className = 'badge badge--neutral';
    stepTab1.classList.replace('text-muted', 'text-primary');
    stepTab1.querySelector('.badge').className = 'badge badge--confirmed';
  });

  // Enviar formulario (Paso 2)
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    alertBox.innerHTML = '';

    // Validar paso 1 por seguridad
    if (!validateStep1()) {
      btnPrev.click();
      return;
    }

    let isValidStep2 = true;

    // Email
    regEmailError.style.display = 'none';
    email.classList.remove('has-error');
    if (!email.value.trim()) {
      email.classList.add('has-error');
      email.setAttribute('aria-invalid', 'true');
      email.setAttribute('aria-describedby', 'regEmailError');
      regEmailError.textContent = 'El correo electrónico es obligatorio.';
      regEmailError.style.display = 'block';
      isValidStep2 = false;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) {
      email.classList.add('has-error');
      email.setAttribute('aria-invalid', 'true');
      email.setAttribute('aria-describedby', 'regEmailError');
      regEmailError.textContent = 'Formato de correo inválido.';
      regEmailError.style.display = 'block';
      isValidStep2 = false;
    }

    // Contraseña
    regPasswordError.style.display = 'none';
    password.classList.remove('has-error');
    if (!password.value) {
      password.classList.add('has-error');
      password.setAttribute('aria-invalid', 'true');
      password.setAttribute('aria-describedby', 'regPasswordError');
      regPasswordError.textContent = 'La contraseña es obligatoria.';
      regPasswordError.style.display = 'block';
      isValidStep2 = false;
    } else if (password.value.length < 10) {
      password.classList.add('has-error');
      password.setAttribute('aria-invalid', 'true');
      password.setAttribute('aria-describedby', 'regPasswordError');
      regPasswordError.textContent = 'La contraseña debe tener al menos 10 caracteres.';
      regPasswordError.style.display = 'block';
      isValidStep2 = false;
    }

    // Confirmación de contraseña
    regPasswordConfirmError.style.display = 'none';
    passwordConfirm.classList.remove('has-error');
    if (password.value && password.value !== passwordConfirm.value) {
      passwordConfirm.classList.add('has-error');
      passwordConfirm.setAttribute('aria-invalid', 'true');
      passwordConfirm.setAttribute('aria-describedby', 'regPasswordConfirmError');
      regPasswordConfirmError.textContent = 'Las contraseñas no coinciden.';
      regPasswordConfirmError.style.display = 'block';
      isValidStep2 = false;
    }

    // Consentimiento informado no premarcado
    consentimientoError.style.display = 'none';
    if (!consentimiento.checked) {
      consentimientoError.textContent = 'Debes aceptar los términos y tratamiento de datos para registrarte.';
      consentimientoError.style.display = 'block';
      isValidStep2 = false;
    }

    if (!isValidStep2) return;

    ui.setButtonLoading(btnSubmit, true);

    try {
      await auth.register({
        tipoDocumento: tipoDoc.value,
        numeroDocumento: numDoc.value.trim(),
        nombres: nombres.value.trim(),
        apellidos: apellidos.value.trim(),
        fechaNacimiento: fechaNac.value,
        telefono: telefono.value.trim() || null,
        email: email.value.trim(),
        password: password.value,
        consentimientoTextoVersion: 'v1.0',
        aceptaConsentimiento: true
      });

      ui.showToast('Cuenta creada con éxito. ¡Bienvenido a MediTriaje!', 'success');
      router.navigate('/patient/dashboard');
    } catch (err) {
      alertBox.innerHTML = `
        <div class="alert alert--danger mb-4" role="alert">
          ${ui.icon('alert-circle', 'icon alert-icon')}
          <div class="alert-content">
            <div class="alert-title">No fue posible registrar la cuenta</div>
            <div>${err.message || 'Verifica los datos e intenta nuevamente.'}</div>
          </div>
        </div>
      `;
    } finally {
      ui.setButtonLoading(btnSubmit, false);
    }
  });
}

/**
 * Renderiza la vista de Recuperación de Contraseña con código OTP por correo (ADR-014, F2.1.3).
 * @param {HTMLElement} container Contenedor principal
 */
export function forgotPasswordView(container) {
  let userEmail = '';

  const renderStep1 = () => {
    container.innerHTML = `
      <div class="container-narrow" style="padding-top: var(--space-8); padding-bottom: var(--space-8);">
        <div class="card" style="padding: var(--space-8);">
          <div class="text-center mb-6">
            <div class="empty-state-icon" style="margin: 0 auto var(--space-3) auto; background-color: var(--teal-50); color: var(--primary);">
              ${ui.icon('shield', 'icon icon--lg')}
            </div>
            <h1 class="text-2xl font-bold mb-1">Recuperar Contraseña</h1>
            <p class="text-sm text-muted">Te enviaremos un código numérico de 6 dígitos a tu correo registrado</p>
          </div>

          <form id="formForgotPassword" novalidate>
            <div id="forgotAlertContainer" aria-live="polite"></div>

            <div class="form-group mb-4">
              <label for="forgotEmail" class="form-label">
                Correo electrónico registrado <span class="required" aria-hidden="true">*</span>
              </label>
              <input 
                type="email" 
                id="forgotEmail" 
                name="email"
                class="form-input" 
                placeholder="ejemplo@correo.com" 
                required 
                autocomplete="email"
                aria-required="true"
                value="${esc(userEmail)}"
              >
              <span class="form-error" id="forgotEmailError" style="display: none;" role="alert"></span>
            </div>

            <button type="submit" id="btnForgotSubmit" class="btn btn-primary w-full mt-2">
              <span>Enviar código de verificación</span>
            </button>
          </form>

          <div class="text-center mt-6 pt-4 border-top">
            <p class="text-sm text-muted">
              ¿Recordaste tu contraseña?
              <a href="#/login" class="font-semibold text-primary" style="margin-left: 4px;">Volver al inicio de sesión</a>
            </p>
          </div>
        </div>
      </div>
    `;

    const form = container.querySelector('#formForgotPassword');
    const emailInput = container.querySelector('#forgotEmail');
    const emailError = container.querySelector('#forgotEmailError');
    const alertBox = container.querySelector('#forgotAlertContainer');
    const btnSubmit = container.querySelector('#btnForgotSubmit');

    setTimeout(() => emailInput?.focus(), 150);

    form?.addEventListener('submit', async (e) => {
      e.preventDefault();
      emailError.style.display = 'none';
      emailError.textContent = '';
      alertBox.innerHTML = '';
      emailInput.classList.remove('has-error');

      const email = emailInput.value.trim();
      if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        emailInput.classList.add('has-error');
        emailError.textContent = 'Ingresa un correo electrónico válido.';
        emailError.style.display = 'block';
        emailInput.focus();
        return;
      }

      userEmail = email;
      ui.setButtonLoading(btnSubmit, true);

      try {
        await auth.forgotPassword(email);
        ui.showToast('Código de recuperación despachado.', 'info');
        renderStep2();
      } catch (err) {
        ui.setButtonLoading(btnSubmit, false);
        alertBox.innerHTML = `
          <div class="alert alert--danger mb-4">
            ${ui.icon('alert-circle', 'icon alert-icon')}
            <div class="alert-content">
              <p class="m-0">${esc(err.message || 'Ocurrió un error al solicitar la recuperación.')}</p>
            </div>
          </div>
        `;
      }
    });
  };

  const renderStep2 = () => {
    container.innerHTML = `
      <div class="container-narrow" style="padding-top: var(--space-8); padding-bottom: var(--space-8);">
        <div class="card" style="padding: var(--space-8);">
          <div class="text-center mb-6">
            <div class="empty-state-icon" style="margin: 0 auto var(--space-3) auto; background-color: var(--teal-50); color: var(--primary);">
              ${ui.icon('shield', 'icon icon--lg')}
            </div>
            <h1 class="text-2xl font-bold mb-1">Nueva Contraseña</h1>
            <p class="text-sm text-muted">Ingresa el código OTP de 6 dígitos enviado a tu correo</p>
          </div>

          <div class="alert alert--info mb-4" role="note">
            ${ui.icon('info', 'icon alert-icon')}
            <div class="alert-content">
              <p class="m-0 text-xs">
                Hemos enviado un código a <strong>${esc(userEmail)}</strong>. Válido por 15 minutos (máximo 3 intentos).
              </p>
            </div>
          </div>

          <form id="formResetPassword" novalidate>
            <div id="resetAlertContainer" aria-live="polite"></div>

            <div class="form-group mb-4 text-center">
              <label for="resetOtpCode" class="form-label font-bold mb-1">
                Código de 6 dígitos <span class="required" aria-hidden="true">*</span>
              </label>
              <input 
                type="text" 
                id="resetOtpCode" 
                name="codigo"
                class="form-input text-center font-mono font-bold" 
                placeholder="123456" 
                maxlength="6"
                inputmode="numeric"
                pattern="[0-9]{6}"
                required 
                autocomplete="one-time-code"
                style="font-size: 1.5rem; letter-spacing: 0.25em; max-width: 200px; margin: 0 auto; display: block;"
              >
              <span class="form-error text-center mt-1" id="otpError" style="display: none;" role="alert"></span>
            </div>

            <div class="form-group mb-4">
              <label for="resetPassword" class="form-label">
                Nueva Contraseña <span class="required" aria-hidden="true">*</span>
              </label>
              <div style="position: relative; display: flex; align-items: center;">
                <input 
                  type="password" 
                  id="resetPassword" 
                  name="password"
                  class="form-input" 
                  placeholder="Mínimo 10 caracteres" 
                  required 
                  autocomplete="new-password"
                  style="padding-right: 48px;"
                >
                <button 
                  type="button" 
                  id="btnToggleResetPassword" 
                  class="btn btn-ghost btn--sm" 
                  style="position: absolute; right: 4px; padding: 6px 10px; color: var(--text-muted);" 
                  aria-label="Mostrar contraseña"
                >
                  ${ui.icon('search', 'icon icon--sm')}
                </button>
              </div>
              <span class="form-error" id="resetPasswordError" style="display: none;" role="alert"></span>
              <p class="text-xs text-muted mt-1">Mínimo 10 caracteres, combinando mayúsculas, minúsculas, números y símbolos.</p>
            </div>

            <div class="form-group mb-4">
              <label for="resetPasswordConfirm" class="form-label">
                Confirmar Nueva Contraseña <span class="required" aria-hidden="true">*</span>
              </label>
              <input 
                type="password" 
                id="resetPasswordConfirm" 
                name="passwordConfirm"
                class="form-input" 
                placeholder="Repite tu nueva contraseña" 
                required 
                autocomplete="new-password"
              >
              <span class="form-error" id="resetConfirmError" style="display: none;" role="alert"></span>
            </div>

            <button type="submit" id="btnResetSubmit" class="btn btn-primary w-full mt-2">
              <span>Guardar Nueva Contraseña</span>
            </button>

            <button type="button" id="btnBackToStep1" class="btn btn-ghost w-full mt-2 text-muted">
              <span>Reenviar código o corregir correo</span>
            </button>
          </form>
        </div>
      </div>
    `;

    const passInput = container.querySelector('#resetPassword');
    const toggleBtn = container.querySelector('#btnToggleResetPassword');
    let isVisible = false;
    toggleBtn?.addEventListener('click', () => {
      isVisible = !isVisible;
      passInput.type = isVisible ? 'text' : 'password';
      toggleBtn.innerHTML = ui.icon(isVisible ? 'x' : 'search', 'icon icon--sm');
    });

    container.querySelector('#btnBackToStep1')?.addEventListener('click', renderStep1);

    const form = container.querySelector('#formResetPassword');
    const otpInput = container.querySelector('#resetOtpCode');
    const confirmInput = container.querySelector('#resetPasswordConfirm');
    const otpError = container.querySelector('#otpError');
    const passError = container.querySelector('#resetPasswordError');
    const confirmError = container.querySelector('#resetConfirmError');
    const alertBox = container.querySelector('#resetAlertContainer');
    const btnSubmit = container.querySelector('#btnResetSubmit');

    setTimeout(() => otpInput?.focus(), 150);

    form?.addEventListener('submit', async (e) => {
      e.preventDefault();
      otpError.style.display = 'none';
      passError.style.display = 'none';
      confirmError.style.display = 'none';
      alertBox.innerHTML = '';
      otpInput.classList.remove('has-error');
      passInput.classList.remove('has-error');
      confirmInput.classList.remove('has-error');

      const otp = otpInput.value.trim();
      const pass = passInput.value;
      const confirm = confirmInput.value;
      let hasError = false;

      if (!otp || !/^[0-9]{6}$/.test(otp)) {
        otpInput.classList.add('has-error');
        otpError.textContent = 'El código debe tener 6 dígitos numéricos.';
        otpError.style.display = 'block';
        hasError = true;
      }

      if (!pass || pass.length < 10) {
        passInput.classList.add('has-error');
        passError.textContent = 'La contraseña debe tener al menos 10 caracteres.';
        passError.style.display = 'block';
        hasError = true;
      }

      if (pass !== confirm) {
        confirmInput.classList.add('has-error');
        confirmError.textContent = 'Las contraseñas no coinciden.';
        confirmError.style.display = 'block';
        hasError = true;
      }

      if (hasError) return;

      ui.setButtonLoading(btnSubmit, true);

      try {
        await auth.resetPassword(userEmail, otp, pass);
        renderSuccess();
      } catch (err) {
        ui.setButtonLoading(btnSubmit, false);
        alertBox.innerHTML = `
          <div class="alert alert--danger mb-4">
            ${ui.icon('alert-circle', 'icon alert-icon')}
            <div class="alert-content">
              <p class="m-0">${esc(err.message || 'No fue posible restablecer la contraseña. Verifica el código.')}</p>
            </div>
          </div>
        `;
      }
    });
  };

  const renderSuccess = () => {
    container.innerHTML = `
      <div class="container-narrow" style="padding-top: var(--space-8); padding-bottom: var(--space-8);">
        <div class="card text-center" style="padding: var(--space-8);">
          <div class="empty-state-icon" style="margin: 0 auto var(--space-4) auto; background-color: var(--success-bg); color: var(--success);">
            ${ui.icon('check', 'icon icon--lg')}
          </div>
          <h1 class="text-2xl font-bold mb-2">¡Contraseña Actualizada!</h1>
          <p class="text-sm text-muted mb-6">
            Tu contraseña ha sido restablecida exitosamente. Por seguridad, todas las sesiones activas previas han sido cerradas.
          </p>
          <a href="#/login" class="btn btn-primary w-full">
            <span>Iniciar Sesión con Nueva Contraseña</span>
          </a>
        </div>
      </div>
    `;
    ui.showToast('Contraseña restablecida exitosamente.', 'success');
  };

  renderStep1();
}
