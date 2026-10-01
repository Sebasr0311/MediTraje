MEDiTRIAJE 2.0
DOCUMENTO MAESTRO TÉCNICO Y FUNCIONAL
Especificación base para desarrollo desde cero mediante CLI

VERSIÓN: 1.0
ESTADO: Base de trabajo
PROPÓSITO: Servir como fuente de verdad para diseño, implementación, revisión y evolución del sistema.


======================================================================
1. IDENTIDAD Y PROPÓSITO DEL PROYECTO
======================================================================

Nombre:
MediTriaje 2.0

Descripción general:
MediTriaje 2.0 será una plataforma web para orientar y gestionar el acceso del paciente a servicios de salud, integrando triaje/orientación, búsqueda y agendamiento de citas, historia clínica, recetas, seguimiento de tratamientos, seguimiento posterior a la atención y un resumen de salud portátil mediante QR temporal.

El sistema parte del proyecto académico original MediTriaje, cuyo objetivo era integrar triaje digital y agendamiento de citas para optimizar la atención, reducir tiempos de espera, mejorar la organización y facilitar el acceso de los usuarios.

La versión 2.0 NO será una simple migración del proyecto anterior. Se construirá desde cero, tomando el proyecto original como antecedente funcional y documental, conservando sus conceptos útiles, corrigiendo decisiones débiles y agregando funcionalidades nuevas.

Objetivo principal:
Construir un sistema web moderno, modular, seguro, mantenible y escalable que acompañe al paciente durante el ciclo de atención:

ORIENTACIÓN/TRIAJE
        ↓
RUTA DE ATENCIÓN
        ↓
DISPONIBILIDAD/CITA
        ↓
ATENCIÓN
        ↓
HISTORIA CLÍNICA
        ↓
RECETA/TRATAMIENTO
        ↓
DISPENSACIÓN
        ↓
SEGUIMIENTO
        ↓
RESUMEN DE SALUD / QR


======================================================================
2. ANTECEDENTE: MEDiTRIAJE ORIGINAL
======================================================================

El proyecto original fue desarrollado como un sistema de triaje y agendamiento de citas médicas.

Conceptos originales que se conservan:

- Pacientes.
- Médicos/profesionales.
- Citas.
- Triaje.
- Clasificación de prioridad.
- Usuarios.
- Autenticación.
- Registros de atención.
- Chatbot/asistente.
- Gestión de pacientes.
- Dashboard.
- Arquitectura por capas.
- Separación de presentación, lógica y datos.
- DAO/repositorios.
- Servicios.
- Validaciones.
- Estados de citas.
- Persistencia de información.

El proyecto original utilizaba:
- Java 17.
- JavaFX 17.
- Gson.
- Maven.
- Archivos JSON.
- Arquitectura de tres capas.
- Entidades como Paciente, Doctor, Cita, Triaje, Usuario y RegistroAtencion.

El nuevo proyecto reemplazará la aplicación de escritorio y los archivos JSON por:

- Aplicación web.
- Frontend HTML/CSS/JavaScript Vanilla.
- Backend Java.
- API REST.
- Oracle Autonomous Transaction Processing (ATP) en Oracle Cloud.
- Persistencia relacional.
- Autenticación/autorización.
- Arquitectura modular.

IMPORTANTE:
No copiar directamente clases, tablas ni estructuras del proyecto anterior sin analizarlas.
El proyecto anterior es una FUENTE DE REQUISITOS Y CONTEXTO, no una base de código que deba conservarse.


======================================================================
3. VISIÓN DE MEDiTRIAJE 2.0
======================================================================

La visión es evolucionar de:

"triaje + citas"

a:

"plataforma integral de orientación, acceso y seguimiento de la atención del paciente".

El sistema debe cubrir tres momentos:

A. ANTES DE LA ATENCIÓN
- Registro.
- Orientación.
- Triaje.
- Priorización.
- Ruta sugerida.
- Búsqueda de atención.
- Disponibilidad.
- Cita.

B. DURANTE LA ATENCIÓN
- Historia clínica.
- Registro de atención.
- Diagnósticos registrados por el profesional.
- Signos vitales.
- Indicaciones.
- Recetas.
- Tratamientos.
- Documentos/resultados cuando el alcance lo permita.

C. DESPUÉS DE LA ATENCIÓN
- Recetas.
- Estado de dispensación.
- Tratamientos activos.
- Seguimiento.
- Recordatorios.
- Controles.
- Nueva orientación cuando corresponda.
- Resumen de salud.
- QR temporal.


======================================================================
4. PRINCIPIO MÉDICO-FUNCIONAL IMPORTANTE
======================================================================

El sistema NO debe presentarse como sustituto de un profesional de salud.

La funcionalidad de orientación y triaje tendrá como propósito:

- Recopilar información.
- Clasificar prioridad según reglas configuradas.
- Orientar.
- Sugerir una ruta de atención.
- Facilitar acceso a servicios.
- Alertar sobre la necesidad de valoración profesional según las reglas definidas.

No se debe implementar como objetivo principal un "médico artificial" que diagnostique de forma autónoma o que prescriba medicamentos.

El asistente puede:
- Explicar funcionalidades.
- Orientar al usuario dentro del sistema.
- Recopilar información inicial.
- Ayudar a completar un proceso de triaje.
- Explicar estados de citas/recetas.
- Dar información general configurada.
- Guiar hacia la ruta de atención.

Las decisiones clínicas deben quedar bajo responsabilidad del profesional autorizado cuando corresponda.

Las dosis o modificaciones de tratamiento no deben ser inventadas por el sistema.


======================================================================
5. MÓDULOS DEL SISTEMA
======================================================================

5.1 AUTENTICACIÓN Y SEGURIDAD

Responsabilidades:
- Inicio de sesión.
- Cierre de sesión.
- Gestión de sesión/token.
- Recuperación de acceso si se implementa.
- Control de roles.
- Control de permisos.
- Protección de endpoints.
- Auditoría.

Roles base:
- PACIENTE.
- PROFESIONAL.
- ADMINISTRADOR.

Se podrán agregar roles especializados posteriormente si el alcance lo requiere.


----------------------------------------------------------------------
5.2 DASHBOARD
----------------------------------------------------------------------

Dashboard adaptado al rol.

