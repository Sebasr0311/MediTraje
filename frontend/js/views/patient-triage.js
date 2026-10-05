/**
 * MediTriaje 2.0 — Asistente de Triaje Clínico del Paciente (patient-triage.js)
 * Asistente por pasos para evaluación de síntomas, corte de emergencia infalible
 * y visualización de resultados con ruta de atención sugerida (M8.2b, HU-02, ADR-009).
 */

import { api } from '../api.js';
import { auth } from '../auth.js';
import { router } from '../router.js';
import { ui } from '../ui.js';

// Estado local reactivo de la sesión de triaje
let triageState = {
  step: 1, // 1: Selección de síntomas, 2: Intensidad y duración
  catalog: [],
  selectedSymptoms: new Map(), // codigo -> { codigo, nombre, categoria, esAlarma, intensidad: 5, duracionHoras: 12 }
  observaciones: '',
  searchTerm: '',
  activeCategory: 'TODOS'
};

/**
 * Reinicia el estado del asistente de triaje a sus valores iniciales.
 */
function resetState() {
  triageState = {
    step: 1,
    catalog: [],
    selectedSymptoms: new Map(),
    observaciones: '',
    searchTerm: '',
    activeCategory: 'TODOS'
  };
}

/**
 * Vista principal del Asistente de Triaje (#/patient/triage)
 * @param {HTMLElement} container Contenedor DOM
 * @param {Object} context Parámetros de ruta y query
 */
export async function patientTriageView(container, context = {}) {
  // Si viene con un ID de triaje para solo lectura o consulta
  const triageId = context.params?.id || context.queryParams?.get('id');
  if (triageId) {
    await renderReadOnlyTriage(container, triageId);
    return;
  }

  resetState();
  ui.renderLoading(container, 'Cargando catálogo clínico de síntomas...');

  try {
    const catalogo = await api.get('/triage/symptoms');
    triageState.catalog = Array.isArray(catalogo) ? catalogo : [];
    renderWizard(container);
  } catch (err) {
    ui.renderError(container, {
      title: 'No fue posible cargar el triaje',
      message: err.message || 'Ocurrió un problema al obtener el catálogo de síntomas.',
      onRetry: () => patientTriageView(container, context)
    });
  }
}

/**
 * Renderiza el asistente por pasos (Paso 1 o Paso 2).
 * @param {HTMLElement} container
 */
function renderWizard(container) {
  container.innerHTML = `
    <div style="max-width: var(--container-narrow); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Encabezado con aviso legal sereno -->
      <div class="mb-6">
        <div class="flex items-center gap-2 mb-2">
          <a href="#/patient/dashboard" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver al panel" title="Volver al panel">
            ${ui.icon('arrow-left')}
          </a>
          <h1 class="text-2xl font-bold m-0">Orientación de Triaje Clínico</h1>
        </div>
        
        <div class="alert alert--info" style="margin-top: var(--space-3);" role="note">
          ${ui.icon('shield', 'icon alert-icon')}
          <div class="alert-content">
            <div class="alert-title">Responde con calma</div>
            <p class="m-0 text-sm">
              Esta herramienta orienta tu nivel de prioridad asistencial y te guía hacia la atención oportuna.
              <strong>No constituye un diagnóstico médico</strong> ni reemplaza la valoración directa de un profesional de la salud.
            </p>
          </div>
        </div>
      </div>

      <!-- Barra de Progreso Accesible -->
      <div class="wizard-stepper" aria-label="Progreso del triaje">
        <span class="wizard-step-label">
          Paso ${triageState.step} de 2: ${triageState.step === 1 ? 'Selección de síntomas' : 'Intensidad y duración'}
        </span>
        <span class="text-xs text-muted font-medium">${triageState.selectedSymptoms.size} síntoma(s) seleccionados</span>
      </div>
      <div class="wizard-progress-bar" role="progressbar" aria-valuenow="${triageState.step * 50}" aria-valuemin="0" aria-valuemax="100">
        <div class="wizard-progress-fill" style="width: ${triageState.step * 50}%;"></div>
      </div>

      <!-- Contenedor dinámico del paso actual -->
      <div id="wizardStepContent" class="mt-6"></div>
    </div>
  `;

  const stepContainer = container.querySelector('#wizardStepContent');
  if (triageState.step === 1) {
    renderStep1(stepContainer, container);
  } else {
    renderStep2(stepContainer, container);
  }
}

