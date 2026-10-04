/**
 * MediTriaje 2.0 — Pantallas de Administración (admin-views.js)
 * M8.4 (HU-10, ADR-002, ADR-003, ADR-005, ADR-007, ADR-011, ADR-012).
 * CRUD de Instituciones, Sedes, Especialidades, Profesionales y Generador de Slots.
 * REGLA ESTRICTA: Cero exposición de contenido clínico a roles administrativos.
 */

import { api } from '../api.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';
import { showMfaModal } from './mfa-setup-modal.js';
import { renderReports } from './admin-reports.js';
import { renderAudit } from './admin-audit.js';

/** Fecha de hoy en formato YYYY-MM-DD en zona America/Bogota. */
function todayBogota() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date());
}

/** Suma días a una fecha ISO YYYY-MM-DD. */
function addDays(isoDate, days) {
  const d = new Date(isoDate + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** Formatea fecha y hora en zona America/Bogota. */
function formatDateTime(iso) {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('es-CO', {
    timeZone: 'America/Bogota',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true
  }).format(new Date(iso));
}

/** Badge accesible para estados de catálogos y turnos. */
function statusBadge(estado) {
  if (estado === 'ACTIVO' || estado === 'LIBRE') {
    return `<span class="badge badge--confirmed">${ui.icon('check', 'icon icon--sm')} ${esc(estado)}</span>`;
  }
  if (estado === 'INACTIVO' || estado === 'BLOQUEADO') {
    return `<span class="badge badge--cancelled">${ui.icon('x', 'icon icon--sm')} ${esc(estado)}</span>`;
  }
  if (estado === 'OCUPADO') {
    return `<span class="badge badge--scheduled">${ui.icon('calendar', 'icon icon--sm')} ${esc(estado)}</span>`;
  }
  return `<span class="badge badge--neutral">${esc(estado)}</span>`;
}

/* ==========================================================================
   VISTA PRINCIPAL DEL PANEL ADMINISTRATIVO
   ========================================================================== */
export async function adminDashboardView(container, { tab = 'institutions' } = {}) {
  // Resolver pestaña activa desde parámetros o ruta
  const hash = window.location.hash;
  let activeTab = tab;
  if (hash.includes('/admin/sites')) activeTab = 'sites';
  else if (hash.includes('/admin/specialties')) activeTab = 'specialties';
  else if (hash.includes('/admin/professionals')) activeTab = 'professionals';
  else if (hash.includes('/admin/slots')) activeTab = 'slots';
  else if (hash.includes('/admin/reports')) activeTab = 'reports';
  else if (hash.includes('/admin/audit')) activeTab = 'audit';
  else if (hash.includes('/admin/institutions')) activeTab = 'institutions';

  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-bottom: var(--space-12);">
      <div class="mb-6 flex justify-between items-center flex-wrap gap-4">
        <div>
          <h1 class="text-2xl font-bold mb-1">Administración del Sistema</h1>
          <p class="text-sm text-muted m-0">Gestión de infraestructura, profesionales asistenciales, slots, reportes y auditoría</p>
        </div>
        <button type="button" id="btnAdminMfa" class="btn btn-secondary btn--sm">
          ${ui.icon('shield', 'icon icon--sm')}
          <span>Seguridad MFA</span>
        </button>
      </div>

      <div class="alert alert--info mb-6" role="note">
        ${ui.icon('shield', 'icon alert-icon')}
        <div class="alert-content">
          <p class="m-0 text-xs">
            <strong>Aislamiento de Privilegios:</strong> El rol administrativo no tiene acceso a historias clínicas,
            diagnósticos ni recetas (ADR-007). Gestiona la oferta asistencial, bitácora de eventos e indicadores operativos agregados.
          </p>
        </div>
      </div>

      <!-- Navegación por pestañas -->
      <nav class="admin-tabs" aria-label="Secciones de administración">
        <button type="button" class="admin-tab ${activeTab === 'institutions' ? 'is-active' : ''}" data-tab="institutions">
          ${ui.icon('hospital', 'icon icon--sm')}<span>Instituciones</span>
        </button>
        <button type="button" class="admin-tab ${activeTab === 'sites' ? 'is-active' : ''}" data-tab="sites">
          ${ui.icon('map-pin', 'icon icon--sm')}<span>Sedes</span>
        </button>
        <button type="button" class="admin-tab ${activeTab === 'specialties' ? 'is-active' : ''}" data-tab="specialties">
          ${ui.icon('activity', 'icon icon--sm')}<span>Especialidades</span>
        </button>
        <button type="button" class="admin-tab ${activeTab === 'professionals' ? 'is-active' : ''}" data-tab="professionals">
          ${ui.icon('user', 'icon icon--sm')}<span>Profesionales</span>
        </button>
        <button type="button" class="admin-tab ${activeTab === 'slots' ? 'is-active' : ''}" data-tab="slots">
          ${ui.icon('calendar', 'icon icon--sm')}<span>Slots de turnos</span>
        </button>
        <button type="button" class="admin-tab ${activeTab === 'reports' ? 'is-active' : ''}" data-tab="reports">
          ${ui.icon('bar-chart', 'icon icon--sm')}<span>Reportes</span>
        </button>
        <button type="button" class="admin-tab ${activeTab === 'audit' ? 'is-active' : ''}" data-tab="audit">
          ${ui.icon('shield', 'icon icon--sm')}<span>Auditoría</span>
        </button>
      </nav>

      <!-- Contenedor del contenido de la pestaña activa -->
      <div id="adminTabContent" aria-live="polite"></div>
    </div>
  `;

  const contentEl = container.querySelector('#adminTabContent');

  function switchTab(t) {
    activeTab = t;
    container.querySelectorAll('.admin-tab').forEach(b => {
      b.classList.toggle('is-active', b.dataset.tab === t);
    });
    // Actualizar hash sin provocar recarga completa
    history.replaceState(null, '', `#/admin/${t}`);
    renderActiveTab();
  }

  container.querySelectorAll('.admin-tab').forEach(btn => {
    btn.addEventListener('click', () => switchTab(btn.dataset.tab));
  });

  function renderActiveTab() {
    if (activeTab === 'sites') renderSites(contentEl);
    else if (activeTab === 'specialties') renderSpecialties(contentEl);
    else if (activeTab === 'professionals') renderProfessionals(contentEl);
    else if (activeTab === 'slots') renderSlots(contentEl);
    else if (activeTab === 'reports') renderReports(contentEl);
    else if (activeTab === 'audit') renderAudit(contentEl);
    else renderInstitutions(contentEl);
  }

  container.querySelector('#btnAdminMfa')?.addEventListener('click', () => {
    showMfaModal();
  });

  renderActiveTab();
}

/* ==========================================================================
   1. PESTAÑA: INSTITUCIONES
   ========================================================================== */
async function renderInstitutions(container) {
  let page = 0;
  const size = 10;
  let estadoFiltro = '';

  container.innerHTML = `
    <div>
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 class="text-xl font-bold m-0">Instituciones de Salud</h2>
        <button type="button" id="btnToggleNewInst" class="btn btn-primary btn--sm">
          ${ui.icon('check', 'icon icon--sm')}<span>+ Nueva institución</span>
        </button>
      </div>

      <!-- Formulario colapsable de creación -->
      <div id="newInstCard" class="card p-4 mb-6" style="display: none; border-left: 4px solid var(--primary);">
        <h3 class="text-md font-bold mb-3">Registrar nueva institución</h3>
        <form id="newInstForm" novalidate>
          <div class="grid grid-cols-1 grid-cols-2-md gap-4 mb-3">
            <div class="form-group m-0">
              <label for="instNit" class="form-label text-xs">NIT <span class="required">*</span></label>
              <input type="text" id="instNit" class="form-input" maxlength="20" placeholder="Ej. 900123456-1" required>
            </div>
            <div class="form-group m-0">
              <label for="instRazonSocial" class="form-label text-xs">Razón social <span class="required">*</span></label>
              <input type="text" id="instRazonSocial" class="form-input" maxlength="120" placeholder="Ej. Hospital San Rafael" required>
            </div>
          </div>
          <p id="newInstError" class="text-xs text-danger mb-3" role="alert"></p>
          <div class="flex gap-2 justify-end">
            <button type="button" id="btnCancelNewInst" class="btn btn-secondary btn--sm">Cancelar</button>
            <button type="submit" id="btnSaveNewInst" class="btn btn-primary btn--sm">Guardar institución</button>
          </div>
        </form>
      </div>

      <!-- Filtro rápido y tabla -->
      <div class="flex items-center justify-between gap-3 mb-3">
        <div class="form-group m-0" style="max-width: 14rem;">
          <select id="instEstadoFilter" class="form-select">
            <option value="">Todos los estados</option>
            <option value="ACTIVO">Solo Activos</option>
            <option value="INACTIVO">Solo Inactivos</option>
          </select>
        </div>
        <div id="instPaginationInfo" class="text-xs text-muted"></div>
      </div>

      <div id="instTableContainer"></div>
    </div>
  `;

  const newCard = container.querySelector('#newInstCard');
  const toggleBtn = container.querySelector('#btnToggleNewInst');
  toggleBtn.addEventListener('click', () => {
    newCard.style.display = newCard.style.display === 'none' ? 'block' : 'none';
    if (newCard.style.display === 'block') container.querySelector('#instNit').focus();
  });
  container.querySelector('#btnCancelNewInst').addEventListener('click', () => {
    newCard.style.display = 'none';
  });

  container.querySelector('#newInstForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const nit = container.querySelector('#instNit').value.trim();
    const razonSocial = container.querySelector('#instRazonSocial').value.trim();
    const errEl = container.querySelector('#newInstError');
    errEl.textContent = '';

    if (!nit || !razonSocial) {
      errEl.textContent = 'El NIT y la razón social son obligatorios.';
      return;
    }

    const saveBtn = container.querySelector('#btnSaveNewInst');
    ui.setButtonLoading(saveBtn, true);
    try {
      await api.post('/admin/institutions', { nit, razonSocial });
      ui.showToast('Institución registrada correctamente.', 'success');
      newCard.style.display = 'none';
      container.querySelector('#newInstForm').reset();
      load();
    } catch (err) {
      errEl.textContent = err.message || 'No fue posible guardar la institución.';
    } finally {
      ui.setButtonLoading(saveBtn, false);
    }
  });

  container.querySelector('#instEstadoFilter').addEventListener('change', (e) => {
    estadoFiltro = e.target.value;
    page = 0;
    load();
  });

  async function load() {
    const tableBox = container.querySelector('#instTableContainer');
    ui.renderLoading(tableBox, 'Cargando instituciones...');
    try {
      const res = await api.get('/admin/institutions', {
        page,
        size,
        estado: estadoFiltro || undefined
      });
      renderTable(res);
    } catch (err) {
      ui.renderError(tableBox, {
        title: 'Error al consultar instituciones',
        message: err.message,
        onRetry: load
      });
    }
  }

  function renderTable(data) {
    const list = data?.content || [];
    const tableBox = container.querySelector('#instTableContainer');
    container.querySelector('#instPaginationInfo').textContent =
      `Mostrando ${list.length} de ${data?.totalElements || 0} registros`;

    if (list.length === 0) {
      ui.renderEmpty(tableBox, {
        icon: 'hospital',
        title: 'Sin instituciones',
        description: 'No se encontraron instituciones registradas con los filtros actuales.'
      });
      return;
    }

    tableBox.innerHTML = `
      <div class="table-container mb-4">
        <table class="table table--stacked">
          <thead>
            <tr>
              <th>NIT</th>
              <th>Razón Social</th>
              <th>Estado</th>
              <th style="text-align: right;">Acciones</th>
            </tr>
          </thead>
          <tbody>
            ${list.map(it => `
              <tr>
                <td data-label="NIT"><span class="font-mono">${esc(it.nit)}</span></td>
                <td data-label="Razón Social"><strong>${esc(it.razonSocial)}</strong></td>
                <td data-label="Estado">${statusBadge(it.estado)}</td>
                <td data-label="Acciones" style="text-align: right;">
                  <button type="button" class="btn btn-secondary btn--sm btn-edit" data-id="${esc(it.publicId)}" data-razon="${esc(it.razonSocial)}">
                    Editar
                  </button>
                  ${it.estado === 'ACTIVO' ? `
                    <button type="button" class="btn btn-ghost btn--sm text-danger btn-deactivate" data-id="${esc(it.publicId)}" data-razon="${esc(it.razonSocial)}">
                      Desactivar
                    </button>` : `
                    <button type="button" class="btn btn-ghost btn--sm text-success btn-activate" data-id="${esc(it.publicId)}" data-razon="${esc(it.razonSocial)}">
                      Activar
                    </button>`}
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>

      <!-- Paginación -->
      <div class="flex items-center justify-between gap-3">
        <button type="button" id="instPrev" class="btn btn-secondary btn--sm" ${!data?.hasPrevious ? 'disabled' : ''}>Anterior</button>
        <span class="text-xs text-muted">Página ${(data?.page || 0) + 1} de ${Math.max(1, data?.totalPages || 1)}</span>
        <button type="button" id="instNext" class="btn btn-secondary btn--sm" ${!data?.hasNext ? 'disabled' : ''}>Siguiente</button>
      </div>
    `;

    tableBox.querySelector('#instPrev')?.addEventListener('click', () => { if (page > 0) { page--; load(); } });
    tableBox.querySelector('#instNext')?.addEventListener('click', () => { page++; load(); });

    // Editar
    tableBox.querySelectorAll('.btn-edit').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const currentRazon = btn.dataset.razon;
        ui.showModal({
          title: 'Editar Institución',
          message: `
            <div class="form-group mb-2">
              <label for="editRazonInput" class="form-label text-xs">Nueva razón social</label>
              <input type="text" id="editRazonInput" class="form-input" value="${esc(currentRazon)}" maxlength="120">
            </div>
            <p id="editRazonErr" class="text-xs text-danger m-0"></p>
          `,
          confirmText: 'Guardar cambios',
          onConfirm: async () => {
            const val = document.querySelector('#editRazonInput')?.value.trim();
            if (!val) {
              ui.showToast('La razón social no puede estar vacía.', 'warning');
              return;
            }
            try {
              await api.put(`/admin/institutions/${id}`, { razonSocial: val });
              ui.showToast('Institución actualizada.', 'success');
              load();
            } catch (err) {
              ui.showToast(err.message || 'No fue posible actualizar.', 'danger');
            }
          }
        });
      });
    });

    // Desactivar
    tableBox.querySelectorAll('.btn-deactivate').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const razon = btn.dataset.razon;
        ui.showModal({
          title: '¿Desactivar institución?',
          message: `La institución <strong>${esc(razon)}</strong> quedará inactiva y no podrán asignársele nuevas sedes.`,
          confirmText: 'Sí, desactivar',
          isDanger: true,
          onConfirm: async () => {
            try {
              await api.patch(`/admin/institutions/${id}/deactivate`);
              ui.showToast('Institución desactivada.', 'info');
              load();
            } catch (err) {
              ui.showToast(err.message || 'Error al desactivar.', 'danger');
            }
          }
        });
      });
    });

    // Activar
    tableBox.querySelectorAll('.btn-activate').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const razon = btn.dataset.razon;
        ui.showModal({
          title: '¿Activar institución?',
          message: `La institución <strong>${esc(razon)}</strong> pasará a estar activa.`,
          confirmText: 'Sí, activar',
          onConfirm: async () => {
            try {
              await api.patch(`/admin/institutions/${id}/activate`);
              ui.showToast('Institución activada.', 'success');
              load();
            } catch (err) {
              ui.showToast(err.message || 'Error al activar.', 'danger');
            }
          }
        });
      });
    });
  }

  await load();
}

/* ==========================================================================
   2. PESTAÑA: SEDES
   ========================================================================== */
async function renderSites(container) {
  let page = 0;
  const size = 10;
  let institucionFiltro = '';
  let estadoFiltro = '';
  let activeInstitutions = [];

  container.innerHTML = `
    <div>
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 class="text-xl font-bold m-0">Sedes Asistenciales</h2>
        <button type="button" id="btnToggleNewSite" class="btn btn-primary btn--sm">
          ${ui.icon('check', 'icon icon--sm')}<span>+ Nueva sede</span>
        </button>
      </div>

      <!-- Formulario colapsable de creación -->
      <div id="newSiteCard" class="card p-4 mb-6" style="display: none; border-left: 4px solid var(--primary);">
        <h3 class="text-md font-bold mb-3">Registrar nueva sede</h3>
        <form id="newSiteForm" novalidate>
          <div class="grid grid-cols-1 grid-cols-2-md gap-4 mb-3">
            <div class="form-group m-0">
              <label for="siteInstSelect" class="form-label text-xs">Institución <span class="required">*</span></label>
              <select id="siteInstSelect" class="form-select" required>
                <option value="">Selecciona una institución...</option>
              </select>
            </div>
            <div class="form-group m-0">
              <label for="siteNombre" class="form-label text-xs">Nombre de la sede <span class="required">*</span></label>
              <input type="text" id="siteNombre" class="form-input" maxlength="80" placeholder="Ej. Sede Norte Principal" required>
            </div>
            <div class="form-group m-0">
              <label for="siteDireccion" class="form-label text-xs">Dirección <span class="required">*</span></label>
              <input type="text" id="siteDireccion" class="form-input" maxlength="120" placeholder="Ej. Calle 100 # 15-20" required>
            </div>
            <div class="form-group m-0">
              <label for="siteCiudad" class="form-label text-xs">Ciudad <span class="required">*</span></label>
              <input type="text" id="siteCiudad" class="form-input" maxlength="60" placeholder="Ej. Bogotá D.C." required>
            </div>
          </div>
          <p id="newSiteError" class="text-xs text-danger mb-3" role="alert"></p>
          <div class="flex gap-2 justify-end">
            <button type="button" id="btnCancelNewSite" class="btn btn-secondary btn--sm">Cancelar</button>
            <button type="submit" id="btnSaveNewSite" class="btn btn-primary btn--sm">Guardar sede</button>
          </div>
        </form>
      </div>

      <!-- Filtros -->
      <div class="grid grid-cols-1 grid-cols-3-md gap-3 mb-3">
        <div class="form-group m-0">
          <select id="siteInstFilter" class="form-select">
            <option value="">Todas las instituciones</option>
          </select>
        </div>
        <div class="form-group m-0">
          <select id="siteEstadoFilter" class="form-select">
            <option value="">Todos los estados</option>
            <option value="ACTIVO">Solo Activos</option>
            <option value="INACTIVO">Solo Inactivos</option>
          </select>
        </div>
        <div id="sitePaginationInfo" class="text-xs text-muted flex items-center justify-end"></div>
      </div>

      <div id="siteTableContainer"></div>
    </div>
  `;

  // Cargar instituciones activas para el select del formulario y el filtro
  try {
    const resInst = await api.get('/admin/institutions', { page: 0, size: 100, estado: 'ACTIVO' });
    activeInstitutions = resInst?.content || [];
    const selectEl = container.querySelector('#siteInstSelect');
    const filterEl = container.querySelector('#siteInstFilter');
    activeInstitutions.forEach(inst => {
      selectEl.insertAdjacentHTML('beforeend', `<option value="${esc(inst.publicId)}">${esc(inst.razonSocial)}</option>`);
      filterEl.insertAdjacentHTML('beforeend', `<option value="${esc(inst.publicId)}">${esc(inst.razonSocial)}</option>`);
    });
  } catch {
    // Si falla, el select quedará con la opción default
  }

  const newCard = container.querySelector('#newSiteCard');
  container.querySelector('#btnToggleNewSite').addEventListener('click', () => {
    newCard.style.display = newCard.style.display === 'none' ? 'block' : 'none';
  });
  container.querySelector('#btnCancelNewSite').addEventListener('click', () => {
    newCard.style.display = 'none';
  });

  container.querySelector('#newSiteForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const institucionPublicId = container.querySelector('#siteInstSelect').value;
    const nombre = container.querySelector('#siteNombre').value.trim();
    const direccion = container.querySelector('#siteDireccion').value.trim();
    const ciudad = container.querySelector('#siteCiudad').value.trim();
    const errEl = container.querySelector('#newSiteError');
    errEl.textContent = '';

    if (!institucionPublicId || !nombre || !direccion || !ciudad) {
      errEl.textContent = 'Todos los campos son obligatorios.';
      return;
    }

    const saveBtn = container.querySelector('#btnSaveNewSite');
    ui.setButtonLoading(saveBtn, true);
    try {
      await api.post('/admin/sites', { institucionPublicId, nombre, direccion, ciudad });
      ui.showToast('Sede registrada correctamente.', 'success');
      newCard.style.display = 'none';
      container.querySelector('#newSiteForm').reset();
      load();
    } catch (err) {
      errEl.textContent = err.message || 'No fue posible registrar la sede.';
    } finally {
      ui.setButtonLoading(saveBtn, false);
    }
  });

  container.querySelector('#siteInstFilter').addEventListener('change', (e) => {
    institucionFiltro = e.target.value;
    page = 0;
    load();
  });

  container.querySelector('#siteEstadoFilter').addEventListener('change', (e) => {
    estadoFiltro = e.target.value;
    page = 0;
    load();
  });

  async function load() {
    const tableBox = container.querySelector('#siteTableContainer');
    ui.renderLoading(tableBox, 'Cargando sedes...');
    try {
      const res = await api.get('/admin/sites', {
        page,
        size,
        institucionPublicId: institucionFiltro || undefined,
        estado: estadoFiltro || undefined
      });
      renderTable(res);
    } catch (err) {
      ui.renderError(tableBox, {
        title: 'Error al consultar sedes',
        message: err.message,
        onRetry: load
      });
    }
  }

  function renderTable(data) {
    const list = data?.content || [];
    const tableBox = container.querySelector('#siteTableContainer');
    container.querySelector('#sitePaginationInfo').textContent =
      `Mostrando ${list.length} de ${data?.totalElements || 0} registros`;

    if (list.length === 0) {
      ui.renderEmpty(tableBox, {
        icon: 'map-pin',
        title: 'Sin sedes',
        description: 'No se encontraron sedes con los filtros aplicados.'
      });
      return;
    }

    tableBox.innerHTML = `
      <div class="table-container mb-4">
        <table class="table table--stacked">
          <thead>
            <tr>
              <th>Sede</th>
              <th>Institución</th>
              <th>Ubicación</th>
              <th>Estado</th>
              <th style="text-align: right;">Acciones</th>
            </tr>
          </thead>
          <tbody>
            ${list.map(it => `
              <tr>
                <td data-label="Sede"><strong>${esc(it.nombre)}</strong></td>
                <td data-label="Institución"><span class="text-sm">${esc(it.institucionRazonSocial || '—')}</span></td>
                <td data-label="Ubicación">${esc(it.direccion)}, ${esc(it.ciudad)}</td>
                <td data-label="Estado">${statusBadge(it.estado)}</td>
                <td data-label="Acciones" style="text-align: right;">
                  <button type="button" class="btn btn-secondary btn--sm btn-edit"
                    data-id="${esc(it.publicId)}"
                    data-nombre="${esc(it.nombre)}"
                    data-dir="${esc(it.direccion)}"
                    data-ciudad="${esc(it.ciudad)}">
                    Editar
                  </button>
                  ${it.estado === 'ACTIVO' ? `
                    <button type="button" class="btn btn-ghost btn--sm text-danger btn-deactivate" data-id="${esc(it.publicId)}" data-nombre="${esc(it.nombre)}">
                      Desactivar
                    </button>` : `
                    <button type="button" class="btn btn-ghost btn--sm text-success btn-activate" data-id="${esc(it.publicId)}" data-nombre="${esc(it.nombre)}">
                      Activar
                    </button>`}
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>

      <!-- Paginación -->
      <div class="flex items-center justify-between gap-3">
        <button type="button" id="sitePrev" class="btn btn-secondary btn--sm" ${!data?.hasPrevious ? 'disabled' : ''}>Anterior</button>
        <span class="text-xs text-muted">Página ${(data?.page || 0) + 1} de ${Math.max(1, data?.totalPages || 1)}</span>
        <button type="button" id="siteNext" class="btn btn-secondary btn--sm" ${!data?.hasNext ? 'disabled' : ''}>Siguiente</button>
      </div>
    `;

    tableBox.querySelector('#sitePrev')?.addEventListener('click', () => { if (page > 0) { page--; load(); } });
    tableBox.querySelector('#siteNext')?.addEventListener('click', () => { page++; load(); });

    // Editar sede
    tableBox.querySelectorAll('.btn-edit').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const curNombre = btn.dataset.nombre;
        const curDir = btn.dataset.dir;
        const curCiudad = btn.dataset.ciudad;

        ui.showModal({
          title: 'Editar Sede',
          message: `
            <div class="form-group mb-2">
              <label for="editSiteNombre" class="form-label text-xs">Nombre de la sede</label>
              <input type="text" id="editSiteNombre" class="form-input" value="${esc(curNombre)}" maxlength="80">
            </div>
            <div class="form-group mb-2">
              <label for="editSiteDir" class="form-label text-xs">Dirección</label>
              <input type="text" id="editSiteDir" class="form-input" value="${esc(curDir)}" maxlength="120">
            </div>
            <div class="form-group mb-2">
              <label for="editSiteCiudad" class="form-label text-xs">Ciudad</label>
              <input type="text" id="editSiteCiudad" class="form-input" value="${esc(curCiudad)}" maxlength="60">
            </div>
          `,
          confirmText: 'Guardar cambios',
          onConfirm: async () => {
            const nombre = document.querySelector('#editSiteNombre')?.value.trim();
            const direccion = document.querySelector('#editSiteDir')?.value.trim();
            const ciudad = document.querySelector('#editSiteCiudad')?.value.trim();
            if (!nombre || !direccion || !ciudad) {
              ui.showToast('Todos los campos son obligatorios.', 'warning');
              return;
            }
            try {
              await api.put(`/admin/sites/${id}`, { nombre, direccion, ciudad });
              ui.showToast('Sede actualizada.', 'success');
              load();
            } catch (err) {
              ui.showToast(err.message || 'Error al actualizar sede.', 'danger');
            }
          }
        });
      });
    });

    // Desactivar sede
    tableBox.querySelectorAll('.btn-deactivate').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const nombre = btn.dataset.nombre;
        ui.showModal({
          title: '¿Desactivar sede?',
          message: `La sede <strong>${esc(nombre)}</strong> no estará disponible para nuevos slots.`,
          confirmText: 'Sí, desactivar',
          isDanger: true,
          onConfirm: async () => {
            try {
              await api.patch(`/admin/sites/${id}/deactivate`);
              ui.showToast('Sede desactivada.', 'info');
              load();
            } catch (err) {
              ui.showToast(err.message || 'Error al desactivar sede.', 'danger');
            }
          }
        });
      });
    });

    // Activar sede
    tableBox.querySelectorAll('.btn-activate').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const nombre = btn.dataset.nombre;
        ui.showModal({
          title: '¿Activar sede?',
          message: `La sede <strong>${esc(nombre)}</strong> pasará a estar activa.`,
          confirmText: 'Sí, activar',
          onConfirm: async () => {
            try {
              await api.patch(`/admin/sites/${id}/activate`);
              ui.showToast('Sede activada.', 'success');
              load();
            } catch (err) {
              ui.showToast(err.message || 'Error al activar sede.', 'danger');
            }
          }
        });
      });
    });
  }

  await load();
}

