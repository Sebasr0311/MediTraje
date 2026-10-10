/**
 * Test unitario para verificar la API de Centro de Mando Operativo y Alertas (Fase O).
 */
import test from 'node:test';
import assert from 'node:assert/strict';

test('Fase O: Exportación de operationalApi y métodos requeridos', async () => {
  const { operationalApi } = await import('../js/api.js');
  assert.ok(operationalApi, 'operationalApi debe estar definido y exportado');
  assert.equal(typeof operationalApi.obtenerDashboard, 'function');
  assert.equal(typeof operationalApi.listarAlertas, 'function');
  assert.equal(typeof operationalApi.reconocerAlerta, 'function');
  assert.equal(typeof operationalApi.generarTrackingQr, 'function');
  assert.equal(typeof operationalApi.consultarTrackingQr, 'function');
});