/**
 * Paso 1: Buscador y selección de síntomas mediante chips táctiles
 */
function renderStep1(stepContainer, mainContainer) {
  // Extraer categorías únicas
  const categories = ['TODOS', ...new Set(triageState.catalog.map(s => s.categoria).filter(Boolean))];

  stepContainer.innerHTML = `
    <div class="card mb-6">
      <div class="card-header">
        <h2 class="card-title text-lg">¿Qué síntomas estás experimentando?</h2>
        <p class="card-subtitle text-sm text-muted">Busca o selecciona uno o varios síntomas de la lista (mínimo 1)</p>
      </div>
      <div class="card-body">
        <!-- Buscador en tiempo real -->
        <div class="form-group mb-4">
          <label for="symptomSearch" class="form-label">Buscar síntoma</label>
          <div style="position: relative;">
            <input 
              type="search" 
              id="symptomSearch" 
              class="form-input" 
              placeholder="Ej. dolor de cabeza, fiebre, mareo, pecho..." 
              value="${triageState.searchTerm}"
              aria-label="Buscar síntoma en el catálogo"
            >
          </div>
        </div>

        <!-- Filtros por categoría -->
        <div class="mb-4">
          <span class="text-xs text-muted font-semibold uppercase tracking-wider block mb-2">Categorías:</span>
          <div class="chip-group" id="categoryChips">
            ${categories.map(cat => `
              <button 
                type="button" 
                class="chip ${triageState.activeCategory === cat ? 'is-selected' : ''}" 
                data-category="${cat}"
                style="padding: var(--space-1) var(--space-3); min-height: 2rem; font-size: var(--text-xs);"
              >
                ${cat === 'TODOS' ? 'Todos los síntomas' : cat}
              </button>
            `).join('')}
          </div>
        </div>

        <!-- Lista de síntomas como chips seleccionables -->
        <div class="mb-4">
          <div class="flex items-center justify-between mb-2">
            <span class="text-xs text-muted font-semibold uppercase tracking-wider">Síntomas disponibles:</span>
            <span id="symptomCounter" class="text-xs text-muted font-medium"></span>
          </div>
          <div class="chip-group" id="symptomsList" style="max-height: 380px; overflow-y: auto; padding: var(--space-1);">
            <!-- Se llena dinámicamente -->
          </div>
        </div>
      </div>
      
      <div class="card-footer flex items-center justify-between gap-3">
        <a href="#/patient/dashboard" class="btn btn-secondary">
          Cancelar
        </a>
        <button type="button" id="btnNextStep" class="btn btn-primary" ${triageState.selectedSymptoms.size === 0 ? 'disabled' : ''}>
          <span>Siguiente: Indicar detalles</span>
          ${ui.icon('chevron-right')}
        </button>
      </div>
    </div>
  `;

  const searchInput = stepContainer.querySelector('#symptomSearch');
  const symptomsList = stepContainer.querySelector('#symptomsList');
  const counterSpan = stepContainer.querySelector('#symptomCounter');
  const btnNext = stepContainer.querySelector('#btnNextStep');

  // Función para filtrar y pintar los chips
  const updateSymptomsList = () => {
    const term = triageState.searchTerm.toLowerCase().trim();
    const cat = triageState.activeCategory;

    const filtered = triageState.catalog.filter(s => {
      const matchesSearch = !term || s.nombre.toLowerCase().includes(term) || s.categoria?.toLowerCase().includes(term);
      const matchesCategory = cat === 'TODOS' || s.categoria === cat;
      return matchesSearch && matchesCategory;
    });

    counterSpan.textContent = `Mostrando ${filtered.length} de ${triageState.catalog.length}`;

    if (filtered.length === 0) {
      symptomsList.innerHTML = `
        <div class="p-6 text-center text-muted text-sm w-full">
          No se encontraron síntomas coincidentes con "${triageState.searchTerm}". Intenta con otro término.
        </div>
      `;
      return;
    }

    symptomsList.innerHTML = filtered.map(s => {
      const isSelected = triageState.selectedSymptoms.has(s.codigo);
      const isAlarm = s.esAlarma;
      const chipClass = `chip ${isSelected ? 'is-selected' : ''} ${isAlarm ? 'chip--alarm' : ''}`;

      return `
        <button 
          type="button" 
          class="${chipClass}" 
          data-code="${s.codigo}"
          role="checkbox"
          aria-checked="${isSelected}"
          aria-label="${s.nombre}${isAlarm ? ' (Síntoma de alarma clínica)' : ''}"
        >
          ${isAlarm ? ui.icon('alert-triangle', 'icon icon--sm') : (isSelected ? ui.icon('check', 'icon icon--sm') : '')}
          <span>${s.nombre}</span>
          ${isAlarm ? '<span class="badge badge--triage-1" style="font-size: 10px; padding: 1px 4px; margin-left: 4px;">Alarma</span>' : ''}
        </button>
      `;
    }).join('');

    // Listener para cada chip
    symptomsList.querySelectorAll('button[data-code]').forEach(btn => {
      btn.addEventListener('click', () => {
        const code = btn.getAttribute('data-code');
        const symptom = triageState.catalog.find(s => s.codigo === code);
        if (!symptom) return;

        if (triageState.selectedSymptoms.has(code)) {
          triageState.selectedSymptoms.delete(code);
        } else {
          if (triageState.selectedSymptoms.size >= 20) {
            ui.showToast('No puedes seleccionar más de 20 síntomas por triaje.', 'warning');
            return;
          }
          triageState.selectedSymptoms.set(code, {
            codigo: symptom.codigo,
            nombre: symptom.nombre,
            categoria: symptom.categoria,
            esAlarma: symptom.esAlarma,
            intensidad: 5,
            duracionHoras: 12
          });
        }

        // Refrescar estado visual del botón y contador
        updateSymptomsList();
        btnNext.disabled = triageState.selectedSymptoms.size === 0;
        
        const stepCounter = mainContainer.querySelector('.wizard-stepper .text-muted');
        if (stepCounter) {
          stepCounter.textContent = `${triageState.selectedSymptoms.size} síntoma(s) seleccionados`;
        }
      });
    });
  };

  // Eventos de búsqueda y filtro
  searchInput.addEventListener('input', (e) => {
    triageState.searchTerm = e.target.value;
    updateSymptomsList();
  });

  stepContainer.querySelectorAll('#categoryChips button').forEach(catBtn => {
    catBtn.addEventListener('click', () => {
      stepContainer.querySelectorAll('#categoryChips button').forEach(b => b.classList.remove('is-selected'));
      catBtn.classList.add('is-selected');
      triageState.activeCategory = catBtn.getAttribute('data-category');
      updateSymptomsList();
    });
  });

  // Avanzar al paso 2
  btnNext.addEventListener('click', () => {
    if (triageState.selectedSymptoms.size === 0) return;
    triageState.step = 2;
    renderWizard(mainContainer);
  });

  // Render inicial
  updateSymptomsList();
}

