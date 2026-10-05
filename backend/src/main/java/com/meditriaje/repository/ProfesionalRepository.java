package com.meditriaje.repository;

import com.meditriaje.dto.admin.ProfesionalResponse;
import com.meditriaje.model.Profesional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la entidad {@code PROFESIONAL}.
 * Implementa consultas 100% parametrizadas y paginación estándar Oracle (ADR-001, ADR-003, ADR-006, HU-10, Ley 1164/2007).
 */
@Repository
public class ProfesionalRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Profesional> profesionalRowMapper = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        return new Profesional(
                rs.getLong("ID"),
                rs.getLong("USUARIO_ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("ESPECIALIDAD_ID"),
                rs.getString("TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                rs.getString("REGISTRO_MEDICO"),
                rs.getString("NOMBRES"),
                rs.getString("APELLIDOS"),
                rs.getString("TELEFONO"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null
        );
    };

    private final RowMapper<ProfesionalResponse> profesionalResponseRowMapper = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new ProfesionalResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("USUARIO_PUBLIC_ID"),
                rs.getString("TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                rs.getString("REGISTRO_MEDICO"),
                rs.getString("NOMBRES"),
                rs.getString("APELLIDOS"),
                rs.getString("EMAIL"),
                rs.getString("TELEFONO"),
                rs.getString("ESPECIALIDAD_PUBLIC_ID"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                rs.getString("ESTADO"),
                rs.getInt("DEBE_CAMBIAR_PASSWORD") == 1,
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    public ProfesionalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public Long crear(Profesional profesional) {
        final String sql = """
            INSERT INTO PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, REGISTRO_MEDICO, NOMBRES, APELLIDOS, TELEFONO)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setLong(1, profesional.usuarioId());
            ps.setString(2, profesional.publicId());
            ps.setLong(3, profesional.especialidadId());
            ps.setString(4, profesional.tipoDocumento() != null ? profesional.tipoDocumento() : "CC");
            ps.setString(5, profesional.numeroDocumento());
            ps.setString(6, profesional.registroMedico());
            ps.setString(7, profesional.nombres());
            ps.setString(8, profesional.apellidos());
            ps.setString(9, profesional.telefono());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el profesional.");
        }
        return key.longValue();
    }

    public void actualizar(Profesional profesional) {
        final String sql = """
            UPDATE PROFESIONAL
            SET ESPECIALIDAD_ID = ?, NOMBRES = ?, APELLIDOS = ?, TELEFONO = ?, UPDATED_AT = CURRENT_TIMESTAMP
            WHERE PUBLIC_ID = ?
            """;
        jdbcTemplate.update(
                sql,
                profesional.especialidadId(),
                profesional.nombres(),
                profesional.apellidos(),
                profesional.telefono(),
                profesional.publicId()
        );
    }

    public Optional<Profesional> buscarPorPublicId(String publicId) {
        final String sql = """
            SELECT ID, USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, REGISTRO_MEDICO, NOMBRES, APELLIDOS, TELEFONO, CREATED_AT, UPDATED_AT
            FROM PROFESIONAL
            WHERE PUBLIC_ID = ?
            """;
        List<Profesional> resultados = jdbcTemplate.query(sql, profesionalRowMapper, publicId);
        return resultados.stream().findFirst();
    }

    public Optional<Profesional> buscarPorId(Long id) {
        final String sql = """
            SELECT ID, USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, REGISTRO_MEDICO, NOMBRES, APELLIDOS, TELEFONO, CREATED_AT, UPDATED_AT
            FROM PROFESIONAL
            WHERE ID = ?
            """;
        List<Profesional> resultados = jdbcTemplate.query(sql, profesionalRowMapper, id);
        return resultados.stream().findFirst();
    }

    public Optional<Profesional> buscarPorUsuarioId(Long usuarioId) {
        final String sql = """
            SELECT ID, USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, REGISTRO_MEDICO, NOMBRES, APELLIDOS, TELEFONO, CREATED_AT, UPDATED_AT
            FROM PROFESIONAL
            WHERE USUARIO_ID = ?
            """;
        List<Profesional> resultados = jdbcTemplate.query(sql, profesionalRowMapper, usuarioId);
        return resultados.stream().findFirst();
    }

    public boolean existePorRegistroMedico(String registroMedico) {
        final String sql = "SELECT COUNT(*) FROM PROFESIONAL WHERE UPPER(TRIM(REGISTRO_MEDICO)) = UPPER(TRIM(?))";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, registroMedico);
        return count != null && count > 0;
    }

    public boolean existePorRegistroMedicoYNoPublicId(String registroMedico, String publicId) {
        final String sql = """
            SELECT COUNT(*)
            FROM PROFESIONAL
            WHERE UPPER(TRIM(REGISTRO_MEDICO)) = UPPER(TRIM(?))
              AND PUBLIC_ID <> ?
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, registroMedico, publicId);
        return count != null && count > 0;
    }

    public boolean existePorDocumento(String tipoDocumento, String numeroDocumento) {
        if (tipoDocumento == null || numeroDocumento == null) return false;
        final String sql = "SELECT COUNT(*) FROM PROFESIONAL WHERE UPPER(TRIM(TIPO_DOCUMENTO)) = UPPER(TRIM(?)) AND UPPER(TRIM(NUMERO_DOCUMENTO)) = UPPER(TRIM(?))";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tipoDocumento, numeroDocumento);
        return count != null && count > 0;
    }

    public boolean existePorDocumentoYNoPublicId(String tipoDocumento, String numeroDocumento, String publicId) {
        if (tipoDocumento == null || numeroDocumento == null) return false;
        final String sql = """
            SELECT COUNT(*)
            FROM PROFESIONAL
            WHERE UPPER(TRIM(TIPO_DOCUMENTO)) = UPPER(TRIM(?))
              AND UPPER(TRIM(NUMERO_DOCUMENTO)) = UPPER(TRIM(?))
              AND PUBLIC_ID <> ?
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tipoDocumento, numeroDocumento, publicId);
        return count != null && count > 0;
    }

    public List<ProfesionalResponse> listar(int page, int size, String especialidadPublicId, String estado) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.PUBLIC_ID, u.PUBLIC_ID AS USUARIO_PUBLIC_ID, p.TIPO_DOCUMENTO, p.NUMERO_DOCUMENTO, p.REGISTRO_MEDICO,
                   p.NOMBRES, p.APELLIDOS, u.EMAIL, p.TELEFONO, e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE, u.ESTADO, u.DEBE_CAMBIAR_PASSWORD, p.CREATED_AT
            FROM PROFESIONAL p
            JOIN USUARIO u ON p.USUARIO_ID = u.ID
            JOIN ESPECIALIDAD e ON p.ESPECIALIDAD_ID = e.ID
            WHERE 1 = 1
            """);

        List<Object> params = new ArrayList<>();

        if (especialidadPublicId != null && !especialidadPublicId.isBlank()) {
            sql.append(" AND e.PUBLIC_ID = ?");
            params.add(especialidadPublicId.trim());
        }

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND u.ESTADO = ?");
            params.add(estado.trim().toUpperCase());
        }

        sql.append(" ORDER BY p.APELLIDOS ASC, p.NOMBRES ASC, p.ID ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);
        params.add(offset);
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), profesionalResponseRowMapper, params.toArray());
    }

    public int contar(String especialidadPublicId, String estado) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM PROFESIONAL p
            JOIN USUARIO u ON p.USUARIO_ID = u.ID
            JOIN ESPECIALIDAD e ON p.ESPECIALIDAD_ID = e.ID
            WHERE 1 = 1
            """);

        List<Object> params = new ArrayList<>();

        if (especialidadPublicId != null && !especialidadPublicId.isBlank()) {
            sql.append(" AND e.PUBLIC_ID = ?");
            params.add(especialidadPublicId.trim());
        }

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND u.ESTADO = ?");
            params.add(estado.trim().toUpperCase());
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }
}
