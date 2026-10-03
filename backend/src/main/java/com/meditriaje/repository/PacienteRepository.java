package com.meditriaje.repository;

import com.meditriaje.dto.PacientePerfilResponse;
import com.meditriaje.model.Paciente;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    public Optional<PacientePerfilResponse> buscarPerfilPorUsuarioPublicId(String usuarioPublicId) {
        final String sql = """
            SELECT p.PUBLIC_ID, p.TIPO_DOCUMENTO, p.NUMERO_DOCUMENTO, p.NOMBRES, p.APELLIDOS,
                   p.FECHA_NACIMIENTO, p.TELEFONO, u.EMAIL
            FROM PACIENTE p
            JOIN USUARIO u ON u.ID = p.USUARIO_ID
            WHERE u.PUBLIC_ID = ?
            """;
        List<PacientePerfilResponse> resultados = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Date fNac = rs.getDate("FECHA_NACIMIENTO");
            return new PacientePerfilResponse(
                    rs.getString("PUBLIC_ID"),
                    rs.getString("TIPO_DOCUMENTO"),
                    rs.getString("NUMERO_DOCUMENTO"),
                    rs.getString("NOMBRES"),
                    rs.getString("APELLIDOS"),
                    fNac != null ? fNac.toLocalDate() : null,
                    rs.getString("TELEFONO"),
                    rs.getString("EMAIL")
            );
        }, usuarioPublicId);
        return resultados.stream().findFirst();
    }

    public Optional<Paciente> buscarPorUsuarioId(Long usuarioId) {
        final String sql = """
            SELECT ID, USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                   NOMBRES, APELLIDOS, FECHA_NACIMIENTO, TELEFONO, CREATED_AT, UPDATED_AT
            FROM PACIENTE
            WHERE USUARIO_ID = ?
            """;
        List<Paciente> resultados = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Date fNac = rs.getDate("FECHA_NACIMIENTO");
            Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
            Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
            return new Paciente(
                    rs.getLong("ID"),
                    rs.getLong("USUARIO_ID"),
                    rs.getString("PUBLIC_ID"),
                    rs.getString("TIPO_DOCUMENTO"),
                    rs.getString("NUMERO_DOCUMENTO"),
                    rs.getString("NOMBRES"),
                    rs.getString("APELLIDOS"),
                    fNac != null ? fNac.toLocalDate() : null,
                    rs.getString("TELEFONO"),
                    tsCreated != null ? tsCreated.toInstant() : null,
                    tsUpdated != null ? tsUpdated.toInstant() : null
            );
        }, usuarioId);
        return resultados.stream().findFirst();
    }

    public Optional<Paciente> buscarPorId(Long id) {
        final String sql = """
            SELECT ID, USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                   NOMBRES, APELLIDOS, FECHA_NACIMIENTO, TELEFONO, CREATED_AT, UPDATED_AT
            FROM PACIENTE
            WHERE ID = ?
            """;
        List<Paciente> resultados = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Date fNac = rs.getDate("FECHA_NACIMIENTO");
            Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
            Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
            return new Paciente(
                    rs.getLong("ID"),
                    rs.getLong("USUARIO_ID"),
                    rs.getString("PUBLIC_ID"),
                    rs.getString("TIPO_DOCUMENTO"),
                    rs.getString("NUMERO_DOCUMENTO"),
                    rs.getString("NOMBRES"),
                    rs.getString("APELLIDOS"),
                    fNac != null ? fNac.toLocalDate() : null,
                    rs.getString("TELEFONO"),
                    tsCreated != null ? tsCreated.toInstant() : null,
                    tsUpdated != null ? tsUpdated.toInstant() : null
            );
        }, id);
        return resultados.stream().findFirst();
    }

    public Optional<Paciente> buscarPorPublicId(String publicId) {
        final String sql = """
            SELECT ID, USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                   NOMBRES, APELLIDOS, FECHA_NACIMIENTO, TELEFONO, CREATED_AT, UPDATED_AT
            FROM PACIENTE
            WHERE PUBLIC_ID = ?
            """;
        List<Paciente> resultados = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Date fNac = rs.getDate("FECHA_NACIMIENTO");
            Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
            Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
            return new Paciente(
                    rs.getLong("ID"),
                    rs.getLong("USUARIO_ID"),
                    rs.getString("PUBLIC_ID"),
                    rs.getString("TIPO_DOCUMENTO"),
                    rs.getString("NUMERO_DOCUMENTO"),
                    rs.getString("NOMBRES"),
                    rs.getString("APELLIDOS"),
                    fNac != null ? fNac.toLocalDate() : null,
                    rs.getString("TELEFONO"),
                    tsCreated != null ? tsCreated.toInstant() : null,
                    tsUpdated != null ? tsUpdated.toInstant() : null
            );
        }, publicId);
        return resultados.stream().findFirst();
    }
}

