// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Flujo Crítico 1: Paciente — Triaje Clínico -> Agendamiento de Cita
 * Criterio de aceptación T9:
 * 1. Paciente autenticado accede a triaje.
 * 2. Visualiza el aviso de prototipo legal en el asistente y en el resultado.
 * 3. Selecciona síntoma no urgente (leve), ajusta intensidad y duración.
 * 4. Obtiene orientación asistencial con ruta de cita sugerida.
 * 5. Avanza al agendamiento vinculado con su triaje.
 * 6. Selecciona slot de disponibilidad y confirma la reserva de cita.
 */

test.describe('Flujo Crítico 1: Paciente — Triaje a Cita', () => {
  const patientEmail = process.env.DEMO_PATIENT_EMAIL || 'paciente1@demo.meditriaje.test';
  const password = process.env.DEMO_PASSWORD || 'DemoSecretPass123!';

  test('debe completar el triaje asistencial leve y agendar una cita vinculada', async ({ page }) => {
    // 1. Iniciar sesión como paciente
    await page.goto('/#/login');
    await expect(page.locator('#formLogin')).toBeVisible();

    await page.fill('#loginEmail', patientEmail);
    await page.fill('#loginPassword', password);
    await page.click('#formLogin button[type="submit"]');

    // Esperar redirección al portal del paciente
    await expect(page).toHaveURL(/.*#\/patient\/dashboard/);
    await expect(page.locator('h1')).toContainText(/Hola,|Panel del Paciente/i);

    // 2. Navegar al asistente de triaje
    await page.goto('/#/patient/triage');
    await expect(page.locator('h1')).toContainText('Orientación de Triaje Clínico');

    // Verificar aviso obligatorio de prototipo académico
    const disclaimer = page.locator('text=Prototipo académico. Orienta, no diagnostica');
    await expect(disclaimer.first()).toBeVisible();

    // 3. Paso 1: Seleccionar síntoma no urgente
    // Esperar a que el catálogo de síntomas se cargue en el DOM
    const symptomChips = page.locator('#symptomsList button.chip');
    await expect(symptomChips.first()).toBeVisible({ timeout: 15000 });

    // Elegir el primer síntoma no marcado como alarma
    const nonAlarmChip = page.locator('#symptomsList button.chip:not([style*="var(--danger)"])').first();
    await nonAlarmChip.click();

    // Avanzar al Paso 2
    const btnNext = page.locator('#btnNextStep');
    await expect(btnNext).toBeEnabled();
    await btnNext.click();

    // 4. Paso 2: Detalles del síntoma (Intensidad y Duración)
    await expect(page.locator('h2')).toContainText('Detalles de tus síntomas');

    // Seleccionar intensidad moderada (ej. 4 de 10)
    const scaleBtn = page.locator('.scale-btn[data-val="4"]').first();
    if (await scaleBtn.isVisible()) {
      await scaleBtn.click();
    }

    // Configurar duración (ej. 12 horas)
    const durationInput = page.locator('.duration-input').first();
    await durationInput.fill('12');

    // Enviar a evaluar
    const btnSubmit = page.locator('#btnSubmitTriage');
    await btnSubmit.click();

    // 5. Pantalla de resultado
    await expect(page.locator('h1')).toContainText(/Resultado de tu Orientación|Orientación/i);

    // Verificar que aparece el botón para agendar cita desde el resultado
    const btnBook = page.locator('#btnBookFromTriage');
    await expect(btnBook).toBeVisible();

    // Verificar que el aviso de prototipo sigue presente en el resultado
    await expect(page.locator('text=Prototipo académico. Orienta, no diagnostica').first()).toBeVisible();

    // 6. Avanzar al agendamiento vinculado
    await btnBook.click();
    await expect(page).toHaveURL(/.*#\/patient\/book/);

    // Esperar disponibilidad de slots
    const slotChips = page.locator('.slot-chip');
    await expect(slotChips.first()).toBeVisible({ timeout: 15000 });

    // Seleccionar el primer horario disponible
    await slotChips.first().click();

    // Verificar tarjeta de confirmación de cita
    const confirmCard = page.locator('#slotConfirmationCard');
    await expect(confirmCard).toBeVisible();

    // Confirmar la reserva de cita
    const btnConfirm = page.locator('#btnConfirmAppointment');
    await expect(btnConfirm).toBeVisible();
    await btnConfirm.click();

    // 7. Verificación de éxito de reserva
    // Debe mostrar la vista de éxito o confirmación
    await expect(page.locator('text=¡Cita agendada exitosamente!|Reserva Exitosa|Cita Confirmada').first()).toBeVisible({ timeout: 15000 });
  });
});
