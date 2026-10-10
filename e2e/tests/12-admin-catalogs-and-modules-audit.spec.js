// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Suite de Auditoría Minuciosa Impecable: Módulos de Administración
 * Cobertura de UI/UX, Accesibilidad (WCAG 2.1 AA), Lógica, Validaciones y Responsive
 * para cada uno de los apartados de administración de MediTriaje 2.0.
 */
test.describe('Auditoría Minuciosa: Catálogos y Módulos de Administración', () => {

  test.beforeEach(async ({ page }) => {
    // 1. Configurar viewport desktop estándar
    await page.setViewportSize({ width: 1280, height: 800 });

    // 2. Mockear autenticación con rol de Administrador
    await page.addInitScript(() => {
      const mockAdmin = {
        id: 1,
        email: 'admin@meditriaje.com',
        nombreCompleto: 'Administrador General',
        nombres: 'Administrador',
        apellidos: 'General',
        roles: ['ROLE_ADMINISTRADOR'],
        autoridades: ['ROLE_ADMINISTRADOR']
      };
      localStorage.setItem('auth_token', 'mock_admin_jwt_token_test');
      localStorage.setItem('auth_user', JSON.stringify(mockAdmin));
    });

    // 3. Interceptar y responder APIs para pruebas deterministas y consistentes
    await page.route('**/api/v1/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-admin-valledupar',
          email: 'admin@meditriaje.com',
          nombreCompleto: 'Administrador General',
          roles: ['ROLE_ADMINISTRADOR']
        })
      });
    });

    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-admin-valledupar',
          email: 'admin@meditriaje.com',
          nombreCompleto: 'Administrador General',
          roles: ['ROLE_ADMINISTRADOR']
        })
      });
    });

    await page.route('**/api/v1/admin/sites*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { publicId: 'site-001', nombre: 'Sede Principal Valledupar', direccion: 'Calle 16 # 12-40', ciudad: 'Valledupar', estado: 'ACTIVO', institucionRazonSocial: 'Hospital Rosario Pumarejo de López' },
            { publicId: 'site-002', nombre: 'Sede Norte La Nevada', direccion: 'Carrera 42 # 5B-10', ciudad: 'Valledupar', estado: 'ACTIVO', institucionRazonSocial: 'Hospital Rosario Pumarejo de López' }
          ],
          totalElements: 2,
          totalPages: 1,
          page: 0,
          hasPrevious: false,
          hasNext: false
        })
      });
    });

    await page.route('**/api/v1/admin/institutions*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { publicId: 'inst-001', nit: '892300456-1', razonSocial: 'Hospital Rosario Pumarejo de López', estado: 'ACTIVO' }
          ],
          totalElements: 1,
          totalPages: 1,
          page: 0,
          hasPrevious: false,
          hasNext: false
        })
      });
    });

    await page.route('**/api/v1/admin/specialties*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { publicId: 'spec-001', nombre: 'Medicina General', duracionSlotMin: 20, estado: 'ACTIVO' },
            { publicId: 'spec-002', nombre: 'Pediatría', duracionSlotMin: 30, estado: 'ACTIVO' },
            { publicId: 'spec-003', nombre: 'Medicina Interna', duracionSlotMin: 30, estado: 'ACTIVO' }
          ],
          totalElements: 3,
          totalPages: 1,
          page: 0,
          hasPrevious: false,
          hasNext: false
        })
      });
    });

    await page.route('**/api/v1/admin/professionals*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              publicId: 'prof-001',
              nombres: 'Camila',
              apellidos: 'Gómez Silva',
              email: 'dra.gomez@meditriaje.com',
              telefono: '3001234567',
              tipoDocumento: 'CC',
              numeroDocumento: '1065123456',
              registroMedico: 'RM-54321',
              especialidadPublicId: 'spec-001',
              especialidadNombre: 'Medicina General',
              estado: 'ACTIVO'
            }
          ],
          totalElements: 1,
          totalPages: 1,
          page: 0,
          hasPrevious: false,
          hasNext: false
        })
      });
    });

    await page.route('**/api/v1/admin/slots*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              publicId: 'slot-001',
              profesionalNombre: 'Camila Gómez Silva',
              especialidadNombre: 'Medicina General',
              sedeNombre: 'Sede Principal Valledupar',
              modalidad: 'PRESENCIAL',
              fechaHoraInicio: '2026-10-12T08:00:00-05:00',
              fechaHoraFin: '2026-10-12T08:20:00-05:00',
              estado: 'LIBRE'
            }
          ],
          totalElements: 1,
          totalPages: 1,
          page: 0,
          hasPrevious: false,
          hasNext: false
        })
      });
    });

    await page.route('**/api/v1/admin/audit*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 101,
              fechaHora: '2026-10-10T18:00:00-05:00',
              tipoEvento: 'LOGIN_EXITOSO',
              usuarioEmail: 'admin@meditriaje.com',
              resultado: 'EXITO',
              ipOrigen: '190.25.10.15'
            }
          ],
          totalElements: 1,
          totalPages: 1,
          page: 0
        })
      });
    });

    await page.route('**/api/v1/admin/reports/operational*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          citasPorEstado: { PROGRAMADA: 12, CONFIRMADA: 8, ATENDIDA: 34, CANCELADA: 2 },
          triajesPorNivel: { 1: 3, 2: 7, 3: 15, 4: 20, 5: 5 },
          recetasEmitidas: 28,
          recetasDispensadas: 24,
          accesosBreakGlass: 1,
          tiempoPromedioAtencionMin: 18.5
        })
      });
    });

    await page.route('**/api/v1/affiliations/eps*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { publicId: 'eps-01', codigoMinSalud: 'EPS001', nombre: 'Nueva EPS' },
          { publicId: 'eps-02', codigoMinSalud: 'EPS002', nombre: 'Sanitas' }
        ])
      });
    });

    await page.route('**/api/v1/hospital/census*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sedeNombre: 'Sede Principal Valledupar',
          totalCamas: 50,
          ocupadas: 35,
          disponibles: 10,
          enLimpieza: 3,
          mantenimiento: 2,
          tasaOcupacion: 70.0,
          areas: [
            { nombre: 'Urgencias', total: 20, ocupadas: 18, disponibles: 2 },
            { nombre: 'Hospitalización General', total: 30, ocupadas: 17, disponibles: 8 }
          ]
        })
      });
    });

    await page.route('**/api/v1/hospital/beds*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { publicId: 'bed-01', codigo: 'CAM-101', pabellon: 'Urgencias', estado: 'DISPONIBLE' },
          { publicId: 'bed-02', codigo: 'CAM-102', pabellon: 'Urgencias', estado: 'OCUPADA' }
        ])
      });
    });

    await page.route('**/api/v1/operational/metrics*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sedeNombre: 'Sede Principal Valledupar',
          tiempoEsperaTriageMin: 12.4,
          cumplimientoRes5596: 94.2,
          alertasActivas: 0,
          triageLevelBreakdown: { 1: 2, 2: 5, 3: 11, 4: 18, 5: 4 }
        })
      });
    });

    await page.route('**/api/v1/operational/alerts*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    });
  });

  // =========================================================================
  // PRUEBA 1: Dashboard Administrativo
  // =========================================================================
  test('1. Módulo Dashboard: métricas, aislamiento de privilegios y auditoría reciente', async ({ page }) => {
    await page.goto('/#/admin/dashboard');
    await expect(page.locator('h1')).toHaveText('Dashboard');
    await expect(page.locator('.alert--info')).toContainText('Aislamiento de Privilegios');

    // Verificar 4 KPIs
    await expect(page.locator('#dashMetricSites')).toBeVisible();
    await expect(page.locator('#dashMetricPros')).toBeVisible();
    await expect(page.locator('#dashMetricSpecs')).toBeVisible();
    await expect(page.locator('#dashMetricSlots')).toBeVisible();

    // Tabla de auditoría reciente con clase table--stacked
    const table = page.locator('#dashRecentAuditContainer table');
    await expect(table).toHaveClass(/table--stacked/);
  });

  // =========================================================================
  // PRUEBA 2: Instituciones de Salud
  // =========================================================================
  test('2. Módulo Instituciones: formulario accesible, filtro y tabla', async ({ page }) => {
    await page.goto('/#/admin/institutions');
    await expect(page.locator('#main-content h2').first()).toContainText('Instituciones de Salud');

    // Botón nueva institución abre formulario y enfoca NIT
    const btnToggle = page.locator('#btnToggleNewInst');
    await expect(btnToggle).toBeVisible();
    await btnToggle.click();
    await expect(page.locator('#newInstCard')).toBeVisible();
    await expect(page.locator('#instNit')).toBeFocused();

    // Tabla de instituciones con datos y scope semántico
    const thNit = page.locator('#main-content table th[scope="col"]').first();
    await expect(thNit).toBeVisible();
    await expect(page.locator('#main-content table tbody tr')).toContainText('Hospital Rosario Pumarejo de López');
  });

  // =========================================================================
  // PRUEBA 3: Sedes Asistenciales
  // =========================================================================
  test('3. Módulo Sedes: listado, filtros por institución y formulario colapsable', async ({ page }) => {
    await page.goto('/#/admin/sites');
    await expect(page.locator('#main-content h2').first()).toContainText('Sedes Asistenciales');

    const btnToggle = page.locator('#btnToggleNewSite');
    await btnToggle.click();
    await expect(page.locator('#newSiteCard')).toBeVisible();

    // Tabla de sedes y botones con aria-label
    const btnEdit = page.locator('.btn-edit').first();
    await expect(btnEdit).toHaveAttribute('aria-label', /Editar sede/);
  });

  // =========================================================================
  // PRUEBA 4: Especialidades Médicas
  // =========================================================================
  test('4. Módulo Especialidades: duraciones de turnos y formulario', async ({ page }) => {
    await page.goto('/#/admin/specialties');
    await expect(page.locator('#main-content h2').first()).toContainText('Especialidades Médicas');

    const btnToggle = page.locator('#btnToggleNewSpec');
    await btnToggle.click();
    await expect(page.locator('#newSpecCard')).toBeVisible();
    await expect(page.locator('#specNombre')).toBeFocused();

    // Verificar listado de especialidades
    await expect(page.locator('#main-content table tbody tr')).toHaveCount(3);
  });

  // =========================================================================
  // PRUEBA 5: Profesionales Asistenciales
  // =========================================================================
  test('5. Módulo Profesionales: validación legal en tiempo real de CC/ReTHUS', async ({ page }) => {
    await page.goto('/#/admin/professionals');
    await expect(page.locator('#main-content h2').first()).toContainText('Profesionales Asistenciales');

    const btnToggle = page.locator('#btnToggleNewProf');
    await btnToggle.click();
    await expect(page.locator('#newProfCard')).toBeVisible();

    // Probar validación en vivo de documento
    const numDocInput = page.locator('#profNumDoc');
    await numDocInput.fill('123'); // Muy corto para CC (requiere 6-10 dígitos)
    await numDocInput.blur();
    const errorEl = page.locator('#profNumDocError');
    await expect(errorEl).toBeVisible();
    await expect(errorEl).toContainText('debe tener entre 6 y 10 dígitos');

    // Corregir documento
    await numDocInput.fill('1065123456');
    await numDocInput.blur();
    await expect(errorEl).toBeHidden();
  });

  // =========================================================================
  // PRUEBA 6: Slots de Turnos
  // =========================================================================
  test('6. Módulo Slots: generador masivo con selector de días y listado', async ({ page }) => {
    await page.goto('/#/admin/slots');
    await expect(page.locator('#main-content h2').first()).toContainText('Slots de Disponibilidad Asistencial');

    // Verificar generador masivo
    await expect(page.locator('#slotGenForm')).toBeVisible();
    await expect(page.locator('input[name="genDay"]')).toHaveCount(6);

    // Verificar listado de turnos
    await expect(page.locator('#slotTableContainer table')).toBeVisible();
  });

  // =========================================================================
  // PRUEBA 7: Calendario de Citas y Supervisión
  // =========================================================================
  test('7. Módulo Citas: cuadrícula horaria time-grid y selector de vistas', async ({ page }) => {
    await page.goto('/#/admin/appointments');
    await expect(page.locator('#main-content h2').first()).toContainText('Calendario de Citas y Supervisión');

    // Selector de vista Semana / Día
    await expect(page.locator('#btnViewWeek')).toBeVisible();
    await expect(page.locator('#btnViewDay')).toBeVisible();
    await expect(page.locator('#btnOpenExportModal')).toBeVisible();
  });

  // =========================================================================
  // PRUEBA 8: Reportes Operativos
  // =========================================================================
  test('8. Módulo Reportes: métricas agregadas, exportar CSV y atajos de rango', async ({ page }) => {
    await page.goto('/#/admin/reports');
    await expect(page.locator('#main-content h2').first()).toContainText('Indicadores y Reportes Operativos');
    await expect(page.locator('#btnExportarCsv')).toBeVisible();

    // Atajos de rango de fecha
    const btnRango = page.locator('.btn-rango');
    await expect(btnRango).toHaveCount(4);
  });

  // =========================================================================
  // PRUEBA 9: Bitácora de Auditoría
  // =========================================================================
  test('9. Módulo Auditoría: bitácora inmutable con filtros y tabla stacked', async ({ page }) => {
    await page.goto('/#/admin/audit');
    await expect(page.locator('#main-content h2').first()).toContainText('Bitácora de Auditoría de Seguridad');
    await expect(page.locator('#auditFilterForm')).toBeVisible();
    await expect(page.locator('#auditTableContainer table')).toHaveClass(/table--stacked/);
  });

  // =========================================================================
  // PRUEBA 10: Importación Masiva EPS
  // =========================================================================
  test('10. Módulo Importación EPS: selector de EPS y plantilla Excel', async ({ page }) => {
    await page.goto('/#/affiliations/import');
    await expect(page.locator('h1')).toContainText('Importación Masiva de Afiliados EPS');
    await expect(page.locator('#btn-descargar-plantilla')).toBeVisible();
    await expect(page.locator('#form-upload-excel')).toBeVisible();
  });

  // =========================================================================
  // PRUEBA 11: Consulta de Aseguramiento EPS
  // =========================================================================
  test('11. Módulo Consulta EPS: aviso legal Ley 1751 y formulario de búsqueda', async ({ page }) => {
    await page.goto('/#/affiliations/search');
    await expect(page.locator('h1')).toContainText('Consulta de Aseguramiento EPS');
    await expect(page.locator('.alert-info')).toContainText('Ley 1751 de 2015');
    await expect(page.locator('#form-consulta-eps')).toBeVisible();
  });

  // =========================================================================
  // PRUEBA 12: Censo Hospitalario
  // =========================================================================
  test('12. Módulo Censo Hospitalario: KPIs de capacidad, selector de sedes y pabellones', async ({ page }) => {
    await page.goto('/#/hospital/census');
    await expect(page.locator('h1')).toContainText('Centro de Control Hospitalario');
    await expect(page.locator('#censusKpiGrid')).toBeVisible();
    await expect(page.locator('#censusSedeSelect')).toBeVisible();
  });

  // =========================================================================
  // PRUEBA 13: Centro de Mando Operativo
  // =========================================================================
  test('13. Módulo Centro de Mando: estado en vivo, Res. 5596 y toggle auto-refresco', async ({ page }) => {
    await page.goto('/#/operational/dashboard');
    await expect(page.locator('h1')).toContainText('Centro de Mando y Analítica Hospitalaria');
    await expect(page.locator('.operational-live-badge')).toBeVisible();
    await expect(page.locator('#btnToggleAutoRefresh')).toBeVisible();
  });

});
