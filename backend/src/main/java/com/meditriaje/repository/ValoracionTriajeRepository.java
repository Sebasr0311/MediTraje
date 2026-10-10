package com.meditriaje.repository;

import com.meditriaje.dto.emergency.ValoracionTriajeResponse;
import com.meditriaje.model.ValoracionTriaje;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para {@code VALORACION_TRIAJE} inmutable (Fase U, ADR-027, U04).
 */
@Repository
public class ValoracionTriajeRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ValoracionTriaje> rowMapper = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        int fc = rs.getInt("FRECUENCIA_CARDIACA");
        int fr = rs.getInt("FRECUENCIA_RESPIRATORIA");
        int so2 = rs.getInt("SATURACION_OXIGENO");
        int glasgow = rs.getInt("ESCALA_GLASGOW");
        BigDecimal temp = rs.getBigDecimal("TEMPERATURA");

        return new ValoracionTriaje(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getInt("VERSION"),
                rs.getString("NIVEL"),
                rs.getString("MOTIVO_CONSULTA"),
                rs.getString("HALLAZGOS_CLINICOS"),
                rs.getString("PRESION_ARTERIAL"),
                rs.wasNull() ? null : fc,
                rs.wasNull() ? null : fr,
                rs.wasNull() ? null : so2,
                temp,
                rs.wasNull() ? null : glasgow,
                rs.getLong("EVALUADOR_USUARIO_ID"),
                rs.getInt("ES_REEVALUACION") == 1,
                rs.getString("MOTIVO_REEVALUACION"),
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    public ValoracionTriajeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public int obtenerSiguienteVersion(Long episodioId) {
        String sql = "SELECT COALESCE(MAX(VERSION), 0) + 1 FROM VALORACION_TRIAJE WHERE EPISODIO_ID = ?";
        Integer version = jdbcTemplate.queryForObject(sql, Integer.class, episodioId);
        return version != null ? version : 1;
    }

    public ValoracionTriaje guardar(ValoracionTriaje valoracion) {
        String sql = """
                INSERT INTO VALORACION_TRIAJE (
                    PUBLIC_ID, EPISODIO_ID, VERSION, NIVEL, MOTIVO_CONSULTA, HALLAZGOS_CLINICOS,
                    PRESION_ARTERIAL, FRECUENCIA_CARDIACA, FRECUENCIA_RESPIRATORIA, SATURACION_OXIGENO,
                    TEMPERATURA, ESCALA_GLASGOW, EVALUADOR_USUARIO_ID, ES_REEVALUACION, MOTIVO_REEVALUACION
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, valoracion.publicId());
            ps.setLong(2, valoracion.episodioId());
            ps.setInt(3, valoracion.version());
            ps.setString(4, valoracion.nivel());
            ps.setString(5, valoracion.motivoConsulta());
            ps.setString(6, valoracion.hallazgosClinicos());
            ps.setString(7, valoracion.presionArterial());

            if (valoracion.frecuenciaCardiaca() != null) {
                ps.setInt(8, valoracion.frecuenciaCardiaca());
            } else {
                ps.setNull(8, java.sql.Types.NUMERIC);
            }
            if (valoracion.frecuenciaRespiratoria() != null) {
                ps.setInt(9, valoracion.frecuenciaRespiratoria());
            } else {
                ps.setNull(9, java.sql.Types.NUMERIC);
            }
            if (valoracion.saturacionOxigeno() != null) {
                ps.setInt(10, valoracion.saturacionOxigeno());
            } else {
                ps.setNull(10, java.sql.Types.NUMERIC);
            }
            if (valoracion.temperatura() != null) {
                ps.setBigDecimal(11, valoracion.temperatura());
            } else {
                ps.setNull(11, java.sql.Types.NUMERIC);
            }
            if (valoracion.escalaGlasgow() != null) {
                ps.setInt(12, valoracion.escalaGlasgow());
            } else {
                ps.setNull(12, java.sql.Types.NUMERIC);
            }

            ps.setLong(13, valoracion.evaluadorUsuarioId());
            ps.setInt(14, valoracion.esReevaluacion() ? 1 : 0);
            ps.setString(15, valoracion.motivoReevaluacion());
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        Long generatedId = id != null ? id.longValue() : null;

        return new ValoracionTriaje(
                generatedId,
                valoracion.publicId(),
                valoracion.episodioId(),
                valoracion.version(),
                valoracion.nivel(),
                valoracion.motivoConsulta(),
                valoracion.hallazgosClinicos(),
                valoracion.presionArterial(),
                valoracion.frecuenciaCardiaca(),
                valoracion.frecuenciaRespiratoria(),
                valoracion.saturacionOxigeno(),
                valoracion.temperatura(),
                valoracion.escalaGlasgow(),
                valoracion.evaluadorUsuarioId(),
                valoracion.esReevaluacion(),
                valoracion.motivoReevaluacion(),
                Instant.now()
        );
    }

    public List<ValoracionTriajeResponse> listarPorEpisodioId(Long episodioId) {
        String sql = """
                SELECT vt.PUBLIC_ID,
                       e.PUBLIC_ID AS EPISODIO_PUB_ID,
                       vt.VERSION,
                       vt.NIVEL,
                       vt.MOTIVO_CONSULTA,
                       vt.HALLAZGOS_CLINICOS,
                       vt.PRESION_ARTERIAL,
                       vt.FRECUENCIA_CARDIACA,
                       vt.FRECUENCIA_RESPIRATORIA,
                       vt.SATURACION_OXIGENO,
                       vt.TEMPERATURA,
                       vt.ESCALA_GLASGOW,
                       u.NOMBRE || ' ' || u.APELLIDO AS EVALUADOR_NOMBRE,
                       vt.ES_REEVALUACION,
                       vt.MOTIVO_REEVALUACION,
                       vt.CREADO_AT
                FROM VALORACION_TRIAJE vt
                JOIN EPISODIO_ATENCION e ON vt.EPISODIO_ID = e.ID
                JOIN USUARIO u ON vt.EVALUADOR_USUARIO_ID = u.ID
                WHERE vt.EPISODIO_ID = ?
                ORDER BY vt.VERSION ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
            int fc = rs.getInt("FRECUENCIA_CARDIACA");
            int fr = rs.getInt("FRECUENCIA_RESPIRATORIA");
            int so2 = rs.getInt("SATURACION_OXIGENO");
            int glasgow = rs.getInt("ESCALA_GLASGOW");
            BigDecimal temp = rs.getBigDecimal("TEMPERATURA");

            return new ValoracionTriajeResponse(
                    rs.getString("PUBLIC_ID"),
                    rs.getString("EPISODIO_PUB_ID"),
                    rs.getInt("VERSION"),
                    rs.getString("NIVEL"),
                    rs.getString("MOTIVO_CONSULTA"),
                    rs.getString("HALLAZGOS_CLINICOS"),
                    rs.getString("PRESION_ARTERIAL"),
                    rs.wasNull() ? null : fc,
                    rs.wasNull() ? null : fr,
                    rs.wasNull() ? null : so2,
                    temp,
                    rs.wasNull() ? null : glasgow,
                    rs.getString("EVALUADOR_NOMBRE"),
                    rs.getInt("ES_REEVALUACION") == 1,
                    rs.getString("MOTIVO_REEVALUACION"),
                    tsCreated != null ? tsCreated.toInstant() : null
            );
        }, episodioId);
    }

    public Optional<ValoracionTriaje> buscarUltimaPorEpisodioId(Long episodioId) {
        String sql = """
                SELECT * FROM VALORACION_TRIAJE
                WHERE EPISODIO_ID = ?
                ORDER BY VERSION DESC
                FETCH FIRST 1 ROWS ONLY
                """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, episodioId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