Paciente:
- Próxima cita.
- Última atención.
- Tratamientos activos.
- Recetas.
- Dispensaciones pendientes.
- Seguimientos pendientes.
- Alertas.
- Acceso a resumen de salud.

Profesional:
- Citas del día.
- Pacientes pendientes.
- Triajes pendientes.
- Atenciones.
- Seguimientos.
- Recetas.

Administrador:
- Usuarios.
- Profesionales.
- Instituciones.
- Especialidades.
- Disponibilidad.
- Citas.
- Indicadores administrativos.


----------------------------------------------------------------------
5.3 PACIENTES
----------------------------------------------------------------------

Información posible:
- Identificación.
- Nombres.
- Apellidos.
- Fecha de nacimiento.
- Datos de contacto.
- Información demográfica necesaria.
- Información de afiliación cuando corresponda.
- Alergias.
- Antecedentes registrados.
- Estado del usuario.

Buenas prácticas:
- Separar identidad/autenticación de información clínica.
- No permitir que un paciente modifique directamente registros clínicos históricos.
- Validar documentos.
- Proteger información personal.


----------------------------------------------------------------------
5.4 PROFESIONALES
----------------------------------------------------------------------

Información:
- Usuario.
- Identificación.
- Nombres.
- Especialidad.
- Registro profesional si el alcance académico lo contempla.
- Institución.
- Estado.
- Disponibilidad.

Funciones:
- Consultar agenda.
- Atender pacientes autorizados.
- Registrar atención.
- Registrar indicaciones.
- Crear recetas.
- Registrar seguimientos.


----------------------------------------------------------------------
5.5 INSTITUCIONES Y SERVICIOS
----------------------------------------------------------------------

Se debe permitir representar:

- Instituciones de salud.
- Sedes.
- Servicios.
- Especialidades.
- Modalidades de atención.
- Disponibilidad.

Esto permite que el sistema no quede amarrado a una única institución.


----------------------------------------------------------------------
5.6 TRIAJE Y ORIENTACIÓN
----------------------------------------------------------------------

Proceso:

1. Paciente inicia triaje.
2. Sistema solicita información.
3. Se registran síntomas.
4. Se registran datos disponibles.
5. Motor de reglas procesa información.
6. Se determina una clasificación de prioridad según reglas definidas.
7. Se genera una orientación.
8. Se determina una ruta sugerida.
9. El resultado queda registrado.
10. Si corresponde, el paciente puede buscar atención/cita.

Datos posibles:
- Síntoma.
- Duración.
- Intensidad.
- Signos vitales.
- Dolor.
- Antecedentes relevantes.
- Alergias.
- Contexto adicional.

La clasificación debe ser configurable y documentada.

No copiar automáticamente reglas médicas del proyecto anterior sin validarlas.
Las reglas deben estar claramente identificadas como reglas de software/prototipo y, si el proyecto requiere validez clínica, deben ser revisadas por personal competente.


----------------------------------------------------------------------
5.7 RUTA DE ATENCIÓN
----------------------------------------------------------------------

El sistema debe evolucionar desde una clasificación simple hacia una orientación de ruta.

Ejemplo conceptual:

TRIAJE
  ↓
PRIORIDAD
  ↓
RUTA
  ├── Atención prioritaria
  ├── Cita presencial
  ├── Modalidad remota cuando corresponda
  └── Seguimiento/consulta programada

La ruta debe depender de reglas configurables.


----------------------------------------------------------------------
5.8 DISPONIBILIDAD INTELIGENTE
----------------------------------------------------------------------

El paciente podrá buscar atención considerando:

- Especialidad.
- Fecha.
- Hora.
- Prioridad.
- Institución.
- Sede.
- Profesional.
- Modalidad.
- Disponibilidad.

El sistema debe devolver opciones reales según la información almacenada.

Ejemplo:

MEDICINA GENERAL
HOY

Opción A
08:30
Presencial
Sede X

Opción B
10:00
Modalidad remota
Sede Y

No inventar disponibilidad.


----------------------------------------------------------------------
5.9 CITAS
----------------------------------------------------------------------

Datos:
- Paciente.
- Profesional.
- Especialidad.
- Institución/sede.
- Fecha.
- Hora.
- Modalidad.
- Motivo.
- Estado.
- Relación con triaje si aplica.

Estados sugeridos:
- PROGRAMADA.
- CONFIRMADA.
- ATENDIDA.
- CANCELADA.
- NO_ASISTIO.
- REPROGRAMADA.

Las transiciones deben estar controladas.

Reglas:
- No permitir doble reserva del mismo recurso/horario.
- Validar disponibilidad.
- Validar que el profesional corresponda a la especialidad.
- Validar que el paciente pueda reservar según reglas.
- Registrar auditoría de operaciones relevantes.


----------------------------------------------------------------------
5.10 HISTORIA CLÍNICA
----------------------------------------------------------------------

Módulo central de MediTriaje 2.0.

Debe permitir almacenar y consultar información clínica estructurada.

Conceptos:
- Historia clínica del paciente.
- Atenciones.
- Triajes.
- Antecedentes.
- Alergias.
- Signos vitales.
- Diagnósticos registrados.
- Indicaciones.
- Recetas.
- Seguimientos.

Principio:
La historia clínica es información sensible.

Por tanto:
- Control de acceso obligatorio.
- Auditoría.
- Mínimo privilegio.
- No exposición innecesaria en respuestas API.
- No registrar datos clínicos en logs comunes.
- No permitir modificaciones arbitrarias.


----------------------------------------------------------------------
5.11 ATENCIONES
----------------------------------------------------------------------

Una atención representa el encuentro asistencial registrado.

Puede contener:
- Paciente.
- Profesional.
- Cita.
- Fecha/hora.
- Motivo.
- Evolución.
- Signos vitales.
- Diagnóstico registrado.
- Indicaciones.
- Observaciones.
- Estado.

El profesional autorizado es quien registra la información clínica.


----------------------------------------------------------------------
5.12 RECETAS
----------------------------------------------------------------------

Una receta estará relacionada con una atención/profesional/paciente.

Puede contener:
- Fecha.
- Profesional.
- Atención.
- Estado.
- Indicaciones generales.

Los detalles pueden contener:
- Medicamento.
- Presentación.
- Cantidad.
- Indicaciones.
- Duración cuando corresponda.

