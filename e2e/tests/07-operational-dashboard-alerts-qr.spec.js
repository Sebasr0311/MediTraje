// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Flujo Fase 5 (Lote O): Centro de Mando, Analítica Hospitalaria, Alertas y QR
 * Cubre los requisitos del Plan Maestro:
 * - RF-018 / O01: Centro de mando con KPIs en tiempo real y tiempos Res 5596/2015.
 * - RF-019 / O02: Motor de alertas operativas con reconocimiento auditado (ACK).
 * - RF-020 / O03: Seguimiento mediante código QR seguro sin exposición de datos clínicos.
 */

test.describe('Fase 5: Centro de Mando Operativo, Analítica, Alertas y QR', () => {
  const staffEmail = process.env.DEMO_ADMIN_EMAIL || 'admin@demo.meditriaje.test';
  const sedeId = 'sede-valledupar-01';

  test.beforeEach(async ({ page }) => {
    // Sesión activa autorizada
    await page.route('**/api/v1/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-admin-01',
          email: staffEmail,
          nombreCompleto: 'Director Operativo Valledupar',
          roles: ['ROLE_ADMINISTRADOR', 'ROLE_PROFESIONAL']
        })
      });
    });

    // Catálogo de sedes
    await page.route('**/api/v1/admin/sites', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { publicId: sedeId, nombre: 'Sede Principal Valledupar', ciudad: 'Valledupar' }
        ])
      });
    });
  });

  test('debe visualizar el centro de mando operativo con métricas de capacidad y tiempos Res 5596 (O01)', async ({ page }) => {
    await page.route('**/api/v1/operational/dashboard**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sedePublicId: sedeId,
          totalCamas: 50,
          camasOcupadas: 42,
          camasDisponibles: 8,
          tasaOcupacionPorcentaje: 84,
          episodiosUrgenciasActivos: 18,
          tiemposPromedioEsperaMinutos: {
            'I': 0,
            'II': 14,
            'III': 38,
            'IV': 95,
            'V': 140
          },
          alertasActivas: []
        })
      });
    });

    await page.goto('/#/operational/dashboard');
    await expect(page.locator('h1')).toContainText('Centro de Mando y Analítica Hospitalaria');

    // Verificar KPIs de ocupación y urgencias
    await expect(page.locator('#kpi-tasa-ocupacion')).toContainText('84%');
    await expect(page.locator('#kpi-pacientes-urgencias')).toContainText('18');
    await expect(page.locator('#kpi-camas-disponibles')).toContainText('8');

    // Verificar tiempos de espera normativos Res 5596
    await expect(page.locator('#tiempo-t1')).toContainText('0 min');
    await expect(page.locator('#tiempo-t2')).toContainText('14 min');
    await expect(page.locator('#tiempo-t3')).toContainText('38 min');
  });

  test('debe gestionar alertas de saturación y permitir su reconocimiento auditado (O02)', async ({ page }) => {
    const alertaId = 'alt-saturacion-001';
    let ackCaptured = false;
    let ackPayload = null;

    // Manejar diálogo prompt y alert en Playwright
    page.on('dialog', async (dialog) => {
      if (dialog.type() === 'prompt') {
        await dialog.accept('Habilitadas 4 camillas de expansión en sala de observación');
      } else {
        await dialog.accept();
      }
    });

    await page.route('**/api/v1/operational/dashboard**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sedePublicId: sedeId,
          totalCamas: 30,
          camasOcupadas: 28,
          camasDisponibles: 2,
          tasaOcupacionPorcentaje: 93,
          episodiosUrgenciasActivos: 25,
          tiemposPromedioEsperaMinutos: { 'I': 0, 'II': 45, 'III': 110, 'IV': 210, 'V': 290 },
          alertasActivas: ackCaptured ? [] : [
            {
              publicId: alertaId,
              sedePublicId: sedeId,
              tipoAlerta: 'SATURACION_CAMAS',
              nivelSeveridad: 'CRITICA',
              mensaje: 'Capacidad de camas al 93%. Superado umbral de seguridad asistencial.',
              creadoAt: new Date().toISOString(),
              estado: 'ACTIVA'
            }
          ]
        })
      });
    });

    await page.route(`**/api/v1/operational/alerts/${alertaId}/acknowledge`, async (route) => {
      ackCaptured = true;
      ackPayload = route.request().postDataJSON();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          alertaPublicId: alertaId,
          estado: 'RECONOCIDA',
          mensaje: 'Alerta reconocida exitosamente'
        })
      });
    });

    await page.goto('/#/operational/dashboard');

    // Verificar presencia del banner de alerta crítica
    await expect(page.locator('#banner-alerta-critica')).toBeVisible();
    await expect(page.locator('text=Superado umbral de seguridad')).toBeVisible();

    // Accionar botón Reconocer (ACK)
    const btnAck = page.locator('.btn-ack-alerta').first();
    await expect(btnAck).toBeVisible();
    await btnAck.click();

    // Verificar que se envió el acuse de recibo
    expect(ackCaptured).toBe(true);
    expect(ackPayload).not.toBeNull();
    expect(ackPayload.motivo).toContain('camillas de expansión');
  });

  test('debe consultar token de seguimiento QR mostrando ubicación y nivel sin filtrar datos clínicos (O03)', async ({ page }) => {
    const qrToken = 'trk-token-safe-9921';

    await page.route('**/api/v1/operational/dashboard**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sedePublicId: sedeId,
          totalCamas: 20,
          camasOcupadas: 10,
          camasDisponibles: 10,
          tasaOcupacionPorcentaje: 50,
          episodiosUrgenciasActivos: 5,
          alertasActivas: []
        })
      });
    });

    // Mock consulta segura del token QR (cero diagnóstico ni notas médicas en payload)
    await page.route(`**/api/v1/operational/tracking-qr/${qrToken}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          tokenQr: qrToken,
          codigoIdentidadProvisional: 'NN-774102',
          ubicacionActual: 'Pabellón Urgencias - Cama 104',
          nivelTriaje: 'II',
          estadoEpisodio: 'EN_OBSERVACION',
          fechaIngreso: new Date(Date.now() - 3600000).toISOString()
        })
      });
    });

    await page.goto('/#/operational/dashboard');

    // Ingresar token QR en el verificador de cabecera
    await page.fill('#input-token-qr', qrToken);
    await page.click('#form-scan-qr button[type="submit"]');

    // Verificar resultado
    await expect(page.locator('#resultado-qr-track')).toBeVisible();
    await expect(page.locator('#qr-id-prov')).toContainText('NN-774102');
    await expect(page.locator('#qr-ubicacion')).toContainText('Pabellón Urgencias - Cama 104');
    await expect(page.locator('#qr-nivel-triaje')).toContainText('Triaje II');
    await expect(page.locator('#qr-token-val')).toContainText(qrToken);

    // Asegurar que NO se expone ningún campo de diagnóstico clínico en la tarjeta
    await expect(page.locator('#resultado-qr-track')).not.toContainText('diagnostico');
    await expect(page.locator('#resultado-qr-track')).not.toContainText('CIE-10');
  });
});
