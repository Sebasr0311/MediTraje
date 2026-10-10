package com.meditriaje.repository;

import com.meditriaje.model.OcupacionCama;
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
public class OcupacionCamaRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<OcupacionCama> rowMapper = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("INICIO_AT");
        Timestamp tsFin = rs.getTimestamp("FIN_AT");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        return new OcupacionCama(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getLong("CAMA_ID"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("ESTADO"),
                rs.getLong("ASIGNADO_POR_USUARIO_ID"),
                rs.getString("MOTIVO_ASIGNACION"),
                tsCreado != null ? tsCreado.toInstant() : null
        );
    };

    public OcupacionCamaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public OcupacionCama guardar(OcupacionCama ocupacion) {
        String sql = """
                INSERT INTO OCUPACION_CAMA (
                    PUBLIC_ID, EPISODIO_ID, CAMA_ID, INICIO_AT, ESTADO, ASIGNADO_POR_USUARIO_ID, MOTIVO_ASIGNACION
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, ocupacion.publicId());
            ps.setLong(2, ocupacion.episodioId());
            ps.setLong(3, ocupacion.camaId());
            ps.setTimestamp(4, Timestamp.from(ocupacion.inicioAt()));
            ps.setString(5, ocupacion.estado() != null ? ocupacion.estado() : "ACTIVA");
            ps.setLong(6, ocupacion.asignadoPorUsuarioId());
            ps.setString(7, ocupacion.motivoAsignacion());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar ocupación recién guardada"));
    }

    public Optional<OcupacionCama> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM OCUPACION_CAMA WHERE ID = ?";
        List<OcupacionCama> resultados = jdbcTemplate.query(sql, rowMapper, id);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    public Optional<OcupacionCama> buscarActivaPorCamaId(Long camaId) {
        String sql = "SELECT * FROM OCUPACION_CAMA WHERE CAMA_ID = ? AND ESTADO = 'ACTIVA'";
        List<OcupacionCama> resultados = jdbcTemplate.query(sql, rowMapper, camaId);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    public Optional<OcupacionCama> buscarActivaPorEpisodioId(Long episodioId) {
        String sql = "SELECT * FROM OCUPACION_CAMA WHERE EPISODIO_ID = ? AND ESTADO = 'ACTIVA'";
        List<OcupacionCama> resultados = jdbcTemplate.query(sql, rowMapper, episodioId);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    public void finalizarOcupacion(Long ocupacionId, Instant finAt) {
        String sql = "UPDATE OCUPACION_CAMA SET ESTADO = 'FINALIZADA', FIN_AT = ? WHERE ID = ?";
        jdbcTemplate.update(sql, Timestamp.from(finAt), ocupacionId);
    }
}
