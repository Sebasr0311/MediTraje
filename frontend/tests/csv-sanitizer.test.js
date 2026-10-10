import test from 'node:test';
import assert from 'node:assert/strict';
import { sanitizeCsvCell } from '../js/views/admin-reports.js';

test('SEC-001: sanitizeCsvCell neutraliza fórmulas de hoja de cálculo anteponiendo comilla simple', async (t) => {
  await t.test('antepone comilla simple para fórmulas con =', () => {
    assert.equal(sanitizeCsvCell('=SUM(A1:A10)'), "'=SUM(A1:A10)");
    assert.equal(sanitizeCsvCell('=1+1'), "'=1+1");
    assert.equal(sanitizeCsvCell('=cmd|/C calc!A0'), "'=cmd|/C calc!A0");
  });

  await t.test('antepone comilla simple para fórmulas con +', () => {
    assert.equal(sanitizeCsvCell('+573001234567'), "'+573001234567");
    assert.equal(sanitizeCsvCell('+123'), "'+123");
  });

  await t.test('antepone comilla simple para fórmulas con -', () => {
    assert.equal(sanitizeCsvCell('-100'), "'-100");
    assert.equal(sanitizeCsvCell('-2+5'), "'-2+5");
  });

  await t.test('antepone comilla simple para fórmulas con @', () => {
    assert.equal(sanitizeCsvCell('@SUM(A1:A5)'), "'@SUM(A1:A5)");
    assert.equal(sanitizeCsvCell('@RM-12345'), "'@RM-12345");
  });

  await t.test('antepone comilla simple para caracteres de control tab (\\t) y retorno de carro (\\r)', () => {
    assert.equal(sanitizeCsvCell('\t=CMD()'), "'\t=CMD()");
    assert.equal(sanitizeCsvCell('\tTextoConTab'), "'\tTextoConTab");
    assert.equal(sanitizeCsvCell('\rTextoConCR'), "'\rTextoConCR");
  });

  await t.test('escapa comillas dobles internas duplicándolas', () => {
    assert.equal(sanitizeCsvCell('Texto con "comillas"'), 'Texto con ""comillas""');
    assert.equal(sanitizeCsvCell('=A1&"test"'), '\'=A1&""test""');
  });

  await t.test('maneja valores nulos, indefinidos y números seguros sin alterar', () => {
    assert.equal(sanitizeCsvCell(null), '');
    assert.equal(sanitizeCsvCell(undefined), '');
    assert.equal(sanitizeCsvCell('Texto Normal'), 'Texto Normal');
    assert.equal(sanitizeCsvCell(12345), '12345');
    assert.equal(sanitizeCsvCell(0), '0');
  });
});
