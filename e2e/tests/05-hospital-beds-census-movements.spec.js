// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Flujo Fase 3 (Lote H): Gestión Hospitalaria, Camas, Movimientos y Egresos
 * Cubre los requisitos del Plan Maestro:
 * - RF-008 / H01: Catálogo de áreas, salas, habitaciones y camas con estado en tiempo real.
 * - RF-009 / H02: Censo hospitalario interactivo con tasa de ocupación por pabellón.
 * - RF-010 / H03: Asignación atómica de cama con bloqueo ante concurrencia (409 Conflict).
 * - RF-011 / H04: Traslados longitudinales intrahospitalarios e historial inmutable.
 * - RF-012 / H05: Egreso hospitalario médico con epicrisis y pase a desinfección.
 */

test.describe('Fase 3: Gestión Hospitalaria, Camas, Movimientos y Egresos', () => {
  const staffEmail = process.env.DEMO_STAFF_EMAIL || 'medico@demo.meditriaje.test';
  const sedeId = 'sede-valledupar-01';

  test.beforeEach(async ({ page }) => {
    // Sesión activa con roles asistenciales
    await page.route('**/api/v1/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-medico-01',
          email: staffEmail,
          nombreCompleto: 'Dr. Alejandro Morales',
          roles: ['ROLE_PROFESIONAL', 'ROLE_ENFERMERIA']
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

  test('debe visualizar el censo hospitalario con métricas de capacidad y ocupación (H01, H02)', async ({ page }) => {
    // Mock censo y camas
    await page.route(`**/api/v1/hospital/census/${sedeId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sedePublicId: sedeId,
          sedeNombre: 'Sede Principal Valledupar',
          totalCamas: 20,
          ocupadas: 14,
          disponibles: 4,
          enLimpieza: 1,
          enMantenimiento: 1,
          tasaOcupacionPorcentaje: 70,
          areas: [
            {
              areaCodigo: 'OBS-URG',
              areaNombre: 'Observación Urgencias Adultos',
              tipoArea: 'OBSERVACION_URGENCIAS',
              totalCamas: 10,
              ocupadas: 8,
              disponibles: 2
            },
            {
              areaCodigo: 'HOSP-P3',
              areaNombre: 'Hospitalización Piso 3 Medicina Interna',
              tipoArea: 'HOSPITALIZACION_GENERAL',
              totalCamas: 10,
              ocupadas: 6,
              disponibles: 2
            }
          ]
        })
      });
    });

    await page.route(`**/api/v1/hospital/beds/${sedeId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            publicId: 'bed-101',
            codigo: 'CAMA-101',
            areaNombre: 'Observación Urgencias Adultos',
            habitacionCodigo: 'HAB-101',
            tipoCama: 'OBSERVACION',
            estado: 'DISPONIBLE',
            pacienteNombre: null
          },
          {
            publicId: 'bed-102',
            codigo: 'CAMA-102',
            areaNombre: 'Observación Urgencias Adultos',
            habitacionCodigo: 'HAB-101',
            tipoCama: 'OBSERVACION',
            estado: 'OCUPADA',
            pacienteNombre: 'Guillermo Mendoza'
          }
        ])
      });
    });

    await page.goto('/#/hospital/census');
    await expect(page.locator('h1')).toContainText('Centro de Control Hospitalario');

    // Verificar métricas del censo
    await expect(page.locator('#kpiTotalCamas')).toContainText('20');
    await expect(page.locator('#kpiOcupadas')).toContainText('14');
    await expect(page.locator('#kpiDisponibles')).toContainText('4');
    await expect(page.locator('#kpiTasaOcupacion')).toContainText('70%');

    // Verificar presencia de pabellones y tarjetas de camas
    await expect(page.locator('text=Observación Urgencias Adultos').first()).toBeVisible();
    await expect(page.locator('text=CAMA-101')).toBeVisible();
    await expect(page.locator('text=CAMA-102')).toBeVisible();
  });

  test('debe asignar atómicamente una cama disponible y rechazar si hay conflicto 409 (H03)', async ({ page }) => {
    const episodeId = 'ep-hosp-001';

    // Mock detalle episodio
    await page.route(`**/api/v1/emergency/episodes/${episodeId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          episodioPublicId: episodeId,
          pacienteNombre: 'Rosa Linda Maestre',
          pacienteDocumento: '49782190',
          sedePublicId: sedeId,
          estado: 'EN_TRIAJE'
        })
      });
    });

    // Sin movimientos iniciales
    await page.route(`**/api/v1/hospital/episodes/${episodeId}/movements`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    });

    // Camas disponibles
    await page.route(`**/api/v1/hospital/beds/${sedeId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            publicId: 'bed-disp-201',
            codigo: 'CAMA-201',
            areaNombre: 'Observación Urgencias Adultos',
            habitacionCodigo: 'HAB-201',
            estado: 'DISPONIBLE'
          }
        ])
      });
    });

    let assignPayload = null;
    let attemptCount = 0;

    await page.route(`**/api/v1/hospital/episodes/${episodeId}/beds/assign`, async (route) => {
      attemptCount++;
      assignPayload = route.request().postDataJSON();

      if (attemptCount === 1) {
        // Primer intento simula colisión concurrente (409 Conflict)
        await route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({
            codigo: 'ERROR_CAMA_NO_DISPONIBLE',
            mensaje: 'La cama seleccionada fue ocupada por otro usuario.'
          })
        });
      } else {
        // Segundo intento exitoso
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            mensaje: 'Cama asignada exitosamente'
          })
        });
      }
    });

    await page.goto(`/#/hospital/beds/${episodeId}`);
    await expect(page.locator('#formBedTitle')).toContainText('Asignación de Cama Hospitalaria');

    // Seleccionar cama y llenar motivo
    await page.waitForSelector('#selCamaDestino option[value="bed-disp-201"]', { state: 'attached' });
    await page.selectOption('#selCamaDestino', 'bed-disp-201');
    await page.fill('#txtMotivoCama', 'Ingreso para monitoreo hemodinámico estrecho');

    // Click inicial (simula 409)
    await page.click('#btnSubmitBedAction');
    await expect(page.locator('text=La cama seleccionada fue ocupada')).toBeVisible();

    // Reintentar envío exitoso
    await page.click('#btnSubmitBedAction');
    expect(assignPayload).not.toBeNull();
    expect(assignPayload.camaPublicId).toBe('bed-disp-201');
    expect(assignPayload.motivoAsignacion).toContain('monitoreo hemodinámico');
  });

  test('debe registrar un traslado longitudinal intrahospitalario entre pabellones (H04)', async ({ page }) => {
    const episodeId = 'ep-hosp-002';

    await page.route(`**/api/v1/emergency/episodes/${episodeId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          episodioPublicId: episodeId,
          pacienteNombre: 'Alfonso Beleño',
          sedePublicId: sedeId,
          camaAsignadaCodigo: 'CAMA-101',
          estado: 'EN_OBSERVACION'
        })
      });
    });

    // Simular que el paciente ya tiene 1 movimiento activo
    await page.route(`**/api/v1/hospital/episodes/${episodeId}/movements`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            publicId: 'mov-001',
            camaOrigenCodigo: null,
            camaDestinoCodigo: 'CAMA-101',
            areaDestinoNombre: 'Observación Urgencias',
            fechaIngreso: new Date(Date.now() - 7200000).toISOString(),
            motivo: 'Ingreso inicial desde triaje',
            esActual: true
          }
        ])
      });
    });

    await page.route(`**/api/v1/hospital/beds/${sedeId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            publicId: 'bed-uci-301',
            codigo: 'CAMA-UCI-01',
            areaNombre: 'Unidad de Cuidados Intensivos',
            habitacionCodigo: 'HAB-UCI',
            estado: 'DISPONIBLE'
          }
        ])
      });
    });

    let transferPayload = null;
    await page.route(`**/api/v1/hospital/episodes/${episodeId}/beds/transfer`, async (route) => {
      transferPayload = route.request().postDataJSON();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          mensaje: 'Traslado registrado exitosamente'
        })
      });
    });

    await page.goto(`/#/hospital/beds/${episodeId}`);

    // Debe mostrar título de Traslado Intrahospitalario
    await expect(page.locator('#formBedTitle')).toContainText('Traslado Intrahospitalario de Paciente');
    await expect(page.locator('#btnSubmitBedActionText')).toContainText('Confirmar Traslado');

    // Seleccionar cama UCI y justificar
    await page.waitForSelector('#selCamaDestino option[value="bed-uci-301"]', { state: 'attached' });
    await page.selectOption('#selCamaDestino', 'bed-uci-301');
    await page.fill('#txtMotivoCama', 'Deterioro ventilatorio progresivo que amerita soporte UCI');

    await page.click('#btnSubmitBedAction');

    expect(transferPayload).not.toBeNull();
    expect(transferPayload.camaDestinoPublicId).toBe('bed-uci-301');
    expect(transferPayload.motivoTraslado).toContain('soporte UCI');
  });

  test('debe tramitar el egreso hospitalario médico con epicrisis y destino de alta (H06)', async ({ page }) => {
    const episodeId = 'ep-hosp-003';

    await page.route(`**/api/v1/emergency/episodes/${episodeId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          episodioPublicId: episodeId,
          pacienteNombre: 'Beatriz Quintana',
          sedeNombre: 'Sede Principal Valledupar',
          camaAsignadaCodigo: 'CAMA-204',
          estado: 'HOSPITALIZADO'
        })
      });
    });

    let dischargePayload = null;
    await page.route(`**/api/v1/hospital/episodes/${episodeId}/discharge`, async (route) => {
      dischargePayload = route.request().postDataJSON();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          mensaje: 'Egreso hospitalario tramitado exitosamente'
        })
      });
    });

    await page.goto(`/#/hospital/discharge/${episodeId}`);
    await expect(page.locator('h1').first()).toContainText('Egreso y Alta Hospitalaria');

    // Diligenciar formulario de alta
    await page.selectOption('#selTipoDestino', 'ALTA_DOMICILIO');
    await page.fill('#txtDiagEgreso', 'Infección urinaria no especificada (N39.0) - Resuelta');
    await page.fill('#txtEpicrisis', 'Paciente completa esquema antibiótico intravenoso con resolución afebril y paraclínicos normalizados.');
    await page.fill('#txtPlanManejo', 'Continuar cefalexina oral por 3 días más. Control ambulatorio por medicina general en 7 días.');

    await page.click('#btnSubmitDischarge');

    expect(dischargePayload).not.toBeNull();
    expect(dischargePayload.tipoDestino).toBe('ALTA_DOMICILIO');
    expect(dischargePayload.diagnosticoEgreso).toContain('Infección urinaria');
    expect(dischargePayload.epicrisisResumen).toContain('esquema antibiótico');
  });
});
