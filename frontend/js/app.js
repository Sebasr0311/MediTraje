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
import { patientAllergiesView } from './views/patient-allergies.js';
import { emergencySummaryView } from './views/emergency-summary-view.js';
import { professionalAgendaView } from './views/professional-agenda.js';
import { professionalAttentionView } from './views/professional-attention.js';
import { professionalPrescriptionView } from './views/professional-prescription.js';
import { professionalPatientHistoryView } from './views/professional-patient-history.js';
import { adminDashboardView } from './views/admin-views.js';
import { pharmacyDispensationView } from './views/pharmacy-dispensation.js';
import { landingView } from './views/landing-view.js';
import { nursingDashboardView } from './views/nursing-dashboard.js';
import { nursingAdmissionView } from './views/nursing-admission.js';
import { nursingAssessmentView } from './views/nursing-assessment.js';
import { nursingIdentityView } from './views/nursing-identity.js';
import { initSystemAssistantWidget } from './views/system-assistant-widget.js';

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
  const brandLink = document.querySelector('.navbar-brand');

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
    } else if (auth.isFarmaceutico) {
      roleName = 'Farmacia';
      roleBadgeClass = 'badge--confirmed';
      dashboardLink = '#/pharmacy/dispensation';
    } else if (auth.isEnfermeria) {
      roleName = 'Enfermería';
      roleBadgeClass = 'badge--in-progress';
      dashboardLink = '#/nursing/dashboard';
    }

    if (brandLink) {
      brandLink.href = dashboardLink;
      brandLink.setAttribute('aria-label', `Ir a mi panel de MediTriaje 2.0 (${roleName})`);
    }

    if (navContainer) {
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
    }

    // Mostrar barra inferior en móviles solo para pacientes
    if (bottomNav) {
      bottomNav.style.display = auth.isPaciente ? 'flex' : 'none';
    }
  } else {
    if (brandLink) {
      brandLink.href = '#/';
      brandLink.setAttribute('aria-label', 'Ir al inicio de MediTriaje 2.0');
    }

    if (navContainer) {
      navContainer.innerHTML = `
        <a href="#/login" class="nav-link font-medium">Iniciar sesión</a>
        <a href="#/register" class="btn btn-primary btn--sm">Registrarme</a>
      `;
    }

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
  router.addRoute('/', landingView);

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
  router.addRoute('/patient/allergies', patientAllergiesView, { requiresAuth: true, requiredRole: 'ROLE_PACIENTE' });

  // Ruta pública: Resumen de Emergencia por Token QR (ADR-010, F2.3.5)
  router.addRoute('/emergency-summary/:token', emergencySummaryView);


  // Rutas M8.3: Agenda, Atención Clínica y Recetas del Profesional Asistencial
  router.addRoute('/professional/agenda', professionalAgendaView, { requiresAuth: true, requiredRole: 'ROLE_PROFESIONAL' });
  router.addRoute('/professional/attention/:id', professionalAttentionView, { requiresAuth: true, requiredRole: 'ROLE_PROFESIONAL' });
  router.addRoute('/professional/prescription/:atencionId', professionalPrescriptionView, { requiresAuth: true, requiredRole: 'ROLE_PROFESIONAL' });
  router.addRoute('/professional/patient-history/:patientPublicId', professionalPatientHistoryView, { requiresAuth: true, requiredRole: 'ROLE_PROFESIONAL' });

  // Rutas M8.4: Administración del Sistema (Oferta Asistencial, Infraestructura y Slots)
  router.addRoute('/admin', adminDashboardView, { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/dashboard', adminDashboardView, { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/institutions', (c) => adminDashboardView(c, { tab: 'institutions' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/sites', (c) => adminDashboardView(c, { tab: 'sites' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/specialties', (c) => adminDashboardView(c, { tab: 'specialties' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/professionals', (c) => adminDashboardView(c, { tab: 'professionals' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/slots', (c) => adminDashboardView(c, { tab: 'slots' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/appointments', (c) => adminDashboardView(c, { tab: 'appointments' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/reports', (c) => adminDashboardView(c, { tab: 'reports' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });
  router.addRoute('/admin/audit', (c) => adminDashboardView(c, { tab: 'audit' }), { requiresAuth: true, requiredRole: 'ROLE_ADMINISTRADOR' });

  // Rutas F2.4: Ventanilla de Dispensación Farmacéutica (ADR-016)
  router.addRoute('/pharmacy/dispensation', pharmacyDispensationView, { requiresAuth: true, requiredRole: 'ROLE_FARMACEUTICO' });
  router.addRoute('/pharmacy', pharmacyDispensationView, { requiresAuth: true, requiredRole: 'ROLE_FARMACEUTICO' });

  // Rutas Fase U: Circuito de Enfermería, Urgencias Presenciales y Triaje I-V (U01-U07)
  router.addRoute('/nursing/dashboard', nursingDashboardView, { requiresAuth: true, requiredRole: 'ROLE_ENFERMERIA' });
  router.addRoute('/nursing', nursingDashboardView, { requiresAuth: true, requiredRole: 'ROLE_ENFERMERIA' });
  router.addRoute('/nursing/admission', nursingAdmissionView, { requiresAuth: true, requiredRole: 'ROLE_ENFERMERIA' });
  router.addRoute('/nursing/assessment/:id', nursingAssessmentView, { requiresAuth: true });
  router.addRoute('/nursing/identity/:id', nursingIdentityView, { requiresAuth: true, requiredRole: 'ROLE_ENFERMERIA' });

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

  // Interceptar clic en el logo de MediTriaje para usuarios autenticados
  document.querySelector('.navbar-brand')?.addEventListener('click', (e) => {
    if (auth.isAuthenticated) {
      e.preventDefault();
      router.redirectToHome();
    }
  });

  // Inicializar widget interactivo del asistente de orientación
  initSystemAssistantWidget();

  // Arrancar el enrutador
  await router.start();
}

document.addEventListener('DOMContentLoaded', bootstrap);

