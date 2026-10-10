import os
import subprocess
import sys
import fitz

html_template = """<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>MediTriaje 2.0 — Dossier Técnico, Funcional y de Impacto Sanitario</title>
<style>
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap');

  @page {
    size: A4 portrait;
    margin: 14mm 14mm 14mm 14mm;
    @top-right {
      content: "MediTriaje 2.0 · Valledupar";
      font-size: 8pt;
      font-weight: 500;
      color: #64748B;
      font-family: 'Inter', sans-serif;
    }
    @bottom-center {
      content: "Página " counter(page) " de 8";
      font-size: 8pt;
      color: #64748B;
      font-family: 'Inter', sans-serif;
    }
  }

  @page :first {
    margin: 12mm 12mm 12mm 12mm;
    @top-right { content: none; }
    @bottom-center { content: none; }
  }

  *, *::before, *::after {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
  }

  body {
    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    color: #1E293B;
    background-color: #FFFFFF;
    line-height: 1.45;
    font-size: 9.2pt;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .doc-page {
    page-break-after: always;
    break-after: page;
    height: 268mm;
    display: flex;
    flex-direction: column;
    justify-content: flex-start;
    overflow: hidden;
  }

  .doc-page:last-child {
    page-break-after: avoid;
    break-after: avoid;
  }

  .page-nav-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1.5px solid #E2E8F0;
    padding-bottom: 5px;
    margin-bottom: 12px;
    font-size: 8pt;
    color: #64748B;
  }
  .page-nav-header strong {
    color: #0F766E;
    font-weight: 600;
  }

  h1 {
    font-size: 17pt;
    font-weight: 800;
    color: #0F172A;
    line-height: 1.2;
    margin-bottom: 6px;
    letter-spacing: -0.3px;
  }
  h2 {
    font-size: 11.5pt;
    font-weight: 700;
    color: #0F766E;
    border-bottom: 1px solid #E2E8F0;
    padding-bottom: 3px;
    margin-top: 10px;
    margin-bottom: 6px;
  }
  h3 {
    font-size: 10pt;
    font-weight: 700;
    color: #115E59;
    margin-top: 8px;
    margin-bottom: 4px;
  }
  h4 {
    font-size: 9pt;
    font-weight: 600;
    color: #334155;
    margin-top: 5px;
    margin-bottom: 3px;
  }

  p {
    margin-bottom: 6px;
    text-align: justify;
    font-size: 9pt;
    line-height: 1.42;
  }

  strong { font-weight: 600; color: #0F172A; }
  em { font-style: italic; }

  code {
    font-family: 'JetBrains Mono', Consolas, monospace;
    font-size: 8pt;
    background: #F1F5F9;
    color: #0F766E;
    padding: 1px 4px;
    border-radius: 4px;
    border: 1px solid #E2E8F0;
  }

  .cover-card {
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    padding: 18mm 16mm 14mm 16mm;
    background: linear-gradient(145deg, #042F2E 0%, #0F766E 55%, #115E59 100%);
    color: #FFFFFF;
    border-radius: 12px;
  }
  .cover-top {
    display: flex;
    gap: 10px;
  }
  .brand-pill {
    background: rgba(255, 255, 255, 0.15);
    border: 1px solid rgba(255, 255, 255, 0.3);
    padding: 4px 12px;
    border-radius: 20px;
    font-size: 8pt;
    font-weight: 600;
    letter-spacing: 0.8px;
    text-transform: uppercase;
  }
  .cover-title-area {
    margin-top: 15mm;
  }
  .cover-title {
    font-size: 36pt;
    font-weight: 800;
    letter-spacing: -1.5px;
    line-height: 1.05;
    margin-bottom: 10px;
    background: linear-gradient(90deg, #FFFFFF, #CCFBF1);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  .cover-subtitle {
    font-size: 14pt;
    font-weight: 500;
    color: #CCFBF1;
    margin-bottom: 14px;
    line-height: 1.35;
  }
  .cover-desc {
    font-size: 9.8pt;
    color: #E6FFFA;
    line-height: 1.55;
    text-align: justify;
    margin-bottom: 15mm;
  }
  .cover-meta-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
    background: rgba(0, 0, 0, 0.28);
    border: 1px solid rgba(255, 255, 255, 0.2);
    padding: 12px 16px;
    border-radius: 8px;
    margin-bottom: 10mm;
  }
  .cover-meta-label {
    color: #99F6E4;
    font-weight: 600;
    text-transform: uppercase;
    font-size: 6.8pt;
    letter-spacing: 0.8px;
    margin-bottom: 2px;
  }
  .cover-meta-val {
    color: #FFFFFF;
    font-weight: 500;
    font-size: 8.5pt;
  }
  .cover-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-top: 1px solid rgba(255, 255, 255, 0.2);
    padding-top: 8px;
    font-size: 8pt;
    color: #CCFBF1;
  }

  .card {
    background: #F8FAFC;
    border: 1px solid #E2E8F0;
    border-radius: 6px;
    padding: 7px 10px;
    margin-bottom: 6px;
  }
  .card-highlight {
    background: #F0FDFA;
    border: 1px solid #99F6E4;
    border-left: 3.5px solid #0F766E;
  }
  .card-accent {
    background: #F8FAFC;
    border: 1px solid #E2E8F0;
    border-left: 3.5px solid #0284C7;
  }
  .card-alert {
    background: #FEF2F2;
    border: 1px solid #FECACA;
    border-left: 3.5px solid #DC2626;
  }

  .grid-2 {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
    margin-bottom: 6px;
  }
  .grid-3 {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 7px;
    margin-bottom: 6px;
  }
  .grid-4 {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 7px;
    margin-bottom: 6px;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    margin: 5px 0 7px 0;
    font-size: 8.3pt;
  }
  th, td {
    padding: 4.5px 6.5px;
    text-align: left;
    vertical-align: top;
    border: 1px solid #E2E8F0;
  }
  th {
    background-color: #F1F5F9;
    color: #0F172A;
    font-weight: 600;
    font-size: 7.6pt;
    text-transform: uppercase;
    letter-spacing: 0.4px;
  }
  tr:nth-child(even) td {
    background-color: #FAFAFA;
  }

  .badge {
    display: inline-block;
    padding: 1.5px 6px;
    border-radius: 10px;
    font-size: 7.2pt;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.3px;
    white-space: nowrap;
  }
  .badge-triage-1 { background: #FEE2E2; color: #991B1B; border: 1px solid #F87171; }
  .badge-triage-2 { background: #FFEDD5; color: #9A3412; border: 1px solid #FB923C; }
  .badge-triage-3 { background: #FEF9C3; color: #854D0E; border: 1px solid #FACC15; }
  .badge-triage-4 { background: #DCFCE7; color: #166534; border: 1px solid #4ADE80; }
  .badge-triage-5 { background: #DBEAFE; color: #1E40AF; border: 1px solid #60A5FA; }

  .feat-item {
    border: 1px solid #E2E8F0;
    border-radius: 6px;
    padding: 6.5px 9px;
    background: #FFFFFF;
    margin-bottom: 5px;
  }
  .feat-title {
    font-weight: 600;
    color: #0F766E;
    font-size: 8.8pt;
    margin-bottom: 2px;
  }

  .wf-grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 5px;
    margin: 6px 0;
  }
  .wf-grid-4 {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 5px;
    margin: 4px 0 6px 0;
  }
  .wf-box {
    background: #FFFFFF;
    border: 1px solid #99F6E4;
    border-top: 3px solid #0F766E;
    border-radius: 5px;
    padding: 5px 6px;
    font-size: 7.5pt;
    text-align: center;
  }
  .wf-step {
    font-weight: 700;
    color: #0F766E;
    font-size: 8pt;
    display: block;
    margin-bottom: 1px;
  }

  .kpi-row {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 7px;
    margin: 8px 0;
  }
  .kpi-box {
    background: #F0FDFA;
    border: 1px solid #99F6E4;
    border-radius: 6px;
    padding: 7px;
    text-align: center;
  }
  .kpi-val {
    font-size: 14pt;
    font-weight: 800;
    color: #0F766E;
    line-height: 1;
    margin-bottom: 3px;
  }
  .kpi-lbl {
    font-size: 6.8pt;
    color: #475569;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.4px;
  }

  ul, ol {
    margin-left: 16px;
    margin-bottom: 5px;
  }
  li {
    margin-bottom: 2px;
    text-align: justify;
    font-size: 8.7pt;
    line-height: 1.38;
  }

</style>
</head>
<body>

<!-- ==========================================
     PÁGINA 1: PORTADA
=========================================== -->
<div class="doc-page">
  <div class="cover-card">
    <div class="cover-top">
      <div class="brand-pill">Documento Técnico Institucional</div>
      <div class="brand-pill">Salud Digital Colombia · Valledupar</div>
    </div>

    <div class="cover-title-area">
      <div class="cover-title">MediTriaje 2.0</div>
      <div class="cover-subtitle">Plataforma Integral de Triaje Inteligente, Gestión Asistencial, Historia Clínica Inmutable y Trazabilidad Farmacéutica</div>
      <div class="cover-desc">
        Dossier completo de arquitectura de software, catálogo funcional, modelo de seguridad de datos e impacto estratégico para la transformación y descongestión de la red hospitalaria pública y privada de <strong>Valledupar y el Departamento del Cesar</strong>.
      </div>
    </div>

    <div class="cover-meta-grid">
      <div>
        <div class="cover-meta-label">Entorno y Jurisdicción</div>
        <div class="cover-meta-val">Valledupar, Cesar · República de Colombia</div>
      </div>
      <div>
        <div class="cover-meta-label">Marco Normativo Aplicable</div>
        <div class="cover-meta-val">Resolución 5596/2015 · Res. 1995/1999 · Ley 1581/2012 · Dec. 780/2016</div>
      </div>
      <div>
        <div class="cover-meta-label">Stack Tecnológico Central</div>
        <div class="cover-meta-val">Java 21 LTS · Spring Boot 3.5.16 · Oracle ATP Cloud · Vanilla ES</div>
      </div>
      <div>
        <div class="cover-meta-label">Aseguramiento de Calidad</div>
        <div class="cover-meta-val">Suite Automatizada: 948 Pruebas (Unitarias, Integración y E2E)</div>
      </div>
    </div>

    <div class="cover-footer">
      <div><strong>MediTriaje 2.0</strong> — Ingeniería de Software en Salud</div>
      <div>Octubre 2026 · Versión 2.0-PROD</div>
    </div>
  </div>
</div>

<!-- ==========================================
     PÁGINA 2: RESUMEN EJECUTIVO E ÍNDICE
=========================================== -->
<div class="doc-page">
  <div class="page-nav-header">
    <span><strong>MediTriaje 2.0</strong> · Dossier Técnico Institucional</span>
    <span>Resumen Ejecutivo e Índice</span>
  </div>

  <h1>Resumen Ejecutivo</h1>
  <p>
    <strong>MediTriaje 2.0</strong> es una solución tecnológica web de alto impacto concebida para cerrar la histórica brecha de acceso, orientación y congestión en los servicios de salud de Colombia, con formulación y contextualización prioritaria para el municipio de <strong>Valledupar</strong> y el departamento del <strong>Cesar</strong>. El sistema integra en una sola plataforma segura el ciclo asistencial completo del paciente: desde la autoevaluación guiada de síntomas mediante un motor de triaje determinista con corte de emergencia infalible, pasando por la búsqueda atómica de turnos médicos sin colisiones, la atención médica inmutable con diagnósticos CIE-10, hasta la prescripción médica auditada con control riguroso de dispensación farmacológica.
  </p>

  <div class="kpi-row">
    <div class="kpi-box">
      <div class="kpi-val">5 Niveles</div>
      <div class="kpi-lbl">Triaje Normativo Res. 5596</div>
    </div>
    <div class="kpi-box">
      <div class="kpi-val">100% Inmutable</div>
      <div class="kpi-lbl">Historia Clínica en Oracle ATP</div>
    </div>
    <div class="kpi-box">
      <div class="kpi-val">0 Colisiones</div>
      <div class="kpi-lbl">Doble Reserva Atómica en BD</div>
    </div>
    <div class="kpi-box">
      <div class="kpi-val">948 Tests</div>
      <div class="kpi-lbl">Verificación Continua Verde</div>
    </div>
  </div>

  <div class="card card-highlight">
    <h3 style="margin-top: 0; color: #0F766E;">Declaración de Principios de Salud Digital</h3>
    <p style="margin-bottom: 0; font-size: 8.8pt;">
      MediTriaje 2.0 opera bajo estrictos postulados éticos y legales: <strong>el sistema nunca diagnostica de forma autónoma ni sustituye el criterio médico profesional</strong>. Su motor orienta al ciudadano en el nivel de urgencia adecuado para descongestionar las salas hospitalarias, derivando inmediatamente a los canales de auxilio vital (Línea 123 y urgencias médicas) ante la presencia de signos de alarma.
    </p>
  </div>

  <h2>Estructura y Contenido del Dossier</h2>
  <table>
    <thead>
      <tr>
        <th style="width: 14%;">Sección</th>
        <th style="width: 34%;">Título y Alcance</th>
        <th style="width: 44%;">Contenido Esencial Desarrollado</th>
        <th style="width: 8%; text-align: center;">Pág.</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td><strong>Capítulo 1</strong></td>
        <td><strong>¿Qué es MediTriaje 2.0?</strong></td>
        <td>Definición, origen evolutivo desde la versión académica, ciclo de vida del paciente en 9 pasos y principios de diseño clínico.</td>
        <td style="text-align: center;">3</td>
      </tr>
      <tr>
        <td><strong>Capítulo 2</strong></td>
        <td><strong>¿Qué hace el Sistema? (Parte 1)</strong></td>
        <td>Módulos de identidad colombiana, seguridad, triaje clínico normativo I–V con corte de emergencia y citas concurrentes.</td>
        <td style="text-align: center;">4</td>
      </tr>
      <tr>
        <td><strong>Capítulo 2</strong></td>
        <td><strong>¿Qué hace el Sistema? (Parte 2)</strong></td>
        <td>Atención e historia inmutable en Oracle ATP, farmacia y dispensación INVIMA, Break-Glass 24h, seguimiento y asistente virtual.</td>
        <td style="text-align: center;">5</td>
      </tr>
      <tr>
        <td><strong>Capítulo 3</strong></td>
        <td><strong>Impacto en Valledupar y el Cesar</strong></td>
        <td>Análisis territorial: descongestión del HEAD y Hospital Rosario Pumarejo, fin a filas de 4 a.m., inclusión de corregimientos y control farmacológico.</td>
        <td style="text-align: center;">6</td>
      </tr>
      <tr>
        <td><strong>Capítulo 4</strong></td>
        <td><strong>Tecnologías que Utiliza</strong></td>
        <td>Especificación detallada: Java 21 LTS, Spring Boot 3.5.16, Oracle ATP Cloud, Pure JDBC, Vanilla JS ES Modules, Brevo API y 948 tests.</td>
        <td style="text-align: center;">7</td>
      </tr>
      <tr>
        <td><strong>Capítulos 5 y 6</strong></td>
        <td><strong>Comparativa, Seguridad y Cierre</strong></td>
        <td>Matriz Antes vs. Después en Valledupar, modelo de auditoría sin datos clínicos, Habeas Data y conclusiones estratégicas.</td>
        <td style="text-align: center;">8</td>
      </tr>
    </tbody>
  </table>
</div>

<!-- ==========================================
     PÁGINA 3: CAPÍTULO 1 - ¿QUÉ ES MEDITRIAJE 2.0?
=========================================== -->
<div class="doc-page">
  <div class="page-nav-header">
    <span><strong>MediTriaje 2.0</strong> · Dossier Técnico Institucional</span>
    <span>Capítulo 1: Identidad, Origen y Principios</span>
  </div>

  <h1>Capítulo 1: ¿Qué es MediTriaje 2.0?</h1>

  <h2>1.1 Definición e Identidad del Proyecto</h2>
  <p>
    <strong>MediTriaje 2.0</strong> es una plataforma web modular concebida para transformar integralmente el acceso, orientación, atención y seguimiento de los pacientes en el sistema de salud colombiano. Desarrollada bajo los principios de <em>Clean Architecture</em> y <em>Security by Design</em>, la plataforma acompaña al ciudadano y al equipo asistencial a lo largo de todas las etapas del proceso de atención médica.
  </p>

  <h2>1.2 Antecedente y Evolución Arquitectónica</h2>
  <p>
    El sistema tiene su génesis en un proyecto académico original construido en Java 17 de escritorio con JavaFX y almacenamiento en archivos planos JSON. Si bien demostró la viabilidad de enlazar la orientación de síntomas con el agendamiento de citas, carecía de concurrencia real, seguridad criptográfica, escalabilidad hospitalaria y persistencia relacional transaccional.
  </p>
  <p>
    <strong>MediTriaje 2.0 fue diseñado y reconstruido completamente desde cero</strong>, sustituyendo la aplicación monousuario de escritorio por una arquitectura web distribuida de alto rendimiento:
  </p>

  <div class="grid-2">
    <div class="card">
      <h4 style="color: #475569;">MediTriaje 1.0 (Antecedente Académico)</h4>
      <ul>
        <li>Aplicación monousuario de escritorio (JavaFX 17).</li>
        <li>Persistencia en ficheros JSON locales (Gson).</li>
        <li>Sin soporte de concurrencia ni bloqueo de turnos.</li>
        <li>Sin auditoría inmutable ni separación de privilegios.</li>
        <li>Sin integración con bases de datos en la nube.</li>
      </ul>
    </div>
    <div class="card card-highlight">
      <h4 style="color: #0F766E;">MediTriaje 2.0 (Plataforma Actual)</h4>
      <ul>
        <li>Plataforma Web Distribuida SPA (Vanilla ES Modules).</li>
        <li>Backend RESTful en Java 21 LTS y Spring Boot 3.5.16.</li>
        <li>Base de Datos Oracle Autonomous Database (ATP Cloud).</li>
        <li>Segregación de roles (Paciente, Profesional, Farmacia, Admin).</li>
        <li>Inmutabilidad garantizada por triggers PL/SQL en base de datos.</li>
      </ul>
    </div>
  </div>

  <h2>1.3 El Ciclo de Atención Integral del Paciente (9 Eslabones)</h2>
  <p>
    A diferencia de soluciones convencionales que operan como simples agendas de citas, MediTriaje 2.0 estructura el recorrido asistencial en 9 etapas continuas y coordinadas:
  </p>

  <div class="wf-grid">
    <div class="wf-box">
      <span class="wf-step">1. Triaje</span>
      Autoevaluación de síntomas e intensidad
    </div>
    <div class="wf-box">
      <span class="wf-step">2. Ruta</span>
      Asignación I–V o corte a Urgencias (123)
    </div>
    <div class="wf-box">
      <span class="wf-step">3. Reserva</span>
      Agendamiento en slot libre sin colisión
    </div>
    <div class="wf-box">
      <span class="wf-step">4. Atención</span>
      Signos vitales, evolución y CIE-10
    </div>
    <div class="wf-box">
      <span class="wf-step">5. Historia</span>
      Cierre inmutable y enmiendas auditadas
    </div>
  </div>
  <div class="wf-grid-4">
    <div class="wf-box">
      <span class="wf-step">6. Prescripción</span>
      Receta atómica con alerta de alergias
    </div>
    <div class="wf-box">
      <span class="wf-step">7. Farmacia</span>
      Dispensación con lote INVIMA y saldo
    </div>
    <div class="wf-box">
      <span class="wf-step">8. Seguimiento</span>
      Tareas post-atención y recordatorios
    </div>
    <div class="wf-box">
      <span class="wf-step">9. Resumen QR</span>
      Token temporal para emergencias
    </div>
  </div>

  <h2>1.4 Principios Fundamentales No Negociables</h2>
  <div class="card card-accent" style="margin-bottom: 0;">
    <ul>
      <li><strong>Soberanía del Backend:</strong> Toda decisión de autorización reside en el servidor (<code>autenticado != autorizado</code>). Un paciente nunca accede a datos de otro paciente.</li>
      <li><strong>Aislamiento Asistencial:</strong> Personal administrativo tiene estrictamente vedado el acceso a historias clínicas (403 Forbidden). Médicos requieren relación asistencial vigente.</li>
      <li><strong>Inmutabilidad Legal (Res. 1995/1999):</strong> Los registros clínicos cerrados jamás se modifican ni se borran. Las correcciones se realizan exclusivamente mediante enmiendas cronológicas sucesivas.</li>
      <li><strong>Privacidad de Datos:</strong> Prohibición absoluta de secretos en el código y de contenido clínico o síntomas en logs o auditoría.</li>
    </ul>
  </div>
</div>

<!-- ==========================================
     PÁGINA 4: CAPÍTULO 2 - FUNCIONALIDADES (PARTE 1)
=========================================== -->
<div class="doc-page">
  <div class="page-nav-header">
    <span><strong>MediTriaje 2.0</strong> · Dossier Técnico Institucional</span>
    <span>Capítulo 2: Catálogo Funcional (Módulos 1 a 3)</span>
  </div>

  <h1>Capítulo 2: ¿Qué hace el Sistema? (Parte 1)</h1>

  <h2>2.1 Módulo de Identidad, Autenticación y Privacidad</h2>
  <div class="feat-item">
    <div class="feat-title">Validación Estricta de Identidad Colombiana y Coherencia Etaria</div>
    <p>
      Valida en memoria el formato de documentos según la Registraduría Nacional: <strong>Cédula de Ciudadanía (CC)</strong> (≥ 18 años), <strong>Tarjeta de Identidad (TI)</strong> (7 a 17 años), <strong>Registro Civil (RC)</strong> (&lt; 7 años), <strong>Cédula de Extranjería (CE)</strong> y <strong>Pasaporte (PA)</strong>. Bloquea inconsistencias biológicas y fechas futuras sin depender de servicios externos.
    </p>
  </div>

  <div class="feat-item">
    <div class="feat-title">Consentimiento Informado Ley 1581 de 2012 (Habeas Data)</div>
    <p>
      El registro exige la aceptación formal del tratamiento de datos sensibles de salud. El consentimiento queda archivado con marca temporal exacta y versión inmutable del texto legal.
    </p>
  </div>

  <div class="feat-item">
    <div class="feat-title">Sesiones Seguras sin localStorage y Autenticación Multifactor (MFA TOTP)</div>
    <p>
      Emisión de <code>access_token</code> JWT de 15 min y <code>refresh_token</code> rotativo de 7 días confinados en cookies <code>HttpOnly; Secure; SameSite=Strict</code> (inmunes a robo XSS). Contraseñas protegidas con <strong>Argon2id</strong> y bloqueo tras 5 intentos fallidos. Incorpora <strong>MFA TOTP (RFC 6238)</strong> compatible con Google y Microsoft Authenticator para profesionales y administradores, con 8 códigos de respaldo cifrados.
    </p>
  </div>

  <h2>2.2 Motor de Triaje Determinista y Corte de Emergencia</h2>
  <p>
    El motor de triaje evalúa síntomas seleccionados del catálogo, duración en horas e intensidad analógica (0 a 10), determinando la prioridad asistencial según la <strong>Resolución 5596 de 2015 del Ministerio de Salud y Protección Social</strong>:
  </p>

  <table>
    <thead>
      <tr>
        <th style="width: 13%;">Nivel</th>
        <th style="width: 18%;">Denominación</th>
        <th style="width: 32%;">Definición Clínica</th>
        <th style="width: 37%;">Ruta Asistencial Derivada</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td><span class="badge badge-triage-1">Nivel I</span></td>
        <td><strong>Reanimación</strong></td>
        <td>Riesgo vital inminente. Requiere maniobras inmediatas.</td>
        <td><strong>Corte de Emergencia:</strong> Llamado al 123 / Urgencias inmediatas. No ofrece cita web.</td>
      </tr>
      <tr>
        <td><span class="badge badge-triage-2">Nivel II</span></td>
        <td><strong>Emergencia</strong></td>
        <td>Riesgo vital potencial o dolor extremo agudo.</td>
        <td><strong>Atención Prioritaria Inmediata:</strong> Derivación urgente a sala asistencial.</td>
      </tr>
      <tr>
        <td><span class="badge badge-triage-3">Nivel III</span></td>
        <td><strong>Urgencia</strong></td>
        <td>Condición clínica aguda que amerita examen y paraclínicos.</td>
        <td><strong>Cita Presencial Rápida:</strong> Oferta preferente en turnos disponibles.</td>
      </tr>
      <tr>
        <td><span class="badge badge-triage-4">Nivel IV</span></td>
        <td><strong>Prioritaria</strong></td>
        <td>Condición médica sin deterioro agudo de signos vitales.</td>
        <td><strong>Consulta Ambulatoria / Telemedicina:</strong> Agendamiento programado.</td>
      </tr>
      <tr>
        <td><span class="badge badge-triage-5">Nivel V</span></td>
        <td><strong>No Urgente</strong></td>
        <td>Problema crónico o sintomatología residual leve.</td>
        <td><strong>Consulta Externa Programada:</strong> Agendamiento regular preventivo.</td>
      </tr>
    </tbody>
  </table>

  <div class="card card-alert">
    <div style="font-weight: 700; color: #991B1B; font-size: 8.8pt; margin-bottom: 2px;">Protocolo de Corte de Emergencia Infalible (ADR-009)</div>
    <p style="font-size: 8.5pt; margin-bottom: 0;">
      Si el paciente reporta un síntoma con <code>ES_ALARMA = 1</code> (dolor torácico opresivo, pérdida súbita de conciencia, asfixia severa) o el cálculo resulta en Nivel I, el sistema <strong>interrumpe de inmediato el agendamiento ordinario</strong>, proyecta advertencias con teléfonos de auxilio (123) y remite al centro hospitalario más cercano, auditando el evento sin registrar datos sensibles.
    </p>
  </div>

  <h2>2.3 Disponibilidad y Agendamiento Concurrente</h2>
  <div class="feat-item" style="margin-bottom: 0;">
    <div class="feat-title">Control de Doble Reserva Atómica en Oracle ATP y Cancelación Autónoma</div>
    <p>
      Generación administrativa de slots libres por sede, médico y especialidad. La prevención de doble reserva opera con una transacción atómica y un <strong>índice único funcional en Oracle ATP</strong> (<code>uq_cita_slot_activa</code>); ante intentos simultáneos, solo una reserva prospera y la otra recibe error <code>409 ConflictoOperacion</code>. El paciente puede cancelar autónomamente hasta con <strong>2 horas de anticipación</strong>, liberando el slot a la comunidad.
    </p>
  </div>
</div>

<!-- ==========================================
     PÁGINA 5: CAPÍTULO 2 - FUNCIONALIDADES (PARTE 2)
=========================================== -->
<div class="doc-page">
  <div class="page-nav-header">
    <span><strong>MediTriaje 2.0</strong> · Dossier Técnico Institucional</span>
    <span>Capítulo 2: Catálogo Funcional (Módulos 4 a 8)</span>
  </div>

  <h1>Capítulo 2: ¿Qué hace el Sistema? (Parte 2)</h1>

  <h2>2.4 Módulo de Atención Médica e Historia Clínica Inmutable</h2>
  <div class="feat-item">
    <div class="feat-title">Validación de Relación Asistencial y Diagnósticos CIE-10 (ADR-007)</div>
    <p>
      Conforme a <code>AccesoClinicoService</code>, un profesional solo puede examinar el historial de un paciente si posee una cita programada activa o una atención propia registrada en los últimos 12 meses. La consulta médica consolida constantes fisiológicas completas (TA, FC, FR, SpO2, Temp, IMC), notas de evolución y diagnósticos estandarizados bajo el catálogo internacional <strong>CIE-10 de la OMS</strong>, con verificación obligatoria de alergias previas.
    </p>
  </div>

  <div class="feat-item">
    <div class="feat-title">Inmutabilidad por Triggers PL/SQL y Enmiendas Cronológicas (ADR-008)</div>
    <p>
      Al cerrarse la atención médica, la fila queda sellada a perpetuidad en Oracle ATP. El trigger <code>TR_ATENCION_INMUTABILIDAD</code> bloquea cualquier instrucción <code>UPDATE</code> o <code>DELETE</code> arrojando error fatal (<code>ORA-20001</code>). En estricto apego a la <strong>Resolución 1995 de 1999</strong>, cualquier corrección o aclaración clínica posterior se registra mediante <strong>enmiendas sucesivas auditadas</strong> (*append-only*), preservando la fidelidad legal del expediente.
    </p>
  </div>

  <h2>2.5 Módulo de Prescripción y Farmacia Hospitalaria</h2>
  <div class="feat-item">
    <div class="feat-title">Prescripción Atómica con Copia Congelada (Snapshot Farmacológico)</div>
    <p>
      La formulación médica valida previamente las alergias del paciente. La receta se emite en una transacción atómica (todo o nada) que guarda una copia congelada (snapshot) del medicamento, concentración, dosis, vía y duración, blindando el registro frente a futuros cambios del catálogo.
    </p>
  </div>

  <div class="feat-item">
    <div class="feat-title">Dispensación Farmacéutica con Rol Especializado y Trazabilidad INVIMA (ADR-016)</div>
    <p>
      El rol <code>ROLE_FARMACEUTICO</code> permite al regente de farmacia buscar recetas vigentes y asentar dispensaciones, pero con <strong>acceso estrictamente bloqueado a notas de evolución médica e historias clínicas</strong>. Soporta entregas parciales y totales calculando saldos matemáticos en tiempo real (<code>saldo = cantidad_prescrita - cantidad_entregada</code>), validando la vigencia máxima en días y registrando lote y vencimiento oficial INVIMA. Cada receta genera un <strong>código legible de reclamación</strong> (<code>REC-XXXXXXXX</code>).
    </p>
  </div>

  <h2>2.6 Módulos Asistenciales de Extensión</h2>
  <div class="grid-2">
    <div class="card">
      <h4 style="color: #0F766E;">Seguimiento y Recordatorios</h4>
      <p style="font-size: 8.5pt;">
        El médico formula tareas de seguimiento (control médico, evolución de síntomas, exámenes y adherencia). La plataforma envía confirmaciones y recordatorios por correo con plantillas HTML institucionales mediante la API HTTP de Brevo.
      </p>
    </div>
    <div class="card">
      <h4 style="color: #0F766E;">Acceso Break-Glass (Urgencia Vital)</h4>
      <p style="font-size: 8.5pt;">
        Protocolo excepcional de emergencia para médicos ante pacientes inconscientes o en shock. Otorga una ventana temporal de 24 horas a la historia clínica, exigiendo justificación obligatoria (≥ 20 caracteres) y auditoría reforzada inmutable.
      </p>
    </div>
  </div>

  <div class="grid-2" style="margin-bottom: 0;">
    <div class="card">
      <h4 style="color: #0F766E;">Métricas Operativas Anónimas</h4>
      <p style="font-size: 8.5pt;">
        Tablero administrativo con indicadores de gestión hospitalaria: tasas de cumplimiento y cancelación, distribución de triaje I–V, demanda por especialidad y balance de farmacia, exportables en CSV sin exponer datos de salud.
      </p>
    </div>
    <div class="card">
      <h4 style="color: #0F766E;">Asistente Virtual Institucional</h4>
      <p style="font-size: 8.5pt;">
        Chatbot determinista para orientación al usuario sobre el uso de la plataforma, preparación para consultas y reclamo de fórmulas, equipado con detector de frases de alarma que remite a la línea de emergencias 123.
      </p>
    </div>
  </div>
</div>

<!-- ==========================================
     PÁGINA 6: CAPÍTULO 3 - IMPACTO EN VALLEDUPAR
=========================================== -->
<div class="doc-page">
  <div class="page-nav-header">
    <span><strong>MediTriaje 2.0</strong> · Dossier Técnico Institucional</span>
    <span>Capítulo 3: Impacto Sanitario en Valledupar y el Cesar</span>
  </div>

  <h1>Capítulo 3: ¿Qué mejora para Valledupar al aplicarlo?</h1>

  <h2>3.1 Contexto de la Red Sanitaria de Valledupar</h2>
  <p>
    El municipio de <strong>Valledupar</strong> cuenta con una población que supera los 550.000 habitantes, caracterizada por una alta densidad en su casco urbano y una extensa red de corregimientos y zonas rurales dispersas. La prestación pública de servicios de salud recae estructuralmente sobre dos entidades esenciales:
  </p>
  <ul>
    <li><strong>Hospital Rosario Pumarejo de López (HRPL):</strong> Principal centro hospitalario de mediana y alta complejidad del departamento del Cesar, centro de referencia para urgencias traumáticas, pediátricas y ginecoobstétricas de toda la región.</li>
    <li><strong>Empresa Social del Estado (ESE) Hospital Eduardo Arredondo Daza (HEAD):</strong> Red pública de baja complejidad encargada de la atención primaria municipal, operando mediante sus sedes urbanas (Sede CDV, Sede San Martín, Sede La Nevada, Sede Rafael Valle Meza) y sus centros de salud satélites en corregimientos.</li>
  </ul>

  <h2>3.2 Desafíos Críticos del Modelo Asistencial Tradicional</h2>
  <div class="card card-alert">
    <p style="margin-bottom: 3px;">
      Históricamente, la red asistencial de Valledupar ha enfrentado cuellos de botella que deterioran la calidad del servicio:
    </p>
    <ul style="margin-bottom: 0;">
      <li><strong>Saturación de salas de urgencias por patologías no urgentes:</strong> Usuarios con cuadros leves (Nivel IV y V) acuden a urgencias del HRPL o del HEAD por falta de citas previas, provocando colapsos de camillas y demoras de hasta 8 horas.</li>
      <li><strong>Filas de madrugada e intermediación:</strong> Ciudadanos obligados a hacer fila desde las 4:00 a.m. a las afueras de los centros de salud para alcanzar una ficha de medicina general.</li>
      <li><strong>Desarticulación rural:</strong> Familias de corregimientos como Badillo, Patillal, Atánquez, Mariangola o Aguas Blancas viajan horas a la ciudad sin certeza de encontrar cupo médico disponible.</li>
      <li><strong>Ausentismo de citas:</strong> Pérdida de turnos médicos por inasistencia sin canales ágiles de cancelación y reasignación comunitaria.</li>
    </ul>
  </div>

  <h2>3.3 Los 5 Pilares de Transformación con MediTriaje 2.0</h2>
  <div class="grid-2">
    <div class="card card-highlight">
      <div class="feat-title">1. Descongestión Efectiva de Urgencias Hospitalarias</div>
      <p style="font-size: 8.5pt;">
        Al realizar el autotriaje en línea alineado con la Resolución 5596, los pacientes Nivel IV o V son encauzados a citas ambulatorias o prioritarias. Esto <strong>filtra la demanda innecesaria antes de que la persona se desplace físicamente al hospital</strong>, liberando camillas y médicos en el HRPL y HEAD para atender urgencias vitales reales (Nivel I y II).
      </p>
    </div>

    <div class="card card-highlight">
      <div class="feat-title">2. Dignificación del Usuario y Fin a las Filas de Madrugada</div>
      <p style="font-size: 8.5pt;">
        MediTriaje 2.0 traslada la asignación de turnos a una plataforma web disponible 24/7. Los usuarios consultan en tiempo real los slots libres por sede (CDV, La Nevada, San Martín, etc.) y médico tratante, reservando su cita en 30 segundos desde su celular, erradicando las filas a la intemperie y la intermediación informal.
      </p>
    </div>
  </div>

  <div class="grid-2">
    <div class="card card-highlight">
      <div class="feat-title">3. Equidad y Acceso para Corregimientos y Zona Rural</div>
      <p style="font-size: 8.5pt;">
        El habitante rural de Mariangola, Aguas Blancas o Patillal ya no viaja a ciegas a Valledupar. Desde su corregimiento evalúa sus síntomas, programa su consulta con fecha y hora confirmada o accede a modalidades de telemedicina asistida, minimizando el gasto de transporte y asegurando una atención digna y programada.
      </p>
    </div>

    <div class="card card-highlight">
      <div class="feat-title">4. Optimización de Agendas y Reducción del Desperdicio</div>
      <p style="font-size: 8.5pt;">
        Con la regla de cancelación autónoma hasta 2 horas antes y el envío automático de recordatorios por correo, se reduce el ausentismo (*no-show*). El turno cancelado vuelve al estado <code>LIBRE</code> inmediatamente en el portal, permitiendo que otro paciente de la comunidad que lo requiera lo reserve al instante.
      </p>
    </div>
  </div>

  <div class="card card-accent" style="margin-bottom: 0;">
    <div class="feat-title">5. Trazabilidad Farmacéutica y Cero Fórmulas Perdidas</div>
    <p style="font-size: 8.5pt; margin-bottom: 0;">
      En las farmacias hospitalarias y dispensarios de Valledupar, el código único de reclamación (<code>REC-XXXXXXXX</code>) vinculado al registro inmutable previene duplicidades, fórmulas adulteradas y extravío de tratamientos. La farmacia registra entregas exactas con lotes INVIMA y fechas de vencimiento, garantizando que el paciente vulnerable reciba la totalidad de sus medicamentos prescritos.
    </p>
  </div>
</div>

<!-- ==========================================
     PÁGINA 7: CAPÍTULO 4 - TECNOLOGÍAS UTILIZADAS
=========================================== -->
<div class="doc-page">
  <div class="page-nav-header">
    <span><strong>MediTriaje 2.0</strong> · Dossier Técnico Institucional</span>
    <span>Capítulo 4: Especificación Tecnológica y Arquitectura</span>
  </div>

  <h1>Capítulo 4: Tecnologías que Utiliza</h1>

  <h2>4.1 Especificación del Stack Tecnológico Completo</h2>
  <p>
    La arquitectura técnica de MediTriaje 2.0 fue seleccionada bajo criterios de alto rendimiento, bajo consumo de recursos, portabilidad, seguridad criptográfica y total independencia frente a librerías propietarias o frameworks frontend efímeros:
  </p>

  <table>
    <thead>
      <tr>
        <th style="width: 20%;">Capa / Componente</th>
        <th style="width: 30%;">Tecnología o Estándar</th>
        <th style="width: 50%;">Rol Arquitectónico y Justificación</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td><strong>Backend y Lenguaje</strong></td>
        <td><strong>Java 21 LTS + Spring Boot 3.5.16</strong></td>
        <td>Uso intensivo de <em>Java Records</em> inmutables para DTOs, hilos virtuales (<em>Virtual Threads</em>) para concurrencia masiva, inyección de dependencias y transacciones ACID.</td>
      </tr>
      <tr>
        <td><strong>Acceso a Datos</strong></td>
        <td><strong>Spring JDBC (JdbcTemplate puro)</strong></td>
        <td><strong>Cero JPA / Hibernate</strong>. SQL 100% nativo, parametrizado y precompilado. Transparencia absoluta sobre planes de ejecución en Oracle y cero sobrecarga de proxies.</td>
      </tr>
      <tr>
        <td><strong>Base de Datos Cloud</strong></td>
        <td><strong>Oracle Autonomous Database (ATP Cloud)</strong></td>
        <td>Instancia relacional en Oracle Cloud Infrastructure (OCI). Motor autónomo con auto-tuneo, auto-parcheo y respaldo transaccional en la nube.</td>
      </tr>
      <tr>
        <td><strong>Migraciones y Conexión</strong></td>
        <td><strong>Flyway + HikariCP / Oracle UCP</strong></td>
        <td>Control inmutable de esquema (V001 a V018). Pool que inicializa cada conexión física fijando la zona horaria <code>America/Bogota</code> y el esquema activo.</td>
      </tr>
      <tr>
        <td><strong>Seguridad y MFA</strong></td>
        <td><strong>Spring Security 6 + JJWT + Argon2id + TOTP</strong></td>
        <td>Protección CSRF, hashing Argon2id, tokens JWT (15 min) y refresh tokens en cookies HttpOnly SameSite=Strict. MFA TOTP (RFC 6238) con backup codes.</td>
      </tr>
      <tr>
        <td><strong>Frontend Web SPA</strong></td>
        <td><strong>HTML5 + CSS3 + Vanilla ES Modules</strong></td>
        <td><strong>Sin frameworks (React/Angular) ni build tools (Vite/Webpack)</strong>. Carga instantánea, accesibilidad WCAG 2.1 AA y tokens de diseño desacoplados (<code>tokens.css</code>).</td>
      </tr>
      <tr>
        <td><strong>Notificaciones Transaccionales</strong></td>
        <td><strong>Brevo HTTP API + JavaMailSender</strong></td>
        <td>Envío de correos sobre HTTPS puerto 443 (mitiga bloqueo de puertos SMTP en PaaS) con plantillas HTML responsivas para citas y códigos OTP.</td>
      </tr>
      <tr>
        <td><strong>Aseguramiento de Calidad</strong></td>
        <td><strong>JUnit 5, Testcontainers, Playwright</strong></td>
        <td>948 pruebas automatizadas: pruebas unitarias de aislamiento, integración contra base de datos real en Docker y pruebas E2E de navegador en vivo.</td>
      </tr>
    </tbody>
  </table>

  <h2>4.2 Arquitectura de Persistencia y Segregación de Privilegios</h2>
  <div class="card card-highlight" style="margin-bottom: 0;">
    <h4 style="color: #0F766E;">Mecanismo de Doble Usuario en Oracle ATP (ADR-012)</h4>
    <p style="font-size: 8.6pt; margin-bottom: 3px;">
      Para garantizar máxima seguridad a nivel del motor de base de datos, MediTriaje 2.0 implementa una estricta segregación de usuarios:
    </p>
    <ul style="margin-bottom: 0;">
      <li><strong><code>MEDITRIAJE_OWNER</code>:</strong> Propietario del esquema completo. Utilizado con exclusividad por Flyway para la ejecución de scripts DDL (tablas, secuencias, índices y triggers PL/SQL). No tiene acceso en runtime desde los servidores web.</li>
      <li><strong><code>MEDITRIAJE_APP</code>:</strong> Usuario de ejecución de la aplicación web en tiempo de ejecución. Cuenta con privilegios mínimos (<code>SELECT</code>, <code>INSERT</code> y <code>UPDATE</code> condicional). <strong>Tiene estrictamente revocado el permiso <code>DELETE</code> sobre las tablas clínicas</strong> (atenciones, recetas, diagnósticos y signos vitales), garantizando que ni un error en el código ni una inyección SQL accidental puedan borrar registros médicos.</li>
    </ul>
  </div>
</div>

<!-- ==========================================
     PÁGINA 8: COMPARATIVA, AUDITORÍA Y CONCLUSIONES
=========================================== -->
<div class="doc-page">
  <div class="page-nav-header">
    <span><strong>MediTriaje 2.0</strong> · Dossier Técnico Institucional</span>
    <span>Capítulo 5: Matriz Comparativa · Capítulo 6: Conclusiones</span>
  </div>

  <h1>Capítulo 5: Matriz Comparativa: Antes vs. Después</h1>

  <table>
    <thead>
      <tr>
        <th style="width: 20%;">Criterio Operativo</th>
        <th style="width: 38%;">Modelo Tradicional en Valledupar</th>
        <th style="width: 42%;">Modelo con MediTriaje 2.0</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td><strong>Acceso y Solicitud de Citas</strong></td>
        <td>Filas presenciales de madrugada desde las 4:00 a.m. en las sedes del HEAD o call centers colapsados.</td>
        <td>Autogestión web 24/7 en tiempo real desde el hogar o dispositivo móvil. Confirmación de reserva en 30 segundos.</td>
      </tr>
      <tr>
        <td><strong>Clasificación del Triaje</strong></td>
        <td>Exclusivamente presencial tras varias horas de espera física en la sala de urgencias.</td>
        <td>Orientación y triaje digital preventivo antes del desplazamiento, con corte automático a línea 123 si hay alarma vital.</td>
      </tr>
      <tr>
        <td><strong>Saturación de Urgencias</strong></td>
        <td>Congestión crítica del Hospital Rosario Pumarejo con pacientes Nivel IV y V que no tienen riesgo vital.</td>
        <td>Derivación adecuada de niveles no urgentes hacia turnos ambulatorios y prioritarios, desahogando los pabellones de trauma.</td>
      </tr>
      <tr>
        <td><strong>Atención en Corregimientos</strong></td>
        <td>Desplazamientos ciegos desde Badillo, Patillal o Mariangola, con alta probabilidad de perder el viaje por falta de cupo.</td>
        <td>Consulta previa de disponibilidad y reserva garantizada antes de viajar. Habilitación de rutas de telemedicina.</td>
      </tr>
      <tr>
        <td><strong>Integridad de la Historia Clínica</strong></td>
        <td>Formatos físicos en papel o sistemas legados vulnerables a modificaciones no auditadas o extravío.</td>
        <td>Registros legalmente inmutables en Oracle ATP. Cero eliminación o sobreescritura; correcciones solo vía enmiendas auditadas.</td>
      </tr>
      <tr>
        <td><strong>Dispensación de Medicamentos</strong></td>
        <td>Fórmulas impresas susceptibles de fraude, adulteración o pérdida. Desconocimiento del saldo entregado.</td>
        <td>Código unívoco de reclamación (<code>REC-XXXXXXXX</code>), cálculo estricto de saldo y trazabilidad de lotes y vigencias INVIMA.</td>
      </tr>
      <tr>
        <td><strong>Privacidad de Datos Médicos</strong></td>
        <td>Planillas expuestas en ventanillas asistenciales y personal administrativo con acceso a información confidencial.</td>
        <td>Segregación absoluta: personal administrativo bloqueado (403). Relación asistencial obligatoria para el cuerpo médico.</td>
      </tr>
    </tbody>
  </table>

  <h1>Capítulo 6: Seguridad, Auditoría y Conclusiones</h1>

  <h2>6.1 Trazabilidad Inmutable sin Fuga de Datos Clínicos (ADR-011)</h2>
  <p>
    Toda acción sensible en MediTriaje 2.0 queda registrada en una bitácora inmutable (<code>AUDITORIA</code>): inicios de sesión, cierres de atención médica, expedición de recetas, dispensaciones y activaciones de emergencia <em>Break-Glass</em>. En cumplimiento estricto del principio de <strong>privacidad por diseño</strong>, la auditoría almacena usuario, IP, acción, timestamp y resultado, pero <strong>prohíbe tajantemente registrar diagnósticos médicos, síntomas o nombres de medicamentos</strong> en las bitácoras o logs del servidor.
  </p>

  <h2>6.2 Conclusión Estratégica y Hoja de Ruta</h2>
  <div class="card card-highlight" style="margin-bottom: 0;">
    <p style="margin-bottom: 5px;">
      <strong>MediTriaje 2.0</strong> no es un simple desarrollo informático, sino un <strong>activo tecnológico estratégico de salud pública</strong> para el municipio de Valledupar. Al unificar la orientación temprana de síntomas, la asignación eficiente de citas, el rigor clínico inmutable y la transparencia en la entrega de fármacos, la plataforma dignifica la experiencia del paciente cesarense, optimiza los recursos públicos del Hospital Rosario Pumarejo de López y la ESE Hospital Eduardo Arredondo Daza, y establece un estándar de ingeniería médica alineado con las más rigurosas exigencias normativas de la República de Colombia.
    </p>
    <div style="display: flex; justify-content: space-between; align-items: center; border-top: 1px solid #99F6E4; padding-top: 5px; font-size: 7.8pt; color: #0F766E;">
      <span>Preparado para Interoperabilidad Nacional (Ley 2015 de 2020)</span>
      <strong>Equipo de Arquitectura e Ingeniería de Software MediTriaje 2.0</strong>
    </div>
  </div>
</div>

</body>
</html>
"""

