package com.meditriaje.repository;

import com.meditriaje.model.Institucion;
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
 * Repositorio JDBC para la entidad {@code INSTITUCION}.
 * Utiliza SQL 100% parametrizado y paginación estándar Oracle (ADR-001, ADR-003, HU-10).
 */
@Repository
public class InstitucionRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Institucion> rowMapper = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREATED_AT");
        return new Institucion(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getString("NIT"),
                rs.getString("RAZON_SOCIAL"),
                rs.getString("ESTADO"),
                ts != null ? ts.toInstant() : null
        );
    };

    public InstitucionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public Institucion crear(String publicId, String nit, String razonSocial) {
        final String sql = """
            INSERT INTO INSTITUCION (PUBLIC_ID, NIT, RAZON_SOCIAL, ESTADO)
            VALUES (?, ?, ?, 'ACTIVO')
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setString(1, publicId);
            ps.setString(2, nit);
            ps.setString(3, razonSocial);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para la institución.");
        }
        return new Institucion(key.longValue(), publicId, nit, razonSocial, "ACTIVO", Instant.now());
    }

    public void actualizar(String publicId, String razonSocial) {
        final String sql = "UPDATE INSTITUCION SET RAZON_SOCIAL = ? WHERE PUBLIC_ID = ?";
        jdbcTemplate.update(sql, razonSocial, publicId);
    }

    public Optional<Institucion> buscarPorPublicId(String publicId) {
        final String sql = "SELECT ID, PUBLIC_ID, NIT, RAZON_SOCIAL, ESTADO, CREATED_AT FROM INSTITUCION WHERE PUBLIC_ID = ?";
        List<Institucion> resultados = jdbcTemplate.query(sql, rowMapper, publicId);
        return resultados.stream().findFirst();
    }

    public Optional<Institucion> buscarPorId(Long id) {
        final String sql = "SELECT ID, PUBLIC_ID, NIT, RAZON_SOCIAL, ESTADO, CREATED_AT FROM INSTITUCION WHERE ID = ?";
        List<Institucion> resultados = jdbcTemplate.query(sql, rowMapper, id);
        return resultados.stream().findFirst();
    }

    public boolean existePorNit(String nit) {
        final String sql = "SELECT COUNT(*) FROM INSTITUCION WHERE UPPER(TRIM(NIT)) = UPPER(TRIM(?))";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, nit);
        return count != null && count > 0;
    }

    public boolean existePorNitYNoPublicId(String nit, String publicId) {
        final String sql = "SELECT COUNT(*) FROM INSTITUCION WHERE UPPER(TRIM(NIT)) = UPPER(TRIM(?)) AND PUBLIC_ID <> ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, nit, publicId);
        return count != null && count > 0;
    }

    public void cambiarEstado(String publicId, String nuevoEstado) {
        final String sql = "UPDATE INSTITUCION SET ESTADO = ? WHERE PUBLIC_ID = ?";
        jdbcTemplate.update(sql, nuevoEstado, publicId);
    }

    public List<Institucion> listar(int page, int size, String estadoFiltro) {
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);

        if (estadoFiltro != null && !estadoFiltro.isBlank()) {
            final String sql = """
                SELECT ID, PUBLIC_ID, NIT, RAZON_SOCIAL, ESTADO, CREATED_AT
                FROM INSTITUCION
                WHERE ESTADO = ?
                ORDER BY RAZON_SOCIAL ASC, ID ASC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
            return jdbcTemplate.query(sql, rowMapper, estadoFiltro.trim().toUpperCase(), offset, limit);
        } else {
            final String sql = """
                SELECT ID, PUBLIC_ID, NIT, RAZON_SOCIAL, ESTADO, CREATED_AT
                FROM INSTITUCION
                ORDER BY RAZON_SOCIAL ASC, ID ASC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
            return jdbcTemplate.query(sql, rowMapper, offset, limit);
        }
    }

    public long contar(String estadoFiltro) {
        if (estadoFiltro != null && !estadoFiltro.isBlank()) {
            final String sql = "SELECT COUNT(*) FROM INSTITUCION WHERE ESTADO = ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, estadoFiltro.trim().toUpperCase());
            return count != null ? count : 0L;
        } else {
            final String sql = "SELECT COUNT(*) FROM INSTITUCION";
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0L;
        }
    }
}
