package com.meditriaje.repository;

import com.meditriaje.model.EntidadEps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class EntidadEpsRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<EntidadEps> rowMapper = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREADO_AT");
        return new EntidadEps(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getString("CODIGO_MINSALUD"),
                rs.getString("NOMBRE"),
                rs.getString("NIT"),
                rs.getString("REGIMEN_HABITUAL"),
                rs.getString("ESTADO"),
                ts != null ? ts.toInstant() : null
        );
    };

    public EntidadEpsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public Optional<EntidadEps> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM ENTIDAD_EPS WHERE ID = ?";
        List<EntidadEps> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<EntidadEps> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM ENTIDAD_EPS WHERE PUBLIC_ID = ?";
        List<EntidadEps> list = jdbcTemplate.query(sql, rowMapper, publicId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<EntidadEps> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM ENTIDAD_EPS WHERE CODIGO_MINSALUD = ?";
        List<EntidadEps> list = jdbcTemplate.query(sql, rowMapper, codigo);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<EntidadEps> listarActivas() {
        String sql = "SELECT * FROM ENTIDAD_EPS WHERE ESTADO = 'ACTIVA' ORDER BY NOMBRE ASC";
        return jdbcTemplate.query(sql, rowMapper);
    }
}
