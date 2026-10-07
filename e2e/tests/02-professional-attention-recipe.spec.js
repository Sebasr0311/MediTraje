// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Flujo Crítico 2: Profesional — Atención Médica -> Alergias -> Receta
 * Criterio de aceptación T9:
 * 1. Profesional autenticado accede a su agenda del día.
 * 2. Inicia o abre la atención clínica de un paciente.
 * 3. Visualiza y verifica el panel de alergias del paciente.
 * 4. Diligencia signos vitales, motivo, evolución y diagnóstico CIE-10.
 * 5. Cierra formalmente la atención clínica (registro inmutable).
 * 6. Emite receta médica enlazada a la atención cerrada con catálogo de medicamentos.
 */

test.describe('Flujo Crítico 2: Profesional — Atención a Receta', () => {
  const doctorEmail = process.env.DEMO_DOCTOR_EMAIL || 'medico1@demo.meditriaje.test';
  const password = process.env.DEMO_PASSWORD || 'DemoSecretPass123!';

  test('debe atender a un paciente, revisar alergias, cerrar atención y emitir receta', async ({ page }) => {
    // 1. Iniciar sesión como profesional asistencial
    await page.goto('/#/login');
    await expect(page.locator('#formLogin')).toBeVisible();

    await page.fill('#loginEmail', doctorEmail);
    await page.fill('#loginPassword', password);
    await page.click('#formLogin button[type="submit"]');

    // Esperar redirección a la agenda del médico
    await expect(page).toHaveURL(/.*#\/professional\/agenda/);
    await expect(page.locator('h1')).toContainText('Agenda del día');

    // 2. Localizar cita para atender
    // Si existe botón de iniciar atención en alguna cita del listado
    const btnStart = page.locator('.btn-start').first();
    const btnHistory = page.locator('a[href*="#/professional/patient-history/"]').first();

    // Esperar a que la lista de citas cargue
    await expect(page.locator('#agendaList')).toBeVisible({ timeout: 15000 });

    if (await btnStart.isVisible()) {
      await btnStart.click();
    } else {
      // Si todas ya están iniciadas o cerradas, navegar a la primera atención disponible de la demo
      const attendLink = page.locator('a[href*="#/professional/attention/"]').first();
      if (await attendLink.isVisible()) {
        await attendLink.click();
      } else {
        // Alternativamente, buscar una cita en estado PROGRAMADA
        const firstCita = page.locator('.appointment-card').first();
        await expect(firstCita).toBeVisible();
      }
    }

    // 3. Verificar que estamos en la vista de atención clínica
    await expect(page).toHaveURL(/.*#\/professional\/attention\/.*/);
    await expect(page.locator('h1')).toContainText('Atención clínica');

    // 4. Verificar presencia del panel de alergias (requisito D3 / T4)
    const allergiesPanel = page.locator('#panelAlergiasContainer, #panelAlergiasContainerClosed');
    await expect(allergiesPanel.first()).toBeVisible();

    // 5. Si la atención está abierta, completarla y cerrarla
    const formAttention = page.locator('#attentionForm');
    if (await formAttention.isVisible()) {
      // Llenar motivo de consulta
      await page.fill('#f-motivo', 'Paciente consulta por cuadro clínico general y control asistencial');

      // Llenar evolución clínica
      await page.fill('#f-evolucion', 'Paciente orientado en tiempo y espacio. Examen físico sin alteraciones agudas. Evolución favorable.');

      // Llenar indicaciones médicas
      await page.fill('#f-indicaciones', 'Reposo relativo, adecuada hidratación oral y signos de alarma explicados.');

      // Buscar diagnóstico CIE-10 (ej. J00 o I10)
      await page.fill('#f-cie10', 'J00');
      const cie10Result = page.locator('#cie10Results button').first();
      await expect(cie10Result).toBeVisible({ timeout: 5000 });
      await cie10Result.click();

      // Diligenciar signos vitales
      if (await page.locator('#v-presionSistolica').isVisible()) {
        await page.fill('#v-presionSistolica', '120');
        await page.fill('#v-presionDiastolica', '80');
        await page.fill('#v-frecuenciaCardiaca', '75');
        await page.fill('#v-temperatura', '36.5');
      }

      // Enviar cierre de atención
      await page.click('#btnClose');

      // Modal de confirmación de cierre irreversible
      const modalConfirmBtn = page.locator('.modal-footer button.btn-primary, button:has-text("Sí, cerrar atención")').first();
      await expect(modalConfirmBtn).toBeVisible();
      await modalConfirmBtn.click();

      // Verificar que la atención transicionó a cerrada e inmutable
      await expect(page.locator('text=Cerrada · inmutable')).toBeVisible({ timeout: 10000 });
    }

    // 6. Avanzar a emisión de receta médica
    const btnPrescription = page.locator('a[href*="#/professional/prescription/"]').first();
    await expect(btnPrescription).toBeVisible();
    await btnPrescription.click();

    // 7. En la vista de receta:
    await expect(page).toHaveURL(/.*#\/professional\/prescription\/.*/);
    await expect(page.locator('h1')).toContainText('Nueva receta médica');

    // Verificar panel de alergias también en la vista de prescripción
    await expect(page.locator('#rxAllergiesContainer')).toBeVisible();

    // Buscar medicamento en catálogo (ej. "acetaminofen" o "amoxicilina")
    await page.fill('#medSearch', 'acetaminofen');
    const medOpt = page.locator('#medResults button.med-opt').first();
    await expect(medOpt).toBeVisible({ timeout: 10000 });
    await medOpt.click();

    // Llenar posología del medicamento
    await page.fill('.f-dosis', '500 mg');
    await page.fill('.f-frecuencia', 'Cada 8 horas con las comidas');
    await page.fill('.f-duracion', '5');
    await page.fill('.f-cantidad', '15');
    await page.fill('.f-indic', 'Tomar con abundante agua');

    // Emitir receta
    await page.click('#btnEmit');

    // Modal de confirmación
    const modalEmitBtn = page.locator('.modal-footer button.btn-primary, button:has-text("Sí, emitir receta")').first();
    if (await modalEmitBtn.isVisible()) {
      await modalEmitBtn.click();
    }

    // Verificar confirmación de receta emitida exitosamente
    await expect(page.locator('text=Receta emitida exitosamente|Receta creada|Cerrada · inmutable').first()).toBeVisible({ timeout: 10000 });
  });
});
