package com.meditriaje.repository;

import com.meditriaje.dto.operational.AlertaOperativaResponse;
import com.meditriaje.model.AlertaOperativa;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AlertaOperativaRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AlertaOperativa> rowMapper = (rs, rowNum) -> {
        Timestamp tsRec = rs.getTimestamp("RECONOCIDO_AT");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Long recUser = rs.getObject("RECONOCIDO_POR_USUARIO_ID") != null ? rs.getLong("RECONOCIDO_POR_USUARIO_ID") : null;
        return new AlertaOperativa(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("SEDE_ID"),
                rs.getString("TIPO_ALERTA"),
                rs.getString("NIVEL_SEVERIDAD"),
                rs.getString("MENSAJE"),
                rs.getString("ESTADO"),
                recUser,
                tsRec != null ? tsRec.toInstant() : null,
                rs.getString("MOTIVO_RECONOCIMIENTO"),
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    private final RowMapper<AlertaOperativaResponse> responseRowMapper = (rs, rowNum) -> {
        Timestamp tsRec = rs.getTimestamp("RECONOCIDO_AT");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        return new AlertaOperativaResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("SEDE_PUB_ID"),
                rs.getString("SEDE_NOMBRE"),
                rs.getString("TIPO_ALERTA"),
                rs.getString("NIVEL_SEVERIDAD"),
                rs.getString("MENSAJE"),
                rs.getString("ESTADO"),
                rs.getString("RECONOCIDO_POR_EMAIL"),
                tsRec != null ? tsRec.toInstant() : null,
                rs.getString("MOTIVO_RECONOCIMIENTO"),
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    public AlertaOperativaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public AlertaOperativa guardar(AlertaOperativa a) {
        String sql = """
                INSERT INTO ALERTA_OPERATIVA (
                    PUBLIC_ID, SEDE_ID, TIPO_ALERTA, NIVEL_SEVERIDAD, MENSAJE, ESTADO
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, a.publicId());
            ps.setLong(2, a.sedeId());
            ps.setString(3, a.tipoAlerta());
            ps.setString(4, a.nivelSeveridad());
            ps.setString(5, a.mensaje());
            ps.setString(6, a.estado() != null ? a.estado() : "ACTIVA");
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar alerta guardada"));
    }

    public Optional<AlertaOperativa> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM ALERTA_OPERATIVA WHERE ID = ?";
        List<AlertaOperativa> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<AlertaOperativa> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM ALERTA_OPERATIVA WHERE PUBLIC_ID = ?";
        List<AlertaOperativa> list = jdbcTemplate.query(sql, rowMapper, publicId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<AlertaOperativaResponse> listarPorSede(Long sedeId, String estado) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.PUBLIC_ID,
                       s.PUBLIC_ID AS SEDE_PUB_ID,
                       s.NOMBRE AS SEDE_NOMBRE,
                       a.TIPO_ALERTA,
                       a.NIVEL_SEVERIDAD,
                       a.MENSAJE,
                       a.ESTADO,
                       u.EMAIL AS RECONOCIDO_POR_EMAIL,
                       a.RECONOCIDO_AT,
                       a.MOTIVO_RECONOCIMIENTO,
                       a.CREADO_AT
                FROM ALERTA_OPERATIVA a
                JOIN SEDE s ON a.SEDE_ID = s.ID
                LEFT JOIN USUARIO u ON a.RECONOCIDO_POR_USUARIO_ID = u.ID
                WHERE 1=1
                """);

        List<Object> params = new ArrayList<>();
        if (sedeId != null) {
            sql.append(" AND a.SEDE_ID = ?");
            params.add(sedeId);
        }
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND a.ESTADO = ?");
            params.add(estado);
        }
        sql.append(" ORDER BY a.CREADO_AT DESC");

        return jdbcTemplate.query(sql.toString(), responseRowMapper, params.toArray());
    }

    public void reconocerAlerta(Long id, Long usuarioId, Instant ahora, String motivo) {
        String sql = """
                UPDATE ALERTA_OPERATIVA
                SET ESTADO = 'RECONOCIDA',
                    RECONOCIDO_POR_USUARIO_ID = ?,
                    RECONOCIDO_AT = ?,
                    MOTIVO_RECONOCIMIENTO = ?
                WHERE ID = ?
                """;
        jdbcTemplate.update(sql, usuarioId, Timestamp.from(ahora), motivo, id);
    }
}