Estados:
- ACTIVA.
- FINALIZADA.
- CANCELADA.
- DISPENSADA_PARCIALMENTE si se necesita.

El sistema no debe inventar recetas.


----------------------------------------------------------------------
5.13 MEDICAMENTOS
----------------------------------------------------------------------

Catálogo controlado.

Datos posibles:
- Código.
- Nombre.
- Principio activo.
- Presentación.
- Estado.

No almacenar información redundante innecesariamente.

La receta debe conservar el detalle histórico necesario para que cambios futuros en el catálogo no alteren la historia de una receta pasada.


----------------------------------------------------------------------
5.14 DISPENSACIÓN / RECLAMACIÓN
----------------------------------------------------------------------

El sistema debe poder representar si una receta/medicamento fue reclamado cuando esa información sea proporcionada por la fuente autorizada.

Estados posibles:
- PENDIENTE.
- RECLAMADO.
- PARCIAL.
- NO DISPONIBLE.
- CANCELADO.

Datos:
- Receta.
- Medicamento.
- Cantidad formulada.
- Cantidad dispensada cuando esté disponible.
- Fecha.
- Estado.
- Fuente de la información.

No afirmar que un medicamento fue reclamado si el sistema no recibió una confirmación válida.


----------------------------------------------------------------------
5.15 TRATAMIENTOS Y SEGUIMIENTO
----------------------------------------------------------------------

Un tratamiento puede relacionarse con una receta/atención.

El seguimiento puede incluir:
- Tratamiento activo.
- Fecha de inicio.
- Fecha prevista de finalización.
- Estado.
- Próximo control.
- Indicaciones registradas.
- Seguimientos realizados.

Estados:
- ACTIVO.
- FINALIZADO.
- CANCELADO.
- PAUSADO cuando esté justificado por el modelo funcional.


----------------------------------------------------------------------
5.16 SEGUIMIENTO POST-ATENCIÓN
----------------------------------------------------------------------

Después de una atención se pueden generar tareas de seguimiento.

Ejemplos:
- Recordatorio de control.
- Seguimiento de tratamiento.
- Confirmación de cita.
- Registro de evolución.
- Pendiente de examen.
- Nueva orientación.

El paciente puede reportar información de seguimiento.

El sistema no debe convertir automáticamente esa información en un diagnóstico.


----------------------------------------------------------------------
5.17 RESUMEN DE SALUD
----------------------------------------------------------------------

Vista resumida de información relevante.

Puede incluir:
- Datos básicos.
- Alergias.
- Antecedentes relevantes.
- Medicamentos/tratamientos activos.
- Últimas atenciones.
- Próximas citas.
- Alertas relevantes.

Debe ser una vista de lectura controlada.


----------------------------------------------------------------------
5.18 QR TEMPORAL
----------------------------------------------------------------------

El QR no debe almacenar directamente toda la historia clínica.

Funcionamiento:

1. Paciente solicita compartir información.
2. Sistema genera token aleatorio.
3. Token tiene fecha de expiración.
4. Se define el alcance de información.
5. Se genera QR.
6. Usuario autorizado escanea.
7. Backend valida token.
8. Backend verifica permisos.
9. Devuelve solamente la información autorizada.
10. Se registra el acceso en auditoría.
11. Token expira o se revoca.

Debe existir:
- Expiración.
- Revocación.
- Alcance.
- Auditoría.
- Protección contra reutilización indebida.


----------------------------------------------------------------------
5.19 ASISTENTE / CHATBOT
----------------------------------------------------------------------

Funciones:
- Navegación.
- Preguntas frecuentes.
- Explicación de módulos.
- Ayuda para agendar.
- Ayuda para entender estados.
- Orientación inicial.
- Apoyo al proceso de triaje.

No debe:
- Sustituir al profesional.
- Inventar diagnósticos.
- Inventar medicamentos.
- Cambiar tratamientos.
- Prometer resultados clínicos.


======================================================================
6. REQUISITOS FUNCIONALES BASE
======================================================================

RF-01 Gestión de usuarios.
RF-02 Autenticación.
RF-03 Autorización por roles.
RF-04 Gestión de pacientes.
RF-05 Gestión de profesionales.
RF-06 Gestión de instituciones.
RF-07 Gestión de especialidades.
RF-08 Gestión de disponibilidad.
RF-09 Registro de triaje.
RF-10 Clasificación de prioridad.
RF-11 Generación de ruta de atención.
RF-12 Consulta de disponibilidad.
RF-13 Creación de citas.
RF-14 Consulta de citas.
RF-15 Cancelación/reprogramación según reglas.
RF-16 Registro de atención.
RF-17 Consulta controlada de historia clínica.
RF-18 Gestión de recetas.
RF-19 Gestión de medicamentos.
RF-20 Gestión de dispensación/reclamación.
RF-21 Gestión de tratamientos.
RF-22 Seguimiento post-atención.
RF-23 Resumen de salud.
RF-24 Generación de QR temporal.
RF-25 Consulta mediante QR autorizado.
RF-26 Auditoría de accesos sensibles.
RF-27 Asistente del sistema.
RF-28 Dashboard por rol.
RF-29 Notificaciones/recordatorios cuando se implemente.
RF-30 Reportes administrativos cuando se implemente.


======================================================================
7. REQUISITOS NO FUNCIONALES
======================================================================

RNF-01 Seguridad.
RNF-02 Disponibilidad.
RNF-03 Rendimiento.
RNF-04 Escalabilidad.
RNF-05 Mantenibilidad.
RNF-06 Usabilidad.
RNF-07 Accesibilidad.
RNF-08 Compatibilidad con navegadores modernos.
RNF-09 Integridad de datos.
RNF-10 Trazabilidad.
RNF-11 Auditoría.
RNF-12 Protección de información sensible.
RNF-13 Modularidad.
RNF-14 Testabilidad.
RNF-15 Documentación.
RNF-16 Configurabilidad.
RNF-17 Recuperación ante errores.


======================================================================
8. ARQUITECTURA
======================================================================

Arquitectura lógica:

FRONTEND
HTML5 + CSS3 + JavaScript Vanilla
        ↓
HTTP/HTTPS
        ↓
API REST
        ↓
BACKEND JAVA
        ↓
CONTROLLERS
        ↓
SERVICES
        ↓
REPOSITORIES/DAO
        ↓
JDBC
        ↓
ORACLE ATP