/* ==========================================================================
   3. PESTAÑA: ESPECIALIDADES
   ========================================================================== */
async function renderSpecialties(container) {
  let page = 0;
  const size = 10;
  let estadoFiltro = '';

  container.innerHTML = `
    <div>
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 class="text-xl font-bold m-0">Especialidades Médicas</h2>
        <button type="button" id="btnToggleNewSpec" class="btn btn-primary btn--sm">
          ${ui.icon('check', 'icon icon--sm')}<span>+ Nueva especialidad</span>
        </button>
      </div>

      <!-- Formulario de creación -->
      <div id="newSpecCard" class="card p-4 mb-6" style="display: none; border-left: 4px solid var(--primary);">
        <h3 class="text-md font-bold mb-3">Registrar nueva especialidad</h3>
        <form id="newSpecForm" novalidate>
          <div class="grid grid-cols-1 grid-cols-2-md gap-4 mb-3">
            <div class="form-group m-0">
              <label for="specNombre" class="form-label text-xs">Nombre de especialidad <span class="required">*</span></label>
              <input type="text" id="specNombre" class="form-input" maxlength="60" placeholder="Ej. Pediatría" required>
            </div>
            <div class="form-group m-0">
              <label for="specDuracion" class="form-label text-xs">Duración estándar de turno (minutos) <span class="required">*</span></label>
              <input type="number" id="specDuracion" class="form-input" min="5" max="240" value="20" required>
            </div>
          </div>
          <p id="newSpecError" class="text-xs text-danger mb-3" role="alert"></p>
          <div class="flex gap-2 justify-end">
            <button type="button" id="btnCancelNewSpec" class="btn btn-secondary btn--sm">Cancelar</button>
            <button type="submit" id="btnSaveNewSpec" class="btn btn-primary btn--sm">Guardar especialidad</button>
          </div>
        </form>
      </div>

      <!-- Filtros -->
      <div class="flex items-center justify-between gap-3 mb-3">
        <div class="form-group m-0" style="max-width: 14rem;">
          <select id="specEstadoFilter" class="form-select">
            <option value="">Todos los estados</option>
            <option value="ACTIVO">Solo Activos</option>
            <option value="INACTIVO">Solo Inactivos</option>
          </select>
        </div>
        <div id="specPaginationInfo" class="text-xs text-muted"></div>
      </div>

      <div id="specTableContainer"></div>
    </div>
  `;

  const newCard = container.querySelector('#newSpecCard');
  container.querySelector('#btnToggleNewSpec').addEventListener('click', () => {
    newCard.style.display = newCard.style.display === 'none' ? 'block' : 'none';
  });
  container.querySelector('#btnCancelNewSpec').addEventListener('click', () => {
    newCard.style.display = 'none';
  });

  container.querySelector('#newSpecForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const nombre = container.querySelector('#specNombre').value.trim();
    const duracionSlotMin = Number(container.querySelector('#specDuracion').value);
    const errEl = container.querySelector('#newSpecError');
    errEl.textContent = '';

    if (!nombre) {
      errEl.textContent = 'El nombre de la especialidad es obligatorio.';
      return;
    }
    if (!duracionSlotMin || duracionSlotMin < 5 || duracionSlotMin > 240) {
      errEl.textContent = 'La duración debe estar entre 5 y 240 minutos.';
      return;
    }

    const saveBtn = container.querySelector('#btnSaveNewSpec');
    ui.setButtonLoading(saveBtn, true);
    try {
      await api.post('/admin/specialties', { nombre, duracionSlotMin });
      ui.showToast('Especialidad registrada correctamente.', 'success');
      newCard.style.display = 'none';
      container.querySelector('#newSpecForm').reset();
      load();
    } catch (err) {
      errEl.textContent = err.message || 'No fue posible guardar la especialidad.';
    } finally {
      ui.setButtonLoading(saveBtn, false);
    }
  });

  container.querySelector('#specEstadoFilter').addEventListener('change', (e) => {
    estadoFiltro = e.target.value;
    page = 0;
    load();
  });

  async function load() {
    const tableBox = container.querySelector('#specTableContainer');
    ui.renderLoading(tableBox, 'Cargando especialidades...');
    try {
      const res = await api.get('/admin/specialties', {
        page,
        size,
        estado: estadoFiltro || undefined
      });
      renderTable(res);
    } catch (err) {
      ui.renderError(tableBox, {
        title: 'Error al consultar especialidades',
        message: err.message,
        onRetry: load
      });
    }
  }

  function renderTable(data) {
    const list = data?.content || [];
    const tableBox = container.querySelector('#specTableContainer');
    container.querySelector('#specPaginationInfo').textContent =
      `Mostrando ${list.length} de ${data?.totalElements || 0} registros`;

    if (list.length === 0) {
      ui.renderEmpty(tableBox, {
        icon: 'activity',
        title: 'Sin especialidades',
        description: 'No se encontraron especialidades con los filtros aplicados.'
      });
      return;
    }

    tableBox.innerHTML = `
      <div class="table-container mb-4">
        <table class="table table--stacked">
          <thead>
            <tr>
              <th>Especialidad</th>
              <th>Duración de slot</th>
              <th>Estado</th>
              <th style="text-align: right;">Acciones</th>
            </tr>
          </thead>
          <tbody>
            ${list.map(it => `
              <tr>
                <td data-label="Especialidad"><strong>${esc(it.nombre)}</strong></td>
                <td data-label="Duración">${it.duracionSlotMin} minutos</td>
                <td data-label="Estado">${statusBadge(it.estado)}</td>
                <td data-label="Acciones" style="text-align: right;">
                  <button type="button" class="btn btn-secondary btn--sm btn-edit"
                    data-id="${esc(it.publicId)}"
                    data-nombre="${esc(it.nombre)}"
                    data-duracion="${it.duracionSlotMin}">
                    Editar
                  </button>
                  ${it.estado === 'ACTIVO' ? `
                    <button type="button" class="btn btn-ghost btn--sm text-danger btn-deactivate" data-id="${esc(it.publicId)}" data-nombre="${esc(it.nombre)}">
                      Desactivar
                    </button>` : `
                    <button type="button" class="btn btn-ghost btn--sm text-success btn-activate" data-id="${esc(it.publicId)}" data-nombre="${esc(it.nombre)}">
                      Activar
                    </button>`}
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>

      <!-- Paginación -->
      <div class="flex items-center justify-between gap-3">
        <button type="button" id="specPrev" class="btn btn-secondary btn--sm" ${!data?.hasPrevious ? 'disabled' : ''}>Anterior</button>
        <span class="text-xs text-muted">Página ${(data?.page || 0) + 1} de ${Math.max(1, data?.totalPages || 1)}</span>
        <button type="button" id="specNext" class="btn btn-secondary btn--sm" ${!data?.hasNext ? 'disabled' : ''}>Siguiente</button>
      </div>
    `;

    tableBox.querySelector('#specPrev')?.addEventListener('click', () => { if (page > 0) { page--; load(); } });
    tableBox.querySelector('#specNext')?.addEventListener('click', () => { page++; load(); });

    // Editar
    tableBox.querySelectorAll('.btn-edit').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const curNombre = btn.dataset.nombre;
        const curDur = btn.dataset.duracion;

        ui.showModal({
          title: 'Editar Especialidad',
          message: `
            <div class="form-group mb-2">
              <label for="editSpecNombre" class="form-label text-xs">Nombre de la especialidad</label>
              <input type="text" id="editSpecNombre" class="form-input" value="${esc(curNombre)}" maxlength="60">
            </div>
            <div class="form-group mb-2">
              <label for="editSpecDur" class="form-label text-xs">Duración de slot (minutos)</label>
              <input type="number" id="editSpecDur" class="form-input" value="${esc(curDur)}" min="5" max="240">
            </div>
          `,
          confirmText: 'Guardar cambios',
          onConfirm: async () => {
            const nombre = document.querySelector('#editSpecNombre')?.value.trim();
            const duracionSlotMin = Number(document.querySelector('#editSpecDur')?.value);
            if (!nombre || !duracionSlotMin || duracionSlotMin < 5 || duracionSlotMin > 240) {
              ui.showToast('Datos inválidos para la especialidad.', 'warning');
              return;
            }
            try {
              await api.put(`/admin/specialties/${id}`, { nombre, duracionSlotMin });
              ui.showToast('Especialidad actualizada.', 'success');
              load();
            } catch (err) {
              ui.showToast(err.message || 'Error al actualizar.', 'danger');
            }
          }
        });
      });
    });

    // Desactivar
    tableBox.querySelectorAll('.btn-deactivate').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const nombre = btn.dataset.nombre;
        ui.showModal({
          title: '¿Desactivar especialidad?',
          message: `La especialidad <strong>${esc(nombre)}</strong> no podrá asociarse a nuevos profesionales.`,
          confirmText: 'Sí, desactivar',
          isDanger: true,
          onConfirm: async () => {
            try {
              await api.patch(`/admin/specialties/${id}/deactivate`);
              ui.showToast('Especialidad desactivada.', 'info');
              load();
            } catch (err) {
              ui.showToast(err.message || 'Error al desactivar.', 'danger');
            }
          }
        });
      });
    });

    // Activar
    tableBox.querySelectorAll('.btn-activate').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const nombre = btn.dataset.nombre;
        ui.showModal({
          title: '¿Activar especialidad?',
          message: `La especialidad <strong>${esc(nombre)}</strong> pasará a estar activa.`,
          confirmText: 'Sí, activar',
          onConfirm: async () => {
            try {
              await api.patch(`/admin/specialties/${id}/activate`);
              ui.showToast('Especialidad activada.', 'success');
              load();
            } catch (err) {
              ui.showToast(err.message || 'Error al activar.', 'danger');
            }
          }
        });
      });
    });
  }

  await load();
}

/* ==========================================================================
   4. PESTAÑA: PROFESIONALES
   ========================================================================== */
async function renderProfessionals(container) {
  let page = 0;
  const size = 10;
  let especialidadFiltro = '';
  let estadoFiltro = '';
  let activeSpecialties = [];

  container.innerHTML = `
    <div>
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 class="text-xl font-bold m-0">Profesionales Asistenciales</h2>
        <button type="button" id="btnToggleNewProf" class="btn btn-primary btn--sm">
          ${ui.icon('check', 'icon icon--sm')}<span>+ Nuevo profesional</span>
        </button>
      </div>

      <!-- Formulario colapsable de alta de profesional -->
      <div id="newProfCard" class="card p-4 mb-6" style="display: none; border-left: 4px solid var(--primary);">
        <h3 class="text-md font-bold mb-3">Alta de profesional asistencial</h3>
        <p class="text-xs text-muted mb-3">
          El sistema generará una contraseña temporal de alta entropía con Argon2id que deberás entregar al médico para su primer acceso.
        </p>
        <form id="newProfForm" novalidate>
          <div class="grid grid-cols-1 grid-cols-2-md gap-4 mb-3">
            <div class="form-group m-0">
              <label for="profRegistro" class="form-label text-xs">Registro médico <span class="required">*</span></label>
              <input type="text" id="profRegistro" class="form-input" maxlength="30" placeholder="Ej. RM-987654" required>
            </div>
            <div class="form-group m-0">
              <label for="profEspecialidad" class="form-label text-xs">Especialidad <span class="required">*</span></label>
              <select id="profEspecialidad" class="form-select" required>
                <option value="">Selecciona especialidad...</option>
              </select>
            </div>
            <div class="form-group m-0">
              <label for="profNombres" class="form-label text-xs">Nombres <span class="required">*</span></label>
              <input type="text" id="profNombres" class="form-input" maxlength="60" placeholder="Ej. Camila" required>
            </div>
            <div class="form-group m-0">
              <label for="profApellidos" class="form-label text-xs">Apellidos <span class="required">*</span></label>
              <input type="text" id="profApellidos" class="form-input" maxlength="60" placeholder="Ej. Gómez Silva" required>
            </div>
            <div class="form-group m-0 grid-cols-2-span">
              <label for="profEmail" class="form-label text-xs">Correo electrónico institucional <span class="required">*</span></label>
              <input type="email" id="profEmail" class="form-input" maxlength="100" placeholder="Ej. dra.gomez@meditriaje.com" required>
            </div>
          </div>
          <p id="newProfError" class="text-xs text-danger mb-3" role="alert"></p>
          <div class="flex gap-2 justify-end">
            <button type="button" id="btnCancelNewProf" class="btn btn-secondary btn--sm">Cancelar</button>
            <button type="submit" id="btnSaveNewProf" class="btn btn-primary btn--sm">Dar de alta</button>
          </div>
        </form>
      </div>

      <!-- Filtros -->
      <div class="grid grid-cols-1 grid-cols-3-md gap-3 mb-3">
        <div class="form-group m-0">
          <select id="profSpecFilter" class="form-select">
            <option value="">Todas las especialidades</option>
          </select>
        </div>
        <div class="form-group m-0">
          <select id="profEstadoFilter" class="form-select">
            <option value="">Todos los estados</option>
            <option value="ACTIVO">Solo Activos</option>
            <option value="INACTIVO">Solo Inactivos</option>
          </select>
        </div>
        <div id="profPaginationInfo" class="text-xs text-muted flex items-center justify-end"></div>
      </div>

      <div id="profTableContainer"></div>
    </div>
  `;

  // Cargar especialidades activas
  try {
    const resSpec = await api.get('/admin/specialties', { page: 0, size: 100, estado: 'ACTIVO' });
    activeSpecialties = resSpec?.content || [];
    const selectEl = container.querySelector('#profEspecialidad');
    const filterEl = container.querySelector('#profSpecFilter');
    activeSpecialties.forEach(sp => {
      selectEl.insertAdjacentHTML('beforeend', `<option value="${esc(sp.publicId)}">${esc(sp.nombre)}</option>`);
      filterEl.insertAdjacentHTML('beforeend', `<option value="${esc(sp.publicId)}">${esc(sp.nombre)}</option>`);
    });
  } catch {
    // Si falla, el select permanece
  }

  const newCard = container.querySelector('#newProfCard');
  container.querySelector('#btnToggleNewProf').addEventListener('click', () => {
    newCard.style.display = newCard.style.display === 'none' ? 'block' : 'none';
  });
  container.querySelector('#btnCancelNewProf').addEventListener('click', () => {
    newCard.style.display = 'none';
  });

  container.querySelector('#newProfForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const registroMedico = container.querySelector('#profRegistro').value.trim();
    const especialidadPublicId = container.querySelector('#profEspecialidad').value;
    const nombres = container.querySelector('#profNombres').value.trim();
    const apellidos = container.querySelector('#profApellidos').value.trim();
    const email = container.querySelector('#profEmail').value.trim().toLowerCase();
    const errEl = container.querySelector('#newProfError');
    errEl.textContent = '';

    if (!registroMedico || !especialidadPublicId || !nombres || !apellidos || !email) {
      errEl.textContent = 'Todos los campos son obligatorios.';
      return;
    }

    const saveBtn = container.querySelector('#btnSaveNewProf');
    ui.setButtonLoading(saveBtn, true);
    try {
      const res = await api.post('/admin/professionals', {
        registroMedico,
        nombres,
        apellidos,
        email,
        especialidadPublicId
      });

      newCard.style.display = 'none';
      container.querySelector('#newProfForm').reset();
      load();

      // Modal destacado con la contraseña temporal generada
      ui.showModal({
        title: '¡Profesional dado de alta!',
        message: `
          <p class="mb-3 text-sm">
            Se ha creado la cuenta asistencial para <strong>${esc(res.nombres)} ${esc(res.apellidos)}</strong> (${esc(res.especialidadNombre)}).
          </p>
          <div class="card p-3 mb-3" style="background: var(--surface-2); border-left: 4px solid var(--warning);">
            <span class="text-xs text-muted block mb-1 font-bold">CONTRASEÑA TEMPORAL GENERADA:</span>
            <div class="flex items-center justify-between gap-2">
              <span id="tempPassCode" class="font-mono text-lg font-bold" style="letter-spacing: 1px;">${esc(res.passwordTemporal)}</span>
              <button type="button" id="btnCopyPass" class="btn btn-secondary btn--sm">Copiar</button>
            </div>
          </div>
          <p class="text-xs text-muted m-0">
            ⚠️ <em>Entrega esta contraseña al profesional. Por motivos de seguridad, no volverá a mostrarse. En su primer inicio de sesión se le exigirá cambiarla de inmediato.</em>
          </p>
        `,
        confirmText: 'Entendido y guardada',
        onConfirm: () => {}
      });

      document.querySelector('#btnCopyPass')?.addEventListener('click', () => {
        navigator.clipboard?.writeText(res.passwordTemporal);
        ui.showToast('Contraseña copiada al portapapeles.', 'info');
      });

    } catch (err) {
      errEl.textContent = err.message || 'No fue posible dar de alta al profesional.';
    } finally {
      ui.setButtonLoading(saveBtn, false);
    }
  });

  container.querySelector('#profSpecFilter').addEventListener('change', (e) => {
    especialidadFiltro = e.target.value;
    page = 0;
    load();
  });

  container.querySelector('#profEstadoFilter').addEventListener('change', (e) => {
    estadoFiltro = e.target.value;
    page = 0;
    load();
  });

  async function load() {
    const tableBox = container.querySelector('#profTableContainer');
    ui.renderLoading(tableBox, 'Cargando profesionales...');
    try {
      const res = await api.get('/admin/professionals', {
        page,
        size,
        especialidadPublicId: especialidadFiltro || undefined,
        estado: estadoFiltro || undefined
      });
      renderTable(res);
    } catch (err) {
      ui.renderError(tableBox, {
        title: 'Error al consultar profesionales',
        message: err.message,
        onRetry: load
      });
    }
  }

  function renderTable(data) {
    const list = data?.content || [];
    const tableBox = container.querySelector('#profTableContainer');
    container.querySelector('#profPaginationInfo').textContent =
      `Mostrando ${list.length} de ${data?.totalElements || 0} registros`;

    if (list.length === 0) {
      ui.renderEmpty(tableBox, {
        icon: 'user',
        title: 'Sin profesionales',
        description: 'No se encontraron profesionales asistenciales registrados con los filtros actuales.'
      });
      return;
    }

    tableBox.innerHTML = `
      <div class="table-container mb-4">
        <table class="table table--stacked">
          <thead>
            <tr>
              <th>Profesional</th>
              <th>Registro</th>
              <th>Contacto</th>
              <th>Especialidad</th>
              <th>Estado</th>
              <th style="text-align: right;">Acciones</th>
            </tr>
          </thead>
          <tbody>
            ${list.map(it => `
              <tr>
                <td data-label="Profesional"><strong>${esc(it.nombres)} ${esc(it.apellidos)}</strong></td>
                <td data-label="Registro"><span class="font-mono text-xs">${esc(it.registroMedico)}</span></td>
                <td data-label="Contacto"><span class="text-sm">${esc(it.email)}</span></td>
                <td data-label="Especialidad"><span class="badge badge--scheduled">${esc(it.especialidadNombre)}</span></td>
                <td data-label="Estado">${statusBadge(it.estado)}</td>
                <td data-label="Acciones" style="text-align: right;">
                  <button type="button" class="btn btn-secondary btn--sm btn-edit"
                    data-id="${esc(it.publicId)}"
                    data-nombres="${esc(it.nombres)}"
                    data-apellidos="${esc(it.apellidos)}"
                    data-spec="${esc(it.especialidadPublicId)}">
                    Editar
                  </button>
                  ${it.estado === 'ACTIVO' ? `
                    <button type="button" class="btn btn-ghost btn--sm text-danger btn-deactivate" data-id="${esc(it.publicId)}" data-nombre="${esc(it.nombres)}">
                      Desactivar
                    </button>` : `
                    <button type="button" class="btn btn-ghost btn--sm text-success btn-activate" data-id="${esc(it.publicId)}" data-nombre="${esc(it.nombres)}">
                      Activar
                    </button>`}
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>

      <!-- Paginación -->
      <div class="flex items-center justify-between gap-3">
        <button type="button" id="profPrev" class="btn btn-secondary btn--sm" ${!data?.hasPrevious ? 'disabled' : ''}>Anterior</button>
        <span class="text-xs text-muted">Página ${(data?.page || 0) + 1} de ${Math.max(1, data?.totalPages || 1)}</span>
        <button type="button" id="profNext" class="btn btn-secondary btn--sm" ${!data?.hasNext ? 'disabled' : ''}>Siguiente</button>
      </div>
    `;

    tableBox.querySelector('#profPrev')?.addEventListener('click', () => { if (page > 0) { page--; load(); } });
    tableBox.querySelector('#profNext')?.addEventListener('click', () => { page++; load(); });

    // Editar profesional
    tableBox.querySelectorAll('.btn-edit').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const curNombres = btn.dataset.nombres;
        const curApellidos = btn.dataset.apellidos;
        const curSpec = btn.dataset.spec;

        const optionsHtml = activeSpecialties.map(sp => `
          <option value="${esc(sp.publicId)}" ${sp.publicId === curSpec ? 'selected' : ''}>${esc(sp.nombre)}</option>
        `).join('');

        ui.showModal({
          title: 'Editar Profesional',
          message: `
            <div class="form-group mb-2">
              <label for="editProfNombres" class="form-label text-xs">Nombres</label>
              <input type="text" id="editProfNombres" class="form-input" value="${esc(curNombres)}" maxlength="60">
            </div>
            <div class="form-group mb-2">
              <label for="editProfApellidos" class="form-label text-xs">Apellidos</label>
              <input type="text" id="editProfApellidos" class="form-input" value="${esc(curApellidos)}" maxlength="60">
            </div>
            <div class="form-group mb-2">
              <label for="editProfSpec" class="form-label text-xs">Especialidad</label>
              <select id="editProfSpec" class="form-select">${optionsHtml}</select>
            </div>
          `,
          confirmText: 'Guardar cambios',
          onConfirm: async () => {
            const nombres = document.querySelector('#editProfNombres')?.value.trim();
            const apellidos = document.querySelector('#editProfApellidos')?.value.trim();
            const especialidadPublicId = document.querySelector('#editProfSpec')?.value;
            if (!nombres || !apellidos || !especialidadPublicId) {
              ui.showToast('Todos los campos son obligatorios.', 'warning');
              return;
            }
            try {
              await api.put(`/admin/professionals/${id}`, { nombres, apellidos, especialidadPublicId });
              ui.showToast('Profesional actualizado.', 'success');
              load();
            } catch (err) {
              ui.showToast(err.message || 'Error al actualizar profesional.', 'danger');
            }
          }
        });
      });
    });

    // Desactivar
    tableBox.querySelectorAll('.btn-deactivate').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const nombre = btn.dataset.nombre;
        ui.showModal({
          title: '¿Desactivar profesional?',
          message: `El usuario asistencial <strong>${esc(nombre)}</strong> no podrá iniciar sesión ni atender citas.`,
          confirmText: 'Sí, desactivar',
          isDanger: true,
          onConfirm: async () => {
            try {
              await api.patch(`/admin/professionals/${id}/deactivate`);
              ui.showToast('Profesional desactivado.', 'info');
              load();
            } catch (err) {
              ui.showToast(err.message || 'Error al desactivar.', 'danger');
            }
          }
        });
      });
    });

    // Activar
    tableBox.querySelectorAll('.btn-activate').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        const nombre = btn.dataset.nombre;
        ui.showModal({
          title: '¿Activar profesional?',
          message: `El profesional <strong>${esc(nombre)}</strong> pasará a estar activo.`,
          confirmText: 'Sí, activar',
          onConfirm: async () => {
            try {
              await api.patch(`/admin/professionals/${id}/activate`);
              ui.showToast('Profesional activado.', 'success');
              load();
            } catch (err) {
              ui.showToast(err.message || 'Error al activar.', 'danger');
            }
          }
        });
      });
    });
  }

  await load();
}

