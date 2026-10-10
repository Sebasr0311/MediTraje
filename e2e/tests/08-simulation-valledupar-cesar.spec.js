// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Simulación Integral Hospitalaria — Valledupar, Cesar, Colombia
 * Entorno: Hospital Rosario Pumarejo de López (Valledupar) y Red de Aseguramiento del Cesar.
 *
 * Flujo simulado de extremo a extremo:
 * 1. Admisión presencial de urgencias para paciente no identificado (NN) remitido desde la Glorieta María Mulata.
 * 2. Valoración presencial de triaje humano Nivel II (Res. 5596 de 2015) en urgencias.
 * 3. Censo hospitalario en tiempo real con pabellones locales (Pabellón Guatapurí y Observación Urgencias).
 * 4. Consulta de derechos y aseguramiento en Dusakawi EPSI (Valledupar) bajo Ley Estatutaria 1751 de 2015.
 * 5. Centro de mando analítico y verificación de código QR para seguimiento de familiares en sala de espera.
 */

test.describe('Simulación Clínica y Operativa — Valledupar, Cesar (Hospital Rosario Pumarejo de López)', () => {
  const staffEmail = 'enfermera.urgencias@hrpl.cesar.gov.co';
  const sedeValledupar = 'sede-hrpl-valledupar-01';
  const episodioSimuladoId = 'ep-valledupar-2026-001';
  const qrTokenValledupar = 'qr-valledupar-hrpl-8849';

  test.beforeEach(async ({ page }) => {
    page.on('dialog', async (dialog) => {
      await dialog.accept();
    });

    // Sesión activa autorizada
    await page.route('**/api/v1/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-enfermera-valledupar',
          email: staffEmail,
          nombreCompleto: 'Lic. Carmen Rosa Baute — Urgencias Valledupar',
          roles: ['ROLE_ENFERMERIA', 'ROLE_ADMINISTRADOR']
        })
      });
    });

    // Sedes de salud de Valledupar
    await page.route('**/api/v1/admin/sites', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            publicId: sedeValledupar,
            nombre: 'Hospital Rosario Pumarejo de López — Sede Central Valledupar',
            ciudad: 'Valledupar, Cesar'
          }
        ])
      });
    });

    // Catálogo de EPS en Valledupar
    await page.route('**/api/v1/affiliations/eps', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { publicId: 'eps-dusakawi', codigoMinSalud: 'EPSI01', nombre: 'DUSAKAWI EPSI (Valledupar / Cesar)' },
          { publicId: 'eps-cajacopi', codigoMinSalud: 'CCF055', nombre: 'CAJACOPI EPS' },
          { publicId: 'eps-coosalud', codigoMinSalud: 'ESS024', nombre: 'COOSALUD EPS' }
        ])
      });
    });
  });

  test('Paso 1: Admisión presencial de urgencias para paciente no identificado (NN) en Valledupar', async ({ page }) => {
    let admisionEnviada = null;

    await page.route('**/api/v1/emergency/admissions', async (route) => {
      admisionEnviada = route.request().postDataJSON();
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          episodioPublicId: episodioSimuladoId,
          codigoProvisional: 'NN-VPAR-2026-042',
          esNN: true,
          estado: 'REGISTRADO',
          mensaje: 'Paciente provisional admitido en Urgencias Valledupar con código opaco'
        })
      });
    });

    await page.goto('/#/nursing/admission');
    await expect(page.locator('h1')).toContainText('Admisión Presencial de Urgencias');

    // Seleccionar sede Valledupar
    await page.waitForSelector(`#admSedeSelect option[value="${sedeValledupar}"]`, { state: 'attached' });
    await page.selectOption('#admSedeSelect', sedeValledupar);

    // Marcar como paciente sin identificación hallado en vía pública de Valledupar
    const chkNN = page.locator('#chkEsNN');
    await chkNN.check();

    await expect(page.locator('#bloquePacienteNN')).toBeVisible();

    // Rellenar datos descriptivos del paciente hallado en la Glorieta María Mulata
    await page.fill('#txtEdadAparente', '35');
    await page.selectOption('#selGeneroAparente', 'MASCULINO');
    await page.selectOption('#selCondicionLlegada', 'DESORIENTADO');
    await page.fill('#txtDescripcionFisica', 'Cicatriz en ceja izquierda, vestimenta azul, hallado en Glorieta María Mulata');

    await page.selectOption('#selViaIngreso', 'AMBULANCIA');
    await page.fill('#txtAcompananteNombre', 'Paramédico CRUE Cesar');
    await page.fill('#txtAcompananteTelefono', '3009876543');
    await page.fill('#txtMotivoConsulta', 'Paciente hallado en vía pública en Valledupar con traumatismo craneoencefálico');

    await page.click('#btnSubmitAdmission');

    expect(admisionEnviada).not.toBeNull();
    expect(admisionEnviada.esIdentidadProvisional).toBe(true);
    expect(admisionEnviada.motivoConsulta).toContain('Valledupar');
  });

  test('Paso 2: Valoración presencial de triaje humano Nivel II (Res. 5596) en Urgencias Valledupar', async ({ page }) => {
    let triajeEnviado = null;

    await page.route(`**/api/v1/emergency/episodes/${episodioSimuladoId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: episodioSimuladoId,
          pacienteNombre: 'Paciente NN Valledupar (NN-VPAR-2026-042)',
          pacienteDocumento: 'SIN IDENTIFICAR',
          edad: 35,
          motivoConsulta: 'Politraumatismo con hipotensión y compromiso neurológico',
          viaIngreso: 'AMBULANCIA',
          estado: 'REGISTRADO'
        })
      });
    });

    await page.route(`**/api/v1/emergency/episodes/${episodioSimuladoId}/triage-assessments`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
      } else {
        triajeEnviado = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            valoracionPublicId: 'trj-valledupar-001',
            episodioPublicId: episodioSimuladoId,
            nivelAsignado: 'II',
            requiereReevaluacion: false,
            mensaje: 'Triaje Nivel II registrado exitosamente'
          })
        });
      }
    });

    await page.goto(`/#/nursing/assessment/${episodioSimuladoId}`);
    await expect(page.locator('#formAssessmentTitle')).toContainText('Registro de Valoración de Triaje');

    // Nivel II (Urgencia Vital / Prioridad Naranja)
    const level2Card = page.locator('.select-triage-level[data-level="II"]');
    await level2Card.click();

    // Signos vitales críticos
    await page.fill('#txtPA', '85/55');
    await page.fill('#txtFC', '122');
    await page.fill('#txtFR', '24');
    await page.fill('#txtSO2', '91');
    await page.fill('#txtTemp', '38.8');
    await page.fill('#txtGlasgow', '11');
    await page.fill('#txtHallazgos', 'Hipotensión marcada, taquicardia refleja y obnubilación en paciente de Valledupar');

    await page.click('button[type="submit"]');

    expect(triajeEnviado).not.toBeNull();
    expect(triajeEnviado.nivel).toBe('II');
    expect(triajeEnviado.presionArterial).toBe('85/55');
    expect(triajeEnviado.escalaGlasgow).toBe(11);
  });

  test('Paso 3: Censo hospitalario con pabellones locales (Pabellón Guatapurí) en Valledupar', async ({ page }) => {
    await page.route(`**/api/v1/hospital/census/${sedeValledupar}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sedePublicId: sedeValledupar,
          sedeNombre: 'Hospital Rosario Pumarejo de López — Valledupar',
          totalCamas: 120,
          ocupadas: 84,
          disponibles: 36,
          enLimpieza: 0,
          enMantenimiento: 0,
          tasaOcupacionPorcentaje: 70,
          areas: [
            {
              areaCodigo: 'OBS-URG',
              areaNombre: 'Observación Urgencias Adultos',
              tipoArea: 'OBSERVACION_URGENCIAS',
              totalCamas: 40,
              ocupadas: 32,
              disponibles: 8
            },
            {
              areaCodigo: 'PAB-GUA',
              areaNombre: 'Pabellón Guatapurí (Hospitalización General)',
              tipoArea: 'HOSPITALIZACION_GENERAL',
              totalCamas: 60,
              ocupadas: 42,
              disponibles: 18
            },
            {
              areaCodigo: 'UCI-ADU',
              areaNombre: 'UCI Adultos San Juan del Cesar',
              tipoArea: 'UCI_ADULTOS',
              totalCamas: 20,
              ocupadas: 10,
              disponibles: 10
            }
          ]
        })
      });
    });

    await page.route(`**/api/v1/hospital/beds/${sedeValledupar}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            publicId: 'bed-val-01',
            codigo: 'URG-OBS-01',
            areaNombre: 'Observación Urgencias Adultos',
            estado: 'DISPONIBLE',
            pacienteNombre: null
          },
          {
            publicId: 'bed-val-02',
            codigo: 'GUA-HOSP-04',
            areaNombre: 'Pabellón Guatapurí (Hospitalización General)',
            estado: 'OCUPADA',
            pacienteNombre: 'Hermes José Maestre'
          }
        ])
      });
    });

    await page.goto('/#/hospital/census');
    await expect(page.locator('h1')).toContainText('Centro de Control Hospitalario');

    // Visualizar métricas del censo de Valledupar
    await expect(page.locator('#kpiTotalCamas')).toHaveText('120');
    await expect(page.locator('#kpiOcupadas')).toHaveText('84');
    await expect(page.locator('#kpiDisponibles')).toHaveText('36');
    await expect(page.locator('#kpiTasaOcupacion')).toHaveText('70%');
    await expect(page.locator('#areaSummaryGrid').getByText('Pabellón Guatapurí')).toBeVisible();
  });

  test('Paso 4: Consulta de derechos en Dusakawi EPSI Valledupar bajo Ley 1751 de 2015', async ({ page }) => {
    await page.route('**/api/v1/affiliations/patients/CC/1065890123', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          afiliadoPublicId: 'af-vpar-009',
          tipoDocumento: 'CC',
          numeroDocumento: '1065890123',
          pacienteNombre: 'Hermes José Maestre Epieyú',
          epsCodigo: 'EPSI01',
          epsNombre: 'DUSAKAWI EPSI (Valledupar / Cesar)',
          regimen: 'SUBSIDIADO',
          tipoAfiliado: 'COTIZANTE',
          fuenteVerificacion: 'BASE DUSAKAWI CESAR / BDUA',
          estado: 'ACTIVO',
          derechoAtencionUrgenciasGarantizado: true
        })
      });
    });

    await page.goto('/#/affiliations/search');
    await expect(page.locator('h1')).toContainText('Consulta de Aseguramiento EPS');

    await page.selectOption('#tipo-doc-eps', 'CC');
    await page.fill('#num-doc-eps', '1065890123');
    await page.click('#btn-consultar');

    // Verificar datos de Dusakawi EPSI y aviso legal MinSalud
    await expect(page.locator('#card-resultado-eps')).toBeVisible();
    await expect(page.locator('#res-eps-nombre')).toContainText('DUSAKAWI EPSI');
    await expect(page.locator('#res-paciente-nombre')).toContainText('Hermes José Maestre');
    await expect(page.locator('#badge-estado-eps')).toHaveText('ACTIVO');
    await expect(page.locator('text=Ley 1751 de 2015')).toBeVisible();
  });

  test('Paso 5: Seguimiento intrahospitalario por código QR seguro para familiares en sala de espera', async ({ page }) => {
    await page.route('**/api/v1/operational/dashboard*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sedePublicId: sedeValledupar,
          totalCamas: 120,
          camasOcupadas: 84,
          camasDisponibles: 36,
          tasaOcupacionPorcentaje: 70,
          episodiosUrgenciasActivos: 18,
          tiemposPromedioEsperaMinutos: { 'I': 0, 'II': 14, 'III': 45, 'IV': 80, 'V': 110 },
          alertasActivas: []
        })
      });
    });

    await page.route(`**/api/v1/operational/tracking-qr/${qrTokenValledupar}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          tokenQr: qrTokenValledupar,
          codigoIdentidadProvisional: 'NN-VPAR-2026-042',
          ubicacionActual: 'Pabellón Guatapurí — Cama GUA-HOSP-04',
          nivelTriaje: 'II',
          estado: 'ACTIVO'
        })
      });
    });

    await page.goto('/#/operational/dashboard');
    await expect(page.locator('h1')).toContainText('Centro de Mando y Analítica Hospitalaria');

    // Ingresar token de Valledupar
    await page.fill('#input-token-qr', qrTokenValledupar);
    await page.click('#form-scan-qr button[type="submit"]');

    // Comprobar información mostrada a familiares
    await expect(page.locator('#resultado-qr-track')).toBeVisible();
    await expect(page.locator('#qr-ubicacion')).toContainText('Pabellón Guatapurí');
    await expect(page.locator('#qr-nivel-triaje')).toContainText('Triaje II');
    await expect(page.locator('#qr-id-prov')).toContainText('NN-VPAR-2026-042');
  });
});
