// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Flujo Crítico 3: Administrador — Aislamiento de Seguridad y 403 en Rutas Clínicas
 * Criterio de aceptación T9 y regla de arquitectura no negociable (ADR-007, AGENTS.md):
 * "El administrador NO accede a contenido clínico. autenticado != autorizado."
 *
 * 1. Admin autenticado accede a sus funciones administrativas (sedes, especialidades, turnos).
 * 2. La UI no expone historiales ni información de salud al administrador.
 * 3. Las peticiones directas a endpoints clínicos retornan estrictamente 403 Forbidden.
 */

test.describe('Flujo Crítico 3: Administrador — Aislamiento de Rutas Clínicas (403)', () => {
  const adminEmail = process.env.DEMO_ADMIN_EMAIL || 'admin@demo.meditriaje.test';
  const password = process.env.DEMO_PASSWORD || 'DemoSecretPass123!';

  test('el administrador debe tener acceso administrativo pero recibir 403 en endpoints clínicos', async ({ page, request }) => {
    // 1. Iniciar sesión como administrador
    await page.goto('/#/login');
    await expect(page.locator('#formLogin')).toBeVisible();

    await page.fill('#loginEmail', adminEmail);
    await page.fill('#loginPassword', password);
    await page.click('#formLogin button[type="submit"]');

    // Esperar redirección a panel administrativo
    await expect(page).toHaveURL(/.*#\/admin\/.*/);

    // 2. Comprobar que en la UI del admin NO existen enlaces de navegación hacia historia clínica
    const clinicalNavLinks = page.locator('nav a[href*="/patient/history"], nav a[href*="/professional/agenda"]');
    await expect(clinicalNavLinks).toHaveCount(0);

    // 3. Evaluar peticiones fetch autenticadas directamente en el contexto del navegador del admin
    // La sesión activa (cookies HttpOnly) debe ser rechazada con 403 en todas las rutas clínicas

    const testEndpoints = [
      { method: 'GET', url: '/api/v1/patients/me/history', label: 'Historia clínica propia' },
      { method: 'GET', url: '/api/v1/patients/00000000-0000-0000-0000-000000000001/history', label: 'Historia clínica por ID' },
      { method: 'GET', url: '/api/v1/attentions/00000000-0000-0000-0000-000000000001', label: 'Detalle de atención médica' },
      { method: 'GET', url: '/api/v1/prescriptions/00000000-0000-0000-0000-000000000001', label: 'Detalle de receta médica' },
      { method: 'GET', url: '/api/v1/clinical/patients/00000000-0000-0000-0000-000000000001/allergies', label: 'Alergias clínicas' },
      { method: 'GET', url: '/api/v1/professionals/me/agenda', label: 'Agenda médica profesional' }
    ];

    for (const ep of testEndpoints) {
      const responseStatus = await page.evaluate(async (url) => {
        try {
          const res = await fetch(url, {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
          });
          return res.status;
        } catch (e) {
          return -1;
        }
      }, ep.url);

      // Toda solicitud a contenido clínico realizada por administrador debe arrojar 403 Forbidden
      expect(responseStatus, `El endpoint clínico ${ep.url} (${ep.label}) debe rechazar al administrador con 403 Forbidden`).toBe(403);
    }

    // 4. Intento de emisión de receta como administrador -> 403 Forbidden
    const createRxStatus = await page.evaluate(async () => {
      try {
        const res = await fetch('/api/v1/prescriptions', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
          },
          credentials: 'include',
          body: JSON.stringify({
            atencionPublicId: '00000000-0000-0000-0000-000000000001',
            detalles: [{
              medicamentoPublicId: '00000000-0000-0000-0000-000000000001',
              dosis: '500mg',
              frecuencia: '8h',
              duracionDias: 3,
              cantidad: 9
            }]
          })
        });
        return res.status;
      } catch {
        return -1;
      }
    });

    expect(createRxStatus, 'La creación de recetas por parte del administrador debe rechazar con 403 Forbidden').toBe(403);
  });
});
