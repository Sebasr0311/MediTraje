package com.meditriaje.repository;

import com.meditriaje.model.Alergia;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

/**
 * Repositorio JDBC para la entidad {@code ALERGIA} (ADR-008, V008).
 * Consultas e inserciones con SQL 100% parametrizado.
 */
@Repository
public class AlergiaRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Alergia> rowMapper = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREATED_AT");
        return new Alergia(
                rs.getLong("ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getString("SUSTANCIA"),
                rs.getString("REACCION"),
                rs.getString("SEVERIDAD"),
                ts != null ? ts.toInstant() : null
        );
    };

    public AlergiaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    /**
     * Lista todas las alergias registradas para un paciente.
     */
    public List<Alergia> listarPorPacienteId(Long pacienteId) {
        if (pacienteId == null) {
            return List.of();
        }
        String sql = """
                SELECT ID, PACIENTE_ID, SUSTANCIA, REACCION, SEVERIDAD, CREATED_AT
                FROM ALERGIA
                WHERE PACIENTE_ID = ?
                ORDER BY CREATED_AT DESC
                """;
        return jdbcTemplate.query(sql, rowMapper, pacienteId);
    }

    /**
     * Registra una nueva alergia para un paciente.
     */
    public Long crear(Alergia alergia) {
        Objects.requireNonNull(alergia, "alergia no puede ser nula");
        String sql = """
                INSERT INTO ALERGIA (PACIENTE_ID, SUSTANCIA, REACCION, SEVERIDAD)
                VALUES (?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, alergia.pacienteId());
            ps.setString(2, alergia.sustancia());
            ps.setString(3, alergia.reaccion());
            ps.setString(4, alergia.severidad());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        if (!keyHolder.getKeyList().isEmpty() && keyHolder.getKeyList().get(0).containsKey("ID")) {
            return ((Number) keyHolder.getKeyList().get(0).get("ID")).longValue();
        }
        throw new IllegalStateException("No se pudo obtener el ID autogenerado para ALERGIA.");
    }
}
