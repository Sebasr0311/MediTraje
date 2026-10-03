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
    void flyway_schema_history_tieneAlMenosV3() {
        JdbcTemplate ownerTemplate = new JdbcTemplate(ownerDataSource());
        Integer count = ownerTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class
        );
        assertThat(count).isGreaterThanOrEqualTo(3);
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