Estructura conceptual del backend:

backend/
  src/
    main/
      java/
        .../
          config/
          controller/
          dto/
          exception/
          mapper/
          model/
          repository/
          security/
          service/
          util/
      resources/
        application.properties
        db/
        logging/

Frontend:

frontend/
  index.html
  assets/
  css/
  js/
    api/
    components/
    pages/
    services/
    utils/
    state/
    validation/


======================================================================
9. PRINCIPIOS SOLID
======================================================================

S - Single Responsibility Principle
Cada clase debe tener una responsabilidad clara.

Ejemplo:
PacienteService no debe encargarse de:
- SQL.
- Renderizar HTML.
- Autenticación.
- Enviar correos.

O - Open/Closed Principle
Las clases deben poder extenderse sin modificar constantemente código existente.

L - Liskov Substitution Principle
Las implementaciones deben respetar los contratos de sus abstracciones.

I - Interface Segregation Principle
Interfaces pequeñas y específicas.

D - Dependency Inversion Principle
Los servicios no deben depender directamente de implementaciones concretas cuando una abstracción sea apropiada.


======================================================================
10. GRASP
======================================================================

Los principios GRASP serán parte explícita del diseño.

10.1 INFORMATION EXPERT
La responsabilidad debe estar en el objeto que posee la información necesaria.

Ejemplo:
La entidad Cita o un servicio especializado relacionado debe conocer las reglas de estado de una cita según el diseño final.

10.2 CREATOR
Asignar creación a la clase que tiene la información o relación necesaria para crear el objeto.

10.3 CONTROLLER
Los controllers REST reciben solicitudes y coordinan el caso de uso.
No deben contener toda la lógica de negocio.

Ejemplo:
CitaController → CitaService → CitaRepository.

10.4 LOW COUPLING
Reducir dependencias innecesarias entre módulos.

10.5 HIGH COHESION
Cada clase/módulo debe concentrarse en responsabilidades relacionadas.

10.6 POLYMORPHISM
Utilizar polimorfismo cuando existan comportamientos variables.

10.7 PURE FABRICATION
Crear servicios/repositorios/clases auxiliares cuando una responsabilidad no corresponda naturalmente a una entidad de dominio.

10.8 INDIRECTION
Utilizar capas/interfaces para evitar acoplamiento directo.

10.9 PROTECTED VARIATIONS
Proteger partes susceptibles a cambios mediante interfaces/configuración.

Ejemplo:
Motor de reglas de triaje desacoplado de Controller y persistencia.


======================================================================
11. PATRONES Y PRINCIPIOS ADICIONALES
======================================================================

Patrones potenciales:
- Repository/DAO.
- Service Layer.
- DTO.
- Mapper.
- Factory cuando exista creación variable.
- Strategy para reglas de triaje o políticas variables.
- Builder cuando la construcción compleja lo justifique.
- Dependency Injection/manual DI según tecnología.
- Adapter cuando se integren servicios externos.
- Specification/Query Object si la búsqueda se vuelve compleja.

NO aplicar patrones solamente por "tener patrones".
Cada patrón debe resolver un problema real.


======================================================================
12. BACKEND JAVA
======================================================================

El backend será responsable de:

- API REST.
- Validaciones.
- Autenticación.
- Autorización.
- Casos de uso.
- Reglas de negocio.
- Transacciones.
- Persistencia.
- Auditoría.
- Manejo de errores.

Separación:

Controller:
HTTP/API.

DTO:
Entrada/salida de API.

Service:
Casos de uso y reglas.

Repository:
Persistencia.

Model:
Dominio/entidades.

Security:
Autenticación/autorización.

Exception:
Errores controlados.

Mapper:
Conversión entre entidades y DTO.


======================================================================
13. API REST
======================================================================

Diseñar endpoints por recursos y casos de uso.

Ejemplos conceptuales:

POST   /api/auth/login
POST   /api/auth/logout

GET    /api/patients/me
GET    /api/patients/me/history
GET    /api/patients/me/prescriptions
GET    /api/patients/me/treatments
GET    /api/patients/me/appointments

POST   /api/triage
GET    /api/triage/{id}

GET    /api/availability
POST   /api/appointments
GET    /api/appointments/{id}
PATCH  /api/appointments/{id}/cancel

POST   /api/attentions
GET    /api/attentions/{id}

POST   /api/prescriptions
GET    /api/prescriptions/{id}

GET    /api/medications

POST   /api/follow-ups
GET    /api/follow-ups

POST   /api/health-summary/qr
POST   /api/health-summary/qr/{token}/revoke

Los endpoints definitivos se definirán después de cerrar casos de uso y modelo de datos.


======================================================================
14. API: BUENAS PRÁCTICAS
======================================================================

- Versionar API si el proyecto lo requiere.
- Validar DTOs.
- No exponer entidades directamente.
- No devolver información clínica innecesaria.
- HTTP status correctos.
- Respuestas consistentes.
- Errores estructurados.
- Paginación.
- Filtros.
- Ordenamiento controlado.
- Límites de tamaño.
- Validación de entrada.
- Protección contra inyección.
- Logs seguros.
- CORS configurado explícitamente.
- HTTPS en producción.


======================================================================
15. BASE DE DATOS ORACLE ATP
======================================================================

La base de datos será Oracle Autonomous Transaction Processing (ATP) en Oracle Cloud.

Objetivos:
- Integridad.
- Seguridad.
- Persistencia.
- Transacciones.
- Relaciones.
- Consultas eficientes.
- Auditoría.
- Escalabilidad.

No utilizar JSON como mecanismo principal de persistencia.


======================================================================
16. MODELO DE DATOS PROPUESTO
======================================================================

Las siguientes entidades son una propuesta inicial y NO deben convertirse automáticamente en DDL hasta validar el modelo.

SEGURIDAD:
- USUARIO
- ROL
- PERMISO
- USUARIO_ROL
- ROL_PERMISO
- SESION/TOKEN si aplica

PERSONAS:
- PACIENTE
- PROFESIONAL
- ESPECIALIDAD

INSTITUCIONES:
- INSTITUCION
- SEDE
- SERVICIO
- DISPONIBILIDAD

ATENCIÓN:
- TRIAJE
- SINTOMA
- TRIAJE_SINTOMA
- ATENCION
- SIGNO_VITAL
- ANTECEDENTE
- ALERGIA

