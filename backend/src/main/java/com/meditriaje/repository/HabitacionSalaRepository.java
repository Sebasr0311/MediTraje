package com.meditriaje.repository;

import com.meditriaje.model.HabitacionSala;
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
public class HabitacionSalaRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<HabitacionSala> rowMapper = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREADO_AT");
        return new HabitacionSala(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("AREA_ID"),
                rs.getString("CODIGO"),
                rs.getString("TIPO"),
                rs.getString("ESTADO"),
                ts != null ? ts.toInstant() : null
        );
    };

    public HabitacionSalaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public HabitacionSala guardar(HabitacionSala habitacion) {
        String sql = """
                INSERT INTO HABITACION_SALA (
                    PUBLIC_ID, AREA_ID, CODIGO, TIPO, ESTADO
                ) VALUES (?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, habitacion.publicId());
            ps.setLong(2, habitacion.areaId());
            ps.setString(3, habitacion.codigo());
            ps.setString(4, habitacion.tipo() != null ? habitacion.tipo() : "COMPARTIDA");
            ps.setString(5, habitacion.estado() != null ? habitacion.estado() : "ACTIVA");
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar habitación recién guardada"));
    }

    public Optional<HabitacionSala> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM HABITACION_SALA WHERE ID = ?";
        List<HabitacionSala> resultados = jdbcTemplate.query(sql, rowMapper, id);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    public Optional<HabitacionSala> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM HABITACION_SALA WHERE PUBLIC_ID = ?";
        List<HabitacionSala> resultados = jdbcTemplate.query(sql, rowMapper, publicId);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    public List<HabitacionSala> listarPorAreaId(Long areaId) {
        String sql = "SELECT * FROM HABITACION_SALA WHERE AREA_ID = ? ORDER BY CODIGO ASC";
        return jdbcTemplate.query(sql, rowMapper, areaId);
    }
}
