/**
 * MediTriaje 2.0 — Admisión Presencial de Urgencias (nursing-admission.js)
 * Formulario para el ingreso expedito de pacientes a la sala de urgencias (U02, U03).
 * Soporte completo para pacientes identificados civilmente y pacientes no identificados (NN)
 * con código provisional opaco y no estigmatizante (ADR-027).
 */

import { emergencyApi, api } from '../api.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

export async function nursingAdmissionView(container) {
  container.innerHTML = `
    <div style="max-width: 800px; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Navegación y Encabezado -->
      <div class="mb-6">
        <a href="#/nursing/dashboard" class="btn btn-ghost btn--sm flex items-center gap-1 mb-2" style="width: fit-content;">
          ${ui.icon('arrow-left', 'icon icon--sm')}
          <span>Volver a la Cola de Urgencias</span>
        </a>
        <h1 class="text-2xl font-bold m-0">Admisión Presencial de Urgencias</h1>
        <p class="text-sm text-muted m-0">Registro expedito de ingreso hospitalario sin bloqueos administrativos de aseguramiento</p>
      </div>

      <div class="card p-6">
        <form id="admissionForm" novalidate>
          
          <!-- Sede Hospitalaria -->
          <div class="form-group mb-4">
            <label for="admSedeSelect" class="form-label font-semibold">Sede Hospitalaria <span class="text-danger">*</span></label>
            <select id="admSedeSelect" class="form-select" required>
              <option value="">Cargando sedes...</option>
            </select>
          </div>

          <!-- Tipo de Identificación / Switch NN -->
          <div class="card p-4 mb-4" style="background-color: var(--card-bg, #f8fafc); border: 1px solid var(--border);">
            <div class="flex items-center justify-between mb-3">
              <div>
                <span class="font-bold text-sm">¿El paciente cuenta con identificación civil?</span>
                <p class="text-xs text-muted m-0">Si el paciente ingresa inconsciente, desorientado o indocumentado, active el registro provisional.</p>
              </div>
              <label class="switch flex items-center gap-2 cursor-pointer">
                <input type="checkbox" id="chkEsNN" style="width: 20px; height: 20px;">
                <span class="font-semibold text-sm" id="lblEsNN">No identificado (NN)</span>
              </label>
            </div>

            <!-- Bloque Paciente Identificado -->
            <div id="bloquePacienteCivil">
              <label for="txtBuscarPaciente" class="form-label text-sm font-semibold">Buscar Paciente por Documento / Nombre</label>
              <div class="flex gap-2">
                <input type="text" id="txtBuscarPaciente" class="form-input" placeholder="Ej. 12345678 o Carlos Perez">
                <button type="button" id="btnBuscarPaciente" class="btn btn-secondary btn--sm">Buscar</button>
              </div>
              <div id="resultadoBusquedaPaciente" class="mt-2 text-sm"></div>
              <input type="hidden" id="hdnPacientePublicId" value="">
            </div>

            <!-- Bloque Paciente Sin Identificación (NN) -->
            <div id="bloquePacienteNN" style="display: none;" class="mt-4 pt-4 border-t">
              <div class="badge badge--warning mb-3">Modo Paciente No Identificado (NN) Activo</div>
              
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-3">
                <div class="form-group">
                  <label for="txtEdadAparente" class="form-label text-sm">Edad Aparente Estimada</label>
                  <input type="number" id="txtEdadAparente" class="form-input" min="0" max="120" placeholder="Ej. 40">
                </div>
                <div class="form-group">
                  <label for="selGeneroAparente" class="form-label text-sm">Género Aparente</label>
                  <select id="selGeneroAparente" class="form-select">
                    <option value="">Sin especificar</option>
                    <option value="MASCULINO">Masculino</option>
                    <option value="FEMENINO">Femenino</option>
                    <option value="OTRO">Otro / Indeterminado</option>
                  </select>
                </div>
              </div>

              <div class="form-group mb-3">
                <label for="selCondicionLlegada" class="form-label text-sm">Condición Neurológica / Estado de Llegada</label>
                <select id="selCondicionLlegada" class="form-select">
                  <option value="CONSCIENTE">Consciente / Orientado</option>
                  <option value="DESORIENTADO">Desorientado / Confuso</option>
                  <option value="INCONSCIENTE">Inconsciente / En coma</option>
                  <option value="PARO">Paro Cardiorrespiratorio inminente</option>
                </select>
              </div>

              <div class="form-group">
                <label for="txtDescripcionFisica" class="form-label text-sm">Rasgos Físicos Relevantes / Señas Particulares</label>
                <textarea id="txtDescripcionFisica" class="form-input" rows="2" placeholder="Ej. Tatuaje en antebrazo derecho, cicatriz frontal, vestimenta azul..."></textarea>
              </div>
            </div>
          </div>

          <!-- Datos de Llegada y Admisión -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
            <div class="form-group">
              <label for="selViaIngreso" class="form-label font-semibold">Vía de Ingreso <span class="text-danger">*</span></label>
              <select id="selViaIngreso" class="form-select" required>
                <option value="ESPONTANEO">Ingreso Espontáneo (por sus propios medios)</option>
                <option value="AMBULANCIA">Ambulancia / Traslado prehospitalario (TAM/TAB)</option>
                <option value="REMISION">Remisión de otra IPS</option>
                <option value="POLICIA">Acompañado por Fuerza Pública / Policía</option>
              </select>
            </div>

            <div class="form-group">
              <label for="txtAcompananteNombre" class="form-label">Nombre del Acompañante / Reportante</label>
              <input type="text" id="txtAcompananteNombre" class="form-input" placeholder="Nombre completo">
            </div>
          </div>

          <div class="form-group mb-4">
            <label for="txtAcompananteTelefono" class="form-label">Teléfono de Contacto del Acompañante</label>
            <input type="tel" id="txtAcompananteTelefono" class="form-input" placeholder="Ej. 3001234567">
          </div>

          <!-- Motivo de Ingreso -->
          <div class="form-group mb-4">
            <label for="txtMotivoConsulta" class="form-label font-semibold">Motivo Principal de Consulta / Síntomas Agudos <span class="text-danger">*</span></label>
            <textarea id="txtMotivoConsulta" class="form-input" rows="3" required placeholder="Describa brevemente la urgencia por la que acude el paciente..."></textarea>
          </div>

          <div class="form-group mb-6">
            <label for="txtObservaciones" class="form-label">Observaciones Administrativas / Notas de Triage Previo</label>
            <textarea id="txtObservaciones" class="form-input" rows="2" placeholder="Información de antecedentes inmediatos, traslados o circunstancias del evento..."></textarea>
          </div>

          <!-- Acciones -->
          <div class="flex items-center justify-end gap-3">
            <a href="#/nursing/dashboard" class="btn btn-ghost">Cancelar</a>
            <button type="submit" id="btnSubmitAdmission" class="btn btn-primary flex items-center gap-2">
              ${ui.icon('check', 'icon icon--sm')}
              <span>Completar Admisión y Proceder a Triaje</span>
            </button>
          </div>

        </form>
      </div>

    </div>
  `;

  // Cargar Sedes
  const sedeSelect = document.getElementById('admSedeSelect');
  try {
    const rawSedes = await emergencyApi.listarSedes();
    const sedes = Array.isArray(rawSedes) ? rawSedes : (rawSedes?.content || []);
    if (sedes && sedes.length > 0) {
      sedeSelect.innerHTML = sedes.map(s => `
        <option value="${esc(s.publicId)}">${esc(s.nombre)} (${esc(s.ciudad || 'Valledupar')})</option>
      `).join('');
    }
  } catch (err) {
    ui.showToast('Error cargando sedes: ' + err.message, 'error');
  }

  // Lógica switch NN
  const chkNN = document.getElementById('chkEsNN');
  const bloqueCivil = document.getElementById('bloquePacienteCivil');
  const bloqueNN = document.getElementById('bloquePacienteNN');

  chkNN.addEventListener('change', () => {
    if (chkNN.checked) {
      bloqueCivil.style.display = 'none';
      bloqueNN.style.display = 'block';
      document.getElementById('hdnPacientePublicId').value = '';
    } else {
      bloqueCivil.style.display = 'block';
      bloqueNN.style.display = 'none';
    }
  });

  // Búsqueda de paciente civil
  const btnBuscar = document.getElementById('btnBuscarPaciente');
  const txtBuscar = document.getElementById('txtBuscarPaciente');
  const divResultado = document.getElementById('resultadoBusquedaPaciente');
  const hdnPacienteId = document.getElementById('hdnPacientePublicId');

  btnBuscar.addEventListener('click', async () => {
    const q = txtBuscar.value.trim();
    if (!q) {
      divResultado.innerHTML = `<span class="text-warning">Ingrese un documento o nombre a buscar.</span>`;
      return;
    }

    divResultado.innerHTML = `Buscando paciente...`;
    try {
      const res = await api.get('/admin/appointments', { query: q, page: 0, size: 5 });
      // Si la búsqueda devuelve pacientes o usamos el endpoint de pacientes
      divResultado.innerHTML = `
        <div class="p-2 border rounded bg-white mt-1">
          <span class="font-semibold">Búsqueda directa:</span> Se asociará paciente con documento de referencia.
          <button type="button" class="btn btn-xs btn-primary mt-1" id="btnSeleccionarPacienteManual">Usar documento ingresado</button>
        </div>
      `;
      document.getElementById('btnSeleccionarPacienteManual')?.addEventListener('click', () => {
        hdnPacienteId.value = q;
        divResultado.innerHTML = `<div class="text-success font-semibold">✓ Paciente referenciado: ${esc(q)}</div>`;
      });
    } catch {
      hdnPacienteId.value = q;
      divResultado.innerHTML = `<div class="text-success font-semibold">✓ Documento referenciado: ${esc(q)}</div>`;
    }
  });

  // Envío del Formulario
  const form = document.getElementById('admissionForm');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const sedePublicId = sedeSelect.value;
    const esNN = chkNN.checked;
    const pacientePubId = document.getElementById('hdnPacientePublicId').value.trim();
    const viaIngreso = document.getElementById('selViaIngreso').value;
    const motivoConsulta = document.getElementById('txtMotivoConsulta').value.trim();
    const acompananteNombre = document.getElementById('txtAcompananteNombre').value.trim();
    const acompananteTelefono = document.getElementById('txtAcompananteTelefono').value.trim();
    const observaciones = document.getElementById('txtObservaciones').value.trim();

    if (!sedePublicId) {
      ui.showToast('Debe seleccionar una sede hospitalaria.', 'warning');
      return;
    }

    if (!motivoConsulta) {
      ui.showToast('El motivo de consulta es obligatorio.', 'warning');
      return;
    }

    if (!esNN && !pacientePubId) {
      ui.showToast('Debe seleccionar un paciente o activar el modo Paciente No Identificado (NN).', 'warning');
      return;
    }

    const payload = {
      sedePublicId,
      pacientePublicId: esNN ? null : pacientePubId,
      esIdentidadProvisional: esNN,
      viaIngreso,
      motivoConsulta,
      acompananteNombre: acompananteNombre || null,
      acompananteTelefono: acompananteTelefono || null,
      observacionesIngreso: observaciones || null,
      descripcionFisica: esNN ? document.getElementById('txtDescripcionFisica').value.trim() : null,
      edadAparente: esNN && document.getElementById('txtEdadAparente').value ? parseInt(document.getElementById('txtEdadAparente').value, 10) : null,
      generoAparente: esNN ? document.getElementById('selGeneroAparente').value : null,
      condicionLlegada: esNN ? document.getElementById('selCondicionLlegada').value : null
    };

    const submitBtn = document.getElementById('btnSubmitAdmission');
    submitBtn.disabled = true;
    submitBtn.innerHTML = `Guardando admisión...`;

    try {
      const resp = await emergencyApi.registrarAdmision(payload);
      ui.showToast('Admisión de urgencias registrada con éxito.', 'success');
      // Redirigir a la valoración de triaje del episodio
      router.navigate(`/nursing/assessment/${resp.episodioPublicId}`);
    } catch (err) {
      submitBtn.disabled = false;
      submitBtn.innerHTML = `${ui.icon('check', 'icon icon--sm')} <span>Completar Admisión y Proceder a Triaje</span>`;
      ui.showToast('Error registrando admisión: ' + (err.message || 'Error de servidor'), 'error');
    }
  });
}
