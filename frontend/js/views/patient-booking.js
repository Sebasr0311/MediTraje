/**
 * MediTraje 2.0 — Vista de Disponibilidad y Reserva de Citas (patient-booking.js)
 * Búsqueda de slots en tiempo real, filtros accesibles por especialidad, sede, modalidad y fecha,
 * confirmación resumida y agendamiento transaccional con manejo de colisiones 409 (M8.2b, HU-03, HU-04).
 */

import { api } from '../api.js';
import { auth } from '../auth.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

// Estado local de la vista de agendamiento
let bookingState = {
  slots: [],
  selectedSlot: null,
  triageId: null,
  triageData: null,
  consultationReason: 'Consulta médica general o chequeo preventivo',
  filters: {
    especialidad: '',
    sede: '',
    modalidad: '',
    fecha: ''
  },
  selectedDoctor: '',
  availableSpecialties: new Map(), // publicId -> nombre
  availableSites: new Map()        // publicId -> nombre
};

/**
 * Formatea una fecha ISO en hora de Colombia legible (ej. "Jueves 8 de octubre, 2026")
 */
function formatDayHeader(isoString) {
  try {
    const d = new Date(isoString);
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    }).format(d);
  } catch {
    return isoString;
  }
}

/**
 * Formatea la hora en Colombia (ej. "08:30 a. m.")
 */
function formatTime(isoString) {
  try {
    const d = new Date(isoString);
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    }).format(d);
  } catch {
    return isoString;
  }
}

/**
 * Agrupa los slots por fecha local (YYYY-MM-DD)
 */
function groupSlotsByDay(slots) {
  const groups = new Map();
  slots.forEach(slot => {
    try {
      const d = new Date(slot.fechaHoraInicio);
      const dayKey = d.toISOString().split('T')[0];
      if (!groups.has(dayKey)) {
        groups.set(dayKey, []);
      }
      groups.get(dayKey).push(slot);
    } catch {
      // Ignorar si fecha es inválida
    }
  });
  return groups;
}

/**
 * Vista principal de consulta de disponibilidad y agendamiento (#/patient/book o #/availability)
 * @param {HTMLElement} container Contenedor principal
 * @param {Object} context Contexto de navegación (query params, router)
 */
