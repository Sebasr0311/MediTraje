package com.meditriaje.repository;

import com.meditriaje.model.Usuario;
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
 * Repositorio para la entidad {@code USUARIO} y sus asignaciones de roles.
 */
@Repository
public class UsuarioRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Usuario> usuarioRowMapper = (rs, rowNum) -> {
        Timestamp tsBloqueado = rs.getTimestamp("BLOQUEADO_HASTA");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new Usuario(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getString("EMAIL"),
                rs.getString("PASSWORD_HASH"),
                rs.getString("ESTADO"),
                rs.getInt("INTENTOS_FALLIDOS"),
                tsBloqueado != null ? tsBloqueado.toInstant() : null,
                tsCreated != null ? tsCreated.toInstant() : null,
                rs.getInt("DEBE_CAMBIAR_PASSWORD") == 1
        );
    };

    public UsuarioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public boolean existePorEmail(String email) {
        final String sql = "SELECT COUNT(*) FROM USUARIO WHERE EMAIL = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    public Long crear(String publicId, String email, String passwordHash) {
        return crear(publicId, email, passwordHash, false);
    }

    public Long crear(String publicId, String email, String passwordHash, boolean debeCambiarPassword) {
        final String sql = """
            INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, DEBE_CAMBIAR_PASSWORD)
            VALUES (?, ?, ?, 'ACTIVO', 0, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setString(1, publicId);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.setInt(4, debeCambiarPassword ? 1 : 0);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el usuario.");
        }
        return key.longValue();
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        final String sql = """
            SELECT ID, PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, BLOQUEADO_HASTA, CREATED_AT, DEBE_CAMBIAR_PASSWORD
            FROM USUARIO
            WHERE EMAIL = ?
            """;
        List<Usuario> resultados = jdbcTemplate.query(sql, usuarioRowMapper, email);
        return resultados.stream().findFirst();
    }

    public Optional<Usuario> buscarPorId(Long id) {
        final String sql = """
            SELECT ID, PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, BLOQUEADO_HASTA, CREATED_AT, DEBE_CAMBIAR_PASSWORD
            FROM USUARIO
            WHERE ID = ?
            """;
        List<Usuario> resultados = jdbcTemplate.query(sql, usuarioRowMapper, id);
        return resultados.stream().findFirst();
    }

    public Optional<Usuario> buscarPorPublicId(String publicId) {
        final String sql = """
            SELECT ID, PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, BLOQUEADO_HASTA, CREATED_AT, DEBE_CAMBIAR_PASSWORD
            FROM USUARIO
            WHERE PUBLIC_ID = ?
            """;
        List<Usuario> resultados = jdbcTemplate.query(sql, usuarioRowMapper, publicId);
        return resultados.stream().findFirst();
    }

    public List<String> obtenerRoles(Long usuarioId) {
        final String sql = """
            SELECT r.NOMBRE
            FROM ROL r
            JOIN USUARIO_ROL ur ON ur.ROL_ID = r.ID
            WHERE ur.USUARIO_ID = ?
            ORDER BY r.NOMBRE
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("NOMBRE"), usuarioId);
    }

    public void actualizarIntentosFallidos(Long usuarioId, int intentos, Instant bloqueadoHasta, String estado) {
        final String sql = """
            UPDATE USUARIO
            SET INTENTOS_FALLIDOS = ?, BLOQUEADO_HASTA = ?, ESTADO = ?, UPDATED_AT = CURRENT_TIMESTAMP
            WHERE ID = ?
            """;
        Timestamp tsBloqueado = bloqueadoHasta != null ? Timestamp.from(bloqueadoHasta) : null;
        jdbcTemplate.update(sql, intentos, tsBloqueado, estado, usuarioId);
    }

    public void restablecerIntentos(Long usuarioId) {
        final String sql = """
            UPDATE USUARIO
            SET INTENTOS_FALLIDOS = 0, BLOQUEADO_HASTA = NULL, ESTADO = 'ACTIVO', UPDATED_AT = CURRENT_TIMESTAMP
            WHERE ID = ?
            """;
        jdbcTemplate.update(sql, usuarioId);
    }

    public Optional<Long> buscarRolIdPorNombre(String nombre) {
        final String sql = "SELECT ID FROM ROL WHERE NOMBRE = ?";
        try {
            Long id = jdbcTemplate.queryForObject(sql, Long.class, nombre);
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void asignarRol(Long usuarioId, Long rolId) {
        final String sql = "INSERT INTO USUARIO_ROL (USUARIO_ID, ROL_ID) VALUES (?, ?)";
        jdbcTemplate.update(sql, usuarioId, rolId);
    }

    public void actualizarPassword(Long usuarioId, String passwordHash, boolean debeCambiarPassword) {
        final String sql = """
            UPDATE USUARIO
            SET PASSWORD_HASH = ?, DEBE_CAMBIAR_PASSWORD = ?, INTENTOS_FALLIDOS = 0, BLOQUEADO_HASTA = NULL, UPDATED_AT = CURRENT_TIMESTAMP
            WHERE ID = ?
            """;
        jdbcTemplate.update(sql, passwordHash, debeCambiarPassword ? 1 : 0, usuarioId);
    }

    public void actualizarEstado(Long usuarioId, String estado) {
        final String sql = """
            UPDATE USUARIO
            SET ESTADO = ?, UPDATED_AT = CURRENT_TIMESTAMP
            WHERE ID = ?
            """;
        jdbcTemplate.update(sql, estado, usuarioId);
    }
}
