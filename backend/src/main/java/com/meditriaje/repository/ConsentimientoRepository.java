package com.meditriaje.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Objects;

/**
 * Repositorio para la entidad {@code CONSENTIMIENTO}.
 */
@Repository
public class ConsentimientoRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConsentimientoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public Long registrar(Long usuarioId, String versionTexto, boolean aceptado, String ipOrigen) {
        final String sql = """
            INSERT INTO CONSENTIMIENTO (USUARIO_ID, VERSION_TEXTO, ACEPTADO, IP_ORIGEN, REVOCADO)
            VALUES (?, ?, ?, ?, 0)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setLong(1, usuarioId);
            ps.setString(2, versionTexto);
            ps.setInt(3, aceptado ? 1 : 0);
            ps.setString(4, ipOrigen);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el consentimiento.");
        }
        return key.longValue();
    }
}
