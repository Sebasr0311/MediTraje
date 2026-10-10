package com.meditriaje.repository;

import com.meditriaje.dto.affiliation.AfiliacionResponse;
import com.meditriaje.model.AfiliacionPaciente;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AfiliacionPacienteRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AfiliacionPaciente> rowMapper = (rs, rowNum) -> {
        Date d = rs.getDate("FECHA_AFILIACION");
        Timestamp tsVerif = rs.getTimestamp("ULTIMA_VERIFICACION_AT");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        return new AfiliacionPaciente(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getObject("PACIENTE_ID", Long.class),
                rs.getString("TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                rs.getLong("EPS_ID"),
                rs.getString("REGIMEN"),
                rs.getString("TIPO_AFILIADO"),
                rs.getString("ESTADO"),
                d != null ? d.toLocalDate() : null,
                rs.getString("FUENTE_VERIFICACION"),
                tsVerif != null ? tsVerif.toInstant() : null,
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null
        );
    };

    private final RowMapper<AfiliacionResponse> responseRowMapper = (rs, rowNum) -> {
        Date d = rs.getDate("FECHA_AFILIACION");
        Timestamp tsVerif = rs.getTimestamp("ULTIMA_VERIFICACION_AT");
        return new AfiliacionResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("PACIENTE_PUB_ID"),
                rs.getString("TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("EPS_CODIGO"),
                rs.getString("EPS_NOMBRE"),
                rs.getString("REGIMEN"),
                rs.getString("TIPO_AFILIADO"),
                rs.getString("ESTADO"),
                d != null ? d.toLocalDate() : null,
                rs.getString("FUENTE_VERIFICACION"),
                tsVerif != null ? tsVerif.toInstant() : null
        );
    };

    public AfiliacionPacienteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public AfiliacionPaciente guardar(AfiliacionPaciente a) {
        String sql = """
                INSERT INTO AFILIACION_PACIENTE (
                    PUBLIC_ID, PACIENTE_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                    EPS_ID, REGIMEN, TIPO_AFILIADO, ESTADO, FECHA_AFILIACION,
                    FUENTE_VERIFICACION, ULTIMA_VERIFICACION_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, a.publicId());
            if (a.pacienteId() != null) ps.setLong(2, a.pacienteId()); else ps.setNull(2, java.sql.Types.NUMERIC);
            ps.setString(3, a.tipoDocumento());
            ps.setString(4, a.numeroDocumento());
            ps.setLong(5, a.epsId());
            ps.setString(6, a.regimen());
            ps.setString(7, a.tipoAfiliado() != null ? a.tipoAfiliado() : "COTIZANTE");
            ps.setString(8, a.estado() != null ? a.estado() : "ACTIVO");
            if (a.fechaAfiliacion() != null) ps.setDate(9, Date.valueOf(a.fechaAfiliacion())); else ps.setNull(9, java.sql.Types.DATE);
            ps.setString(10, a.fuenteVerificacion() != null ? a.fuenteVerificacion() : "CARGA_MASIVA");
            ps.setTimestamp(11, a.ultimaVerificacionAt() != null ? Timestamp.from(a.ultimaVerificacionAt()) : Timestamp.from(Instant.now()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar afiliación guardada"));
    }

    public Optional<AfiliacionPaciente> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM AFILIACION_PACIENTE WHERE ID = ?";
        List<AfiliacionPaciente> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<AfiliacionPaciente> buscarPorDocumento(String tipoDoc, String numDoc) {
        String sql = "SELECT * FROM AFILIACION_PACIENTE WHERE TIPO_DOCUMENTO = ? AND NUMERO_DOCUMENTO = ?";
        List<AfiliacionPaciente> list = jdbcTemplate.query(sql, rowMapper, tipoDoc, numDoc);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void actualizarAfiliacion(Long id, Long epsId, String regimen, String tipoAfiliado, String estado, Instant verificacionAt) {
        String sql = """
                UPDATE AFILIACION_PACIENTE
                SET EPS_ID = ?, REGIMEN = ?, TIPO_AFILIADO = ?, ESTADO = ?,
                    ULTIMA_VERIFICACION_AT = ?, UPDATED_AT = CURRENT_TIMESTAMP
                WHERE ID = ?
                """;
        jdbcTemplate.update(sql, epsId, regimen, tipoAfiliado, estado, Timestamp.from(verificacionAt), id);
    }

    public void vincularPaciente(String tipoDoc, String numDoc, Long pacienteId) {
        String sql = "UPDATE AFILIACION_PACIENTE SET PACIENTE_ID = ?, UPDATED_AT = CURRENT_TIMESTAMP WHERE TIPO_DOCUMENTO = ? AND NUMERO_DOCUMENTO = ? AND PACIENTE_ID IS NULL";
        jdbcTemplate.update(sql, pacienteId, tipoDoc, numDoc);
    }

    public Optional<AfiliacionResponse> buscarDetallePorDocumento(String tipoDoc, String numDoc) {
        String sql = """
                SELECT a.PUBLIC_ID,
                       p.PUBLIC_ID AS PACIENTE_PUB_ID,
                       a.TIPO_DOCUMENTO,
                       a.NUMERO_DOCUMENTO,
                       CASE
                           WHEN p.ID IS NOT NULL THEN (p.NOMBRE || ' ' || p.APELLIDO)
                           ELSE NULL
                       END AS PACIENTE_NOMBRE,
                       eps.CODIGO_MINSALUD AS EPS_CODIGO,
                       eps.NOMBRE AS EPS_NOMBRE,
                       a.REGIMEN,
                       a.TIPO_AFILIADO,
                       a.ESTADO,
                       a.FECHA_AFILIACION,
                       a.FUENTE_VERIFICACION,
                       a.ULTIMA_VERIFICACION_AT
                FROM AFILIACION_PACIENTE a
                JOIN ENTIDAD_EPS eps ON a.EPS_ID = eps.ID
                LEFT JOIN PACIENTE p ON a.PACIENTE_ID = p.ID
                WHERE a.TIPO_DOCUMENTO = ? AND a.NUMERO_DOCUMENTO = ?
                """;
        List<AfiliacionResponse> list = jdbcTemplate.query(sql, responseRowMapper, tipoDoc, numDoc);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<AfiliacionResponse> buscarDetallePorPacienteId(Long pacienteId) {
        String sql = """
                SELECT a.PUBLIC_ID,
                       p.PUBLIC_ID AS PACIENTE_PUB_ID,
                       a.TIPO_DOCUMENTO,
                       a.NUMERO_DOCUMENTO,
                       (p.NOMBRE || ' ' || p.APELLIDO) AS PACIENTE_NOMBRE,
                       eps.CODIGO_MINSALUD AS EPS_CODIGO,
                       eps.NOMBRE AS EPS_NOMBRE,
                       a.REGIMEN,
                       a.TIPO_AFILIADO,
                       a.ESTADO,
                       a.FECHA_AFILIACION,
                       a.FUENTE_VERIFICACION,
                       a.ULTIMA_VERIFICACION_AT
                FROM AFILIACION_PACIENTE a
                JOIN ENTIDAD_EPS eps ON a.EPS_ID = eps.ID
                JOIN PACIENTE p ON a.PACIENTE_ID = p.ID
                WHERE a.PACIENTE_ID = ?
                """;
        List<AfiliacionResponse> list = jdbcTemplate.query(sql, responseRowMapper, pacienteId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
