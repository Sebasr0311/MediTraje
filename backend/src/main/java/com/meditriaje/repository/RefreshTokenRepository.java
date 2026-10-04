package com.meditriaje.repository;

import com.meditriaje.model.RefreshToken;
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
 * Repositorio para la gestión y persistencia de refresh tokens rotativos (ADR-002).
 */
@Repository
public class RefreshTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<RefreshToken> refreshTokenRowMapper = (rs, rowNum) -> {
        Timestamp tsExp = rs.getTimestamp("EXPIRACION");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new RefreshToken(
                rs.getLong("ID"),
                rs.getLong("USUARIO_ID"),
                rs.getString("TOKEN_HASH"),
                tsExp != null ? tsExp.toInstant() : null,
                rs.getInt("REVOCADO") == 1,
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    public RefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public Long crear(Long usuarioId, String tokenHash, Instant expiracion) {
        final String sql = """
            INSERT INTO REFRESH_TOKEN (USUARIO_ID, TOKEN_HASH, EXPIRACION, REVOCADO)
            VALUES (?, ?, ?, 0)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setLong(1, usuarioId);
            ps.setString(2, tokenHash);
            ps.setTimestamp(3, Timestamp.from(expiracion));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el refresh token.");
        }
        return key.longValue();
    }

    public Optional<RefreshToken> buscarPorTokenHash(String tokenHash) {
        final String sql = """
            SELECT ID, USUARIO_ID, TOKEN_HASH, EXPIRACION, REVOCADO, CREATED_AT
            FROM REFRESH_TOKEN
            WHERE TOKEN_HASH = ?
            """;
        List<RefreshToken> resultados = jdbcTemplate.query(sql, refreshTokenRowMapper, tokenHash);
        return resultados.stream().findFirst();
    }

    public void revocar(Long tokenId) {
        final String sql = "UPDATE REFRESH_TOKEN SET REVOCADO = 1 WHERE ID = ?";
        jdbcTemplate.update(sql, tokenId);
    }

    public void revocarPorTokenHash(String tokenHash) {
        final String sql = "UPDATE REFRESH_TOKEN SET REVOCADO = 1 WHERE TOKEN_HASH = ?";
        jdbcTemplate.update(sql, tokenHash);
    }

    public void revocarTodosPorUsuario(Long usuarioId) {
        final String sql = "UPDATE REFRESH_TOKEN SET REVOCADO = 1 WHERE USUARIO_ID = ? AND REVOCADO = 0";
        jdbcTemplate.update(sql, usuarioId);
    }
}
