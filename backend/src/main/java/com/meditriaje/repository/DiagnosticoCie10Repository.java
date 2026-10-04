package com.meditriaje.repository;

import com.meditriaje.dto.clinical.DiagnosticoCie10Response;
import com.meditriaje.model.DiagnosticoCie10;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para el catálogo de patologías {@code DIAGNOSTICO_CIE10}.
 * SQL 100% parametrizado (ADR-001, ADR-012, ADR-013).
 */
@Repository
public class DiagnosticoCie10Repository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<DiagnosticoCie10> diagnosticoRowMapper = (rs, rowNum) -> new DiagnosticoCie10(
            rs.getLong("ID"),
            rs.getString("CODIGO"),
            rs.getString("DESCRIPCION"),
            rs.getString("ESTADO")
    );

    public DiagnosticoCie10Repository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public Optional<DiagnosticoCie10> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT ID, CODIGO, DESCRIPCION, ESTADO FROM DIAGNOSTICO_CIE10 WHERE CODIGO = ?";
        List<DiagnosticoCie10> lista = jdbcTemplate.query(sql, diagnosticoRowMapper, codigo.trim().toUpperCase(Locale.ROOT));
        return lista.stream().findFirst();
    }

    public Optional<DiagnosticoCie10> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = "SELECT ID, CODIGO, DESCRIPCION, ESTADO FROM DIAGNOSTICO_CIE10 WHERE ID = ?";
        List<DiagnosticoCie10> lista = jdbcTemplate.query(sql, diagnosticoRowMapper, id);
        return lista.stream().findFirst();
    }

    public List<DiagnosticoCie10Response> listarActivos(String query) {
        StringBuilder sql = new StringBuilder("SELECT CODIGO, DESCRIPCION FROM DIAGNOSTICO_CIE10 WHERE ESTADO = 'ACTIVO'");
        List<Object> params = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            sql.append(" AND (UPPER(CODIGO) LIKE ? OR UPPER(DESCRIPCION) LIKE ?)");
            String pattern = "%" + query.trim().toUpperCase(Locale.ROOT) + "%";
            params.add(pattern);
            params.add(pattern);
        }

        sql.append(" ORDER BY CODIGO ASC");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DiagnosticoCie10Response(
                rs.getString("CODIGO"),
                rs.getString("DESCRIPCION")
        ), params.toArray());
    }
}
