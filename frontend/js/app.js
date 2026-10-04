/**
 * MediTriaje 2.0 — Entrada Principal de la Aplicación Frontend (app.js)
 * Inicialización de autenticación, enrutador, barra de navegación y vistas base.
 */

import { auth } from './auth.js';
import { router } from './router.js';
import { ui } from './ui.js';

// Inicialización de Tema Claro / Oscuro
function initTheme() {
  const root = document.documentElement;
  const themeBtn = document.getElementById('themeToggleBtn');
  const themeText = document.getElementById('themeToggleText');

  const applyTheme = (theme) => {
    if (theme === 'dark') {
      root.setAttribute('data-theme', 'dark');
      if (themeText) themeText.textContent = 'Modo Claro';
    } else {
      root.removeAttribute('data-theme');
      if (themeText) themeText.textContent = 'Modo Oscuro';
    }
    localStorage.setItem('theme', theme);
  };

  const savedTheme = localStorage.getItem('theme') || 'light';
  applyTheme(savedTheme);

  if (themeBtn) {
    themeBtn.addEventListener('click', () => {
      const current = root.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
      applyTheme(current === 'dark' ? 'light' : 'dark');
    });
  }
}

// Actualización de la barra de navegación según el estado de la sesión
function updateNavbar() {
  const navContainer = document.getElementById('nav-auth-links');
  const bottomNav = document.getElementById('bottom-nav');
  if (!navContainer) return;

  if (auth.isAuthenticated) {
    const user = auth.user;
    let roleName = 'Paciente';
    let roleBadgeClass = 'badge--confirmed';
    let dashboardLink = '#/patient/dashboard';

    if (auth.isProfesional) {
      roleName = 'Profesional';
      roleBadgeClass = 'badge--scheduled';
      dashboardLink = '#/professional/agenda';
    } else if (auth.isAdmin) {
      roleName = 'Administrador';
      roleBadgeClass = 'badge--rescheduled';
      dashboardLink = '#/admin/dashboard';
    }

    navContainer.innerHTML = `
      <a href="${dashboardLink}" class="nav-link">
        ${ui.icon('user', 'icon icon--sm')}
        <span class="font-medium">${user.email}</span>
        <span class="badge ${roleBadgeClass}" style="margin-left: 4px;">${roleName}</span>
      </a>
      <button type="button" id="btnLogout" class="btn btn-ghost btn--sm" title="Cerrar sesion activa">
        ${ui.icon('log-out', 'icon icon--sm')}
        <span>Salir</span>
      </button>
    `;

    document.getElementById('btnLogout')?.addEventListener('click', async () => {
      ui.showModal({
        title: 'Cerrar sesión',
        message: '¿Deseas cerrar tu sesión actual en MediTriaje?',
        confirmText: 'Sí, salir',
        cancelText: 'Permanecer',
        onConfirm: async () => {
          await auth.logout();
          ui.showToast('Sesión cerrada correctamente.', 'info');
        }
      });
    });

    // Mostrar barra inferior en móviles solo para pacientes
    if (bottomNav) {
      bottomNav.style.display = auth.isPaciente ? 'flex' : 'none';
    }
  } else {
    navContainer.innerHTML = `
      <a href="#/login" class="nav-link font-medium">Iniciar sesión</a>
      <a href="#/register" class="btn btn-primary btn--sm">Registrarme</a>
    `;

    if (bottomNav) {
      bottomNav.style.display = 'none';
    }
  }
}

