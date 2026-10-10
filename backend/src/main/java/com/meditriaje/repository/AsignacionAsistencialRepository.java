package com.meditriaje.repository;

import com.meditriaje.model.AsignacionAsistencial;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para {@code ASIGNACION_ASISTENCIAL} (Fase U, ADR-022, U06).
 */
@Repository
public class AsignacionAsistencialRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AsignacionAsistencial> rowMapper = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("FECHA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_FIN");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");

        return new AsignacionAsistencial(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getLong("PROFESIONAL_ID"),
                rs.getString("FUNCION"),
                rs.getLong("ASIGNADO_POR_USUARIO_ID"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getInt("ACTIVO") == 1,
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    public AsignacionAsistencialRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public AsignacionAsistencial guardar(AsignacionAsistencial asignacion) {
        String sql = """
                INSERT INTO ASIGNACION_ASISTENCIAL (
                    PUBLIC_ID, EPISODIO_ID, PROFESIONAL_ID, FUNCION, ASIGNADO_POR_USUARIO_ID, FECHA_INICIO, ACTIVO
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, asignacion.publicId());
            ps.setLong(2, asignacion.episodioId());
            ps.setLong(3, asignacion.profesionalId());
            ps.setString(4, asignacion.funcion());
            ps.setLong(5, asignacion.asignadoPorUsuarioId());
            ps.setTimestamp(6, asignacion.fechaInicio() != null ? Timestamp.from(asignacion.fechaInicio()) : Timestamp.from(Instant.now()));
            ps.setInt(7, asignacion.activo() ? 1 : 0);
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        Long generatedId = id != null ? id.longValue() : null;

        return new AsignacionAsistencial(
                generatedId,
                asignacion.publicId(),
                asignacion.episodioId(),
                asignacion.profesionalId(),
                asignacion.funcion(),
                asignacion.asignadoPorUsuarioId(),
                asignacion.fechaInicio() != null ? asignacion.fechaInicio() : Instant.now(),
                null,
                true,
                Instant.now()
        );
    }

    public int inactivarAsignacionPreviaPorFuncion(Long episodioId, String funcion) {
        String sql = """
                UPDATE ASIGNACION_ASISTENCIAL
                SET ACTIVO = 0, FECHA_FIN = CURRENT_TIMESTAMP
                WHERE EPISODIO_ID = ? AND FUNCION = ? AND ACTIVO = 1
                """;
        return jdbcTemplate.update(sql, episodioId, funcion);
    }

    public boolean tieneAsignacionActiva(Long profesionalId, Long episodioId) {
        String sql = """
                SELECT COUNT(*) FROM ASIGNACION_ASISTENCIAL
                WHERE PROFESIONAL_ID = ? AND EPISODIO_ID = ? AND ACTIVO = 1
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, profesionalId, episodioId);
        return count != null && count > 0;
    }

    public List<AsignacionAsistencial> listarPorEpisodioId(Long episodioId) {
        String sql = "SELECT * FROM ASIGNACION_ASISTENCIAL WHERE EPISODIO_ID = ? ORDER BY FECHA_INICIO ASC";
        return jdbcTemplate.query(sql, rowMapper, episodioId);
    }
}