CITAS:
- CITA

RECETAS:
- RECETA
- RECETA_DETALLE
- MEDICAMENTO
- DISPENSACION

TRATAMIENTOS:
- TRATAMIENTO
- SEGUIMIENTO

RESUMEN:
- RESUMEN_SALUD
- ACCESO_QR

AUDITORÍA:
- AUDITORIA


======================================================================
17. REGLAS PARA EL MODELO RELACIONAL
======================================================================

- Normalizar hasta 3FN cuando sea apropiado.
- Evitar columnas duplicadas.
- Evitar grupos repetitivos.
- Definir PK para cada entidad.
- Definir FK para relaciones.
- Definir NOT NULL donde sea obligatorio.
- Definir UNIQUE donde corresponda.
- Definir CHECK para estados y valores válidos cuando sea apropiado.
- Definir índices según consultas reales.
- No indexar todo indiscriminadamente.
- Utilizar claves técnicas cuando sea conveniente.
- Mantener claves de negocio con restricciones UNIQUE cuando corresponda.
- Definir ON DELETE con mucho cuidado.
- Evitar eliminación física de información clínica histórica cuando no corresponda.
- Utilizar estados o mecanismos de baja lógica cuando el dominio lo requiera.


======================================================================
18. IDENTIFICADORES
======================================================================

Definir una estrategia consistente.

No mezclar:
- UUID.
- NUMBER autoincremental.
- códigos manuales.

sin una razón.

Para Oracle, seleccionar una estrategia única por tipo de entidad después de evaluar:
- Integridad.
- Rendimiento.
- Exposición en API.
- Seguridad.
- Facilidad de desarrollo.

Los identificadores internos no deben exponer información sensible.


======================================================================
19. FECHAS Y HORAS
======================================================================

Usar tipos Oracle adecuados.

Diferenciar:
- Fecha.
- Fecha/hora.
- Zona horaria.

Las citas y eventos clínicos requieren especial cuidado con zona horaria.

La aplicación debe manejar una zona horaria definida para el proyecto y documentarla.


======================================================================
20. AUDITORÍA
======================================================================

Debe existir auditoría para operaciones sensibles.

Ejemplos:
- Inicio de sesión.
- Acceso a historia clínica.
- Creación/modificación de atención.
- Creación de receta.
- Acceso mediante QR.
- Cambio de permisos.
- Cancelación de citas.
- Cambios administrativos importantes.

Auditoría puede contener:
- Usuario.
- Acción.
- Recurso.
- Identificador del recurso.
- Fecha/hora.
- Resultado.
- Información técnica necesaria.

No guardar secretos ni información clínica completa en logs.


======================================================================
21. SEGURIDAD
======================================================================

Principios:
- Mínimo privilegio.
- Defensa en profundidad.
- Separación de responsabilidades.
- Validación en servidor.
- No confiar en datos del navegador.
- Consultas parametrizadas.
- Contraseñas con hash seguro.
- Tokens protegidos.
- Expiración de sesión.
- Protección de endpoints.
- Control de acceso por recurso.
- Auditoría.

El frontend nunca debe decidir por sí solo si un usuario tiene permiso.

El backend debe validar siempre.


======================================================================
22. HISTORIA CLÍNICA Y CONTROL DE ACCESO
======================================================================

Regla fundamental:

AUTENTICADO != AUTORIZADO.

Ejemplo:

Paciente A:
Puede consultar su propia información.

Paciente A:
NO puede consultar información del paciente B aunque conozca el ID.

Profesional:
Debe tener autorización contextual para consultar información.

Administrador:
No debe recibir automáticamente permisos para consultar contenido clínico.

Las consultas deben aplicar filtros de seguridad en backend.


======================================================================
23. QR Y SEGURIDAD
======================================================================

Nunca:

QR → historia clínica completa.

Preferido:

QR → token aleatorio
       ↓
Backend
       ↓
Validación
       ↓
Permisos
       ↓
Información mínima autorizada

El token:
- Debe ser impredecible.
- Debe expirar.
- Puede revocarse.
- Debe tener alcance.
- Debe registrarse en auditoría.
- No debe reutilizarse indefinidamente.


======================================================================
24. FRONTEND VANILLA
======================================================================

Tecnologías:
- HTML5.
- CSS3.
- JavaScript moderno.

No utilizar un framework SPA como React/Vue/Angular para la primera implementación si el alcance acordado es Vanilla.

El código debe organizarse modularmente.

Ejemplo:

js/
  api/
  components/
  pages/
  services/
  state/
  utils/
  validation/


======================================================================
25. FRONTEND: PRINCIPIOS
======================================================================

- Responsive.
- Accesible.
- Semántico.
- Consistente.
- Validaciones UX.
- Estados de carga.
- Estados vacíos.
- Estados de error.
- Confirmaciones para operaciones sensibles.
- Feedback de éxito.
- Navegación clara.
- No almacenar secretos en localStorage.
- No confiar en ocultar botones como mecanismo de seguridad.


======================================================================
26. UI/UX
======================================================================

El diseño debe priorizar:

- Claridad.
- Accesibilidad.
- Jerarquía visual.
- Consistencia.
- Bajo número de pasos.
- Lectura fácil.
- Responsive.
- Mensajes comprensibles.
- Estados claramente visibles.

Se deben diseñar como mínimo:
- Login.
- Dashboard por rol.
- Perfil.
- Triaje.
- Resultado/ruta.
- Buscar disponibilidad.
- Citas.
- Historia clínica.
- Atención profesional.
- Recetas.
- Medicamentos.
- Dispensación.
- Seguimientos.
- Resumen de salud.
- QR.
- Administración.


======================================================================
27. VALIDACIONES
======================================================================

Validar en:
1. Frontend para UX.
2. Backend para seguridad/integridad.
3. Base de datos para restricciones críticas.

Nunca depender únicamente del frontend.


======================================================================
28. MANEJO DE ERRORES
======================================================================

Crear excepciones de dominio cuando sean necesarias.

Ejemplos:
- UsuarioNoEncontrado.
- CitaNoDisponible.
- CitaEstadoInvalido.
- AccesoNoAutorizado.
- RecursoNoEncontrado.
- DatosInvalidos.
- TokenExpirado.
- QRRevocado.

No devolver stack traces al usuario.

Los errores deben ser consistentes.


