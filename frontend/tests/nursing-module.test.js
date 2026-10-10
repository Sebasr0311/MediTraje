/**
 * Test unitario para verificar las rutas y roles del módulo de Enfermería y Urgencias (Fase U).
 */
import test from 'node:test';
import assert from 'node:assert/strict';

test('Fase U: Exportación de emergencyApi y funciones de API requeridas', async () => {
  const { emergencyApi } = await import('../js/api.js');
  assert.ok(emergencyApi, 'emergencyApi debe estar definido y exportado');
  assert.equal(typeof emergencyApi.registrarAdmision, 'function');
  assert.equal(typeof emergencyApi.obtenerDetalleEpisodio, 'function');
  assert.equal(typeof emergencyApi.reconciliarIdentidad, 'function');
  assert.equal(typeof emergencyApi.registrarValoracionTriaje, 'function');
  assert.equal(typeof emergencyApi.listarHistorialTriaje, 'function');
  assert.equal(typeof emergencyApi.asignarEquipo, 'function');
  assert.equal(typeof emergencyApi.listarColaUrgencias, 'function');
  assert.equal(typeof emergencyApi.cerrarEpisodio, 'function');
});

test('Fase U: Getter isEnfermeria en Auth', async () => {
  const { auth } = await import('../js/auth.js');
  assert.ok('isEnfermeria' in auth, 'auth debe exponer el getter isEnfermeria');
});
