package com.meditriaje.repository;

import com.meditriaje.dto.clinical.AccesoBreakGlassResponse;
import com.meditriaje.model.AccesoBreakGlass;
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
 * Repositorio JDBC para registros de acceso de emergencia Break-Glass (ADR-017).
 * SQL 100% parametrizado e inserciones inmutables.
 */
@Repository
public class BreakGlassRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AccesoBreakGlass> entityRowMapper = (rs, rowNum) -> {
        Timestamp expTs = rs.getTimestamp("FECHA_EXPIRACION");
        Timestamp crTs = rs.getTimestamp("CREATED_AT");
        return new AccesoBreakGlass(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PROFESIONAL_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getString("MOTIVO"),
                expTs != null ? expTs.toInstant() : null,
                crTs != null ? crTs.toInstant() : null
        );
    };

    public BreakGlassRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    /**
     * Registra un nuevo evento de acceso excepcional Break-Glass.
     *
     * @param acceso Entidad inmutable con los datos del acceso
     * @return Identificador numérico generado en base de datos
     */
    public Long registrarAcceso(AccesoBreakGlass acceso) {
        Objects.requireNonNull(acceso, "acceso no puede ser nulo");
        String sql = """
                INSERT INTO ACCESO_BREAK_GLASS (PUBLIC_ID, PROFESIONAL_ID, PACIENTE_ID, MOTIVO, FECHA_EXPIRACION, CREATED_AT)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, acceso.publicId());
            ps.setLong(2, acceso.profesionalId());
            ps.setLong(3, acceso.pacienteId());
            ps.setString(4, acceso.motivo());
            ps.setTimestamp(5, Timestamp.from(acceso.fechaExpiracion()));
            ps.setTimestamp(6, Timestamp.from(acceso.createdAt() != null ? acceso.createdAt() : Instant.now()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID generado para ACCESO_BREAK_GLASS");
        }
        return key.longValue();
    }

    /**
     * Verifica si existe una autorización Break-Glass activa y no expirada entre el profesional y el paciente.
     */
    public boolean existeAccesoActivo(Long profesionalId, Long pacienteId, Instant ahora) {
        if (profesionalId == null || pacienteId == null || ahora == null) {
            return false;
        }
        String sql = """
                SELECT COUNT(*) FROM ACCESO_BREAK_GLASS
                WHERE PROFESIONAL_ID = ? AND PACIENTE_ID = ? AND FECHA_EXPIRACION > ?
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, profesionalId, pacienteId, Timestamp.from(ahora));
        return count != null && count > 0;
    }

    /**
     * Recupera la entidad por su identificador público UUID.
     */
    public Optional<AccesoBreakGlass> buscarEntidadPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT ID, PUBLIC_ID, PROFESIONAL_ID, PACIENTE_ID, MOTIVO, FECHA_EXPIRACION, CREATED_AT FROM ACCESO_BREAK_GLASS WHERE PUBLIC_ID = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, entityRowMapper, publicId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Recupera el DTO de respuesta con nombres de profesional y paciente.
     */
    public Optional<AccesoBreakGlassResponse> buscarPorPublicId(String publicId, Instant ahora) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT bg.PUBLIC_ID,
                       prof.PUBLIC_ID AS PROF_PUB_ID,
                       (prof.NOMBRES || ' ' || prof.APELLIDOS) AS PROF_NOMBRE,
                       pac.PUBLIC_ID AS PAC_PUB_ID,
                       (pac.NOMBRES || ' ' || pac.APELLIDOS) AS PAC_NOMBRE,
                       bg.MOTIVO,
                       bg.FECHA_EXPIRACION,
                       bg.CREATED_AT
                FROM ACCESO_BREAK_GLASS bg
                JOIN PROFESIONAL prof ON bg.PROFESIONAL_ID = prof.ID
                JOIN PACIENTE pac ON bg.PACIENTE_ID = pac.ID
                WHERE bg.PUBLIC_ID = ?
                """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Timestamp expTs = rs.getTimestamp("FECHA_EXPIRACION");
                Timestamp crTs = rs.getTimestamp("CREATED_AT");
                Instant expInstant = expTs != null ? expTs.toInstant() : null;
                boolean activo = expInstant != null && ahora != null && ahora.isBefore(expInstant);
                return new AccesoBreakGlassResponse(
                        rs.getString("PUBLIC_ID"),
                        rs.getString("PROF_PUB_ID"),
                        rs.getString("PROF_NOMBRE") != null ? rs.getString("PROF_NOMBRE").trim() : "",
                        rs.getString("PAC_PUB_ID"),
                        rs.getString("PAC_NOMBRE") != null ? rs.getString("PAC_NOMBRE").trim() : "",
                        rs.getString("MOTIVO"),
                        expInstant,
                        crTs != null ? crTs.toInstant() : null,
                        activo
                );
            }, publicId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Lista las autorizaciones Break-Glass actualmente activas (no expiradas) para un profesional.
     */
    public List<AccesoBreakGlassResponse> listarActivosPorProfesional(Long profesionalId, Instant ahora) {
        if (profesionalId == null || ahora == null) {
            return List.of();
        }
        String sql = """
                SELECT bg.PUBLIC_ID,
                       prof.PUBLIC_ID AS PROF_PUB_ID,
                       (prof.NOMBRES || ' ' || prof.APELLIDOS) AS PROF_NOMBRE,
                       pac.PUBLIC_ID AS PAC_PUB_ID,
                       (pac.NOMBRES || ' ' || pac.APELLIDOS) AS PAC_NOMBRE,
                       bg.MOTIVO,
                       bg.FECHA_EXPIRACION,
                       bg.CREATED_AT
                FROM ACCESO_BREAK_GLASS bg
                JOIN PROFESIONAL prof ON bg.PROFESIONAL_ID = prof.ID
                JOIN PACIENTE pac ON bg.PACIENTE_ID = pac.ID
                WHERE bg.PROFESIONAL_ID = ? AND bg.FECHA_EXPIRACION > ?
                ORDER BY bg.FECHA_EXPIRACION DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp expTs = rs.getTimestamp("FECHA_EXPIRACION");
            Timestamp crTs = rs.getTimestamp("CREATED_AT");
            Instant expInstant = expTs != null ? expTs.toInstant() : null;
            return new AccesoBreakGlassResponse(
                    rs.getString("PUBLIC_ID"),
                    rs.getString("PROF_PUB_ID"),
                    rs.getString("PROF_NOMBRE") != null ? rs.getString("PROF_NOMBRE").trim() : "",
                    rs.getString("PAC_PUB_ID"),
                    rs.getString("PAC_NOMBRE") != null ? rs.getString("PAC_NOMBRE").trim() : "",
                    rs.getString("MOTIVO"),
                    expInstant,
                    crTs != null ? crTs.toInstant() : null,
                    true
            );
        }, profesionalId, Timestamp.from(ahora));
    }
}
