package com.meditriaje;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración con Testcontainers (Oracle Free).
 *
 * <p>Valida los criterios de aceptación de M1.5:</p>
 * <ol>
 *   <li>Levanta Oracle Free en Docker.</li>
 *   <li>Crea {@code MEDITRIAJE_OWNER} (usuario DDL, ejecuta migraciones Flyway).</li>
 *   <li>Crea {@code MEDITRIAJE_APP} (usuario runtime, privilegios mínimos).</li>
 *   <li>Aplica migraciones Flyway como {@code MEDITRIAJE_OWNER}.</li>
 *   <li>Valida que {@code MEDITRIAJE_APP} puede consultar tablas con GRANT.</li>
 *   <li>Valida que {@code MEDITRIAJE_APP} NO puede hacer DDL.</li>
 *   <li>Valida que {@code flyway_schema_history} tiene al menos V1.</li>
 * </ol>
 *
 * <p><strong>Docker requerido.</strong> Si Docker no está disponible localmente,
 * los tests se marcan {@code SKIPPED} (no {@code FAILED}).
 * En CI (GitHub Actions {@code ubuntu-latest}) Docker siempre está disponible.</p>
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class OracleIntegrationTest {

    private static final String OWNER_USER = "MEDITRIAJE_OWNER";
    private static final String OWNER_PASS = "Owner_Pass1";
    private static final String APP_USER   = "MEDITRIAJE_APP";
    private static final String APP_PASS   = "App_Pass1";

    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withPassword("testPassword1")
            .withReuse(false);

    /** DataSource con el usuario que genera el container (tiene DBA en el PDB). */
    private static DataSource systemDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("oracle.jdbc.OracleDriver");
        ds.setUrl(oracle.getJdbcUrl());
        ds.setUsername(oracle.getUsername());
        ds.setPassword(oracle.getPassword());
        return ds;
    }

    /** DataSource usando MEDITRIAJE_OWNER para migraciones Flyway. */
    private static DataSource ownerDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("oracle.jdbc.OracleDriver");
        ds.setUrl(oracle.getJdbcUrl());
        ds.setUsername(OWNER_USER);
        ds.setPassword(OWNER_PASS);
        return ds;
    }

    /** DataSource usando MEDITRIAJE_APP para la aplicación (privilegios mínimos). */
    private static DataSource appDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("oracle.jdbc.OracleDriver");
        ds.setUrl(oracle.getJdbcUrl());
        ds.setUsername(APP_USER);
        ds.setPassword(APP_PASS);
        return ds;
    }

    @BeforeAll
    static void provisionarUsuarios() {
        // Skip graceful si Docker no está disponible (dev local sin Docker Desktop)
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker no disponible — prueba de integración omitida"
        );

        JdbcTemplate sys = new JdbcTemplate(systemDataSource());

        // Crear usuario DDL (owner de esquema)
        sys.execute("CREATE USER " + OWNER_USER + " IDENTIFIED BY \"" + OWNER_PASS + "\"");
        sys.execute("GRANT CONNECT, RESOURCE, CREATE SESSION TO " + OWNER_USER);
        sys.execute("ALTER USER " + OWNER_USER + " QUOTA UNLIMITED ON USERS");

        // Crear usuario runtime (solo puede conectar y operar con GRANTs explícitos)
        sys.execute("CREATE USER " + APP_USER + " IDENTIFIED BY \"" + APP_PASS + "\"");
        sys.execute("GRANT CREATE SESSION TO " + APP_USER);

        // Aplicar migraciones Flyway como MEDITRIAJE_OWNER
        Flyway flyway = Flyway.configure()
                .dataSource(ownerDataSource())
                .locations("classpath:database/migrations")
                .schemas(OWNER_USER)
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .load();
        flyway.migrate();
    }

    @Test
    void flyway_schema_history_tieneAlMenosV7() {
        JdbcTemplate ownerTemplate = new JdbcTemplate(ownerDataSource());
        Integer count = ownerTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class
        );
        assertThat(count).isGreaterThanOrEqualTo(7);
    }

    @Test
    void app_puedeConsultarTablasTriaje() {
        // MEDITRIAJE_APP puede consultar las tablas de triaje (V007)
        JdbcTemplate appTemplate = new JdbcTemplate(appDataSource());
        for (String tabla : new String[] {"SINTOMA", "REGLA_TRIAJE", "TRIAJE", "TRIAJE_SINTOMA"}) {
            assertThat(appTemplate.queryForObject("SELECT COUNT(*) FROM " + OWNER_USER + "." + tabla, Integer.class))
                    .as("SELECT en " + tabla).isNotNull();
        }
    }

    @Test
    void semillas_triaje_tienenSintomasYAlarmas() {
        JdbcTemplate appTemplate = new JdbcTemplate(appDataSource());
        Integer total = appTemplate.queryForObject("SELECT COUNT(*) FROM " + OWNER_USER + ".SINTOMA", Integer.class);
        Integer alarmas = appTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + OWNER_USER + ".SINTOMA WHERE ES_ALARMA = 1", Integer.class);
        assertThat(total).isGreaterThanOrEqualTo(15);
        assertThat(alarmas).isGreaterThanOrEqualTo(1);
        // Cada síntoma no alarma tiene reglas que cubren las 11 intensidades (0-10)
        Integer sinCobertura = appTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + OWNER_USER + ".SINTOMA s WHERE s.ES_ALARMA = 0 AND "
                        + "(SELECT COUNT(*) FROM " + OWNER_USER + ".REGLA_TRIAJE r WHERE r.SINTOMA_ID = s.ID "
                        + "AND r.VERSION = 'v1-prototipo') <> 3",
                Integer.class);
        assertThat(sinCobertura).isZero();
    }

    @Test
    void app_noPuedeUpdateNiDeleteEnTriaje() {
        JdbcTemplate appTemplate = new JdbcTemplate(appDataSource());
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> appTemplate.update("UPDATE " + OWNER_USER + ".TRIAJE SET NIVEL_PRIORIDAD = 'V'"),
                "TRIAJE es inmutable: sin UPDATE para MEDITRIAJE_APP");
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> appTemplate.update("DELETE FROM " + OWNER_USER + ".TRIAJE"),
                "TRIAJE es inmutable: sin DELETE para MEDITRIAJE_APP");
    }

    @Test
    void db_fkCompuestaRechazaCitaConTriajeDeOtroPaciente() {
        JdbcTemplate ownerTemplate = new JdbcTemplate(ownerDataSource());
        String uid = java.util.UUID.randomUUID().toString().substring(0, 8);

        // Dos pacientes A y B
        Long[] pacientes = new Long[2];
        for (int i = 0; i < 2; i++) {
            String p = uid + "-t" + i;
            ownerTemplate.update("INSERT INTO " + OWNER_USER + ".USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES (?, ?, ?)",
                    "u-tri-" + p, "tri-" + p + "@test.com", "hash");
            Long uId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".USUARIO WHERE PUBLIC_ID = ?", Long.class, "u-tri-" + p);
            ownerTemplate.update(
                    "INSERT INTO " + OWNER_USER + ".PACIENTE (PUBLIC_ID, USUARIO_ID, NUMERO_IDENTIFICACION, NOMBRES, APELLIDOS, FECHA_NACIMIENTO, GENERO, TELEFONO) "
                            + "VALUES (?, ?, ?, ?, ?, DATE '1990-01-01', 'M', '3001234567')",
                    "pac-tri-" + p, uId, "CC-TRI-" + p, "Paciente" + i, "Test");
            pacientes[i] = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".PACIENTE WHERE PUBLIC_ID = ?", Long.class, "pac-tri-" + p);
        }

        // Triaje del paciente A
        ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".TRIAJE (PUBLIC_ID, PACIENTE_ID, VERSION_REGLAS, NIVEL_PRIORIDAD, RUTA_SUGERIDA) "
                        + "VALUES (?, ?, 'v1-prototipo', 'III', 'CITA_PRESENCIAL')",
                "tri-" + uid, pacientes[0]);
        Long triajeId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".TRIAJE WHERE PUBLIC_ID = ?", Long.class, "tri-" + uid);

        // Slot mínimo
        ownerTemplate.update("INSERT INTO " + OWNER_USER + ".USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES (?, ?, ?)",
                "u-doc-t-" + uid, "doc-t-" + uid + "@test.com", "hash");
        Long docUsuarioId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".USUARIO WHERE PUBLIC_ID = ?", Long.class, "u-doc-t-" + uid);
        ownerTemplate.update("INSERT INTO " + OWNER_USER + ".ESPECIALIDAD (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN) VALUES (?, ?, ?)",
                "esp-t-" + uid, "Esp-T-" + uid, 20);
        Long espId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".ESPECIALIDAD WHERE PUBLIC_ID = ?", Long.class, "esp-t-" + uid);
        ownerTemplate.update("INSERT INTO " + OWNER_USER + ".PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS) VALUES (?, ?, ?, ?, ?, ?)",
                docUsuarioId, "prof-t-" + uid, espId, "RM-T-" + uid, "Doc", "Test");
        Long profId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".PROFESIONAL WHERE PUBLIC_ID = ?", Long.class, "prof-t-" + uid);
        ownerTemplate.update("INSERT INTO " + OWNER_USER + ".INSTITUCION (PUBLIC_ID, NIT, RAZON_SOCIAL) VALUES (?, ?, ?)",
                "inst-t-" + uid, "NIT-T-" + uid, "Inst-T-" + uid);
        Long instId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".INSTITUCION WHERE PUBLIC_ID = ?", Long.class, "inst-t-" + uid);
        ownerTemplate.update("INSERT INTO " + OWNER_USER + ".SEDE (INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD) VALUES (?, ?, ?, ?, ?)",
                instId, "sede-t-" + uid, "Sede-T-" + uid, "Calle 1", "Bogota");
        Long sedeId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".SEDE WHERE PUBLIC_ID = ?", Long.class, "sede-t-" + uid);
        java.time.Instant ahora = java.time.Instant.now();
        ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, SEDE_ID, ESPECIALIDAD_ID, FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'PRESENCIAL', 'LIBRE')",
                "slot-t-" + uid, profId, sedeId, espId, java.sql.Timestamp.from(ahora), java.sql.Timestamp.from(ahora.plusSeconds(1200)));
        Long slotId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".DISPONIBILIDAD_SLOT WHERE PUBLIC_ID = ?", Long.class, "slot-t-" + uid);

        // Paciente B con triaje de A -> rechazado por FK_CITA_TRIAJE_PACIENTE
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> ownerTemplate.update(
                        "INSERT INTO " + OWNER_USER + ".CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, TRIAJE_ID) VALUES (?, ?, ?, ?)",
                        "cita-x-" + uid, slotId, pacientes[1], triajeId),
                "La FK compuesta debe rechazar una cita con triaje de otro paciente");

        // Paciente A con su propio triaje -> permitido
        int filas = ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, TRIAJE_ID) VALUES (?, ?, ?, ?)",
                "cita-ok-" + uid, slotId, pacientes[0], triajeId);
        assertThat(filas).isEqualTo(1);
    }

    @Test
    void app_puedeConsultarControlSistema() {
        // MEDITRIAJE_APP recibe GRANT SELECT en CONTROL_SISTEMA desde V001__baseline.sql
        JdbcTemplate appTemplate = new JdbcTemplate(appDataSource());
        Integer count = appTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + OWNER_USER + ".CONTROL_SISTEMA",
                Integer.class
        );
        assertThat(count).isNotNull();
    }

    @Test
    void app_puedeConsultarRolesSembrados() {
        // MEDITRIAJE_APP puede consultar el catálogo de roles (semillas de V002)
        JdbcTemplate appTemplate = new JdbcTemplate(appDataSource());
        Integer count = appTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + OWNER_USER + ".ROL",
                Integer.class
        );
        assertThat(count).isEqualTo(3);
    }

    @Test
    void app_puedeConsultarPacientes() {
        // MEDITRIAJE_APP puede consultar la tabla PACIENTE (V003)
        JdbcTemplate appTemplate = new JdbcTemplate(appDataSource());
        Integer count = appTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + OWNER_USER + ".PACIENTE",
                Integer.class
        );
        assertThat(count).isNotNull();
    }

    @Test
    void app_puedeConsultarTablasOferta() {
        // MEDITRIAJE_APP puede consultar las tablas de oferta asistencial (V004)
        JdbcTemplate appTemplate = new JdbcTemplate(appDataSource());
        assertThat(appTemplate.queryForObject("SELECT COUNT(*) FROM " + OWNER_USER + ".INSTITUCION", Integer.class)).isNotNull();
        assertThat(appTemplate.queryForObject("SELECT COUNT(*) FROM " + OWNER_USER + ".SEDE", Integer.class)).isNotNull();
        assertThat(appTemplate.queryForObject("SELECT COUNT(*) FROM " + OWNER_USER + ".ESPECIALIDAD", Integer.class)).isNotNull();
        assertThat(appTemplate.queryForObject("SELECT COUNT(*) FROM " + OWNER_USER + ".PROFESIONAL", Integer.class)).isNotNull();
        assertThat(appTemplate.queryForObject("SELECT COUNT(*) FROM " + OWNER_USER + ".DISPONIBILIDAD_SLOT", Integer.class)).isNotNull();
    }

    @Test
    void app_puedeConsultarCitas() {
        // MEDITRIAJE_APP puede consultar la tabla CITA (V006)
        JdbcTemplate appTemplate = new JdbcTemplate(appDataSource());
        assertThat(appTemplate.queryForObject("SELECT COUNT(*) FROM " + OWNER_USER + ".CITA", Integer.class)).isNotNull();
    }

    @Test
    void db_rechazaDobleCitaActivaEnMismoSlot() {
        // Demuestra a nivel de motor Oracle el índice funcional único UQ_CITA_SLOT_ACTIVA (ADR-006)
        JdbcTemplate ownerTemplate = new JdbcTemplate(ownerDataSource());
        String uid = java.util.UUID.randomUUID().toString().substring(0, 8);

        // 1. Crear datos mínimos para la prueba
        ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES (?, ?, ?)",
                "u-doc-" + uid, "doc-" + uid + "@test.com", "hash"
        );
        Long docUsuarioId = ownerTemplate.queryForObject(
                "SELECT ID FROM " + OWNER_USER + ".USUARIO WHERE PUBLIC_ID = ?", Long.class, "u-doc-" + uid
        );

        ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".ESPECIALIDAD (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN) VALUES (?, ?, ?)",
                "esp-" + uid, "Esp-" + uid, 20
        );
        Long espId = ownerTemplate.queryForObject(
                "SELECT ID FROM " + OWNER_USER + ".ESPECIALIDAD WHERE PUBLIC_ID = ?", Long.class, "esp-" + uid
        );

        ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS) VALUES (?, ?, ?, ?, ?, ?)",
                docUsuarioId, "prof-" + uid, espId, "RM-" + uid, "Doc", "Test"
        );
        Long profId = ownerTemplate.queryForObject(
                "SELECT ID FROM " + OWNER_USER + ".PROFESIONAL WHERE PUBLIC_ID = ?", Long.class, "prof-" + uid
        );

        ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".INSTITUCION (PUBLIC_ID, NIT, RAZON_SOCIAL) VALUES (?, ?, ?)",
                "inst-" + uid, "NIT-" + uid, "Inst-" + uid
        );
        Long instId = ownerTemplate.queryForObject(
                "SELECT ID FROM " + OWNER_USER + ".INSTITUCION WHERE PUBLIC_ID = ?", Long.class, "inst-" + uid
        );

        ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".SEDE (INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD) VALUES (?, ?, ?, ?, ?)",
                instId, "sede-" + uid, "Sede-" + uid, "Calle 1", "Bogota"
        );
        Long sedeId = ownerTemplate.queryForObject(
                "SELECT ID FROM " + OWNER_USER + ".SEDE WHERE PUBLIC_ID = ?", Long.class, "sede-" + uid
        );

        ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES (?, ?, ?)",
                "u-pac-" + uid, "pac-" + uid + "@test.com", "hash"
        );
        Long pacUsuarioId = ownerTemplate.queryForObject(
                "SELECT ID FROM " + OWNER_USER + ".USUARIO WHERE PUBLIC_ID = ?", Long.class, "u-pac-" + uid
        );

        ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".PACIENTE (PUBLIC_ID, USUARIO_ID, NUMERO_IDENTIFICACION, NOMBRES, APELLIDOS, FECHA_NACIMIENTO, GENERO, TELEFONO) " +
                "VALUES (?, ?, ?, ?, ?, DATE '1990-01-01', 'M', '3001234567')",
                "pac-" + uid, pacUsuarioId, "CC-" + uid, "Paciente", "Test"
        );
        Long pacId = ownerTemplate.queryForObject(
                "SELECT ID FROM " + OWNER_USER + ".PACIENTE WHERE PUBLIC_ID = ?", Long.class, "pac-" + uid
        );

        java.time.Instant ahora = java.time.Instant.now();
        ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, SEDE_ID, ESPECIALIDAD_ID, FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'PRESENCIAL', 'LIBRE')",
                "slot-" + uid, profId, sedeId, espId, java.sql.Timestamp.from(ahora), java.sql.Timestamp.from(ahora.plusSeconds(1200))
        );
        Long slotId = ownerTemplate.queryForObject(
                "SELECT ID FROM " + OWNER_USER + ".DISPONIBILIDAD_SLOT WHERE PUBLIC_ID = ?", Long.class, "slot-" + uid
        );

        // 2. Primera cita en estado PROGRAMADA sobre el slot -> ÉXITO
        ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, ESTADO) VALUES (?, ?, ?, 'PROGRAMADA')",
                "cita-1-" + uid, slotId, pacId
        );

        // 3. Segunda cita concurrente en estado PROGRAMADA sobre el MISMO slot -> Debe fallar por UQ_CITA_SLOT_ACTIVA
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> ownerTemplate.update(
                        "INSERT INTO " + OWNER_USER + ".CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, ESTADO) VALUES (?, ?, ?, 'PROGRAMADA')",
                        "cita-2-" + uid, slotId, pacId
                ),
                "El índice UQ_CITA_SLOT_ACTIVA debe impedir registrar una segunda cita activa en el mismo slot"
        );

        // 4. Cancelar la primera cita (pasa a estado CANCELADA)
        ownerTemplate.update(
                "UPDATE " + OWNER_USER + ".CITA SET ESTADO = 'CANCELADA' WHERE PUBLIC_ID = ?",
                "cita-1-" + uid
        );

        // 5. Ahora sí debe permitir registrar una nueva cita activa en el mismo slot (el CASE devuelve NULL para CANCELADA)
        int filasInsertadas = ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, ESTADO) VALUES (?, ?, ?, 'PROGRAMADA')",
                "cita-3-" + uid, slotId, pacId
        );
        assertThat(filasInsertadas).isEqualTo(1);
    }

    @Test
    void db_concurrenciaMultihilo_diezHilosMismoSlot_exactamenteUnoGana() throws Exception {
        // Validación determinista multihilo en base de datos real (ADR-006, M4.4)
        JdbcTemplate ownerTemplate = new JdbcTemplate(ownerDataSource());
        String uid = java.util.UUID.randomUUID().toString().substring(0, 8);

        // 1. Crear profesional, sede, especialidad y slot libre
        ownerTemplate.update("INSERT INTO " + OWNER_USER + ".USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES (?, ?, ?)",
                "u-doc-mc-" + uid, "doc-mc-" + uid + "@test.com", "hash");
        Long docUsuarioId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".USUARIO WHERE PUBLIC_ID = ?", Long.class, "u-doc-mc-" + uid);

        ownerTemplate.update("INSERT INTO " + OWNER_USER + ".ESPECIALIDAD (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN) VALUES (?, ?, ?)",
                "esp-mc-" + uid, "Esp-MC-" + uid, 20);
        Long espId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".ESPECIALIDAD WHERE PUBLIC_ID = ?", Long.class, "esp-mc-" + uid);

        ownerTemplate.update("INSERT INTO " + OWNER_USER + ".PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS) VALUES (?, ?, ?, ?, ?, ?)",
                docUsuarioId, "prof-mc-" + uid, espId, "RM-MC-" + uid, "Doc", "MultiThread");
        Long profId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".PROFESIONAL WHERE PUBLIC_ID = ?", Long.class, "prof-mc-" + uid);

        ownerTemplate.update("INSERT INTO " + OWNER_USER + ".INSTITUCION (PUBLIC_ID, NIT, RAZON_SOCIAL) VALUES (?, ?, ?)",
                "inst-mc-" + uid, "NIT-MC-" + uid, "Inst-MC-" + uid);
        Long instId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".INSTITUCION WHERE PUBLIC_ID = ?", Long.class, "inst-mc-" + uid);

        ownerTemplate.update("INSERT INTO " + OWNER_USER + ".SEDE (INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD) VALUES (?, ?, ?, ?, ?)",
                instId, "sede-mc-" + uid, "Sede-MC-" + uid, "Calle 10", "Bogota");
        Long sedeId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".SEDE WHERE PUBLIC_ID = ?", Long.class, "sede-mc-" + uid);

        java.time.Instant ahora = java.time.Instant.now();
        ownerTemplate.update(
                "INSERT INTO " + OWNER_USER + ".DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, SEDE_ID, ESPECIALIDAD_ID, FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'PRESENCIAL', 'LIBRE')",
                "slot-mc-" + uid, profId, sedeId, espId, java.sql.Timestamp.from(ahora), java.sql.Timestamp.from(ahora.plusSeconds(1200))
        );
        Long slotId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".DISPONIBILIDAD_SLOT WHERE PUBLIC_ID = ?", Long.class, "slot-mc-" + uid);

        // 2. Crear 10 pacientes distintos
        int numHilos = 10;
        java.util.List<Long> pacienteIds = new java.util.ArrayList<>();
        for (int i = 0; i < numHilos; i++) {
            String pUid = uid + "-" + i;
            ownerTemplate.update("INSERT INTO " + OWNER_USER + ".USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES (?, ?, ?)",
                    "u-pac-mc-" + pUid, "pac-mc-" + pUid + "@test.com", "hash");
            Long uId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".USUARIO WHERE PUBLIC_ID = ?", Long.class, "u-pac-mc-" + pUid);

            ownerTemplate.update(
                    "INSERT INTO " + OWNER_USER + ".PACIENTE (PUBLIC_ID, USUARIO_ID, NUMERO_IDENTIFICACION, NOMBRES, APELLIDOS, FECHA_NACIMIENTO, GENERO, TELEFONO) " +
                    "VALUES (?, ?, ?, ?, ?, DATE '1990-01-01', 'M', '3001234567')",
                    "pac-mc-" + pUid, uId, "CC-MC-" + pUid, "Paciente" + i, "Test"
            );
            Long pId = ownerTemplate.queryForObject("SELECT ID FROM " + OWNER_USER + ".PACIENTE WHERE PUBLIC_ID = ?", Long.class, "pac-mc-" + pUid);
            pacienteIds.add(pId);
        }

        // 3. Ejecutar 10 hilos concurrentes intentando reservar el MISMO slot
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(numHilos);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(numHilos);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finishLatch = new java.util.concurrent.CountDownLatch(numHilos);

        java.util.concurrent.atomic.AtomicInteger exitos = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger noDisponibles = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < numHilos; i++) {
            final int index = i;
            final Long pacId = pacienteIds.get(i);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    // Operación de reserva atómica idéntica a la capa de persistencia
                    int filas = ownerTemplate.update(
                            "UPDATE " + OWNER_USER + ".DISPONIBILIDAD_SLOT SET ESTADO = 'OCUPADO' WHERE ID = ? AND ESTADO = 'LIBRE'",
                            slotId
                    );
                    if (filas == 1) {
                        ownerTemplate.update(
                                "INSERT INTO " + OWNER_USER + ".CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, ESTADO) VALUES (?, ?, ?, 'PROGRAMADA')",
                                "cita-mc-" + uid + "-" + index, slotId, pacId
                        );
                        exitos.incrementAndGet();
                    } else {
                        noDisponibles.incrementAndGet();
                    }
                } catch (Exception e) {
                    noDisponibles.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        readyLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        startLatch.countDown();
        finishLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(exitos.get()).as("Exactamente 1 hilo de 10 debe reservar el slot en la BD").isEqualTo(1);
        assertThat(noDisponibles.get()).as("Los 9 hilos restantes deben fallar al intentar reservar").isEqualTo(numHilos - 1);

        Integer citasActivas = ownerTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + OWNER_USER + ".CITA WHERE SLOT_ID = ? AND ESTADO = 'PROGRAMADA'",
                Integer.class, slotId
        );
        assertThat(citasActivas).as("Solo debe existir 1 cita activa en la tabla CITA").isEqualTo(1);
    }

    @Test
    void app_noPuedeHacerDropTable() {
        // MEDITRIAJE_APP NO debe tener privilegio de DDL
        JdbcTemplate appTemplate = new JdbcTemplate(appDataSource());
        boolean tuvoError = false;
        try {
            appTemplate.execute("DROP TABLE " + OWNER_USER + ".CONTROL_SISTEMA");
        } catch (Exception e) {
            tuvoError = true;
        }
        assertThat(tuvoError).as("MEDITRIAJE_APP no debe poder hacer DROP TABLE").isTrue();
    }

    @Test
    void app_noPuedeHacerUpdateAuditoria() {
        // AUDITORIA es insert-only para MEDITRIAJE_APP (sin GRANT UPDATE ni DELETE)
        JdbcTemplate appTemplate = new JdbcTemplate(appDataSource());
        boolean tuvoError = false;
        try {
            appTemplate.execute("UPDATE " + OWNER_USER + ".AUDITORIA SET ACCION = 'MODIFICADA'");
        } catch (Exception e) {
            tuvoError = true;
        }
        assertThat(tuvoError).as("MEDITRIAJE_APP no debe poder hacer UPDATE sobre AUDITORIA").isTrue();
    }
}
