package com.meditriaje.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio para la entidad {@code USUARIO} y sus asignaciones de roles.
 */
@Repository
public class UsuarioRepository {

    private final JdbcTemplate jdbcTemplate;

    public UsuarioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public boolean existePorEmail(String email) {
        final String sql = "SELECT COUNT(*) FROM USUARIO WHERE EMAIL = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    public Long crear(String publicId, String email, String passwordHash) {
        final String sql = """
            INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS)
            VALUES (?, ?, ?, 'ACTIVO', 0)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setString(1, publicId);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el usuario.");
        }
        return key.longValue();
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
}
