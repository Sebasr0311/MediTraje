/**
 * MediTriaje 2.0 — Entrada Principal de la Aplicación Frontend (app.js)
 * Inicialización de autenticación, enrutador, barra de navegación y vistas base.
 */

import { auth } from './auth.js';
import { router } from './router.js';
import { ui } from './ui.js';
import { loginView, registerView, forgotPasswordView } from './views/auth-views.js';
import { patientDashboardView } from './views/patient-dashboard.js';
import { patientTriageView } from './views/patient-triage.js';
import { patientBookingView } from './views/patient-booking.js';
import { patientAppointmentsView } from './views/patient-appointments.js';
import { patientHistoryView } from './views/patient-history.js';
import { patientPrescriptionsView } from './views/patient-prescriptions.js';
import { patientFollowUpsView } from './views/patient-follow-ups.js';
import { patientEmergencyQrView } from './views/patient-emergency-qr.js';
import { emergencySummaryView } from './views/emergency-summary-view.js';
import { professionalAgendaView } from './views/professional-agenda.js';
import { professionalAttentionView } from './views/professional-attention.js';
import { professionalPrescriptionView } from './views/professional-prescription.js';
import { adminDashboardView } from './views/admin-views.js';

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
        <span class="font-medium nav-user-email">${user.email}</span>
        <span class="badge ${roleBadgeClass}" style="margin-left: 4px;">${roleName}</span>
      </a>
      <button type="button" id="btnLogout" class="btn btn-ghost btn--sm" title="Cerrar sesion activa" aria-label="Cerrar sesión">
        ${ui.icon('log-out', 'icon icon--sm')}
        <span class="nav-logout-text">Salir</span>
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

  // Ruta 2: Login (M8.2a)
  router.addRoute('/login', loginView, { guestOnly: true });

  // Ruta 2.1: Recuperación de contraseña (F2.1.3, F2.1.5)
  router.addRoute('/forgot-password', forgotPasswordView, { guestOnly: true });

  // Ruta 3: Registro de Paciente en 2 pasos (M8.2a)
  router.addRoute('/register', registerView, { guestOnly: true });

  // Ruta 4: Dashboard del Paciente (M8.2a)
  router.addRoute('/patient/dashboard', patientDashboardView, { requiresAuth: true, requiredRole: 'ROLE_PACIENTE' });

  // Rutas M8.2b: Triaje Clínico, Corte de Emergencia y Agendamiento
  router.addRoute('/patient/triage', patientTriageView, { requiresAuth: true, requiredRole: 'ROLE_PACIENTE' });
  router.addRoute('/patient/triage/:id', patientTriageView, { requiresAuth: true, requiredRole: 'ROLE_PACIENTE' });
  router.addRoute('/patient/book', patientBookingView, { requiresAuth: true, requiredRole: 'ROLE_PACIENTE' });
  router.addRoute('/availability', patientBookingView, { requiresAuth: true });

  // Rutas M8.2c: Citas, Historia Clínica y Recetas Médicas del Paciente
  router.addRoute('/patient/appointments', patientAppointmentsView, { requiresAuth: true, requiredRole: 'ROLE_PACIENTE' });
  router.addRoute('/patient/history', patientHistoryView, { requiresAuth: true, requiredRole: 'ROLE_PACIENTE' });
  router.addRoute('/patient/prescriptions', patientPrescriptionsView, { requiresAuth: true, requiredRole: 'ROLE_PACIENTE' });
  router.addRoute('/patient/follow-ups', patientFollowUpsView, { requiresAuth: true, requiredRole: 'ROLE_PACIENTE' });
  router.addRoute('/patient/emergency-qr', patientEmergencyQrView, { requiresAuth: true, requiredRole: 'ROLE_PACIENTE' });

  // Ruta pública: Resumen de Emergencia por Token QR (ADR-010, F2.3.5)
  router.addRoute('/emergency-summary/:token', emergencySummaryView);


  // Rutas M8.3: Agenda, Atención Clínica y Recetas del Profesional Asistencial
  router.addRoute('/professional/agenda', professionalAgendaView, { requiresAuth: true, requiredRole: 'ROLE_PROFESIONAL' });
  router.addRoute('/professional/attention/:id', professionalAttentionView, { requiresAuth: true, requiredRole: 'ROLE_PROFESIONAL' });
  router.addRoute('/professional/prescription/:atencionId', professionalPrescriptionView, { requiresAuth: true, requiredRole: 'ROLE_PROFESIONAL' });

  // Rutas M8.4: Administración del Sistema (Oferta Asistencial, Infraestructura y Slots)
  router.addRoute('/admin', adminDashboardView, { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/dashboard', adminDashboardView, { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/institutions', (c) => adminDashboardView(c, { tab: 'institutions' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/sites', (c) => adminDashboardView(c, { tab: 'sites' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/specialties', (c) => adminDashboardView(c, { tab: 'specialties' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/professionals', (c) => adminDashboardView(c, { tab: 'professionals' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/slots', (c) => adminDashboardView(c, { tab: 'slots' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });

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