/**
 * Paso 2: Detalles de los síntomas (Escala 0-10 de intensidad y duración en horas)
 */
function renderStep2(stepContainer, mainContainer) {
  const selectedList = Array.from(triageState.selectedSymptoms.values());

  stepContainer.innerHTML = `
    <div class="mb-4">
      <h2 class="text-xl font-bold mb-1">Detalles de tus síntomas</h2>
      <p class="text-sm text-muted mb-4">Indica la intensidad del dolor o malestar y hace cuánto tiempo comenzaron.</p>
    </div>

    <!-- Lista de tarjetas por síntoma -->
    <div class="flex flex-col gap-5 mb-6" id="symptomsDetailList">
      ${selectedList.map((item, idx) => `
        <div class="card" data-symptom-code="${item.codigo}" style="border-left: 4px solid ${item.esAlarma ? 'var(--danger)' : 'var(--primary)'};">
          <div class="card-header flex items-center justify-between pb-2">
            <div class="flex items-center gap-2">
              <span class="badge ${item.esAlarma ? 'badge--triage-1' : 'badge--scheduled'}">${idx + 1}</span>
              <h3 class="card-title text-md font-bold">${item.nombre}</h3>
            </div>
            ${item.esAlarma ? '<span class="badge badge--triage-1">Síntoma de Alarma</span>' : ''}
          </div>
          
          <div class="card-body pt-2">
            <!-- Selector de Intensidad (0-10) -->
            <div class="form-group mb-5">
              <div class="flex items-center justify-between mb-1">
                <label class="form-label m-0">Intensidad del malestar (0 = Ninguno, 10 = Máximo dolor):</label>
                <span class="badge badge--neutral font-bold text-sm intensity-label" id="label-intensity-${item.codigo}">
                  ${item.intensidad} / 10 · ${getIntensityDescription(item.intensidad)}
                </span>
              </div>
              <div class="scale-selector" role="group" aria-label="Escala de intensidad para ${item.nombre}">
                ${[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(val => `
                  <button 
                    type="button" 
                    class="scale-btn ${item.intensidad === val ? 'is-selected' : ''}" 
                    data-val="${val}"
                    data-code="${item.codigo}"
                    aria-pressed="${item.intensidad === val}"
                  >
                    ${val}
                  </button>
                `).join('')}
              </div>
            </div>

            <!-- Duración en Horas -->
            <div class="form-group mb-2">
              <label for="duration-${item.codigo}" class="form-label">
                ¿Hace cuántas horas comenzaron los síntomas aproximadamente?
              </label>
              <div class="flex flex-wrap items-center gap-2">
                <input 
                  type="number" 
                  id="duration-${item.codigo}" 
                  class="form-input duration-input" 
                  style="max-width: 140px;" 
                  min="0" 
                  step="1" 
                  value="${item.duracionHoras}" 
                  data-code="${item.codigo}"
                  aria-label="Duración en horas para ${item.nombre}"
                >
                <span class="text-sm text-muted">horas</span>

                <!-- Presets rápidos para comodidad táctil -->
                <div class="flex flex-wrap gap-1" style="margin-left: auto;">
                  ${[
                    { label: '2 h', val: 2 },
                    { label: '6 h', val: 6 },
                    { label: '12 h', val: 12 },
                    { label: '24 h (1 d)', val: 24 },
                    { label: '48 h (2 d)', val: 48 },
                    { label: '72 h (3 d)', val: 72 }
                  ].map(preset => `
                    <button 
                      type="button" 
                      class="btn btn-secondary btn--sm duration-preset" 
                      data-code="${item.codigo}" 
                      data-hours="${preset.val}"
                      style="font-size: var(--text-xs); padding: 4px 8px; min-height: 32px;"
                    >
                      ${preset.label}
                    </button>
                  `).join('')}
                </div>
              </div>
            </div>
          </div>
        </div>
      `).join('')}
    </div>

    <!-- Campo opcional de observaciones -->
    <div class="card mb-6">
      <div class="card-header pb-2">
        <h3 class="card-title text-md font-semibold">Observaciones adicionales (opcional)</h3>
        <p class="card-subtitle text-xs text-muted">Antecedentes, circunstancias o factores relevantes</p>
      </div>
      <div class="card-body">
        <div class="form-group m-0">
          <textarea 
            id="triageObservations" 
            class="form-input" 
            rows="3" 
            maxlength="500" 
            placeholder="Describe brevemente cualquier otro síntoma o condición médica relevante..."
          >${triageState.observaciones}</textarea>
          <div class="flex justify-end mt-1">
            <span id="charCount" class="text-xs text-muted">0 / 500 caracteres</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Acciones del pie del asistente -->
    <div class="flex items-center justify-between gap-4">
      <button type="button" id="btnBackToStep1" class="btn btn-secondary">
        ${ui.icon('chevron-left')}
        <span>Atrás: Modificar síntomas</span>
      </button>

      <button type="button" id="btnSubmitTriage" class="btn btn-primary btn--lg">
        ${ui.icon('activity')}
        <span>Evaluar mis síntomas</span>
      </button>
    </div>
  `;

  // Listener para botones de intensidad
  stepContainer.querySelectorAll('.scale-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const code = btn.getAttribute('data-code');
      const val = parseInt(btn.getAttribute('data-val'), 10);
      const item = triageState.selectedSymptoms.get(code);
      if (!item) return;

      item.intensidad = val;

      // Actualizar botones de este síntoma
      stepContainer.querySelectorAll(`.scale-btn[data-code="${code}"]`).forEach(b => {
        const isCur = parseInt(b.getAttribute('data-val'), 10) === val;
        b.classList.toggle('is-selected', isCur);
        b.setAttribute('aria-pressed', isCur ? 'true' : 'false');
      });

      // Actualizar label
      const label = stepContainer.querySelector(`#label-intensity-${code}`);
      if (label) {
        label.textContent = `${val} / 10 · ${getIntensityDescription(val)}`;
      }
    });
  });

  // Listener para inputs de duración
  stepContainer.querySelectorAll('.duration-input').forEach(input => {
    input.addEventListener('change', (e) => {
      const code = input.getAttribute('data-code');
      const item = triageState.selectedSymptoms.get(code);
      if (item) {
        item.duracionHoras = Math.max(0, parseFloat(e.target.value) || 0);
      }
    });
  });

  // Listener para presets de duración
  stepContainer.querySelectorAll('.duration-preset').forEach(btn => {
    btn.addEventListener('click', () => {
      const code = btn.getAttribute('data-code');
      const hours = parseInt(btn.getAttribute('data-hours'), 10);
      const item = triageState.selectedSymptoms.get(code);
      if (item) {
        item.duracionHoras = hours;
        const input = stepContainer.querySelector(`#duration-${code}`);
        if (input) input.value = hours;
      }
    });
  });

  // Contador de caracteres de observaciones
  const obsTextarea = stepContainer.querySelector('#triageObservations');
  const charCount = stepContainer.querySelector('#charCount');
  obsTextarea.addEventListener('input', (e) => {
    triageState.observaciones = e.target.value;
    charCount.textContent = `${e.target.value.length} / 500 caracteres`;
  });
  charCount.textContent = `${obsTextarea.value.length} / 500 caracteres`;

  // Volver a paso 1
  stepContainer.querySelector('#btnBackToStep1').addEventListener('click', () => {
    triageState.step = 1;
    renderWizard(mainContainer);
  });

  // Enviar a evaluar
  const btnSubmit = stepContainer.querySelector('#btnSubmitTriage');
  btnSubmit.addEventListener('click', async () => {
    await submitTriage(mainContainer, btnSubmit);
  });
}