/* ==========================================================================
   5. PESTAÑA: SLOTS DE DISPONIBILIDAD (GENERADOR Y GESTIÓN)
   ========================================================================== */
async function renderSlots(container) {
  let page = 0;
  const size = 15;
  let activePros = [];
  let activeSites = [];

  const today = todayBogota();
  const nextWeek = addDays(today, 7);

  container.innerHTML = `
    <div>
      <div class="mb-6">
        <h2 class="text-xl font-bold mb-1">Slots de Disponibilidad Asistencial</h2>
        <p class="text-sm text-muted m-0">Generación masiva y gestión de turnos según ADR-005 (zona America/Bogota) y ADR-006</p>
      </div>

      <!-- SECCIÓN 1: GENERADOR DE TURNOS -->
      <div class="card p-6 mb-8" style="border-top: 4px solid var(--primary);">
        <h3 class="text-lg font-bold mb-2">Generador masivo de slots</h3>
        <p class="text-xs text-muted mb-4">
          Crea turnos continuos para un profesional en una sede sin colisiones ni solapes horarios.
        </p>

        <form id="slotGenForm" novalidate>
          <div class="grid grid-cols-1 grid-cols-3-md gap-4 mb-4">
            <div class="form-group m-0">
              <label for="genProf" class="form-label text-xs">Profesional <span class="required">*</span></label>
              <select id="genProf" class="form-select" required>
                <option value="">Selecciona profesional...</option>
              </select>
            </div>
            <div class="form-group m-0">
              <label for="genSite" class="form-label text-xs">Sede <span class="required">*</span></label>
              <select id="genSite" class="form-select" required>
                <option value="">Selecciona sede...</option>
              </select>
            </div>
            <div class="form-group m-0">
              <label for="genModalidad" class="form-label text-xs">Modalidad <span class="required">*</span></label>
              <select id="genModalidad" class="form-select" required>
                <option value="PRESENCIAL">Presencial en sede</option>
                <option value="TELEMEDICINA">Telemedicina</option>
              </select>
            </div>
          </div>

          <div class="grid grid-cols-1 grid-cols-4-md gap-4 mb-4">
            <div class="form-group m-0">
              <label for="genFechaInicio" class="form-label text-xs">Fecha desde <span class="required">*</span></label>
              <input type="date" id="genFechaInicio" class="form-input" value="${today}" min="${today}" required>
            </div>
            <div class="form-group m-0">
              <label for="genFechaFin" class="form-label text-xs">Fecha hasta <span class="required">*</span></label>
              <input type="date" id="genFechaFin" class="form-input" value="${nextWeek}" min="${today}" required>
            </div>
            <div class="form-group m-0">
              <label for="genHoraInicio" class="form-label text-xs">Hora inicio <span class="required">*</span></label>
              <input type="time" id="genHoraInicio" class="form-input" value="08:00" required>
            </div>
            <div class="form-group m-0">
              <label for="genHoraFin" class="form-label text-xs">Hora fin <span class="required">*</span></label>
              <input type="time" id="genHoraFin" class="form-input" value="12:00" required>
            </div>
          </div>

          <div class="mb-4">
            <label class="form-label text-xs">Días aplicables</label>
            <div class="flex flex-wrap gap-3">
              <label class="flex items-center gap-1 text-xs cursor-pointer"><input type="checkbox" name="genDay" value="MONDAY" checked> Lun</label>
              <label class="flex items-center gap-1 text-xs cursor-pointer"><input type="checkbox" name="genDay" value="TUESDAY" checked> Mar</label>
              <label class="flex items-center gap-1 text-xs cursor-pointer"><input type="checkbox" name="genDay" value="WEDNESDAY" checked> Mié</label>
              <label class="flex items-center gap-1 text-xs cursor-pointer"><input type="checkbox" name="genDay" value="THURSDAY" checked> Jue</label>
              <label class="flex items-center gap-1 text-xs cursor-pointer"><input type="checkbox" name="genDay" value="FRIDAY" checked> Vie</label>
              <label class="flex items-center gap-1 text-xs cursor-pointer"><input type="checkbox" name="genDay" value="SATURDAY"> Sáb</label>
            </div>
          </div>

          <p id="genError" class="text-xs text-danger mb-3" role="alert"></p>
          <div class="flex justify-end">
            <button type="submit" id="btnGenSubmit" class="btn btn-primary btn--lg">
              ${ui.icon('calendar', 'icon icon--sm')}<span>Generar slots</span>
            </button>
          </div>
        </form>
      </div>

      <!-- SECCIÓN 2: CONSULTA Y GESTIÓN DE TURNOS -->
      <div>
        <h3 class="text-lg font-bold mb-3">Listado y gestión de turnos</h3>
        <div class="card p-4 mb-4">
          <form id="slotSearchForm" class="grid grid-cols-1 grid-cols-4-md gap-3 items-end">
            <div class="form-group m-0">
              <label for="filterProf" class="form-label text-xs">Profesional</label>
              <select id="filterProf" class="form-select">
                <option value="">Todos los profesionales</option>
              </select>
            </div>
            <div class="form-group m-0">
              <label for="filterEstado" class="form-label text-xs">Estado</label>
              <select id="filterEstado" class="form-select">
                <option value="">Todos los estados</option>
                <option value="LIBRE">Libre</option>
                <option value="BLOQUEADO">Bloqueado</option>
                <option value="OCUPADO">Ocupado</option>
              </select>
            </div>
            <div class="form-group m-0">
              <label for="filterDesde" class="form-label text-xs">Desde</label>
              <input type="date" id="filterDesde" class="form-input" value="${today}">
            </div>
            <div>
              <button type="submit" class="btn btn-primary w-full">
                ${ui.icon('search')}<span>Buscar slots</span>
              </button>
            </div>
          </form>
        </div>

        <div id="slotTableContainer"></div>
      </div>
    </div>
  `;

  // Cargar profesionales y sedes para los selects
  try {
    const [resP, resS] = await Promise.all([
      api.get('/admin/professionals', { page: 0, size: 100, estado: 'ACTIVO' }),
      api.get('/admin/sites', { page: 0, size: 100, estado: 'ACTIVO' })
    ]);
    activePros = resP?.content || [];
    activeSites = resS?.content || [];

    const genP = container.querySelector('#genProf');
    const filP = container.querySelector('#filterProf');
    activePros.forEach(p => {
      const opt = `<option value="${esc(p.publicId)}">${esc(p.nombres)} ${esc(p.apellidos)} (${esc(p.especialidadNombre)})</option>`;
      genP.insertAdjacentHTML('beforeend', opt);
      filP.insertAdjacentHTML('beforeend', opt);
    });

    const genS = container.querySelector('#genSite');
    activeSites.forEach(s => {
      genS.insertAdjacentHTML('beforeend', `<option value="${esc(s.publicId)}">${esc(s.nombre)}</option>`);
    });
  } catch {
    // Manejo silencioso de carga de catálogos
  }

  // Generación de slots
  container.querySelector('#slotGenForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const profesionalPublicId = container.querySelector('#genProf').value;
    const sedePublicId = container.querySelector('#genSite').value;
    const modalidad = container.querySelector('#genModalidad').value;
    const fechaInicio = container.querySelector('#genFechaInicio').value;
    const fechaFin = container.querySelector('#genFechaFin').value;
    const horaInicio = container.querySelector('#genHoraInicio').value;
    const horaFin = container.querySelector('#genHoraFin').value;

    const checkedDays = Array.from(container.querySelectorAll('input[name="genDay"]:checked')).map(cb => cb.value);

    const errEl = container.querySelector('#genError');
    errEl.textContent = '';

    if (!profesionalPublicId || !sedePublicId || !fechaInicio || !fechaFin || !horaInicio || !horaFin) {
      errEl.textContent = 'Completa todos los campos obligatorios del generador.';
      return;
    }
    if (fechaFin < fechaInicio) {
      errEl.textContent = 'La fecha final no puede ser anterior a la inicial.';
      return;
    }
    if (horaFin <= horaInicio) {
      errEl.textContent = 'La hora final debe ser posterior a la hora inicial.';
      return;
    }

    const btn = container.querySelector('#btnGenSubmit');
    ui.setButtonLoading(btn, true);
    try {
      const res = await api.post('/admin/slots/generate', {
        profesionalPublicId,
        sedePublicId,
        fechaInicio,
        fechaFin,
        horaInicio,
        horaFin,
        modalidad,
        diasSemana: checkedDays.length > 0 ? checkedDays : null
      });

      ui.showToast(res.mensaje || `¡Se generaron ${res.slotsGenerados} slots exitosamente!`, 'success');
      loadSlots();
    } catch (err) {
      errEl.textContent = err.message || 'No fue posible generar los slots.';
    } finally {
      ui.setButtonLoading(btn, false);
    }
  });

  // Búsqueda de slots
  container.querySelector('#slotSearchForm').addEventListener('submit', (e) => {
    e.preventDefault();
    page = 0;
    loadSlots();
  });

  async function loadSlots() {
    const tableBox = container.querySelector('#slotTableContainer');
    ui.renderLoading(tableBox, 'Buscando slots...');

    const prof = container.querySelector('#filterProf').value;
    const estado = container.querySelector('#filterEstado').value;
    const desde = container.querySelector('#filterDesde').value;

    let fechaDesdeIso = undefined;
    if (desde) {
      // Medianoche en America/Bogota (UTC-5)
      fechaDesdeIso = `${desde}T00:00:00-05:00`;
    }

    try {
      const res = await api.get('/admin/slots', {
        page,
        size,
        profesionalPublicId: prof || undefined,
        estado: estado || undefined,
        fechaDesde: fechaDesdeIso
      });
      renderSlotsTable(res);
    } catch (err) {
      ui.renderError(tableBox, {
        title: 'Error al consultar turnos',
        message: err.message,
        onRetry: loadSlots
      });
    }
  }

  function renderSlotsTable(data) {
    const list = data?.content || [];
    const tableBox = container.querySelector('#slotTableContainer');

    if (list.length === 0) {
      ui.renderEmpty(tableBox, {
        icon: 'calendar',
        title: 'Sin turnos encontrados',
        description: 'No hay slots de disponibilidad que coincidan con la búsqueda.'
      });
      return;
    }

    tableBox.innerHTML = `
      <div class="table-container mb-4">
        <table class="table table--stacked">
          <thead>
            <tr>
              <th>Fecha y Horario</th>
              <th>Profesional</th>
              <th>Sede / Modalidad</th>
              <th>Estado</th>
              <th style="text-align: right;">Acciones</th>
            </tr>
          </thead>
          <tbody>
            ${list.map(s => `
              <tr>
                <td data-label="Horario">
                  <strong>${formatDateTime(s.fechaHoraInicio)}</strong>
                  <span class="text-xs text-muted block">Hasta ${formatDateTime(s.fechaHoraFin)}</span>
                </td>
                <td data-label="Profesional">
                  <span>${esc(s.profesionalNombre)}</span>
                  <span class="badge badge--neutral text-xs block" style="width: fit-content; margin-top: 2px;">${esc(s.especialidadNombre)}</span>
                </td>
                <td data-label="Sede">
                  <span>${esc(s.sedeNombre)}</span>
                  <span class="text-xs text-muted block">${s.modalidad === 'TELEMEDICINA' ? 'Telemedicina' : 'Presencial'}</span>
                </td>
                <td data-label="Estado">${statusBadge(s.estado)}</td>
                <td data-label="Acciones" style="text-align: right;">
                  ${s.estado === 'LIBRE' ? `
                    <button type="button" class="btn btn-secondary btn--sm btn-block-slot" data-id="${esc(s.publicId)}">Bloquear</button>
                    <button type="button" class="btn btn-ghost btn--sm text-danger btn-del-slot" data-id="${esc(s.publicId)}">Eliminar</button>
                  ` : s.estado === 'BLOQUEADO' ? `
                    <button type="button" class="btn btn-secondary btn--sm btn-unblock-slot" data-id="${esc(s.publicId)}">Desbloquear</button>
                  ` : `
                    <span class="text-xs text-muted">Cita asignada</span>
                  `}
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>

      <!-- Paginación -->
      <div class="flex items-center justify-between gap-3">
        <button type="button" id="slotsPrev" class="btn btn-secondary btn--sm" ${!data?.hasPrevious ? 'disabled' : ''}>Anterior</button>
        <span class="text-xs text-muted">Página ${(data?.page || 0) + 1} de ${Math.max(1, data?.totalPages || 1)}</span>
        <button type="button" id="slotsNext" class="btn btn-secondary btn--sm" ${!data?.hasNext ? 'disabled' : ''}>Siguiente</button>
      </div>
    `;

    tableBox.querySelector('#slotsPrev')?.addEventListener('click', () => { if (page > 0) { page--; loadSlots(); } });
    tableBox.querySelector('#slotsNext')?.addEventListener('click', () => { page++; loadSlots(); });

    // Bloquear slot
    tableBox.querySelectorAll('.btn-block-slot').forEach(b => {
      b.addEventListener('click', async () => {
        try {
          await api.patch(`/admin/slots/${b.dataset.id}/block`);
          ui.showToast('Slot bloqueado.', 'info');
          loadSlots();
        } catch (err) {
          ui.showToast(err.message || 'Error al bloquear.', 'danger');
        }
      });
    });

    // Desbloquear slot
    tableBox.querySelectorAll('.btn-unblock-slot').forEach(b => {
      b.addEventListener('click', async () => {
        try {
          await api.patch(`/admin/slots/${b.dataset.id}/unblock`);
          ui.showToast('Slot desbloqueado a libre.', 'success');
          loadSlots();
        } catch (err) {
          ui.showToast(err.message || 'Error al desbloquear.', 'danger');
        }
      });
    });

    // Eliminar slot
    tableBox.querySelectorAll('.btn-del-slot').forEach(b => {
      b.addEventListener('click', () => {
        ui.showModal({
          title: '¿Eliminar este slot?',
          message: 'Se eliminará el turno libre. Los pacientes ya no podrán agendar en este horario.',
          confirmText: 'Sí, eliminar',
          isDanger: true,
          onConfirm: async () => {
            try {
              await api.delete(`/admin/slots/${b.dataset.id}`);
              ui.showToast('Slot eliminado.', 'info');
              loadSlots();
            } catch (err) {
              ui.showToast(err.message || 'Error al eliminar slot.', 'danger');
            }
          }
        });
      });
    });
  }

  await loadSlots();
}