======================================================================
29. TRANSACCIONES
======================================================================

Utilizar transacciones cuando una operación implique varios cambios que deben ser atómicos.

Ejemplo:
Crear receta + detalles de receta.

Ejemplo:
Reservar cita + bloquear disponibilidad.

Si una parte falla:
ROLLBACK.

No dejar datos parcialmente creados.


======================================================================
30. CONCURRENCIA
======================================================================

Especial atención a las citas.

Dos usuarios no deben poder reservar simultáneamente el mismo espacio.

La solución debe apoyarse en:
- Restricciones de BD.
- Transacciones.
- Estrategia de concurrencia.
- Validación final antes de confirmar.

No confiar únicamente en:
"consultar disponibilidad y después insertar".


======================================================================
31. SEGURIDAD DE ORACLE ATP
======================================================================

La conexión a Oracle ATP debe manejarse mediante configuración segura.

Nunca:
- Contraseñas en Git.
- Credenciales en frontend.
- Wallet dentro del repositorio.
- Secretos hardcodeados.

Utilizar:
- Variables de entorno.
- Secret manager cuando corresponda.
- Configuración externa.
- Wallet/configuración de Oracle protegida.

La aplicación backend es la única que debe conectarse directamente a Oracle.


======================================================================
32. CONFIGURACIÓN
======================================================================

Separar:
- Configuración de desarrollo.
- Configuración de pruebas.
- Configuración de producción.

Ejemplos:
DB_URL
DB_USER
DB_PASSWORD
JWT_SECRET o equivalente
CORS_ORIGINS

Nunca subir secretos al repositorio.


======================================================================
33. MIGRACIONES DE BASE DE DATOS
======================================================================

La BD debe evolucionar mediante scripts versionados.

Ejemplo:

db/
  migrations/
    V001__crear_seguridad.sql
    V002__crear_personas.sql
    V003__crear_instituciones.sql
    V004__crear_citas.sql
    V005__crear_triaje.sql
    V006__crear_historia.sql
    V007__crear_recetas.sql
    V008__crear_seguimientos.sql
    V009__crear_qr.sql
    V010__crear_auditoria.sql

Los nombres definitivos pueden cambiar.

No modificar manualmente producción sin registrar el cambio.


======================================================================
34. DATOS DE PRUEBA
======================================================================

Separar:
- DDL.
- Migraciones.
- Datos de prueba.
- Datos reales.

Nunca utilizar información clínica real de personas para pruebas académicas.

Crear:
- Pacientes ficticios.
- Profesionales ficticios.
- Instituciones ficticias.
- Citas ficticias.
- Recetas ficticias.


======================================================================
35. TESTING
======================================================================

Pruebas unitarias:
- Servicios.
- Reglas de negocio.
- Validaciones.
- Estados.
- Motor de triaje.

Pruebas de integración:
- API + Oracle.
- Repositorios.
- Transacciones.

Pruebas API:
- Autenticación.
- Autorización.
- CRUD.
- Errores.
- Seguridad.

Pruebas frontend:
- Formularios.
- Navegación.
- Estados.
- Validaciones.

Pruebas de seguridad:
- Acceso cruzado entre pacientes.
- Roles.
- Tokens.
- QR.
- Manipulación de IDs.
- Datos no autorizados.


======================================================================
36. GIT Y CONTROL DE VERSIONES
======================================================================

Repositorio:

MediTriaje-2.0

Ramas sugeridas:

