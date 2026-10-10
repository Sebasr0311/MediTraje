package com.meditriaje.repository;

import com.meditriaje.model.IdentidadProvisional;
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
 * Repositorio JDBC para {@code IDENTIDAD_PROVISIONAL} (Fase U, ADR-023, U03).
 */
@Repository
public class IdentidadProvisionalRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<IdentidadProvisional> rowMapper = (rs, rowNum) -> {
        Timestamp tsConf = rs.getTimestamp("FECHA_CONFIRMACION");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        long pacId = rs.getLong("VINCULADO_PACIENTE_ID");
        long confPor = rs.getLong("CONFIRMADO_POR_USUARIO_ID");
        int edad = rs.getInt("EDAD_APARENTE");

        return new IdentidadProvisional(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getString("CODIGO_PROVISIONAL"),
                rs.getString("DESCRIPCION_FISICA"),
                rs.wasNull() ? null : edad,
                rs.getString("GENERO_APARENTE"),
                rs.getString("CONDICION_LLEGADA"),
                rs.getString("ESTADO"),
                rs.wasNull() ? null : pacId,
                rs.wasNull() ? null : confPor,
                tsConf != null ? tsConf.toInstant() : null,
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    public IdentidadProvisionalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public IdentidadProvisional guardar(IdentidadProvisional identidad) {
        String sql = """
                INSERT INTO IDENTIDAD_PROVISIONAL (
                    PUBLIC_ID, EPISODIO_ID, CODIGO_PROVISIONAL, DESCRIPCION_FISICA,
                    EDAD_APARENTE, GENERO_APARENTE, CONDICION_LLEGADA, ESTADO
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, identidad.publicId());
            ps.setLong(2, identidad.episodioId());
            ps.setString(3, identidad.codigoProvisional());
            ps.setString(4, identidad.descripcionFisica());
            if (identidad.edadAparente() != null) {
                ps.setInt(5, identidad.edadAparente());
            } else {
                ps.setNull(5, java.sql.Types.NUMERIC);
            }
            ps.setString(6, identidad.generoAparente());
            ps.setString(7, identidad.condicionLlegada());
            ps.setString(8, identidad.estado());
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        Long generatedId = id != null ? id.longValue() : null;

        return new IdentidadProvisional(
                generatedId,
                identidad.publicId(),
                identidad.episodioId(),
                identidad.codigoProvisional(),
                identidad.descripcionFisica(),
                identidad.edadAparente(),
                identidad.generoAparente(),
                identidad.condicionLlegada(),
                identidad.estado(),
                null,
                null,
                null,
                Instant.now()
        );
    }

    public Optional<IdentidadProvisional> buscarPorEpisodioId(Long episodioId) {
        String sql = "SELECT * FROM IDENTIDAD_PROVISIONAL WHERE EPISODIO_ID = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, episodioId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<IdentidadProvisional> buscarPorCodigo(String codigoProvisional) {
        String sql = "SELECT * FROM IDENTIDAD_PROVISIONAL WHERE CODIGO_PROVISIONAL = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, codigoProvisional));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int reconciliarIdentidad(Long id, Long pacienteId, Long confirmadoPorUsuarioId, Instant fechaConfirmacion) {
        String sql = """
                UPDATE IDENTIDAD_PROVISIONAL
                SET ESTADO = 'VINCULADA',
                    VINCULADO_PACIENTE_ID = ?,
                    CONFIRMADO_POR_USUARIO_ID = ?,
                    FECHA_CONFIRMACION = ?
                WHERE ID = ? AND ESTADO = 'PROVISIONAL'
                """;
        return jdbcTemplate.update(
                sql,
                pacienteId,
                confirmadoPorUsuarioId,
                Timestamp.from(fechaConfirmacion != null ? fechaConfirmacion : Instant.now()),
                id
        );
    }
}
