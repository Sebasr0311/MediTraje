/**
 * MediTriaje 2.0 — Menú de Navegación Lateral Accesible (app-sidebar.js)
 *
 * Características:
 * - Menú lateral a la izquierda con diseño Rail colapsado (solo iconos) en reposo.
 * - Expansión fluida por hover (mouseenter/mouseleave) para visualización rápida de opciones.
 * - Botón de fijación (Pin toggle) con aria-pressed para bloquearlo abierto de forma permanente.
 * - Persistencia del estado fijado en localStorage ('sidebar_pinned').
 * - Soporte de accesibilidad total WCAG 2.1 AA:
 *   - WCAG 1.4.13 (Content on Hover or Focus): dismissable con tecla Escape, persistent mientras el cursor esté encima.
 *   - WCAG 2.1.1 (Keyboard Accessible): se expande en :focus-within para navegación por teclado con Tab.
 *   - WCAG 2.4.1 (Bypass Blocks): compatible con skip-link al contenido principal.
 *   - WCAG 4.1.2 (Name, Role, Value): landmark nav con aria-label, aria-pressed en pin, aria-current="page" en ruta activa.
 * - Drawer modal en pantallas móviles (< 768px) con botón hamburguesa accesible y backdrop.
 */

import { auth } from '../auth.js';
import { ui, esc } from '../ui.js';

let sidebarElement = null;
let backdropElement = null;
let toggleBtnNavbar = null;

const STORAGE_PIN_KEY = 'meditriaje_sidebar_pinned';

/**
 * Genera la configuración de navegación según los roles del usuario autenticado.
 */
function getNavigationConfig() {
  const sections = [];

  if (auth.isPaciente) {
    sections.push({
      title: 'Mi Salud',
      items: [
        { label: 'Inicio', href: '#/patient/dashboard', icon: 'home' },
        { label: 'Triaje Virtual', href: '#/patient/triage', icon: 'activity' },
        { label: 'Mis Citas Médicas', href: '#/patient/appointments', icon: 'calendar' },
        { label: 'Mi Historia Clínica', href: '#/patient/history', icon: 'file-text' },
        { label: 'Mis Recetas', href: '#/patient/prescriptions', icon: 'pill' },
        { label: 'Carnet QR Urgencias', href: '#/patient/emergency-qr', icon: 'qr-code' },
        { label: 'Alergias y Registro', href: '#/patient/allergies', icon: 'shield-check' }
      ]
    });
  }

  if (auth.isEnfermeria) {
    sections.push({
      title: 'Urgencias y Triaje',
      items: [
        { label: 'Panel de Enfermería', href: '#/nursing/dashboard', icon: 'activity' },
        { label: 'Admisión de Urgencias', href: '#/nursing/admission', icon: 'user-plus' },
        { label: 'Censo de Camas', href: '#/hospital/census', icon: 'grid' },
        { label: 'Aseguramiento EPS', href: '#/affiliations/search', icon: 'shield-check' },
        { label: 'Centro de Mando Operativo', href: '#/operational/dashboard', icon: 'sliders' }
      ]
    });
  }

  if (auth.isProfesional) {
    sections.push({
      title: 'Atención Médica',
      items: [
        { label: 'Agenda y Pacientes', href: '#/professional/agenda', icon: 'calendar' },
        { label: 'Historias Clínicas', href: '#/professional/patient-history', icon: 'file-text' },
        { label: 'Censo Hospitalario', href: '#/hospital/census', icon: 'grid' },
        { label: 'Centro de Mando', href: '#/operational/dashboard', icon: 'sliders' },
        { label: 'Aseguramiento EPS', href: '#/affiliations/search', icon: 'shield-check' }
      ]
    });
  }

  if (auth.isAdmin) {
    sections.push({
      title: 'Gestión y Operación',
      items: [
        { label: 'Panel de Control', href: '#/admin/dashboard', icon: 'shield' },
        { label: 'Profesionales de la Salud', href: '#/admin/professionals', icon: 'users' },
        { label: 'Censo Hospitalario', href: '#/hospital/census', icon: 'grid' },
        { label: 'Gestión de Camas', href: '#/hospital/beds', icon: 'layers' },
        { label: 'Importación EPS (BDUA)', href: '#/affiliations/import', icon: 'database' },
        { label: 'Aseguramiento EPS', href: '#/affiliations/search', icon: 'shield-check' },
        { label: 'Centro de Mando y Analítica', href: '#/operational/dashboard', icon: 'sliders' }
      ]
    });
  }

  if (auth.isFarmaceutico) {
    sections.push({
      title: 'Servicio Farmacéutico',
      items: [
        { label: 'Dispensación Farmacia', href: '#/pharmacy/dispensation', icon: 'pill' },
        { label: 'Aseguramiento EPS', href: '#/affiliations/search', icon: 'shield-check' },
        { label: 'Centro de Mando', href: '#/operational/dashboard', icon: 'sliders' }
      ]
    });
  }

  return sections;
}

