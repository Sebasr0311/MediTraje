package com.meditriaje.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Repositorio para la entidad {@code PACIENTE}.
 */
@Repository
public class PacienteRepository {

    private final JdbcTemplate jdbcTemplate;

    public PacienteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public boolean existePorDocumento(String tipoDocumento, String numeroDocumento) {
        final String sql = "SELECT COUNT(*) FROM PACIENTE WHERE TIPO_DOCUMENTO = ? AND NUMERO_DOCUMENTO = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tipoDocumento, numeroDocumento);
        return count != null && count > 0;
    }

    public Long crear(
            Long usuarioId,
            String publicId,
            String tipoDocumento,
            String numeroDocumento,
            String nombres,
            String apellidos,
            LocalDate fechaNacimiento,
            String telefono
    ) {
        final String sql = """
            INSERT INTO PACIENTE (USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, FECHA_NACIMIENTO, TELEFONO)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setLong(1, usuarioId);
            ps.setString(2, publicId);
            ps.setString(3, tipoDocumento);
            ps.setString(4, numeroDocumento);
            ps.setString(5, nombres);
            ps.setString(6, apellidos);
            ps.setDate(7, Date.valueOf(fechaNacimiento));
            ps.setString(8, telefono);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el paciente.");
        }
        return key.longValue();
    }
}
