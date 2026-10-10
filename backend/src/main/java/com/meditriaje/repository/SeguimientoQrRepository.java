package com.meditriaje.repository;

import com.meditriaje.model.SeguimientoIntrahospitalarioQr;
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
public class SeguimientoQrRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SeguimientoIntrahospitalarioQr> rowMapper = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Timestamp tsExp = rs.getTimestamp("EXPIRA_AT");
        return new SeguimientoIntrahospitalarioQr(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getString("CODIGO_QR_TOKEN"),
                rs.getString("UBICACION_ACTUAL_TEXTO"),
                rs.getString("ESTADO"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsExp != null ? tsExp.toInstant() : null
        );
    };

    public SeguimientoQrRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public SeguimientoIntrahospitalarioQr guardar(SeguimientoIntrahospitalarioQr q) {
        String sql = """
                INSERT INTO SEGUIMIENTO_INTRAHOSPITALARIO_QR (
                    PUBLIC_ID, EPISODIO_ID, CODIGO_QR_TOKEN, UBICACION_ACTUAL_TEXTO,
                    ESTADO, EXPIRA_AT
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, q.publicId());
            ps.setLong(2, q.episodioId());
            ps.setString(3, q.codigoQrToken());
            ps.setString(4, q.ubicacionActualTexto());
            ps.setString(5, q.estado() != null ? q.estado() : "ACTIVO");
            ps.setTimestamp(6, q.expiraAt() != null ? Timestamp.from(q.expiraAt()) : null);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar token QR guardado"));
    }

    public Optional<SeguimientoIntrahospitalarioQr> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM SEGUIMIENTO_INTRAHOSPITALARIO_QR WHERE ID = ?";
        List<SeguimientoIntrahospitalarioQr> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<SeguimientoIntrahospitalarioQr> buscarPorToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM SEGUIMIENTO_INTRAHOSPITALARIO_QR WHERE CODIGO_QR_TOKEN = ?";
        List<SeguimientoIntrahospitalarioQr> list = jdbcTemplate.query(sql, rowMapper, token);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<SeguimientoIntrahospitalarioQr> buscarActivoPorEpisodioId(Long episodioId) {
        String sql = "SELECT * FROM SEGUIMIENTO_INTRAHOSPITALARIO_QR WHERE EPISODIO_ID = ? AND ESTADO = 'ACTIVO' ORDER BY CREADO_AT DESC";
        List<SeguimientoIntrahospitalarioQr> list = jdbcTemplate.query(sql, rowMapper, episodioId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void actualizarUbicacion(Long id, String nuevaUbicacion) {
        String sql = "UPDATE SEGUIMIENTO_INTRAHOSPITALARIO_QR SET UBICACION_ACTUAL_TEXTO = ? WHERE ID = ?";
        jdbcTemplate.update(sql, nuevaUbicacion, id);
    }
}