/**
 * Obtiene el nombre del rol legible para el pie del sidebar.
 */
function getRoleBadgeInfo() {
  if (auth.isAdmin) return { name: 'Administrador', badgeClass: 'badge--rescheduled' };
  if (auth.isProfesional) return { name: 'Profesional', badgeClass: 'badge--scheduled' };
  if (auth.isEnfermeria) return { name: 'Enfermería', badgeClass: 'badge--in-progress' };
  if (auth.isFarmaceutico) return { name: 'Farmacia', badgeClass: 'badge--confirmed' };
  return { name: 'Paciente', badgeClass: 'badge--confirmed' };
}

/**
 * Inicializa la barra lateral accesible.
 */
export function initSidebar() {
  sidebarElement = document.getElementById('app-sidebar');
  backdropElement = document.getElementById('sidebar-backdrop');
  toggleBtnNavbar = document.getElementById('sidebarToggleBtn');

  if (!sidebarElement) return;

  // Restaurar estado fijado desde localStorage
  const isPinned = localStorage.getItem(STORAGE_PIN_KEY) === 'true';
  setPinnedState(isPinned);

  // Evento mouseenter / mouseleave para expansión suave por hover
  sidebarElement.addEventListener('mouseenter', () => {
    if (!sidebarElement.classList.contains('is-pinned')) {
      sidebarElement.classList.add('is-hovered');
    }
  });

  sidebarElement.addEventListener('mouseleave', () => {
    sidebarElement.classList.remove('is-hovered');
  });

  // Eventos de teclado (WCAG 1.4.13 Dismissable y 2.1.1 Keyboard Accessible)
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (sidebarElement.classList.contains('is-mobile-open')) {
        closeMobileSidebar();
      } else if (sidebarElement.classList.contains('is-hovered')) {
        sidebarElement.classList.remove('is-hovered');
        // Quitar foco activo de enlaces para cumplir criterio dismissable
        if (sidebarElement.contains(document.activeElement)) {
          document.activeElement.blur();
        }
      }
    }
  });

  // Delegación de eventos unificada para el menú lateral (Pin, Cerrar móvil, Backdrop, Toggle)
  document.addEventListener('click', (e) => {
    // 1. Cerrar drawer móvil con botón 'X'
    const closeBtn = e.target.closest('#btnCloseSidebarMobile');
    if (closeBtn) {
      e.stopPropagation();
      closeMobileSidebar();
      return;
    }

    // 2. Alternar estado fijado (Pin)
    const pinBtn = e.target.closest('#btnPinSidebar');
    if (pinBtn) {
      e.stopPropagation();
      togglePinSidebar();
      return;
    }

    // 3. Clic en enlace de navegación dentro de móvil cierra el drawer
    const link = e.target.closest('.sidebar-link');
    if (link && sidebarElement?.classList.contains('is-mobile-open')) {
      closeMobileSidebar();
      return;
    }

    // 4. Clic en backdrop cierra el drawer móvil
    if (e.target.closest('#sidebar-backdrop')) {
      closeMobileSidebar();
      return;
    }

    // 5. Botón de menú hamburguesa alterna el drawer móvil
    const toggleBtn = e.target.closest('#sidebarToggleBtn');
    if (toggleBtn) {
      const isOpen = sidebarElement?.classList.contains('is-mobile-open');
      if (isOpen) {
        closeMobileSidebar();
      } else {
        openMobileSidebar();
      }
    }
  });

  // Sincronizar ruta activa en hashchange
  window.addEventListener('hashchange', () => {
    updateActiveRouteLinks();
    if (sidebarElement.classList.contains('is-mobile-open')) {
      closeMobileSidebar();
    }
  });

  // Render inicial según autenticación
  updateSidebar();
}