export async function patientBookingView(container, context = {}) {
  // Extraer triageId opcional vinculado
  bookingState.triageId = context.queryParams?.get('triageId') || null;
  bookingState.selectedSlot = null;
  bookingState.selectedDoctor = '';
  bookingState.triageData = null;
  bookingState.consultationReason = 'Consulta médica general o chequeo preventivo';
  bookingState.filters = {
    especialidad: '',
    sede: '',
    modalidad: '',
    fecha: ''
  };

  // Si hay triaje vinculado, cargar sus detalles clínicos para la razón de la consulta
  if (bookingState.triageId) {
    try {
      bookingState.triageData = await api.get(`/triage/${bookingState.triageId}`);
      if (bookingState.triageData) {
        const sNames = (bookingState.triageData.sintomas || []).map(s => s.nombre).join(', ');
        bookingState.consultationReason = bookingState.triageData.observaciones || (sNames ? `Síntomas reportados: ${sNames}` : 'Orientación médica por triaje clínico');
      }
    } catch {
      bookingState.triageData = null;
    }
  }

  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Encabezado -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-4">
        <div>
          <div class="flex items-center gap-2 mb-1">
            <a href="#/patient/dashboard" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver al panel" title="Volver al panel">
              ${ui.icon('arrow-left')}
            </a>
            <h1 class="text-2xl font-bold m-0">Disponibilidad de Citas Médicas</h1>
          </div>
          <p class="text-sm text-muted m-0">Consulta los turnos libres en tiempo real y agenda tu cita con el especialista indicado</p>
        </div>

        ${bookingState.triageId ? `
          <div class="badge badge--scheduled flex items-center gap-2 p-2">
            ${ui.icon('activity', 'icon icon--sm text-primary')}
            <span>Triaje clínico vinculado</span>
          </div>
        ` : ''}
      </div>

      <!-- Tarjeta informativa de Triaje Clínico Vinculado si aplica -->
      ${bookingState.triageData ? `
        <div class="card p-4 mb-6" style="border-left: 4px solid var(--primary); background: var(--surface);">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div class="flex items-center gap-2 mb-1">
                <span class="badge ${bookingState.triageData.nivelPrioridad === 'II' ? 'badge--triage-2' : (bookingState.triageData.nivelPrioridad === 'IV' ? 'badge--triage-4' : 'badge--triage-3')} text-xs font-bold">
                  Triaje Nivel ${bookingState.triageData.nivelPrioridad}
                </span>
                <span class="text-xs text-muted font-mono">ID: ${bookingState.triageId.slice(0, 8)}</span>
              </div>
              <strong class="text-sm block text-text">Razón de la consulta vinculada:</strong>
              <p class="text-xs text-muted m-0 font-medium">${bookingState.consultationReason}</p>
            </div>
            <div>
              <span class="badge badge--neutral text-xs">Ruta sugerida: ${bookingState.triageData.rutaSugerida || 'Cita Presencial'}</span>
            </div>
          </div>
        </div>
      ` : ''}

      <!-- Barra de Filtros de Búsqueda -->
      <div class="card mb-6" id="bookingFiltersCard">
        <div class="card-body">
          <form id="filtersForm" class="grid grid-cols-1 grid-cols-4-md gap-4 items-end">
            
            <!-- Filtro Especialidad -->
            <div class="form-group m-0">
              <label for="filterSpecialty" class="form-label text-xs">Especialidad médica</label>
              <select id="filterSpecialty" class="form-select text-sm">
                <option value="">Todas las especialidades</option>
              </select>
            </div>

            <!-- Filtro Razón de la Consulta -->
            <div class="form-group m-0">
              <label for="filterReason" class="form-label text-xs">Razón de la consulta</label>
              <select id="filterReason" class="form-select text-sm" ${bookingState.triageData ? 'disabled' : ''}>
                <option value="Consulta médica general o chequeo preventivo">Consulta general / chequeo preventivo</option>
                <option value="Valoración médica por síntomas recientes">Valoración médica por síntomas</option>
                <option value="Control y seguimiento de tratamiento">Control y seguimiento de tratamiento</option>
                <option value="Lectura y revisión de exámenes de laboratorio">Lectura de exámenes de laboratorio</option>
                <option value="Consulta pediátrica / control infantil">Consulta pediátrica / control infantil</option>
                <option value="Renovación de fórmula médica">Renovación de fórmula médica</option>
              </select>
            </div>

            <!-- Filtro Sede -->
            <div class="form-group m-0">
              <label for="filterSite" class="form-label text-xs">Sede de atención</label>
              <select id="filterSite" class="form-select text-sm">
                <option value="">Todas las sedes</option>
              </select>
            </div>

            <!-- Filtro Modalidad -->
            <div class="form-group m-0">
              <label for="filterModality" class="form-label text-xs">Modalidad</label>
              <select id="filterModality" class="form-select text-sm">
                <option value="">Todas las modalidades</option>
                <option value="PRESENCIAL">Presencial en sede</option>
                <option value="TELEMEDICINA">Telemedicina virtual</option>
              </select>
            </div>

            <!-- Botones de Acción -->
            <div class="flex items-center gap-2 col-span-full">
              <button type="submit" class="btn btn-primary text-sm" id="btnApplyFilters">
                ${ui.icon('search')}
                <span>Buscar turnos y especialistas</span>
              </button>
              <button type="button" class="btn btn-secondary text-sm" id="btnResetFilters" title="Limpiar filtros" aria-label="Limpiar filtros">
                ${ui.icon('x')}
                <span>Restablecer</span>
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- Resumen y Confirmación de Slot Seleccionado (Aparece al elegir turno) -->
      <div id="slotConfirmationCard" style="display: none;" class="card card--highlight mb-8 animate-fade-in"></div>

      <!-- Contenedor de Resultados de Horarios -->
      <div id="availabilityResultsContainer">
        <div class="skeleton skeleton-card" style="height: 160px; margin-bottom: var(--space-4);"></div>
      </div>
    </div>
  `;

  // Carga inicial de disponibilidad
  await loadAvailability(container);
  setupFilterEvents(container);
}

/**
 * Consulta la disponibilidad de slots desde la API
 */
async function loadAvailability(container) {
  const resultsContainer = container.querySelector('#availabilityResultsContainer');
  ui.renderLoading(resultsContainer, 'Consultando horarios médicos disponibles...');

  const params = {
    page: 0,
    size: 100
  };

  if (bookingState.filters.especialidad) {
    params.especialidad = bookingState.filters.especialidad;
  }
  if (bookingState.filters.sede) {
    params.sede = bookingState.filters.sede;
  }
  if (bookingState.filters.modalidad) {
    params.modalidad = bookingState.filters.modalidad;
  }
  if (bookingState.filters.fecha) {
    params.fecha = bookingState.filters.fecha;
  }

  try {
    const res = await api.get('/availability', params);
    bookingState.slots = res?.content || [];

    // Poblar dropdowns de filtros con datos únicos encontrados
    populateFilterOptions(container);

    renderSlotsGrouped(container);
  } catch (err) {
    ui.renderError(resultsContainer, {
      title: 'No fue posible cargar la disponibilidad',
      message: err.message || 'Ocurrió un problema al obtener los horarios disponibles.',
      onRetry: () => loadAvailability(container)
    });
  }
}

/**
 * Llena dinámicamente los selectores de especialidades y sedes
 */
function populateFilterOptions(container) {
  const specialtySelect = container.querySelector('#filterSpecialty');
  const siteSelect = container.querySelector('#filterSite');

  // Solo rellenar si aún no están poblados o si tienen 1 opción
  if (specialtySelect && specialtySelect.options.length <= 1) {
    bookingState.slots.forEach(s => {
      if (s.especialidadPublicId && s.especialidadNombre) {
        bookingState.availableSpecialties.set(s.especialidadPublicId, s.especialidadNombre);
      }
      if (s.sedePublicId && s.sedeNombre) {
        bookingState.availableSites.set(s.sedePublicId, s.sedeNombre);
      }
    });

    bookingState.availableSpecialties.forEach((nombre, id) => {
      const opt = document.createElement('option');
      opt.value = id;
      opt.textContent = nombre;
      if (bookingState.filters.especialidad === id) opt.selected = true;
      specialtySelect.appendChild(opt);
    });

    bookingState.availableSites.forEach((nombre, id) => {
      const opt = document.createElement('option');
      opt.value = id;
      opt.textContent = nombre;
      if (bookingState.filters.sede === id) opt.selected = true;
      siteSelect.appendChild(opt);
    });
  }
}

/**
 * Configura los eventos del formulario de filtros
 */
function setupFilterEvents(container) {
  const form = container.querySelector('#filtersForm');
  const btnReset = container.querySelector('#btnResetFilters');

  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    bookingState.filters.especialidad = container.querySelector('#filterSpecialty')?.value || '';
    if (!bookingState.triageData) {
      const reasonVal = container.querySelector('#filterReason')?.value;
      if (reasonVal) bookingState.consultationReason = reasonVal;
    }
    bookingState.filters.sede = container.querySelector('#filterSite')?.value || '';
    bookingState.filters.modalidad = container.querySelector('#filterModality')?.value || '';
    
    // Ocultar confirmación previa si cambia el filtro
    hideConfirmationCard(container);
    await loadAvailability(container);
  });

  const filterReasonEl = container.querySelector('#filterReason');
  filterReasonEl?.addEventListener('change', () => {
    if (!bookingState.triageData) {
      bookingState.consultationReason = filterReasonEl.value;
      hideConfirmationCard(container);
      renderSlotsGrouped(container);
    }
  });

  const filterSpecialtyEl = container.querySelector('#filterSpecialty');
  filterSpecialtyEl?.addEventListener('change', async () => {
    bookingState.filters.especialidad = filterSpecialtyEl.value;
    bookingState.selectedDoctor = '';
    hideConfirmationCard(container);
    await loadAvailability(container);
  });

  btnReset?.addEventListener('click', async () => {
    if (form) form.reset();
    bookingState.filters = { especialidad: '', sede: '', modalidad: '', fecha: '' };
    bookingState.selectedDoctor = '';
    if (!bookingState.triageData) {
      bookingState.consultationReason = 'Consulta médica general o chequeo preventivo';
    }
    hideConfirmationCard(container);
    await loadAvailability(container);
  });
}

/**
 * Renderiza los turnos agrupados por día con chips de hora táctiles
 */
function renderSlotsGrouped(container) {
  const resultsContainer = container.querySelector('#availabilityResultsContainer');
  if (!resultsContainer) return;

  if (bookingState.slots.length === 0) {
    ui.renderEmpty(resultsContainer, {
      icon: 'calendar',
      title: 'No hay horarios disponibles',
      description: 'No se encontraron turnos de atención libres para los filtros seleccionados.',
      actionText: 'Restablecer filtros',
      onAction: () => {
        const form = container.querySelector('#filtersForm');
        if (form) form.reset();
        bookingState.filters = { especialidad: '', sede: '', modalidad: '', fecha: '' };
        bookingState.selectedDoctor = '';
        hideConfirmationCard(container);
        loadAvailability(container);
      }
    });
    return;
  }

  const selectedSpecialtyName = bookingState.availableSpecialties.get(bookingState.filters.especialidad) || '';

  const doctorsMap = new Map();
  bookingState.slots.forEach(slot => {
    if (slot.profesionalPublicId && slot.profesionalNombre) {
      // Filtrar estrictamente por la especialidad seleccionada
      if (bookingState.filters.especialidad && slot.especialidadPublicId !== bookingState.filters.especialidad) {
        return;
      }
      if (!doctorsMap.has(slot.profesionalPublicId)) {
        doctorsMap.set(slot.profesionalPublicId, {
          publicId: slot.profesionalPublicId,
          nombre: slot.profesionalNombre,
          especialidad: slot.especialidadNombre || 'Medicina General',
          especialidadPublicId: slot.especialidadPublicId,
          totalSlots: 0,
          modalidades: new Set()
        });
      }
      const doc = doctorsMap.get(slot.profesionalPublicId);
      doc.totalSlots++;
      if (slot.modalidad) doc.modalidades.add(slot.modalidad);
    }
  });

  const displayedSlots = bookingState.selectedDoctor
    ? bookingState.slots.filter(s => s.profesionalPublicId === bookingState.selectedDoctor && (!bookingState.filters.especialidad || s.especialidadPublicId === bookingState.filters.especialidad))
    : (bookingState.filters.especialidad 
        ? bookingState.slots.filter(s => s.especialidadPublicId === bookingState.filters.especialidad) 
        : bookingState.slots);

  const grouped = groupSlotsByDay(displayedSlots);
  const selectedDoctorObj = bookingState.selectedDoctor ? doctorsMap.get(bookingState.selectedDoctor) : null;

  resultsContainer.innerHTML = `
    <!-- Panel Gráfico de Selección de Médico Filtrado por Especialidad y Motivo -->
    ${doctorsMap.size > 0 ? `
      <div class="card p-5 mb-6" style="background: var(--surface); border-top: 3px solid var(--primary);">
        <div class="flex flex-wrap items-center justify-between gap-2 mb-3">
          <div>
            <h2 class="text-base font-bold m-0 flex items-center gap-2">
              ${ui.icon('user', 'icon icon--sm text-primary')}
              <span>Médicos disponibles para tu consulta</span>
            </h2>
            <p class="text-xs text-muted m-0">
              ${selectedSpecialtyName ? `Especialidad: <strong>${selectedSpecialtyName}</strong> · ` : ''}Razón de consulta: <strong>${esc(bookingState.consultationReason)}</strong>
            </p>
          </div>
          <span class="badge badge--scheduled text-xs">${doctorsMap.size} profesional(es) disponible(s)</span>
        </div>

        <div class="doctor-cards-grid">
          <!-- Opción: Todos los médicos -->
          <button 
            type="button" 
            class="doctor-card ${!bookingState.selectedDoctor ? 'is-selected' : ''}" 
            data-doctor-id=""
            aria-pressed="${!bookingState.selectedDoctor}"
          >
            <div class="doctor-avatar doctor-avatar--all">
              ${ui.icon('users', 'icon icon--md')}
            </div>
            <div class="text-left flex-1 min-w-0">
              <strong class="block text-sm">${selectedSpecialtyName ? `Todos en ${selectedSpecialtyName}` : 'Todos los médicos'}</strong>
              <span class="text-xs text-muted block">${displayedSlots.length} turnos en total</span>
              <span class="badge badge--neutral text-xs mt-1" style="font-size: 10px;">Ver toda la oferta</span>
            </div>
          </button>

          <!-- Opciones por cada médico disponible -->
          ${Array.from(doctorsMap.values()).map(doc => {
            const isSel = bookingState.selectedDoctor === doc.publicId;
            const initials = doc.nombre.split(' ').filter(Boolean).slice(0, 2).map(n => n[0]).join('').toUpperCase() || 'DR';
            return `
              <button 
                type="button" 
                class="doctor-card ${isSel ? 'is-selected' : ''}" 
                data-doctor-id="${doc.publicId}"
                aria-pressed="${isSel}"
              >
                <div class="doctor-avatar">
                  <span>${initials}</span>
                </div>
                <div class="text-left flex-1 min-w-0">
                  <strong class="block text-sm truncate" title="Dr(a). ${doc.nombre}">Dr(a). ${doc.nombre}</strong>
                  <span class="text-xs text-muted block truncate font-medium">${doc.especialidad}</span>
                  <div class="flex flex-wrap items-center gap-1.5 mt-1">
                    <span class="badge ${isSel ? 'badge--confirmed' : 'badge--scheduled'} text-xs" style="font-size: 10px;">
                      ${doc.totalSlots} turno(s) libre(s)
                    </span>
                    <span class="badge badge--neutral text-xs" style="font-size: 10px;">
                      Apto para tu motivo
                    </span>
                  </div>
                </div>
              </button>
            `;
          }).join('')}
        </div>
      </div>
    ` : `
      ${bookingState.filters.especialidad ? `
        <div class="card p-6 text-center mb-6">
          <p class="text-muted m-0">No se encontraron profesionales con turnos libres para ${selectedSpecialtyName || 'la especialidad seleccionada'}.</p>
        </div>
      ` : ''}
    `}

    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
      <div>
        <span class="text-xs text-muted font-bold uppercase tracking-wider">
          ${displayedSlots.length} horario(s) disponible(s)
          ${selectedDoctorObj ? `· Dr(a). ${selectedDoctorObj.nombre}` : '· Todos los médicos'}
        </span>
      </div>
      <span class="text-xs text-muted">Selecciona una hora para ver el resumen y confirmar</span>
    </div>

    ${displayedSlots.length === 0 ? `
      <div class="card p-6 text-center">
        <p class="text-muted m-0">No hay turnos disponibles para el médico seleccionado con los filtros actuales.</p>
        <button type="button" class="btn btn-secondary btn--sm mt-3" id="btnShowAllDoctors">
          <span>Ver todos los médicos</span>
        </button>
      </div>
    ` : `
      <div class="flex flex-col gap-6">
        ${Array.from(grouped.entries()).map(([dayKey, daySlots]) => `
          <div class="card p-5" style="border-left: 4px solid var(--primary);">
            <div class="flex items-center gap-2 mb-3 pb-2 border-b">
              ${ui.icon('calendar', 'icon icon--sm text-primary')}
              <h3 class="text-md font-bold m-0 capitalize">${formatDayHeader(daySlots[0].fechaHoraInicio)}</h3>
              <span class="badge badge--scheduled text-xs" style="margin-left: auto;">${daySlots.length} turnos</span>
            </div>

            <!-- Grid de Chips de Hora -->
            <div class="slot-grid">
              ${daySlots.map(slot => {
                const isSelected = bookingState.selectedSlot?.slotPublicId === slot.slotPublicId;
                const isTele = slot.modalidad === 'TELEMEDICINA';

                return `
                  <button 
                    type="button" 
                    class="slot-chip ${isSelected ? 'is-selected' : ''}" 
                    data-slot-id="${slot.slotPublicId}"
                    aria-pressed="${isSelected}"
                    aria-label="Cita a las ${formatTime(slot.fechaHoraInicio)} con ${slot.profesionalNombre}, ${slot.especialidadNombre}"
                  >
                    <span class="text-md font-bold">${formatTime(slot.fechaHoraInicio)}</span>
                    <span class="slot-chip-sub text-xs mt-1">
                      ${isTele ? 'Telemedicina' : (slot.sedeNombre || 'Presencial')}
                    </span>
                    <span class="slot-chip-sub text-xs text-muted" style="font-size: 11px;">
                      ${slot.profesionalNombre ? `Dr(a). ${slot.profesionalNombre.split(' ')[0]}` : ''}
                    </span>
                  </button>
                `;
              }).join('')}
            </div>
          </div>
        `).join('')}
      </div>
    `}
  `;

  // Asignar listeners a las tarjetas de doctores
  resultsContainer.querySelectorAll('.doctor-card').forEach(card => {
    card.addEventListener('click', () => {
      const docId = card.getAttribute('data-doctor-id') || '';
      bookingState.selectedDoctor = docId;
      hideConfirmationCard(container);
      renderSlotsGrouped(container);
    });
  });

  const btnShowAll = resultsContainer.querySelector('#btnShowAllDoctors');
  btnShowAll?.addEventListener('click', () => {
    bookingState.selectedDoctor = '';
    hideConfirmationCard(container);
    renderSlotsGrouped(container);
  });

  // Asignar listeners de selección a cada slot chip
  resultsContainer.querySelectorAll('button[data-slot-id]').forEach(btn => {
    btn.addEventListener('click', () => {
      const slotId = btn.getAttribute('data-slot-id');
      const slot = bookingState.slots.find(s => s.slotPublicId === slotId);
      if (!slot) return;

      bookingState.selectedSlot = slot;

      // Resaltar visualmente el chip activo
      resultsContainer.querySelectorAll('.slot-chip').forEach(b => {
        const isCur = b.getAttribute('data-slot-id') === slotId;
        b.classList.toggle('is-selected', isCur);
        b.setAttribute('aria-pressed', isCur ? 'true' : 'false');
      });

      // Mostrar tarjeta de confirmación
      showConfirmationCard(container, slot);
    });
  });
}

/**
 * Muestra la tarjeta de confirmación del turno seleccionado
 */
function showConfirmationCard(container, slot) {
  const confirmCard = container.querySelector('#slotConfirmationCard');
  if (!confirmCard) return;

  const isTele = slot.modalidad === 'TELEMEDICINA';
  const fechaTexto = formatDayHeader(slot.fechaHoraInicio);
  const horaTexto = formatTime(slot.fechaHoraInicio);

  confirmCard.style.display = 'block';
  confirmCard.innerHTML = `
    <div class="card-header flex items-center justify-between pb-2">
      <div class="flex items-center gap-2">
        ${ui.icon('calendar', 'icon icon--md text-primary')}
        <h2 class="card-title text-xl font-bold">Resumen de la cita médica</h2>
      </div>
      <span class="badge ${isTele ? 'badge--rescheduled' : 'badge--scheduled'} text-xs">
        ${isTele ? 'Telemedicina Virtual' : 'Presencial en Sede'}
      </span>
    </div>

    <div class="card-body">
      <div class="grid grid-cols-1 grid-cols-2-md gap-4 mb-4">
        <div>
          <span class="text-xs text-muted font-bold uppercase tracking-wider block">Fecha y Hora:</span>
          <p class="text-md font-bold m-0 text-primary capitalize">${fechaTexto} a las ${horaTexto}</p>
          <span class="text-xs text-muted">Duración aproximada: ${slot.duracionMinutos || 20} minutos</span>
        </div>

        <div>
          <span class="text-xs text-muted font-bold uppercase tracking-wider block">Profesional Asignado:</span>
          <p class="text-md font-semibold m-0">${slot.profesionalNombre || 'Profesional de Turno'}</p>
          <span class="badge badge--neutral text-xs">${slot.especialidadNombre || 'Medicina General'}</span>
        </div>

        <div>
          <span class="text-xs text-muted font-bold uppercase tracking-wider block">Lugar de Atención:</span>
          <p class="text-sm font-semibold m-0">${slot.sedeNombre || 'Centro Médico MediTriaje'}</p>
          <p class="text-xs text-muted m-0">${slot.sedeDireccion ? `${slot.sedeDireccion}, ${slot.sedeCiudad || 'Bogotá'}` : 'Consultorio asignado'}</p>
        </div>

        <div>
          <span class="text-xs text-muted font-bold uppercase tracking-wider block">Triaje Clínico:</span>
          <p class="text-sm m-0">
            ${bookingState.triageId ? `Vinculado (${bookingState.triageId.slice(0, 8)}...)` : 'Sin triaje previo'}
          </p>
        </div>

        <div class="col-span-full p-2.5" style="background-color: var(--surface-2); border-radius: var(--radius-sm); border-left: 3px solid var(--primary);">
          <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-0.5">Razón de la Consulta / Motivo Asistencial:</span>
          <p class="text-sm font-semibold m-0 text-text">${esc(bookingState.consultationReason)}</p>
        </div>
      </div>

      <div class="alert alert--info" style="margin-bottom: 0;">
        ${ui.icon('info', 'icon alert-icon')}
        <div class="alert-content">
          <p class="text-xs m-0">
            Al confirmar, el horario quedará reservado a tu nombre. Podrás consultar tu cita en cualquier momento desde tu panel.
          </p>
        </div>
      </div>
    </div>

    <div class="card-footer flex flex-wrap items-center justify-between gap-3">
      <button type="button" class="btn btn-secondary" id="btnCancelSelection">
        <span>Elegir otro horario</span>
      </button>

      <button type="button" class="btn btn-primary btn--lg" id="btnConfirmAppointment">
        ${ui.icon('check')}
        <span>Confirmar mi cita</span>
      </button>
    </div>
  `;

  // Foco accesible hacia la confirmación
  confirmCard.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

  confirmCard.querySelector('#btnCancelSelection')?.addEventListener('click', () => {
    hideConfirmationCard(container);
  });

  const btnConfirm = confirmCard.querySelector('#btnConfirmAppointment');
  btnConfirm?.addEventListener('click', async () => {
    await executeBooking(container, btnConfirm, slot);
  });
}

/**
 * Oculta la tarjeta de confirmación y desmarca la selección
 */
function hideConfirmationCard(container) {
  bookingState.selectedSlot = null;
  const confirmCard = container.querySelector('#slotConfirmationCard');
  if (confirmCard) {
    confirmCard.style.display = 'none';
    confirmCard.innerHTML = '';
  }
  container.querySelectorAll('.slot-chip.is-selected').forEach(b => {
    b.classList.remove('is-selected');
    b.setAttribute('aria-pressed', 'false');
  });
}

/**
 * Envía la petición POST /api/v1/appointments al backend y maneja colisiones 409
 */
async function executeBooking(container, submitBtn, slot) {
  ui.setButtonLoading(submitBtn, true);

  const payload = {
    slotPublicId: slot.slotPublicId,
    triajePublicId: bookingState.triageId || null
  };

  try {
    const cita = await api.post('/appointments', payload);
    ui.setButtonLoading(submitBtn, false);

    // Renderizar Pantalla de Éxito
    renderSuccessBookingView(container, cita, slot);
  } catch (err) {
    ui.setButtonLoading(submitBtn, false);

    // Manejo amigable y cálido de conflicto por concurrencia (409)
    if (err.status === 409) {
      ui.showModal({
        title: 'Horario no disponible',
        message: 'Ese horario acaba de ser tomado por otro paciente. Por favor elige otro horario disponible.',
        confirmText: 'Ver otros horarios',
        cancelText: '',
        onConfirm: async () => {
          hideConfirmationCard(container);
          await loadAvailability(container);
        }
      });
    } else {
      ui.showToast(err.message || 'No fue posible reservar la cita. Por favor intenta de nuevo.', 'danger');
    }
  }
}

/**
 * Pantalla de confirmación y éxito tras agendamiento
 */
function renderSuccessBookingView(container, cita, slot) {
  window.scrollTo(0, 0);

  const isTele = cita.modalidad === 'TELEMEDICINA' || slot.modalidad === 'TELEMEDICINA';
  const fechaTexto = formatDayHeader(cita.fechaHoraInicio || slot.fechaHoraInicio);
  const horaTexto = formatTime(cita.fechaHoraInicio || slot.fechaHoraInicio);

  container.innerHTML = `
    <div style="max-width: var(--container-narrow); margin: 0 auto; padding-top: var(--space-8); padding-bottom: var(--space-12); text-align: center;">
      
      <!-- Icono de Éxito Grande -->
      <div style="width: 72px; height: 72px; border-radius: 50%; background-color: var(--success-bg); color: var(--success); display: inline-flex; align-items: center; justify-content: center; margin-bottom: var(--space-4);">
        ${ui.icon('check', 'icon icon--lg')}
      </div>

      <span class="badge badge--confirmed text-sm font-bold block mb-2" style="max-width: fit-content; margin-left: auto; margin-right: auto;">
        ¡Cita Agendada Exitosamente!
      </span>

      <h1 class="text-3xl font-bold mb-3">Tu cita ha sido confirmada</h1>
      <p class="text-muted text-sm mb-6" style="max-width: 50ch; margin-left: auto; margin-right: auto;">
        Tu turno quedó reservado en nuestro sistema. Te esperamos puntualmente para brindarte la mejor atención.
      </p>

      <!-- Tarjeta con Detalles Concretos de la Cita -->
      <div class="card p-6 mb-8 text-left" style="border-top: 4px solid var(--success);">
        <div class="flex items-center justify-between pb-3 border-b mb-4">
          <span class="text-xs text-muted font-bold uppercase tracking-wider">Código de Reserva:</span>
          <span class="font-mono font-bold text-sm text-primary">${cita.publicId ? cita.publicId.slice(0, 8) : 'CONFIRMADO'}</span>
        </div>

        <div class="grid grid-cols-1 grid-cols-2-md gap-4 mb-4">
          <div>
            <span class="text-xs text-muted font-bold uppercase tracking-wider block">Fecha y Hora:</span>
            <p class="text-md font-bold m-0 text-text capitalize">${fechaTexto}</p>
            <p class="text-sm font-semibold text-primary m-0">${horaTexto}</p>
          </div>

          <div>
            <span class="text-xs text-muted font-bold uppercase tracking-wider block">Profesional:</span>
            <p class="text-md font-semibold m-0">${cita.profesionalNombre || slot.profesionalNombre}</p>
            <span class="badge badge--neutral text-xs">${cita.especialidadNombre || slot.especialidadNombre}</span>
          </div>

          <div>
            <span class="text-xs text-muted font-bold uppercase tracking-wider block">Sede o Enlace:</span>
            <p class="text-sm font-semibold m-0">${cita.sedeNombre || slot.sedeNombre}</p>
            <p class="text-xs text-muted m-0">${cita.sedeDireccion || slot.sedeDireccion || 'Consultorio programado'}</p>
          </div>

          <div>
            <span class="text-xs text-muted font-bold uppercase tracking-wider block">Modalidad:</span>
            <span class="badge ${isTele ? 'badge--rescheduled' : 'badge--scheduled'} text-xs">
              ${isTele ? 'Telemedicina' : 'Presencial'}
            </span>
          </div>

          <div class="col-span-full p-2.5" style="background-color: var(--surface-2); border-radius: var(--radius-sm); border-left: 3px solid var(--primary);">
            <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-0.5">Razón de la Consulta / Motivo Asistencial:</span>
            <p class="text-sm font-semibold m-0 text-text">${esc(cita.motivoConsulta || bookingState.consultationReason || 'Consulta médica general')}</p>
          </div>
        </div>

        <!-- Instrucciones para el paciente -->
        <div class="p-3" style="background-color: var(--surface-2); border-radius: var(--radius-md); font-size: var(--text-xs); color: var(--text-muted); line-height: var(--leading-normal);">
          <strong class="text-text block mb-1">Recomendaciones para tu atención:</strong>
          ${isTele 
            ? 'Conéctate 5 minutos antes de la hora programada en un espacio tranquilo y con buena conexión a internet.'
            : 'Por favor preséntate en la sede 15 minutos antes con tu documento de identidad original.'}
        </div>
      </div>

      <!-- Acciones de Navegación -->
      <div class="flex flex-wrap items-center justify-center gap-4">
        <a href="#/patient/dashboard" class="btn btn-primary btn--lg">
          ${ui.icon('hospital')}
          <span>Ir a mi panel principal</span>
        </a>

        <a href="#/patient/appointments" class="btn btn-secondary btn--lg">
          ${ui.icon('calendar')}
          <span>Ver todas mis citas</span>
        </a>
      </div>
    </div>
  `;
}
