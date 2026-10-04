package com.meditriaje.repository;

import com.meditriaje.dto.prescription.MedicamentoResponse;
import com.meditriaje.model.Medicamento;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para el catálogo maestro de medicamentos {@code MEDICAMENTO}.
 * SQL 100% parametrizado (ADR-001, ADR-012, HU-08).
 */
@Repository
public class MedicamentoRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Medicamento> medicamentoRowMapper = (rs, rowNum) -> new Medicamento(
            rs.getLong("ID"),
            rs.getString("PUBLIC_ID"),
            rs.getString("CODIGO"),
            rs.getString("NOMBRE_COMERCIAL"),
            rs.getString("PRINCIPIO_ACTIVO"),
            rs.getString("PRESENTACION"),
            rs.getString("CONCENTRACION"),
            rs.getString("ESTADO")
    );

    public MedicamentoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public Optional<Medicamento> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        String sql = """
            SELECT ID, PUBLIC_ID, CODIGO, NOMBRE_COMERCIAL, PRINCIPIO_ACTIVO, PRESENTACION, CONCENTRACION, ESTADO
            FROM MEDICAMENTO
            WHERE PUBLIC_ID = ?
            """;
        List<Medicamento> lista = jdbcTemplate.query(sql, medicamentoRowMapper, publicId.trim());
        return lista.stream().findFirst();
    }

    public Optional<Medicamento> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = """
            SELECT ID, PUBLIC_ID, CODIGO, NOMBRE_COMERCIAL, PRINCIPIO_ACTIVO, PRESENTACION, CONCENTRACION, ESTADO
            FROM MEDICAMENTO
            WHERE ID = ?
            """;
        List<Medicamento> lista = jdbcTemplate.query(sql, medicamentoRowMapper, id);
        return lista.stream().findFirst();
    }

    public List<MedicamentoResponse> listarActivos(String query, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        int offset = safePage * safeSize;

        StringBuilder sql = new StringBuilder("""
            SELECT PUBLIC_ID, CODIGO, NOMBRE_COMERCIAL, PRINCIPIO_ACTIVO, PRESENTACION, CONCENTRACION, ESTADO
            FROM MEDICAMENTO
            WHERE ESTADO = 'ACTIVO'
            """);
        List<Object> params = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            sql.append(" AND (UPPER(NOMBRE_COMERCIAL) LIKE ? OR UPPER(PRINCIPIO_ACTIVO) LIKE ? OR UPPER(CODIGO) LIKE ?)");
            String pattern = "%" + query.trim().toUpperCase(Locale.ROOT) + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        sql.append(" ORDER BY NOMBRE_COMERCIAL ASC, CODIGO ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(safeSize);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new MedicamentoResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("CODIGO"),
                rs.getString("NOMBRE_COMERCIAL"),
                rs.getString("PRINCIPIO_ACTIVO"),
                rs.getString("PRESENTACION"),
                rs.getString("CONCENTRACION"),
                rs.getString("ESTADO")
        ), params.toArray());
    }

    public int contarActivos(String query) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM MEDICAMENTO WHERE ESTADO = 'ACTIVO'");
        List<Object> params = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            sql.append(" AND (UPPER(NOMBRE_COMERCIAL) LIKE ? OR UPPER(PRINCIPIO_ACTIVO) LIKE ? OR UPPER(CODIGO) LIKE ?)");
            String pattern = "%" + query.trim().toUpperCase(Locale.ROOT) + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        Integer total = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return total != null ? total : 0;
    }
}
