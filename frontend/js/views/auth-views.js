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
                  <option value="RC">Registro Civil (RC)</option>
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
                <span class="form-help" id="numDocHelp">6 a 10 dígitos numéricos (mayores de edad ≥18 años).</span>
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
                <span class="form-help" id="nombresHelp">Solo letras, tildes y espacios (2 a 60 caracteres).</span>
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
                <span class="form-help" id="apellidosHelp">Solo letras, tildes y espacios (2 a 60 caracteres).</span>
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
                <span class="form-help" id="fechaNacHelp" style="color: var(--teal-700); font-weight: 500;"></span>
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
                <span class="form-help" id="telefonoHelp">10 dígitos iniciando por 3 (ej. 3001234567) o +57.</span>
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
              
              <!-- Indicador interactivo de fortaleza y requisitos en vivo -->
              <div id="passwordStrengthContainer" class="mt-2 mb-3" style="display: none;">
                <div class="flex items-center justify-between text-xs mb-1">
                  <span class="text-muted">Fortaleza:</span>
                  <span id="passwordStrengthLabel" class="font-bold text-danger">Muy débil</span>
                </div>
                <div style="background-color: var(--border); height: 6px; border-radius: 3px; overflow: hidden;">
                  <div id="passwordStrengthBar" style="height: 100%; width: 20%; transition: width 0.3s ease, background-color 0.3s ease; background-color: var(--danger);"></div>
                </div>
                <ul id="passwordRequirementsList" class="text-xs mt-2" style="list-style-type: none; padding-left: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 4px;">
                  <li id="reqLength" class="text-muted flex items-center gap-1">• Mínimo 10 caracteres</li>
                  <li id="reqUpper" class="text-muted flex items-center gap-1">• Una letra mayúscula</li>
                  <li id="reqLower" class="text-muted flex items-center gap-1">• Una letra minúscula</li>
                  <li id="reqNumber" class="text-muted flex items-center gap-1">• Un número (0-9)</li>
                  <li id="reqSymbol" class="text-muted flex items-center gap-1">• Un símbolo (!@#$...)</li>
                </ul>
              </div>
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

  const numDocHelp = document.getElementById('numDocHelp');
  const nombresHelp = document.getElementById('nombresHelp');
  const apellidosHelp = document.getElementById('apellidosHelp');
  const fechaNacHelp = document.getElementById('fechaNacHelp');
  const telefonoHelp = document.getElementById('telefonoHelp');

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

  const strengthContainer = document.getElementById('passwordStrengthContainer');
  const strengthLabel = document.getElementById('passwordStrengthLabel');
  const strengthBar = document.getElementById('passwordStrengthBar');
  const reqLength = document.getElementById('reqLength');
  const reqUpper = document.getElementById('reqUpper');
  const reqLower = document.getElementById('reqLower');
  const reqNumber = document.getElementById('reqNumber');
  const reqSymbol = document.getElementById('reqSymbol');

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

  // =========================================================================
  // LIVE VALIDATION — NORMA COLOMBIANA EN SALUD (MinSalud RIPS / Registraduría)
  // =========================================================================

  const DOC_RULES = {
    CC: {
      label: 'Cédula de Ciudadanía',
      regex: /^\d{6,10}$/,
      isNumeric: true,
      minAge: 18,
      maxAge: 125,
      placeholder: 'Ej. 1098765432',
      help: '6 a 10 dígitos numéricos (mayores de edad ≥18 años).',
      errorFormat: 'La Cédula de Ciudadanía (CC) debe contener entre 6 y 10 dígitos numéricos sin letras.',
      errorAge: (age) => `La Cédula de Ciudadanía (CC) solo es válida para personas mayores de 18 años. Edad calculada: ${age} años.`
    },
    TI: {
      label: 'Tarjeta de Identidad',
      regex: /^\d{10,11}$/,
      isNumeric: true,
      minAge: 7,
      maxAge: 17,
      placeholder: 'Ej. 1098765432',
      help: '10 u 11 dígitos numéricos (menores entre 7 y 17 años).',
      errorFormat: 'La Tarjeta de Identidad (TI) debe contener 10 u 11 dígitos numéricos.',
      errorAge: (age) => `La Tarjeta de Identidad (TI) solo aplica para menores entre 7 y 17 años cumplidos. Edad calculada: ${age} años.`
    },
    RC: {
      label: 'Registro Civil',
      regex: /^\d{10,11}$/,
      isNumeric: true,
      minAge: 0,
      maxAge: 6,
      placeholder: 'Ej. 1098765432',
      help: '10 u 11 dígitos numéricos (infantes menores de 7 años).',
      errorFormat: 'El Registro Civil (RC) debe contener 10 u 11 dígitos numéricos.',
      errorAge: (age) => `El Registro Civil (RC) solo aplica para infantes menores de 7 años. Edad calculada: ${age} años.`
    },
    CE: {
      label: 'Cédula de Extranjería',
      regex: /^[a-zA-Z0-9]{3,10}$/,
      isNumeric: false,
      minAge: 0,
      maxAge: 125,
      placeholder: 'Ej. E123456',
      help: 'Alfanumérico de 3 a 10 caracteres.',
      errorFormat: 'La Cédula de Extranjería (CE) debe contener entre 3 y 10 caracteres alfanuméricos.'
    },
    PA: {
      label: 'Pasaporte',
      regex: /^[a-zA-Z0-9]{5,20}$/,
      isNumeric: false,
      minAge: 0,
      maxAge: 125,
      placeholder: 'Ej. PA1234567',
      help: 'Alfanumérico de 5 a 20 caracteres.',
      errorFormat: 'El Pasaporte (PA) debe contener entre 5 y 20 caracteres alfanuméricos.'
    }
  };

  function calcularEdad(fechaStr) {
    if (!fechaStr) return null;
    const fecha = new Date(fechaStr + 'T00:00:00');
    if (isNaN(fecha.getTime())) return null;
    const hoy = new Date();
    let edad = hoy.getFullYear() - fecha.getFullYear();
    const m = hoy.getMonth() - fecha.getMonth();
    if (m < 0 || (m === 0 && hoy.getDate() < fecha.getDate())) {
      edad--;
    }
    return edad;
  }

  function setValidationStatus(inputEl, errorEl, isValid, errorMsg, helpEl, helpMsg) {
    if (isValid) {
      inputEl.classList.remove('has-error', 'form-input--error');
      inputEl.removeAttribute('aria-invalid');
      if (inputEl.value && inputEl.value.trim()) {
        inputEl.classList.add('has-success', 'form-input--success');
      } else {
        inputEl.classList.remove('has-success', 'form-input--success');
      }
      if (errorEl) {
        errorEl.style.display = 'none';
        errorEl.textContent = '';
      }
      if (helpEl && helpMsg !== undefined) {
        helpEl.textContent = helpMsg;
      }
    } else {
      inputEl.classList.remove('has-success', 'form-input--success');
      inputEl.classList.add('has-error', 'form-input--error');
      inputEl.setAttribute('aria-invalid', 'true');
      if (errorEl) {
        inputEl.setAttribute('aria-describedby', errorEl.id);
        errorEl.textContent = errorMsg;
        errorEl.style.display = 'block';
      }
      if (helpEl && helpMsg !== undefined) {
        helpEl.textContent = helpMsg;
      }
    }
    return isValid;
  }

  function validateNumDoc(isLive = false) {
    const rule = DOC_RULES[tipoDoc.value] || DOC_RULES.CC;
    const val = numDoc.value.trim();
    if (!val) {
      if (!isLive) {
        return setValidationStatus(numDoc, numDocError, false, 'El número de documento es obligatorio.', numDocHelp, rule.help);
      }
      return setValidationStatus(numDoc, numDocError, true, '', numDocHelp, rule.help);
    }
    if (rule.isNumeric && !/^\d+$/.test(val)) {
      return setValidationStatus(numDoc, numDocError, false, rule.errorFormat, numDocHelp, rule.help);
    }
    if (!rule.regex.test(val)) {
      return setValidationStatus(numDoc, numDocError, false, rule.errorFormat, numDocHelp, rule.help);
    }
    return setValidationStatus(numDoc, numDocError, true, '', numDocHelp, rule.help);
  }

  function validateNombres(isLive = false) {
    const val = nombres.value.trim();
    if (!val) {
      if (!isLive) {
        return setValidationStatus(nombres, nombresError, false, 'Los nombres son obligatorios.');
      }
      return setValidationStatus(nombres, nombresError, true, '');
    }
    if (!/^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\s'-]{2,60}$/.test(val)) {
      return setValidationStatus(nombres, nombresError, false, 'Los nombres solo pueden contener letras, espacios, guiones y tildes (2 a 60 caracteres).');
    }
    return setValidationStatus(nombres, nombresError, true, '');
  }

  function validateApellidos(isLive = false) {
    const val = apellidos.value.trim();
    if (!val) {
      if (!isLive) {
        return setValidationStatus(apellidos, apellidosError, false, 'Los apellidos son obligatorios.');
      }
      return setValidationStatus(apellidos, apellidosError, true, '');
    }
    if (!/^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\s'-]{2,60}$/.test(val)) {
      return setValidationStatus(apellidos, apellidosError, false, 'Los apellidos solo pueden contener letras, espacios, guiones y tildes (2 a 60 caracteres).');
    }
    return setValidationStatus(apellidos, apellidosError, true, '');
  }

  function validateFechaNac(isLive = false) {
    const val = fechaNac.value;
    const rule = DOC_RULES[tipoDoc.value] || DOC_RULES.CC;
    if (!val) {
      if (fechaNacHelp) fechaNacHelp.textContent = '';
      if (!isLive) {
        return setValidationStatus(fechaNac, fechaNacError, false, 'La fecha de nacimiento es obligatoria.');
      }
      return setValidationStatus(fechaNac, fechaNacError, true, '');
    }
    const edad = calcularEdad(val);
    const selectedDate = new Date(val + 'T00:00:00');
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    if (isNaN(selectedDate.getTime()) || selectedDate >= today || (edad !== null && edad < 0)) {
      return setValidationStatus(fechaNac, fechaNacError, false, 'La fecha de nacimiento no puede ser futura ni el día de hoy.', fechaNacHelp, '');
    }
    if (edad > 125) {
      return setValidationStatus(fechaNac, fechaNacError, false, 'La fecha de nacimiento no es válida (edad superior a 125 años).', fechaNacHelp, '');
    }
    if (edad < rule.minAge || (rule.maxAge && edad > rule.maxAge)) {
      const msg = (typeof rule.errorAge === 'function') ? rule.errorAge(edad) : `Edad (${edad} años) fuera de rango para ${rule.label}.`;
      return setValidationStatus(fechaNac, fechaNacError, false, msg, fechaNacHelp, '');
    }
    return setValidationStatus(fechaNac, fechaNacError, true, '', fechaNacHelp, `✓ Edad calculada: ${edad} años (${rule.label}).`);
  }

  function validateTelefono(isLive = false) {
    const raw = telefono.value.trim();
    if (!raw) {
      return setValidationStatus(telefono, telefonoError, true, '', telefonoHelp, '10 dígitos iniciando por 3 (ej. 3001234567) o +57.');
    }
    const clean = raw.replace(/\s+/g, '');
    if (!/^(\+57)?3[0-9]{9}$/.test(clean)) {
      return setValidationStatus(telefono, telefonoError, false, 'El celular debe tener 10 dígitos e iniciar por 3 (ej. 3001234567) o prefijo +57.', telefonoHelp, '');
    }
    return setValidationStatus(telefono, telefonoError, true, '', telefonoHelp, '✓ Celular colombiano válido.');
  }

  function validateEmail(isLive = false) {
    const val = email.value.trim();
    if (!val) {
      if (!isLive) {
        return setValidationStatus(email, regEmailError, false, 'El correo electrónico es obligatorio.');
      }
      return setValidationStatus(email, regEmailError, true, '');
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val)) {
      return setValidationStatus(email, regEmailError, false, 'Ingresa un formato de correo electrónico válido (ej. usuario@correo.com).');
    }
    return setValidationStatus(email, regEmailError, true, '');
  }

  function validatePassword(isLive = false) {
    const val = password.value;
    if (!val) {
      if (strengthContainer) strengthContainer.style.display = 'none';
      if (!isLive) {
        return setValidationStatus(password, regPasswordError, false, 'La contraseña es obligatoria.');
      }
      return setValidationStatus(password, regPasswordError, true, '');
    }

    if (strengthContainer) strengthContainer.style.display = 'block';

    const reqs = {
      len: val.length >= 10,
      upper: /[A-Z]/.test(val),
      lower: /[a-z]/.test(val),
      num: /[0-9]/.test(val),
      sym: /[^a-zA-Z0-9]/.test(val)
    };

    const updateReqItem = (el, passed, text) => {
      if (!el) return;
      if (passed) {
        el.className = 'text-success font-semibold flex items-center gap-1';
        el.textContent = '✓ ' + text;
      } else {
        el.className = 'text-muted flex items-center gap-1';
        el.textContent = '• ' + text;
      }
    };

    updateReqItem(reqLength, reqs.len, 'Mínimo 10 caracteres');
    updateReqItem(reqUpper, reqs.upper, 'Una letra mayúscula');
    updateReqItem(reqLower, reqs.lower, 'Una letra minúscula');
    updateReqItem(reqNumber, reqs.num, 'Un número (0-9)');
    updateReqItem(reqSymbol, reqs.sym, 'Un símbolo (!@#$...)');

    const score = Object.values(reqs).filter(Boolean).length;
    if (strengthBar && strengthLabel) {
      if (score <= 2) {
        strengthBar.style.width = '25%';
        strengthBar.style.backgroundColor = 'var(--danger)';
        strengthLabel.textContent = 'Débil';
        strengthLabel.className = 'font-bold text-danger';
      } else if (score <= 4) {
        strengthBar.style.width = '65%';
        strengthBar.style.backgroundColor = 'var(--warning)';
        strengthLabel.textContent = 'Media';
        strengthLabel.className = 'font-bold text-warning';
      } else {
        strengthBar.style.width = '100%';
        strengthBar.style.backgroundColor = 'var(--success)';
        strengthLabel.textContent = 'Fuerte';
        strengthLabel.className = 'font-bold text-success';
      }
    }

    if (!reqs.len) {
      return setValidationStatus(password, regPasswordError, false, 'La contraseña debe tener al menos 10 caracteres.');
    }
    if (score < 4) {
      return setValidationStatus(password, regPasswordError, false, 'La contraseña debe combinar mayúsculas, minúsculas, números y símbolos.');
    }
    return setValidationStatus(password, regPasswordError, true, '');
  }

  function validatePasswordConfirm(isLive = false) {
    const val = passwordConfirm.value;
    const pwd = password.value;
    if (!val) {
      if (!isLive) {
        return setValidationStatus(passwordConfirm, regPasswordConfirmError, false, 'Debes confirmar tu contraseña.');
      }
      return setValidationStatus(passwordConfirm, regPasswordConfirmError, true, '');
    }
    if (val !== pwd) {
      return setValidationStatus(passwordConfirm, regPasswordConfirmError, false, 'Las contraseñas no coinciden.');
    }
    return setValidationStatus(passwordConfirm, regPasswordConfirmError, true, '');
  }

  function validateConsentimiento(isLive = false) {
    if (!consentimiento.checked) {
      if (!isLive) {
        consentimientoError.textContent = 'Debes aceptar los términos y tratamiento de datos para registrarte.';
        consentimientoError.style.display = 'block';
      }
      return false;
    }
    consentimientoError.style.display = 'none';
    consentimientoError.textContent = '';
    return true;
  }

  // --- Listeners de Live Validation en Paso 1 ---
  tipoDoc.addEventListener('change', () => {
    const rule = DOC_RULES[tipoDoc.value] || DOC_RULES.CC;
    numDoc.placeholder = rule.placeholder;
    if (numDocHelp) numDocHelp.textContent = rule.help;
    validateNumDoc(true);
    validateFechaNac(true);
  });

  numDoc.addEventListener('input', () => validateNumDoc(true));
  numDoc.addEventListener('blur', () => validateNumDoc(false));

  nombres.addEventListener('input', () => validateNombres(true));
  nombres.addEventListener('blur', () => validateNombres(false));

  apellidos.addEventListener('input', () => validateApellidos(true));
  apellidos.addEventListener('blur', () => validateApellidos(false));

  fechaNac.addEventListener('input', () => validateFechaNac(true));
  fechaNac.addEventListener('change', () => validateFechaNac(true));
  fechaNac.addEventListener('blur', () => validateFechaNac(false));

  telefono.addEventListener('input', () => validateTelefono(true));
  telefono.addEventListener('blur', () => validateTelefono(false));

  // --- Listeners de Live Validation en Paso 2 ---
  email.addEventListener('input', () => validateEmail(true));
  email.addEventListener('blur', () => validateEmail(false));

  password.addEventListener('input', () => {
    validatePassword(true);
    if (passwordConfirm.value) validatePasswordConfirm(true);
  });
  password.addEventListener('blur', () => validatePassword(false));

  passwordConfirm.addEventListener('input', () => validatePasswordConfirm(true));
  passwordConfirm.addEventListener('blur', () => validatePasswordConfirm(false));

  consentimiento.addEventListener('change', () => validateConsentimiento(true));

  // Validación completa del Paso 1 para avanzar
  function validateStep1() {
    alertBox.innerHTML = '';
    const v1 = validateNumDoc(false);
    const v2 = validateNombres(false);
    const v3 = validateApellidos(false);
    const v4 = validateFechaNac(false);
    const v5 = validateTelefono(false);

    const isValid = v1 && v2 && v3 && v4 && v5;
    if (!isValid) {
      if (!v1) numDoc.focus();
      else if (!v2) nombres.focus();
      else if (!v3) apellidos.focus();
      else if (!v4) fechaNac.focus();
      else if (!v5) telefono.focus();
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

    const vEmail = validateEmail(false);
    const vPass = validatePassword(false);
    const vPassConf = validatePasswordConfirm(false);
    const vCons = validateConsentimiento(false);

    if (!vEmail || !vPass || !vPassConf || !vCons) {
      if (!vEmail) email.focus();
      else if (!vPass) password.focus();
      else if (!vPassConf) passwordConfirm.focus();
      else if (!vCons) consentimiento.focus();
      return;
    }

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

    const validateEmail = (isLive = false) => {
      const email = emailInput.value.trim();
      emailError.style.display = 'none';
      emailError.textContent = '';
      emailInput.classList.remove('has-error', 'has-success', 'form-input--success');

      if (!email) {
        if (!isLive) {
          emailInput.classList.add('has-error');
          emailError.textContent = 'El correo electrónico es obligatorio.';
          emailError.style.display = 'block';
          return false;
        }
        return true;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        emailInput.classList.add('has-error');
        emailError.textContent = 'Ingresa un correo electrónico válido.';
        emailError.style.display = 'block';
        return false;
      }
      emailInput.classList.add('has-success', 'form-input--success');
      return true;
    };

    emailInput?.addEventListener('input', () => validateEmail(true));
    emailInput?.addEventListener('blur', () => validateEmail(false));

    form?.addEventListener('submit', async (e) => {
      e.preventDefault();
      alertBox.innerHTML = '';

      if (!validateEmail(false)) {
        emailInput.focus();
        return;
      }

      const email = emailInput.value.trim();
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
              <span class="form-help text-xs" id="otpHelp">Ingresa los 6 números del correo</span>
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

              <!-- Medidor de fortaleza interactivo -->
              <div id="resetStrengthContainer" class="mt-2 mb-3" style="display: none;">
                <div class="flex items-center justify-between text-xs mb-1">
                  <span class="text-muted">Fortaleza:</span>
                  <span id="resetStrengthLabel" class="font-bold text-danger">Débil</span>
                </div>
                <div style="background-color: var(--border); height: 6px; border-radius: 3px; overflow: hidden;">
                  <div id="resetStrengthBar" style="height: 100%; width: 20%; transition: width 0.3s ease, background-color 0.3s ease; background-color: var(--danger);"></div>
                </div>
                <ul id="resetReqList" class="text-xs mt-2" style="list-style-type: none; padding-left: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 4px;">
                  <li id="resetReqLength" class="text-muted flex items-center gap-1">• Mínimo 10 caracteres</li>
                  <li id="resetReqUpper" class="text-muted flex items-center gap-1">• Una letra mayúscula</li>
                  <li id="resetReqLower" class="text-muted flex items-center gap-1">• Una letra minúscula</li>
                  <li id="resetReqNumber" class="text-muted flex items-center gap-1">• Un número (0-9)</li>
                  <li id="resetReqSymbol" class="text-muted flex items-center gap-1">• Un símbolo (!@#$...)</li>
                </ul>
              </div>
              <span class="form-error" id="resetPasswordError" style="display: none;" role="alert"></span>
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
    const otpHelp = container.querySelector('#otpHelp');
    const confirmInput = container.querySelector('#resetPasswordConfirm');
    const otpError = container.querySelector('#otpError');
    const passError = container.querySelector('#resetPasswordError');
    const confirmError = container.querySelector('#resetConfirmError');
    const alertBox = container.querySelector('#resetAlertContainer');
    const btnSubmit = container.querySelector('#btnResetSubmit');

    // Elementos del medidor
    const strengthContainer = container.querySelector('#resetStrengthContainer');
    const strengthBar = container.querySelector('#resetStrengthBar');
    const strengthLabel = container.querySelector('#resetStrengthLabel');
    const reqLength = container.querySelector('#resetReqLength');
    const reqUpper = container.querySelector('#resetReqUpper');
    const reqLower = container.querySelector('#resetReqLower');
    const reqNumber = container.querySelector('#resetReqNumber');
    const reqSymbol = container.querySelector('#resetReqSymbol');

    setTimeout(() => otpInput?.focus(), 150);

    const validateOtp = (isLive = false) => {
      // Filtrar no numéricos y limitar a 6 dígitos en tiempo real
      otpInput.value = otpInput.value.replace(/\D/g, '').slice(0, 6);
      const val = otpInput.value;
      otpError.style.display = 'none';
      otpError.textContent = '';
      otpInput.classList.remove('has-error', 'has-success', 'form-input--success');

      if (!val) {
        if (otpHelp) otpHelp.textContent = 'Ingresa los 6 números del correo';
        if (!isLive) {
          otpInput.classList.add('has-error');
          otpError.textContent = 'El código OTP es obligatorio.';
          otpError.style.display = 'block';
          return false;
        }
        return true;
      }

      if (val.length < 6) {
        if (otpHelp) otpHelp.textContent = `${val.length}/6 dígitos ingresados`;
        if (!isLive) {
          otpInput.classList.add('has-error');
          otpError.textContent = 'El código debe tener exactamente 6 dígitos numéricos.';
          otpError.style.display = 'block';
          return false;
        }
        return true;
      }

      otpInput.classList.add('has-success', 'form-input--success');
      if (otpHelp) otpHelp.textContent = '✓ Código de 6 dígitos completado.';
      return true;
    };

    const validatePassword = (isLive = false) => {
      const val = passInput.value;
      passError.style.display = 'none';
      passError.textContent = '';
      passInput.classList.remove('has-error', 'has-success', 'form-input--success');

      if (!val) {
        if (strengthContainer) strengthContainer.style.display = 'none';
        if (!isLive) {
          passInput.classList.add('has-error');
          passError.textContent = 'La nueva contraseña es obligatoria.';
          passError.style.display = 'block';
          return false;
        }
        return true;
      }

      if (strengthContainer) strengthContainer.style.display = 'block';

      const reqs = {
        len: val.length >= 10,
        upper: /[A-Z]/.test(val),
        lower: /[a-z]/.test(val),
        num: /[0-9]/.test(val),
        sym: /[^a-zA-Z0-9]/.test(val)
      };

      const updateReqItem = (el, passed, text) => {
        if (!el) return;
        if (passed) {
          el.className = 'text-success font-semibold flex items-center gap-1';
          el.textContent = '✓ ' + text;
        } else {
          el.className = 'text-muted flex items-center gap-1';
          el.textContent = '• ' + text;
        }
      };

      updateReqItem(reqLength, reqs.len, 'Mínimo 10 caracteres');
      updateReqItem(reqUpper, reqs.upper, 'Una letra mayúscula');
      updateReqItem(reqLower, reqs.lower, 'Una letra minúscula');
      updateReqItem(reqNumber, reqs.num, 'Un número (0-9)');
      updateReqItem(reqSymbol, reqs.sym, 'Un símbolo (!@#$...)');

      const score = Object.values(reqs).filter(Boolean).length;
      if (strengthBar && strengthLabel) {
        if (score <= 2) {
          strengthBar.style.width = '25%';
          strengthBar.style.backgroundColor = 'var(--danger)';
          strengthLabel.textContent = 'Débil';
          strengthLabel.className = 'font-bold text-danger';
        } else if (score <= 4) {
          strengthBar.style.width = '65%';
          strengthBar.style.backgroundColor = 'var(--warning)';
          strengthLabel.textContent = 'Media';
          strengthLabel.className = 'font-bold text-warning';
        } else {
          strengthBar.style.width = '100%';
          strengthBar.style.backgroundColor = 'var(--success)';
          strengthLabel.textContent = 'Fuerte';
          strengthLabel.className = 'font-bold text-success';
        }
      }

      if (!reqs.len) {
        passInput.classList.add('has-error');
        passError.textContent = 'La contraseña debe tener al menos 10 caracteres.';
        passError.style.display = 'block';
        return false;
      }
      if (score < 4) {
        passInput.classList.add('has-error');
        passError.textContent = 'La contraseña debe combinar mayúsculas, minúsculas, números y símbolos.';
        passError.style.display = 'block';
        return false;
      }

      passInput.classList.add('has-success', 'form-input--success');
      return true;
    };

    const validateConfirm = (isLive = false) => {
      const pass = passInput.value;
      const conf = confirmInput.value;
      confirmError.style.display = 'none';
      confirmError.textContent = '';
      confirmInput.classList.remove('has-error', 'has-success', 'form-input--success');

      if (!conf) {
        if (!isLive) {
          confirmInput.classList.add('has-error');
          confirmError.textContent = 'Debes confirmar la nueva contraseña.';
          confirmError.style.display = 'block';
          return false;
        }
        return true;
      }

      if (conf !== pass) {
        confirmInput.classList.add('has-error');
        confirmError.textContent = 'Las contraseñas no coinciden.';
        confirmError.style.display = 'block';
        return false;
      }

      confirmInput.classList.add('has-success', 'form-input--success');
      return true;
    };

    otpInput.addEventListener('input', () => validateOtp(true));
    otpInput.addEventListener('blur', () => validateOtp(false));

    passInput.addEventListener('input', () => {
      validatePassword(true);
      if (confirmInput.value) validateConfirm(true);
    });
    passInput.addEventListener('blur', () => validatePassword(false));

    confirmInput.addEventListener('input', () => validateConfirm(true));
    confirmInput.addEventListener('blur', () => validateConfirm(false));

    form?.addEventListener('submit', async (e) => {
      e.preventDefault();
      alertBox.innerHTML = '';

      const vOtp = validateOtp(false);
      const vPass = validatePassword(false);
      const vConf = validateConfirm(false);

      if (!vOtp || !vPass || !vConf) {
        if (!vOtp) otpInput.focus();
        else if (!vPass) passInput.focus();
        else confirmInput.focus();
        return;
      }

      ui.setButtonLoading(btnSubmit, true);

      try {
        await auth.resetPassword(userEmail, otpInput.value.trim(), passInput.value);
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
