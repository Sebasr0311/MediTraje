package com.meditriaje.repository;

import com.meditriaje.model.IngresoUrgencia;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para {@code INGRESO_URGENCIA} (Fase U, ADR-022, U02).
 */
@Repository
public class IngresoUrgenciaRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<IngresoUrgencia> rowMapper = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");

        return new IngresoUrgencia(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getString("MEDIO_LLEGADA"),
                rs.getString("ACOMPANANTE_NOMBRE"),
                rs.getString("ACOMPANANTE_CONTACTO"),
                rs.getString("MOTIVO_RESUMIDO"),
                rs.getString("OBSERVACIONES"),
                rs.getLong("REGISTRADO_POR_USUARIO_ID"),
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    public IngresoUrgenciaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public IngresoUrgencia guardar(IngresoUrgencia ingreso) {
        String sql = """
                INSERT INTO INGRESO_URGENCIA (
                    PUBLIC_ID, EPISODIO_ID, MEDIO_LLEGADA, ACOMPANANTE_NOMBRE, ACOMPANANTE_CONTACTO,
                    MOTIVO_RESUMIDO, OBSERVACIONES, REGISTRADO_POR_USUARIO_ID
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, ingreso.publicId());
            ps.setLong(2, ingreso.episodioId());
            ps.setString(3, ingreso.medioLlegada());
            ps.setString(4, ingreso.acompananteNombre());
            ps.setString(5, ingreso.acompananteContacto());
            ps.setString(6, ingreso.motivoResumido());
            ps.setString(7, ingreso.observaciones());
            ps.setLong(8, ingreso.registradoPorUsuarioId());
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        Long generatedId = id != null ? id.longValue() : null;

        return new IngresoUrgencia(
                generatedId,
                ingreso.publicId(),
                ingreso.episodioId(),
                ingreso.medioLlegada(),
                ingreso.acompananteNombre(),
                ingreso.acompananteContacto(),
                ingreso.motivoResumido(),
                ingreso.observaciones(),
                ingreso.registradoPorUsuarioId(),
                Instant.now()
        );
    }

    public Optional<IngresoUrgencia> buscarPorEpisodioId(Long episodioId) {
        String sql = "SELECT * FROM INGRESO_URGENCIA WHERE EPISODIO_ID = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, episodioId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
