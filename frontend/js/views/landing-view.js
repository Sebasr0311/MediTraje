/**
 * MediTriaje 2.0 — Vista de Landing Page (landing-view.js)
 * Diseño profesional inspirado en Radix UI y shadcn/ui.
 * Totalmente accesible, responsivo y basado en tokens CSS de MediTriaje 2.0.
 */

import { auth } from '../auth.js';
import { ui } from '../ui.js';

export async function landingView(container) {
  const isAuth = auth.isAuthenticated;
  let userDashboardUrl = '#/patient/dashboard';
  let userDashboardLabel = 'Ir a mi panel de paciente';

  if (auth.isAdmin) {
    userDashboardUrl = '#/admin/dashboard';
    userDashboardLabel = 'Ir al panel de administración';
  } else if (auth.isProfesional) {
    userDashboardUrl = '#/professional/agenda';
    userDashboardLabel = 'Ir a mi agenda médica';
  } else if (auth.isFarmaceutico) {
    userDashboardUrl = '#/pharmacy/dispensation';
    userDashboardLabel = 'Ir a dispensación farmacéutica';
  }

  container.innerHTML = `
    <div class="landing-hero">
      <!-- 1. Hero Badge Pill (shadcn style) -->
      <button type="button" id="btnScrollSim1" class="hero-badge" aria-label="Ir al simulador de triaje en vivo">
        <span style="color: var(--primary); display: inline-flex;">${ui.icon('sparkles', 'icon icon--xs')}</span>
        <span>MediTriaje 2.0 · Plataforma Cloud Asistencial en Vivo</span>
        <span style="color: var(--text-muted); display: inline-flex;">${ui.icon('arrow-right', 'icon icon--xs')}</span>
      </button>

      <!-- 2. Hero Headline & Description -->
      <h1 class="text-4xl font-bold mb-4" style="max-width: 26ch; margin-left: auto; margin-right: auto; line-height: 1.15; letter-spacing: -0.02em;">
        Atención médica oportuna, <span class="gradient-text">triaje clínico inteligente</span> y recetas digitales
      </h1>

      <p class="text-muted text-lg mb-8" style="max-width: 62ch; margin-left: auto; margin-right: auto; line-height: 1.6;">
        Sistema integral de gestión en salud bajo normativa colombiana. Orienta tus síntomas en tiempo real, agenda citas médicas presenciales o de telemedicina sin colisiones, y consulta tu historial clínico inmutable.
      </p>

      <!-- 3. Primary / Secondary CTAs -->
      <div class="flex flex-wrap gap-4 justify-center items-center mb-8">
        ${isAuth ? `
          <a href="${userDashboardUrl}" class="btn btn-primary btn--lg" style="box-shadow: var(--shadow-md);">
            ${ui.icon('user', 'icon')}
            <span>${userDashboardLabel}</span>
            ${ui.icon('arrow-right', 'icon icon--sm')}
          </a>
        ` : `
          <a href="#/register" class="btn btn-primary btn--lg" style="box-shadow: var(--shadow-md);">
            ${ui.icon('activity', 'icon')}
            <span>Comenzar mi triaje ahora</span>
            ${ui.icon('arrow-right', 'icon icon--sm')}
          </a>
          <a href="#/login" class="btn btn-secondary btn--lg">
            <span>Ingresar a mi cuenta</span>
          </a>
        `}
        <button type="button" id="btnScrollSim2" class="btn btn-ghost btn--lg">
          ${ui.icon('search', 'icon icon--sm')}
          <span>Probar simulador en vivo</span>
        </button>
      </div>

      <!-- 4. Micro-Trust Bar -->
      <div class="flex flex-wrap justify-center items-center gap-6 text-xs text-muted">
        <span class="flex items-center gap-2">
          ${ui.icon('shield', 'icon icon--sm text-primary')}
          <span>Cifrado Argon2id &amp; JWT</span>
        </span>
        <span class="flex items-center gap-2">
          ${ui.icon('clock', 'icon icon--sm text-primary')}
          <span>Corte de emergencia 123</span>
        </span>
        <span class="flex items-center gap-2">
          ${ui.icon('file-text', 'icon icon--sm text-primary')}
          <span>Inmutabilidad clínica ADR-007</span>
        </span>
        <span class="flex items-center gap-2">
          ${ui.icon('hospital', 'icon icon--sm text-primary')}
          <span>Triaje MinSalud Colombia</span>
        </span>
      </div>

      <!-- 5. Mockup Dashboard Preview Card (shadcn visual style) -->
      <div class="hero-preview-window">
        <div class="window-header">
          <div class="window-dots">
            <span class="window-dot window-dot--red"></span>
            <span class="window-dot window-dot--yellow"></span>
            <span class="window-dot window-dot--green"></span>
          </div>
          <span class="text-xs font-mono text-muted" style="margin-left: 8px;">
            https://meditraje.vercel.app · Sesión Segura (TLS Directo)
          </span>
          <span style="margin-left: auto;" class="flex items-center gap-2 text-xs font-medium text-success">
            <span class="pulse-dot"></span>
            <span>Servicios Asistenciales en Línea</span>
          </span>
        </div>

        <div style="padding: var(--space-6);">
          <div class="flex flex-wrap items-center justify-between gap-4 mb-6 pb-4" style="border-bottom: 1px solid var(--border);">
            <div>
              <div class="text-xs text-muted uppercase font-bold tracking-wider mb-1">Paciente Asistencial</div>
              <div class="text-lg font-bold">Ana Sofía Morales Gómez</div>
              <div class="text-xs text-muted">CC 1.065.432.890 · IPS MediSalud Valledupar</div>
            </div>
            <div class="flex gap-2">
              <span class="badge" style="background: var(--triage-2-bg); color: var(--triage-2-fg); border: 1px solid var(--triage-2-bd);">
                Nivel II · Muy urgente (Naranja)
              </span>
              <span class="badge badge--confirmed">
                ${ui.icon('check', 'icon icon--xs')} Cita Confirmada
              </span>
            </div>
          </div>

          <div class="grid grid-cols-1 grid-cols-3-md gap-4">
            <div class="card" style="padding: var(--space-4); background-color: var(--surface-2);">
              <div class="text-xs text-muted font-bold uppercase mb-2 flex items-center gap-2">
                ${ui.icon('activity', 'icon icon--sm text-primary')}
                <span>1. Orientación de Triaje</span>
              </div>
              <div class="font-semibold text-sm mb-1">Fiebre persistente e insuficiencia leve</div>
              <div class="text-xs text-muted mb-2">Duración: 24 horas · Intensidad: 7/10</div>
              <div class="text-xs font-medium text-warning">Ruta: Atención Prioritaria Inmediata</div>
            </div>

            <div class="card" style="padding: var(--space-4); background-color: var(--surface-2);">
              <div class="text-xs text-muted font-bold uppercase mb-2 flex items-center gap-2">
                ${ui.icon('calendar', 'icon icon--sm text-primary')}
                <span>2. Asignación de Turno</span>
              </div>
              <div class="font-semibold text-sm mb-1">Dr. Carlos Alberto Mendoza</div>
              <div class="text-xs text-muted mb-2">Medicina General · Sede Centro</div>
              <div class="text-xs font-semibold text-success">Mañana a las 08:30 AM (Presencial)</div>
            </div>

            <div class="card" style="padding: var(--space-4); background-color: var(--surface-2);">
              <div class="text-xs text-muted font-bold uppercase mb-2 flex items-center gap-2">
                ${ui.icon('pill', 'icon icon--sm text-primary')}
                <span>3. Receta Digital Sellada</span>
              </div>
              <div class="font-semibold text-sm mb-1">Amoxicilina 500mg cápsulas</div>
              <div class="text-xs text-muted mb-2">1 cápsula cada 8h por 7 días (#21)</div>
              <div class="text-xs font-medium text-primary">Snapshot farmacológico inmutable</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 6. Metrics Strip (Bento Counters) -->
    <div class="metrics-container">
      <div class="metric-card">
        <div class="metric-value">42</div>
        <div class="metric-label">Reglas Clínicas</div>
        <div class="metric-sub">Motor determinista según intensidad y duración</div>
      </div>

      <div class="metric-card">
        <div class="metric-value">5</div>
        <div class="metric-label">Niveles de Triaje</div>
        <div class="metric-sub">Escala I a V con semaforización reglamentaria</div>
      </div>

      <div class="metric-card">
        <div class="metric-value">100%</div>
        <div class="metric-label">Inmutabilidad</div>
        <div class="metric-sub">Historias clínicas protegidas con enmiendas cronológicas</div>
      </div>

      <div class="metric-card">
        <div class="metric-value">0</div>
        <div class="metric-label">Colisiones</div>
        <div class="metric-sub">Reserva atómica de slots y prevención de doble turno</div>
      </div>
    </div>

    <!-- 7. Bento Grid de Capacidades Asistenciales -->
    <section class="mb-16">
      <div class="text-center mb-10">
        <span class="badge badge--scheduled mb-2">Arquitectura y Capacidades</span>
        <h2 class="text-3xl font-bold mb-3">Diseñado para la seguridad y calidad asistencial</h2>
        <p class="text-muted" style="max-width: 60ch; margin: 0 auto;">
          Cada componente sigue principios de Clean Architecture, segregación de responsabilidades y trazabilidad médica estricta.
        </p>
      </div>

      <div class="bento-grid">
        <!-- Tarjeta 1 -->
        <div class="bento-card">
          <div>
            <div class="bento-icon-wrapper mb-4">
              ${ui.icon('activity', 'icon icon--md')}
            </div>
            <h3 class="text-lg font-bold mb-2">Triaje Clínico con Corte de Emergencia</h3>
            <p class="text-sm text-muted">
              Motor puro y determinista. Ante síntomas de alarma vital (dolor torácico opresivo, dificultad severa, pérdida de conciencia), activa de inmediato el corte de emergencia orientando al 123 o urgencias.
            </p>
          </div>
          <div>
            <span class="badge" style="background: var(--teal-50); color: var(--teal-800); border: 1px solid var(--teal-200);">
              Motor Puro Determinista
            </span>
          </div>
        </div>

        <!-- Tarjeta 2 -->
        <div class="bento-card">
          <div>
            <div class="bento-icon-wrapper mb-4">
              ${ui.icon('calendar', 'icon icon--md')}
            </div>
            <h3 class="text-lg font-bold mb-2">Agendamiento Asistencial sin Fricciones</h3>
            <p class="text-sm text-muted">
              Consulta en tiempo real por especialidad, sede física y modalidad (presencial o telemedicina). Bloqueo atómico de turnos con política estricta de cancelación con al menos 2 horas de anticipación.
            </p>
          </div>
          <div>
            <span class="badge badge--confirmed">
              Slots en Tiempo Real
            </span>
          </div>
        </div>

        <!-- Tarjeta 3 -->
        <div class="bento-card">
          <div>
            <div class="bento-icon-wrapper mb-4">
              ${ui.icon('file-text', 'icon icon--md')}
            </div>
            <h3 class="text-lg font-bold mb-2">Historia Clínica Inmutable</h3>
            <p class="text-sm text-muted">
              Cumplimiento normativo estricto (ADR-007): una atención médica cerrada nunca se modifica ni se borra. Cualquier ajuste requiere una enmienda cronológica firmada y justificada.
            </p>
          </div>
          <div>
            <span class="badge badge--scheduled">
              Enmiendas Cronológicas
            </span>
          </div>
        </div>

        <!-- Tarjeta 4 -->
        <div class="bento-card">
          <div>
            <div class="bento-icon-wrapper mb-4">
              ${ui.icon('pill', 'icon icon--md')}
            </div>
            <h3 class="text-lg font-bold mb-2">Recetas con Snapshot Farmacológico</h3>
            <p class="text-sm text-muted">
              Prescripción digital vinculada a la atención con congelamiento histórico del medicamento (nombre, principio activo, presentación), evitando discrepancias futuras en farmacia.
            </p>
          </div>
          <div>
            <span class="badge badge--confirmed">
              Dispensación Trazable
            </span>
          </div>
        </div>

        <!-- Tarjeta 5 -->
        <div class="bento-card">
          <div>
            <div class="bento-icon-wrapper mb-4">
              ${ui.icon('shield', 'icon icon--md')}
            </div>
            <h3 class="text-lg font-bold mb-2">Ficha de Emergencia con Código QR</h3>
            <p class="text-sm text-muted">
              Carné de emergencia accesible mediante token temporal para socorristas o paramédicos en situaciones críticas, informando grupo sanguíneo, alergias y contactos de urgencia.
            </p>
          </div>
          <div>
            <span class="badge badge--scheduled">
              Acceso Rápido Vital
            </span>
          </div>
        </div>

        <!-- Tarjeta 6 -->
        <div class="bento-card">
          <div>
            <div class="bento-icon-wrapper mb-4">
              ${ui.icon('lock', 'icon icon--md')}
            </div>
            <h3 class="text-lg font-bold mb-2">Seguridad &amp; Protocolo Break-Glass</h3>
            <p class="text-sm text-muted">
              Aislamiento de roles: el personal administrativo nunca accede a datos clínicos. En emergencias no asignadas, los médicos pueden activar el protocolo Break-Glass bajo auditoría forense inmutable.
            </p>
          </div>
          <div>
            <span class="badge" style="background: var(--slate-100); color: var(--slate-700); border: 1px solid var(--slate-300);">
              Auditoría Forense
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- 8. Simulador Interactivo de Triaje en Vivo -->
    <section id="demo-simulador" class="mb-16 scroll-mt-8">
      <div class="text-center mb-8">
        <span class="badge badge--scheduled mb-2">Demostrador en Tiempo Real</span>
        <h2 class="text-3xl font-bold mb-3">Simula el motor de triaje sin registro previo</h2>
        <p class="text-muted" style="max-width: 60ch; margin: 0 auto;">
          Selecciona un motivo de consulta y ajusta la intensidad de tus síntomas para comprobar cómo clasifica el algoritmo.
        </p>
      </div>

      <div class="simulator-box">
        <div class="grid grid-cols-1 grid-cols-2-md gap-6 mb-6">
          <div>
            <label for="simSintoma" class="form-label text-sm font-semibold mb-2 block">
              Selecciona síntoma principal:
            </label>
            <select id="simSintoma" class="form-select w-full">
              <option value="alarma-toracico">🚨 Dolor torácico opresivo de alarma (Vital)</option>
              <option value="alarma-respiratoria">🚨 Dificultad respiratoria severa (Vital)</option>
              <option value="fiebre" selected>Fiebre alta persistente (&gt; 38.5°C)</option>
              <option value="cefalea">Dolor de cabeza intenso o cefalea</option>
              <option value="abdominal">Dolor abdominal cólico</option>
              <option value="garganta">Molestia o dolor de garganta</option>
              <option value="rutina">Chequeo médico preventivo o rutina</option>
            </select>
          </div>

          <div>
            <label for="simDuracion" class="form-label text-sm font-semibold mb-2 block">
              Duración aproximada del síntoma:
            </label>
            <select id="simDuracion" class="form-select w-full">
              <option value="1">Menos de 2 horas</option>
              <option value="6">Entre 2 y 6 horas</option>
              <option value="24" selected>24 horas (1 día)</option>
              <option value="48">48 horas (2 días)</option>
              <option value="72">Más de 72 horas (3+ días)</option>
            </select>
          </div>
        </div>

        <div class="mb-6">
          <div class="flex justify-between items-center mb-2">
            <label for="simIntensidad" class="form-label text-sm font-semibold">
              Nivel de Intensidad del Síntoma:
            </label>
            <span id="simIntensidadValor" class="badge badge--scheduled font-bold" style="font-size: var(--text-sm);">
              7 / 10
            </span>
          </div>
          <input type="range" id="simIntensidad" class="w-full" min="0" max="10" value="7" style="accent-color: var(--primary); cursor: pointer;">
          <div class="flex justify-between text-xs text-muted mt-1">
            <span>0 (Leve / Nulo)</span>
            <span>5 (Moderado)</span>
            <span>10 (Severo / Extremo)</span>
          </div>
        </div>

        <!-- Tarjeta de Resultado Dinámico del Simulador -->
        <div id="simResultadoBox" class="card" style="border: 2px solid var(--teal-300); background-color: var(--teal-50); padding: var(--space-5);">
          <div class="flex flex-wrap items-center justify-between gap-3 mb-3">
            <span class="text-xs uppercase font-bold tracking-wider" style="color: var(--teal-800);">Clasificación del Motor:</span>
            <span id="simNivelBadge" class="badge" style="background: var(--triage-3-bg); color: var(--triage-3-fg); border: 1px solid var(--triage-3-bd); font-size: var(--text-sm); font-weight: var(--weight-bold);">
              Nivel III · Urgente (Amarillo)
            </span>
          </div>

          <div id="simRutaText" class="text-lg font-bold text-slate-900 mb-2">
            Ruta Sugerida: Consulta Médica Presencial
          </div>

          <p id="simMensajeText" class="text-sm text-muted mb-4">
            Tus síntomas requieren evaluación médica presencial en las próximas 4 a 6 horas para descartar complicaciones.
          </p>

          <div class="flex flex-wrap items-center justify-between gap-3 pt-3" style="border-top: 1px solid var(--teal-200);">
            <span class="text-xs text-muted flex items-center gap-1">
              ${ui.icon('info', 'icon icon--xs')} Demostración puramente orientativa.
            </span>
            <a href="#/register" class="btn btn-primary btn--sm">
              <span>Agendar cita con esta prioridad</span>
              ${ui.icon('arrow-right', 'icon icon--xs')}
            </a>
          </div>
        </div>
      </div>
    </section>

    <!-- 9. Aviso Médico y Responsabilidad Legal (ADR-009) -->
    <div class="card mb-16" style="border-left: 4px solid var(--warning); background-color: var(--warning-bg); color: var(--on-warning-bg); padding: var(--space-5);">
      <div class="flex items-start gap-4">
        <div style="flex-shrink: 0; margin-top: 2px;">
          ${ui.icon('alert-triangle', 'icon icon--md text-warning')}
        </div>
        <div>
          <h4 class="font-bold text-md mb-1" style="color: var(--on-warning-bg);">Aviso de Prototipo y Responsabilidad Médica (ADR-009)</h4>
          <p class="text-sm" style="line-height: 1.5; margin: 0;">
            MediTriaje 2.0 es una plataforma tecnológica asistencial académica. Sus algoritmos de triaje automatizado orientan el flujo de agendamiento y priorización operativa, pero <strong>no reemplazan bajo ninguna circunstancia el criterio clínico de un profesional de la salud matriculado</strong>. Ante dolor opresivo en el pecho, dificultad respiratoria súbita o pérdida del conocimiento, <strong>comunícate de inmediato a la Línea de Emergencias 123</strong> o acude al servicio de urgencias más cercano.
          </p>
        </div>
      </div>
    </div>

    <!-- 10. Preguntas Frecuentes (Radix UI Accordion Style) -->
    <section class="mb-16">
      <div class="text-center mb-8">
        <span class="badge badge--scheduled mb-2">Preguntas Frecuentes</span>
        <h2 class="text-3xl font-bold mb-3">Todo lo que necesitas saber</h2>
        <p class="text-muted" style="max-width: 60ch; margin: 0 auto;">
          Respuestas claras sobre el funcionamiento, privacidad y agendamiento en la plataforma.
        </p>
      </div>

      <div class="faq-accordion">
        <details class="faq-item" open>
          <summary class="faq-summary">
            <span>¿Cómo determina el sistema mi nivel de prioridad en el triaje?</span>
            ${ui.icon('chevron-down', 'icon icon--sm')}
          </summary>
          <div class="faq-content">
            El motor de triaje utiliza un algoritmo determinista puro basado en la duración del síntoma, su escala de intensidad (0 a 10) y la presencia de factores de riesgo. Cruza tus datos con una matriz de 42 reglas prototípicas alineadas a los 5 niveles estándar del sistema de salud colombiano (I: Resucitación/Emergencia a V: No urgente).
          </div>
        </details>

        <details class="faq-item">
          <summary class="faq-summary">
            <span>¿Qué ocurre si selecciono un síntoma de alarma como dolor en el pecho?</span>
            ${ui.icon('chevron-down', 'icon icon--sm')}
          </summary>
          <div class="faq-content">
            El sistema activa de inmediato la compuerta de <strong>Corte de Emergencia</strong>. Se clasifica de forma automática como <strong>Nivel I (Emergencia Vital)</strong>, se bloquea la reserva de citas programadas y se muestra en pantalla completa las instrucciones para contactar a la Línea 123 o acudir inmediatamente al centro asistencial más cercano.
          </div>
        </details>

        <details class="faq-item">
          <summary class="faq-summary">
            <span>¿Puedo cancelar o reprogramar una cita médica ya agendada?</span>
            ${ui.icon('chevron-down', 'icon icon--sm')}
          </summary>
          <div class="faq-content">
            Sí. Siguiendo la política de optimización asistencial de la plataforma, los pacientes pueden cancelar o modificar sus citas sin penalidad hasta con <strong>2 horas de anticipación</strong> a la hora de inicio del turno. Esto permite que el horario quede disponible inmediatamente para otro paciente que lo necesite.
          </div>
        </details>

        <details class="faq-item">
          <summary class="faq-summary">
            <span>¿Por qué las historias clínicas y atenciones no se pueden borrar?</span>
            ${ui.icon('chevron-down', 'icon icon--sm')}
          </summary>
          <div class="faq-content">
            Por mandato ético y normativo (ADR-007). Los registros médicos firmados son inmutables para garantizar la autenticidad jurídica. En caso de requerir una corrección diagnóstica o farmacológica, el médico tratante emite una <strong>enmienda cronológica justificada</strong> que queda indexada sin alterar el registro original.
          </div>
        </details>

        <details class="faq-item">
          <summary class="faq-summary">
            <span>¿Cómo funciona la Ficha Médica de Emergencia por código QR?</span>
            ${ui.icon('chevron-down', 'icon icon--sm')}
          </summary>
          <div class="faq-content">
            Desde tu portal de paciente puedes generar un código QR con token temporal cifrado. Al ser escaneado por personal de primeros auxilios o brigadistas, muestra únicamente datos vitales: tipo sanguíneo, alergias a medicamentos, condiciones preexistentes y contacto de emergencia, sin exponer historiales sensibles.
          </div>
        </details>
      </div>
    </section>

    <!-- 11. Footer Completo y Profesional -->
    <footer class="landing-footer">
      <div class="footer-grid">
        <div class="footer-col">
          <div class="flex items-center gap-2 mb-3">
            <svg class="icon icon-hospital text-primary" viewBox="0 0 24 24" style="width: 24px; height: 24px;" aria-hidden="true">
              <path d="M12 6v4"/><path d="M14 14h-4"/><path d="M14 18h-4"/><path d="M14 8h-4"/><path d="M18 12h-4"/><path d="M6 12h4"/><rect width="16" height="20" x="4" y="2" rx="2"/><path d="M10 22v-4h4v4"/>
            </svg>
            <span class="font-bold text-lg text-slate-900" style="color: var(--text);">MediTriaje 2.0</span>
          </div>
          <p class="text-sm text-muted mb-4" style="max-width: 32ch;">
            Plataforma asistencial web para la orientación de triaje, agendamiento de citas, historia clínica inmutable y recetas digitales en Colombia.
          </p>
          <div class="text-xs text-muted">
            Desplegado en Render (Backend Spring Boot 3) + Vercel (Frontend ES Modules) + Oracle Cloud ATP.
          </div>
        </div>

        <div class="footer-col">
          <h4>Plataforma</h4>
          <ul>
            <li><button type="button" id="btnScrollSim3" style="background: none; border: none; padding: 0; color: var(--text-muted); cursor: pointer; font: inherit;">Simulador de Triaje</button></li>
            <li><a href="#/availability">Consulta de Disponibilidad</a></li>
            <li><a href="#/register">Registro de Pacientes</a></li>
            <li><a href="#/login">Ingreso a la Plataforma</a></li>
          </ul>
        </div>

        <div class="footer-col">
          <h4>Normativa &amp; Salud</h4>
          <ul>
            <li><a href="#/emergency-summary/token-demo">Ficha de Emergencia QR</a></li>
            <li><a href="javascript:void(0)" onclick="alert('Sistema alineado con Resolución 5596 de Triaje en Colombia.')">Estándar de Triaje (I-V)</a></li>
            <li><a href="javascript:void(0)" onclick="alert('Inmutabilidad garantizada por arquitectura limpia y registros append-only (ADR-007).')">Inmutabilidad Clínica</a></li>
            <li><a href="javascript:void(0)" onclick="alert('Protocolo de auditoría forense para aperturas de urgencia no programadas (ADR-018).')">Protocolo Break-Glass</a></li>
          </ul>
        </div>

        <div class="footer-col">
          <h4>Proyecto Académico</h4>
          <ul>
            <li><a href="https://github.com/Sebasr0311/MediTraje" target="_blank" rel="noopener noreferrer">Repositorio GitHub</a></li>
            <li><a href="javascript:void(0)" onclick="alert('Universidad Popular del Cesar · Ingeniería de Sistemas')">UPC Valledupar</a></li>
            <li><a href="javascript:void(0)" onclick="alert('Arquitectura Limpia · Java 21 · Spring Boot 3 · Oracle Autonomous DB · Vanilla ES Modules')">Stack Tecnológico</a></li>
            <li><a href="#/login">Portal Administrativo</a></li>
          </ul>
        </div>
      </div>

      <div class="flex flex-wrap justify-between items-center gap-4 pt-6" style="border-top: 1px solid var(--border);">
        <div class="text-xs text-muted">
          © 2026 MediTriaje 2.0 · Plataforma de Demostración Asistencial y Académica.
        </div>
        <div class="flex items-center gap-4 text-xs text-muted">
          <span>Diseño UI/UX con Tokens CSS</span>
          <span>·</span>
          <span>WCAG 2.1 AA Accesible</span>
        </div>
      </div>
    </footer>
  `;

  // 12. Lógica Interactiva del Simulador de Triaje
  initSimulator(container);
}

