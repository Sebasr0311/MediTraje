/**
 * MediTriaje 2.0 — Importación Masiva de Afiliados EPS (affiliation-import.js)
 * Carga segura de XLSX, previsualización en staging y commit atómico/parcial (A02, A03, A04).
 */

import { api, affiliationApi } from '../api.js';

export async function renderAffiliationImport(container) {
  container.innerHTML = `
    <div class="view-header">
      <div class="breadcrumb">Administración &gt; Aseguramiento EPS &gt; Importación Masiva</div>
      <h1 class="view-title">Importación Masiva de Afiliados EPS</h1>
      <p class="view-subtitle">Cargue archivos XLSX oficiales de aseguradoras para actualizar el censo de afiliados en Valledupar.</p>
    </div>

    <!-- Tarjeta de Descarga de Plantilla Oficial -->
    <div class="card" style="margin-top: 1rem; background: var(--surface-2); border-left: 4px solid var(--primary); padding: 1.25rem;">
      <div style="display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 1rem;">
        <div>
          <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.25rem;">
            <span style="font-size: 1.25rem;" aria-hidden="true">📥</span>
            <h2 style="font-size: 1.1rem; font-weight: 700; margin: 0; color: var(--text);">Plantilla Oficial de Afiliados (.xlsx)</h2>
          </div>
          <p style="margin: 0; font-size: 0.875rem; color: var(--text-muted);">
            Descargue el formato estándar preconfigurado con columnas requeridas (TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, REGIMEN, TIPO_AFILIADO), ejemplos reales y guía técnica MinSalud.
          </p>
        </div>
        <button type="button" id="btn-descargar-plantilla" class="btn btn-secondary" style="font-weight: 600; white-space: nowrap;" aria-label="Descargar plantilla oficial de afiliados en Excel">
          Descargar Plantilla Excel
        </button>
      </div>
    </div>

    <div class="grid grid-cols-1 grid-cols-2-md gap-6 mt-4">
      <!-- Tarjeta de Carga -->
      <div class="card">
        <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 1rem;">1. Selección y Carga de Archivo</h2>
        <form id="form-upload-excel" class="form-group" style="display: flex; flex-direction: column; gap: 1rem;">
          <div>
            <label for="select-eps" class="label-required">Entidad Promotora de Salud (EPS)</label>
            <select id="select-eps" class="form-control" required aria-label="Seleccionar Entidad Promotora de Salud">
              <option value="">-- Seleccione una EPS --</option>
            </select>
          </div>

          <div>
            <label for="file-excel" class="label-required">Archivo Excel (.xlsx, máx. 10 MB)</label>
            <input type="file" id="file-excel" class="form-control" accept=".xlsx" required aria-label="Cargar archivo Excel de afiliados" />
            <small style="color: var(--text-muted); display: block; margin-top: 0.25rem;">
              Columnas requeridas: TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, REGIMEN, TIPO_AFILIADO.
            </small>
          </div>

          <button type="submit" id="btn-previsualizar" class="btn btn-primary" style="margin-top: 0.5rem;" aria-label="Analizar y previsualizar archivo de afiliados">
            Analizar y Previsualizar Archivo
          </button>
        </form>
      </div>

      <!-- Tarjeta de Métricas de Validación -->
      <div class="card" id="card-metricas" style="display: none;">
        <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 1rem;">2. Resultados del Pre-análisis</h2>
        <div class="grid grid-cols-1 grid-cols-3-md gap-3 text-center mb-6">
          <div style="background: var(--surface-2); padding: 1rem; border-radius: var(--radius-md);">
            <div style="font-size: 0.85rem; color: var(--text-muted);">Total Filas</div>
            <div id="stat-total" style="font-size: 1.75rem; font-weight: 700;">0</div>
          </div>
          <div style="background: var(--success-bg, #DCFCE7); color: var(--on-success-bg, #14532D); padding: 1rem; border-radius: var(--radius-md);">
            <div style="font-size: 0.85rem;">Válidas</div>
            <div id="stat-validas" style="font-size: 1.75rem; font-weight: 700;">0</div>
          </div>
          <div style="background: var(--danger-bg, #FEE2E2); color: var(--on-danger-bg, #7F1D1D); padding: 1rem; border-radius: var(--radius-md);">
            <div style="font-size: 0.85rem;">Con Errores</div>
            <div id="stat-fallidas" style="font-size: 1.75rem; font-weight: 700;">0</div>
          </div>
        </div>

        <div style="border-top: 1px solid var(--border); padding-top: 1rem;">
          <label for="select-modo-commit" style="font-weight: 600; display: block; margin-bottom: 0.5rem;">Modo de Aplicación:</label>
          <select id="select-modo-commit" class="form-control" style="margin-bottom: 1rem;" aria-label="Modo de aplicación del lote">
            <option value="VALID_ROWS">Importar solo filas válidas (ignorar filas con error)</option>
            <option value="ATOMIC_ALL">Atómico estricto (rechazar todo si hay un solo error)</option>
          </select>
          <button id="btn-confirmar-commit" class="btn btn-success" style="width: 100%;" aria-label="Confirmar y aplicar importación definitiva">
            Confirmar y Aplicar Importación Definitiva
          </button>
        </div>
      </div>
    </div>

    <!-- Muestra de Filas Analizadas -->
    <div class="card" id="card-muestra" style="margin-top: 1.5rem; display: none;">
      <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 1rem;">Muestra de Registros Analizados (Staging)</h2>
      <div class="table-container">
        <table class="table table--stacked" style="width: 100%; text-align: left;">
          <thead>
            <tr>
              <th scope="col">Fila</th>
              <th scope="col">Documento</th>
              <th scope="col">Nombres y Apellidos</th>
              <th scope="col">Régimen</th>
              <th scope="col">Tipo Afiliado</th>
              <th scope="col">Estado</th>
              <th scope="col">Observación / Error</th>
            </tr>
          </thead>
          <tbody id="tbody-muestra"></tbody>
        </table>
      </div>
    </div>
  `;

  const selectEps = container.querySelector('#select-eps');
  const formUpload = container.querySelector('#form-upload-excel');
  const cardMetricas = container.querySelector('#card-metricas');
  const cardMuestra = container.querySelector('#card-muestra');
  const tbodyMuestra = container.querySelector('#tbody-muestra');
  const statTotal = container.querySelector('#stat-total');
  const statValidas = container.querySelector('#stat-validas');
  const statFallidas = container.querySelector('#stat-fallidas');
  const btnCommit = container.querySelector('#btn-confirmar-commit');
  const selectModo = container.querySelector('#select-modo-commit');

  let currentLoteId = null;

  // Cargar lista de EPS
  try {
    const epsList = await affiliationApi.listarEps();
    if (epsList && epsList.length > 0) {
      epsList.forEach(eps => {
        const opt = document.createElement('option');
        opt.value = eps.publicId;
        opt.textContent = `${eps.codigoMinSalud} — ${eps.nombre}`;
        selectEps.appendChild(opt);
      });
    }
  } catch (err) {
    console.warn('Lista de EPS en sincronización o no disponible:', err);
    const opt = document.createElement('option');
    opt.value = '';
    opt.disabled = true;
    opt.textContent = '-- En sincronización con el servidor --';
    selectEps.appendChild(opt);
  }

  // Descarga de plantilla oficial Excel (.xlsx)
  const btnDescargarPlantilla = container.querySelector('#btn-descargar-plantilla');
  if (btnDescargarPlantilla) {
    btnDescargarPlantilla.addEventListener('click', async () => {
      btnDescargarPlantilla.disabled = true;
      const originalText = btnDescargarPlantilla.textContent;
      btnDescargarPlantilla.textContent = 'Descargando plantilla...';
      try {
        const templateUrl = api.buildUrl('/affiliations/template');
        const res = await fetch(templateUrl, { credentials: 'include' });
        if (res.ok) {
          const blob = await res.blob();
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = 'plantilla_afiliados_eps.xlsx';
          document.body.appendChild(a);
          a.click();
          a.remove();
          window.URL.revokeObjectURL(url);
        } else {
          const a = document.createElement('a');
          a.href = '/assets/plantilla_afiliados_eps.xlsx';
          a.download = 'plantilla_afiliados_eps.xlsx';
          document.body.appendChild(a);
          a.click();
          a.remove();
        }
      } catch (err) {
        const a = document.createElement('a');
        a.href = '/assets/plantilla_afiliados_eps.xlsx';
        a.download = 'plantilla_afiliados_eps.xlsx';
        document.body.appendChild(a);
        a.click();
        a.remove();
      } finally {
        btnDescargarPlantilla.disabled = false;
        btnDescargarPlantilla.textContent = originalText;
      }
    });
  }

  // Carga de archivo y preview
  formUpload.addEventListener('submit', async (e) => {
    e.preventDefault();
    const epsPubId = selectEps.value;
    const fileInput = container.querySelector('#file-excel');
    if (!epsPubId || !fileInput.files[0]) {
      alert('Por favor seleccione la EPS y el archivo Excel.');
      return;
    }

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);
    formData.append('epsPublicId', epsPubId);

    const btnPrev = container.querySelector('#btn-previsualizar');
    btnPrev.disabled = true;
    btnPrev.textContent = 'Analizando archivo...';

    try {
      const res = await affiliationApi.cargarPreviewExcel(formData);
      currentLoteId = res.lotePublicId;
      statTotal.textContent = res.totalFilas;
      statValidas.textContent = res.filasValidas;
      statFallidas.textContent = res.filasFallidas;

      tbodyMuestra.innerHTML = '';
      if (res.muestraFilas && res.muestraFilas.length > 0) {
        res.muestraFilas.forEach(f => {
          const tr = document.createElement('tr');
          const esValida = f.estadoFila === 'VALIDO';
          tr.innerHTML = `
            <td>#${f.numeroFila}</td>
            <td><strong>${f.tipoDocumento}</strong> ${f.numeroDocumento}</td>
            <td>${f.nombres || ''} ${f.apellidos || ''}</td>
            <td>${f.regimen || '-'}</td>
            <td>${f.tipoAfiliado || '-'}</td>
            <td>
              <span class="badge ${esValida ? 'badge-success' : 'badge-danger'}">
                ${esValida ? 'Válida' : 'Error'}
              </span>
            </td>
            <td style="color: ${esValida ? 'inherit' : 'var(--color-danger, #d32f2f)'}; font-size: 0.85rem;">
              ${f.errorMotivo || 'OK para importación'}
            </td>
          `;
          tbodyMuestra.appendChild(tr);
        });
      }

      cardMetricas.style.display = 'block';
      cardMuestra.style.display = 'block';
    } catch (err) {
      alert(err.message || 'Error analizando archivo Excel.');
    } finally {
      btnPrev.disabled = false;
      btnPrev.textContent = 'Analizar y Previsualizar Archivo';
    }
  });

  // Confirmar commit definitivo
  btnCommit.addEventListener('click', async () => {
    if (!currentLoteId) return;
    const modo = selectModo.value;
    if (!confirm(`¿Está seguro de aplicar los registros a la base de datos en modo "${modo}"?`)) {
      return;
    }

    btnCommit.disabled = true;
    btnCommit.textContent = 'Aplicando registros...';

    try {
      await affiliationApi.confirmarLote(currentLoteId, modo);
      alert('¡Importación completada con éxito! La base de afiliados ha sido actualizada.');
      cardMetricas.style.display = 'none';
      cardMuestra.style.display = 'none';
      formUpload.reset();
      currentLoteId = null;
    } catch (err) {
      alert(err.message || 'Error al aplicar el lote de importación.');
    } finally {
      btnCommit.disabled = false;
      btnCommit.textContent = 'Confirmar y Aplicar Importación Definitiva';
    }
  });
}
