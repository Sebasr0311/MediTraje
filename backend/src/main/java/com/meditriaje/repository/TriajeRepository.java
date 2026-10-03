package com.meditriaje.repository;

import com.meditriaje.dto.triage.CatalogoSintomaResponse;
import com.meditriaje.dto.triage.SintomaItemResponse;
import com.meditriaje.dto.triage.TriajeResponse;
import com.meditriaje.model.Triaje;
import com.meditriaje.model.TriajeSintoma;
import com.meditriaje.triage.MotorTriajeBasadoEnReglas;
import com.meditriaje.triage.RutaSugerida;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la persistencia y consulta inmutable de triajes clínicos (ADR-001, ADR-009, HU-02).
 */
@Repository
public class TriajeRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Triaje> triajeRowMapper = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new Triaje(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getString("VERSION_REGLAS"),
                rs.getString("NIVEL_PRIORIDAD"),
                rs.getString("RUTA_SUGERIDA"),
                rs.getInt("ES_EMERGENCIA") == 1,
                rs.getString("OBSERVACIONES"),
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    public TriajeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    /**
     * Inserta un triaje evaluado y retorna el ID interno autogenerado (ADR-003, ADR-009).
     */
    public Long guardarTriaje(Triaje triaje) {
        Objects.requireNonNull(triaje, "El triaje no puede ser nulo");
        final String sql = """
            INSERT INTO TRIAJE (PUBLIC_ID, PACIENTE_ID, VERSION_REGLAS, NIVEL_PRIORIDAD, RUTA_SUGERIDA, ES_EMERGENCIA, OBSERVACIONES)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setString(1, triaje.publicId());
            ps.setLong(2, triaje.pacienteId());
            ps.setString(3, triaje.versionReglas());
            ps.setString(4, triaje.nivelPrioridad());
            ps.setString(5, triaje.rutaSugerida());
            ps.setInt(6, triaje.esEmergencia() ? 1 : 0);
            ps.setString(7, triaje.observaciones());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el triaje.");
        }
        return key.longValue();
    }

    /**
     * Inserta por lote los síntomas reportados en la evaluación de triaje (ADR-009).
     */
    public void guardarSintomas(Long triajeId, List<TriajeSintoma> sintomas) {
        Objects.requireNonNull(triajeId, "triajeId no puede ser nulo");
        if (sintomas == null || sintomas.isEmpty()) {
            return;
        }

        final String sql = """
            INSERT INTO TRIAJE_SINTOMA (TRIAJE_ID, SINTOMA_ID, DURACION_HORAS, INTENSIDAD)
            VALUES (?, ?, ?, ?)
            """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                TriajeSintoma s = sintomas.get(i);
                ps.setLong(1, triajeId);
                ps.setLong(2, s.sintomaId());
                ps.setBigDecimal(3, s.duracionHoras());
                ps.setInt(4, s.intensidad());
            }

            @Override
            public int getBatchSize() {
                return sintomas.size();
            }
        });
    }

    /**
     * Consulta consolidada de un triaje por su identificador público UUID con sus síntomas y paciente (ADR-003, ADR-009).
     */
    public Optional<TriajeResponse> buscarPorPublicId(String publicId) {
        Objects.requireNonNull(publicId, "El publicId no puede ser nulo");
        final String sql = """
            SELECT t.ID AS TRIAJE_ID,
                   t.PUBLIC_ID AS TRIAJE_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   t.VERSION_REGLAS,
                   t.NIVEL_PRIORIDAD,
                   t.RUTA_SUGERIDA,
                   t.ES_EMERGENCIA,
                   t.OBSERVACIONES,
                   t.CREATED_AT,
                   s.CODIGO AS SINTOMA_CODIGO,
                   s.NOMBRE AS SINTOMA_NOMBRE,
                   ts.DURACION_HORAS,
                   ts.INTENSIDAD,
                   s.ES_ALARMA
            FROM TRIAJE t
            JOIN PACIENTE p ON t.PACIENTE_ID = p.ID
            LEFT JOIN TRIAJE_SINTOMA ts ON ts.TRIAJE_ID = t.ID
            LEFT JOIN SINTOMA s ON ts.SINTOMA_ID = s.ID
            WHERE t.PUBLIC_ID = ?
            ORDER BY s.NOMBRE ASC
            """;

        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }

            String triajePublicId = rs.getString("TRIAJE_PUBLIC_ID");
            String pacientePublicId = rs.getString("PACIENTE_PUBLIC_ID");
            String versionReglas = rs.getString("VERSION_REGLAS");
            String nivelPrioridad = rs.getString("NIVEL_PRIORIDAD");
            String rutaSugerida = rs.getString("RUTA_SUGERIDA");
            boolean esEmergencia = rs.getInt("ES_EMERGENCIA") == 1;
            String observaciones = rs.getString("OBSERVACIONES");
            Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
            Instant createdAt = tsCreated != null ? tsCreated.toInstant() : null;

            List<SintomaItemResponse> sintomas = new ArrayList<>();
            List<String> sintomasAlarma = new ArrayList<>();

            do {
                String codigo = rs.getString("SINTOMA_CODIGO");
                if (codigo != null) {
                    String nombre = rs.getString("SINTOMA_NOMBRE");
                    BigDecimal duracion = rs.getBigDecimal("DURACION_HORAS");
                    int intensidad = rs.getInt("INTENSIDAD");
                    boolean esAlarma = rs.getInt("ES_ALARMA") == 1;

                    sintomas.add(new SintomaItemResponse(codigo, nombre, duracion, intensidad, esAlarma));
                    if (esAlarma) {
                        sintomasAlarma.add(codigo);
                    }
                }
            } while (rs.next());

            sintomasAlarma.sort(String::compareTo);

            String mensaje = esEmergencia
                    ? MotorTriajeBasadoEnReglas.MENSAJE_EMERGENCIA
                    : MotorTriajeBasadoEnReglas.mensajePara(RutaSugerida.valueOf(rutaSugerida));

            return Optional.of(new TriajeResponse(
                    triajePublicId,
                    pacientePublicId,
                    nivelPrioridad,
                    rutaSugerida,
                    esEmergencia,
                    versionReglas,
                    mensaje,
                    MotorTriajeBasadoEnReglas.AVISO,
                    sintomasAlarma,
                    sintomas,
                    observaciones,
                    createdAt
            ));
        }, publicId);
    }

    /**
     * Busca la entidad Triaje por su clave pública UUID (ADR-003, ADR-009).
     */
    public Optional<Triaje> buscarEntidadPorPublicId(String publicId) {
        Objects.requireNonNull(publicId, "El publicId no puede ser nulo");
        final String sql = """
            SELECT ID, PUBLIC_ID, PACIENTE_ID, VERSION_REGLAS, NIVEL_PRIORIDAD, RUTA_SUGERIDA,
                   ES_EMERGENCIA, OBSERVACIONES, CREATED_AT
            FROM TRIAJE
            WHERE PUBLIC_ID = ?
            """;
        List<Triaje> resultados = jdbcTemplate.query(sql, triajeRowMapper, publicId);
        return resultados.stream().findFirst();
    }

    /**
     * Busca la entidad Triaje por su ID interno numérico (ADR-003).
     */
    public Optional<Triaje> buscarEntidadPorId(Long id) {
        Objects.requireNonNull(id, "El id no puede ser nulo");
        final String sql = """
            SELECT ID, PUBLIC_ID, PACIENTE_ID, VERSION_REGLAS, NIVEL_PRIORIDAD, RUTA_SUGERIDA,
                   ES_EMERGENCIA, OBSERVACIONES, CREATED_AT
            FROM TRIAJE
            WHERE ID = ?
            """;
        List<Triaje> resultados = jdbcTemplate.query(sql, triajeRowMapper, id);
        return resultados.stream().findFirst();
    }

    /**
     * Lista los síntomas activos del catálogo ordenados por categoría y nombre (ADR-009).
     */
    public List<CatalogoSintomaResponse> listarCatalogoSintomasActivos() {
        final String sql = """
            SELECT PUBLIC_ID, CODIGO, NOMBRE, CATEGORIA, ES_ALARMA
            FROM SINTOMA
            WHERE ESTADO = 'ACTIVO'
            ORDER BY CATEGORIA, NOMBRE
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CatalogoSintomaResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("CODIGO"),
                rs.getString("NOMBRE"),
                rs.getString("CATEGORIA"),
                rs.getInt("ES_ALARMA") == 1
        ));
    }

    /**
     * Mapeo rápido de código a ID interno numérico de los síntomas activos en base de datos.
     */
    public Map<String, Long> obtenerMapaCodigoAIdSintomas() {
        final String sql = "SELECT CODIGO, ID FROM SINTOMA WHERE ESTADO = 'ACTIVO'";
        Map<String, Long> mapa = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            mapa.put(rs.getString("CODIGO"), rs.getLong("ID"));
        });
        return mapa;
    }
}