function initSimulator(container) {
  const selectSintoma = container.querySelector('#simSintoma');
  const selectDuracion = container.querySelector('#simDuracion');
  const sliderIntensidad = container.querySelector('#simIntensidad');
  const labelIntensidad = container.querySelector('#simIntensidadValor');
  const badgeNivel = container.querySelector('#simNivelBadge');
  const textRuta = container.querySelector('#simRutaText');
  const textMensaje = container.querySelector('#simMensajeText');
  const resultadoBox = container.querySelector('#simResultadoBox');

  if (!selectSintoma || !sliderIntensidad) return;

  const updateSimulation = () => {
    const sintoma = selectSintoma.value;
    const intensidad = parseInt(sliderIntensidad.value, 10);
    const duracion = parseInt(selectDuracion.value, 10);

    labelIntensidad.textContent = `${intensidad} / 10`;

    // 1. Corte de Emergencia (Síntomas de Alarma Vital)
    if (sintoma.startsWith('alarma')) {
      badgeNivel.style.background = 'var(--triage-1-bg)';
      badgeNivel.style.color = 'var(--triage-1-fg)';
      badgeNivel.style.borderColor = 'var(--triage-1-bd)';
      badgeNivel.textContent = 'Nivel I · Emergencia Vital (Rojo)';

      textRuta.textContent = 'Ruta: Urgencias Médicas Inmediatas (Línea 123)';
      textRuta.style.color = 'var(--danger)';

      textMensaje.textContent = '⚠️ ¡CORTE DE EMERGENCIA ACTIVADO! Presentas un síntoma de alarma médica inminente. Llama al 123 o acude de inmediato al centro de urgencias más cercano.';

      resultadoBox.style.borderColor = 'var(--danger)';
      resultadoBox.style.backgroundColor = 'var(--danger-bg)';
      return;
    }

    // 2. Cálculo determinista según intensidad y duración
    if (intensidad >= 8) {
      badgeNivel.style.background = 'var(--triage-2-bg)';
      badgeNivel.style.color = 'var(--triage-2-fg)';
      badgeNivel.style.borderColor = 'var(--triage-2-bd)';
      badgeNivel.textContent = 'Nivel II · Muy Urgente (Naranja)';

      textRuta.textContent = 'Ruta Sugerida: Atención Prioritaria Inmediata';
      textRuta.style.color = 'var(--warning)';

      textMensaje.textContent = 'Tus síntomas tienen intensidad severa. Requieren valoración clínica prioritaria presencial en las próximas 2 horas.';

      resultadoBox.style.borderColor = 'var(--warning)';
      resultadoBox.style.backgroundColor = 'var(--warning-bg)';
    } else if (intensidad >= 5 || (sintoma === 'fiebre' && duracion >= 48)) {
      badgeNivel.style.background = 'var(--triage-3-bg)';
      badgeNivel.style.color = 'var(--triage-3-fg)';
      badgeNivel.style.borderColor = 'var(--triage-3-bd)';
      badgeNivel.textContent = 'Nivel III · Urgente (Amarillo)';

      textRuta.textContent = 'Ruta Sugerida: Consulta Médica Presencial';
      textRuta.style.color = 'var(--text)';

      textMensaje.textContent = 'Síntomas moderados que justifican consulta médica en el transcurso del día para definir plan terapéutico.';

      resultadoBox.style.borderColor = 'var(--teal-300)';
      resultadoBox.style.backgroundColor = 'var(--teal-50)';
    } else if (intensidad >= 3) {
      badgeNivel.style.background = 'var(--triage-4-bg)';
      badgeNivel.style.color = 'var(--triage-4-fg)';
      badgeNivel.style.borderColor = 'var(--triage-4-bd)';
      badgeNivel.textContent = 'Nivel IV · Menos Urgente (Verde)';

      textRuta.textContent = 'Ruta Sugerida: Telemedicina o Consulta Externa';
      textRuta.style.color = 'var(--text)';

      textMensaje.textContent = 'Síntomas leves o estables. Puedes agendar una videoconsulta de telemedicina o consulta médica presencial programada.';

      resultadoBox.style.borderColor = 'var(--success)';
      resultadoBox.style.backgroundColor = 'var(--success-bg)';
    } else {
      badgeNivel.style.background = 'var(--triage-5-bg)';
      badgeNivel.style.color = 'var(--triage-5-fg)';
      badgeNivel.style.borderColor = 'var(--triage-5-bd)';
      badgeNivel.textContent = 'Nivel V · No Urgente (Azul)';

      textRuta.textContent = 'Ruta Sugerida: Consulta Médica Programada';
      textRuta.style.color = 'var(--text)';

      textMensaje.textContent = 'Condición sin riesgo agudo identificable. Ideal para chequeos preventivos, lectura de exámenes o citas de control.';

      resultadoBox.style.borderColor = 'var(--info)';
      resultadoBox.style.backgroundColor = 'var(--info-bg)';
    }
  };

  const scrollSim = () => {
    container.querySelector('#demo-simulador')?.scrollIntoView({ behavior: 'smooth' });
  };
  container.querySelector('#btnScrollSim1')?.addEventListener('click', scrollSim);
  container.querySelector('#btnScrollSim2')?.addEventListener('click', scrollSim);
  container.querySelector('#btnScrollSim3')?.addEventListener('click', scrollSim);

  selectSintoma.addEventListener('change', updateSimulation);
  selectDuracion.addEventListener('change', updateSimulation);
  sliderIntensidad.addEventListener('input', updateSimulation);

  updateSimulation();
}