/**
 * Retorna texto descriptivo para el nivel de intensidad
 */
function getIntensityDescription(val) {
  if (val <= 3) return 'Leve';
  if (val <= 6) return 'Moderada';
  if (val <= 8) return 'Severa';
  return 'Muy intensa';
}

/**
 * Ejecuta el envío de la evaluación de triaje al backend
 */
async function submitTriage(container, submitBtn) {
  ui.setButtonLoading(submitBtn, true);

  const payload = {
    sintomas: Array.from(triageState.selectedSymptoms.values()).map(s => ({
      codigo: s.codigo,
      duracionHoras: s.duracionHoras,
      intensidad: s.intensidad
    })),
    observaciones: triageState.observaciones?.trim() || null
  };

  try {
    const resultado = await api.post('/triage', payload);
    ui.setButtonLoading(submitBtn, false);

    // Evaluación de Corte de Emergencia
    if (resultado.esEmergencia || resultado.nivelPrioridad === 'I') {
      renderEmergencyView(container, resultado);
    } else {
      renderTriageResultView(container, resultado);
    }
  } catch (err) {
    ui.setButtonLoading(submitBtn, false);
    ui.showToast(err.message || 'Error al evaluar el triaje. Por favor intenta de nuevo.', 'danger');
  }
}

/**
 * PANTALLA DE CORTE DE EMERGENCIA (Infalible, sin botón de agendar)
 * @param {HTMLElement} container
 * @param {Object} resultado
 */