// Configuración de las rutas del sistema
function setupRoutes() {
  const app = document.getElementById('app');
  router.setContainer(app);

  // Ruta 1: Inicio / Landing
  router.addRoute('/', async (container) => {
    container.innerHTML = `
      <div class="sg-section text-center" style="padding-top: var(--space-8); padding-bottom: var(--space-8);">
        <span class="badge badge--scheduled mb-3">Plataforma Asistencial Integral</span>
        <h1 class="text-4xl font-bold mb-3" style="max-width: 24ch; margin-left: auto; margin-right: auto;">
          Atención médica oportuna, triaje clínico y recetas digitales
        </h1>
        <p class="text-muted mb-6" style="margin-left: auto; margin-right: auto; max-width: 58ch;">
          Orienta tus síntomas mediante nuestro sistema de triaje automatizado, agenda consultas con especialistas y accede a tu historial clínico inmutable.
        </p>

        <div class="flex flex-wrap gap-4 justify-center items-center">
          <a href="#/register" class="btn btn-primary btn--lg">
            ${ui.icon('activity')}
            <span>Comenzar ahora</span>
          </a>
          <a href="#/login" class="btn btn-secondary btn--lg">
            <span>Ingresar a mi cuenta</span>
          </a>
        </div>

        <div class="grid grid-cols-1 grid-cols-3-md gap-6 mt-12 text-left">
          <div class="card">
            <div class="empty-state-icon" style="margin-left: 0; background-color: var(--teal-50); color: var(--primary);">
              ${ui.icon('activity', 'icon icon--lg')}
            </div>
            <h3 class="text-lg font-semibold mb-2">Triaje Clínico</h3>
            <p class="text-sm text-muted">Evaluación de síntomas con corte de emergencia para orientar el nivel de prioridad asistencial.</p>
          </div>

          <div class="card">
            <div class="empty-state-icon" style="margin-left: 0; background-color: var(--info-bg); color: var(--info);">
              ${ui.icon('calendar', 'icon icon--lg')}
            </div>
            <h3 class="text-lg font-semibold mb-2">Citas y Disponibilidad</h3>
            <p class="text-sm text-muted">Búsqueda en tiempo real por especialidad y sede, sin colisiones ni doble agendamiento.</p>
          </div>

          <div class="card">
            <div class="empty-state-icon" style="margin-left: 0; background-color: var(--success-bg); color: var(--success);">
              ${ui.icon('file-text', 'icon icon--lg')}
            </div>
            <h3 class="text-lg font-semibold mb-2">Historia y Recetas</h3>
            <p class="text-sm text-muted">Consultas inmutables, enmiendas cronológicas y recetas con snapshots farmacológicos estables.</p>
          </div>
        </div>
      </div>
    `;
  });

  // Ruta 2: Login
  router.addRoute('/login', async (container, { queryParams }) => {
    container.innerHTML = `
      <div class="container-narrow" style="padding-top: var(--space-8);">
        <div class="card" style="padding: var(--space-8);">
          <div class="text-center mb-6">
            <h1 class="text-2xl font-bold mb-2">Iniciar Sesión</h1>
            <p class="text-sm text-muted">Ingresa tus credenciales para acceder a MediTriaje 2.0</p>
          </div>

          <form id="formLogin" novalidate>
            <div id="loginAlertContainer"></div>

            <div class="form-group">
              <label for="loginEmail" class="form-label">
                Correo electrónico <span class="required" aria-hidden="true">*</span>
              </label>
              <input type="email" id="loginEmail" class="form-input" placeholder="usuario@correo.com" required autocomplete="username">
            </div>

            <div class="form-group">
              <label for="loginPassword" class="form-label">
                Contraseña <span class="required" aria-hidden="true">*</span>
              </label>
              <input type="password" id="loginPassword" class="form-input" placeholder="••••••••••" required autocomplete="current-password">
            </div>

            <button type="submit" id="btnLoginSubmit" class="btn btn-primary w-full mt-4">
              <span>Ingresar</span>
            </button>
          </form>

          <div class="text-center mt-6 pt-4 border-top">
            <span class="text-sm text-muted">¿Eres paciente nuevo?</span>
            <a href="#/register" class="text-sm font-semibold text-primary" style="margin-left: 4px;">Regístrate aquí</a>
          </div>
        </div>
      </div>
    `;

    const form = document.getElementById('formLogin');
    const btnSubmit = document.getElementById('btnLoginSubmit');
    const alertBox = document.getElementById('loginAlertContainer');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      alertBox.innerHTML = '';

      const email = document.getElementById('loginEmail').value.trim();
      const password = document.getElementById('loginPassword').value;

      if (!email || !password) {
        alertBox.innerHTML = `
          <div class="alert alert--danger mb-4">
            ${ui.icon('alert-circle', 'icon alert-icon')}
            <div class="alert-content">Por favor ingresa tu correo y contraseña.</div>
          </div>
        `;
        return;
      }

      ui.setButtonLoading(btnSubmit, true);

      try {
        await auth.login(email, password);
        ui.showToast('Inicio de sesión exitoso.', 'success');

        const redirect = queryParams.get('redirect');
        if (redirect) {
          router.navigate(decodeURIComponent(redirect));
        } else {
          router.redirectToHome();
        }
      } catch (err) {
        alertBox.innerHTML = `
          <div class="alert alert--danger mb-4">
            ${ui.icon('alert-circle', 'icon alert-icon')}
            <div class="alert-content">
              <div class="alert-title">No fue posible ingresar</div>
              <div>${err.message || 'Credenciales inválidas.'}</div>
            </div>
          </div>
        `;
      } finally {
        ui.setButtonLoading(btnSubmit, false);
      }
    });
  }, { guestOnly: true });

  // Ruta 3: Registro de Paciente
  router.addRoute('/register', async (container) => {
    container.innerHTML = `
      <div class="container-narrow" style="padding-top: var(--space-6);">
        <div class="card" style="padding: var(--space-8);">
          <div class="text-center mb-6">
            <h1 class="text-2xl font-bold mb-2">Crear Cuenta de Paciente</h1>
            <p class="text-sm text-muted">Regístrate para agendar citas y gestionar tu historia médica</p>
          </div>

          <form id="formRegister" novalidate>
            <div id="registerAlertContainer"></div>

            <div class="grid grid-cols-1 grid-cols-2-md gap-4">
              <div class="form-group">
                <label for="regTipoDoc" class="form-label">Tipo de Documento <span class="required">*</span></label>
                <select id="regTipoDoc" class="form-select" required>
                  <option value="CC" selected>Cédula de Ciudadanía (CC)</option>
                  <option value="TI">Tarjeta de Identidad (TI)</option>
                  <option value="CE">Cédula de Extranjería (CE)</option>
                  <option value="PA">Pasaporte (PA)</option>
                </select>
              </div>

              <div class="form-group">
                <label for="regNumDoc" class="form-label">Número de Documento <span class="required">*</span></label>
                <input type="text" id="regNumDoc" class="form-input" placeholder="Ej. 1098765432" required>
              </div>
            </div>

            <div class="grid grid-cols-1 grid-cols-2-md gap-4">
              <div class="form-group">
                <label for="regNombres" class="form-label">Nombres <span class="required">*</span></label>
                <input type="text" id="regNombres" class="form-input" placeholder="Ej. Carlos" required autocomplete="given-name">
              </div>

              <div class="form-group">
                <label for="regApellidos" class="form-label">Apellidos <span class="required">*</span></label>
                <input type="text" id="regApellidos" class="form-input" placeholder="Ej. Pérez Gómez" required autocomplete="family-name">
              </div>
            </div>

            <div class="grid grid-cols-1 grid-cols-2-md gap-4">
              <div class="form-group">
                <label for="regFechaNac" class="form-label">Fecha de Nacimiento <span class="required">*</span></label>
                <input type="date" id="regFechaNac" class="form-input" required autocomplete="bday">
              </div>

              <div class="form-group">
                <label for="regTelefono" class="form-label">Teléfono móvil</label>
                <input type="tel" id="regTelefono" class="form-input" placeholder="3001234567" autocomplete="tel">
              </div>
            </div>

            <div class="form-group">
              <label for="regEmail" class="form-label">Correo electrónico <span class="required">*</span></label>
              <input type="email" id="regEmail" class="form-input" placeholder="ejemplo@correo.com" required autocomplete="email">
            </div>

            <div class="form-group">
              <label for="regPassword" class="form-label">Contraseña <span class="required">*</span></label>
              <input type="password" id="regPassword" class="form-input" placeholder="Mínimo 10 caracteres" required autocomplete="new-password">
              <span class="form-help">Debe incluir al menos 10 caracteres y combinar mayúsculas, números o símbolos.</span>
            </div>

            <div class="form-check mt-3 mb-4">
              <input type="checkbox" id="regConsentimiento" required>
              <label for="regConsentimiento" class="form-check-label">
                Acepto el tratamiento de datos de salud y los términos de uso asistencial bajo la Ley 1581 de 2012 (versión v1.0).
              </label>
            </div>

            <button type="submit" id="btnRegisterSubmit" class="btn btn-primary w-full">
              <span>Registrarme como paciente</span>
            </button>
          </form>

          <div class="text-center mt-6 pt-4 border-top">
            <span class="text-sm text-muted">¿Ya tienes una cuenta?</span>
            <a href="#/login" class="text-sm font-semibold text-primary" style="margin-left: 4px;">Inicia sesión</a>
          </div>
        </div>
      </div>
    `;

    const form = document.getElementById('formRegister');
    const btnSubmit = document.getElementById('btnRegisterSubmit');
    const alertBox = document.getElementById('registerAlertContainer');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      alertBox.innerHTML = '';

      const tipoDocumento = document.getElementById('regTipoDoc').value;
      const numeroDocumento = document.getElementById('regNumDoc').value.trim();
      const nombres = document.getElementById('regNombres').value.trim();
      const apellidos = document.getElementById('regApellidos').value.trim();
      const fechaNacimiento = document.getElementById('regFechaNac').value;
      const telefono = document.getElementById('regTelefono').value.trim();
      const email = document.getElementById('regEmail').value.trim();
      const password = document.getElementById('regPassword').value;
      const aceptaConsentimiento = document.getElementById('regConsentimiento').checked;

      if (!numeroDocumento || !nombres || !apellidos || !fechaNacimiento || !email || !password) {
        alertBox.innerHTML = `
          <div class="alert alert--danger mb-4">
            ${ui.icon('alert-circle', 'icon alert-icon')}
            <div class="alert-content">Por favor completa todos los campos requeridos (*).</div>
          </div>
        `;
        return;
      }

      if (!aceptaConsentimiento) {
        alertBox.innerHTML = `
          <div class="alert alert--warning mb-4">
            ${ui.icon('alert-triangle', 'icon alert-icon')}
            <div class="alert-content">Debes aceptar el consentimiento de datos personales para continuar.</div>
          </div>
        `;
        return;
      }

      ui.setButtonLoading(btnSubmit, true);

      try {
        await auth.register({
          tipoDocumento,
          numeroDocumento,
          nombres,
          apellidos,
          fechaNacimiento,
          telefono: telefono || null,
          email,
          password,
          consentimientoTextoVersion: 'v1.0',
          aceptaConsentimiento: true
        });

        ui.showToast('Cuenta de paciente creada exitosamente.', 'success');
        router.navigate('/patient/dashboard');
      } catch (err) {
        alertBox.innerHTML = `
          <div class="alert alert--danger mb-4">
            ${ui.icon('alert-circle', 'icon alert-icon')}
            <div class="alert-content">
              <div class="alert-title">Error en el registro</div>
              <div>${err.message || 'No fue posible registrar la cuenta.'}</div>
            </div>
          </div>
        `;
      } finally {
        ui.setButtonLoading(btnSubmit, false);
      }
    });
  }, { guestOnly: true });

  // Rutas base para los paneles de cada rol (se desarrollarán completamente en M8.2..M8.4)
  router.addRoute('/patient/dashboard', async (container) => {
    container.innerHTML = `
      <div class="sg-section">
        <div class="flex items-center justify-between mb-6">
          <div>
            <h1 class="text-2xl font-bold">Panel del Paciente</h1>
            <p class="text-muted">Bienvenido a tu portal asistencial personal</p>
          </div>
          <span class="badge badge--confirmed">Paciente activo</span>
        </div>

        <div class="card card--highlight mb-6">
          <div class="card-header">
            <h3 class="card-title">¿Necesitas atención médica hoy?</h3>
          </div>
          <div class="card-body">
            <p class="text-sm mb-4">Inicia la valoración de triaje para determinar la prioridad asistencial y sugerirte la ruta adecuada.</p>
            <button type="button" class="btn btn-primary" onclick="alert('El módulo de triaje y citas se habilitará en las siguientes tareas M8.2')">
              ${ui.icon('activity')}
              <span>Iniciar nuevo triaje</span>
            </button>
          </div>
        </div>
      </div>
    `;
  }, { requiresAuth: true, requiredRole: 'ROLE_PACIENTE' });

  router.addRoute('/professional/agenda', async (container) => {
    container.innerHTML = `
      <div class="sg-section">
        <h1 class="text-2xl font-bold mb-2">Agenda Asistencial del Profesional</h1>
        <p class="text-muted mb-6">Visualización de citas asignadas del día y atención médica</p>
        <div class="card">
          <p class="text-sm">Pantalla en construcción para M8.3.</p>
        </div>
      </div>
    `;
  }, { requiresAuth: true, requiredRole: 'ROLE_PROFESIONAL' });

  router.addRoute('/admin/dashboard', async (container) => {
    container.innerHTML = `
      <div class="sg-section">
        <h1 class="text-2xl font-bold mb-2">Administración del Sistema</h1>
        <p class="text-muted mb-6">Gestión de catálogos institucionales, profesionales y slots de disponibilidad</p>
        <div class="card">
          <p class="text-sm">Pantalla en construcción para M8.4.</p>
        </div>
      </div>
    `;
  }, { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });

  // Manejador 404 No Encontrado
  router.notFound(async (container, { path }) => {
    ui.renderEmpty(container, {
      icon: 'alert-circle',
      title: 'Página no encontrada (404)',
      description: `La ruta "${path}" no existe o fue movida.`,
      actionText: 'Volver al inicio',
      onAction: () => router.navigate('/')
    });
  });

  // Manejador 403 Prohibido
  router.forbidden(async (container, { path, requiredRole }) => {
    ui.renderEmpty(container, {
      icon: 'shield',
      title: 'Acceso Denegado (403)',
      description: `No tienes los privilegios requeridos (${requiredRole}) para acceder a este recurso.`,
      actionText: 'Volver a mi panel',
      onAction: () => router.redirectToHome()
    });
  });
}

// Inicialización de la Aplicación
async function bootstrap() {
  initTheme();
  setupRoutes();

  // Escuchar cambios de autenticación para sincronizar la barra superior
  auth.addEventListener('auth:change', () => {
    updateNavbar();
  });

  // Restaurar sesión activa de forma transparente
  await auth.init();
  updateNavbar();

  // Arrancar el enrutador
  await router.start();
}

document.addEventListener('DOMContentLoaded', bootstrap);
