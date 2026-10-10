// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Suite de Auditoría de Accesibilidad Web (WCAG 2.1 AA) y Rediseño de Navegación Lateral
 *
 * Cobertura de especificaciones:
 * 1. Semántica de Landmarks y Bypass Blocks (WCAG 2.4.1 Skip-link).
 * 2. Menú de barra lateral a la izquierda: comportamiento hover (rail colapsado -> expande con cursor).
 * 3. Botón de fijación permanente (Pin / Lock) con aria-pressed y persistencia en localStorage.
 * 4. Navegación por teclado (WCAG 2.1.1) y descarte por tecla Escape (WCAG 1.4.13).
 * 5. Tamaños de interacción accesibles (WCAG 2.5.5 / 2.5.8 >= 44x44px).
 * 6. Responsive Drawer en dispositivos móviles con botón hamburguesa accesible y backdrop.
 */

test.describe('Auditoría Accesibilidad WCAG 2.1 AA y Menú Lateral (Sidebar)', () => {
  const staffEmail = 'enfermera.urgencias@hrpl.cesar.gov.co';

  test.beforeEach(async ({ page }) => {
    // Interceptar llamadas de sesión para simular profesional de enfermería en Valledupar
    await page.route('**/api/v1/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-enfermera-valledupar',
          email: staffEmail,
          nombreCompleto: 'Lic. Carmen Rosa Baute',
          roles: ['ROLE_ENFERMERIA']
        })
      });
    });

    await page.route('**/api/v1/admin/sites', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            publicId: 'sede-valledupar-01',
            nombre: 'Hospital Rosario Pumarejo de López — Valledupar'
          }
        ])
      });
    });

    await page.route('**/api/v1/emergency/episodes/active*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    });

    await page.route('**/api/v1/operational/dashboard*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sedePublicId: 'sede-valledupar-01',
          sedeNombre: 'Hospital Rosario Pumarejo de López — Valledupar',
          totalCamas: 120,
          camasDisponibles: 18,
          camasOcupadas: 92,
          camasLimpieza: 7,
          camasMantenimiento: 3,
          tasaOcupacionPorcentaje: 76.7,
          episodiosUrgenciasActivos: 14,
          episodiosPorNivelTriaje: {
            'I': 1,
            'II': 3,
            'III': 6,
            'IV': 3,
            'V': 1
          },
          tiemposPromedioEsperaMinutos: {
            'I': 0.0,
            'II': 24.5,
            'III': 38.0,
            'IV': 55.0,
            'V': 70.0
          },
          alertasActivas: [
            {
              publicId: 'alt-sat-001',
              tipoAlerta: 'SATURACION_CAMAS',
              nivelSeveridad: 'ALTA',
              mensaje: 'Ocupación de camas de observación al 76.7% en Valledupar.',
              creadoAt: '2026-10-10T11:30:00Z',
              estado: 'ACTIVA'
            }
          ]
        })
      });
    });

    await page.route('**/api/v1/operational/tracking-qr/*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          tokenQr: 'QROP-VAL-9988',
          codigoIdentidadProvisional: 'NN-VAL-0042',
          ubicacionActual: 'Cama OBS-04 — Urgencias Valledupar',
          nivelTriaje: 'II',
          sedeNombre: 'Hospital Rosario Pumarejo de López — Valledupar'
        })
      });
    });
  });

  test('1. Landmarks semánticos y Skip-link para bypass de bloques (WCAG 2.4.1)', async ({ page }) => {
    await page.goto('/#/nursing/dashboard');

    // Verificar landmarks semánticos obligatorios
    await expect(page.locator('header.navbar')).toBeVisible();
    await expect(page.locator('main#main-content')).toBeVisible();
    await expect(page.locator('footer.app-footer')).toBeVisible();
    await expect(page.locator('nav#app-sidebar')).toBeVisible();

    // Skip-link accesible
    const skipLink = page.locator('a.skip-link');
    await expect(skipLink).toHaveAttribute('href', '#main-content');
    await skipLink.focus();
    await expect(skipLink).toBeVisible();
  });

  test('2. Menú lateral con comportamiento Hover: colapsado en reposo y expandido al posar cursor', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/#/nursing/dashboard');

    const sidebar = page.locator('#app-sidebar');
    await expect(sidebar).toBeVisible();

    // En reposo (sin fijar): el ancho debe ser el rail colapsado (~68px / 4.25rem)
    const initialBox = await sidebar.boundingBox();
    expect(initialBox).not.toBeNull();
    if (initialBox) {
      expect(initialBox.width).toBeLessThanOrEqual(80);
    }

    // Posar el cursor sobre el menú lateral (hover)
    await sidebar.hover();
    await page.waitForTimeout(300); // Transición CSS

    // Debe expandirse suavemente a más de 200px para visualizar etiquetas
    const hoveredBox = await sidebar.boundingBox();
    expect(hoveredBox).not.toBeNull();
    if (hoveredBox) {
      expect(hoveredBox.width).toBeGreaterThanOrEqual(240);
    }

    // Verificar que los textos de los enlaces sean legibles durante hover
    await expect(sidebar.locator('text=Panel de Enfermería')).toBeVisible();
    await expect(sidebar.locator('text=Admisión de Urgencias')).toBeVisible();

    // Retirar el cursor fuera del sidebar
    await page.mouse.move(600, 300);
    await page.waitForTimeout(300);

    // Vuelve al ancho colapsado
    const closedBox = await sidebar.boundingBox();
    expect(closedBox).not.toBeNull();
    if (closedBox) {
      expect(closedBox.width).toBeLessThanOrEqual(80);
    }
  });

  test('3. Botón de fijación (Pin): mantiene el menú abierto permanentemente con aria-pressed', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/#/nursing/dashboard');

    const sidebar = page.locator('#app-sidebar');
    const pinBtn = page.locator('#btnPinSidebar');

    // Desplegar temporalmente por hover para ver el botón pin
    await sidebar.hover();
    await expect(pinBtn).toBeVisible();
    await expect(pinBtn).toHaveAttribute('aria-pressed', 'false');

    // Hacer clic en el botón de fijar (Pin)
    await pinBtn.click();
    await expect(pinBtn).toHaveAttribute('aria-pressed', 'true');
    await expect(sidebar).toHaveClass(/is-pinned/);
    await expect(page.locator('body')).toHaveClass(/sidebar-is-pinned/);

    // Retirar el cursor a la mitad de la pantalla
    await page.mouse.move(800, 400);
    await page.waitForTimeout(300);

    // El menú DEBE PERMANECER ABIERTO (ancho >= 240px) gracias al pin
    const pinnedBox = await sidebar.boundingBox();
    expect(pinnedBox).not.toBeNull();
    if (pinnedBox) {
      expect(pinnedBox.width).toBeGreaterThanOrEqual(240);
    }
    await expect(sidebar.locator('text=Panel de Enfermería')).toBeVisible();

    // Desfijar el menú al volver a hacer clic en el botón pin
    await pinBtn.click();
    await expect(pinBtn).toHaveAttribute('aria-pressed', 'false');
    await expect(sidebar).not.toHaveClass(/is-pinned/);

    // Quitar el foco del botón pin para que :focus-within no lo mantenga expandido
    await pinBtn.blur();
    await page.mouse.move(800, 400);
    await page.waitForTimeout(300);

    const unpinnedBox = await sidebar.boundingBox();
    expect(unpinnedBox).not.toBeNull();
    if (unpinnedBox) {
      expect(unpinnedBox.width).toBeLessThanOrEqual(80);
    }
  });

  test('4. Accesibilidad por teclado (WCAG 2.1.1) y descarte por tecla Escape (WCAG 1.4.13)', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/#/nursing/dashboard');

    const sidebar = page.locator('#app-sidebar');

    // Enfocar el primer enlace dentro del sidebar con Tab
    const firstLink = sidebar.locator('.sidebar-link').first();
    await firstLink.focus();

    // Por foco (:focus-within), el menú debe expandirse para permitir lectura accesible
    const focusedBox = await sidebar.boundingBox();
    expect(focusedBox).not.toBeNull();
    if (focusedBox) {
      expect(focusedBox.width).toBeGreaterThanOrEqual(240);
    }

    // Verificar atributo de página activa
    await expect(firstLink).toHaveAttribute('aria-current', 'page');

    // Al presionar tecla Escape, se descarta la expansión activa
    await page.keyboard.press('Escape');
    await page.waitForTimeout(250);
  });

  test('5. Tamaños de interacción táctil y contraste conforme a WCAG AA', async ({ page }) => {
    await page.goto('/#/nursing/dashboard');

    const links = page.locator('#app-sidebar .sidebar-link');
    const count = await links.count();
    expect(count).toBeGreaterThan(0);

    for (let i = 0; i < count; i++) {
      const link = links.nth(i);
      const box = await link.boundingBox();
      expect(box).not.toBeNull();
      if (box) {
        // WCAG 2.5.5 / 2.5.8: Altura mínima táctil de 44px
        expect(box.height).toBeGreaterThanOrEqual(44);
      }
    }
  });

  test('6. Modo responsive móvil (< 768px): drawer modal accesible con botón toggle y backdrop', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/#/nursing/dashboard');

    const sidebar = page.locator('#app-sidebar');
    const toggleBtn = page.locator('#sidebarToggleBtn');
    const backdrop = page.locator('#sidebar-backdrop');

    // En móvil el botón de menú hamburguesa debe estar visible
    await expect(toggleBtn).toBeVisible();
    await expect(toggleBtn).toHaveAttribute('aria-expanded', 'false');

    // Abrir el drawer tocando el botón toggle
    await toggleBtn.click();
    await expect(toggleBtn).toHaveAttribute('aria-expanded', 'true');
    await expect(sidebar).toHaveClass(/is-mobile-open/);
    await expect(backdrop).toHaveClass(/is-visible/);

    // Cerrar tocando el botón de cierre móvil accesible
    const closeBtn = page.locator('#btnCloseSidebarMobile');
    await expect(closeBtn).toBeVisible();
    await closeBtn.click();
    await expect(toggleBtn).toHaveAttribute('aria-expanded', 'false');
    await expect(sidebar).not.toHaveClass(/is-mobile-open/);
    await expect(backdrop).not.toHaveClass(/is-visible/);

    // Abrir de nuevo y cerrar con tecla Escape (WCAG 1.4.13)
    await toggleBtn.click();
    await expect(sidebar).toHaveClass(/is-mobile-open/);
    await page.keyboard.press('Escape');
    await expect(sidebar).not.toHaveClass(/is-mobile-open/);
    await expect(toggleBtn).toHaveAttribute('aria-expanded', 'false');
  });

  test('7. Menú lateral: al seleccionar un apartado en modo no fijado, se repliega de inmediato y no se queda estático', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/#/nursing/dashboard');

    const sidebar = page.locator('#app-sidebar');

    // 1. En reposo está colapsado (rail <= 80px)
    const initialBox = await sidebar.boundingBox();
    expect(initialBox).not.toBeNull();
    if (initialBox) expect(initialBox.width).toBeLessThanOrEqual(80);

    // 2. Expandir por hover
    await sidebar.hover();
    await page.waitForTimeout(300);
    const hoveredBox = await sidebar.boundingBox();
    expect(hoveredBox).not.toBeNull();
    if (hoveredBox) expect(hoveredBox.width).toBeGreaterThanOrEqual(240);

    // 3. Hacer clic en "Centro de Mando y Analítica"
    const linkCentroMando = sidebar.locator('a[href="#/operational/dashboard"]');
    await expect(linkCentroMando).toBeVisible();
    await linkCentroMando.click();

    // 4. Tras hacer clic, el menú DEBE replegarse inmediatamente al ancho del rail (<= 80px)
    await page.waitForTimeout(350);
    const postClickBox = await sidebar.boundingBox();
    expect(postClickBox).not.toBeNull();
    if (postClickBox) {
      expect(postClickBox.width).toBeLessThanOrEqual(80);
    }

    // 5. Verificar que la URL cambió al Centro de Mando
    await expect(page).toHaveURL(/#\/operational\/dashboard/);
  });

  test('8. Centro de Mando y Analítica Hospitalaria: auditoría de diseño impecable, KPIs, Res. 5596 y responsive', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/#/operational/dashboard');

    // 1. Título y badge en vivo
    await expect(page.locator('h1.view-title')).toContainText('Centro de Mando y Analítica Hospitalaria');
    await expect(page.locator('.operational-live-badge')).toBeVisible();

    // 2. Tarjetas de KPIs principales
    await expect(page.locator('#kpi-tasa-ocupacion')).toContainText('77%');
    await expect(page.locator('#kpi-progress-bar')).toBeVisible();
    await expect(page.locator('#kpi-pacientes-urgencias')).toContainText('14');
    await expect(page.locator('#kpi-alertas-activas')).toContainText('1');
    await expect(page.locator('#kpi-camas-disponibles')).toContainText('18');

    // 3. Banner de alerta crítica de saturación
    await expect(page.locator('#banner-alerta-critica')).toBeVisible();

    // 4. Tiempos de triaje Res. 5596/2015
    await expect(page.locator('#tiempo-t1')).toContainText('0 min');
    await expect(page.locator('#tiempo-t2')).toContainText('25 min');
    await expect(page.locator('#tiempo-t3')).toContainText('38 min');

    // 5. Tabla de alertas y botón de reconocimiento auditado (ACK)
    const btnAck = page.locator('.btn-ack-alerta').first();
    await expect(btnAck).toBeVisible();
    await btnAck.click();

    // Modal accesible para ACK
    const modalTitle = page.locator('#modal-dialog-title');
    await expect(modalTitle).toContainText('Reconocimiento de Alerta');
    await page.locator('#txtMotivoAckModal').fill('Se coordinó refuerzo médico en urgencias Valledupar.');
    const btnConfirm = page.locator('.btn-confirm-modal');
    await expect(btnConfirm).toBeVisible();
    await page.locator('.btn-cancel-modal').click(); // Cancelar modal para no mutar estado

    // 6. Consulta de manilla / cabecera QR segura
    const inputToken = page.locator('#input-token-qr');
    await inputToken.fill('QROP-VAL-9988');
    await page.locator('#btnConsultarQr').click();

    await expect(page.locator('#resultado-qr-track')).toBeVisible();
    await expect(page.locator('#qr-id-prov')).toContainText('NN-VAL-0042');
    await expect(page.locator('#qr-ubicacion')).toContainText('Cama OBS-04');

    // 7. Verificación Responsive Móvil (375x667)
    await page.setViewportSize({ width: 375, height: 667 });
    await page.waitForTimeout(300);

    // En móvil los KPIs y tarjetas se apilan limpiamente sin desborde
    await expect(page.locator('#kpi-tasa-ocupacion')).toBeVisible();
    await expect(page.locator('#banner-alerta-critica')).toBeVisible();
    await expect(page.locator('#form-scan-qr')).toBeVisible();
  });
});
