package com.meditriaje.repository;

import com.meditriaje.dto.followup.SeguimientoResponse;
import com.meditriaje.model.SeguimientoPostAtencion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la entidad {@code SEGUIMIENTO_POST_ATENCION} (ADR-015, F2.2).
 * Utiliza SQL 100% parametrizado y evita exponer claves autonuméricas en DTOs.
 */
@Repository
public class SeguimientoRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SeguimientoPostAtencion> entidadRowMapper = (rs, rowNum) -> {
        Date dControl = rs.getDate("FECHA_SUGERIDA_CONTROL");
        Timestamp tsResp = rs.getTimestamp("FECHA_RESPUESTA_PACIENTE");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");

        return new SeguimientoPostAtencion(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("ATENCION_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getLong("PROFESIONAL_ID"),
                rs.getString("TIPO"),
                rs.getString("INDICACIONES"),
                dControl != null ? dControl.toLocalDate() : null,
                rs.getString("ESTADO"),
                tsResp != null ? tsResp.toInstant() : null,
                rs.getString("REPORTE_PACIENTE"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null
        );
    };

    private final RowMapper<SeguimientoResponse> responseRowMapper = (rs, rowNum) -> {
        Date dControl = rs.getDate("FECHA_SUGERIDA_CONTROL");
        Timestamp tsResp = rs.getTimestamp("FECHA_RESPUESTA_PACIENTE");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");

        return new SeguimientoResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("ATENCION_PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                rs.getString("TIPO"),
                rs.getString("INDICACIONES"),
                dControl != null ? dControl.toLocalDate() : null,
                rs.getString("ESTADO"),
                tsResp != null ? tsResp.toInstant() : null,
                rs.getString("REPORTE_PACIENTE"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null
        );
    };

    public SeguimientoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    /**
     * Inserta un nuevo seguimiento post-atención en la base de datos.
     */
    public Long guardar(SeguimientoPostAtencion s) {
        Objects.requireNonNull(s, "SeguimientoPostAtencion no puede ser nulo");
        String sql = """
                INSERT INTO SEGUIMIENTO_POST_ATENCION (
                    PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID,
                    TIPO, INDICACIONES, FECHA_SUGERIDA_CONTROL, ESTADO,
                    FECHA_RESPUESTA_PACIENTE, REPORTE_PACIENTE, CREATED_AT, UPDATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, s.publicId());
            ps.setLong(2, s.atencionId());
            ps.setLong(3, s.pacienteId());
            ps.setLong(4, s.profesionalId());
            ps.setString(5, s.tipo());
            ps.setString(6, s.indicaciones());
            if (s.fechaSugeridaControl() != null) {
                ps.setDate(7, Date.valueOf(s.fechaSugeridaControl()));
            } else {
                ps.setNull(7, java.sql.Types.DATE);
            }
            ps.setString(8, s.estado() != null ? s.estado() : "PENDIENTE");
            if (s.fechaRespuestaPaciente() != null) {
                ps.setTimestamp(9, Timestamp.from(s.fechaRespuestaPaciente()));
            } else {
                ps.setNull(9, java.sql.Types.TIMESTAMP);
            }
            ps.setString(10, s.reportePaciente());
            ps.setTimestamp(11, Timestamp.from(s.createdAt() != null ? s.createdAt() : Instant.now()));
            ps.setTimestamp(12, Timestamp.from(s.updatedAt() != null ? s.updatedAt() : Instant.now()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Error al obtener ID autogenerado para el seguimiento.");
        }
        return key.longValue();
    }

    /**
     * Busca la entidad por su identificador público (UUID).
     */
    public Optional<SeguimientoPostAtencion> buscarEntidadPorPublicId(String publicId) {
        String sql = """
                SELECT ID, PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID,
                       TIPO, INDICACIONES, FECHA_SUGERIDA_CONTROL, ESTADO,
                       FECHA_RESPUESTA_PACIENTE, REPORTE_PACIENTE, CREATED_AT, UPDATED_AT
                FROM SEGUIMIENTO_POST_ATENCION
                WHERE PUBLIC_ID = ?
                """;
        List<SeguimientoPostAtencion> lista = jdbcTemplate.query(sql, entidadRowMapper, publicId);
        return lista.stream().findFirst();
    }

    /**
     * Busca la entidad por su ID primario numérico.
     */
    public Optional<SeguimientoPostAtencion> buscarEntidadPorId(Long id) {
        String sql = """
                SELECT ID, PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID,
                       TIPO, INDICACIONES, FECHA_SUGERIDA_CONTROL, ESTADO,
                       FECHA_RESPUESTA_PACIENTE, REPORTE_PACIENTE, CREATED_AT, UPDATED_AT
                FROM SEGUIMIENTO_POST_ATENCION
                WHERE ID = ?
                """;
        List<SeguimientoPostAtencion> lista = jdbcTemplate.query(sql, entidadRowMapper, id);
        return lista.stream().findFirst();
    }

    /**
     * Busca un seguimiento y lo mapea al DTO completo con información relacionada.
     */
    public Optional<SeguimientoResponse> buscarPorPublicId(String publicId) {
        String sql = """
                SELECT s.ID, s.PUBLIC_ID, s.ATENCION_ID, s.PACIENTE_ID, s.PROFESIONAL_ID,
                       s.TIPO, s.INDICACIONES, s.FECHA_SUGERIDA_CONTROL, s.ESTADO,
                       s.FECHA_RESPUESTA_PACIENTE, s.REPORTE_PACIENTE, s.CREATED_AT, s.UPDATED_AT,
                       a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                       p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                       TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                       prof.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                       TRIM(prof.NOMBRES || ' ' || prof.APELLIDOS) AS PROFESIONAL_NOMBRE,
                       esp.NOMBRE AS ESPECIALIDAD_NOMBRE
                FROM SEGUIMIENTO_POST_ATENCION s
                JOIN ATENCION a ON s.ATENCION_ID = a.ID
                JOIN PACIENTE p ON s.PACIENTE_ID = p.ID
                JOIN PROFESIONAL prof ON s.PROFESIONAL_ID = prof.ID
                JOIN ESPECIALIDAD esp ON prof.ESPECIALIDAD_ID = esp.ID
                WHERE s.PUBLIC_ID = ?
                """;
        List<SeguimientoResponse> lista = jdbcTemplate.query(sql, responseRowMapper, publicId);
        return lista.stream().findFirst();
    }

    /**
     * Registra el reporte de evolución aportado por el paciente y marca el seguimiento como COMPLETADO.
     */
    public void registrarReportePaciente(Long id, String reportePaciente, Instant fechaRespuesta) {
        String sql = """
                UPDATE SEGUIMIENTO_POST_ATENCION
                SET REPORTE_PACIENTE = ?,
                    FECHA_RESPUESTA_PACIENTE = ?,
                    ESTADO = 'COMPLETADO',
                    UPDATED_AT = CURRENT_TIMESTAMP
                WHERE ID = ?
                """;
        jdbcTemplate.update(sql, reportePaciente, Timestamp.from(fechaRespuesta), id);
    }

    /**
     * Actualiza el estado de un seguimiento (p. ej. CANCELADO).
     */
    public void actualizarEstado(Long id, String nuevoEstado) {
        String sql = """
                UPDATE SEGUIMIENTO_POST_ATENCION
                SET ESTADO = ?,
                    UPDATED_AT = CURRENT_TIMESTAMP
                WHERE ID = ?
                """;
        jdbcTemplate.update(sql, nuevoEstado, id);
    }

    /**
     * Lista los seguimientos asociados a una atención médica específica.
     */
    public List<SeguimientoResponse> listarPorAtencionId(Long atencionId) {
        String sql = """
                SELECT s.ID, s.PUBLIC_ID, s.ATENCION_ID, s.PACIENTE_ID, s.PROFESIONAL_ID,
                       s.TIPO, s.INDICACIONES, s.FECHA_SUGERIDA_CONTROL, s.ESTADO,
                       s.FECHA_RESPUESTA_PACIENTE, s.REPORTE_PACIENTE, s.CREATED_AT, s.UPDATED_AT,
                       a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                       p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                       TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                       prof.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                       TRIM(prof.NOMBRES || ' ' || prof.APELLIDOS) AS PROFESIONAL_NOMBRE,
                       esp.NOMBRE AS ESPECIALIDAD_NOMBRE
                FROM SEGUIMIENTO_POST_ATENCION s
                JOIN ATENCION a ON s.ATENCION_ID = a.ID
                JOIN PACIENTE p ON s.PACIENTE_ID = p.ID
                JOIN PROFESIONAL prof ON s.PROFESIONAL_ID = prof.ID
                JOIN ESPECIALIDAD esp ON prof.ESPECIALIDAD_ID = esp.ID
                WHERE s.ATENCION_ID = ?
                ORDER BY s.CREATED_AT ASC, s.ID ASC
                """;
        return jdbcTemplate.query(sql, responseRowMapper, atencionId);
    }

    /**
     * Lista paginada de seguimientos pertenecientes a un paciente con filtro opcional de estado.
     */
    public List<SeguimientoResponse> listarPorPacienteId(Long pacienteId, String estadoFiltro, int page, int size) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.ID, s.PUBLIC_ID, s.ATENCION_ID, s.PACIENTE_ID, s.PROFESIONAL_ID,
                       s.TIPO, s.INDICACIONES, s.FECHA_SUGERIDA_CONTROL, s.ESTADO,
                       s.FECHA_RESPUESTA_PACIENTE, s.REPORTE_PACIENTE, s.CREATED_AT, s.UPDATED_AT,
                       a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                       p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                       TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                       prof.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                       TRIM(prof.NOMBRES || ' ' || prof.APELLIDOS) AS PROFESIONAL_NOMBRE,
                       esp.NOMBRE AS ESPECIALIDAD_NOMBRE
                FROM SEGUIMIENTO_POST_ATENCION s
                JOIN ATENCION a ON s.ATENCION_ID = a.ID
                JOIN PACIENTE p ON s.PACIENTE_ID = p.ID
                JOIN PROFESIONAL prof ON s.PROFESIONAL_ID = prof.ID
                JOIN ESPECIALIDAD esp ON prof.ESPECIALIDAD_ID = esp.ID
                WHERE s.PACIENTE_ID = ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(pacienteId);

        if (estadoFiltro != null && !estadoFiltro.isBlank()) {
            sql.append(" AND s.ESTADO = ? ");
            params.add(estadoFiltro.toUpperCase().trim());
        }

        sql.append(" ORDER BY s.CREATED_AT DESC, s.ID DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(page * size);
        params.add(size);

        return jdbcTemplate.query(sql.toString(), responseRowMapper, params.toArray());
    }

    /**
     * Conteo total de seguimientos de un paciente con filtro opcional de estado.
     */
    public int contarPorPacienteId(Long pacienteId, String estadoFiltro) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM SEGUIMIENTO_POST_ATENCION WHERE PACIENTE_ID = ?");
        List<Object> params = new ArrayList<>();
        params.add(pacienteId);

        if (estadoFiltro != null && !estadoFiltro.isBlank()) {
            sql.append(" AND ESTADO = ?");
            params.add(estadoFiltro.toUpperCase().trim());
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }
}
