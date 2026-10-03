package com.meditriaje.repository;

import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.model.DisponibilidadSlot;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la entidad {@code DISPONIBILIDAD_SLOT}.
 * Implementa consultas parametrizadas, inserción en lote y paginación estándar ANSI/Oracle (ADR-001, ADR-003, ADR-006, HU-10).
 */
@Repository
public class DisponibilidadSlotRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SlotResponse> slotResponseRowMapper = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("FECHA_HORA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_HORA_FIN");
        return new SlotResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("SEDE_PUBLIC_ID"),
                rs.getString("SEDE_NOMBRE"),
                rs.getString("ESPECIALIDAD_PUBLIC_ID"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("MODALIDAD"),
                rs.getString("ESTADO")
        );
    };

    private final RowMapper<DisponibilidadSlot> slotRowMapper = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("FECHA_HORA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_HORA_FIN");
        return new DisponibilidadSlot(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PROFESIONAL_ID"),
                rs.getLong("SEDE_ID"),
                rs.getLong("ESPECIALIDAD_ID"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("MODALIDAD"),
                rs.getString("ESTADO")
        );
    };

    public DisponibilidadSlotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public Long guardar(DisponibilidadSlot slot) {
        Objects.requireNonNull(slot, "El slot no puede ser nulo");
        final String sql = """
            INSERT INTO DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, SEDE_ID, ESPECIALIDAD_ID,
                                            FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setString(1, slot.publicId());
            ps.setLong(2, slot.profesionalId());
            ps.setLong(3, slot.sedeId());
            ps.setLong(4, slot.especialidadId());
            ps.setTimestamp(5, Timestamp.from(slot.fechaHoraInicio()));
            ps.setTimestamp(6, Timestamp.from(slot.fechaHoraFin()));
            ps.setString(7, slot.modalidad());
            ps.setString(8, slot.estado());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el slot.");
        }
        return key.longValue();
    }

    public void guardarLote(List<DisponibilidadSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        final String sql = """
            INSERT INTO DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, SEDE_ID, ESPECIALIDAD_ID,
                                            FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DisponibilidadSlot slot = slots.get(i);
                ps.setString(1, slot.publicId());
                ps.setLong(2, slot.profesionalId());
                ps.setLong(3, slot.sedeId());
                ps.setLong(4, slot.especialidadId());
                ps.setTimestamp(5, Timestamp.from(slot.fechaHoraInicio()));
                ps.setTimestamp(6, Timestamp.from(slot.fechaHoraFin()));
                ps.setString(7, slot.modalidad());
                ps.setString(8, slot.estado());
            }

            @Override
            public int getBatchSize() {
                return slots.size();
            }
        });
    }

    public Optional<SlotResponse> buscarPorPublicId(String publicId) {
        final String sql = """
            SELECT s.PUBLIC_ID,
                   p.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   p.NOMBRES || ' ' || p.APELLIDOS AS PROFESIONAL_NOMBRE,
                   sd.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   sd.NOMBRE AS SEDE_NOMBRE,
                   e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   s.FECHA_HORA_INICIO,
                   s.FECHA_HORA_FIN,
                   s.MODALIDAD,
                   s.ESTADO
            FROM DISPONIBILIDAD_SLOT s
            JOIN PROFESIONAL p ON s.PROFESIONAL_ID = p.ID
            JOIN SEDE sd ON s.SEDE_ID = sd.ID
            JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
            WHERE s.PUBLIC_ID = ?
            """;
        List<SlotResponse> resultados = jdbcTemplate.query(sql, slotResponseRowMapper, publicId);
        return resultados.stream().findFirst();
    }

    public Optional<DisponibilidadSlot> buscarPorId(Long id) {
        final String sql = """
            SELECT ID, PUBLIC_ID, PROFESIONAL_ID, SEDE_ID, ESPECIALIDAD_ID,
                   FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO
            FROM DISPONIBILIDAD_SLOT
            WHERE ID = ?
            """;
        List<DisponibilidadSlot> resultados = jdbcTemplate.query(sql, slotRowMapper, id);
        return resultados.stream().findFirst();
    }

    public Optional<DisponibilidadSlot> buscarEntidadPorPublicId(String publicId) {
        final String sql = """
            SELECT ID, PUBLIC_ID, PROFESIONAL_ID, SEDE_ID, ESPECIALIDAD_ID,
                   FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO
            FROM DISPONIBILIDAD_SLOT
            WHERE PUBLIC_ID = ?
            """;
        List<DisponibilidadSlot> resultados = jdbcTemplate.query(sql, slotRowMapper, publicId);
        return resultados.stream().findFirst();
    }

    public boolean existeSolape(Long profesionalId, Instant inicio, Instant fin) {
        final String sql = """
            SELECT COUNT(*)
            FROM DISPONIBILIDAD_SLOT
            WHERE PROFESIONAL_ID = ?
              AND FECHA_HORA_INICIO < ?
              AND FECHA_HORA_FIN > ?
            """;
        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                profesionalId,
                Timestamp.from(fin),
                Timestamp.from(inicio)
        );
        return count != null && count > 0;
    }

    public void cambiarEstado(String publicId, String nuevoEstado) {
        final String sql = "UPDATE DISPONIBILIDAD_SLOT SET ESTADO = ? WHERE PUBLIC_ID = ?";
        jdbcTemplate.update(sql, nuevoEstado, publicId);
    }

    public int eliminar(String publicId) {
        final String sql = "DELETE FROM DISPONIBILIDAD_SLOT WHERE PUBLIC_ID = ? AND ESTADO = 'LIBRE'";
        return jdbcTemplate.update(sql, publicId);
    }

    public List<SlotResponse> listar(
            int page,
            int size,
            String profesionalPublicId,
            String sedePublicId,
            String especialidadPublicId,
            Instant fechaDesde,
            Instant fechaHasta,
            String estado
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT s.PUBLIC_ID,
                   p.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   p.NOMBRES || ' ' || p.APELLIDOS AS PROFESIONAL_NOMBRE,
                   sd.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   sd.NOMBRE AS SEDE_NOMBRE,
                   e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   s.FECHA_HORA_INICIO,
                   s.FECHA_HORA_FIN,
                   s.MODALIDAD,
                   s.ESTADO
            FROM DISPONIBILIDAD_SLOT s
            JOIN PROFESIONAL p ON s.PROFESIONAL_ID = p.ID
            JOIN SEDE sd ON s.SEDE_ID = sd.ID
            JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
            WHERE 1 = 1
            """);

        List<Object> params = new ArrayList<>();

        if (profesionalPublicId != null && !profesionalPublicId.isBlank()) {
            sql.append(" AND p.PUBLIC_ID = ?");
            params.add(profesionalPublicId.trim());
        }

        if (sedePublicId != null && !sedePublicId.isBlank()) {
            sql.append(" AND sd.PUBLIC_ID = ?");
            params.add(sedePublicId.trim());
        }

        if (especialidadPublicId != null && !especialidadPublicId.isBlank()) {
            sql.append(" AND e.PUBLIC_ID = ?");
            params.add(especialidadPublicId.trim());
        }

        if (fechaDesde != null) {
            sql.append(" AND s.FECHA_HORA_INICIO >= ?");
            params.add(Timestamp.from(fechaDesde));
        }

        if (fechaHasta != null) {
            sql.append(" AND s.FECHA_HORA_INICIO <= ?");
            params.add(Timestamp.from(fechaHasta));
        }

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND s.ESTADO = ?");
            params.add(estado.trim().toUpperCase(Locale.ROOT));
        }

        sql.append(" ORDER BY s.FECHA_HORA_INICIO ASC, s.ID ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);
        params.add(offset);
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), slotResponseRowMapper, params.toArray());
    }

    public int contar(
            String profesionalPublicId,
            String sedePublicId,
            String especialidadPublicId,
            Instant fechaDesde,
            Instant fechaHasta,
            String estado
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM DISPONIBILIDAD_SLOT s
            JOIN PROFESIONAL p ON s.PROFESIONAL_ID = p.ID
            JOIN SEDE sd ON s.SEDE_ID = sd.ID
            JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
            WHERE 1 = 1
            """);

        List<Object> params = new ArrayList<>();

        if (profesionalPublicId != null && !profesionalPublicId.isBlank()) {
            sql.append(" AND p.PUBLIC_ID = ?");
            params.add(profesionalPublicId.trim());
        }

        if (sedePublicId != null && !sedePublicId.isBlank()) {
            sql.append(" AND sd.PUBLIC_ID = ?");
            params.add(sedePublicId.trim());
        }

        if (especialidadPublicId != null && !especialidadPublicId.isBlank()) {
            sql.append(" AND e.PUBLIC_ID = ?");
            params.add(especialidadPublicId.trim());
        }

        if (fechaDesde != null) {
            sql.append(" AND s.FECHA_HORA_INICIO >= ?");
            params.add(Timestamp.from(fechaDesde));
        }

        if (fechaHasta != null) {
            sql.append(" AND s.FECHA_HORA_INICIO <= ?");
            params.add(Timestamp.from(fechaHasta));
        }

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND s.ESTADO = ?");
            params.add(estado.trim().toUpperCase(Locale.ROOT));
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }
}