export function renderEmergencyView(container, resultado) {
  window.scrollTo(0, 0);

  // Obtener nombres legibles de los síntomas de alarma
  const alarmSymptomItems = (resultado.sintomasAlarma && resultado.sintomasAlarma.length > 0)
    ? resultado.sintomasAlarma.map(codigo => {
        const fromRes = (resultado.sintomas || []).find(s => s.codigo === codigo);
        if (fromRes && fromRes.nombre) return fromRes.nombre;
        const fromCat = (triageState.catalog || []).find(c => c.codigo === codigo);
        if (fromCat && fromCat.nombre) return fromCat.nombre;
        return codigo.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
      })
    : ['Signos de alarma clínica detectados'];

  container.innerHTML = `
    <div style="max-width: var(--container-narrow); margin: 0 auto; padding-top: var(--space-6); padding-bottom: var(--space-12);">
      
      <!-- Tarjeta Principal de Emergencia Nivel I -->
      <div class="card mb-6" style="border: 2px solid var(--danger); border-top: 8px solid var(--danger); box-shadow: var(--shadow-xl); overflow: hidden;">
        
        <!-- Header de Emergencia -->
        <div class="p-6" style="background: linear-gradient(180deg, rgba(239, 68, 68, 0.08) 0%, var(--surface) 100%); border-bottom: 1px solid var(--border-color);">
          <div class="flex flex-wrap items-center justify-between gap-2 mb-3">
            <span class="badge badge--triage-1 text-sm font-bold flex items-center gap-1" style="background-color: var(--danger); color: #FFFFFF; padding: var(--space-1) var(--space-3); border-radius: var(--radius-full);">
              ${ui.icon('alert-triangle', 'icon icon--sm')}
              <span>Nivel I · Emergencia Médica Inmediata</span>
            </span>
            ${resultado.publicId ? `<span class="text-xs text-muted font-mono">ID: ${resultado.publicId.slice(0, 8)}</span>` : ''}
          </div>

          <div class="flex items-start gap-4 mt-2">
            <div style="background-color: var(--danger); color: #FFFFFF; border-radius: 50%; width: 48px; height: 48px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; box-shadow: 0 4px 10px rgba(220, 38, 38, 0.3);">
              ${ui.icon('alert-triangle', 'icon icon--lg')}
            </div>
            <div>
              <h1 class="text-2xl font-bold m-0" style="color: var(--danger); line-height: var(--leading-tight);">
                Busca atención de urgencias ahora
              </h1>
              <p class="text-base font-medium mt-2 mb-0" style="color: var(--text); line-height: var(--leading-normal);">
                ${resultado.mensaje || 'Llama al 123 o acude a urgencias de inmediato.'}
              </p>
            </div>
          </div>
        </div>

        <div class="card-body p-6">
          
          <!-- Bloque de Acción Inmediata: Botón de Llamada al 123 -->
          <div class="text-center p-6 mb-6" style="background-color: var(--surface-2); border-radius: var(--radius-lg); border: 1px solid var(--border-color);">
            <p class="text-sm font-semibold text-text mb-3">
              Si tú o el paciente están en peligro inminente:
            </p>
            <a 
              href="tel:123" 
              class="btn btn-danger btn--lg" 
              id="btnEmergencyCall"
              style="font-size: var(--text-2xl); font-weight: var(--weight-bold); padding: var(--space-4) var(--space-8); min-height: 4.2rem; width: 100%; max-width: 400px; margin: 0 auto; display: inline-flex; align-items: center; justify-content: center; gap: var(--space-3); box-shadow: 0 10px 15px -3px rgba(220, 38, 38, 0.3), 0 4px 6px -4px rgba(220, 38, 38, 0.2); text-decoration: none; border-radius: var(--radius-md);"
            >
              ${ui.icon('phone', 'icon icon--lg')}
              <span>Llamar al 123</span>
            </a>
            <p class="text-xs text-muted font-medium mt-3 mb-0">
              Línea Única de Emergencias Nacional (Colombia) · Gratuita desde cualquier teléfono o celular
            </p>
          </div>

          <!-- Signos de alarma clínica detectados -->
          <div class="p-4 mb-6" style="background-color: var(--triage-1-bg); border-left: 4px solid var(--danger); border-radius: var(--radius-md);">
            <div class="flex items-center gap-2 mb-2 text-danger font-bold text-xs uppercase tracking-wider">
              ${ui.icon('alert-circle', 'icon icon--sm')}
              <span>Signos de alarma clínica detectados:</span>
            </div>
            <div class="flex flex-wrap gap-2 mt-1">
              ${alarmSymptomItems.map(item => `
                <span class="badge" style="background-color: #FFFFFF; color: var(--danger); border: 1px solid var(--danger); font-weight: var(--weight-semibold); font-size: var(--text-sm); padding: var(--space-1) var(--space-3);">
                  ${item}
                </span>
              `).join('')}
            </div>
          </div>

          <!-- Recomendaciones críticas de seguridad y primeros auxilios -->
          <div class="p-5 mb-6" style="background-color: var(--surface); border: 1px solid var(--border-color); border-radius: var(--radius-md);">
            <h3 class="text-sm font-bold uppercase tracking-wider text-muted mb-4 flex items-center gap-2">
              ${ui.icon('shield', 'icon icon--sm text-danger')}
              <span>Recomendaciones inmediatas de seguridad</span>
            </h3>
            <div class="flex flex-col gap-3">
              <div class="flex items-start gap-3">
                <span style="background-color: rgba(239, 68, 68, 0.12); color: var(--danger); border-radius: var(--radius-sm); width: 24px; height: 24px; display: inline-flex; align-items: center; justify-content: center; font-weight: bold; font-size: 13px; flex-shrink: 0;">1</span>
                <div>
                  <strong class="text-sm font-semibold block text-text">No conduzcas:</strong>
                  <span class="text-sm text-muted">No manejes ningún vehículo. Pide auxilio inmediato a un familiar, vecino o solicita una ambulancia al 123.</span>
                </div>
              </div>
              <div class="flex items-start gap-3">
                <span style="background-color: rgba(239, 68, 68, 0.12); color: var(--danger); border-radius: var(--radius-sm); width: 24px; height: 24px; display: inline-flex; align-items: center; justify-content: center; font-weight: bold; font-size: 13px; flex-shrink: 0;">2</span>
                <div>
                  <strong class="text-sm font-semibold block text-text">Reposo absoluto:</strong>
                  <span class="text-sm text-muted">Suspende cualquier esfuerzo físico. Mantén la calma y permanece sentado en posición erguida si sientes dolor torácico o falta de aire.</span>
                </div>
              </div>
              <div class="flex items-start gap-3">
                <span style="background-color: rgba(239, 68, 68, 0.12); color: var(--danger); border-radius: var(--radius-sm); width: 24px; height: 24px; display: inline-flex; align-items: center; justify-content: center; font-weight: bold; font-size: 13px; flex-shrink: 0;">3</span>
                <div>
                  <strong class="text-sm font-semibold block text-text">Acude a Urgencias:</strong>
                  <span class="text-sm text-muted">Si estás cerca de una institución hospitalaria con servicio de urgencias 24h, trasládate de inmediato acompañado.</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Aviso legal obligatorio inmutable -->
          <div class="p-4" style="background-color: var(--surface-2); border-radius: var(--radius-md); font-size: var(--text-xs); color: var(--text-muted); line-height: var(--leading-normal);">
            <strong class="text-text block mb-1">Aviso de Orientación Asistencial:</strong>
            <p class="m-0 mb-1">${resultado.aviso || 'Esta orientación es un prototipo, no sustituye la valoración de un profesional de la salud.'}</p>
            <span class="font-mono text-muted" style="font-size: 11px;">Versión de reglas clínicas: ${resultado.versionReglas || 'v1-prototipo'}</span>
          </div>
        </div>

        <!-- Acciones secundarias seguras -->
        <div class="card-footer flex flex-wrap items-center justify-between gap-3 p-4" style="background-color: var(--surface-2); border-top: 1px solid var(--border-color);">
          <a href="#/patient/dashboard" class="btn btn-secondary">
            ${ui.icon('arrow-left')}
            <span>Volver a mi panel</span>
          </a>

          <a href="#/patient/triage" class="btn btn-ghost text-sm">
            <span>Iniciar nueva consulta</span>
          </a>
        </div>
      </div>
    </div>
  `;
}

