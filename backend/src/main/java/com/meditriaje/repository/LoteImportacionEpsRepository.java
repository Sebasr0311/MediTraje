package com.meditriaje.repository;

import com.meditriaje.model.DetalleImportacionEps;
import com.meditriaje.model.LoteImportacionEps;
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
public class LoteImportacionEpsRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<LoteImportacionEps> loteRowMapper = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Timestamp tsProc = rs.getTimestamp("PROCESADO_AT");
        return new LoteImportacionEps(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getString("NOMBRE_ARCHIVO"),
                rs.getString("HASH_SHA256"),
                rs.getLong("EPS_ID"),
                rs.getInt("TOTAL_FILAS"),
                rs.getInt("FILAS_VALIDAS"),
                rs.getInt("FILAS_FALLIDAS"),
                rs.getString("ESTADO"),
                rs.getLong("SUBIDO_POR_USUARIO_ID"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsProc != null ? tsProc.toInstant() : null
        );
    };

    private final RowMapper<DetalleImportacionEps> detalleRowMapper = (rs, rowNum) -> new DetalleImportacionEps(
            rs.getLong("ID"),
            rs.getLong("LOTE_ID"),
            rs.getInt("NUMERO_FILA"),
            rs.getString("TIPO_DOCUMENTO"),
            rs.getString("NUMERO_DOCUMENTO"),
            rs.getString("NOMBRES"),
            rs.getString("APELLIDOS"),
            rs.getString("REGIMEN"),
            rs.getString("TIPO_AFILIADO"),
            rs.getString("ESTADO_FILA"),
            rs.getString("ERROR_MOTIVO")
    );

    public LoteImportacionEpsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public LoteImportacionEps guardarLote(LoteImportacionEps lote) {
        String sql = """
                INSERT INTO LOTE_IMPORTACION_EPS (
                    PUBLIC_ID, NOMBRE_ARCHIVO, HASH_SHA256, EPS_ID,
                    TOTAL_FILAS, FILAS_VALIDAS, FILAS_FALLIDAS, ESTADO,
                    SUBIDO_POR_USUARIO_ID
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, lote.publicId());
            ps.setString(2, lote.nombreArchivo());
            ps.setString(3, lote.hashSha256());
            ps.setLong(4, lote.epsId());
            ps.setInt(5, lote.totalFilas());
            ps.setInt(6, lote.filasValidas());
            ps.setInt(7, lote.filasFallidas());
            ps.setString(8, lote.estado() != null ? lote.estado() : "PREVIEW");
            ps.setLong(9, lote.subidoPorUsuarioId());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar lote guardado"));
    }

    public Optional<LoteImportacionEps> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM LOTE_IMPORTACION_EPS WHERE ID = ?";
        List<LoteImportacionEps> list = jdbcTemplate.query(sql, loteRowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<LoteImportacionEps> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM LOTE_IMPORTACION_EPS WHERE PUBLIC_ID = ?";
        List<LoteImportacionEps> list = jdbcTemplate.query(sql, loteRowMapper, publicId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void guardarDetallesBatch(Long loteId, List<DetalleImportacionEps> detalles) {
        if (detalles == null || detalles.isEmpty()) return;
        String sql = """
                INSERT INTO DETALLE_IMPORTACION_EPS (
                    LOTE_ID, NUMERO_FILA, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                    NOMBRES, APELLIDOS, REGIMEN, TIPO_AFILIADO, ESTADO_FILA, ERROR_MOTIVO
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql, detalles, detalles.size(), (ps, d) -> {
            ps.setLong(1, loteId);
            ps.setInt(2, d.numeroFila());
            ps.setString(3, d.tipoDocumento());
            ps.setString(4, d.numeroDocumento());
            ps.setString(5, d.nombres());
            ps.setString(6, d.apellidos());
            ps.setString(7, d.regimen());
            ps.setString(8, d.tipoAfiliado());
            ps.setString(9, d.estadoFila());
            ps.setString(10, d.errorMotivo());
        });
    }

    public void actualizarTotales(Long loteId, int total, int validas, int fallidas) {
        String sql = "UPDATE LOTE_IMPORTACION_EPS SET TOTAL_FILAS = ?, FILAS_VALIDAS = ?, FILAS_FALLIDAS = ? WHERE ID = ?";
        jdbcTemplate.update(sql, total, validas, fallidas, loteId);
    }

    public void cambiarEstadoLote(Long loteId, String nuevoEstado, Instant procesadoAt) {
        String sql = "UPDATE LOTE_IMPORTACION_EPS SET ESTADO = ?, PROCESADO_AT = ? WHERE ID = ?";
        jdbcTemplate.update(sql, nuevoEstado, procesadoAt != null ? Timestamp.from(procesadoAt) : null, loteId);
    }

    public List<DetalleImportacionEps> listarDetalles(Long loteId, int limite) {
        String sql = "SELECT * FROM DETALLE_IMPORTACION_EPS WHERE LOTE_ID = ? ORDER BY NUMERO_FILA ASC FETCH FIRST ? ROWS ONLY";
        return jdbcTemplate.query(sql, detalleRowMapper, loteId, limite);
    }

    public List<DetalleImportacionEps> listarFilasValidas(Long loteId) {
        String sql = "SELECT * FROM DETALLE_IMPORTACION_EPS WHERE LOTE_ID = ? AND ESTADO_FILA = 'VALIDO' ORDER BY NUMERO_FILA ASC";
        return jdbcTemplate.query(sql, detalleRowMapper, loteId);
    }
}
