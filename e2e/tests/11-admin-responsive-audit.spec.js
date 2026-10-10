// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Auditoría Responsive de Todos los Apartados de Admin y Pie de Página Institucional
 * Requisitos:
 * 1. Todos los apartados de administración deben ser totalmente adaptables (responsive) en móviles (375px) y tablet (768px).
 * 2. El pie de página con el aviso legal académico debe estar perfectamente ubicado, estructurado y organizado.
 */

test.describe('Auditoría Responsive: Apartados de Administración y Footer', () => {
  const adminEmail = 'admin@meditriaje.com';

  test.beforeEach(async ({ page }) => {
    // Mock de sesión de administrador
    await page.route('**/api/v1/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-admin-valledupar',
          email: adminEmail,
          nombreCompleto: 'Director Administrativo',
          roles: ['ROLE_ADMINISTRADOR']
        })
      });
    });

    // Mocks de APIs administrativas
    await page.route('**/api/v1/admin/sites*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { publicId: 'site-1', nombre: 'Hospital Central Valledupar', institucionNombre: 'E.S.E. Hospital Rosario Pumarejo', ciudad: 'Valledupar', estado: 'ACTIVO' }
          ],
          totalElements: 1,
          totalPages: 1
        })
      });
    });

    await page.route('**/api/v1/admin/institutions*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { publicId: 'inst-1', nit: '892300456-1', razonSocial: 'E.S.E. Hospital Rosario Pumarejo de López', estado: 'ACTIVO' }
          ],
          totalElements: 1,
          totalPages: 1
        })
      });
    });

    await page.route('**/api/v1/admin/specialties*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { publicId: 'spec-1', nombre: 'Medicina Interna', duracionSlotMin: 30, estado: 'ACTIVO' }
          ],
          totalElements: 1,
          totalPages: 1
        })
      });
    });

    await page.route('**/api/v1/admin/professionals*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { publicId: 'pro-1', nombres: 'Carlos', apellidos: 'Mendoza', tipoDocumento: 'CC', numeroDocumento: '1065123456', registroMedico: 'RM-54321', especialidadNombre: 'Medicina General', estado: 'ACTIVO' }
          ],
          totalElements: 1,
          totalPages: 1
        })
      });
    });

    await page.route('**/api/v1/admin/slots*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { publicId: 'slot-1', profesionalNombre: 'Dr. Carlos Mendoza', sedeNombre: 'Hospital Central', modalidad: 'PRESENCIAL', inicio: '2026-10-15T08:00:00Z', fin: '2026-10-15T08:30:00Z', estado: 'LIBRE' }
          ],
          totalElements: 1,
          totalPages: 1
        })
      });
    });

    await page.route('**/api/v1/admin/audit*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { id: 101, fechaHora: '2026-10-10T14:00:00Z', usuarioEmail: adminEmail, tipoEvento: 'LOGIN_EXITOSO', resultado: 'EXITO', ipOrigen: '127.0.0.1' }
          ],
          totalElements: 1,
          totalPages: 1
        })
      });
    });

    await page.route('**/api/v1/admin/reports/operational*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          citas: { totalCitas: 40, tasaCumplimiento: 90, tasaCancelacion: 5, porEspecialidad: { 'Medicina General': 30 }, porSede: { 'Sede Central': 40 } },
          triaje: { totalTriajes: 15, emergencias: 2, tasaEmergencia: 13, nivel1: 2, nivel2: 3, nivel3: 5, nivel4: 3, nivel5: 2 },
          farmacia: { totalRecetas: 20, unidadesDispensadas: 50, dispensadasTotal: 18, dispensadasParcial: 2 },
          breakGlass: { totalActivaciones: 1, activacionesActivas: 0 }
        })
      });
    });

    await page.route('**/api/v1/affiliations/eps', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { publicId: 'eps-1', codigoMinSalud: 'EPS001', nombre: 'Nueva EPS' }
        ])
      });
    });

    await page.route('**/api/v1/hospital/census/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          totalCamas: 30,
          ocupadas: 18,
          disponibles: 10,
          enLimpieza: 1,
          enMantenimiento: 1,
          tasaOcupacion: 60.0,
          resumenPabellones: [
            { areaServicio: 'URGENCIAS', ocupadas: 8, total: 10, porcentajeOcupacion: 80.0 }
          ]
        })
      });
    });

    await page.route('**/api/v1/hospital/beds/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { publicId: 'bed-01', codigoCama: 'URG-01', habitacion: '101', pabellon: 'Urgencias', areaServicio: 'URGENCIAS', estado: 'DISPONIBLE' }
        ])
      });
    });
  });

  test('el pie de página institucional debe ser semántico, accesible y estar organizado al final de la pantalla', async ({ page }) => {
    await page.goto('/#/admin/dashboard');

    const footer = page.locator('footer.app-footer');
    await expect(footer).toBeVisible();

    // Verificación de marca y aviso de prototipo organizado
    await expect(footer).toContainText('MediTriaje 2.0');
    await expect(footer).toContainText('Plataforma Académica Asistencial');
    await expect(footer.locator('.badge')).toContainText('Prototipo');
    await expect(footer.locator('.app-footer-disclaimer')).toContainText('Ley Estatutaria 1751 de 2015');

    // Verificar que el footer no tape el contenido
    const mainBox = await page.locator('#main-content').boundingBox();
    const footerBox = await footer.boundingBox();
    expect(footerBox?.y).toBeGreaterThanOrEqual(mainBox ? mainBox.y : 0);
  });

  test('todos los apartados administrativos deben renderizarse sin desbordamiento horizontal en móvil (375px)', async ({ page }) => {
    // Configurar viewport móvil
    await page.setViewportSize({ width: 375, height: 667 });

    const routes = [
      '#/admin/dashboard',
      '#/admin/sites',
      '#/admin/institutions',
      '#/admin/specialties',
      '#/admin/professionals',
      '#/admin/slots',
      '#/admin/reports',
      '#/admin/audit',
      '#/affiliations/import',
      '#/affiliations/search',
      '#/hospital/census'
    ];

    for (const route of routes) {
      await page.goto(`/${route}`);
      // Esperar estabilidad de la vista
      await page.waitForTimeout(200);

      // Verificar que el cuerpo del documento no genere scroll horizontal no deseado
      const overflow = await page.evaluate(() => {
        return document.documentElement.scrollWidth <= window.innerWidth + 5;
      });
      expect(overflow, `La ruta ${route} no debe provocar desbordamiento horizontal en pantalla móvil de 375px`).toBe(true);

      // Verificar que el footer siga visible y accesible
      await expect(page.locator('footer.app-footer')).toBeVisible();
    }
  });

  test('todos los apartados administrativos deben renderizarse correctamente en tablet (768px)', async ({ page }) => {
    // Configurar viewport tablet
    await page.setViewportSize({ width: 768, height: 1024 });

    const routes = [
      '#/admin/dashboard',
      '#/admin/sites',
      '#/admin/institutions',
      '#/admin/specialties',
      '#/admin/professionals',
      '#/admin/slots',
      '#/admin/reports',
      '#/admin/audit',
      '#/affiliations/import',
      '#/hospital/census'
    ];

    for (const route of routes) {
      await page.goto(`/${route}`);
      await page.waitForTimeout(150);

      const overflow = await page.evaluate(() => {
        return document.documentElement.scrollWidth <= window.innerWidth + 5;
      });
      expect(overflow, `La ruta ${route} debe adaptarse limpiamente en tablet de 768px`).toBe(true);
    }
  });
});
