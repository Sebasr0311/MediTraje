// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Flujo Fase 2 (Lote U): Enfermería y Circuito de Urgencias Presenciales
 * Cubre los requisitos del Plan Maestro:
 * - RF-001 / U01: Experiencia y rol de enfermería (ROLE_ENFERMERIA).
 * - RF-002 / U02: Admisión presencial de urgencias sin cuenta previa ni bloqueo administrativo.
 * - RF-003 / U03: Identidad provisional para paciente indocumentado (NN-XXXXXX).
 * - RF-004 / U03: Identificación posterior y trazabilidad de paciente provisional.
 * - RF-005 / U04: Valoración presencial de triaje humano I-V con signos vitales y Glasgow.
 * - RF-006 / U04: Múltiples reevaluaciones dinámicas inmutables append-only.
 * - RF-007 / U05: Cola priorizada de urgencias por sede en tiempo real.
 */

test.describe('Fase 2: Circuito de Enfermería, Urgencias y Triaje Presencial', () => {
  const nurseEmail = process.env.DEMO_NURSE_EMAIL || 'enfermera@demo.meditriaje.test';
  const password = process.env.DEMO_PASSWORD || 'DemoSecretPass123!';

  test.beforeEach(async ({ page }) => {
    // Interceptor inteligente de APIs para sesión de enfermería persistente
    await page.route('**/api/v1/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-enfermera-01',
          email: nurseEmail,
          nombreCompleto: 'Enfermera Asistencial Turno Urgencias',
          roles: ['ROLE_ENFERMERIA']
        })
      });
    });

    await page.route('**/api/v1/admin/sites', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { publicId: 'sede-valledupar-01', nombre: 'Sede Principal Valledupar', ciudad: 'Valledupar' }
        ])
      });
    });

    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-enfermera-01',
          email: nurseEmail,
          nombreCompleto: 'Enfermera Asistencial Turno Urgencias',
          roles: ['ROLE_ENFERMERIA']
        })
      });
    });
  });

  test('debe autenticarse como enfermería y visualizar el Centro de Urgencias con métricas', async ({ page }) => {
    let isUserLoggedIn = false;
    await page.route('**/api/v1/auth/refresh', async (route) => {
      if (!isUserLoggedIn) {
        await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ message: 'No session' }) });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            publicId: 'usr-enfermera-01',
            email: nurseEmail,
            nombreCompleto: 'Enfermera Asistencial Turno Urgencias',
            roles: ['ROLE_ENFERMERIA']
          })
        });
      }
    });

    await page.route('**/api/v1/auth/login', async (route) => {
      isUserLoggedIn = true;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-enfermera-01',
          email: nurseEmail,
          nombreCompleto: 'Enfermera Asistencial',
          roles: ['ROLE_ENFERMERIA']
        })
      });
    });

    await page.route('**/api/v1/emergency/queue/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sedeId: 'sede-valledupar-01',
          totalEnEspera: 2,
          criticos: 1,
          pendientesTriaje: 1,
          pacientesNN: 1,
          items: [
            {
              episodioId: 'ep-001',
              fechaIngreso: new Date().toISOString(),
              pacienteNombre: 'Paciente NN (NN-849201)',
              esIdentidadProvisional: true,
              codigoProvisional: 'NN-849201',
              motivoIngreso: 'Accidente de tránsito en moto, inconsciente',
              nivelTriaje: 'I',
              minutosEspera: 5,
              estado: 'EN_TRIAJE',
              medicoAsignado: null
            },
            {
              episodioId: 'ep-002',
              fechaIngreso: new Date().toISOString(),
              pacienteNombre: 'Carlos Andrés Gómez',
              esIdentidadProvisional: false,
              documento: '1065123456',
              motivoIngreso: 'Dolor abdominal cólico difuso',
              nivelTriaje: 'III',
              minutosEspera: 45,
              estado: 'REGISTRADO',
              medicoAsignado: null
            }
          ]
        })
      });
    });

    await page.goto('/#/login');
    await expect(page.locator('#formLogin')).toBeVisible();
    await page.fill('#loginEmail', nurseEmail);
    await page.fill('#loginPassword', password);
    await page.click('#formLogin button[type="submit"]');

    // Debe dirigir al panel de enfermería
    await expect(page).toHaveURL(/.*#\/nursing/);
    await expect(page.locator('h1')).toContainText('Centro de Urgencias y Triaje');

    // Verificar tarjetas de KPIs de urgencias
    await expect(page.locator('#kpiTotal')).toContainText('2');
    await expect(page.locator('#kpiCriticos')).toContainText('1');
    await expect(page.locator('#kpiNN')).toContainText('1');

    // Verificar que la tabla muestra las filas de la cola
    const queueTable = page.locator('#urgencyQueueTable');
    await expect(queueTable).toBeVisible();
    await expect(page.locator('text=NN-849201').first()).toBeVisible();
    await expect(page.locator('text=Carlos Andrés Gómez')).toBeVisible();
  });

  test('debe admitir un paciente indocumentado NN con código provisional opaco (U02, U03)', async ({ page }) => {
    let admissionPayload = null;
    await page.route('**/api/v1/emergency/admissions', async (route) => {
      admissionPayload = route.request().postDataJSON();
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          episodioPublicId: 'ep-nn-003',
          codigoProvisional: 'NN-991204',
          esNN: true,
          estado: 'REGISTRADO',
          mensaje: 'Paciente provisional admitido exitosamente'
        })
      });
    });

    await page.goto('/#/nursing/admission');
    await expect(page.locator('h1')).toContainText('Admisión Presencial de Urgencias');

    // Esperar y seleccionar sede
    await page.waitForSelector('#admSedeSelect option[value="sede-valledupar-01"]', { state: 'attached' });
    await page.selectOption('#admSedeSelect', 'sede-valledupar-01');

    // Activar switch de paciente no identificado (NN)
    const chkNN = page.locator('#chkEsNN');
    await chkNN.check();

    // Comprobar que el bloque NN se hace visible y se oculta la búsqueda civil
    await expect(page.locator('#bloquePacienteNN')).toBeVisible();
    await expect(page.locator('#bloquePacienteCivil')).toBeHidden();

    // Rellenar datos estimativos del paciente NN
    await page.fill('#txtEdadAparente', '45');
    await page.selectOption('#selGeneroAparente', 'MASCULINO');
    await page.selectOption('#selCondicionLlegada', 'DESORIENTADO');
    await page.fill('#txtDescripcionFisica', 'Cicatriz en ceja izquierda, pantalón de mezclilla azul');

    // Vía de ingreso y motivo de urgencia
    await page.selectOption('#selViaIngreso', 'AMBULANCIA');
    await page.fill('#txtAcompananteNombre', 'Paramédico Defensa Civil');
    await page.fill('#txtAcompananteTelefono', '3009876543');
    await page.fill('#txtMotivoConsulta', 'Paciente hallado en vía pública con traumatismo craneoencefálico leve y confusión');

    // Enviar admisión
    await page.click('#btnSubmitAdmission');

    // Verificar que el payload contiene la bandera esNN y los datos
    expect(admissionPayload).not.toBeNull();
    expect(admissionPayload.esIdentidadProvisional).toBe(true);
    expect(admissionPayload.motivoConsulta).toContain('traumatismo craneoencefálico');
    expect(admissionPayload.edadAparente).toBe(45);
  });

  test('debe realizar la valoración de triaje presencial humano I-V con signos vitales y Glasgow (U04)', async ({ page }) => {
    const episodeId = 'ep-test-val-01';

    // Mock detalle del episodio
    await page.route(`**/api/v1/emergency/episodes/${episodeId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: episodeId,
          pacienteNombre: 'María Elena Restrepo',
          pacienteDocumento: '39485762',
          edad: 52,
          motivoConsulta: 'Dolor precordial opresivo con irradiación a brazo izquierdo',
          viaIngreso: 'ESPONTANEO',
          estado: 'REGISTRADO'
        })
      });
    });

    await page.route(`**/api/v1/emergency/episodes/${episodeId}/triage-assessments`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([])
        });
      } else {
        assessmentPayload = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            valoracionPublicId: 'val-001',
            episodioPublicId: episodeId,
            nivelAsignado: 'II',
            requiereReevaluacion: false,
            mensaje: 'Valoración registrada exitosamente'
          })
        });
      }
    });

    let assessmentPayload = null;

    await page.goto(`/#/nursing/assessment/${episodeId}`);
    await expect(page.locator('#formAssessmentTitle')).toContainText('Registro de Valoración de Triaje');

    // Seleccionar Nivel II (Emergencia)
    const level2Card = page.locator('.select-triage-level[data-level="II"]');
    await level2Card.click();

    // Rellenar signos vitales completos
    await page.fill('#txtPA', '150/95');
    await page.fill('#txtFC', '102');
    await page.fill('#txtFR', '24');
    await page.fill('#txtSO2', '93');
    await page.fill('#txtTemp', '37.1');
    await page.fill('#txtGlasgow', '15');

    // Hallazgos clínicos
    await page.fill('#txtHallazgos', 'Diaforesis profusa, palidez mucocutánea y dolor torácico retroesternal EVA 8/10');

    // Guardar valoración
    await page.click('#btnSubmitAssessment');

    // Verificar datos enviados en el payload
    expect(assessmentPayload).not.toBeNull();
    expect(assessmentPayload.nivel).toBe('II');
    expect(assessmentPayload.presionArterial).toBe('150/95');
    expect(assessmentPayload.frecuenciaCardiaca).toBe(102);
    expect(assessmentPayload.escalaGlasgow).toBe(15);
  });

  test('debe permitir la reevaluación dinámica de un paciente ya clasificado (RF-006 / U04)', async ({ page }) => {
    const episodeId = 'ep-test-reeval-02';

    // Mock episodio con valoración previa existente
    await page.route(`**/api/v1/emergency/episodes/${episodeId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: episodeId,
          pacienteNombre: 'Jorge Iván Arboleda',
          motivoConsulta: 'Cefalea intensa y mareo',
          estado: 'EN_TRIAJE'
        })
      });
    });

    // Simular que ya existe 1 valoración previa en el historial inmutable
    await page.route(`**/api/v1/emergency/episodes/${episodeId}/triage-assessments`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            publicId: 'val-prev-01',
            nivel: 'III',
            evaluadorNombre: 'Enfermera Turno Mañana',
            fechaRegistro: new Date(Date.now() - 3600000).toISOString(),
            presionArterial: '130/85',
            frecuenciaCardiaca: 82,
            escalaGlasgow: 15,
            hallazgosClinicos: 'Valoración inicial de ingreso'
          }
        ])
      });
    });

    await page.goto(`/#/nursing/assessment/${episodeId}`);

    // Comprobar que detecta automáticamente que es una REEVALUACIÓN clínica (Versión 2)
    await expect(page.locator('#bloqueReevaluacion')).toBeVisible();
    await expect(page.locator('#formAssessmentTitle')).toContainText('Reevaluación Clínica de Urgencias (Versión 2)');

    // El campo de motivo de cambio debe estar presente
    await expect(page.locator('#txtMotivoReevaluacion')).toBeVisible();
  });
});
