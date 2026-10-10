/**
 * MediTriaje 2.0 — Importación Masiva de Afiliados EPS (affiliation-import.js)
 * Carga segura de XLSX, previsualización en staging y commit atómico/parcial (A02, A03, A04).
 */

import { affiliationApi } from '../api.js';

export async function renderAffiliationImport(container) {
  container.innerHTML = `
    <div class="view-header">
      <div class="breadcrumb">Administración &gt; Aseguramiento EPS &gt; Importación Masiva</div>
      <h1 class="view-title">Importación Masiva de Afiliados EPS</h1>
      <p class="view-subtitle">Cargue archivos XLSX oficiales de aseguradoras para actualizar el censo de afiliados en Valledupar.</p>
    </div>

    <div class="grid grid-2" style="gap: 1.5rem; margin-top: 1rem;">
      <!-- Tarjeta de Carga -->
      <div class="card">
        <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 1rem;">1. Selección y Carga de Archivo</h2>
        <form id="form-upload-excel" class="form-group" style="display: flex; flex-direction: column; gap: 1rem;">
          <div>
            <label for="select-eps" class="label-required">Entidad Promotora de Salud (EPS)</label>
            <select id="select-eps" class="form-control" required>
              <option value="">-- Seleccione una EPS --</option>
            </select>
          </div>

          <div>
            <label for="file-excel" class="label-required">Archivo Excel (.xlsx, máx. 10 MB)</label>
            <input type="file" id="file-excel" class="form-control" accept=".xlsx" required />
            <small style="color: var(--color-text-secondary); display: block; margin-top: 0.25rem;">
              Columnas requeridas: TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, REGIMEN, TIPO_AFILIADO.
            </small>
          </div>

          <button type="submit" id="btn-previsualizar" class="btn btn-primary" style="margin-top: 0.5rem;">
            Analizar y Previsualizar Archivo
          </button>
        </form>
      </div>

      <!-- Tarjeta de Métricas de Validación -->
      <div class="card" id="card-metricas" style="display: none;">
        <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 1rem;">2. Resultados del Pre-análisis</h2>
        <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; text-align: center; margin-bottom: 1.5rem;">
          <div style="background: var(--color-bg-secondary); padding: 1rem; border-radius: var(--radius-md);">
            <div style="font-size: 0.85rem; color: var(--color-text-secondary);">Total Filas</div>
            <div id="stat-total" style="font-size: 1.75rem; font-weight: 700;">0</div>
          </div>
          <div style="background: #e8f5e9; color: #2e7d32; padding: 1rem; border-radius: var(--radius-md);">
            <div style="font-size: 0.85rem;">Válidas</div>
            <div id="stat-validas" style="font-size: 1.75rem; font-weight: 700;">0</div>
          </div>
          <div style="background: #ffebee; color: #c62828; padding: 1rem; border-radius: var(--radius-md);">
            <div style="font-size: 0.85rem;">Con Errores</div>
            <div id="stat-fallidas" style="font-size: 1.75rem; font-weight: 700;">0</div>
          </div>
        </div>

        <div style="border-top: 1px solid var(--color-border); padding-top: 1rem;">
          <label for="select-modo-commit" style="font-weight: 600; display: block; margin-bottom: 0.5rem;">Modo de Aplicación:</label>
          <select id="select-modo-commit" class="form-control" style="margin-bottom: 1rem;">
            <option value="VALID_ROWS">Importar solo filas válidas (ignorar filas con error)</option>
            <option value="ATOMIC_ALL">Atómico estricto (rechazar todo si hay un solo error)</option>
          </select>
          <button id="btn-confirmar-commit" class="btn btn-success" style="width: 100%;">
            Confirmar y Aplicar Importación Definitiva
          </button>
        </div>
      </div>
    </div>

    <!-- Muestra de Filas Analizadas -->
    <div class="card" id="card-muestra" style="margin-top: 1.5rem; display: none;">
      <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 1rem;">Muestra de Registros Analizados (Staging)</h2>
      <div style="overflow-x: auto;">
        <table class="table" style="width: 100%; text-align: left;">
          <thead>
            <tr>
              <th>Fila</th>
              <th>Documento</th>
              <th>Nombres y Apellidos</th>
              <th>Régimen</th>
              <th>Tipo Afiliado</th>
              <th>Estado</th>
              <th>Observación / Error</th>
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
    console.error('Error cargando lista de EPS:', err);
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
