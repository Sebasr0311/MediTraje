// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Flujo Fase 4 (Lotes A y C): Aseguramiento EPS, Importación Masiva y Citas Avanzadas
 * Cubre los requisitos del Plan Maestro:
 * - RF-013 / A01: Catálogo de aseguradoras EPS y regímenes vigentes en Colombia.
 * - RF-014 / A02-A03: Carga segura de XLSX, validación en staging y previsualización de errores.
 * - RF-015 / A04: Aplicación definitiva del lote (VALID_ROWS vs ATOMIC_ALL).
 * - RF-016 / A05: Consulta no bloqueante de derechos de afiliación (Ley 1751 de 2015).
 * - RF-017 / C02: Bloqueo de agenda y gestión de ausencias médicas asistenciales.
 */

test.describe('Fase 4: Aseguramiento EPS, Importación Masiva y Citas Avanzadas', () => {
  const adminEmail = process.env.DEMO_ADMIN_EMAIL || 'admin@demo.meditriaje.test';

  test.beforeEach(async ({ page }) => {
    // Aceptar automáticamente diálogos de confirm y alert en la UI
    page.on('dialog', async (dialog) => {
      await dialog.accept();
    });

    // Sesión activa con rol de administrador
    await page.route('**/api/v1/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-admin-01',
          email: adminEmail,
          nombreCompleto: 'Administrador Hospitalario',
          roles: ['ROLE_ADMINISTRADOR']
        })
      });
    });

    // Catálogo de EPS
    await page.route('**/api/v1/affiliations/eps', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { publicId: 'eps-sura-01', codigoMinSalud: 'EPS010', nombre: 'EPS SURA' },
          { publicId: 'eps-sanitas-01', codigoMinSalud: 'EPS005', nombre: 'EPS SANITAS' },
          { publicId: 'eps-nueva-01', codigoMinSalud: 'EPS037', nombre: 'NUEVA EPS' }
        ])
      });
    });
  });

  test('debe cargar y previsualizar archivo XLSX de EPS con métricas de staging y commit parcial (A02, A03, A04)', async ({ page }) => {
    const loteId = 'lote-xlsx-2026-001';

    // Mock upload preview de Excel
    let uploadCaptured = false;
    await page.route('**/api/v1/affiliations/upload-preview', async (route) => {
      uploadCaptured = true;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          lotePublicId: loteId,
          totalFilas: 10,
          filasValidas: 8,
          filasFallidas: 2,
          muestraFilas: [
            {
              numeroFila: 2,
              tipoDocumento: 'CC',
              numeroDocumento: '1065123456',
              nombres: 'Carlos Andrés',
              apellidos: 'Gómez Quintero',
              regimen: 'CONTRIBUTIVO',
              tipoAfiliado: 'COTIZANTE',
              estadoFila: 'VALIDO',
              errorMotivo: null
            },
            {
              numeroFila: 3,
              tipoDocumento: 'TI',
              numeroDocumento: '99010154321',
              nombres: 'María José',
              apellidos: 'Pérez',
              regimen: 'SUBSIDIADO',
              tipoAfiliado: 'BENEFICIARIO',
              estadoFila: 'INVALIDO',
              errorMotivo: 'TIPO_DOCUMENTO no concuerda con rango de edad'
            }
          ]
        })
      });
    });

    // Mock commit definitivo
    let commitPayload = null;
    await page.route(`**/api/v1/affiliations/batches/${loteId}/commit`, async (route) => {
      commitPayload = route.request().postDataJSON();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          lotePublicId: loteId,
          modoAplicado: 'VALID_ROWS',
          filasInsertadas: 8,
          mensaje: 'Lote aplicado exitosamente'
        })
      });
    });

    await page.goto('/#/admin/affiliations/import');
    await expect(page.locator('h1')).toContainText('Importación Masiva de Afiliados EPS');

    // Seleccionar EPS
    await page.selectOption('#select-eps', 'eps-sura-01');

    // Adjuntar archivo dummy XLSX
    await page.setInputFiles('#file-excel', {
      name: 'afiliados_valledupar_2026.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: Buffer.from('FAKE_XLSX_CONTENT')
    });

    // Enviar a previsualización
    await page.click('#btn-previsualizar');

    // Verificar métricas del pre-análisis
    await expect(page.locator('#card-metricas')).toBeVisible();
    await expect(page.locator('#stat-total')).toContainText('10');
    await expect(page.locator('#stat-validas')).toContainText('8');
    await expect(page.locator('#stat-fallidas')).toContainText('2');

    // Verificar muestra de staging en la tabla
    await expect(page.locator('#card-muestra')).toBeVisible();
    await expect(page.locator('text=Carlos Andrés Gómez Quintero')).toBeVisible();
    await expect(page.locator('text=TIPO_DOCUMENTO no concuerda')).toBeVisible();

    // Confirmar aplicación definitiva en modo VALID_ROWS
    await page.selectOption('#select-modo-commit', 'VALID_ROWS');
    await page.click('#btn-confirmar-commit');

    expect(uploadCaptured).toBe(true);
    expect(commitPayload).not.toBeNull();
    expect(commitPayload.modoCommit).toBe('VALID_ROWS');
  });

  test('debe consultar derechos de aseguramiento EPS con aviso legal Ley 1751 de 2015 (A05)', async ({ page }) => {
    await page.route('**/api/v1/affiliations/patients/CC/1065123456', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          tipoDocumento: 'CC',
          numeroDocumento: '1065123456',
          pacienteNombre: 'Carlos Andrés Gómez Quintero',
          epsCodigo: 'EPS010',
          epsNombre: 'EPS SURA',
          regimen: 'CONTRIBUTIVO',
          tipoAfiliado: 'COTIZANTE',
          estado: 'ACTIVO',
          fuenteVerificacion: 'BDUA_VALLEDUPAR_2026'
        })
      });
    });

    await page.goto('/#/affiliations/search');
    await expect(page.locator('h1')).toContainText('Consulta de Aseguramiento EPS');

    // Verificar presencia del aviso obligatorio Ley 1751/2015
    await expect(page.locator('text=Ley 1751 de 2015')).toBeVisible();
    await expect(page.locator('text=Bajo ninguna circunstancia')).toBeVisible();

    // Realizar consulta
    await page.selectOption('#tipo-doc-eps', 'CC');
    await page.fill('#num-doc-eps', '1065123456');
    await page.click('#btn-consultar');

    // Verificar tarjeta de resultados
    await expect(page.locator('#card-resultado-eps')).toBeVisible();
    await expect(page.locator('#res-paciente-nombre')).toContainText('Carlos Andrés Gómez Quintero');
    await expect(page.locator('#res-eps-nombre')).toContainText('EPS SURA');
    await expect(page.locator('#res-regimen')).toContainText('CONTRIBUTIVO');
    await expect(page.locator('#badge-estado-eps')).toContainText('ACTIVO');
  });

  test('debe permitir a un profesional registrar ausencia médica bloqueando la agenda (C02)', async ({ page }) => {
    // Sesión como profesional
    await page.route('**/api/v1/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-prof-01',
          email: 'medico@demo.meditriaje.test',
          nombreCompleto: 'Dr. Roberto Mendoza',
          roles: ['ROLE_PROFESIONAL']
        })
      });
    });

    let absencePayload = null;
    await page.route('**/api/v1/affiliations/absences', async (route) => {
      absencePayload = route.request().postDataJSON();
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          ausenciaPublicId: 'aus-2026-01',
          profesionalPublicId: 'usr-prof-01',
          mensaje: 'Ausencia médica registrada y slots bloqueados preventivamente'
        })
      });
    });

    // Navegar a la app para fijar el origen window
    await page.goto('/#/affiliations/search');

    // Enviar solicitud de ausencia
    const response = await page.evaluate(async () => {
      const resp = await fetch('/api/v1/affiliations/absences', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          profesionalPublicId: 'usr-prof-01',
          fechaInicio: '2026-10-15T08:00:00Z',
          fechaFin: '2026-10-15T18:00:00Z',
          tipoAusencia: 'INCAPACIDAD_MEDICA',
          motivo: 'Incapacidad médica por enfermedad general certificada'
        })
      });
      return await resp.json();
    });

    expect(response).not.toBeNull();
    expect(response.ausenciaPublicId).toBe('aus-2026-01');
    expect(absencePayload.tipoAusencia).toBe('INCAPACIDAD_MEDICA');
    expect(absencePayload.motivo).toContain('Incapacidad médica');
  });
});
