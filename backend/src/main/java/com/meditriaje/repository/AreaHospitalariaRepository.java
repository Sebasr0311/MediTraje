package com.meditriaje.repository;

import com.meditriaje.model.AreaHospitalaria;
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
public class AreaHospitalariaRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AreaHospitalaria> rowMapper = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREADO_AT");
        return new AreaHospitalaria(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("SEDE_ID"),
                rs.getString("CODIGO"),
                rs.getString("NOMBRE"),
                rs.getString("TIPO"),
                rs.getString("PISO"),
                rs.getString("ESTADO"),
                ts != null ? ts.toInstant() : null
        );
    };

    public AreaHospitalariaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public AreaHospitalaria guardar(AreaHospitalaria area) {
        String sql = """
                INSERT INTO AREA_HOSPITALARIA (
                    PUBLIC_ID, SEDE_ID, CODIGO, NOMBRE, TIPO, PISO, ESTADO
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, area.publicId());
            ps.setLong(2, area.sedeId());
            ps.setString(3, area.codigo());
            ps.setString(4, area.nombre());
            ps.setString(5, area.tipo());
            ps.setString(6, area.piso());
            ps.setString(7, area.estado() != null ? area.estado() : "ACTIVA");
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar área recién guardada"));
    }

    public Optional<AreaHospitalaria> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM AREA_HOSPITALARIA WHERE ID = ?";
        List<AreaHospitalaria> resultados = jdbcTemplate.query(sql, rowMapper, id);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    public Optional<AreaHospitalaria> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM AREA_HOSPITALARIA WHERE PUBLIC_ID = ?";
        List<AreaHospitalaria> resultados = jdbcTemplate.query(sql, rowMapper, publicId);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    public List<AreaHospitalaria> listarPorSedeId(Long sedeId) {
        String sql = "SELECT * FROM AREA_HOSPITALARIA WHERE SEDE_ID = ? ORDER BY CODIGO ASC";
        return jdbcTemplate.query(sql, rowMapper, sedeId);
    }
}
