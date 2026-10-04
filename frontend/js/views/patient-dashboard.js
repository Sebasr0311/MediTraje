/**
 * MediTriaje 2.0 — Vista del Dashboard del Paciente (patient-dashboard.js)
 * Carga reactiva de perfil, próxima cita, historial y recetas recientes (M8.2a, HU-09).
 */

import { api } from '../api.js';
import { auth } from '../auth.js';
import { router } from '../router.js';
import { ui } from '../ui.js';

/**
 * Formatea una fecha ISO a formato colombiano legible
 * @param {string} isoString Fecha en formato ISO
 * @param {boolean} includeTime Si incluye hora y minutos
 * @returns {string} Fecha formateada
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
      options.hour12 = true;
    }
    return new Intl.DateTimeFormat('es-CO', options).format(d);
  } catch {
    return isoString;
  }
}

/**
 * Renderiza el dashboard principal del paciente
 * @param {HTMLElement} container Contenedor principal
 */
export async function patientDashboardView(container) {
  // 1. Estructura base inicial con skeletons de carga
  container.innerHTML = `
    <div class="sg-section" style="padding-top: var(--space-6); padding-bottom: var(--space-12);">
      <!-- Saludo y bienvenida -->
      <div id="patientGreetingHeader" class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <div class="skeleton skeleton-title" style="width: 240px; height: 32px; margin-bottom: 8px;"></div>
          <div class="skeleton skeleton-text" style="width: 320px; height: 16px;"></div>
        </div>
        <span class="badge badge--confirmed">
          ${ui.icon('check', 'icon icon--sm')}
          <span>Paciente Activo</span>
        </span>
      </div>

      <!-- Tarjeta destacada de Triaje -->
      <div class="card card--highlight mb-8">
        <div class="card-header flex items-center gap-3">
          <div style="background-color: var(--teal-100); color: var(--primary); padding: 8px; border-radius: var(--radius-md); display: flex;">
            ${ui.icon('activity', 'icon icon--md')}
          </div>
          <div>
            <h2 class="card-title text-xl">¿Necesitas orientación médica hoy?</h2>
            <p class="card-subtitle text-sm text-muted">Evalúa tus síntomas de forma ágil y segura</p>
          </div>
        </div>
        <div class="card-body">
          <p class="text-sm mb-5" style="max-width: 65ch;">
            Inicia nuestro sistema de triaje clínico para clasificar tu nivel de prioridad asistencial y sugerirte de inmediato la ruta más oportuna de atención.
          </p>
          <a href="#/patient/triage" class="btn btn-primary btn--lg">
            ${ui.icon('activity')}
            <span>Iniciar nuevo triaje</span>
          </a>
        </div>
      </div>

      <!-- Grid con Próxima Cita y Resúmenes -->
      <div class="grid grid-cols-1 grid-cols-2-md gap-6 mb-8">
        <!-- Próxima Cita -->
        <div class="card" id="cardNextAppointment">
          <div class="card-header flex items-center justify-between">
            <h3 class="card-title text-lg flex items-center gap-2">
              ${ui.icon('calendar', 'icon icon--sm text-primary')}
              <span>Próxima Cita</span>
            </h3>
            <a href="#/patient/appointments" class="text-sm font-medium text-primary">Ver todas</a>
          </div>
          <div class="card-body" id="appointmentContainer">
            <div class="skeleton skeleton-card" style="height: 120px;"></div>
          </div>
        </div>

        <!-- Resumen de Atenciones Recientes -->
        <div class="card" id="cardRecentHistory">
          <div class="card-header flex items-center justify-between">
            <h3 class="card-title text-lg flex items-center gap-2">
              ${ui.icon('file-text', 'icon icon--sm text-primary')}
              <span>Últimas Atenciones</span>
            </h3>
            <a href="#/patient/history" class="text-sm font-medium text-primary">Ver historia</a>
          </div>
          <div class="card-body" id="historyContainer">
            <div class="skeleton skeleton-card" style="height: 120px;"></div>
          </div>
        </div>
      </div>

      <!-- Recetas Médicas Recientes -->
      <div class="card mb-8" id="cardRecentPrescriptions">
        <div class="card-header flex items-center justify-between">
          <h3 class="card-title text-lg flex items-center gap-2">
            ${ui.icon('pill', 'icon icon--sm text-primary')}
            <span>Mis Recetas Médicas</span>
          </h3>
          <a href="#/patient/prescriptions" class="text-sm font-medium text-primary">Ver todas las recetas</a>
        </div>
        <div class="card-body" id="prescriptionsContainer">
          <div class="skeleton skeleton-card" style="height: 100px;"></div>
        </div>
      </div>

      <!-- Seguimiento Post-Atención (F2.2.4) -->
      <div class="card mb-8" id="cardRecentFollowUps">
        <div class="card-header flex items-center justify-between">
          <h3 class="card-title text-lg flex items-center gap-2">
            ${ui.icon('shield', 'icon icon--sm text-primary')}
            <span>Seguimiento Post-Atención</span>
          </h3>
          <a href="#/patient/follow-ups" class="text-sm font-medium text-primary">Ver todas mis tareas</a>
        </div>
        <div class="card-body" id="followUpsContainer">
          <div class="skeleton skeleton-card" style="height: 100px;"></div>
        </div>
      </div>
    </div>
  `;

  // 2. Carga en paralelo de datos del paciente
  const [perfilResult, appointmentsResult, historyResult, prescriptionsResult, followUpsResult] = await Promise.allSettled([
    api.get('/patients/me'),
    api.get('/patients/me/appointments?page=0&size=5'),
    api.get('/patients/me/history?page=0&size=3'),
    api.get('/patients/me/prescriptions?page=0&size=3'),
    api.get('/patients/me/follow-ups?page=0&size=3')
  ]);

  // 3. Renderizar Saludo
  const greetingEl = document.getElementById('patientGreetingHeader');
  if (perfilResult.status === 'fulfilled' && perfilResult.value) {
    const p = perfilResult.value;
    const nombreCompleto = `${p.nombres || ''} ${p.apellidos || ''}`.trim() || auth.user?.email || 'Paciente';
    greetingEl.querySelector('div').innerHTML = `
      <h1 class="text-2xl font-bold mb-1">¡Hola, ${nombreCompleto}!</h1>
      <p class="text-muted text-sm">Documento: ${p.tipoDocumento} ${p.numeroDocumento} · Portal personal de salud</p>
    `;
  } else {
    greetingEl.querySelector('div').innerHTML = `
      <h1 class="text-2xl font-bold mb-1">¡Bienvenido a MediTriaje!</h1>
      <p class="text-muted text-sm">${auth.user?.email || 'Portal del paciente'}</p>
    `;
  }

  // 4. Renderizar Próxima Cita
  const appointmentContainer = document.getElementById('appointmentContainer');
  if (appointmentsResult.status === 'fulfilled' && appointmentsResult.value?.content) {
    const citas = appointmentsResult.value.content;
    // Buscar la cita activa más próxima (PROGRAMADA o CONFIRMADA)
    const proximaCita = citas.find(c => c.estado === 'PROGRAMADA' || c.estado === 'CONFIRMADA');

    if (proximaCita) {
      const badgeClass = proximaCita.estado === 'CONFIRMADA' ? 'badge--confirmed' : 'badge--scheduled';
      appointmentContainer.innerHTML = `
        <div style="background-color: var(--surface-2); border-radius: var(--radius-md); padding: var(--space-4); border-left: 4px solid var(--primary);">
          <div class="flex items-center justify-between mb-2">
            <span class="badge ${badgeClass}">${proximaCita.estado}</span>
            <span class="text-xs text-muted font-medium">${proximaCita.modalidad || 'PRESENCIAL'}</span>
          </div>

          <div class="font-semibold text-base mb-1" style="color: var(--text);">
            ${proximaCita.especialidadNombre || 'Consulta General'}
          </div>

          <div class="text-sm text-muted mb-3">
            <div><strong>Fecha:</strong> ${formatDate(proximaCita.fechaHoraInicio)}</div>
            <div><strong>Profesional:</strong> ${proximaCita.profesionalNombre || 'Por asignar'}</div>
            <div><strong>Sede:</strong> ${proximaCita.sedeNombre || 'Sede Principal'} · ${proximaCita.sedeDireccion || ''}</div>
          </div>

          <div class="flex gap-2">
            <a href="#/patient/appointments" class="btn btn-secondary btn--sm">
              <span>Gestionar cita</span>
            </a>
          </div>
        </div>
      `;
    } else {
      // Estado vacío de citas
      appointmentContainer.innerHTML = `
        <div class="empty-state text-center py-4">
          <div class="empty-state-icon" style="margin: 0 auto 8px auto; width: 40px; height: 40px;">
            ${ui.icon('calendar', 'icon icon--md text-muted')}
          </div>
          <p class="text-sm font-medium mb-1">Aún no tienes citas agendadas</p>
          <p class="text-xs text-muted mb-4">Puedes consultar horarios disponibles o realizar un triaje previo.</p>
          <a href="#/patient/triage" class="btn btn-secondary btn--sm">
            ${ui.icon('search', 'icon icon--sm')}
            <span>Agendar nueva cita</span>
          </a>
        </div>
      `;
    }
  } else {
    appointmentContainer.innerHTML = `
      <div class="alert alert--warning text-xs">
        ${ui.icon('alert-triangle', 'icon alert-icon')}
        <div>No fue posible cargar tus citas en este momento.</div>
      </div>
    `;
  }

  // 5. Renderizar Últimas Atenciones
  const historyContainer = document.getElementById('historyContainer');
  if (historyResult.status === 'fulfilled' && historyResult.value?.content) {
    const atenciones = historyResult.value.content;
    if (atenciones.length > 0) {
      historyContainer.innerHTML = `
        <div class="flex flex-col gap-3">
          ${atenciones.map(a => `
            <div style="border-bottom: 1px solid var(--border); padding-bottom: var(--space-3);" class="last-no-border">
              <div class="flex justify-between items-center text-xs text-muted mb-1">
                <span>${formatDate(a.fechaAtencion || a.createdAt, false)}</span>
                <span class="badge badge--neutral">${a.profesionalEspecialidad || 'Atención'}</span>
              </div>
              <div class="font-medium text-sm mb-1">${a.motivoConsulta || 'Consulta médica'}</div>
              <div class="text-xs text-muted">
                ${a.diagnosticos?.length ? `<strong>Diag:</strong> ${a.diagnosticos[0].codigoCie10} - ${a.diagnosticos[0].descripcion}` : 'Diagnóstico registrado en historia clínica'}
              </div>
            </div>
          `).join('')}
        </div>
      `;
    } else {
      historyContainer.innerHTML = `
        <div class="empty-state text-center py-4">
          <div class="empty-state-icon" style="margin: 0 auto 8px auto; width: 40px; height: 40px;">
            ${ui.icon('file-text', 'icon icon--md text-muted')}
          </div>
          <p class="text-sm font-medium mb-1">Sin atenciones registradas</p>
          <p class="text-xs text-muted">Tu historial clínico aparecerá aquí después de tus consultas.</p>
        </div>
      `;
    }
  } else {
    historyContainer.innerHTML = `
      <div class="alert alert--warning text-xs">
        ${ui.icon('alert-triangle', 'icon alert-icon')}
        <div>No fue posible cargar tu historial clínico.</div>
      </div>
    `;
  }

  // 6. Renderizar Recetas Recientes
  const prescriptionsContainer = document.getElementById('prescriptionsContainer');
  if (prescriptionsResult.status === 'fulfilled' && prescriptionsResult.value?.content) {
    const recetas = prescriptionsResult.value.content;
    if (recetas.length > 0) {
      prescriptionsContainer.innerHTML = `
        <div class="grid grid-cols-1 grid-cols-2-md gap-4">
          ${recetas.map(r => `
            <div class="card" style="background-color: var(--surface-2); padding: var(--space-4); border: 1px solid var(--border);">
              <div class="flex justify-between items-start mb-2">
                <div>
                  <span class="badge badge--confirmed mb-1">Vigente (${r.vigenciaDias} días)</span>
                  <div class="text-xs text-muted">Expedida: ${formatDate(r.createdAt, false)}</div>
                </div>
                <span class="text-xs font-medium text-muted">${r.profesionalNombre || 'Médico tratante'}</span>
              </div>

              <div class="text-sm mt-3">
                <div class="font-medium mb-1 text-xs text-muted">Medicamentos prescritos:</div>
                <ul style="padding-left: var(--space-4); margin: 0;" class="text-xs">
                  ${r.detalles?.map(d => `
                    <li><strong>${d.nombreComercial}</strong> (${d.principioActivo} ${d.concentracion}) - ${d.dosis}, ${d.frecuencia}</li>
                  `).join('') || '<li>Detalles disponibles en receta</li>'}
                </ul>
              </div>
            </div>
          `).join('')}
        </div>
      `;
    } else {
      prescriptionsContainer.innerHTML = `
        <div class="empty-state text-center py-4">
          <div class="empty-state-icon" style="margin: 0 auto 8px auto; width: 40px; height: 40px;">
            ${ui.icon('pill', 'icon icon--md text-muted')}
          </div>
          <p class="text-sm font-medium mb-1">No tienes recetas médicas recientes</p>
          <p class="text-xs text-muted">Las fórmulas farmacológicas emitidas por tus médicos aparecerán aquí.</p>
        </div>
      `;
    }
  } else {
    prescriptionsContainer.innerHTML = `
      <div class="alert alert--warning text-xs">
        ${ui.icon('alert-triangle', 'icon alert-icon')}
        <div>No fue posible cargar tus recetas médicas.</div>
      </div>
    `;
  }

  // 7. Renderizar Seguimientos Post-Atención Recientes (F2.2.4)
  const followUpsContainer = document.getElementById('followUpsContainer');
  if (followUpsResult.status === 'fulfilled' && followUpsResult.value?.content) {
    const seguimientos = followUpsResult.value.content;
    if (seguimientos.length > 0) {
      followUpsContainer.innerHTML = `
        <div class="grid grid-cols-1 grid-cols-2-md gap-4">
          ${seguimientos.map(s => {
            const isPending = s.estado === 'PENDIENTE';
            const badgeClass = isPending ? 'badge--scheduled' : (s.estado === 'COMPLETADO' ? 'badge--confirmed' : 'badge--cancelled');
            const tipoLabel = s.tipo ? s.tipo.replace(/_/g, ' ') : 'SEGUIMIENTO';
            return `
              <div class="card" style="background-color: var(--surface-2); padding: var(--space-4); border: 1px solid var(--border); border-left: 3px solid ${isPending ? 'var(--primary)' : 'var(--success)'};">
                <div class="flex justify-between items-start mb-2">
                  <span class="badge ${badgeClass}">${s.estado}</span>
                  <span class="text-xs text-muted uppercase font-medium">${tipoLabel}</span>
                </div>
                <div class="font-medium text-sm mb-1">${s.profesionalNombre || 'Médico tratante'} · <span class="text-xs text-muted">${s.profesionalEspecialidad || ''}</span></div>
                <p class="text-xs text-muted mb-3" style="display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;">${s.indicaciones || ''}</p>
                <div class="flex justify-between items-center text-xs">
                  <span class="text-muted">${s.fechaSugeridaControl ? 'Control: ' + s.fechaSugeridaControl : ''}</span>
                  <a href="#/patient/follow-ups" class="font-medium text-primary">${isPending ? 'Reportar evolución' : 'Ver detalle'}</a>
                </div>
              </div>
            `;
          }).join('')}
        </div>
      `;
    } else {
      followUpsContainer.innerHTML = `
        <div class="empty-state text-center py-4">
          <div class="empty-state-icon" style="margin: 0 auto 8px auto; width: 40px; height: 40px;">
            ${ui.icon('shield', 'icon icon--md text-muted')}
          </div>
          <p class="text-sm font-medium mb-1">Sin tareas de seguimiento pendientes</p>
          <p class="text-xs text-muted">Cuando tus profesionales indiquen controles o exámenes posteriores, se mostrarán aquí.</p>
        </div>
      `;
    }
  } else {
    followUpsContainer.innerHTML = `
      <div class="alert alert--warning text-xs">
        ${ui.icon('alert-triangle', 'icon alert-icon')}
        <div>No fue posible cargar tus tareas de seguimiento.</div>
      </div>
    `;
  }
}

