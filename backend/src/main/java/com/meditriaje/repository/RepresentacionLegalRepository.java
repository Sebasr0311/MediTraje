package com.meditriaje.repository;

import com.meditriaje.dto.affiliation.RepresentacionLegalResponse;
import com.meditriaje.model.RepresentacionLegal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class RepresentacionLegalRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<RepresentacionLegal> rowMapper = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREADO_AT");
        return new RepresentacionLegal(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PACIENTE_MENOR_ID"),
                rs.getLong("REPRESENTANTE_PACIENTE_ID"),
                rs.getString("PARENTESCO"),
                rs.getString("DOCUMENTO_SOPORTE"),
                rs.getInt("VERIFICADO") == 1,
                ts != null ? ts.toInstant() : null
        );
    };

    private final RowMapper<RepresentacionLegalResponse> responseRowMapper = (rs, rowNum) -> new RepresentacionLegalResponse(
            rs.getString("PUBLIC_ID"),
            rs.getString("MENOR_PUB_ID"),
            (rs.getString("MENOR_NOMBRE") + " " + rs.getString("MENOR_APELLIDO")),
            rs.getString("MENOR_DOC"),
            rs.getString("REP_PUB_ID"),
            (rs.getString("REP_NOMBRE") + " " + rs.getString("REP_APELLIDO")),
            rs.getString("REP_DOC"),
            rs.getString("PARENTESCO"),
            rs.getInt("VERIFICADO") == 1
    );

    public RepresentacionLegalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public RepresentacionLegal guardar(RepresentacionLegal r) {
        String sql = """
                INSERT INTO REPRESENTACION_LEGAL (
                    PUBLIC_ID, PACIENTE_MENOR_ID, REPRESENTANTE_PACIENTE_ID,
                    PARENTESCO, DOCUMENTO_SOPORTE, VERIFICADO
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, r.publicId());
            ps.setLong(2, r.pacienteMenorId());
            ps.setLong(3, r.representantePacienteId());
            ps.setString(4, r.parentesco());
            ps.setString(5, r.documentoSoporte());
            ps.setInt(6, r.verificado() ? 1 : 0);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar representación legal"));
    }

    public Optional<RepresentacionLegal> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM REPRESENTACION_LEGAL WHERE ID = ?";
        List<RepresentacionLegal> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<RepresentacionLegalResponse> listarPorRepresentanteId(Long representanteId) {
        String sql = """
                SELECT rl.PUBLIC_ID,
                       pm.PUBLIC_ID AS MENOR_PUB_ID,
                       pm.NOMBRES AS MENOR_NOMBRE,
                       pm.APELLIDOS AS MENOR_APELLIDO,
                       pm.NUMERO_DOCUMENTO AS MENOR_DOC,
                       pr.PUBLIC_ID AS REP_PUB_ID,
                       pr.NOMBRES AS REP_NOMBRE,
                       pr.APELLIDOS AS REP_APELLIDO,
                       pr.NUMERO_DOCUMENTO AS REP_DOC,
                       rl.PARENTESCO,
                       rl.VERIFICADO
                FROM REPRESENTACION_LEGAL rl
                JOIN PACIENTE pm ON rl.PACIENTE_MENOR_ID = pm.ID
                JOIN PACIENTE pr ON rl.REPRESENTANTE_PACIENTE_ID = pr.ID
                WHERE rl.REPRESENTANTE_PACIENTE_ID = ?
                """;
        return jdbcTemplate.query(sql, responseRowMapper, representanteId);
    }
}
