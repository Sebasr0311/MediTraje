package com.meditriaje.repository;

import com.meditriaje.model.EgresoHospitalario;
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
public class EgresoHospitalarioRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<EgresoHospitalario> rowMapper = (rs, rowNum) -> {
        Timestamp tsEgreso = rs.getTimestamp("FECHA_EGRESO");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        return new EgresoHospitalario(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getLong("MEDICO_EGRESO_ID"),
                tsEgreso != null ? tsEgreso.toInstant() : null,
                rs.getString("TIPO_DESTINO"),
                rs.getString("DIAGNOSTICO_EGRESO"),
                rs.getString("EPICRISIS_RESUMEN"),
                rs.getString("PLAN_MANEJO"),
                tsCreado != null ? tsCreado.toInstant() : null
        );
    };

    public EgresoHospitalarioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public EgresoHospitalario guardar(EgresoHospitalario egreso) {
        String sql = """
                INSERT INTO EGRESO_HOSPITALARIO (
                    PUBLIC_ID, EPISODIO_ID, MEDICO_EGRESO_ID, FECHA_EGRESO,
                    TIPO_DESTINO, DIAGNOSTICO_EGRESO, EPICRISIS_RESUMEN, PLAN_MANEJO
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, egreso.publicId());
            ps.setLong(2, egreso.episodioId());
            ps.setLong(3, egreso.medicoEgresoId());
            ps.setTimestamp(4, Timestamp.from(egreso.fechaEgreso()));
            ps.setString(5, egreso.tipoDestino());
            ps.setString(6, egreso.diagnosticoEgreso());
            ps.setString(7, egreso.epicrisisResumen());
            ps.setString(8, egreso.planManejo());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar egreso recién guardado"));
    }

    public Optional<EgresoHospitalario> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM EGRESO_HOSPITALARIO WHERE ID = ?";
        List<EgresoHospitalario> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<EgresoHospitalario> buscarPorEpisodioId(Long episodioId) {
        String sql = "SELECT * FROM EGRESO_HOSPITALARIO WHERE EPISODIO_ID = ?";
        List<EgresoHospitalario> list = jdbcTemplate.query(sql, rowMapper, episodioId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
