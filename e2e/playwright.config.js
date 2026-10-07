// @ts-check
const { defineConfig, devices } = require('@playwright/test');

/**
 * Configuración de Playwright E2E para MediTriaje 2.0
 * Cubre los 3 flujos críticos del plan post-auditoría:
 * 1. Paciente: Triaje -> Cita
 * 2. Profesional: Atención -> Alergia -> Receta
 * 3. Administrador: Aislamiento 403 en rutas clínicas
 */
module.exports = defineConfig({
  testDir: './tests',
  timeout: 60 * 1000,
  expect: {
    timeout: 10 * 1000
  },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }]
  ],
  outputDir: 'test-results/',
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:3000',
    headless: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    actionTimeout: 15 * 1000,
    navigationTimeout: 30 * 1000
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
