/**
 * Test unitario para verificar la API y vistas de Afiliaciones EPS y Citas (Fases A y C).
 */
import test from 'node:test';
import assert from 'node:assert/strict';

test('Fase A y C: Exportación de affiliationApi y métodos requeridos', async () => {
  const { affiliationApi } = await import('../js/api.js');
  assert.ok(affiliationApi, 'affiliationApi debe estar definido y exportado');
  assert.equal(typeof affiliationApi.listarEps, 'function');
  assert.equal(typeof affiliationApi.cargarPreviewExcel, 'function');
  assert.equal(typeof affiliationApi.confirmarLote, 'function');
  assert.equal(typeof affiliationApi.consultarAfiliacion, 'function');
  assert.equal(typeof affiliationApi.registrarAusencia, 'function');
  assert.equal(typeof affiliationApi.listarAusencias, 'function');
  assert.equal(typeof affiliationApi.registrarTutor, 'function');
  assert.equal(typeof affiliationApi.listarMenoresTutor, 'function');
});
