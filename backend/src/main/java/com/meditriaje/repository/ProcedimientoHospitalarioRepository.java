package com.meditriaje.repository;

import com.meditriaje.dto.hospital.ProcedimientoResponse;
import com.meditriaje.model.ProcedimientoHospitalario;
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

@Repository
public class ProcedimientoHospitalarioRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ProcedimientoHospitalario> rowMapper = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("INICIO_AT");
        Timestamp tsFin = rs.getTimestamp("FIN_AT");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        return new ProcedimientoHospitalario(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getString("TIPO_PROCEDIMIENTO"),
                rs.getString("DESCRIPCION"),
                rs.getLong("PROFESIONAL_ID"),
                rs.getObject("SALA_ID", Long.class),
                rs.getString("ESTADO"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("OBSERVACIONES"),
                tsCreado != null ? tsCreado.toInstant() : null
        );
    };

    private final RowMapper<ProcedimientoResponse> responseRowMapper = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("INICIO_AT");
        Timestamp tsFin = rs.getTimestamp("FIN_AT");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        return new ProcedimientoResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("EPISODIO_PUB_ID"),
                rs.getString("TIPO_PROCEDIMIENTO"),
                rs.getString("DESCRIPCION"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("SALA_CODIGO"),
                rs.getString("ESTADO"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("OBSERVACIONES"),
                tsCreado != null ? tsCreado.toInstant() : null
        );
    };

    public ProcedimientoHospitalarioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public ProcedimientoHospitalario guardar(ProcedimientoHospitalario p) {
        String sql = """
                INSERT INTO PROCEDIMIENTO_HOSPITALARIO (
                    PUBLIC_ID, EPISODIO_ID, TIPO_PROCEDIMIENTO, DESCRIPCION,
                    PROFESIONAL_ID, SALA_ID, ESTADO, INICIO_AT, OBSERVACIONES
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, p.publicId());
            ps.setLong(2, p.episodioId());
            ps.setString(3, p.tipoProcedimiento());
            ps.setString(4, p.descripcion());
            ps.setLong(5, p.profesionalId());
            if (p.salaId() != null) ps.setLong(6, p.salaId()); else ps.setNull(6, java.sql.Types.NUMERIC);
            ps.setString(7, p.estado() != null ? p.estado() : "PROGRAMADO");
            if (p.inicioAt() != null) ps.setTimestamp(8, Timestamp.from(p.inicioAt())); else ps.setNull(8, java.sql.Types.TIMESTAMP);
            ps.setString(9, p.observaciones());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar procedimiento recién guardado"));
    }

    public Optional<ProcedimientoHospitalario> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM PROCEDIMIENTO_HOSPITALARIO WHERE ID = ?";
        List<ProcedimientoHospitalario> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<ProcedimientoHospitalario> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM PROCEDIMIENTO_HOSPITALARIO WHERE PUBLIC_ID = ?";
        List<ProcedimientoHospitalario> list = jdbcTemplate.query(sql, rowMapper, publicId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void actualizarEstado(Long id, String nuevoEstado, Instant finAt, String observaciones) {
        String sql = "UPDATE PROCEDIMIENTO_HOSPITALARIO SET ESTADO = ?, FIN_AT = ?, OBSERVACIONES = COALESCE(?, OBSERVACIONES) WHERE ID = ?";
        jdbcTemplate.update(sql, nuevoEstado, finAt != null ? Timestamp.from(finAt) : null, observaciones, id);
    }

    public List<ProcedimientoResponse> listarPorEpisodioId(Long episodioId) {
        String sql = """
                SELECT ph.PUBLIC_ID,
                       e.PUBLIC_ID AS EPISODIO_PUB_ID,
                       ph.TIPO_PROCEDIMIENTO,
                       ph.DESCRIPCION,
                       (prof.NOMBRE || ' ' || prof.APELLIDO) AS PROFESIONAL_NOMBRE,
                       hs.CODIGO AS SALA_CODIGO,
                       ph.ESTADO,
                       ph.INICIO_AT,
                       ph.FIN_AT,
                       ph.OBSERVACIONES,
                       ph.CREADO_AT
                FROM PROCEDIMIENTO_HOSPITALARIO ph
                JOIN EPISODIO_ATENCION e ON ph.EPISODIO_ID = e.ID
                JOIN PROFESIONAL_SALUD prof ON ph.PROFESIONAL_ID = prof.ID
                LEFT JOIN HABITACION_SALA hs ON ph.SALA_ID = hs.ID
                WHERE ph.EPISODIO_ID = ?
                ORDER BY ph.CREADO_AT DESC
                """;
        return jdbcTemplate.query(sql, responseRowMapper, episodioId);
    }
}
