package com.meditriaje.repository;

import com.meditriaje.model.Especialidad;
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
 * Repositorio JDBC para la entidad {@code ESPECIALIDAD}.
 * Utiliza SQL 100% parametrizado y paginación estándar Oracle (ADR-001, ADR-003, ADR-006, HU-10).
 */
@Repository
public class EspecialidadRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Especialidad> rowMapper = (rs, rowNum) -> new Especialidad(
            rs.getLong("ID"),
            rs.getString("PUBLIC_ID"),
            rs.getString("NOMBRE"),
            rs.getInt("DURACION_SLOT_MIN"),
            rs.getString("ESTADO")
    );

    public EspecialidadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public Especialidad crear(String publicId, String nombre, int duracionSlotMin) {
        final String sql = """
            INSERT INTO ESPECIALIDAD (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO)
            VALUES (?, ?, ?, 'ACTIVO')
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setString(1, publicId);
            ps.setString(2, nombre);
            ps.setInt(3, duracionSlotMin);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para la especialidad.");
        }
        return new Especialidad(key.longValue(), publicId, nombre, duracionSlotMin, "ACTIVO");
    }

    public void actualizar(String publicId, String nombre, int duracionSlotMin) {
        final String sql = "UPDATE ESPECIALIDAD SET NOMBRE = ?, DURACION_SLOT_MIN = ? WHERE PUBLIC_ID = ?";
        jdbcTemplate.update(sql, nombre, duracionSlotMin, publicId);
    }

    public Optional<Especialidad> buscarPorPublicId(String publicId) {
        final String sql = "SELECT ID, PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO FROM ESPECIALIDAD WHERE PUBLIC_ID = ?";
        List<Especialidad> resultados = jdbcTemplate.query(sql, rowMapper, publicId);
        return resultados.stream().findFirst();
    }

    public Optional<Especialidad> buscarPorId(Long id) {
        final String sql = "SELECT ID, PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO FROM ESPECIALIDAD WHERE ID = ?";
        List<Especialidad> resultados = jdbcTemplate.query(sql, rowMapper, id);
        return resultados.stream().findFirst();
    }

    public boolean existePorNombre(String nombre) {
        final String sql = "SELECT COUNT(*) FROM ESPECIALIDAD WHERE UPPER(TRIM(NOMBRE)) = UPPER(TRIM(?))";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, nombre);
        return count != null && count > 0;
    }

    public boolean existePorNombreYNoPublicId(String nombre, String publicId) {
        final String sql = "SELECT COUNT(*) FROM ESPECIALIDAD WHERE UPPER(TRIM(NOMBRE)) = UPPER(TRIM(?)) AND PUBLIC_ID <> ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, nombre, publicId);
        return count != null && count > 0;
    }

    public void cambiarEstado(String publicId, String nuevoEstado) {
        final String sql = "UPDATE ESPECIALIDAD SET ESTADO = ? WHERE PUBLIC_ID = ?";
        jdbcTemplate.update(sql, nuevoEstado, publicId);
    }

    public List<Especialidad> listar(int page, int size, String estadoFiltro) {
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);

        if (estadoFiltro != null && !estadoFiltro.isBlank()) {
            final String sql = """
                SELECT ID, PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO
                FROM ESPECIALIDAD
                WHERE ESTADO = ?
                ORDER BY NOMBRE ASC, ID ASC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
            return jdbcTemplate.query(sql, rowMapper, estadoFiltro.trim().toUpperCase(), offset, limit);
        } else {
            final String sql = """
                SELECT ID, PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO
                FROM ESPECIALIDAD
                ORDER BY NOMBRE ASC, ID ASC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
            return jdbcTemplate.query(sql, rowMapper, offset, limit);
        }
    }

    public long contar(String estadoFiltro) {
        if (estadoFiltro != null && !estadoFiltro.isBlank()) {
            final String sql = "SELECT COUNT(*) FROM ESPECIALIDAD WHERE ESTADO = ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, estadoFiltro.trim().toUpperCase());
            return count != null ? count : 0L;
        } else {
            final String sql = "SELECT COUNT(*) FROM ESPECIALIDAD";
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0L;
        }
    }
}