main
develop
feature/*
fix/*
refactor/*
docs/*
test/*

No hacer commits gigantes.

Mensajes claros.

Ejemplos:
feat: implementar creación de citas
fix: evitar doble reserva
refactor: separar lógica de disponibilidad
test: agregar pruebas de recetas
docs: actualizar modelo de datos


======================================================================
37. DOCUMENTACIÓN
======================================================================

El proyecto debe mantener:

README.md
ARCHITECTURE.md
API.md
DATABASE.md
SECURITY.md
CONTRIBUTING.md
CHANGELOG.md

Documentación académica:
- Descripción.
- Justificación.
- Problema.
- Objetivos.
- Requisitos.
- Casos de uso.
- UML.
- MER.
- Modelo relacional.
- Arquitectura.
- Seguridad.
- Pruebas.


======================================================================
38. ESTRUCTURA DEL REPOSITORIO
======================================================================

MediTriaje-2.0/
│
├── backend/
│   ├── src/
│   ├── pom.xml
│   └── README.md
│
├── frontend/
│   ├── index.html
│   ├── assets/
│   ├── css/
│   └── js/
│
├── database/
│   ├── migrations/
│   ├── seeds/
│   ├── queries/
│   └── README.md
│
├── docs/
│   ├── requirements/
│   ├── architecture/
│   ├── uml/
│   ├── database/
│   ├── api/
│   └── security/
│
├── tests/
│
├── scripts/
│
├── .gitignore
├── README.md
├── SECURITY.md
├── ARCHITECTURE.md
└── CHANGELOG.md


======================================================================
39. METODOLOGÍA DE DESARROLLO CON CLI
======================================================================

La CLI debe trabajar como agente de desarrollo controlado.

REGLA PRINCIPAL:

NO IMPLEMENTAR FUNCIONALIDADES IMPORTANTES SIN ACTUALIZAR/CONSULTAR ESTA ESPECIFICACIÓN.

Flujo:

1. Analizar requisito.
2. Identificar módulo.
3. Identificar impacto.
4. Revisar arquitectura.
5. Revisar modelo de datos.
6. Diseñar solución.
7. Implementar.
8. Ejecutar pruebas.
9. Revisar errores.
10. Documentar.
11. Verificar Git.
12. Reportar resultado.

La CLI debe evitar:
- Crear tablas innecesarias.
- Duplicar lógica.
- Inventar requisitos.
- Cambiar arquitectura sin justificación.
- Saltarse pruebas.
- Hardcodear credenciales.
- Exponer información clínica.
- Implementar reglas clínicas no definidas.


======================================================================
40. REGLA DE NO IMPROVISACIÓN
======================================================================

Cuando exista una decisión no definida:

NO inventarla silenciosamente.

Debe marcarse como:
PENDIENTE DE DECISIÓN.

Ejemplos:
- Regla exacta de triaje.
- Estados definitivos.
- Política de cancelación.
- Tiempo de expiración del QR.
- Alcance exacto del resumen.
- Modelo exacto de autenticación.
- Proveedor de notificaciones.

La CLI debe preguntar o dejar registrada la decisión pendiente.


======================================================================
41. ORDEN DE IMPLEMENTACIÓN
======================================================================

FASE 0
Documento maestro y alcance.

FASE 1
Requisitos y casos de uso.

FASE 2
Arquitectura.

FASE 3
Modelo conceptual.

FASE 4
Modelo relacional.

FASE 5
Migraciones Oracle.

FASE 6
Proyecto Java base.

FASE 7
Seguridad/autenticación.

FASE 8
Usuarios/roles.

FASE 9
Pacientes/profesionales.

FASE 10
Instituciones/especialidades/disponibilidad.

FASE 11
Citas.

FASE 12
Triaje/orientación.

FASE 13
Atenciones/historia clínica.

FASE 14
Recetas/medicamentos.

FASE 15
Dispensación.

FASE 16
Tratamientos/seguimientos.

FASE 17
Resumen de salud.

FASE 18
QR temporal.

FASE 19
Dashboard.

FASE 20
Asistente.

FASE 21
Frontend completo.

FASE 22
Pruebas.

FASE 23
Seguridad.

FASE 24
Documentación.

FASE 25
Despliegue.


======================================================================
42. ORDEN CORRECTO ANTES DE PROGRAMAR
======================================================================

NO comenzar creando tablas directamente.

Primero:

PROBLEMA
↓
OBJETIVOS
↓
ACTORES
↓
CASOS DE USO
↓
REQUISITOS
↓
REGLAS DE NEGOCIO
↓
MODELO DE DOMINIO
↓
MER
↓
MODELO RELACIONAL
↓
NORMALIZACIÓN
↓
DDL
↓
API
↓
BACKEND
↓
FRONTEND


======================================================================
43. CASOS DE USO PRINCIPALES
======================================================================

PACIENTE:

- Registrarse/iniciar sesión.
- Consultar perfil.
- Realizar triaje.
- Consultar resultado de orientación.
- Buscar atención.
- Consultar disponibilidad.
- Agendar cita.
- Cancelar/reprogramar según reglas.
- Consultar citas.
- Consultar historia.
- Consultar recetas.
- Consultar estado de dispensación.
- Consultar tratamientos.
- Registrar seguimiento.
- Consultar resumen de salud.
- Generar QR.
- Revocar QR.
- Utilizar asistente.

PROFESIONAL:

- Iniciar sesión.
- Consultar agenda.
- Consultar pacientes autorizados.
- Consultar información clínica necesaria.
- Registrar atención.
- Registrar evolución.
- Crear receta.
- Registrar indicaciones.
- Registrar seguimiento.

ADMINISTRADOR:

- Gestionar usuarios.
- Gestionar profesionales.
- Gestionar especialidades.
- Gestionar instituciones.
- Gestionar sedes.
- Gestionar disponibilidad.
- Gestionar parámetros.
- Consultar información administrativa.
- Consultar auditoría autorizada.


======================================================================
44. REGLAS DE NEGOCIO IMPORTANTES
======================================================================

RB-01
Un paciente no puede consultar la información de otro paciente.

RB-02
Un usuario autenticado no implica acceso total.

RB-03
Las decisiones de autorización se realizan en backend.

RB-04
Una cita debe corresponder a una disponibilidad válida.

RB-05
No puede existir doble reserva para el mismo recurso/horario.

RB-06
Una atención debe estar relacionada con un paciente y un profesional.

RB-07
Una receta debe tener un profesional responsable.

RB-08
Los registros clínicos históricos deben proteger su integridad.

RB-09
Una dispensación solo puede marcarse como realizada con información válida.

RB-10
Un QR debe tener expiración y/o revocación.

RB-11
Los accesos sensibles deben poder auditarse.

RB-12
El sistema no debe inventar diagnósticos, recetas, dispensaciones ni disponibilidad.

RB-13
El frontend no es una frontera de seguridad.

RB-14
Las reglas de negocio deben residir en el backend.

RB-15
Las restricciones críticas también deben protegerse en Oracle.


======================================================================
45. INDICADORES Y REPORTES FUTUROS
======================================================================

Posibles indicadores:

Administrativos:
- Citas por periodo.
- Citas atendidas.
- Cancelaciones.
- No asistencia.
- Disponibilidad.
- Demanda por especialidad.

Operativos:
- Triajes realizados.
- Distribución de prioridades.
- Tiempos del proceso.
- Seguimientos pendientes.

No convertir indicadores clínicos en conclusiones médicas automáticas.


======================================================================
46. POSIBLES EXTENSIONES FUTURAS
======================================================================

- Notificaciones por correo.
- SMS/WhatsApp mediante proveedor autorizado.
- Aplicación móvil.
- Telemedicina/teleorientación según requisitos aplicables.
- Integración con sistemas externos.
- Interoperabilidad.
- Firma digital.
- Documentos clínicos.
- Integración con servicios de dispensación.
- Analítica.
- IA como apoyo bajo supervisión.
- Multiinstitución.
- Escalabilidad territorial para Cesar.


======================================================================
47. CONTEXTO COLOMBIANO
======================================================================

El sistema se plantea para Colombia y puede contextualizarse inicialmente en Valledupar/Cesar.

La propuesta debe considerar:
- Acceso urbano y rural.
- Necesidad de orientación.
- Disponibilidad de servicios.
- Modalidades remotas cuando corresponda.
- Protección de información clínica.
- Interoperabilidad como evolución futura.

No inventar estadísticas de Valledupar/Cesar.
Si se agregan estadísticas o afirmaciones locales, deben investigarse y citarse.


======================================================================
48. PRIVACIDAD Y DATOS SENSIBLES
======================================================================

La información clínica y personal debe tratarse como información sensible.

Principios:
- Mínimo acceso.
- Mínima exposición.
- Propósito definido.
- Auditoría.
- Protección en tránsito.
- Protección en almacenamiento.
- No usar datos reales para pruebas.
- No incluir datos personales en repositorios.
- No exponer información sensible en URLs innecesariamente.
- No incluir historias clínicas completas en logs.


======================================================================
49. CALIDAD DEL CÓDIGO
======================================================================

Toda implementación debe buscar:

- Código legible.
- Nombres descriptivos.
- Métodos pequeños.
- Clases cohesionadas.
- Dependencias controladas.
- Baja duplicación.
- Validaciones.
- Manejo de errores.
- Pruebas.
- Documentación útil.

Evitar:
- God classes.
- God services.
- Métodos gigantes.
- SQL en controllers.
- HTML con lógica compleja.
- JavaScript monolítico.
- Variables globales innecesarias.
- Hardcodeo.
- Copiar/pegar lógica.


======================================================================
50. CRITERIO DE "HECHO"
======================================================================

Una funcionalidad NO está terminada solamente porque "funciona".

Debe cumplir:

[ ] Requisito definido.
[ ] Diseño aprobado.
[ ] Código implementado.
[ ] Validaciones.
[ ] Seguridad.
[ ] Persistencia.
[ ] Manejo de errores.
[ ] Pruebas.
[ ] UI terminada.
[ ] Documentación.
[ ] Sin secretos.
[ ] Sin duplicación importante.
[ ] Revisión de arquitectura.
[ ] Git limpio.
[ ] Migración de BD versionada si aplica.


======================================================================
51. DESPLIEGUE
======================================================================

Arquitectura objetivo:

USUARIO
  ↓
NAVEGADOR
  ↓ HTTPS
FRONTEND WEB
  ↓ HTTPS
JAVA REST API
  ↓ TLS
ORACLE ATP

Nunca:
Frontend → Oracle directamente.

La aplicación Java será el intermediario entre navegador y base de datos.


======================================================================
52. ENTORNO DE DESARROLLO
======================================================================

Se debe documentar:

- Versión de Java.
- Versión de Maven.
- Versión de Oracle/JDBC.
- Navegadores soportados.
- Variables de entorno.
- Configuración de Oracle ATP.
- Wallet si corresponde.
- Comandos de ejecución.
- Comandos de pruebas.
- Comandos de migración.


======================================================================
53. COMANDOS ESPERADOS PARA LA CLI
======================================================================

La CLI debe poder ejecutar, según la implementación elegida:

- Instalar dependencias.
- Compilar backend.
- Ejecutar backend.
- Ejecutar pruebas.
- Validar frontend.
- Ejecutar migraciones.
- Cargar datos de prueba.
- Revisar estado Git.
- Generar documentación.
- Empaquetar aplicación.

Los comandos concretos dependerán de las herramientas finalmente seleccionadas.


======================================================================
54. DECISIONES PENDIENTES
======================================================================

ANTES DE IMPLEMENTAR, definir:

1. Framework exacto del backend Java, si se utilizará uno.
2. Estrategia de autenticación.
3. Estrategia de tokens/sesiones.
4. Algoritmo de hash de contraseñas.
5. Estrategia exacta de IDs.
6. Estados definitivos.
7. Reglas exactas de triaje.
8. Catálogo de síntomas.
9. Catálogo de prioridades.
10. Modelo de disponibilidad.
11. Política de cancelación.
12. Modelo de dispensación.
13. Alcance del seguimiento.
14. Duración de QR.
15. Alcance del QR.
16. Política de auditoría.
17. Estrategia de notificaciones.
18. Política de archivos/documentos.
19. Estrategia de backups.
20. Estrategia de despliegue.
21. Dominio/nombre definitivo.
22. Diseño visual definitivo.


======================================================================
55. REGLA DE ORO PARA LA CLI
======================================================================

La CLI debe tratar este archivo como especificación base, pero NO como permiso para inventar decisiones.

Si encuentra contradicción:
1. Identificarla.
2. Informarla.
3. No ocultarla.
4. Proponer alternativas.
5. Esperar decisión cuando sea necesario.

Si encuentra una mejora:
1. Explicar el beneficio.
2. Explicar impacto.
3. No aplicarla silenciosamente si cambia arquitectura, BD o alcance.


======================================================================
56. RESUMEN EJECUTIVO
======================================================================

MediTriaje 2.0 será una plataforma web construida desde cero con:

FRONTEND:
HTML + CSS + JavaScript Vanilla

BACKEND:
Java + API REST

BASE DE DATOS:
Oracle Autonomous Transaction Processing (ATP) en Oracle Cloud

ARQUITECTURA:
Capas + separación de responsabilidades + REST + Repository/DAO + Service Layer

PRINCIPIOS:
SOLID + GRASP + alta cohesión + bajo acoplamiento + seguridad por diseño

FUNCIONALIDADES CENTRALES:

1. Autenticación y roles.
2. Pacientes.
3. Profesionales.
4. Instituciones.
5. Especialidades.
6. Disponibilidad.
7. Triaje.
8. Orientación/ruta de atención.
9. Citas.
10. Historia clínica.
11. Atenciones.
12. Recetas.
13. Medicamentos.
14. Dispensación.
15. Tratamientos.
16. Seguimiento.
17. Resumen de salud.
18. QR temporal.
19. Dashboard.
20. Asistente.

PROPUESTA DIFERENCIADORA:

El paciente no solamente solicita una cita.

MediTriaje 2.0 acompaña su proceso:

"Necesito atención"
        ↓
"¿Qué ruta corresponde?"
        ↓
"¿Dónde y cuándo puedo ser atendido?"
        ↓
"¿Qué ocurrió durante mi atención?"
        ↓
"¿Qué tratamiento tengo?"
        ↓
"¿Ya fue reclamado?"
        ↓
"¿Cómo va mi seguimiento?"
        ↓
"¿Qué información de salud puedo compartir de forma segura?"

Esta es la idea central que debe mantenerse durante todo el desarrollo.


======================================================================
57. REGLA FINAL DE DESARROLLO
======================================================================

NO construir primero la base de datos.
NO construir primero el frontend.
NO comenzar creando CRUDs sin modelo.

El orden recomendado es:

DOCUMENTO MAESTRO
→ REQUISITOS
→ CASOS DE USO
→ REGLAS DE NEGOCIO
→ MODELO DE DOMINIO
→ ARQUITECTURA
→ MER
→ MODELO RELACIONAL
→ NORMALIZACIÓN
→ DDL/MIGRACIONES ORACLE
→ BACKEND
→ API
→ FRONTEND
→ PRUEBAS
→ SEGURIDAD
→ DOCUMENTACIÓN
→ DESPLIEGUE

Todo cambio importante debe quedar documentado.

FIN DEL DOCUMENTO