/**
 * Abre la barra lateral en vista móvil con gestión de accesibilidad.
 */
function openMobileSidebar() {
  if (!sidebarElement) return;
  sidebarElement.classList.add('is-mobile-open');
  const backdrop = document.getElementById('sidebar-backdrop');
  if (backdrop) backdrop.classList.add('is-visible');
  const toggleBtn = document.getElementById('sidebarToggleBtn');
  if (toggleBtn) {
    toggleBtn.setAttribute('aria-expanded', 'true');
    toggleBtn.classList.add('is-active');
  }
  // Mover foco al primer enlace del menú accesible
  const firstLink = sidebarElement.querySelector('.sidebar-link, .sidebar-pin-btn');
  if (firstLink) firstLink.focus();
}

/**
 * Cierra la barra lateral en vista móvil.
 */
function closeMobileSidebar() {
  if (!sidebarElement) return;
  sidebarElement.classList.remove('is-mobile-open');
  const backdrop = document.getElementById('sidebar-backdrop');
  if (backdrop) backdrop.classList.remove('is-visible');
  const toggleBtn = document.getElementById('sidebarToggleBtn');
  if (toggleBtn) {
    toggleBtn.setAttribute('aria-expanded', 'false');
    toggleBtn.classList.remove('is-active');
  }
}

/**
 * Aplica o retira el estado fijado (pinned) permanente.
 */
function setPinnedState(pinned) {
  if (!sidebarElement) return;
  const pinBtn = document.getElementById('btnPinSidebar');

  if (pinned) {
    sidebarElement.classList.add('is-pinned');
    document.body.classList.add('sidebar-is-pinned');
    localStorage.setItem(STORAGE_PIN_KEY, 'true');
    if (pinBtn) {
      pinBtn.setAttribute('aria-pressed', 'true');
      pinBtn.setAttribute('title', 'Desanclar menú lateral (modo hover)');
      pinBtn.classList.add('is-active');
    }
  } else {
    sidebarElement.classList.remove('is-pinned');
    document.body.classList.remove('sidebar-is-pinned');
    localStorage.setItem(STORAGE_PIN_KEY, 'false');
    if (pinBtn) {
      pinBtn.setAttribute('aria-pressed', 'false');
      pinBtn.setAttribute('title', 'Fijar menú lateral (mantener abierto)');
      pinBtn.classList.remove('is-active');
    }
  }
}

/**
 * Alterna el estado fijado.
 */
export function togglePinSidebar() {
  const isCurrentlyPinned = sidebarElement?.classList.contains('is-pinned');
  setPinnedState(!isCurrentlyPinned);
  ui.showToast(
    !isCurrentlyPinned ? 'Menú lateral fijado.' : 'Menú lateral desanclado (se expande al pasar el cursor).',
    'info',
    2500
  );
}

/**
 * Actualiza la estructura completa del menú lateral según la sesión activa.
 */
