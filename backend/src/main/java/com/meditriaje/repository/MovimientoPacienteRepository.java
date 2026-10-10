package com.meditriaje.repository;

import com.meditriaje.dto.hospital.MovimientoResponse;
import com.meditriaje.model.MovimientoPaciente;
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
public class MovimientoPacienteRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<MovimientoPaciente> rowMapper = (rs, rowNum) -> {
        Timestamp tsFecha = rs.getTimestamp("FECHA_MOVIMIENTO");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        return new MovimientoPaciente(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getObject("AREA_ORIGEN_ID", Long.class),
                rs.getLong("AREA_DESTINO_ID"),
                rs.getObject("CAMA_ORIGEN_ID", Long.class),
                rs.getObject("CAMA_DESTINO_ID", Long.class),
                tsFecha != null ? tsFecha.toInstant() : null,
                rs.getString("MOTIVO_TRASLADO"),
                rs.getLong("REGISTRADO_POR_USUARIO_ID"),
                tsCreado != null ? tsCreado.toInstant() : null
        );
    };

    private final RowMapper<MovimientoResponse> responseRowMapper = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("FECHA_MOVIMIENTO");
        return new MovimientoResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("EPISODIO_PUB_ID"),
                rs.getString("AREA_ORIGEN_NOMBRE"),
                rs.getString("AREA_DESTINO_NOMBRE"),
                rs.getString("CAMA_ORIGEN_CODIGO"),
                rs.getString("CAMA_DESTINO_CODIGO"),
                ts != null ? ts.toInstant() : null,
                rs.getString("MOTIVO_TRASLADO"),
                rs.getString("REGISTRADO_POR_NOMBRE")
        );
    };

    public MovimientoPacienteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public MovimientoPaciente guardar(MovimientoPaciente mov) {
        String sql = """
                INSERT INTO MOVIMIENTO_PACIENTE (
                    PUBLIC_ID, EPISODIO_ID, AREA_ORIGEN_ID, AREA_DESTINO_ID,
                    CAMA_ORIGEN_ID, CAMA_DESTINO_ID, FECHA_MOVIMIENTO,
                    MOTIVO_TRASLADO, REGISTRADO_POR_USUARIO_ID
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, mov.publicId());
            ps.setLong(2, mov.episodioId());
            if (mov.areaOrigenId() != null) ps.setLong(3, mov.areaOrigenId()); else ps.setNull(3, java.sql.Types.NUMERIC);
            ps.setLong(4, mov.areaDestinoId());
            if (mov.camaOrigenId() != null) ps.setLong(5, mov.camaOrigenId()); else ps.setNull(5, java.sql.Types.NUMERIC);
            if (mov.camaDestinoId() != null) ps.setLong(6, mov.camaDestinoId()); else ps.setNull(6, java.sql.Types.NUMERIC);
            ps.setTimestamp(7, Timestamp.from(mov.fechaMovimiento()));
            ps.setString(8, mov.motivoTraslado());
            ps.setLong(9, mov.registradoPorUsuarioId());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar movimiento recién guardado"));
    }

    public Optional<MovimientoPaciente> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM MOVIMIENTO_PACIENTE WHERE ID = ?";
        List<MovimientoPaciente> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<MovimientoResponse> listarPorEpisodioId(Long episodioId) {
        String sql = """
                SELECT m.PUBLIC_ID,
                       e.PUBLIC_ID AS EPISODIO_PUB_ID,
                       ao.NOMBRE AS AREA_ORIGEN_NOMBRE,
                       ad.NOMBRE AS AREA_DESTINO_NOMBRE,
                       co.CODIGO AS CAMA_ORIGEN_CODIGO,
                       cd.CODIGO AS CAMA_DESTINO_CODIGO,
                       m.FECHA_MOVIMIENTO,
                       m.MOTIVO_TRASLADO,
                       u.EMAIL AS REGISTRADO_POR_NOMBRE
                FROM MOVIMIENTO_PACIENTE m
                JOIN EPISODIO_ATENCION e ON m.EPISODIO_ID = e.ID
                LEFT JOIN AREA_HOSPITALARIA ao ON m.AREA_ORIGEN_ID = ao.ID
                JOIN AREA_HOSPITALARIA ad ON m.AREA_DESTINO_ID = ad.ID
                LEFT JOIN CAMA_HOSPITALARIA co ON m.CAMA_ORIGEN_ID = co.ID
                LEFT JOIN CAMA_HOSPITALARIA cd ON m.CAMA_DESTINO_ID = cd.ID
                JOIN USUARIO u ON m.REGISTRADO_POR_USUARIO_ID = u.ID
                WHERE m.EPISODIO_ID = ?
                ORDER BY m.FECHA_MOVIMIENTO ASC
                """;
        return jdbcTemplate.query(sql, responseRowMapper, episodioId);
    }
}