html_path = os.path.abspath("MediTriaje_2.0_Dossier_Integral_Valledupar.html")
pdf_path = os.path.abspath("MediTriaje_2.0_Dossier_Integral_Valledupar.pdf")

with open(html_path, "w", encoding="utf-8") as f:
    f.write(html_template)

print(f"HTML generado exitosamente en: {html_path}")

# Run Edge headless print
edge_path = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
file_url = "file:///" + html_path.replace("\\", "/")

cmd = [
    edge_path,
    "--headless=new",
    "--disable-gpu",
    "--no-pdf-header-footer",
    f"--print-to-pdf={pdf_path}",
    file_url
]

print("Compilando PDF con Microsoft Edge Headless...")
result = subprocess.run(cmd, capture_output=True, text=True)

if os.path.exists(pdf_path):
    size_kb = os.path.getsize(pdf_path) / 1024
    print(f"PDF generado con exito: {pdf_path} ({size_kb:.2f} KB)")
    
    # Inspect with fitz
    doc = fitz.open(pdf_path)
    print(f"TOTAL PAGINAS EXACTAS EN PDF: {len(doc)}")
    for i, page in enumerate(doc):
        text = page.get_text()
        first_line = text.strip().split('\n')[0] if text.strip() else 'EMPTY'
        last_line = text.strip().split('\n')[-1] if text.strip() else 'EMPTY'
        print(f"  Página {i+1}: longitud={len(text)} chars | Inicio: [{first_line[:35]}] | Fin: [{last_line[-35:]}]")
    
    # Re-render page 7 to verify
    doc[6].get_pixmap(dpi=150).save("page_7_fixed.png")
    doc.close()
else:
    print(f"Error generando PDF: {result.stderr}")
    sys.exit(1)