/**
 * PANTALLA DE RESULTADO NO URGENTE (Niveles II a V)
 * @param {HTMLElement} container
 * @param {Object} resultado
 */
export function renderTriageResultView(container, resultado) {
  window.scrollTo(0, 0);

  const nivel = resultado.nivelPrioridad || 'III';
  const nivelConfig = getNivelDisplayConfig(nivel);

  container.innerHTML = `
    <div style="max-width: var(--container-narrow); margin: 0 auto; padding-top: var(--space-6); padding-bottom: var(--space-12);">
      
      <!-- Tarjeta Principal del Resultado -->
      <div class="card mb-6" style="border-top: 6px solid ${nivelConfig.borderColor};">
        <div class="card-header pb-3">
          <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
            <span class="badge ${nivelConfig.badgeClass} text-sm font-bold flex items-center gap-2">
              ${ui.icon(nivelConfig.icon, 'icon icon--sm')}
              <span>Nivel ${nivel} · ${nivelConfig.title}</span>
            </span>
            <span class="text-xs text-muted font-mono">ID: ${resultado.publicId.slice(0, 8)}</span>
          </div>
          <h1 class="card-title text-2xl font-bold">Resultado de tu Orientación</h1>
          <p class="card-subtitle text-sm text-muted">Clasificación orientativa según los síntomas reportados</p>
        </div>

        <div class="card-body">
          <!-- Mensaje orientativo del motor de reglas -->
          <div class="alert alert--info mb-6">
            ${ui.icon('info', 'icon alert-icon')}
            <div class="alert-content">
              <div class="alert-title">Ruta asistencial sugerida: ${formatRutaSugerida(resultado.rutaSugerida)}</div>
              <p class="m-0">${resultado.mensaje}</p>
            </div>
          </div>

          <!-- Resumen de los síntomas evaluados -->
          <div class="mb-6">
            <h3 class="text-sm font-bold uppercase tracking-wider text-muted mb-3">Síntomas reportados:</h3>
            <div class="table-container">
              <table class="table">
                <thead>
                  <tr>
                    <th>Síntoma</th>
                    <th>Intensidad</th>
                    <th>Duración</th>
                  </tr>
                </thead>
                <tbody>
                  ${(resultado.sintomas || []).map(s => `
                    <tr>
                      <td class="font-medium">${s.nombre}</td>
                      <td>
                        <span class="badge badge--neutral">${s.intensidad} / 10</span>
                      </td>
                      <td>${s.duracionHoras} h</td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          </div>

          <!-- Aviso Legal Inmutable -->
          <div class="p-4" style="background-color: var(--surface-2); border-radius: var(--radius-md); font-size: var(--text-xs); color: var(--text-muted); line-height: var(--leading-normal);">
            <strong class="text-text block mb-1">Aviso de Orientación Asistencial:</strong>
            ${resultado.aviso || 'Esta orientación es un prototipo, no sustituye la valoración de un profesional de la salud.'}
          </div>
        </div>

        <!-- Acciones Principales: Agendar cita médica vinculada -->
        <div class="card-footer flex flex-wrap items-center justify-between gap-3">
          <a href="#/patient/dashboard" class="btn btn-secondary">
            <span>Volver a mi panel</span>
          </a>

          <a href="#/patient/book?triageId=${resultado.publicId}" class="btn btn-primary btn--lg" id="btnBookFromTriage">
            ${ui.icon('calendar')}
            <span>Ver horarios disponibles para agendar</span>
          </a>
        </div>
      </div>
    </div>
  `;
}

/**
 * Consulta un triaje existente por ID para lectura
 */
async function renderReadOnlyTriage(container, triageId) {
  ui.renderLoading(container, 'Cargando información del triaje...');
  try {
    const triaje = await api.get(`/triage/${triageId}`);
    if (triaje.esEmergencia || triaje.nivelPrioridad === 'I') {
      renderEmergencyView(container, triaje);
    } else {
      renderTriageResultView(container, triaje);
    }
  } catch (err) {
    ui.renderError(container, {
      title: 'No fue posible abrir el triaje',
      message: err.message || 'No se encontró el triaje solicitado o no tienes permiso para acceder a él.',
      onRetry: () => router.navigate('/patient/dashboard')
    });
  }
}

/**
 * Mapeo estético de badges y colores por nivel de triaje (I..V)
 */
function getNivelDisplayConfig(nivel) {
  switch (nivel) {
    case 'I':
      return {
        badgeClass: 'badge--triage-1',
        title: 'Emergencia Inmediata',
        borderColor: 'var(--triage-1-bd)',
        icon: 'alert-triangle'
      };
    case 'II':
      return {
        badgeClass: 'badge--triage-2',
        title: 'Atención Prioritaria',
        borderColor: 'var(--triage-2-bd)',
        icon: 'alert-circle'
      };
    case 'III':
      return {
        badgeClass: 'badge--triage-3',
        title: 'Cita Presencial Prioritaria',
        borderColor: 'var(--triage-3-bd)',
        icon: 'clock'
      };
    case 'IV':
      return {
        badgeClass: 'badge--triage-4',
        title: 'Cita No Urgente / Telemedicina',
        borderColor: 'var(--triage-4-bd)',
        icon: 'activity'
      };
    case 'V':
    default:
      return {
        badgeClass: 'badge--triage-5',
        title: 'Consulta General Programada',
        borderColor: 'var(--triage-5-bd)',
        icon: 'calendar'
      };
  }
}

/**
 * Formatea la ruta sugerida a texto amigable
 */
function formatRutaSugerida(ruta) {
  switch (ruta) {
    case 'URGENCIAS':
      return 'Servicio de Urgencias';
    case 'ATENCION_PRIORITARIA':
      return 'Consulta Médica Prioritaria';
    case 'CITA_PRESENCIAL':
      return 'Cita Médica Presencial';
    case 'CITA_TELEMEDICINA':
      return 'Consulta por Telemedicina';
    case 'CONSULTA_PROGRAMADA':
      return 'Consulta Médica Programada';
    default:
      return ruta || 'Atención General';
  }
}
