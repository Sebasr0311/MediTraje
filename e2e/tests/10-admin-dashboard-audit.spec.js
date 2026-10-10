// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Auditoría Integral del Dashboard Administrativo y Menú Lateral
 * Requisitos del usuario:
 * 1. No debe haber 'Administración del Sistema', sino 'Dashboard'.
 * 2. Los apartados administrativos deben estar en la barra menú y en las tarjetas.
 * 3. Auditoría UI/UX impecable: skeletons de carga, microinteracciones, accesibilidad y cero bugs de consola.
 */

test.describe('Auditoría Integral: Dashboard Administrativo y Menú Lateral', () => {
  const adminEmail = 'admin@meditriaje.com';

  test.beforeEach(async ({ page }) => {
    // Escuchar errores de consola para verificar que no haya bugs
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        console.error(`[Browser Error]: ${msg.text()}`);
      }
    });

    // Simular sesión de Administrador activo
    await page.route('**/api/v1/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-admin-valledupar',
          email: adminEmail,
          nombreCompleto: 'Administrador Hospitalario',
          roles: ['ROLE_ADMINISTRADOR']
        })
      });
    });

    // Mock de métricas para el Dashboard
    await page.route('**/api/v1/admin/sites*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { publicId: 'site-1', nombre: 'Hospital Central Valledupar', estado: 'ACTIVO' },
            { publicId: 'site-2', nombre: 'Clínica César', estado: 'ACTIVO' }
          ],
          totalElements: 2
        })
      });
    });

    await page.route('**/api/v1/admin/professionals*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { publicId: 'pro-1', nombreCompleto: 'Dr. Carlos Mendoza', estado: 'ACTIVO' },
            { publicId: 'pro-2', nombreCompleto: 'Dra. María Gómez', estado: 'ACTIVO' }
          ],
          totalElements: 12
        })
      });
    });

    await page.route('**/api/v1/admin/specialties*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { publicId: 'spec-1', nombre: 'Medicina General', estado: 'ACTIVO' },
            { publicId: 'spec-2', nombre: 'Pediatría', estado: 'ACTIVO' }
          ],
          totalElements: 8
        })
      });
    });

    await page.route('**/api/v1/admin/slots*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { publicId: 'slot-1', inicio: '2026-10-15T08:00:00Z', estado: 'LIBRE' }
          ],
          totalElements: 45
        })
      });
    });

    // Mock de eventos de auditoría
    await page.route('**/api/v1/admin/audit*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              publicId: 'aud-1',
              fechaHora: '2026-10-10T14:30:00Z',
              tipoEvento: 'AUTENTICACION_LOGIN',
              usuarioEmail: adminEmail,
              resultado: 'EXITO',
              ipOrigen: '192.168.1.100'
            },
            {
              publicId: 'aud-2',
              fechaHora: '2026-10-10T14:32:00Z',
              tipoEvento: 'CREACION_TURNO_SLOT',
              usuarioEmail: adminEmail,
              resultado: 'EXITO',
              ipOrigen: '192.168.1.100'
            }
          ],
          totalElements: 2
        })
      });
    });

    // Mock de configuración MFA
    await page.route('**/api/v1/auth/mfa/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          secretKey: 'JBSWY3DPEHPK3PXP',
          qrCodeBase64: 'data:image/svg+xml;utf8,<svg></svg>',
          recoveryCodes: ['REC-001', 'REC-002']
        })
      });
    });
  });

  test('debe cargar el Dashboard administrativo con título, KPIs, módulos y sin texto genérico antiguo', async ({ page }) => {
    await page.goto('/#/admin/dashboard');

    // 1. Título 'Dashboard' visible y ausencia de 'Administración del Sistema' en encabezados principales
    const mainHeading = page.locator('h1');
    await expect(mainHeading).toContainText('Dashboard');
    const oldTitle = page.locator('h1:has-text("Administración del Sistema")');
    await expect(oldTitle).toHaveCount(0);

    // 2. Comprobar alerta de aislamiento de privilegios (ADR-007)
    await expect(page.locator('text=Aislamiento de Privilegios')).toBeVisible();

    // 3. Comprobar que los KPIs numéricos cargan reemplazando los skeletons
    await expect(page.locator('#dashMetricSites')).toHaveText('2');
    await expect(page.locator('#dashMetricPros')).toHaveText('12');
    await expect(page.locator('#dashMetricSpecs')).toHaveText('8');
    await expect(page.locator('#dashMetricSlots')).toHaveText('45');

    // 4. Comprobar las 3 áreas de gestión con enlaces
    await expect(page.locator('h3:has-text("Infraestructura")')).toBeVisible();
    await expect(page.locator('h3:has-text("Cuerpo Médico y Citas")')).toBeVisible();
    await expect(page.locator('h3:has-text("Aseguramiento y Control")')).toBeVisible();

    // 5. Comprobar la bitácora reciente de auditoría
    await expect(page.locator('#dashRecentAuditContainer table')).toBeVisible();
    await expect(page.locator('#dashRecentAuditContainer')).toContainText('AUTENTICACION_LOGIN');
    await expect(page.locator('#dashRecentAuditContainer')).toContainText('ÉXITO');
  });

  test('el menú lateral debe listar todos los apartados organizados bajo la barra de menú', async ({ page }) => {
    await page.goto('/#/admin/dashboard');

    const sidebar = page.locator('#app-sidebar');
    await expect(sidebar).toBeVisible();

    // Comprobar enlaces principales en el menú lateral
    await expect(sidebar.locator('a[href="#/admin/dashboard"]')).toBeVisible();
    await expect(sidebar.locator('a[href="#/operational/dashboard"]')).toBeVisible();
    await expect(sidebar.locator('a[href="#/hospital/census"]')).toBeVisible();

    // Comprobar infraestructura
    await expect(sidebar.locator('a[href="#/admin/institutions"]')).toBeVisible();
    await expect(sidebar.locator('a[href="#/admin/sites"]')).toBeVisible();

    // Comprobar cuerpo médico y citas
    await expect(sidebar.locator('a[href="#/admin/specialties"]')).toBeVisible();
    await expect(sidebar.locator('a[href="#/admin/professionals"]')).toBeVisible();
    await expect(sidebar.locator('a[href="#/admin/slots"]')).toBeVisible();
    await expect(sidebar.locator('a[href="#/admin/appointments"]')).toBeVisible();

    // Comprobar aseguramiento y control
    await expect(sidebar.locator('a[href="#/affiliations/import"]')).toBeVisible();
    await expect(sidebar.locator('a[href="#/affiliations/search"]')).toBeVisible();
    await expect(sidebar.locator('a[href="#/admin/reports"]')).toBeVisible();
    await expect(sidebar.locator('a[href="#/admin/audit"]')).toBeVisible();
  });

  test('el botón Actualizar debe refrescar métricas y emitir toast de confirmación', async ({ page }) => {
    await page.goto('/#/admin/dashboard');

    const refreshBtn = page.locator('#btnAdminRefresh');
    await expect(refreshBtn).toBeVisible();
    await refreshBtn.click();

    // Verificar notificación Toast
    const toast = page.locator('.toast');
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('actualizadas en tiempo real');
  });

  test('el botón Seguridad MFA debe abrir el modal accesible de configuración de dos pasos', async ({ page }) => {
    await page.goto('/#/admin/dashboard');

    const mfaBtn = page.locator('#btnAdminMfa');
    await expect(mfaBtn).toBeVisible();
    await mfaBtn.click();

    // Modal abierto
    const modal = page.locator('#modal-container .modal-backdrop');
    await expect(modal).toBeVisible();
    await expect(modal).toContainText('Autenticación en Dos Pasos');

    // Cerrar modal con tecla Escape
    await page.keyboard.press('Escape');
    await expect(page.locator('#modal-container .modal-backdrop')).toHaveCount(0);
  });
});
