package com.meditriaje.repository;

import com.meditriaje.dto.affiliation.AusenciaResponse;
import com.meditriaje.model.AusenciaMedica;
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
public class AusenciaMedicaRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AusenciaMedica> rowMapper = (rs, rowNum) -> {
        Timestamp tsIni = rs.getTimestamp("FECHA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_FIN");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        return new AusenciaMedica(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PROFESIONAL_ID"),
                tsIni != null ? tsIni.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("MOTIVO"),
                rs.getString("ESTADO"),
                rs.getLong("REGISTRADO_POR_USUARIO_ID"),
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    private final RowMapper<AusenciaResponse> responseRowMapper = (rs, rowNum) -> {
        Timestamp tsIni = rs.getTimestamp("FECHA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_FIN");
        return new AusenciaResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("PROF_PUB_ID"),
                (rs.getString("PROF_NOMBRE") + " " + rs.getString("PROF_APELLIDO")),
                tsIni != null ? tsIni.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("MOTIVO"),
                rs.getString("ESTADO")
        );
    };

    public AusenciaMedicaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public AusenciaMedica guardar(AusenciaMedica a) {
        String sql = """
                INSERT INTO AUSENCIA_MEDICA (
                    PUBLIC_ID, PROFESIONAL_ID, FECHA_INICIO, FECHA_FIN,
                    MOTIVO, ESTADO, REGISTRADO_POR_USUARIO_ID
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, a.publicId());
            ps.setLong(2, a.profesionalId());
            ps.setTimestamp(3, Timestamp.from(a.fechaInicio()));
            ps.setTimestamp(4, Timestamp.from(a.fechaFin()));
            ps.setString(5, a.motivo());
            ps.setString(6, a.estado() != null ? a.estado() : "ACTIVA");
            ps.setLong(7, a.registradoPorUsuarioId());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar ausencia guardada"));
    }

    public Optional<AusenciaMedica> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM AUSENCIA_MEDICA WHERE ID = ?";
        List<AusenciaMedica> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean existeTraslapeAusencia(Long profesionalId, Instant inicio, Instant fin) {
        String sql = """
                SELECT COUNT(*) FROM AUSENCIA_MEDICA
                WHERE PROFESIONAL_ID = ? AND ESTADO = 'ACTIVA'
                  AND NOT (FECHA_FIN <= ? OR FECHA_INICIO >= ?)
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, profesionalId, Timestamp.from(inicio), Timestamp.from(fin));
        return count != null && count > 0;
    }

    public List<AusenciaResponse> listarPorProfesionalId(Long profesionalId) {
        String sql = """
                SELECT a.PUBLIC_ID,
                       p.PUBLIC_ID AS PROF_PUB_ID,
                       p.NOMBRES AS PROF_NOMBRE,
                       p.APELLIDOS AS PROF_APELLIDO,
                       a.FECHA_INICIO,
                       a.FECHA_FIN,
                       a.MOTIVO,
                       a.ESTADO
                FROM AUSENCIA_MEDICA a
                JOIN PROFESIONAL p ON a.PROFESIONAL_ID = p.ID
                WHERE a.PROFESIONAL_ID = ?
                ORDER BY a.FECHA_INICIO DESC
                """;
        return jdbcTemplate.query(sql, responseRowMapper, profesionalId);
    }
}
