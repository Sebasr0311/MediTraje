package com.meditriaje.repository;

import com.meditriaje.model.CodigoVerificacion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la entidad {@code CODIGO_VERIFICACION} (F2.1, ADR-014).
 * Almacena y valida códigos de recuperación y verificación con SQL parametrizado.
 */
@Repository
public class CodigoVerificacionRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<CodigoVerificacion> codigoRowMapper = (rs, rowNum) -> {
        Timestamp tsExp = rs.getTimestamp("FECHA_EXPIRACION");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new CodigoVerificacion(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("USUARIO_ID"),
                rs.getString("TIPO"),
                rs.getString("CODIGO_HASH"),
                tsExp != null ? tsExp.toInstant() : null,
                rs.getInt("INTENTOS_FALLIDOS"),
                rs.getInt("MAX_INTENTOS"),
                rs.getInt("USADO") == 1,
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    public CodigoVerificacionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public Long crear(CodigoVerificacion codigo) {
        final String sql = """
            INSERT INTO CODIGO_VERIFICACION (
                PUBLIC_ID, USUARIO_ID, TIPO, CODIGO_HASH, FECHA_EXPIRACION, INTENTOS_FALLIDOS, MAX_INTENTOS, USADO
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setString(1, codigo.publicId());
            ps.setLong(2, codigo.usuarioId());
            ps.setString(3, codigo.tipo());
            ps.setString(4, codigo.codigoHash());
            ps.setTimestamp(5, Timestamp.from(codigo.fechaExpiracion()));
            ps.setInt(6, codigo.intentosFallidos());
            ps.setInt(7, codigo.maxIntentos());
            ps.setInt(8, codigo.usado() ? 1 : 0);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el codigo de verificacion.");
        }
        return key.longValue();
    }

    public Optional<CodigoVerificacion> buscarPorId(Long id) {
        final String sql = """
            SELECT ID, PUBLIC_ID, USUARIO_ID, TIPO, CODIGO_HASH, FECHA_EXPIRACION, INTENTOS_FALLIDOS, MAX_INTENTOS, USADO, CREATED_AT
            FROM CODIGO_VERIFICACION
            WHERE ID = ?
            """;
        List<CodigoVerificacion> resultados = jdbcTemplate.query(sql, codigoRowMapper, id);
        return resultados.stream().findFirst();
    }

    public Optional<CodigoVerificacion> buscarUltimoPendientePorUsuarioYTipo(Long usuarioId, String tipo) {
        final String sql = """
            SELECT ID, PUBLIC_ID, USUARIO_ID, TIPO, CODIGO_HASH, FECHA_EXPIRACION, INTENTOS_FALLIDOS, MAX_INTENTOS, USADO, CREATED_AT
            FROM CODIGO_VERIFICACION
            WHERE USUARIO_ID = ? AND TIPO = ? AND USADO = 0
            ORDER BY CREATED_AT DESC, ID DESC
            FETCH FIRST 1 ROWS ONLY
            """;
        List<CodigoVerificacion> resultados = jdbcTemplate.query(sql, codigoRowMapper, usuarioId, tipo);
        return resultados.stream().findFirst();
    }

    public void incrementarIntentos(Long id) {
        final String sql = """
            UPDATE CODIGO_VERIFICACION
            SET INTENTOS_FALLIDOS = INTENTOS_FALLIDOS + 1
            WHERE ID = ?
            """;
        jdbcTemplate.update(sql, id);
    }

    public void marcarComoUsado(Long id) {
        final String sql = """
            UPDATE CODIGO_VERIFICACION
            SET USADO = 1
            WHERE ID = ?
            """;
        jdbcTemplate.update(sql, id);
    }

    public void invalidarCodigosPrevios(Long usuarioId, String tipo) {
        final String sql = """
            UPDATE CODIGO_VERIFICACION
            SET USADO = 1
            WHERE USUARIO_ID = ? AND TIPO = ? AND USADO = 0
            """;
        jdbcTemplate.update(sql, usuarioId, tipo);
    }
}