export function updateSidebar() {
  if (!sidebarElement) return;

  if (!auth.isAuthenticated) {
    sidebarElement.style.display = 'none';
    document.body.classList.remove('has-sidebar');
    if (toggleBtnNavbar) toggleBtnNavbar.style.display = 'none';
    return;
  }

  sidebarElement.style.display = 'flex';
  document.body.classList.add('has-sidebar');
  if (toggleBtnNavbar) toggleBtnNavbar.style.display = 'inline-flex';

  const sections = getNavigationConfig();
  const roleInfo = getRoleBadgeInfo();
  const user = auth.user || {};
  const currentHash = window.location.hash || '#/';
  const isPinned = localStorage.getItem(STORAGE_PIN_KEY) === 'true';

  let sectionsHtml = '';
  sections.forEach((sec, idx) => {
    sectionsHtml += `
      <div class="sidebar-section">
        <div class="sidebar-section-title" id="sec-title-${idx}">
          <span class="sidebar-section-text">${esc(sec.title)}</span>
        </div>
        <ul class="sidebar-nav" role="list" aria-labelledby="sec-title-${idx}">
          ${sec.items.map(item => {
            const isActive = currentHash === item.href || (item.href !== '#/' && currentHash.startsWith(item.href));
            return `
              <li class="sidebar-item">
                <a href="${item.href}" 
                   class="sidebar-link ${isActive ? 'is-active' : ''}" 
                   data-route="${item.href}"
                   aria-label="${esc(item.label)}"
                   ${isActive ? 'aria-current="page"' : ''}>
                  <span class="sidebar-icon" aria-hidden="true">${ui.icon(item.icon, 'icon')}</span>
                  <span class="sidebar-label">${esc(item.label)}</span>
                </a>
              </li>
            `;
          }).join('')}
        </ul>
      </div>
    `;
  });

  sidebarElement.innerHTML = `
    <!-- Cabecera del Menú Lateral Accesible -->
    <div class="sidebar-header">
      <div class="sidebar-brand-group">
        <span class="sidebar-brand-badge" aria-hidden="true">${ui.icon('hospital', 'icon text-primary')}</span>
        <span class="sidebar-brand-title font-bold">MediTriaje</span>
      </div>

      <div class="flex items-center gap-1">
        <!-- Botón de Fijación (Pin / Lock) Accesible en Desktop -->
        <button type="button" 
                id="btnPinSidebar" 
                class="sidebar-pin-btn ${isPinned ? 'is-active' : ''}" 
                aria-label="Fijar menú lateral para mantenerlo abierto" 
                aria-pressed="${isPinned ? 'true' : 'false'}" 
                title="${isPinned ? 'Desanclar menú lateral (modo hover)' : 'Fijar menú lateral (mantener abierto)'}">
          ${ui.icon('pin', 'icon icon--sm')}
          <span class="sr-only">Fijar menú</span>
        </button>

        <!-- Botón accesible de cierre en móvil -->
        <button type="button" 
                id="btnCloseSidebarMobile" 
                class="sidebar-close-btn" 
                aria-label="Cerrar menú lateral" 
                title="Cerrar menú">
          ${ui.icon('x', 'icon icon--sm')}
        </button>
      </div>
    </div>

    <!-- Contenido de Enlaces de Navegación -->
    <div class="sidebar-body" id="sidebar-nav-container">
      ${sectionsHtml}
    </div>

    <!-- Pie del Menú con Perfil y Rol del Usuario -->
    <div class="sidebar-footer">
      <div class="sidebar-user-card" title="${esc(user.email || '')}">
        <span class="sidebar-user-avatar" aria-hidden="true">
          ${esc((user.email || 'U').charAt(0).toUpperCase())}
        </span>
        <div class="sidebar-user-info">
          <span class="sidebar-user-email truncate">${esc(user.email || '')}</span>
          <span class="badge ${roleInfo.badgeClass} sidebar-user-badge">${roleInfo.name}</span>
        </div>
      </div>
    </div>
  `;

  document.getElementById('btnCloseSidebarMobile')?.addEventListener('click', (e) => {
    console.log('[DEBUG] btnCloseSidebarMobile DIRECT listener fired');
    e.stopPropagation();
    closeMobileSidebar();
  });

  // Re-aplicar estado visual de pinned
  setPinnedState(isPinned);
}

/**
 * Actualiza la clase is-active y aria-current="page" en los enlaces según el hash actual.
 */
export function updateActiveRouteLinks() {
  if (!sidebarElement) return;
  const currentHash = window.location.hash || '#/';
  const links = sidebarElement.querySelectorAll('.sidebar-link');

  links.forEach(link => {
    const route = link.getAttribute('data-route');
    const isActive = route && (currentHash === route || (route !== '#/' && currentHash.startsWith(route)));
    if (isActive) {
      link.classList.add('is-active');
      link.setAttribute('aria-current', 'page');
    } else {
      link.classList.remove('is-active');
      link.removeAttribute('aria-current');
    }
  });
}
