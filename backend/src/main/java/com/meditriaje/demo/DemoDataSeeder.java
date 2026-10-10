package com.meditriaje.demo;

import com.meditriaje.dto.RegistroPacienteRequest;
import com.meditriaje.dto.admin.CrearEspecialidadRequest;
import com.meditriaje.dto.admin.CrearInstitucionRequest;
import com.meditriaje.dto.admin.CrearProfesionalRequest;
import com.meditriaje.dto.admin.CrearSedeRequest;
import com.meditriaje.dto.admin.GenerarSlotsRequest;
import com.meditriaje.dto.admin.GenerarSlotsResponse;
import com.meditriaje.dto.admin.InstitucionResponse;
import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.allergy.RegistrarAlergiaRequest;
import com.meditriaje.dto.appointment.CancelarCitaRequest;
import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.appointment.ReservarCitaRequest;
import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.clinical.CerrarAtencionRequest;
import com.meditriaje.dto.clinical.CrearEnmiendaRequest;
import com.meditriaje.dto.clinical.IniciarAtencionRequest;
import com.meditriaje.dto.clinical.SignosVitalesDto;
import com.meditriaje.dto.followup.CrearSeguimientoRequest;
import com.meditriaje.dto.pharmacy.DetalleEntregaRequest;
import com.meditriaje.dto.pharmacy.RegistrarDispensacionRequest;
import com.meditriaje.dto.prescription.CrearRecetaDetalleRequest;
import com.meditriaje.dto.prescription.CrearRecetaRequest;
import com.meditriaje.dto.prescription.MedicamentoResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.dto.triage.CrearTriajeRequest;
import com.meditriaje.dto.triage.SintomaItemRequest;
import com.meditriaje.dto.triage.TriajeResponse;
import com.meditriaje.model.Especialidad;
import com.meditriaje.model.Institucion;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.Sede;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.CitaRepository;
import com.meditriaje.repository.DisponibilidadSlotRepository;
import com.meditriaje.repository.EspecialidadRepository;
import com.meditriaje.repository.InstitucionRepository;
import com.meditriaje.repository.MedicamentoRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.SedeRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.service.AdminCatalogService;
import com.meditriaje.service.AdminProfessionalService;
import com.meditriaje.service.AllergyService;
import com.meditriaje.service.AppointmentService;
import com.meditriaje.service.AuthService;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.service.DispensationService;
import com.meditriaje.service.FollowUpService;
import com.meditriaje.service.PrescriptionService;
import com.meditriaje.service.SlotGeneratorService;
import com.meditriaje.service.TriajeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Seeder de datos de demostración para MediTriaje 2.0 (Plan Post-Auditoría §T8.2).
 * Desactivado por defecto. Se activa exclusivamente con {@code DEMO_SEED=true}.
 *
 * <p>Reglas de seguridad y diseño:
 * <ul>
 *   <li>Es idempotente: comprueba si los datos ya fueron generados antes de sembrar.</li>
 *   <li>Usa exclusivamente el dominio reservado {@code @demo.meditriaje.test}.</li>
 *   <li><b>Se niega tajantemente a ejecutarse</b> si la base de datos contiene usuarios de otros dominios.</li>
 *   <li>Contraseña obligatoria desde la variable {@code DEMO_PASSWORD} (mínimo 12 caracteres).</li>
 *   <li>Crea los datos mediante los <b>servicios de la aplicación</b> para respetar triggers, auditoría e inmutabilidad.</li>
 * </ul>
 */
