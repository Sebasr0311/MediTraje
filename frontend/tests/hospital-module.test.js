/**
 * Test unitario para verificar las funciones de API del módulo de Gestión Hospitalaria (Fase H).
 */
import test from 'node:test';
import assert from 'node:assert/strict';

test('Fase H: Exportación de hospitalApi y métodos requeridos', async () => {
  const { hospitalApi } = await import('../js/api.js');
  assert.ok(hospitalApi, 'hospitalApi debe estar definido y exportado');
  assert.equal(typeof hospitalApi.asignarCama, 'function');
  assert.equal(typeof hospitalApi.trasladarPaciente, 'function');
  assert.equal(typeof hospitalApi.cambiarEstadoCama, 'function');
  assert.equal(typeof hospitalApi.registrarProcedimiento, 'function');
  assert.equal(typeof hospitalApi.actualizarEstadoProcedimiento, 'function');
  assert.equal(typeof hospitalApi.registrarEgreso, 'function');
  assert.equal(typeof hospitalApi.obtenerCenso, 'function');
  assert.equal(typeof hospitalApi.listarCamas, 'function');
  assert.equal(typeof hospitalApi.listarMovimientos, 'function');
  assert.equal(typeof hospitalApi.listarProcedimientos, 'function');
});
