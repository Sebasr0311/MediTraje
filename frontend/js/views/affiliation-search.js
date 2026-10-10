/**
 * MediTriaje 2.0 — Consulta de Afiliación y Aseguramiento EPS (affiliation-search.js)
 * Consulta en tiempo real de EPS y régimen. No bloqueante para triage/urgencias (A05, ADR-025).
 */

import { affiliationApi } from '../api.js';

export async function renderAffiliationSearch(container) {
  container.innerHTML = `
    <div class="view-header">
      <div class="breadcrumb">Admisiones &gt; Aseguramiento EPS &gt; Verificación de Derechos</div>
      <h1 class="view-title">Consulta de Aseguramiento EPS</h1>
      <p class="view-subtitle">Verifique los derechos de aseguramiento en salud del paciente en Valledupar.</p>
    </div>

    <!-- Banner normativo de no bloqueo asistencial -->
    <div class="alert alert-info" style="margin-top: 1rem; border-left: 4px solid var(--color-primary);">
      <strong>Aviso de Cumplimiento Legal (Ley 1751 de 2015):</strong> La comprobación de derechos de aseguramiento es estrictamente administrativa.
      Bajo ninguna circunstancia la falta de carné, afiliación inactiva o condición de no asegurado puede suspender o demorar la valoración médica inicial de urgencias.
    </div>

    <div class="card" style="margin-top: 1.5rem; max-width: 600px;">
      <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 1rem;">Buscar Paciente por Documento</h2>
      <form id="form-consulta-eps" style="display: flex; gap: 0.75rem; flex-wrap: wrap;">
        <div style="flex: 1; min-width: 120px;">
          <label for="tipo-doc-eps" class="label-required">Tipo Doc.</label>
          <select id="tipo-doc-eps" class="form-control" required>
            <option value="CC">CC - Cédula</option>
            <option value="TI">TI - Tarjeta Id</option>
            <option value="RC">RC - Registro Civil</option>
            <option value="CE">CE - Extranjería</option>
            <option value="PA">PA - Pasaporte</option>
            <option value="PPT">PPT - Permiso Protección</option>
          </select>
        </div>
        <div style="flex: 2; min-width: 180px;">
          <label for="num-doc-eps" class="label-required">Número Documento</label>
          <input type="text" id="num-doc-eps" class="form-control" placeholder="Ej: 1065123456" required />
        </div>
        <div style="width: 100%; margin-top: 0.5rem;">
          <button type="submit" id="btn-consultar" class="btn btn-primary" style="width: 100%;">
            Consultar Estado de Afiliación
          </button>
        </div>
      </form>
    </div>

    <!-- Tarjeta con el Resultado -->
    <div class="card" id="card-resultado-eps" style="margin-top: 1.5rem; display: none; max-width: 600px;">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
        <h2 style="font-size: 1.25rem; font-weight: 600; margin: 0;">Estado de Aseguramiento</h2>
        <span id="badge-estado-eps" class="badge">ACTIVO</span>
      </div>

      <div style="display: flex; flex-direction: column; gap: 0.75rem;">
        <div>
          <span style="color: var(--color-text-secondary); font-size: 0.85rem; display: block;">Paciente</span>
          <strong id="res-paciente-nombre" style="font-size: 1.05rem;">-</strong>
          <span id="res-paciente-doc" style="color: var(--color-text-secondary); font-size: 0.9rem;">-</span>
        </div>

        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; border-top: 1px solid var(--color-border); padding-top: 0.75rem;">
          <div>
            <span style="color: var(--color-text-secondary); font-size: 0.85rem; display: block;">Aseguradora (EPS)</span>
            <strong id="res-eps-nombre">-</strong>
            <div id="res-eps-codigo" style="font-size: 0.8rem; color: var(--color-text-secondary);">-</div>
          </div>
          <div>
            <span style="color: var(--color-text-secondary); font-size: 0.85rem; display: block;">Régimen</span>
            <strong id="res-regimen">-</strong>
          </div>
        </div>

        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; border-top: 1px solid var(--color-border); padding-top: 0.75rem;">
          <div>
            <span style="color: var(--color-text-secondary); font-size: 0.85rem; display: block;">Tipo Afiliado</span>
            <span id="res-tipo-afiliado">-</span>
          </div>
          <div>
            <span style="color: var(--color-text-secondary); font-size: 0.85rem; display: block;">Fuente Verificación</span>
            <span id="res-fuente-verif" style="font-size: 0.85rem; color: var(--color-text-secondary);">-</span>
          </div>
        </div>
      </div>
    </div>
  `;

  const form = container.querySelector('#form-consulta-eps');
  const cardRes = container.querySelector('#card-resultado-eps');
  const badgeEstado = container.querySelector('#badge-estado-eps');
  const resNombre = container.querySelector('#res-paciente-nombre');
  const resDoc = container.querySelector('#res-paciente-doc');
  const resEpsNombre = container.querySelector('#res-eps-nombre');
  const resEpsCodigo = container.querySelector('#res-eps-codigo');
  const resRegimen = container.querySelector('#res-regimen');
  const resTipoAfiliado = container.querySelector('#res-tipo-afiliado');
  const resFuente = container.querySelector('#res-fuente-verif');
  const btnConsultar = container.querySelector('#btn-consultar');

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const tipoDoc = container.querySelector('#tipo-doc-eps').value;
    const numDoc = container.querySelector('#num-doc-eps').value.trim();

    if (!tipoDoc || !numDoc) return;

    btnConsultar.disabled = true;
    btnConsultar.textContent = 'Consultando...';

    try {
      const res = await affiliationApi.consultarAfiliacion(tipoDoc, numDoc);
      cardRes.style.display = 'block';

      resNombre.textContent = res.pacienteNombre || 'Paciente No Registrado Previamente';
      resDoc.textContent = `${res.tipoDocumento} ${res.numeroDocumento}`;
      resEpsNombre.textContent = res.epsNombre || 'PARTICULAR / NO ASEGURADO';
      resEpsCodigo.textContent = res.epsCodigo ? `Código: ${res.epsCodigo}` : '';
      resRegimen.textContent = res.regimen || 'NO_ASEGURADO';
      resTipoAfiliado.textContent = res.tipoAfiliado || 'N/A';
      resFuente.textContent = res.fuenteVerificacion || 'CONSULTA DIRECTA';

      if (res.estado === 'ACTIVO') {
        badgeEstado.textContent = 'ACTIVO';
        badgeEstado.className = 'badge badge-success';
      } else if (res.estado === 'NO_AFILIADO') {
        badgeEstado.textContent = 'NO AFILIADO';
        badgeEstado.className = 'badge badge-warning';
      } else {
        badgeEstado.textContent = res.estado || 'DESCONOCIDO';
        badgeEstado.className = 'badge badge-secondary';
      }
    } catch (err) {
      alert(err.message || 'Error consultando aseguramiento.');
    } finally {
      btnConsultar.disabled = false;
      btnConsultar.textContent = 'Consultar Estado de Afiliación';
    }
  });
}
