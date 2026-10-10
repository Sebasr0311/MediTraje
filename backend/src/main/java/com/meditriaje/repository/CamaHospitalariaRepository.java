package com.meditriaje.repository;

import com.meditriaje.dto.hospital.CamaDetalleResponse;
import com.meditriaje.model.CamaHospitalaria;
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
public class CamaHospitalariaRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<CamaHospitalaria> rowMapper = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        return new CamaHospitalaria(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("HABITACION_ID"),
                rs.getString("CODIGO"),
                rs.getString("ESTADO"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null
        );
    };

    private final RowMapper<CamaDetalleResponse> detalleRowMapper = (rs, rowNum) -> new CamaDetalleResponse(
            rs.getString("CAMA_PUB_ID"),
            rs.getString("HAB_PUB_ID"),
            rs.getString("HAB_CODIGO"),
            rs.getString("AREA_PUB_ID"),
            rs.getString("AREA_NOMBRE"),
            rs.getString("SEDE_PUB_ID"),
            rs.getString("SEDE_NOMBRE"),
            rs.getString("CAMA_CODIGO"),
            rs.getString("ESTADO"),
            rs.getString("EPISODIO_PUB_ID"),
            rs.getString("PACIENTE_NOMBRE"),
            rs.getString("OCUPACION_PUB_ID")
    );

    public CamaHospitalariaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public CamaHospitalaria guardar(CamaHospitalaria cama) {
        String sql = """
                INSERT INTO CAMA_HOSPITALARIA (
                    PUBLIC_ID, HABITACION_ID, CODIGO, ESTADO
                ) VALUES (?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, cama.publicId());
            ps.setLong(2, cama.habitacionId());
            ps.setString(3, cama.codigo());
            ps.setString(4, cama.estado() != null ? cama.estado() : "DISPONIBLE");
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar cama recién guardada"));
    }

    public Optional<CamaHospitalaria> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM CAMA_HOSPITALARIA WHERE ID = ?";
        List<CamaHospitalaria> resultados = jdbcTemplate.query(sql, rowMapper, id);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    public Optional<CamaHospitalaria> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM CAMA_HOSPITALARIA WHERE PUBLIC_ID = ?";
        List<CamaHospitalaria> resultados = jdbcTemplate.query(sql, rowMapper, publicId);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    public void actualizarEstado(Long camaId, String nuevoEstado) {
        String sql = "UPDATE CAMA_HOSPITALARIA SET ESTADO = ?, UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = ?";
        jdbcTemplate.update(sql, nuevoEstado, camaId);
    }

    public List<CamaDetalleResponse> listarDetallePorSedeId(Long sedeId) {
        String sql = """
                SELECT c.PUBLIC_ID AS CAMA_PUB_ID,
                       c.CODIGO AS CAMA_CODIGO,
                       c.ESTADO,
                       h.PUBLIC_ID AS HAB_PUB_ID,
                       h.CODIGO AS HAB_CODIGO,
                       a.PUBLIC_ID AS AREA_PUB_ID,
                       a.NOMBRE AS AREA_NOMBRE,
                       s.PUBLIC_ID AS SEDE_PUB_ID,
                       s.NOMBRE AS SEDE_NOMBRE,
                       ep.PUBLIC_ID AS EPISODIO_PUB_ID,
                       CASE
                           WHEN p.ID IS NOT NULL THEN (p.NOMBRE || ' ' || p.APELLIDO)
                           WHEN ip.CODIGO_PROVISIONAL IS NOT NULL THEN ('NN (' || ip.CODIGO_PROVISIONAL || ')')
                           ELSE NULL
                       END AS PACIENTE_NOMBRE,
                       oc.PUBLIC_ID AS OCUPACION_PUB_ID
                FROM CAMA_HOSPITALARIA c
                JOIN HABITACION_SALA h ON c.HABITACION_ID = h.ID
                JOIN AREA_HOSPITALARIA a ON h.AREA_ID = a.ID
                JOIN SEDE s ON a.SEDE_ID = s.ID
                LEFT JOIN OCUPACION_CAMA oc ON oc.CAMA_ID = c.ID AND oc.ESTADO = 'ACTIVA'
                LEFT JOIN EPISODIO_ATENCION ep ON oc.EPISODIO_ID = ep.ID
                LEFT JOIN PACIENTE p ON ep.PACIENTE_ID = p.ID
                LEFT JOIN IDENTIDAD_PROVISIONAL ip ON ip.EPISODIO_ID = ep.ID AND ip.ESTADO = 'PROVISIONAL'
                WHERE s.ID = ?
                ORDER BY a.CODIGO, h.CODIGO, c.CODIGO
                """;
        return jdbcTemplate.query(sql, detalleRowMapper, sedeId);
    }

    public Optional<CamaDetalleResponse> buscarDetallePorPublicId(String publicId) {
        String sql = """
                SELECT c.PUBLIC_ID AS CAMA_PUB_ID,
                       c.CODIGO AS CAMA_CODIGO,
                       c.ESTADO,
                       h.PUBLIC_ID AS HAB_PUB_ID,
                       h.CODIGO AS HAB_CODIGO,
                       a.PUBLIC_ID AS AREA_PUB_ID,
                       a.NOMBRE AS AREA_NOMBRE,
                       s.PUBLIC_ID AS SEDE_PUB_ID,
                       s.NOMBRE AS SEDE_NOMBRE,
                       ep.PUBLIC_ID AS EPISODIO_PUB_ID,
                       CASE
                           WHEN p.ID IS NOT NULL THEN (p.NOMBRE || ' ' || p.APELLIDO)
                           WHEN ip.CODIGO_PROVISIONAL IS NOT NULL THEN ('NN (' || ip.CODIGO_PROVISIONAL || ')')
                           ELSE NULL
                       END AS PACIENTE_NOMBRE,
                       oc.PUBLIC_ID AS OCUPACION_PUB_ID
                FROM CAMA_HOSPITALARIA c
                JOIN HABITACION_SALA h ON c.HABITACION_ID = h.ID
                JOIN AREA_HOSPITALARIA a ON h.AREA_ID = a.ID
                JOIN SEDE s ON a.SEDE_ID = s.ID
                LEFT JOIN OCUPACION_CAMA oc ON oc.CAMA_ID = c.ID AND oc.ESTADO = 'ACTIVA'
                LEFT JOIN EPISODIO_ATENCION ep ON oc.EPISODIO_ID = ep.ID
                LEFT JOIN PACIENTE p ON ep.PACIENTE_ID = p.ID
                LEFT JOIN IDENTIDAD_PROVISIONAL ip ON ip.EPISODIO_ID = ep.ID AND ip.ESTADO = 'PROVISIONAL'
                WHERE c.PUBLIC_ID = ?
                """;
        List<CamaDetalleResponse> list = jdbcTemplate.query(sql, detalleRowMapper, publicId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