@Component
@Order(100)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_DOMAIN = "@demo.meditriaje.test";
    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    private static final String IP_LOCAL = "127.0.0.1";

    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfesionalRepository profesionalRepository;
    private final EspecialidadRepository especialidadRepository;
    private final InstitucionRepository institucionRepository;
    private final SedeRepository sedeRepository;
    private final DisponibilidadSlotRepository slotRepository;
    private final CitaRepository citaRepository;
    private final MedicamentoRepository medicamentoRepository;
    private final AdminCatalogService adminCatalogService;
    private final AdminProfessionalService adminProfessionalService;
    private final AuthService authService;
    private final SlotGeneratorService slotGeneratorService;
    private final TriajeService triajeService;
    private final AppointmentService appointmentService;
    private final ClinicalAttentionService clinicalAttentionService;
    private final PrescriptionService prescriptionService;
    private final AllergyService allergyService;
    private final FollowUpService followUpService;
    private final DispensationService dispensationService;

    public DemoDataSeeder(
            Environment environment,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ProfesionalRepository profesionalRepository,
            EspecialidadRepository especialidadRepository,
            InstitucionRepository institucionRepository,
            SedeRepository sedeRepository,
            DisponibilidadSlotRepository slotRepository,
            CitaRepository citaRepository,
            MedicamentoRepository medicamentoRepository,
            AdminCatalogService adminCatalogService,
            AdminProfessionalService adminProfessionalService,
            AuthService authService,
            SlotGeneratorService slotGeneratorService,
            TriajeService triajeService,
            AppointmentService appointmentService,
            ClinicalAttentionService clinicalAttentionService,
            PrescriptionService prescriptionService,
            AllergyService allergyService,
            FollowUpService followUpService,
            DispensationService dispensationService
    ) {
        this.environment = Objects.requireNonNull(environment, "environment no puede ser nulo");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder no puede ser nulo");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "pacienteRepository no puede ser nulo");
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository, "profesionalRepository no puede ser nulo");
        this.especialidadRepository = Objects.requireNonNull(especialidadRepository, "especialidadRepository no puede ser nulo");
        this.institucionRepository = Objects.requireNonNull(institucionRepository, "institucionRepository no puede ser nulo");
        this.sedeRepository = Objects.requireNonNull(sedeRepository, "sedeRepository no puede ser nulo");
        this.slotRepository = Objects.requireNonNull(slotRepository, "slotRepository no puede ser nulo");
        this.citaRepository = Objects.requireNonNull(citaRepository, "citaRepository no puede ser nulo");
        this.medicamentoRepository = Objects.requireNonNull(medicamentoRepository, "medicamentoRepository no puede ser nulo");
        this.adminCatalogService = Objects.requireNonNull(adminCatalogService, "adminCatalogService no puede ser nulo");
        this.adminProfessionalService = Objects.requireNonNull(adminProfessionalService, "adminProfessionalService no puede ser nulo");
        this.authService = Objects.requireNonNull(authService, "authService no puede ser nulo");
        this.slotGeneratorService = Objects.requireNonNull(slotGeneratorService, "slotGeneratorService no puede ser nulo");
        this.triajeService = Objects.requireNonNull(triajeService, "triajeService no puede ser nulo");
        this.appointmentService = Objects.requireNonNull(appointmentService, "appointmentService no puede ser nulo");
        this.clinicalAttentionService = Objects.requireNonNull(clinicalAttentionService, "clinicalAttentionService no puede ser nulo");
        this.prescriptionService = Objects.requireNonNull(prescriptionService, "prescriptionService no puede ser nulo");
        this.allergyService = Objects.requireNonNull(allergyService, "allergyService no puede ser nulo");
        this.followUpService = Objects.requireNonNull(followUpService, "followUpService no puede ser nulo");
        this.dispensationService = Objects.requireNonNull(dispensationService, "dispensationService no puede ser nulo");
    }

    @Override
    public void run(String... args) {
        String seedEnabled = environment.getProperty("DEMO_SEED");
        if (seedEnabled == null || seedEnabled.isBlank()) {
            seedEnabled = environment.getProperty("meditriaje.demo.seed", "false");
        }
        seedEnabled = seedEnabled != null ? seedEnabled.trim() : "false";

        if (!"true".equalsIgnoreCase(seedEnabled)) {
            log.debug("DemoDataSeeder: Inactivo (DEMO_SEED != true).");
            return;
        }

        log.info("DemoDataSeeder: Iniciando verificación de seguridad y pre-condiciones de siembra...");

        // 1. Validar contraseña demo requerida (mínimo 12 caracteres, nunca por defecto)
        String demoPassword = environment.getProperty("DEMO_PASSWORD");
        if (demoPassword == null || demoPassword.isBlank()) {
            demoPassword = environment.getProperty("meditriaje.demo.password", "");
        }
        demoPassword = demoPassword != null ? demoPassword.trim() : "";

        if (demoPassword.length() < 12) {
            String errorMsg = "DemoDataSeeder: Operación abortada. La variable DEMO_PASSWORD debe estar definida con al menos 12 caracteres.";
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // 2. Advertencia sobre DEMO_RESET e inmutabilidad clínica
        String demoReset = environment.getProperty("DEMO_RESET");
        if (demoReset == null || demoReset.isBlank()) {
            demoReset = environment.getProperty("meditriaje.demo.reset", "false");
        }
        demoReset = demoReset != null ? demoReset.trim() : "false";

        if ("true".equalsIgnoreCase(demoReset)) {
            log.warn("DemoDataSeeder: DEMO_RESET=true detectado. Por diseño de inmutabilidad clínica (ADR-008, ADR-011, ADR-016), " +
                    "las tablas ATENCION, RECETA, ALERGIA y AUDITORIA cuentan con triggers Oracle que impiden DELETE físico, y el usuario " +
                    "MEDITRIAJE_APP carece de privilegios DELETE en ellas. Para un reinicio total de esquema demo, aplique 'flyway clean' o " +
                    "re-ejecute el aprovisionamiento de esquema en Oracle ATP con MEDITRIAJE_OWNER.");
        }

        // 3. Blindaje de aislamiento: NEGATIVA a ejecutarse si hay usuarios de otros dominios
        Integer usuariosNoDemo = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM USUARIO WHERE LOWER(EMAIL) NOT LIKE '%" + DEMO_DOMAIN.toLowerCase() + "'",
                Integer.class
        );

        if (usuariosNoDemo != null && usuariosNoDemo > 0) {
            String errorAislamiento = "DemoDataSeeder: ABORTADO POR SEGURIDAD. La base de datos contiene " + usuariosNoDemo +
                    " usuario(s) que no pertenecen al dominio reservado '" + DEMO_DOMAIN + "'. " +
                    "Para prevenir contaminación accidental de datos reales o entornos no demo, la siembra ha sido rechazada.";
            log.error(errorAislamiento);
            throw new IllegalStateException(errorAislamiento);
        }

        // 4. Idempotencia: si ya existen usuarios demo sembrados, omitir
        Integer usuariosDemo = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM USUARIO WHERE LOWER(EMAIL) LIKE '%" + DEMO_DOMAIN.toLowerCase() + "'",
                Integer.class
        );

        if (usuariosDemo != null && usuariosDemo >= 17) {
            log.info("DemoDataSeeder: Datos de demostración ya sembrados ({} usuarios '{}' detectados). Omitiendo siembra.",
                    usuariosDemo, DEMO_DOMAIN);
            return;
        }

        log.info("DemoDataSeeder: Entorno validado. Procediendo a sembrar datos de demostración mediante servicios de dominio...");

        try {
            ejecutarSiembra(demoPassword);
            log.info("DemoDataSeeder: ¡Siembra de datos de demostración completada con éxito!");
        } catch (Exception e) {
            log.error("DemoDataSeeder: Error durante la siembra de datos demo: {}", e.getMessage(), e);
            throw new IllegalStateException("Error en siembra de demostración: " + e.getMessage(), e);
        }
    }

    private void ejecutarSiembra(String demoPassword) {
        String hashDemo = passwordEncoder.encode(demoPassword);

        // A. Crear Administrador Demo
        String adminPublicId = UUID.randomUUID().toString();
        String adminEmail = "admin" + DEMO_DOMAIN;
        Long adminId;
        if (!usuarioRepository.existePorEmail(adminEmail)) {
            adminId = usuarioRepository.crear(adminPublicId, adminEmail, hashDemo, false);
            Long rolAdmin = usuarioRepository.buscarRolIdPorNombre("ROLE_ADMINISTRADOR")
                    .orElseThrow(() -> new IllegalStateException("Rol ROLE_ADMINISTRADOR no encontrado en BD."));
            usuarioRepository.asignarRol(adminId, rolAdmin);
            log.info("Demo: Administrador creado: {}", adminEmail);
        } else {
            Usuario u = usuarioRepository.buscarPorEmail(adminEmail).orElseThrow();
            adminId = u.id();
            adminPublicId = u.publicId();
        }

        // B. Crear Farmacéutico Demo
        String farmEmail = "farmacia" + DEMO_DOMAIN;
        String farmPublicId = UUID.randomUUID().toString();
        if (!usuarioRepository.existePorEmail(farmEmail)) {
            Long farmId = usuarioRepository.crear(farmPublicId, farmEmail, hashDemo, false);
            Long rolFarm = usuarioRepository.buscarRolIdPorNombre("ROLE_FARMACEUTICO")
                    .orElseThrow(() -> new IllegalStateException("Rol ROLE_FARMACEUTICO no encontrado en BD."));
            usuarioRepository.asignarRol(farmId, rolFarm);
            log.info("Demo: Farmacéutico creado: {}", farmEmail);
        } else {
            farmPublicId = usuarioRepository.buscarPorEmail(farmEmail).orElseThrow().publicId();
        }

        // C. Institución y Sedes
        String institucionPublicId;
        if (!institucionRepository.existePorNit("900123456-1")) {
            InstitucionResponse inst = adminCatalogService.crearInstitucion(
                    new CrearInstitucionRequest("900123456-1", "Hospital Universitario San Rafael Demo"),
                    adminPublicId, IP_LOCAL
            );
            institucionPublicId = inst.publicId();
            log.info("Demo: Institución creada: {}", inst.razonSocial());
        } else {
            institucionPublicId = institucionRepository.listar(0, 10, "ACTIVO").stream()
                    .filter(i -> "900123456-1".equals(i.nit()))
                    .findFirst()
                    .map(Institucion::publicId)
                    .orElseThrow();
        }

        // Sedes
        String sede1PublicId = obtenerOCrearSede(institucionPublicId, "Sede Principal Chapinero", "Calle 53 # 10-20", "Bogotá", adminPublicId);
        String sede2PublicId = obtenerOCrearSede(institucionPublicId, "Sede Ambulatoria Norte", "Autopista Norte # 128-45", "Bogotá", adminPublicId);

        // D. Especialidades (4)
        String espMedGeneral = obtenerOCrearEspecialidad("Medicina General", 20, adminPublicId);
        String espMedInterna = obtenerOCrearEspecialidad("Medicina Interna", 30, adminPublicId);
        String espPediatria = obtenerOCrearEspecialidad("Pediatría", 20, adminPublicId);
        String espCardiologia = obtenerOCrearEspecialidad("Cardiología", 30, adminPublicId);

        // E. 5 Profesionales Asistenciales y 1 Personal de Enfermería
        List<ProfesionalDemoInfo> profesionales = new ArrayList<>();
        profesionales.add(altaProfesionalDemo("dra.gomez" + DEMO_DOMAIN, "1018273645", "RM-102938", "María Paula", "Gómez Vargas", espMedGeneral, adminPublicId, hashDemo));
        profesionales.add(altaProfesionalDemo("dr.rodriguez" + DEMO_DOMAIN, "1029384756", "RM-203948", "Carlos Eduardo", "Rodríguez Peña", espMedGeneral, adminPublicId, hashDemo));
        profesionales.add(altaProfesionalDemo("dra.restrepo" + DEMO_DOMAIN, "1038475629", "RM-304958", "Ana Lucía", "Restrepo Mejia", espMedInterna, adminPublicId, hashDemo));
        profesionales.add(altaProfesionalDemo("dr.silva" + DEMO_DOMAIN, "1049586738", "RM-405968", "Felipe Andrés", "Silva Castro", espPediatria, adminPublicId, hashDemo));
        profesionales.add(altaProfesionalDemo("dr.martinez" + DEMO_DOMAIN, "1058674930", "RM-506978", "Jorge Hernán", "Martínez Osorio", espCardiologia, adminPublicId, hashDemo));

        // E2. Personal de Enfermería y Triaje Presencial (Fase U, ADR-022, ADR-026)
        altaProfesionalDemo("enfermera" + DEMO_DOMAIN, "1098765432", "ENF-102938", "Beatriz Elena", "Valencia Ramos", espMedGeneral, adminPublicId, hashDemo, "ROLE_ENFERMERIA");

        // F. Generar agendas de los próximos 14 días para los profesionales
        LocalDate fechaInicioSlots = LocalDate.now(ZONE_BOGOTA).plusDays(1);
        LocalDate fechaFinSlots = fechaInicioSlots.plusDays(13);
        List<SlotResponse> slotsGenerados = new ArrayList<>();

        for (int i = 0; i < profesionales.size(); i++) {
            ProfesionalDemoInfo prof = profesionales.get(i);
            String sedeElegida = (i % 2 == 0) ? sede1PublicId : sede2PublicId;
            try {
                GenerarSlotsResponse resSlots = slotGeneratorService.generarSlots(
                        new GenerarSlotsRequest(
                                prof.profesionalPublicId(),
                                sedeElegida,
                                fechaInicioSlots,
                                fechaFinSlots,
                                LocalTime.of(8, 0),
                                LocalTime.of(12, 0),
                                prof.duracionSlotMin(),
                                "PRESENCIAL",
                                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                        ),
                        adminPublicId, IP_LOCAL
                );
                slotsGenerados.addAll(resSlots.slots());
                log.info("Demo: Generados {} slots para {}", resSlots.slotsGenerados(), prof.email());
            } catch (Exception e) {
                log.warn("Demo: No se pudieron generar slots para {}: {}", prof.email(), e.getMessage());
            }
        }

        // G. 10 Pacientes con Historia Clínica
        List<PacienteDemoInfo> pacientes = new ArrayList<>();
        pacientes.add(registrarPacienteDemo("laura.morales" + DEMO_DOMAIN, "1020304050", "Laura Sofía", "Morales Ruiz", LocalDate.of(1995, 5, 12), "3101234567", demoPassword));
        pacientes.add(registrarPacienteDemo("santiago.moreno" + DEMO_DOMAIN, "1030405060", "Santiago", "Moreno Vargas", LocalDate.of(1990, 8, 20), "3112345678", demoPassword));
        pacientes.add(registrarPacienteDemo("valentina.castro" + DEMO_DOMAIN, "1040506070", "Valentina", "Castro Ríos", LocalDate.of(1988, 2, 14), "3123456789", demoPassword));
        pacientes.add(registrarPacienteDemo("mateo.herrera" + DEMO_DOMAIN, "1050607080", "Mateo", "Herrera Gómez", LocalDate.of(2000, 11, 3), "3134567890", demoPassword));
        pacientes.add(registrarPacienteDemo("camila.pena" + DEMO_DOMAIN, "1060708090", "Camila Andrea", "Peña Salazar", LocalDate.of(1993, 7, 25), "3145678901", demoPassword));
        pacientes.add(registrarPacienteDemo("daniel.torres" + DEMO_DOMAIN, "1070809010", "Daniel Esteban", "Torres Nieto", LocalDate.of(1985, 9, 18), "3156789012", demoPassword));
        pacientes.add(registrarPacienteDemo("mariana.duque" + DEMO_DOMAIN, "1080901020", "Mariana", "Duque Zuluaga", LocalDate.of(1997, 4, 30), "3167890123", demoPassword));
        pacientes.add(registrarPacienteDemo("andres.quintana" + DEMO_DOMAIN, "1090102030", "Andrés Felipe", "Quintana Marín", LocalDate.of(1992, 12, 8), "3178901234", demoPassword));
        pacientes.add(registrarPacienteDemo("isabel.salgado" + DEMO_DOMAIN, "1011223344", "Isabel Cristina", "Salgado Cano", LocalDate.of(1999, 1, 19), "3189012345", demoPassword));
        pacientes.add(registrarPacienteDemo("nicolas.osorio" + DEMO_DOMAIN, "1022334455", "Nicolás", "Osorio Pardo", LocalDate.of(1994, 6, 22), "3190123456", demoPassword));

        // H. Flujo Clínico 1: Triaje leve -> Cita -> Atención -> Diagnóstico -> Receta -> Alergia -> Seguimiento -> Dispensación
        if (!slotsGenerados.isEmpty()) {
            try {
                PacienteDemoInfo p1 = pacientes.get(0); // Laura
                ProfesionalDemoInfo med1 = profesionales.get(0); // Dra. Gómez

                // Triaje leve Nivel III
                TriajeResponse triaje1 = triajeService.evaluarYGuardarTriaje(
                        new CrearTriajeRequest(
                                List.of(new SintomaItemRequest("SIN-001", new BigDecimal("24.0"), 4)),
                                "Dolor de cabeza tipo opresivo de intensidad moderada sin signos de alarma."
                        ),
                        p1.usuarioPublicId(), IP_LOCAL
                );

                // Reservar cita
                SlotResponse slot1 = slotsGenerados.stream()
                        .filter(s -> s.profesionalPublicId().equals(med1.profesionalPublicId()))
                        .findFirst().orElse(slotsGenerados.get(0));

                CitaResponse cita1 = appointmentService.reservarCita(
                        new ReservarCitaRequest(slot1.publicId(), triaje1.publicId()),
                        p1.usuarioPublicId(), IP_LOCAL
                );

                // Iniciar y cerrar atención médica
                AtencionResponse atencion1 = clinicalAttentionService.iniciarAtencion(
                        new IniciarAtencionRequest(cita1.publicId()),
                        med1.usuarioPublicId(), IP_LOCAL
                );

                SignosVitalesDto signos1 = new SignosVitalesDto(
                        120, 80, 72, 16,
                        new BigDecimal("36.5"), 98,
                        new BigDecimal("62.5"), new BigDecimal("165.0")
                );

                clinicalAttentionService.cerrarAtencion(
                        atencion1.publicId(),
                        new CerrarAtencionRequest(
                                "R51",
                                "Cefalea tensional de 24 horas de evolución.",
                                "Paciente alerta, orientada, sin signos de focalización neurológica.",
                                "Manejo ambulatorio con analgésicos, reposo e hidratación adecuada.",
                                signos1
                        ),
                        med1.usuarioPublicId(), IP_LOCAL
                );

                // Registrar alergia médica profesional
                allergyService.registrarAlergiaPorProfesional(
                        p1.pacientePublicId(),
                        new RegistrarAlergiaRequest("Penicilina", "Anafilaxia previa en la infancia", "GRAVE", atencion1.publicId()),
                        med1.usuarioPublicId(), IP_LOCAL
                );

                // Emitir receta médica con medicamentos del catálogo
                String medAcetaminofen = obtenerMedicamentoPublicId("MED-ACE-500");
                String medIbuprofeno = obtenerMedicamentoPublicId("MED-IBU-400");

                RecetaResponse receta1 = prescriptionService.emitirReceta(
                        new CrearRecetaRequest(
                                atencion1.publicId(),
                                30,
                                List.of(
                                        new CrearRecetaDetalleRequest(medAcetaminofen, "500 mg", "Cada 8 horas", 3, 10, "Tomar después de las comidas"),
                                        new CrearRecetaDetalleRequest(medIbuprofeno, "400 mg", "Cada 12 horas en caso de dolor severo", 2, 6, "Uso condicionado a dolor")
                                )
                        ),
                        med1.usuarioPublicId(), IP_LOCAL
                );

                // Prescribir seguimiento post-atención
                followUpService.prescribirSeguimiento(
                        atencion1.publicId(),
                        new CrearSeguimientoRequest("CONTROL_MEDICO", "Control ambulatorio para reevaluación de cefalea", LocalDate.now(ZONE_BOGOTA).plusDays(7)),
                        med1.usuarioPublicId(), IP_LOCAL
                );

                // Dispensación farmacéutica de la receta
                dispensationService.registrarDispensacion(
                        new RegistrarDispensacionRequest(
                                receta1.publicId(),
                                sede1PublicId,
                                "Dispensación completa de analgésico primario.",
                                List.of(new DetalleEntregaRequest(medAcetaminofen, 10, "LOTE-2026-A1", LocalDate.now(ZONE_BOGOTA).plusYears(2)))
                        ),
                        farmPublicId,
                        List.of(new SimpleGrantedAuthority("ROLE_FARMACEUTICO")),
                        IP_LOCAL
                );

                log.info("Demo: Flujo clínico integral 1 completado para paciente {}", p1.email());
            } catch (Exception e) {
                log.warn("Demo: Advertencia en flujo clínico 1: {}", e.getMessage());
            }
        }

        // I. Flujo 2: Triaje de Emergencia Vital (Nivel I)
        try {
            PacienteDemoInfo p2 = pacientes.get(1); // Santiago
            triajeService.evaluarYGuardarTriaje(
                    new CrearTriajeRequest(
                            List.of(new SintomaItemRequest("SIN-007", new BigDecimal("1.0"), 9)),
                            "Dolor opresivo retroesternal con irradiación a miembro superior izquierdo y diaforesis."
                    ),
                    p2.usuarioPublicId(), IP_LOCAL
            );
            log.info("Demo: Triaje de emergencia vital (Nivel I) registrado para paciente {}", p2.email());
        } catch (Exception e) {
            log.warn("Demo: Advertencia en triaje de emergencia: {}", e.getMessage());
        }

        // J. Flujo 3: Atención con Enmienda Médica Inmutable
        if (slotsGenerados.size() > 1) {
            try {
                PacienteDemoInfo p3 = pacientes.get(2); // Valentina
                ProfesionalDemoInfo med2 = profesionales.get(1); // Dr. Rodríguez

                SlotResponse slot2 = slotsGenerados.stream()
                        .filter(s -> s.profesionalPublicId().equals(med2.profesionalPublicId()) && "LIBRE".equals(s.estado()))
                        .findFirst().orElse(slotsGenerados.get(1));

                CitaResponse cita2 = appointmentService.reservarCita(
                        new ReservarCitaRequest(slot2.publicId(), null),
                        p3.usuarioPublicId(), IP_LOCAL
                );

                AtencionResponse atencion2 = clinicalAttentionService.iniciarAtencion(
                        new IniciarAtencionRequest(cita2.publicId()),
                        med2.usuarioPublicId(), IP_LOCAL
                );

                SignosVitalesDto signos2 = new SignosVitalesDto(
                        145, 95, 80, 18,
                        new BigDecimal("36.7"), 97,
                        new BigDecimal("75.0"), new BigDecimal("160.0")
                );

                clinicalAttentionService.cerrarAtencion(
                        atencion2.publicId(),
                        new CerrarAtencionRequest(
                                "I10",
                                "Control de hipertensión arterial.",
                                "Cifras tensionales elevadas en dos tomas.",
                                "Ajuste farmacológico y control de factores de riesgo.",
                                signos2
                        ),
                        med2.usuarioPublicId(), IP_LOCAL
                );

                // Enmienda médica obligatoria (ADR-008)
                clinicalAttentionService.crearEnmienda(
                        atencion2.publicId(),
                        new CrearEnmiendaRequest(
                                "Aclaración sobre toma tensional",
                                "Se constató que la segunda toma fue realizada con 15 minutos de reposo previo."
                        ),
                        med2.usuarioPublicId(), IP_LOCAL
                );

                // Receta para hipertensión
                String medLosartan = obtenerMedicamentoPublicId("MED-LOS-050");
                prescriptionService.emitirReceta(
                        new CrearRecetaRequest(
                                atencion2.publicId(),
                                30,
                                List.of(new CrearRecetaDetalleRequest(medLosartan, "50 mg", "Una tableta diaria en ayunas", 30, 30, "No suspender bruscamente"))
                        ),
                        med2.usuarioPublicId(), IP_LOCAL
                );

                log.info("Demo: Atención con enmienda y receta completada para paciente {}", p3.email());
            } catch (Exception e) {
                log.warn("Demo: Advertencia en flujo con enmienda: {}", e.getMessage());
            }
        }

        // K. Flujo 4: Alergia autorreportada por paciente
        try {
            PacienteDemoInfo p4 = pacientes.get(3); // Mateo
            allergyService.registrarMiAlergia(
                    new RegistrarAlergiaRequest("Polen y gramíneas", "Rinitis alérgica estacional y prurito nasal", "LEVE", null),
                    p4.usuarioPublicId(), IP_LOCAL
            );
            log.info("Demo: Alergia autorreportada registrada para paciente {}", p4.email());
        } catch (Exception e) {
            log.warn("Demo: Advertencia en alergia autorreportada: {}", e.getMessage());
        }

        // L. Flujo 5: Alergia inactivada
        try {
            PacienteDemoInfo p5 = pacientes.get(4); // Camila
            ProfesionalDemoInfo med1 = profesionales.get(0); // Dra. Gómez

            var alResp = allergyService.registrarAlergiaPorProfesional(
                    p5.pacientePublicId(),
                    new RegistrarAlergiaRequest("Amoxicilina", "Exantema macular leve en infancia", "LEVE", null),
                    med1.usuarioPublicId(), IP_LOCAL
            );

            allergyService.inactivarAlergiaPorProfesional(
                    alResp.publicId(),
                    new com.meditriaje.dto.allergy.InactivarAlergiaRequest("Prueba de provocación oral negativa en unidad de alergología hospitalaria"),
                    med1.usuarioPublicId(), IP_LOCAL
            );
            log.info("Demo: Alergia inactivada registrada para paciente {}", p5.email());
        } catch (Exception e) {
            log.warn("Demo: Advertencia en alergia inactivada: {}", e.getMessage());
        }

        // M. Flujo 6: Cita Cancelada por Paciente
        if (slotsGenerados.size() > 2) {
            try {
                PacienteDemoInfo p6 = pacientes.get(5); // Daniel
                SlotResponse slot6 = slotsGenerados.stream()
                        .filter(s -> "LIBRE".equals(s.estado()))
                        .findFirst().orElse(null);

                if (slot6 != null) {
                    CitaResponse cita6 = appointmentService.reservarCita(
                            new ReservarCitaRequest(slot6.publicId(), null),
                            p6.usuarioPublicId(), IP_LOCAL
                    );

                    appointmentService.cancelarCita(
                            cita6.publicId(),
                            new CancelarCitaRequest("Compromiso laboral imprevisto fuera de la ciudad"),
                            p6.usuarioPublicId(),
                            List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                            IP_LOCAL
                    );
                    log.info("Demo: Cita cancelada con éxito para paciente {}", p6.email());
                }
            } catch (Exception e) {
                log.warn("Demo: Advertencia en cita cancelada: {}", e.getMessage());
            }
        }

        // N. Flujo 7: Cita con Inasistencia (No-Show)
        if (slotsGenerados.size() > 3) {
            try {
                PacienteDemoInfo p7 = pacientes.get(6); // Mariana
                ProfesionalDemoInfo med2 = profesionales.get(1); // Dr. Rodríguez

                SlotResponse slot7 = slotsGenerados.stream()
                        .filter(s -> "LIBRE".equals(s.estado()))
                        .findFirst().orElse(null);

                if (slot7 != null) {
                    CitaResponse cita7 = appointmentService.reservarCita(
                            new ReservarCitaRequest(slot7.publicId(), null),
                            p7.usuarioPublicId(), IP_LOCAL
                    );

                    // Para cumplir la regla temporal (now >= inicio de la cita), ajustar transitoriamente el horario del slot
                    jdbcTemplate.update(
                            "UPDATE DISPONIBILIDAD_SLOT SET FECHA_HORA_INICIO = CURRENT_TIMESTAMP - INTERVAL '1' HOUR, " +
                                    "FECHA_HORA_FIN = CURRENT_TIMESTAMP - INTERVAL '40' MINUTE WHERE PUBLIC_ID = ?",
                            slot7.publicId()
                    );

                    appointmentService.marcarNoAsistio(
                            cita7.publicId(),
                            med2.usuarioPublicId(),
                            List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                            IP_LOCAL
                    );
                    log.info("Demo: Cita marcada como NO_ASISTIO con éxito para paciente {}", p7.email());
                }
            } catch (Exception e) {
                log.warn("Demo: Advertencia en cita no-show: {}", e.getMessage());
            }
        }

        // O. Citas Programadas Activas para los pacientes restantes (8, 9, 10)
        for (int pIdx = 7; pIdx < pacientes.size(); pIdx++) {
            PacienteDemoInfo p = pacientes.get(pIdx);
            SlotResponse sLibre = slotsGenerados.stream()
                    .filter(s -> "LIBRE".equals(s.estado()))
                    .findFirst().orElse(null);

            if (sLibre != null) {
                try {
                    appointmentService.reservarCita(
                            new ReservarCitaRequest(sLibre.publicId(), null),
                            p.usuarioPublicId(), IP_LOCAL
                    );
                    log.info("Demo: Cita PROGRAMADA agendada para paciente {}", p.email());
                } catch (Exception e) {
                    log.warn("Demo: Advertencia al agendar cita para {}: {}", p.email(), e.getMessage());
                }
            }
        }
    }

    private String obtenerOCrearSede(String institucionPublicId, String nombre, String direccion, String ciudad, String adminPublicId) {
        return sedeRepository.listar(0, 10, institucionPublicId, "ACTIVO").stream()
                .filter(s -> s.nombre().equalsIgnoreCase(nombre))
                .findFirst()
                .map(Sede::publicId)
                .orElseGet(() -> adminCatalogService.crearSede(
                        new CrearSedeRequest(institucionPublicId, nombre, direccion, ciudad),
                        adminPublicId, IP_LOCAL
                ).publicId());
    }

    private String obtenerOCrearEspecialidad(String nombre, int duracionMin, String adminPublicId) {
        return especialidadRepository.listar(0, 10, "ACTIVO").stream()
                .filter(e -> e.nombre().equalsIgnoreCase(nombre))
                .findFirst()
                .map(Especialidad::publicId)
                .orElseGet(() -> adminCatalogService.crearEspecialidad(
                        new CrearEspecialidadRequest(nombre, duracionMin),
                        adminPublicId, IP_LOCAL
                ).publicId());
    }

    private ProfesionalDemoInfo altaProfesionalDemo(
            String email, String numDoc, String regMed, String nombres, String apellidos,
            String especialidadPublicId, String adminPublicId, String hashDemo
    ) {
        return altaProfesionalDemo(email, numDoc, regMed, nombres, apellidos, especialidadPublicId, adminPublicId, hashDemo, "ROLE_PROFESIONAL");
    }

    private ProfesionalDemoInfo altaProfesionalDemo(
            String email, String numDoc, String regMed, String nombres, String apellidos,
            String especialidadPublicId, String adminPublicId, String hashDemo, String rol
    ) {
        if (!usuarioRepository.existePorEmail(email)) {
            var resp = adminProfessionalService.altaProfesional(
                    new CrearProfesionalRequest("CC", numDoc, regMed, nombres, apellidos, email, "3109876543", especialidadPublicId, rol),
                    adminPublicId, IP_LOCAL
            );

            // Fijar contraseña oficial de demo para que pueda loguearse directamente
            Usuario u = usuarioRepository.buscarPorEmail(email).orElseThrow();
            usuarioRepository.actualizarPassword(u.id(), hashDemo, false);

            int duracion = especialidadRepository.buscarPorPublicId(especialidadPublicId)
                    .map(Especialidad::duracionSlotMin).orElse(20);

            log.info("Demo: Personal asistencial creado con rol {}: {} ({})", rol, email, nombres + " " + apellidos);
            return new ProfesionalDemoInfo(resp.publicId(), u.publicId(), u.id(), email, duracion);
        } else {
            Usuario u = usuarioRepository.buscarPorEmail(email).orElseThrow();
            Profesional p = profesionalRepository.buscarPorUsuarioId(u.id()).orElseThrow();
            int duracion = especialidadRepository.buscarPorId(p.especialidadId())
                    .map(Especialidad::duracionSlotMin).orElse(20);
            return new ProfesionalDemoInfo(p.publicId(), u.publicId(), u.id(), email, duracion);
        }
    }

    private PacienteDemoInfo registrarPacienteDemo(
            String email, String numDoc, String nombres, String apellidos,
            LocalDate fechaNac, String telefono, String password
    ) {
        if (!usuarioRepository.existePorEmail(email)) {
            var reg = authService.registrarPaciente(
                    new RegistroPacienteRequest("CC", numDoc, nombres, apellidos, fechaNac, telefono, email, password, "v1.0", true),
                    IP_LOCAL
            );
            Usuario u = usuarioRepository.buscarPorEmail(email).orElseThrow();
            Paciente p = pacienteRepository.buscarPorUsuarioId(u.id()).orElseThrow();
            log.info("Demo: Paciente registrado: {} ({})", email, nombres + " " + apellidos);
            return new PacienteDemoInfo(p.publicId(), u.publicId(), email);
        } else {
            Usuario u = usuarioRepository.buscarPorEmail(email).orElseThrow();
            Paciente p = pacienteRepository.buscarPorUsuarioId(u.id()).orElseThrow();
            return new PacienteDemoInfo(p.publicId(), u.publicId(), email);
        }
    }

    private String obtenerMedicamentoPublicId(String codigo) {
        return medicamentoRepository.listarActivos(codigo, 0, 1).stream()
                .filter(m -> m.codigo().equalsIgnoreCase(codigo))
                .findFirst()
                .map(MedicamentoResponse::publicId)
                .orElse("00000000-0000-7000-a000-000000000001");
    }

    private record ProfesionalDemoInfo(
            String profesionalPublicId,
            String usuarioPublicId,
            Long usuarioId,
            String email,
            int duracionSlotMin
    ) {}

    private record PacienteDemoInfo(
            String pacientePublicId,
            String usuarioPublicId,
            String email
    ) {}
}
