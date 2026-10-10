/**
 * MediTriaje 2.0 — Reconciliación de Identidad Provisional (nursing-identity.js)
 * Proceso seguro y auditado para vincular un paciente admitido como no identificado (NN)
 * con su registro civil verificado una vez establecida su identidad (U03, ADR-027).
 */

import { emergencyApi, api } from '../api.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

export async function nursingIdentityView(container, { params }) {
  const episodePublicId = params?.id || params?.episodioId;

  if (!episodePublicId) {
    ui.renderEmpty(container, {
      title: 'Episodio no especificado',
      description: 'No se suministró un identificador de episodio de urgencias.',
      actionText: 'Volver a la cola',
      actionUrl: '#/nursing/dashboard'
    });
    return;
  }

  container.innerHTML = `
    <div style="max-width: 750px; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Navegación -->
      <div class="mb-4">
        <a href="#/nursing/dashboard" class="btn btn-ghost btn--sm flex items-center gap-1" style="width: fit-content;">
          ${ui.icon('arrow-left', 'icon icon--sm')}
          <span>Volver a la Cola de Urgencias</span>
        </a>
      </div>

      <!-- Encabezado -->
      <div class="mb-6">
        <div class="badge badge--warning mb-2 font-bold">Procedimiento Asistencial Seguro</div>
        <h1 class="text-2xl font-bold m-0">Reconciliación de Identidad Provisional (NN)</h1>
        <p class="text-sm text-muted m-0">Vinculación auditada de un episodio de atención de urgencias con el paciente civil correspondiente</p>
      </div>

      <!-- Resumen de Identidad Provisional -->
      <div class="card p-6 mb-6" id="summaryProvisionalCard">
        <p class="text-sm text-muted">Cargando datos provisionales del episodio...</p>
      </div>

      <!-- Formulario de Reconciliación -->
      <div class="card p-6">
        <h2 class="text-lg font-bold mb-4">Vincular con Paciente Registrado</h2>

        <form id="reconcileForm" novalidate>
          
          <div class="form-group mb-4">
            <label for="txtDocPaciente" class="form-label font-semibold">Identificación Civil del Paciente (Cédula / TI / CE / Pasaporte) <span class="text-danger">*</span></label>
            <div class="flex gap-2">
              <input type="text" id="txtDocPaciente" class="form-input" placeholder="Número de documento..." required>
              <button type="button" id="btnBuscarPacienteCivil" class="btn btn-secondary btn--sm">Buscar</button>
            </div>
            <div id="resultadoBusquedaCivil" class="mt-2 text-sm"></div>
            <input type="hidden" id="hdnPacienteCivilPublicId" value="">
          </div>

          <div class="form-group mb-6">
            <label for="txtMotivoReconciliacion" class="form-label font-semibold">Motivo y Evidencia de Reconciliación <span class="text-danger">*</span></label>
            <textarea id="txtMotivoReconciliacion" class="form-input" rows="3" required placeholder="Especifique cómo se verificó la identidad (ej. presentación de documento original por familiar, cotejo dactilar, verificación EPS)..."></textarea>
          </div>

          <div class="card p-4 mb-6 bg-slate-50 border text-xs text-muted">
            <span class="font-bold text-slate-800">Nota de Trazabilidad y Seguridad:</span>
            Esta operación no destruye el historial asistencial previo ni el código provisional, el cual permanecerá vinculado como referencia inmutable en la bitácora de auditoría clínica (Fase U, ADR-027).
          </div>

          <div class="flex items-center justify-end gap-3">
            <a href="#/nursing/dashboard" class="btn btn-ghost">Cancelar</a>
            <button type="submit" id="btnSubmitReconcile" class="btn btn-primary flex items-center gap-2">
              ${ui.icon('link', 'icon icon--sm')}
              <span>Confirmar y Reconciliar Identidad</span>
            </button>
          </div>

        </form>
      </div>

    </div>
  `;

  // Cargar Detalle del Episodio
  try {
    const episodio = await emergencyApi.obtenerDetalleEpisodio(episodePublicId);
    renderSummary(episodio);
  } catch (err) {
    ui.showToast('Error cargando datos del episodio: ' + err.message, 'error');
  }

  // Búsqueda de Paciente Civil
  const btnBuscar = document.getElementById('btnBuscarPacienteCivil');
  const txtDoc = document.getElementById('txtDocPaciente');
  const divRes = document.getElementById('resultadoBusquedaCivil');
  const hdnId = document.getElementById('hdnPacienteCivilPublicId');

  btnBuscar.addEventListener('click', async () => {
    const doc = txtDoc.value.trim();
    if (!doc) {
      divRes.innerHTML = `<span class="text-warning">Ingrese el número de documento a buscar.</span>`;
      return;
    }

    divRes.innerHTML = `Buscando paciente civil...`;
    try {
      divRes.innerHTML = `
        <div class="p-3 border rounded bg-white mt-1">
          <div class="font-bold">Documento ingresado: ${esc(doc)}</div>
          <div class="text-xs text-muted mt-1">El sistema vinculará el episodio a este registro de paciente.</div>
          <button type="button" class="btn btn-xs btn-primary mt-2" id="btnConfirmarSeleccion">Usar este paciente</button>
        </div>
      `;
      document.getElementById('btnConfirmarSeleccion')?.addEventListener('click', () => {
        hdnId.value = doc;
        divRes.innerHTML = `<div class="text-success font-semibold mt-1">✓ Paciente civil seleccionado para vinculación.</div>`;
      });
    } catch (err) {
      divRes.innerHTML = `<span class="text-danger">Error buscando paciente: ${esc(err.message)}</span>`;
    }
  });

  // Envío del Formulario
  const form = document.getElementById('reconcileForm');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const pacienteId = hdnId.value.trim() || txtDoc.value.trim();
    const motivo = document.getElementById('txtMotivoReconciliacion').value.trim();

    if (!pacienteId) {
      ui.showToast('Debe ingresar el identificador del paciente civil verificado.', 'warning');
      return;
    }

    if (!motivo) {
      ui.showToast('Debe suministrar el motivo de reconciliación.', 'warning');
      return;
    }

    const btnSubmit = document.getElementById('btnSubmitReconcile');
    btnSubmit.disabled = true;
    btnSubmit.innerHTML = `Procesando vinculación...`;

    try {
      await emergencyApi.reconciliarIdentidad(episodePublicId, {
        pacientePublicId: pacienteId,
        motivoReconciliacion: motivo
      });
      ui.showToast('Identidad provisional reconciliada exitosamente.', 'success');
      router.navigate('#/nursing/dashboard');
    } catch (err) {
      btnSubmit.disabled = false;
      btnSubmit.innerHTML = `${ui.icon('link', 'icon icon--sm')} <span>Confirmar y Reconciliar Identidad</span>`;
      ui.showToast('Error en reconciliación: ' + (err.message || 'Error de servidor'), 'error');
    }
  });
}

function renderSummary(ep) {
  const card = document.getElementById('summaryProvisionalCard');
  card.innerHTML = `
    <div class="flex items-center justify-between mb-2">
      <span class="text-xs font-bold uppercase tracking-wider text-muted">Registro Provisional Activo</span>
      <span class="badge badge--warning font-bold">${esc(ep.identidadEstado || 'PROVISIONAL')}</span>
    </div>
    <div class="text-xl font-mono font-bold text-indigo-700">${esc(ep.codigoProvisional || 'NN-SIN-CODIGO')}</div>
    <div class="text-sm mt-2 text-muted">
      Motivo de Ingreso: <strong>${esc(ep.motivoConsulta || '—')}</strong>
    </div>
    <div class="text-xs text-muted mt-1">
      Vía de Ingreso: ${esc(ep.viaIngreso || 'ESPONTANEO')} · Sede: ${esc(ep.sedeNombre || 'Hospital')}
    </div>
  `;
}
