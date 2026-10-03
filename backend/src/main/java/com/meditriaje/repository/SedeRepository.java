package com.meditriaje.repository;

import com.meditriaje.model.Sede;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la entidad {@code SEDE}.
 * Utiliza SQL 100% parametrizado y paginación estándar Oracle (ADR-001, ADR-003, HU-10).
 */
@Repository
public class SedeRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Sede> rowMapper = (rs, rowNum) -> new Sede(
            rs.getLong("ID"),
            rs.getLong("INSTITUCION_ID"),
            rs.getString("PUBLIC_ID"),
            rs.getString("NOMBRE"),
            rs.getString("DIRECCION"),
            rs.getString("CIUDAD"),
            rs.getString("ESTADO")
    );

    public SedeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public Sede crear(Long institucionId, String publicId, String nombre, String direccion, String ciudad) {
        final String sql = """
            INSERT INTO SEDE (INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD, ESTADO)
            VALUES (?, ?, ?, ?, ?, 'ACTIVO')
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setLong(1, institucionId);
            ps.setString(2, publicId);
            ps.setString(3, nombre);
            ps.setString(4, direccion);
            ps.setString(5, ciudad);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para la sede.");
        }
        return new Sede(key.longValue(), institucionId, publicId, nombre, direccion, ciudad, "ACTIVO");
    }

    public void actualizar(String publicId, String nombre, String direccion, String ciudad) {
        final String sql = "UPDATE SEDE SET NOMBRE = ?, DIRECCION = ?, CIUDAD = ? WHERE PUBLIC_ID = ?";
        jdbcTemplate.update(sql, nombre, direccion, ciudad, publicId);
    }

    public Optional<Sede> buscarPorPublicId(String publicId) {
        final String sql = "SELECT ID, INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD, ESTADO FROM SEDE WHERE PUBLIC_ID = ?";
        List<Sede> resultados = jdbcTemplate.query(sql, rowMapper, publicId);
        return resultados.stream().findFirst();
    }

    public Optional<Sede> buscarPorId(Long id) {
        final String sql = "SELECT ID, INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD, ESTADO FROM SEDE WHERE ID = ?";
        List<Sede> resultados = jdbcTemplate.query(sql, rowMapper, id);
        return resultados.stream().findFirst();
    }

    public void cambiarEstado(String publicId, String nuevoEstado) {
        final String sql = "UPDATE SEDE SET ESTADO = ? WHERE PUBLIC_ID = ?";
        jdbcTemplate.update(sql, nuevoEstado, publicId);
    }

    public List<Sede> listar(int page, int size, String institucionPublicId, String estadoFiltro) {
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);

        boolean hasInstitucion = institucionPublicId != null && !institucionPublicId.isBlank();
        boolean hasEstado = estadoFiltro != null && !estadoFiltro.isBlank();

        if (hasInstitucion && hasEstado) {
            final String sql = """
                SELECT s.ID, s.INSTITUCION_ID, s.PUBLIC_ID, s.NOMBRE, s.DIRECCION, s.CIUDAD, s.ESTADO
                FROM SEDE s
                JOIN INSTITUCION i ON s.INSTITUCION_ID = i.ID
                WHERE i.PUBLIC_ID = ? AND s.ESTADO = ?
                ORDER BY s.NOMBRE ASC, s.ID ASC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
            return jdbcTemplate.query(sql, rowMapper, institucionPublicId.trim(), estadoFiltro.trim().toUpperCase(), offset, limit);
        } else if (hasInstitucion) {
            final String sql = """
                SELECT s.ID, s.INSTITUCION_ID, s.PUBLIC_ID, s.NOMBRE, s.DIRECCION, s.CIUDAD, s.ESTADO
                FROM SEDE s
                JOIN INSTITUCION i ON s.INSTITUCION_ID = i.ID
                WHERE i.PUBLIC_ID = ?
                ORDER BY s.NOMBRE ASC, s.ID ASC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
            return jdbcTemplate.query(sql, rowMapper, institucionPublicId.trim(), offset, limit);
        } else if (hasEstado) {
            final String sql = """
                SELECT s.ID, s.INSTITUCION_ID, s.PUBLIC_ID, s.NOMBRE, s.DIRECCION, s.CIUDAD, s.ESTADO
                FROM SEDE s
                WHERE s.ESTADO = ?
                ORDER BY s.NOMBRE ASC, s.ID ASC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
            return jdbcTemplate.query(sql, rowMapper, estadoFiltro.trim().toUpperCase(), offset, limit);
        } else {
            final String sql = """
                SELECT s.ID, s.INSTITUCION_ID, s.PUBLIC_ID, s.NOMBRE, s.DIRECCION, s.CIUDAD, s.ESTADO
                FROM SEDE s
                ORDER BY s.NOMBRE ASC, s.ID ASC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
            return jdbcTemplate.query(sql, rowMapper, offset, limit);
        }
    }

    public long contar(String institucionPublicId, String estadoFiltro) {
        boolean hasInstitucion = institucionPublicId != null && !institucionPublicId.isBlank();
        boolean hasEstado = estadoFiltro != null && !estadoFiltro.isBlank();

        if (hasInstitucion && hasEstado) {
            final String sql = """
                SELECT COUNT(*)
                FROM SEDE s
                JOIN INSTITUCION i ON s.INSTITUCION_ID = i.ID
                WHERE i.PUBLIC_ID = ? AND s.ESTADO = ?
                """;
            Long count = jdbcTemplate.queryForObject(sql, Long.class, institucionPublicId.trim(), estadoFiltro.trim().toUpperCase());
            return count != null ? count : 0L;
        } else if (hasInstitucion) {
            final String sql = """
                SELECT COUNT(*)
                FROM SEDE s
                JOIN INSTITUCION i ON s.INSTITUCION_ID = i.ID
                WHERE i.PUBLIC_ID = ?
                """;
            Long count = jdbcTemplate.queryForObject(sql, Long.class, institucionPublicId.trim());
            return count != null ? count : 0L;
        } else if (hasEstado) {
            final String sql = "SELECT COUNT(*) FROM SEDE WHERE ESTADO = ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, estadoFiltro.trim().toUpperCase());
            return count != null ? count : 0L;
        } else {
            final String sql = "SELECT COUNT(*) FROM SEDE";
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0L;
        }
    }
}
